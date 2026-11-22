package com.cani.service.admin;

import com.cani.dto.admin.shift.AdminShiftDetailDto;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.ShiftStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.AttendanceLogRepository;
import com.cani.repository.PaymentRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import com.cani.service.AdminShiftService;
import com.cani.service.AuditLogService;
import com.cani.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminShiftServiceTest {

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AdminShiftService adminShiftService;

    private UUID adminUserId;
    private UUID shiftId;
    private UUID eoUserId;
    private Shift testShift;
    private Event testEvent;
    private EventOrganizer testEo;
    private User testEoUser;

    @BeforeEach
    void setUp() {
        adminUserId = UUID.randomUUID();
        shiftId = UUID.randomUUID();
        eoUserId = UUID.randomUUID();

        testEoUser = User.builder()
                .userId(eoUserId)
                .fullName("Nguyễn Văn EO")
                .phone("0901234567")
                .email("eo@cani.vn")
                .build();

        testEo = EventOrganizer.builder()
                .eoId(UUID.randomUUID())
                .user(testEoUser)
                .companyName("Công ty Sự Kiện Việt")
                .build();

        testEvent = Event.builder()
                .eventId(UUID.randomUUID())
                .name("Lễ Hội Âm Nhạc CANi")
                .eventOrganizer(testEo)
                .venueAddress("Trung tâm Hội nghị Quốc gia")
                .build();

        testShift = Shift.builder()
                .shiftId(shiftId)
                .event(testEvent)
                .jobType("Phục vụ bàn")
                .hourlyWage(new BigDecimal("50000"))
                .requiredWorkers(5)
                .confirmedWorkers(2)
                .remainingWorkers(3)
                .status(ShiftStatus.ACTIVE)
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .build();
    }

    @Test
    @DisplayName("Hủy ca cưỡng chế thành công khi ca đang ACTIVE")
    void testCancelShift_Success() {
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(testShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID workerUserId = UUID.randomUUID();
        User workerUser = User.builder().userId(workerUserId).fullName("Worker 1").build();
        Worker worker = Worker.builder().workerId(UUID.randomUUID()).user(workerUser).build();
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(UUID.randomUUID())
                .shift(testShift)
                .worker(worker)
                .status(AssignmentStatus.CONFIRMED)
                .build();

        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(List.of(sa));
        when(paymentRepository.findByShiftId(shiftId)).thenReturn(Collections.emptyList());

        AdminShiftDetailDto result = adminShiftService.cancelShift(adminUserId, shiftId, "Phát hiện thông tin sai lệch");

        assertThat(result).isNotNull();
        assertThat(testShift.getStatus()).isEqualTo(ShiftStatus.CANCELLED);
        assertThat(testShift.getCancellationReason()).isEqualTo("Phát hiện thông tin sai lệch");
        assertThat(testShift.getCancelledBy()).isEqualTo(adminUserId);
        assertThat(sa.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);

        verify(notificationService, atLeast(2)).createAndSend(any(), anyString(), anyString(), any(), anyString(), any(), any());
        verify(auditLogService, times(1)).logAction(any());
    }

    @Test
    @DisplayName("Hủy ca thất bại khi ca đã hoàn thành (COMPLETED)")
    void testCancelShift_AlreadyCompleted() {
        testShift.setStatus(ShiftStatus.COMPLETED);
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(testShift));

        assertThatThrownBy(() -> adminShiftService.cancelShift(adminUserId, shiftId, "Lý do hủy"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể hủy ca làm việc đã hoàn thành");
    }

    @Test
    @DisplayName("Tạm đình chỉ ca thành công khi ca đang ACTIVE")
    void testSuspendShift_Success() {
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(testShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(Collections.emptyList());
        when(paymentRepository.findByShiftId(shiftId)).thenReturn(Collections.emptyList());

        AdminShiftDetailDto result = adminShiftService.suspendShift(adminUserId, shiftId, "Tạm dừng để kiểm tra khiếu nại");

        assertThat(result).isNotNull();
        assertThat(testShift.getStatus()).isEqualTo(ShiftStatus.SUSPENDED);
        assertThat(testShift.getAdminNote()).isEqualTo("Tạm dừng để kiểm tra khiếu nại");
        assertThat(testShift.getSuspendedAt()).isNotNull();

        verify(notificationService, times(1)).createAndSend(eq(eoUserId), anyString(), anyString(), any(), anyString(), any(), any());
        verify(auditLogService, times(1)).logAction(any());
    }

    @Test
    @DisplayName("Tạm đình chỉ ca thất bại khi ca đã hủy (CANCELLED)")
    void testSuspendShift_AlreadyCancelled() {
        testShift.setStatus(ShiftStatus.CANCELLED);
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(testShift));

        assertThatThrownBy(() -> adminShiftService.suspendShift(adminUserId, shiftId, "Lý do"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chỉ có thể tạm đình chỉ ca đang hoạt động hoặc đang tuyển");
    }

    @Test
    @DisplayName("Gửi cảnh báo tới EO thành công")
    void testWarnEo_Success() {
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(testShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(Collections.emptyList());
        when(paymentRepository.findByShiftId(shiftId)).thenReturn(Collections.emptyList());

        AdminShiftDetailDto result = adminShiftService.warnEo(adminUserId, shiftId, "Yêu cầu chỉnh sửa thù lao theo cam kết");

        assertThat(result).isNotNull();
        assertThat(testShift.getAdminNote()).isEqualTo("Yêu cầu chỉnh sửa thù lao theo cam kết");

        verify(notificationService, times(1)).createAndSend(eq(eoUserId), anyString(), anyString(), any(), anyString(), any(), any());
        verify(auditLogService, times(1)).logAction(any());
    }
}
