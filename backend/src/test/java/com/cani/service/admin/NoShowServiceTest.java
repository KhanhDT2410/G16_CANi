package com.cani.service.admin;

import com.cani.service.*;

import com.cani.dto.admin.noshow.NoShowFilterRequestDto;
import com.cani.dto.admin.noshow.NoShowResponseDto;
import com.cani.dto.admin.noshow.NoShowScanResultDto;
import com.cani.dto.admin.noshow.NoShowStatsDto;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.AttendanceStatus;
import com.cani.entity.enums.NoShowDetectedBy;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoShowServiceTest {

    @Mock
    private NoShowRecordRepository noShowRecordRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReliabilityLogRepository reliabilityLogRepository;

    @Mock
    private RsAppealRepository rsAppealRepository;

    @Mock
    private MatchingDispatchService matchingDispatchService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private NoShowService noShowService;

    private User testUser;
    private Worker testWorker;
    private Event testEvent;
    private Shift testShift;
    private ShiftAssignment testAssignment;
    private UUID assignmentId;
    private UUID workerId;

    @BeforeEach
    void setUp() {
        assignmentId = UUID.randomUUID();
        workerId = UUID.randomUUID();

        testUser = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Nguyễn Văn A")
                .email("worker@cani.vn")
                .status(UserStatus.ACTIVE)
                .build();

        testWorker = Worker.builder()
                .workerId(workerId)
                .user(testUser)
                .reliabilityScore(85)
                .build();

        testEvent = Event.builder()
                .eventId(UUID.randomUUID())
                .name("Lễ Hội Âm Nhạc CANi")
                .build();

        testShift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(testEvent)
                .jobType("Nhân viên an ninh")
                .startTime(Instant.now().minusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .requiredWorkers(5)
                .remainingWorkers(2)
                .build();

        testAssignment = ShiftAssignment.builder()
                .assignmentId(assignmentId)
                .shift(testShift)
                .worker(testWorker)
                .status(AssignmentStatus.CONFIRMED)
                .build();
    }

    @Test
    @DisplayName("Đánh dấu No-show thành công: trừ 25 RS, tạo log, đổi status sang NO_SHOW và kích hoạt Emergency Dispatch")
    void markNoShow_success() {
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(testAssignment));
        when(noShowRecordRepository.existsByShiftAssignment_AssignmentId(assignmentId)).thenReturn(false);
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.empty());

        when(reliabilityLogRepository.save(any(ReliabilityLog.class))).thenAnswer(invocation -> {
            ReliabilityLog log = invocation.getArgument(0);
            log.setLogId(UUID.randomUUID());
            return log;
        });

        when(noShowRecordRepository.save(any(NoShowRecord.class))).thenAnswer(invocation -> {
            NoShowRecord record = invocation.getArgument(0);
            record.setId(UUID.randomUUID());
            return record;
        });

        when(matchingDispatchService.dispatchForShift(testShift.getShiftId())).thenReturn(3);

        NoShowResponseDto result = noShowService.markNoShow(
                assignmentId, NoShowDetectedBy.ADMIN, UUID.randomUUID(), "Worker không đến làm"
        );

        assertNotNull(result);
        assertEquals(AssignmentStatus.NO_SHOW, testAssignment.getStatus());
        assertEquals(3, testShift.getRemainingWorkers()); // Đã cộng thêm 1 slot
        assertEquals(60, testWorker.getReliabilityScore()); // 85 - 25 = 60
        assertFalse(result.getIsAccountLocked());
        assertTrue(result.getEmergencyDispatched());

        verify(workerRepository).save(testWorker);
        verify(shiftRepository).save(testShift);
        verify(shiftAssignmentRepository).save(testAssignment);
        verify(attendanceLogRepository).save(any(AttendanceLog.class));
        verify(reliabilityLogRepository).save(any(ReliabilityLog.class));
        verify(notificationService, atLeastOnce()).sendNotification(any(User.class), anyString(), anyString(), any(), anyString(), any());
        verify(matchingDispatchService).dispatchForShift(testShift.getShiftId());
        verify(auditLogService).logAction(any());
    }

    @Test
    @DisplayName("Tự động khóa tài khoản khi điểm uy tín RS giảm dưới ngưỡng 20 sau khi bị trừ No-show")
    void markNoShow_autoLock_whenRsBelow20() {
        testWorker.setReliabilityScore(30); // 30 - 25 = 5 (< 20 threshold)

        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(testAssignment));
        when(noShowRecordRepository.existsByShiftAssignment_AssignmentId(assignmentId)).thenReturn(false);
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(reliabilityLogRepository.save(any(ReliabilityLog.class))).thenAnswer(i -> i.getArgument(0));
        when(noShowRecordRepository.save(any(NoShowRecord.class))).thenAnswer(i -> i.getArgument(0));

        NoShowResponseDto result = noShowService.markNoShow(
                assignmentId, NoShowDetectedBy.ADMIN, UUID.randomUUID(), "Vắng mặt ca trực"
        );

        assertEquals(5, testWorker.getReliabilityScore());
        assertEquals(UserStatus.LOCKED, testUser.getStatus());
        assertTrue(result.getIsAccountLocked());

        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Ném lỗi BadRequestException nếu ca làm việc đã được ghi nhận No-show trước đó")
    void markNoShow_fails_whenAlreadyMarked() {
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(testAssignment));
        when(noShowRecordRepository.existsByShiftAssignment_AssignmentId(assignmentId)).thenReturn(true);

        assertThrows(BadRequestException.class, () ->
                noShowService.markNoShow(assignmentId, NoShowDetectedBy.ADMIN, UUID.randomUUID(), "Lỗi trùng")
        );

        verify(workerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném lỗi BadRequestException nếu ca làm việc đã ở trạng thái COMPLETED hoặc CANCELLED")
    void markNoShow_fails_whenAssignmentCompletedOrCancelled() {
        testAssignment.setStatus(AssignmentStatus.COMPLETED);
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(testAssignment));
        when(noShowRecordRepository.existsByShiftAssignment_AssignmentId(assignmentId)).thenReturn(false);

        assertThrows(BadRequestException.class, () ->
                noShowService.markNoShow(assignmentId, NoShowDetectedBy.ADMIN, UUID.randomUUID(), "Lỗi trạng thái")
        );
    }

    @Test
    @DisplayName("Ném lỗi BadRequestException nếu nhân sự đã có log điểm danh QR check-in thành công")
    void markNoShow_fails_whenWorkerAlreadyCheckedIn() {
        AttendanceLog attendanceLog = AttendanceLog.builder()
                .checkinTime(Instant.now().minusSeconds(1800))
                .attendanceStatus(AttendanceStatus.ON_TIME)
                .build();

        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(testAssignment));
        when(noShowRecordRepository.existsByShiftAssignment_AssignmentId(assignmentId)).thenReturn(false);
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.of(attendanceLog));

        assertThrows(BadRequestException.class, () ->
                noShowService.markNoShow(assignmentId, NoShowDetectedBy.ADMIN, UUID.randomUUID(), "Lỗi đã check-in")
        );
    }

    @Test
    @DisplayName("Tiến trình quét tự động scanAndProcessNoShows phát hiện và xử lý thành công ca vi phạm quá 30 phút")
    void scanAndProcessNoShows_findsCandidates_andMarksThem() {
        when(shiftAssignmentRepository.findPotentialNoShows(eq(AssignmentStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(List.of(testAssignment));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(noShowRecordRepository.existsByShiftAssignment_AssignmentId(assignmentId)).thenReturn(false);
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(testAssignment));
        when(reliabilityLogRepository.save(any(ReliabilityLog.class))).thenAnswer(i -> i.getArgument(0));
        when(noShowRecordRepository.save(any(NoShowRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(matchingDispatchService.dispatchForShift(testShift.getShiftId())).thenReturn(2);

        NoShowScanResultDto scanResult = noShowService.scanAndProcessNoShows();

        assertNotNull(scanResult);
        assertEquals(1, scanResult.getTotalCandidatesScanned());
        assertEquals(1, scanResult.getTotalMarkedNoShow());
        assertEquals(1, scanResult.getTotalEmergencyDispatched());
        verify(auditLogService, atLeastOnce()).logAction(any());
    }

    @Test
    @DisplayName("Lấy danh sách phân trang các ca No-show kèm bộ lọc")
    void getNoShows_returnsPage() {
        NoShowRecord record = NoShowRecord.builder()
                .id(UUID.randomUUID())
                .shiftAssignment(testAssignment)
                .worker(testWorker)
                .shift(testShift)
                .event(testEvent)
                .detectedBy(NoShowDetectedBy.SYSTEM_CRON)
                .detectedAt(Instant.now())
                .penaltyAmount(25)
                .isAccountLocked(false)
                .emergencyDispatched(true)
                .build();

        Page<NoShowRecord> page = new PageImpl<>(List.of(record));
        when(noShowRecordRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(page);

        NoShowFilterRequestDto filter = NoShowFilterRequestDto.builder()
                .page(0)
                .size(10)
                .build();

        Page<NoShowResponseDto> result = noShowService.getNoShows(filter);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Nguyễn Văn A", result.getContent().get(0).getWorkerName());
        assertEquals("Lễ Hội Âm Nhạc CANi", result.getContent().get(0).getEventName());
    }

    @Test
    @DisplayName("Lấy số liệu thống kê tổng hợp No-show")
    void getNoShowStats_returnsSummary() {
        when(noShowRecordRepository.count()).thenReturn(15L);
        when(noShowRecordRepository.countByIsAccountLockedTrue()).thenReturn(2L);
        when(noShowRecordRepository.sumTotalPenaltyPoints()).thenReturn(375L);
        when(noShowRecordRepository.countByDetectedAtBetween(any(), any())).thenReturn(3L);

        NoShowStatsDto stats = noShowService.getNoShowStats();

        assertNotNull(stats);
        assertEquals(15L, stats.getTotalNoShows());
        assertEquals(2L, stats.getTotalAccountsLocked());
        assertEquals(375L, stats.getTotalRsDeducted());
        assertEquals(3L, stats.getNoShowsToday());
    }
}
