package com.cani.service;

import com.cani.entity.Event;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Shift;
import com.cani.entity.User;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.EventMode;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.ShiftStatus;
import com.cani.repository.DispatchProposalRepository;
import com.cani.repository.EventRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShiftLifecycleService - Cảnh báo thiếu nhân sự sát giờ & Vòng đời ca")
class ShiftLifecycleServiceTest {

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private DispatchProposalRepository dispatchProposalRepository;

    @InjectMocks
    private ShiftLifecycleService shiftLifecycleService;

    private UUID eoUserId;
    private Event activeMatchingEvent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shiftLifecycleService, "notificationService", notificationService);
        ReflectionTestUtils.setField(shiftLifecycleService, "shiftAssignmentRepository", shiftAssignmentRepository);
        ReflectionTestUtils.setField(shiftLifecycleService, "dispatchProposalRepository", dispatchProposalRepository);
        shiftLifecycleService.setUnderstaffedAlertHours(4);

        eoUserId = UUID.randomUUID();
        User eoUser = User.builder().userId(eoUserId).fullName("EO Test").build();
        EventOrganizer eo = EventOrganizer.builder().eoId(UUID.randomUUID()).user(eoUser).build();

        activeMatchingEvent = Event.builder()
                .eventId(UUID.randomUUID())
                .eventOrganizer(eo)
                .name("Tech Summit 2026")
                .mode(EventMode.MATCHING)
                .status(EventStatus.ACTIVE)
                .startTime(Instant.now().plus(Duration.ofHours(1)))
                .endTime(Instant.now().plus(Duration.ofHours(10)))
                .build();

        lenient().when(eventRepository.findByStatusNotInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Còn hơn 4 giờ trước giờ bắt đầu -> không tạo cảnh báo sát giờ")
    void evaluate_MoreThan4HoursBeforeStart_NoAlertSent() {
        Instant now = Instant.now();
        Shift shift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(activeMatchingEvent)
                .jobType("Lễ tân")
                .status(ShiftStatus.ACTIVE)
                .requiredWorkers(6)
                .confirmedWorkers(2)
                .startTime(now.plus(Duration.ofHours(5)))
                .endTime(now.plus(Duration.ofHours(9)))
                .understaffedAlertSent(false)
                .build();

        when(shiftRepository.findByStatusNotInWithEvent(anyCollection())).thenReturn(List.of(shift));
        when(shiftAssignmentRepository.existsByShift_ShiftId(shift.getShiftId())).thenReturn(false);

        shiftLifecycleService.evaluateShiftsAndEventsLifecycleAt(now);

        verify(notificationService, never()).createAndSend(any(), any(), any(), any(), any(), any(), any());
        assertFalse(Boolean.TRUE.equals(shift.getUnderstaffedAlertSent()));
    }

    @Test
    @DisplayName("Mốc cảnh báo cấu hình được (ví dụ 6h) -> vào khung 6h gửi cảnh báo")
    void evaluate_CustomConfiguredAlertHours_TriggersWithinCustomWindow() {
        shiftLifecycleService.setUnderstaffedAlertHours(6);
        Instant now = Instant.now();
        Shift shift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(activeMatchingEvent)
                .jobType("Lễ tân")
                .status(ShiftStatus.ACTIVE)
                .requiredWorkers(4)
                .confirmedWorkers(1)
                .startTime(now.plus(Duration.ofHours(5)))
                .endTime(now.plus(Duration.ofHours(9)))
                .understaffedAlertSent(false)
                .build();

        when(shiftRepository.findByStatusNotInWithEvent(anyCollection())).thenReturn(List.of(shift));
        when(shiftAssignmentRepository.existsByShift_ShiftId(shift.getShiftId())).thenReturn(false);

        shiftLifecycleService.evaluateShiftsAndEventsLifecycleAt(now);

        verify(notificationService, times(1)).createAndSend(
                eq(eoUserId), contains("Thiếu nhân sự sát giờ"), contains("Đã chốt 1/4 · Còn thiếu 3 người"),
                eq(NotificationType.REMINDER), eq("SHIFT"), eq(shift.getShiftId()), anyMap());
        assertTrue(Boolean.TRUE.equals(shift.getUnderstaffedAlertSent()));
    }

    @Test
    @DisplayName("Vào khung 4h & thiếu người -> tạo 1 thông báo với eventId/shiftId, job chạy lặp không trùng; đủ rồi lại thiếu -> gửi đợt mới")
    void evaluate_EpisodeLifecycle_DeduplicatesWithinEpisodeAndAlertsOnNewEpisode() {
        Instant now = Instant.now();
        UUID shiftId = UUID.randomUUID();
        Shift shift = Shift.builder()
                .shiftId(shiftId)
                .event(activeMatchingEvent)
                .jobType("Lễ tân")
                .status(ShiftStatus.ACTIVE)
                .requiredWorkers(6)
                .confirmedWorkers(4)
                .startTime(now.plus(Duration.ofHours(2)))
                .endTime(now.plus(Duration.ofHours(6)))
                .understaffedAlertSent(false)
                .build();

        when(shiftRepository.findByStatusNotInWithEvent(anyCollection())).thenReturn(List.of(shift));
        // Có 4 assignment CONFIRMED (các proposal PENDING không tính là đã chốt)
        when(shiftAssignmentRepository.existsByShift_ShiftId(shiftId)).thenReturn(true);
        when(shiftAssignmentRepository.countByShift_ShiftIdAndStatusIn(eq(shiftId), anyList())).thenReturn(4L);

        // Lần 1: Đợt thiếu đầu tiên -> gửi đúng 1 thông báo
        shiftLifecycleService.evaluateShiftsAndEventsLifecycleAt(now);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService, times(1)).createAndSend(
                eq(eoUserId),
                contains("Thiếu nhân sự sát giờ"),
                contains("Đã chốt 4/6 · Còn thiếu 2 người"),
                eq(NotificationType.REMINDER),
                eq("SHIFT"),
                eq(shiftId),
                payloadCaptor.capture()
        );
        assertEquals(activeMatchingEvent.getEventId().toString(), payloadCaptor.getValue().get("eventId"));
        assertEquals(shiftId.toString(), payloadCaptor.getValue().get("shiftId"));
        assertTrue(Boolean.TRUE.equals(shift.getUnderstaffedAlertSent()));

        // Lần 2: Job chạy lại, số thiếu đổi từ 2 lên 3 trong cùng đợt -> KHÔNG gửi trùng
        when(shiftAssignmentRepository.countByShift_ShiftIdAndStatusIn(eq(shiftId), anyList())).thenReturn(3L);
        shiftLifecycleService.evaluateShiftsAndEventsLifecycleAt(now.plusSeconds(60));
        verify(notificationService, times(1)).createAndSend(any(), any(), any(), any(), any(), any(), any());

        // Lần 3: Ca đã đủ 6/6 người -> reset trạng thái đợt cảnh báo (vẫn giữ lịch sử trong notifications)
        when(shiftAssignmentRepository.countByShift_ShiftIdAndStatusIn(eq(shiftId), anyList())).thenReturn(6L);
        shiftLifecycleService.evaluateShiftsAndEventsLifecycleAt(now.plusSeconds(120));
        assertFalse(Boolean.TRUE.equals(shift.getUnderstaffedAlertSent()));
        verify(notificationService, times(1)).createAndSend(any(), any(), any(), any(), any(), any(), any());

        // Lần 4: Có người hủy nên thiếu lại (5/6) -> mở đợt mới, gửi thông báo mới
        when(shiftAssignmentRepository.countByShift_ShiftIdAndStatusIn(eq(shiftId), anyList())).thenReturn(5L);
        shiftLifecycleService.evaluateShiftsAndEventsLifecycleAt(now.plusSeconds(180));
        verify(notificationService, times(2)).createAndSend(
                eq(eoUserId), anyString(), anyString(),
                eq(NotificationType.REMINDER), eq("SHIFT"), eq(shiftId), anyMap());
        verify(notificationService, times(1)).createAndSend(
                eq(eoUserId), anyString(), contains("Đã chốt 5/6 · Còn thiếu 1 người"),
                eq(NotificationType.REMINDER), eq("SHIFT"), eq(shiftId), anyMap());
        assertTrue(Boolean.TRUE.equals(shift.getUnderstaffedAlertSent()));
    }

    @Test
    @DisplayName("Bỏ qua ca nháp (DRAFT), đã hủy (CANCELLED) hoặc đã kết thúc (COMPLETED)")
    void evaluate_SkipsDraftCancelledAndCompletedShifts() {
        Instant now = Instant.now();
        Shift draft = Shift.builder().shiftId(UUID.randomUUID()).event(activeMatchingEvent).jobType("Nháp")
                .status(ShiftStatus.DRAFT).requiredWorkers(5).confirmedWorkers(0)
                .startTime(now.plus(Duration.ofHours(1))).endTime(now.plus(Duration.ofHours(4))).build();
        Shift cancelled = Shift.builder().shiftId(UUID.randomUUID()).event(activeMatchingEvent).jobType("Hủy")
                .status(ShiftStatus.CANCELLED).requiredWorkers(5).confirmedWorkers(0)
                .startTime(now.plus(Duration.ofHours(1))).endTime(now.plus(Duration.ofHours(4))).build();
        Shift completed = Shift.builder().shiftId(UUID.randomUUID()).event(activeMatchingEvent).jobType("Xong")
                .status(ShiftStatus.COMPLETED).requiredWorkers(5).confirmedWorkers(0)
                .startTime(now.minus(Duration.ofHours(5))).endTime(now.minus(Duration.ofHours(1))).build();

        when(shiftRepository.findByStatusNotInWithEvent(anyCollection())).thenReturn(List.of(draft, cancelled, completed));

        shiftLifecycleService.evaluateShiftsAndEventsLifecycleAt(now);

        verify(notificationService, never()).createAndSend(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ca đã vào giờ làm (IN_PROGRESS) thiếu người: cảnh báo KHÔNG mời kích hoạt điều phối khẩn cấp")
    void evaluate_InProgressShiftUnderstaffed_AlertReflectsAllowedEoActions() {
        Instant now = Instant.now();
        Shift shift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(activeMatchingEvent)
                .jobType("Âm thanh")
                .status(ShiftStatus.ACTIVE)
                .requiredWorkers(3)
                .confirmedWorkers(1)
                .startTime(now.minus(Duration.ofMinutes(5)))
                .endTime(now.plus(Duration.ofHours(4)))
                .understaffedAlertSent(false)
                .build();

        when(shiftRepository.findByStatusNotInWithEvent(anyCollection())).thenReturn(List.of(shift));
        when(shiftAssignmentRepository.existsByShift_ShiftId(shift.getShiftId())).thenReturn(false);

        shiftLifecycleService.evaluateShiftsAndEventsLifecycleAt(now);

        assertEquals(ShiftStatus.IN_PROGRESS, shift.getStatus());
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(1)).createAndSend(
                eq(eoUserId),
                contains("Cảnh báo thiếu nhân sự khi vào ca"),
                bodyCaptor.capture(),
                eq(NotificationType.REMINDER),
                eq("SHIFT"),
                eq(shift.getShiftId()),
                anyMap()
        );
        assertFalse(bodyCaptor.getValue().toLowerCase().contains("bật điều phối khẩn cấp"),
                "Cảnh báo ca IN_PROGRESS không được mời EO bấm điều phối khẩn cấp");
        assertTrue(bodyCaptor.getValue().contains("điểm danh"));
    }

    @Test
    @DisplayName("Ca đã được luồng khác bật Emergency trước đó -> job quét không gửi thêm cảnh báo mời kích hoạt")
    void evaluate_AlreadyEmergencyEnabled_DoesNotAlertToTriggerAgain() {
        Instant now = Instant.now();
        Shift shift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(activeMatchingEvent)
                .jobType("Phục vụ")
                .status(ShiftStatus.ACTIVE)
                .requiredWorkers(5)
                .confirmedWorkers(2)
                .startTime(now.plus(Duration.ofHours(2)))
                .endTime(now.plus(Duration.ofHours(6)))
                .emergency(true)
                .understaffedAlertSent(false)
                .build();

        when(shiftRepository.findByStatusNotInWithEvent(anyCollection())).thenReturn(List.of(shift));
        when(shiftAssignmentRepository.existsByShift_ShiftId(shift.getShiftId())).thenReturn(false);

        shiftLifecycleService.evaluateShiftsAndEventsLifecycleAt(now);

        verify(notificationService, never()).createAndSend(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sự kiện INVITE thiếu người sát giờ -> thông báo nhắc mời thêm Worker")
    void evaluate_InviteModeUnderstaffed_SendsInviteGuidance() {
        Instant now = Instant.now();
        activeMatchingEvent.setMode(EventMode.INVITE);
        Shift shift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(activeMatchingEvent)
                .jobType("Check-in")
                .status(ShiftStatus.ACTIVE)
                .requiredWorkers(4)
                .confirmedWorkers(2)
                .startTime(now.plus(Duration.ofHours(2)))
                .endTime(now.plus(Duration.ofHours(6)))
                .understaffedAlertSent(false)
                .build();

        when(shiftRepository.findByStatusNotInWithEvent(anyCollection())).thenReturn(List.of(shift));
        when(shiftAssignmentRepository.existsByShift_ShiftId(shift.getShiftId())).thenReturn(false);

        shiftLifecycleService.evaluateShiftsAndEventsLifecycleAt(now);

        verify(notificationService, times(1)).createAndSend(
                eq(eoUserId),
                contains("Thiếu nhân sự sát giờ"),
                contains("mời thêm Worker"),
                eq(NotificationType.REMINDER),
                eq("SHIFT"),
                eq(shift.getShiftId()),
                anyMap()
        );
        assertFalse(Boolean.TRUE.equals(shift.getEmergency()), "Job cảnh báo không được tự bật emergency");
    }
}
