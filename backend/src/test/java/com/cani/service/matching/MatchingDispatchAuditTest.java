package com.cani.service.matching;

import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.repository.*;
import com.cani.service.BlockListService;
import com.cani.service.MatchingDispatchService;
import com.cani.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingDispatchAuditTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private DispatchProposalRepository dispatchProposalRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BlockListService blockListService;

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @Mock
    private WorkerSkillRepository workerSkillRepository;

    @Mock
    private WorkerAvailabilityRepository workerAvailabilityRepository;

    @InjectMocks
    private MatchingDispatchService matchingDispatchService;

    private EventOrganizer eo;
    private Event matchingEvent;
    private Shift activeShift;
    private Worker baseWorker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(matchingDispatchService, "notificationService", notificationService);
        ReflectionTestUtils.setField(matchingDispatchService, "blockListService", blockListService);
        ReflectionTestUtils.setField(matchingDispatchService, "systemConfigRepository", systemConfigRepository);
        ReflectionTestUtils.setField(matchingDispatchService, "workerSkillRepository", workerSkillRepository);
        ReflectionTestUtils.setField(matchingDispatchService, "workerAvailabilityRepository", workerAvailabilityRepository);

        User eoUser = User.builder().userId(UUID.randomUUID()).role(UserRole.EVENT_ORGANIZER).status(UserStatus.ACTIVE).build();
        eo = EventOrganizer.builder().eoId(UUID.randomUUID()).user(eoUser).build();

        matchingEvent = Event.builder()
                .eventId(UUID.randomUUID())
                .eventOrganizer(eo)
                .name("Tech Expo")
                .mode(EventMode.MATCHING)
                .status(EventStatus.ACTIVE)
                .venueLat(10.7769)
                .venueLng(106.7009)
                .build();

        activeShift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(matchingEvent)
                .jobType("Lễ tân")
                .startTime(Instant.now().plus(24, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(28, ChronoUnit.HOURS))
                .requiredWorkers(1)
                .confirmedWorkers(0)
                .remainingWorkers(1)
                .hourlyWage(BigDecimal.valueOf(50000))
                .status(ShiftStatus.ACTIVE)
                .locationLat(10.7769)
                .locationLng(106.7009)
                .maxDistanceKm(15.0)
                .build();

        baseWorker = createEligibleWorker("Base Worker", 10.7800, 106.7020, 4.5, 5, 5, 90);
    }

    private Worker createEligibleWorker(String name, Double lat, Double lng, Double rating, Integer ratingCount, Integer completed, Integer rs) {
        User u = User.builder()
                .userId(UUID.randomUUID())
                .fullName(name)
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();
        return Worker.builder()
                .workerId(UUID.randomUUID())
                .user(u)
                .fullName(name)
                .kycStatus(KycStatus.APPROVED)
                .isOnline(true)
                .baseLat(lat)
                .baseLng(lng)
                .ratingAvg(rating != null ? BigDecimal.valueOf(rating) : null)
                .ratingCount(ratingCount)
                .totalShiftsCompleted(completed)
                .reliabilityScore(rs)
                .minWage(BigDecimal.ZERO)
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Event Mode Guards (INVITE vs MATCHING)")
    class EventModeGuardTests {

        @Test
        @DisplayName("dispatchForShift() chặn hoàn toàn Event INVITE")
        void dispatchForShift_InviteEvent_ReturnsZero() {
            matchingEvent.setMode(EventMode.INVITE);
            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));

            int count = matchingDispatchService.dispatchForShift(activeShift.getShiftId());

            assertEquals(0, count);
            verify(workerRepository, never()).findByIsOnlineTrue();
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("dispatchForEvent() bỏ qua tất cả Shift của Event INVITE")
        void dispatchForEvent_InviteEvent_ReturnsZero() {
            matchingEvent.setMode(EventMode.INVITE);
            when(shiftRepository.findByEvent_EventIdAndStatus(matchingEvent.getEventId(), ShiftStatus.ACTIVE))
                    .thenReturn(List.of(activeShift));

            int count = matchingDispatchService.dispatchForEvent(matchingEvent.getEventId());

            assertEquals(0, count);
            verify(shiftRepository, never()).findById(any());
        }

        @Test
        @DisplayName("triggerEmergencyDispatch() chặn hoàn toàn Event INVITE và không bật cờ emergency")
        void triggerEmergencyDispatch_InviteEvent_ReturnsZero() {
            matchingEvent.setMode(EventMode.INVITE);
            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));

            int count = matchingDispatchService.triggerEmergencyDispatch(activeShift.getShiftId(), 25.0);

            assertEquals(0, count);
            assertFalse(Boolean.TRUE.equals(activeShift.getEmergency()));
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("dispatchForShift() chặn Event đã CANCELLED hoặc COMPLETED")
        void dispatchForShift_CancelledEvent_ReturnsZero() {
            matchingEvent.setStatus(EventStatus.CANCELLED);
            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));

            int count = matchingDispatchService.dispatchForShift(activeShift.getShiftId());

            assertEquals(0, count);
            verify(dispatchProposalRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Hard Filter Strictness")
    class HardFilterTests {

        @Test
        @DisplayName("Worker isOnline=null bị loại (không mặc định Online)")
        void dispatchForShift_OnlineNullWorker_Rejected() {
            baseWorker.setIsOnline(null);
            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(baseWorker));

            int count = matchingDispatchService.dispatchForShift(activeShift.getShiftId());

            assertEquals(0, count);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("Fail-closed: Shift có requiredSkills nhưng workerSkillRepository=null không cho phép match")
        void dispatchForShift_RequiredSkillsWithoutRepo_RejectedSafely() {
            ReflectionTestUtils.setField(matchingDispatchService, "workerSkillRepository", null);
            activeShift.setRequiredSkills(new UUID[]{UUID.randomUUID()});

            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(baseWorker));

            int count = matchingDispatchService.dispatchForShift(activeShift.getShiftId());

            assertEquals(0, count);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("Kỹ năng đã hết hạn (expiresAt trong quá khứ) bị loại ở Hard Filter")
        void dispatchForShift_ExpiredWorkerSkill_Rejected() {
            UUID skillId = UUID.randomUUID();
            activeShift.setRequiredSkills(new UUID[]{skillId});

            WorkerSkill expiredSkill = WorkerSkill.builder()
                    .worker(baseWorker)
                    .skill(Skill.builder().skillId(skillId).name("MC").build())
                    .verificationTier(VerificationTier.AI_DOCUMENT)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();

            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(baseWorker));
            when(workerSkillRepository.findByWorker_WorkerId(baseWorker.getWorkerId())).thenReturn(List.of(expiredSkill));

            int count = matchingDispatchService.dispatchForShift(activeShift.getShiftId());

            assertEquals(0, count);
            verify(dispatchProposalRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("GPS Coordinates & Distance Validation")
    class CoordinateValidationTests {

        @Test
        @DisplayName("Shift thiếu tọa độ -> return 0 (không fallback 0.0 km)")
        void dispatchForShift_MissingShiftCoords_ReturnsZero() {
            activeShift.setLocationLat(null);
            activeShift.setLocationLng(null);
            matchingEvent.setVenueLat(null);
            matchingEvent.setVenueLng(null);

            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));

            int count = matchingDispatchService.dispatchForShift(activeShift.getShiftId());

            assertEquals(0, count);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("Worker thiếu tọa độ -> bị loại khỏi danh sách ứng viên")
        void dispatchForShift_MissingWorkerCoords_Rejected() {
            baseWorker.setBaseLat(null);
            baseWorker.setBaseLng(null);

            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(baseWorker));

            int count = matchingDispatchService.dispatchForShift(activeShift.getShiftId());

            assertEquals(0, count);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("Tọa độ vượt miền hợp lệ (lat=95, lng=200) -> bị loại")
        void dispatchForShift_OutOfBoundsCoords_Rejected() {
            baseWorker.setBaseLat(95.0);
            baseWorker.setBaseLng(200.0);

            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(baseWorker));

            int count = matchingDispatchService.dispatchForShift(activeShift.getShiftId());

            assertEquals(0, count);
            assertFalse(MatchingDispatchService.isValidCoordinate(95.0, 106.7));
            assertFalse(MatchingDispatchService.isValidCoordinate(10.7, 200.0));
            assertEquals(Double.MAX_VALUE, MatchingDispatchService.calculateDistance(95.0, 106.7, 10.7, 106.7));
        }

        @Test
        @DisplayName("Emergency Dispatch thiếu tọa độ -> không fallback 0.0 km, return 0")
        void triggerEmergencyDispatch_MissingCoords_ReturnsZero() {
            activeShift.setLocationLat(null);
            activeShift.setLocationLng(null);
            matchingEvent.setVenueLat(null);
            matchingEvent.setVenueLng(null);

            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));

            int count = matchingDispatchService.triggerEmergencyDispatch(activeShift.getShiftId(), 30.0);

            assertEquals(0, count);
            verify(dispatchProposalRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Soft Ranking & Candidate Selection Order")
    class RankingAndSelectionTests {

        @Test
        @DisplayName("Worker chưa có rating (ratingCount=0) dùng neutral 3.5 sao, không mặc định 5.0 sao")
        void softRanking_UnratedWorkerUsesNeutralPolicy() {
            Worker unrated = createEligibleWorker("New Unrated", 10.7769, 106.7009, 5.0, 0, 5, 90);
            Worker rated48 = createEligibleWorker("Rated 4.8", 10.7769, 106.7009, 4.8, 12, 5, 90);

            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(unrated, rated48));
            when(dispatchProposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            int count = matchingDispatchService.dispatchForShift(activeShift.getShiftId());
            assertEquals(2, count);

            ArgumentCaptor<DispatchProposal> captor = ArgumentCaptor.forClass(DispatchProposal.class);
            verify(dispatchProposalRepository, times(2)).save(captor.capture());

            List<DispatchProposal> saved = captor.getAllValues();
            DispatchProposal first = saved.get(0);
            DispatchProposal second = saved.get(1);

            assertEquals(rated48.getWorkerId(), first.getWorker().getWorkerId());
            assertEquals(unrated.getWorkerId(), second.getWorker().getWorkerId());
            assertTrue(first.getMatchScore().compareTo(second.getMatchScore()) > 0);
            assertTrue(second.getScoreBreakdown().contains("\"ratingScore\":70.0"));
        }

        @Test
        @DisplayName("idleWorkerBoost chỉ cộng khi không hoạt động >= 14 ngày, không cộng cho worker vừa đi làm")
        void softRanking_IdleBoostRequiresRealInactivity() {
            Worker activeRecently = createEligibleWorker("Recent Worker", 10.7769, 106.7009, 4.5, 5, 5, 90);
            Worker trulyIdle = createEligibleWorker("Idle Worker", 10.7769, 106.7009, 4.5, 5, 5, 90);

            ShiftAssignment recentAssignment = ShiftAssignment.builder()
                    .worker(activeRecently)
                    .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                    .build();
            ShiftAssignment oldAssignment = ShiftAssignment.builder()
                    .worker(trulyIdle)
                    .createdAt(Instant.now().minus(20, ChronoUnit.DAYS))
                    .build();

            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(activeRecently, trulyIdle));
            when(shiftAssignmentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(activeRecently.getWorkerId()))
                    .thenReturn(List.of(recentAssignment));
            when(shiftAssignmentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(trulyIdle.getWorkerId()))
                    .thenReturn(List.of(oldAssignment));
            when(dispatchProposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            matchingDispatchService.dispatchForShift(activeShift.getShiftId());

            ArgumentCaptor<DispatchProposal> captor = ArgumentCaptor.forClass(DispatchProposal.class);
            verify(dispatchProposalRepository, times(2)).save(captor.capture());

            DispatchProposal first = captor.getAllValues().get(0);
            DispatchProposal second = captor.getAllValues().get(1);

            assertEquals(trulyIdle.getWorkerId(), first.getWorker().getWorkerId());
            assertTrue(first.getScoreBreakdown().contains("\"idleBonus\":10.0"));
            assertTrue(second.getScoreBreakdown().contains("\"idleBonus\":0.0"));
        }

        @Test
        @DisplayName("Verified skill còn hạn cộng +10.0, SELF_DECLARED không cộng bonus")
        void softRanking_VerifiedSkillBonusAppliedProperly() {
            UUID reqSkill = UUID.randomUUID();
            activeShift.setRequiredSkills(new UUID[]{reqSkill});

            Worker verifiedWorker = createEligibleWorker("Verified", 10.7769, 106.7009, 4.5, 5, 5, 90);
            Worker selfDeclaredWorker = createEligibleWorker("SelfDeclared", 10.7769, 106.7009, 4.5, 5, 5, 90);

            Skill skill = Skill.builder().skillId(reqSkill).name("Pha chế").build();
            when(workerSkillRepository.findByWorker_WorkerId(verifiedWorker.getWorkerId())).thenReturn(List.of(
                    WorkerSkill.builder().worker(verifiedWorker).skill(skill).verificationTier(VerificationTier.PLATFORM_PROVEN).build()
            ));
            when(workerSkillRepository.findByWorker_WorkerId(selfDeclaredWorker.getWorkerId())).thenReturn(List.of(
                    WorkerSkill.builder().worker(selfDeclaredWorker).skill(skill).verificationTier(VerificationTier.SELF_DECLARED).build()
            ));
            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(selfDeclaredWorker, verifiedWorker));
            when(dispatchProposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            matchingDispatchService.dispatchForShift(activeShift.getShiftId());

            ArgumentCaptor<DispatchProposal> captor = ArgumentCaptor.forClass(DispatchProposal.class);
            verify(dispatchProposalRepository, times(2)).save(captor.capture());

            DispatchProposal first = captor.getAllValues().get(0);
            DispatchProposal second = captor.getAllValues().get(1);
            assertEquals(verifiedWorker.getWorkerId(), first.getWorker().getWorkerId());
            assertTrue(first.getScoreBreakdown().contains("\"skillBonus\":10.0"));
            assertTrue(second.getScoreBreakdown().contains("\"skillBonus\":0.0"));
        }

        @Test
        @DisplayName("Loại Worker đã có Proposal/Assignment trước khi cắt Top N -> ứng viên hợp lệ kế tiếp được đôn lên")
        void selectionOrder_ExcludesDuplicatesBeforeLimitingTopN() {
            // needed = 1 => targetDispatchCount = 1*2 + 3 = 5. Create 7 workers, top 5 already have proposal/assignment!
            List<Worker> workers = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                Worker w = createEligibleWorker("W" + i, 10.7769, 106.7009, 4.9 - (i * 0.1), 10, 5, 90);
                workers.add(w);
                if (i < 3) {
                    lenient().when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(
                            eq(activeShift.getShiftId()), eq(w.getWorkerId()), any())).thenReturn(true);
                } else if (i < 5) {
                    lenient().when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(
                            activeShift.getShiftId(), w.getWorkerId())).thenReturn(true);
                }
            }

            when(shiftRepository.findById(activeShift.getShiftId())).thenReturn(Optional.of(activeShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(workers);
            when(dispatchProposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            int count = matchingDispatchService.dispatchForShift(activeShift.getShiftId());

            // 2 remaining eligible workers (W5, W6) must BOTH receive proposals instead of getting dropped by premature limit(5)
            assertEquals(2, count);
            verify(dispatchProposalRepository, times(2)).save(any());
        }
    }
}
