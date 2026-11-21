package com.cani.service.eo;

import com.cani.dto.admin.noshow.NoShowResponseDto;
import com.cani.dto.event.ReportNoShowRequestDto;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.NoShowDetectedBy;
import com.cani.exception.BadRequestException;
import com.cani.repository.*;
import com.cani.service.EoEventService;
import com.cani.service.NoShowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EoEventService - reportNoShow Unit Tests")
class EoEventServiceNoShowTest {

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private NoShowService noShowService;

    @InjectMocks
    private EoEventService eoEventService;

    private UUID userId;
    private UUID eoId;
    private UUID assignmentId;
    private EventOrganizer eo;
    private User eoUser;
    private Event event;
    private Shift shift;
    private ShiftAssignment assignment;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(eoEventService, "noShowService", noShowService);

        userId = UUID.randomUUID();
        eoId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();

        eoUser = User.builder().userId(userId).email("eo@cani.vn").fullName("EO Test").build();
        eo = EventOrganizer.builder().eoId(eoId).user(eoUser).build();

        event = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(eo).name("Festival Test").build();
        shift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(event)
                .jobType("Nhân viên an ninh")
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(3, ChronoUnit.HOURS))
                .build();

        assignment = ShiftAssignment.builder()
                .assignmentId(assignmentId)
                .shift(shift)
                .status(AssignmentStatus.CONFIRMED)
                .build();
    }

    @Test
    @DisplayName("UTC01 - Báo vắng mặt thành công khi ca đã bắt đầu và đúng chủ sở hữu")
    void reportNoShow_Success() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(eo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        NoShowResponseDto mockResponse = NoShowResponseDto.builder()
                .id(UUID.randomUUID())
                .assignmentId(assignmentId)
                .penaltyAmount(25)
                .detectedBy(NoShowDetectedBy.EVENT_ORGANIZER)
                .emergencyDispatched(true)
                .build();

        when(noShowService.markNoShow(eq(assignmentId), eq(NoShowDetectedBy.EVENT_ORGANIZER), eq(userId), any()))
                .thenReturn(mockResponse);

        ReportNoShowRequestDto request = ReportNoShowRequestDto.builder()
                .notes("Worker không có mặt khi ca bắt đầu")
                .build();

        NoShowResponseDto result = eoEventService.reportNoShow(userId, assignmentId, request);

        assertNotNull(result);
        assertEquals(25, result.getPenaltyAmount());
        assertEquals(NoShowDetectedBy.EVENT_ORGANIZER, result.getDetectedBy());
        assertTrue(Boolean.TRUE.equals(result.getEmergencyDispatched()));

        verify(noShowService, times(1))
                .markNoShow(eq(assignmentId), eq(NoShowDetectedBy.EVENT_ORGANIZER), eq(userId), eq("Worker không có mặt khi ca bắt đầu"));
    }

    @Test
    @DisplayName("UTC02 - Báo vắng mặt thất bại khi ca làm việc chưa đến giờ bắt đầu")
    void reportNoShow_ShiftNotStarted_ThrowsBadRequestException() {
        shift.setStartTime(Instant.now().plus(2, ChronoUnit.HOURS));

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(eo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        ReportNoShowRequestDto request = ReportNoShowRequestDto.builder().notes("Chưa đến giờ").build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                eoEventService.reportNoShow(userId, assignmentId, request)
        );

        assertTrue(ex.getMessage().contains("chưa đến giờ bắt đầu"));
        verify(noShowService, never()).markNoShow(any(), any(), any(), any());
    }

    @Test
    @DisplayName("UTC03 - Báo vắng mặt thất bại khi EO không sở hữu sự kiện của ca này")
    void reportNoShow_NotOwner_ThrowsBadRequestException() {
        EventOrganizer otherEo = EventOrganizer.builder().eoId(UUID.randomUUID()).build();
        event.setEventOrganizer(otherEo);

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(eo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        ReportNoShowRequestDto request = ReportNoShowRequestDto.builder().build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                eoEventService.reportNoShow(userId, assignmentId, request)
        );

        assertTrue(ex.getMessage().contains("không sở hữu"));
        verify(noShowService, never()).markNoShow(any(), any(), any(), any());
    }

    @Test
    @DisplayName("UTC04 - Báo vắng mặt thất bại khi NoShowService bị null (bảo trì)")
    void reportNoShow_ServiceNull_ThrowsBadRequestException() {
        ReflectionTestUtils.setField(eoEventService, "noShowService", null);

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(eo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                eoEventService.reportNoShow(userId, assignmentId, null)
        );

        assertTrue(ex.getMessage().contains("tạm bảo trì"));
    }
}
