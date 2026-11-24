package com.cani.service.eo;

import com.cani.dto.admin.noshow.NoShowScanResultDto;
import com.cani.dto.event.ShiftResponseDto;
import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.exception.BadRequestException;
import com.cani.repository.*;
import com.cani.service.EoEventService;
import com.cani.service.NoShowService;
import com.cani.service.NotificationService;
import com.cani.service.ShiftLifecycleService;
import com.cani.service.WorkerJobService;
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
    @Autowired private ShiftAssignmentRepository shiftAssignmentRepository;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private DispatchProposalRepository dispatchProposalRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private ShiftLifecycleService shiftLifecycleService;
    @Autowired private EoEventService eoEventService;
    @Autowired private NoShowService noShowService;
    @Autowired private WorkerJobService workerJobService;

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

    @Test
    @DisplayName("Auto No-show dùng bán kính khẩn cấp mở rộng (20km > 15km), giữ hard filter & chống trùng lặp")
    void noShowAutoDispatch_ExpandsToEmergencyRadius_KeepsEligibilityAndPreventsDuplicates() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User eoUser = saveUser("eo-ns-" + suffix, UserRole.EVENT_ORGANIZER);
        EventOrganizer eo = eventOrganizerRepository.save(EventOrganizer.builder()
                .user(eoUser).companyName("EO NoShow Co").verificationStatus(VerificationStatus.APPROVED).build());

        // Worker 1 đã chốt nhưng sẽ không check-in -> NO_SHOW
        User w1User = saveUser("wk-ns1-" + suffix, UserRole.WORKER);
        Worker wNoShow = saveOnlineWorker(w1User, "Worker NoShow", KycStatus.APPROVED, 10.7769, 106.7009);

        // Worker 2 hợp lệ cách venue ~20.0 km (ngoài 15km thường, trong 25km khẩn cấp)
        User w2User = saveUser("wk-ns2-" + suffix, UserRole.WORKER);
        Worker wExtended = saveOnlineWorker(w2User, "Worker Extended 20km", KycStatus.APPROVED, 10.9570, 106.7009);

        // Worker 3 ở gần (2km) nhưng chưa eKYC -> phải bị hard filter loại
        User w3User = saveUser("wk-ns3-" + suffix, UserRole.WORKER);
        Worker wIneligible = saveOnlineWorker(w3User, "Worker Unverified", KycStatus.PENDING, 10.7850, 106.7009);

        Instant now = Instant.now();
        Event ev = eventRepository.save(Event.builder()
                .eventOrganizer(eo)
                .name("Event NoShow Emergency " + suffix)
                .venueAddress("Quận 1, TP.HCM")
                .venueLat(10.7769).venueLng(106.7009)
                .eventDate(LocalDate.now())
                .startTime(now.minus(Duration.ofHours(1)))
                .endTime(now.plus(Duration.ofHours(5)))
                .mode(EventMode.MATCHING)
                .status(EventStatus.ONGOING)
                .totalShifts(1).totalRequiredWorkers(1).totalConfirmedWorkers(1)
                .build());

        // DB status cố tình để ACTIVE (chưa sync IN_PROGRESS) dù startTime đã qua 35 phút
        Shift shift = shiftRepository.save(Shift.builder()
                .event(ev)
                .jobType("Điều phối")
                .startTime(now.minus(Duration.ofMinutes(35)))
                .endTime(now.plus(Duration.ofHours(3)))
                .requiredWorkers(1)
                .confirmedWorkers(1)
                .remainingWorkers(0)
                .maxDistanceKm(15.0)
                .hourlyWage(BigDecimal.valueOf(60000))
                .locationLat(10.7769).locationLng(106.7009)
                .status(ShiftStatus.FULLY_STAFFED)
                .emergency(false)
                .build());

        ShiftAssignment sa = shiftAssignmentRepository.save(ShiftAssignment.builder()
                .shift(shift).worker(wNoShow).source(AssignmentSource.MATCHING)
                .status(AssignmentStatus.CONFIRMED).eoConfirmed(false).build());

        // 1. Auto scan phát hiện quá 30 phút -> mark NO_SHOW, bật emergency và tìm được wExtended (20km)
        NoShowScanResultDto scan1 = noShowService.scanAndProcessNoShows();
        assertTrue(scan1.getTotalMarkedNoShow() >= 1);

        Shift updatedShift = shiftRepository.findById(shift.getShiftId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(updatedShift.getEmergency()));
        assertEquals(ShiftStatus.IN_PROGRESS, updatedShift.getStatus());

        List<DispatchProposal> proposals = dispatchProposalRepository.findByShift_ShiftId(shift.getShiftId());
        assertFalse(proposals.isEmpty());
        assertTrue(proposals.stream().anyMatch(p -> p.getWorker().getWorkerId().equals(wExtended.getWorkerId())),
                "Ứng viên nằm ngoài bán kính thường (15km) nhưng trong bán kính khẩn cấp (25km) phải nhận được đề xuất");
        assertTrue(proposals.stream().noneMatch(p -> p.getWorker().getWorkerId().equals(wIneligible.getWorkerId())),
                "Ứng viên chưa đạt điều kiện (chưa eKYC) vẫn phải bị lọc bỏ");

        long eoNotifCountAfterFirst = notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(eoUser.getUserId()).size();
        assertEquals(1L, eoNotifCountAfterFirst);

        int proposalCountAfterFirst = proposals.size();

        // 2. Xử lý lại cùng sự cố (cron chạy lại + EO bấm thủ công + markNoShow lại) -> không tạo trùng & chặn manual
        noShowService.scanAndProcessNoShows();
        assertThrows(BadRequestException.class,
                () -> noShowService.markNoShow(sa.getAssignmentId(), NoShowDetectedBy.EVENT_ORGANIZER, eoUser.getUserId(), "Thử lại"));
        assertThrows(BadRequestException.class,
                () -> eoEventService.triggerEmergencyDispatchWithDetails(eoUser.getUserId(), shift.getShiftId(), 25.0));

        assertEquals(proposalCountAfterFirst, dispatchProposalRepository.findByShift_ShiftId(shift.getShiftId()).size());
        assertEquals(eoNotifCountAfterFirst, notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(eoUser.getUserId()).size());
    }

    @Test
    @DisplayName("Worker tự hủy: thiếu người -> bật emergency + cảnh báo EO + accept đủ người thì tự tắt; vẫn đủ người thì không bật")
    void workerCancelFlow_SyncsEmergencyBannerAndResetsWhenRefilled() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User eoUser = saveUser("eo-cn-" + suffix, UserRole.EVENT_ORGANIZER);
        EventOrganizer eo = eventOrganizerRepository.save(EventOrganizer.builder()
                .user(eoUser).companyName("EO Cancel Co").verificationStatus(VerificationStatus.APPROVED).build());

        User w1User = saveUser("wk-cn1-" + suffix, UserRole.WORKER);
        Worker w1 = saveOnlineWorker(w1User, "Worker Canceller", KycStatus.APPROVED, 10.7769, 106.7009);

        User w2User = saveUser("wk-cn2-" + suffix, UserRole.WORKER);
        Worker w2 = saveOnlineWorker(w2User, "Worker Replacement", KycStatus.APPROVED, 10.9570, 106.7009); // ~20km

        Instant now = Instant.now();
        Event ev = eventRepository.save(Event.builder()
                .eventOrganizer(eo)
                .name("Event Cancel Sync " + suffix)
                .venueAddress("Quận 1, TP.HCM")
                .venueLat(10.7769).venueLng(106.7009)
                .eventDate(LocalDate.now())
                .startTime(now.plus(Duration.ofHours(6)))
                .endTime(now.plus(Duration.ofHours(10)))
                .mode(EventMode.MATCHING)
                .status(EventStatus.ACTIVE)
                .totalShifts(2).totalRequiredWorkers(2).totalConfirmedWorkers(3)
                .build());

        // Ca A: cần 1, đang chốt 1 -> Worker hủy làm thiếu 0/1 -> bật emergency
        Shift understaffShift = shiftRepository.save(Shift.builder()
                .event(ev).jobType("Lễ tân").startTime(now.plus(Duration.ofHours(6))).endTime(now.plus(Duration.ofHours(10)))
                .requiredWorkers(1).confirmedWorkers(1).remainingWorkers(0)
                .maxDistanceKm(15.0).hourlyWage(BigDecimal.valueOf(60000))
                .locationLat(10.7769).locationLng(106.7009)
                .status(ShiftStatus.FULLY_STAFFED).emergency(false).build());
        ShiftAssignment saA = shiftAssignmentRepository.save(ShiftAssignment.builder()
                .shift(understaffShift).worker(w1).source(AssignmentSource.MATCHING)
                .status(AssignmentStatus.CONFIRMED).eoConfirmed(false).build());

        // Ca B: cần 1 nhưng có 2 confirmed (khung giờ khác Ca A để không trùng lịch w2) -> Worker hủy vẫn còn 1/1 -> KHÔNG bật emergency
        Shift stillFullShift = shiftRepository.save(Shift.builder()
                .event(ev).jobType("Hậu cần").startTime(now.plus(Duration.ofHours(12))).endTime(now.plus(Duration.ofHours(16)))
                .requiredWorkers(1).confirmedWorkers(2).remainingWorkers(0)
                .maxDistanceKm(15.0).hourlyWage(BigDecimal.valueOf(60000))
                .locationLat(10.7769).locationLng(106.7009)
                .status(ShiftStatus.FULLY_STAFFED).emergency(false).build());
        ShiftAssignment saB = shiftAssignmentRepository.save(ShiftAssignment.builder()
                .shift(stillFullShift).worker(w1).source(AssignmentSource.MATCHING)
                .status(AssignmentStatus.CONFIRMED).eoConfirmed(false).build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .shift(stillFullShift).worker(w2).source(AssignmentSource.MATCHING)
                .status(AssignmentStatus.CONFIRMED).eoConfirmed(false).build());

        // 1. Hủy ca B (vẫn đủ người) -> emergency = false, không dispatch
        workerJobService.cancelAssignment(w1User.getUserId(), saB.getAssignmentId(), "Bận đột xuất");
        Shift reloadedB = shiftRepository.findById(stillFullShift.getShiftId()).orElseThrow();
        assertFalse(Boolean.TRUE.equals(reloadedB.getEmergency()));
        assertTrue(dispatchProposalRepository.findByShift_ShiftId(stillFullShift.getShiftId()).isEmpty());

        // 2. Hủy ca A (làm thiếu người) -> emergency = true, API trả nearStartUnderstaffed=true, canTriggerEmergency=false
        workerJobService.cancelAssignment(w1User.getUserId(), saA.getAssignmentId(), "Ốm đột xuất");
        Shift reloadedA = shiftRepository.findById(understaffShift.getShiftId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(reloadedA.getEmergency()));

        List<DispatchProposal> proposalsA = dispatchProposalRepository.findByShift_ShiftId(understaffShift.getShiftId());
        assertFalse(proposalsA.isEmpty());
        DispatchProposal w2Proposal = proposalsA.stream()
                .filter(p -> p.getWorker().getWorkerId().equals(w2.getWorkerId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Ứng viên thay thế hợp lệ phải nhận được đề xuất khẩn cấp"));

        ShiftResponseDto dtoAfterCancel = eoEventService.getMyEvents(eoUser.getUserId()).stream()
                .filter(e -> e.getEventId().equals(ev.getEventId())).findFirst().orElseThrow()
                .getShifts().stream().filter(s -> s.getShiftId().equals(understaffShift.getShiftId())).findFirst().orElseThrow();
        assertTrue(Boolean.TRUE.equals(dtoAfterCancel.getEmergency()));
        assertTrue(Boolean.TRUE.equals(dtoAfterCancel.getNearStartUnderstaffed()));
        assertFalse(Boolean.TRUE.equals(dtoAfterCancel.getCanTriggerEmergency()));
        assertTrue(dtoAfterCancel.getPendingProposalCount() >= 1);

        // EO không được kích hoạt trùng
        assertThrows(BadRequestException.class,
                () -> eoEventService.triggerEmergencyDispatchWithDetails(eoUser.getUserId(), understaffShift.getShiftId(), 25.0));

        // 3. Worker 2 nhận ca -> bù đủ -> tự động tắt emergency & gỡ cảnh báo
        workerJobService.acceptProposal(w2User.getUserId(), w2Proposal.getProposalId());
        ShiftResponseDto dtoAfterRefill = eoEventService.getMyEvents(eoUser.getUserId()).stream()
                .filter(e -> e.getEventId().equals(ev.getEventId())).findFirst().orElseThrow()
                .getShifts().stream().filter(s -> s.getShiftId().equals(understaffShift.getShiftId())).findFirst().orElseThrow();
        assertFalse(Boolean.TRUE.equals(dtoAfterRefill.getEmergency()));
        assertFalse(Boolean.TRUE.equals(dtoAfterRefill.getNearStartUnderstaffed()));
        assertEquals(0, dtoAfterRefill.getMissingWorkers());
        assertEquals(ShiftStatus.FULLY_STAFFED, dtoAfterRefill.getStatus());
    }

    @Test
    @DisplayName("Ranh giới thời gian thực tế: trước start, trong ca >= 30p (DB chưa sync), sau start <30p, đã kết thúc và đã hủy")
    void timeBoundaries_ManualAllowedInProgressWithMinRemaining_AutoStopsWhenEndedCancelledOrTooShort() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User eoUser = saveUser("eo-tb-" + suffix, UserRole.EVENT_ORGANIZER);
        EventOrganizer eo = eventOrganizerRepository.save(EventOrganizer.builder()
                .user(eoUser).companyName("EO Time Co").verificationStatus(VerificationStatus.APPROVED).build());
        User wUser = saveUser("wk-tb-" + suffix, UserRole.WORKER);
        Worker w = saveOnlineWorker(wUser, "Worker Time", KycStatus.APPROVED, 10.7769, 106.7009);

        Instant now = Instant.now();
        Event ev = eventRepository.save(Event.builder()
                .eventOrganizer(eo).name("Event Time Boundary " + suffix)
                .venueAddress("Quận 1, TP.HCM").venueLat(10.7769).venueLng(106.7009)
                .eventDate(LocalDate.now()).startTime(now.minus(Duration.ofHours(3))).endTime(now.plus(Duration.ofHours(6)))
                .mode(EventMode.MATCHING).status(EventStatus.ONGOING)
                .totalShifts(4).totalRequiredWorkers(8).totalConfirmedWorkers(2)
                .build());

        // 1. Ngay trước startTime (+5m) -> manual trigger thành công
        Shift beforeStart = shiftRepository.save(Shift.builder()
                .event(ev).jobType("Trước giờ").startTime(now.plus(Duration.ofMinutes(5))).endTime(now.plus(Duration.ofHours(4)))
                .requiredWorkers(2).confirmedWorkers(0).remainingWorkers(2)
                .hourlyWage(BigDecimal.valueOf(50000)).locationLat(10.7769).locationLng(106.7009)
                .status(ShiftStatus.ACTIVE).emergency(false).build());
        Map<String, Object> beforeRes = eoEventService.triggerEmergencyDispatchWithDetails(eoUser.getUserId(), beforeStart.getShiftId(), 25.0);
        assertEquals(true, beforeRes.get("emergency"));

        // 2. Sau giờ bắt đầu nhưng còn >= 30 phút (dù DB status còn ACTIVE) -> manual trigger thành công & đồng bộ IN_PROGRESS
        Shift atOrAfterStartDbStale = shiftRepository.save(Shift.builder()
                .event(ev).jobType("Vào ca DB cũ").startTime(now.minus(Duration.ofSeconds(10))).endTime(now.plus(Duration.ofHours(3)))
                .requiredWorkers(2).confirmedWorkers(0).remainingWorkers(2)
                .hourlyWage(BigDecimal.valueOf(50000)).locationLat(10.7769).locationLng(106.7009)
                .status(ShiftStatus.ACTIVE).emergency(false).build());
        Map<String, Object> inProgressRes = eoEventService.triggerEmergencyDispatchWithDetails(eoUser.getUserId(), atOrAfterStartDbStale.getShiftId(), 25.0);
        assertEquals(true, inProgressRes.get("emergency"));
        Shift syncedInProgress = shiftRepository.findById(atOrAfterStartDbStale.getShiftId()).orElseThrow();
        assertEquals(ShiftStatus.IN_PROGRESS, syncedInProgress.getStatus());

        // 3. Sau giờ bắt đầu chỉ còn 15 phút (< 30p) -> cả manual và no-show auto emergency dispatch đều bị chặn
        Shift shortRemaining = shiftRepository.save(Shift.builder()
                .event(ev).jobType("Sắp hết ca").startTime(now.minus(Duration.ofHours(2))).endTime(now.plus(Duration.ofMinutes(15)))
                .requiredWorkers(2).confirmedWorkers(1).remainingWorkers(1)
                .hourlyWage(BigDecimal.valueOf(50000)).locationLat(10.7769).locationLng(106.7009)
                .status(ShiftStatus.IN_PROGRESS).emergency(false).build());
        assertThrows(BadRequestException.class,
                () -> eoEventService.triggerEmergencyDispatchWithDetails(eoUser.getUserId(), shortRemaining.getShiftId(), 25.0));
        ShiftAssignment saShort = shiftAssignmentRepository.save(ShiftAssignment.builder()
                .shift(shortRemaining).worker(w).source(AssignmentSource.MATCHING)
                .status(AssignmentStatus.CONFIRMED).eoConfirmed(false).build());
        var shortNoShow = noShowService.markNoShow(saShort.getAssignmentId(), NoShowDetectedBy.SYSTEM_CRON, null, "Quá hạn");
        assertFalse(Boolean.TRUE.equals(shortNoShow.getEmergencyDispatched()));
        assertFalse(Boolean.TRUE.equals(shiftRepository.findById(shortRemaining.getShiftId()).orElseThrow().getEmergency()));

        // 4. Đã kết thúc (DB status vẫn IN_PROGRESS) hoặc CANCELLED -> cả manual và auto đều dừng
        Shift staleEnded = shiftRepository.save(Shift.builder()
                .event(ev).jobType("Đã kết thúc").startTime(now.minus(Duration.ofHours(4))).endTime(now.minus(Duration.ofMinutes(5)))
                .requiredWorkers(2).confirmedWorkers(0).remainingWorkers(2)
                .hourlyWage(BigDecimal.valueOf(50000)).locationLat(10.7769).locationLng(106.7009)
                .status(ShiftStatus.IN_PROGRESS).emergency(false).build());
        assertThrows(BadRequestException.class,
                () -> eoEventService.triggerEmergencyDispatchWithDetails(eoUser.getUserId(), staleEnded.getShiftId(), 25.0));

        Shift cancelled = shiftRepository.save(Shift.builder()
                .event(ev).jobType("Đã hủy").startTime(now.plus(Duration.ofHours(1))).endTime(now.plus(Duration.ofHours(4)))
                .requiredWorkers(2).confirmedWorkers(0).remainingWorkers(2)
                .hourlyWage(BigDecimal.valueOf(50000)).locationLat(10.7769).locationLng(106.7009)
                .status(ShiftStatus.CANCELLED).emergency(false).build());
        assertThrows(BadRequestException.class,
                () -> eoEventService.triggerEmergencyDispatchWithDetails(eoUser.getUserId(), cancelled.getShiftId(), 25.0));
    }

    private User saveUser(String prefix, UserRole role) {
        return userRepository.save(User.builder()
                .userId(UUID.randomUUID())
                .email(prefix + "@test.local")
                .fullName(prefix)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private Worker saveOnlineWorker(User user, String fullName, KycStatus kycStatus, double lat, double lng) {
        return workerRepository.save(Worker.builder()
                .user(user)
                .fullName(fullName)
                .kycStatus(kycStatus)
                .isOnline(true)
                .baseLat(lat)
                .baseLng(lng)
                .reliabilityScore(90)
                .minWage(BigDecimal.ZERO)
                .build());
    }
}
