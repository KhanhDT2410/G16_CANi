package com.cani.service.eo;

import com.cani.dto.event.ShiftResponseDto;
import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.exception.BadRequestException;
import com.cani.repository.*;
import com.cani.service.EoEventService;
import com.cani.service.NotificationService;
import com.cani.service.ShiftLifecycleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("h2")
@DisplayName("Integration & Concurrency: Understaffed Alert Episodes & Emergency Dispatch")
class EmergencyDispatchFlowIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private EventOrganizerRepository eventOrganizerRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private DispatchProposalRepository dispatchProposalRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private ShiftLifecycleService shiftLifecycleService;
    @Autowired private EoEventService eoEventService;

    @Test
    @DisplayName("E2E: Quét thiếu người theo đợt, đọc thông báo giữ cảnh báo sự kiện, và chống bấm đúp/đồng thời")
    void fullFlow_UnderstaffedAlertsAndConcurrentEmergencyDispatch() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User eoUser = userRepository.save(User.builder()
                .userId(UUID.randomUUID())
                .email("eo-emg-" + suffix + "@test.local")
                .fullName("EO Emergency Test")
                .role(UserRole.EVENT_ORGANIZER)
                .status(UserStatus.ACTIVE)
                .build());
        EventOrganizer eo = eventOrganizerRepository.save(EventOrganizer.builder()
                .user(eoUser)
                .companyName("CANi Test Co")
                .verificationStatus(VerificationStatus.APPROVED)
                .build());

        User wUser = userRepository.save(User.builder()
                .userId(UUID.randomUUID())
                .email("wk-emg-" + suffix + "@test.local")
                .fullName("Worker Online")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build());
        workerRepository.save(Worker.builder()
                .user(wUser)
                .fullName("Worker Online")
                .kycStatus(KycStatus.APPROVED)
                .isOnline(true)
                .baseLat(10.7769)
                .baseLng(106.7009)
                .reliabilityScore(90)
                .minWage(BigDecimal.ZERO)
                .build());

        Instant now = Instant.now();
        Event ev = eventRepository.save(Event.builder()
                .eventOrganizer(eo)
                .name("Gala Khẩn Cấp " + suffix)
                .venueAddress("Quận 1, TP.HCM")
                .venueLat(10.7769)
                .venueLng(106.7009)
                .eventDate(LocalDate.now())
                .startTime(now.plus(Duration.ofHours(1)))
                .endTime(now.plus(Duration.ofHours(8)))
                .mode(EventMode.MATCHING)
                .status(EventStatus.ACTIVE)
                .totalShifts(1)
                .totalRequiredWorkers(4)
                .totalConfirmedWorkers(2)
                .build());

        Shift shift = shiftRepository.save(Shift.builder()
                .event(ev)
                .jobType("Lễ tân")
                .startTime(now.plus(Duration.ofHours(2)))
                .endTime(now.plus(Duration.ofHours(6)))
                .requiredWorkers(4)
                .confirmedWorkers(2)
                .remainingWorkers(2)
                .hourlyWage(BigDecimal.valueOf(60000))
                .locationLat(10.7769)
                .locationLng(106.7009)
                .status(ShiftStatus.ACTIVE)
                .emergency(false)
                .understaffedAlertSent(false)
                .build());

        // 1. Job chạy -> tạo đúng 1 thông báo chưa đọc trong DB
        shiftLifecycleService.evaluateShiftsAndEventsLifecycleAt(now);
        assertEquals(1L, notificationService.getUnreadCount(eoUser.getUserId()));

        // Chạy lại job -> không tạo trùng
        shiftLifecycleService.evaluateShiftsAndEventsLifecycleAt(now.plusSeconds(30));
        assertEquals(1, notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(eoUser.getUserId()).size());

        // 2. EO đọc thông báo -> giảm unreadCount về 0, nhưng cảnh báo ca trên sự kiện vẫn active
        UUID notifId = notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(eoUser.getUserId())
                .get(0).getNotificationId();
        notificationService.markAsRead(eoUser.getUserId(), notifId);
        assertEquals(0L, notificationService.getUnreadCount(eoUser.getUserId()));

        List<ShiftResponseDto> dtoList = eoEventService.getMyEvents(eoUser.getUserId()).stream()
                .filter(e -> e.getEventId().equals(ev.getEventId()))
                .findFirst().orElseThrow()
                .getShifts();
        assertEquals(1, dtoList.size());
        assertTrue(Boolean.TRUE.equals(dtoList.get(0).getNearStartUnderstaffed()));
        assertTrue(Boolean.TRUE.equals(dtoList.get(0).getCanTriggerEmergency()));
        assertEquals(2, dtoList.get(0).getMissingWorkers());

        // 3. Hai request kích hoạt khẩn cấp đồng thời -> 1 thành công, 1 bị từ chối, không trùng proposal
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try {
                    Map<String, Object> res = eoEventService.triggerEmergencyDispatchWithDetails(
                            eoUser.getUserId(), shift.getShiftId(), 25.0);
                    return "OK:" + res.get("dispatchedCount");
                } catch (BadRequestException ex) {
                    return "REJECTED:" + ex.getMessage();
                }
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : futures) {
            outcomes.add(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        long okCount = outcomes.stream().filter(o -> o.startsWith("OK:")).count();
        long rejectedCount = outcomes.stream().filter(o -> o.startsWith("REJECTED:")).count();
        assertEquals(1L, okCount, "Chỉ đúng 1 request đồng thời được phép kích hoạt: " + outcomes);
        assertEquals(1L, rejectedCount, "Request thứ 2 phải bị từ chối sạch: " + outcomes);

        List<DispatchProposal> createdProposals = dispatchProposalRepository.findByShift_ShiftId(shift.getShiftId());
        long distinctWorkers = createdProposals.stream().map(p -> p.getWorker().getWorkerId()).distinct().count();
        assertEquals(createdProposals.size(), distinctWorkers, "Không được tạo đề xuất trùng cho cùng Worker");
        assertTrue(Boolean.TRUE.equals(shiftRepository.findById(shift.getShiftId()).orElseThrow().getEmergency()));
    }
}
