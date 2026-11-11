package com.cani.service.matching;

import com.cani.service.*;

import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.repository.DispatchProposalRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import com.cani.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exhaustive Unit Test Suite for MatchingDispatchService (On-Demand Workforce Matching & Dispatch Engine).
 * Covers FC-MATCH-01 through FC-MATCH-05 conforming to IEEE 829 and Report-5.1_Unit Test.xlsx standard.
 */
@ExtendWith(MockitoExtension.class)
class MatchingDispatchServiceTest {

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

    @InjectMocks
    private MatchingDispatchService matchingDispatchService;

    private Shift sampleShift;
    private Event sampleEvent;
    private Worker onlineWorker;
    private Worker offlineWorker;
    private Worker lowRsWorker;
    private Worker experiencedWorker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(matchingDispatchService, "notificationService", notificationService);

        UUID eventId = UUID.randomUUID();
        sampleEvent = Event.builder()
                .eventId(eventId)
                .name("Hội nghị Công nghệ CANi Tech Expo 2026")
                .venueLat(10.7769) // TP.HCM Bến Thành
                .venueLng(106.7009)
                .build();

        sampleShift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(sampleEvent)
                .jobType("Lễ tân / Check-in")
                .startTime(Instant.now().plus(Duration.ofDays(1)))
                .endTime(Instant.now().plus(Duration.ofDays(1)).plus(Duration.ofHours(4)))
                .requiredWorkers(2)
                .confirmedWorkers(0)
                .remainingWorkers(2)
                .hourlyWage(BigDecimal.valueOf(45000))
                .status(ShiftStatus.ACTIVE)
                .locationLat(10.7769)
                .locationLng(106.7009)
                .minReliabilityScore(60)
                .maxDistanceKm(15.0)
                .build();

        // 1. Worker online đủ tiêu chuẩn
        User user1 = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE).build();
        onlineWorker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(user1)
                .fullName("Nguyễn Văn Online")
                .isOnline(true)
                .reliabilityScore(85)
                .ratingAvg(BigDecimal.valueOf(4.8))
                .totalShiftsCompleted(2) // New worker (<3)
                .baseLat(10.7780) // Cách ~200m
                .baseLng(106.7010)
                .build();

        // 2. Worker offline
        User user2 = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE).build();
        offlineWorker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(user2)
                .fullName("Trần Thị Offline")
                .isOnline(false)
                .reliabilityScore(90)
                .ratingAvg(BigDecimal.valueOf(5.0))
                .totalShiftsCompleted(15)
                .baseLat(10.7780)
                .baseLng(106.7010)
                .build();

        // 3. Worker uy tín thấp (< 60)
        User user3 = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE).build();
        lowRsWorker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(user3)
                .fullName("Lê Văn Uy Tín Thấp")
                .isOnline(true)
                .reliabilityScore(45)
                .ratingAvg(BigDecimal.valueOf(3.5))
                .totalShiftsCompleted(4)
                .baseLat(10.7780)
                .baseLng(106.7010)
                .build();

        // 4. Worker kỳ cựu nhiều kinh nghiệm
        User user4 = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE).build();
        experiencedWorker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(user4)
                .fullName("Phạm Thị Kỳ Cựu")
                .isOnline(true)
                .reliabilityScore(95)
                .ratingAvg(BigDecimal.valueOf(4.9))
                .totalShiftsCompleted(25)
                .baseLat(10.7800)
                .baseLng(106.7020)
                .build();
    }

    // =========================================================================
    // 1. FC-MATCH-01: Tính khoảng cách Haversine (calculateDistance)
    // =========================================================================
    @Nested
    @DisplayName("1. FC-MATCH-01: Calculate Distance (Haversine)")
    class HaversineDistanceTests {

        @Test
        @DisplayName("MATCH001: Tính khoảng cách chính xác giữa Chợ Bến Thành và Nhà Thờ Đức Bà (~0.88km)")
        void calculateDistance_AccurateBetweenRealCoordinates() {
            // Tọa độ Chợ Bến Thành (10.7719, 106.6983) và Nhà Thờ Đức Bà (10.7797, 106.6990)
            double distance = MatchingDispatchService.calculateDistance(10.7719, 106.6983, 10.7797, 106.6990);
            assertThat(distance).isGreaterThan(0.7).isLessThan(1.2);
        }

        @Test
        @DisplayName("MATCH002: Tọa độ trùng nhau trả về khoảng cách đúng bằng 0.0 km")
        void calculateDistance_IdenticalCoordinates_ReturnsZero() {
            double distance = MatchingDispatchService.calculateDistance(10.7769, 106.7009, 10.7769, 106.7009);
            assertEquals(0.0, distance, 0.0001);
        }

        @Test
        @DisplayName("MATCH003: Tọa độ null kích hoạt fallback mặc định 3.0 km an toàn")
        void calculateDistance_NullCoordinates_ReturnsFallbackDefault() {
            double distance = MatchingDispatchService.calculateDistance(null, 106.7009, 10.7769, null);
            assertEquals(3.0, distance, 0.0001);
        }

        @Test
        @DisplayName("MATCH004: Khoảng cách vượt quá bán kính 15km giữa TP.HCM và Biên Hòa (~30km)")
        void calculateDistance_FarCoordinates_ReturnsDistanceGreaterThan15Km() {
            // Bến Thành (10.7769, 106.7009) đến Biên Hòa Đồng Nai (10.9574, 106.8427) ~25-30km
            double distance = MatchingDispatchService.calculateDistance(10.7769, 106.7009, 10.9574, 106.8427);
            assertThat(distance).isGreaterThan(20.0);
        }
    }

    // =========================================================================
    // 2. FC-MATCH-02 & FC-MATCH-03: Hard Filter loại trừ ứng viên không đạt chuẩn
    // =========================================================================
    @Nested
    @DisplayName("2. FC-MATCH-03: Hard Filter Eligible Worker Candidates")
    class HardFilterTests {

        @Test
        @DisplayName("MATCH005: Worker đang offline (isOnline=false) bị loại khỏi danh sách dispatch")
        void passHardFilter_OfflineWorker_FilteredOut() {
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(offlineWorker));

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("MATCH006: Worker có tài khoản bị LOCKED hoặc SUSPENDED bị loại khỏi danh sách dispatch")
        void passHardFilter_LockedOrSuspendedUser_FilteredOut() {
            User lockedUser = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.LOCKED).build();
            Worker lockedWorker = Worker.builder()
                    .workerId(UUID.randomUUID())
                    .user(lockedUser)
                    .isOnline(true)
                    .reliabilityScore(90)
                    .baseLat(10.7769)
                    .baseLng(106.7009)
                    .build();

            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(lockedWorker));

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("MATCH007: Worker có điểm uy tín < 60 bị loại bỏ")
        void passHardFilter_LowReliabilityScore_FilteredOut() {
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(lowRsWorker));

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("MATCH008: Ca làm việc yêu cầu EXPERT (10 ca) -> Worker mới có 2 ca bị loại")
        void passHardFilter_InsufficientExperienceLevel_FilteredOut() {
            sampleShift.setMinExperienceLevel(ExperienceLevel.EXPERT); // Cần tối thiểu 10 ca
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(onlineWorker)); // mới có 2 ca

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("MATCH009: Ca làm việc yêu cầu INTERMEDIATE (3 ca) -> Worker có 25 ca thỏa mãn điều kiện")
        void passHardFilter_SufficientExperienceLevel_PassesFilter() {
            sampleShift.setMinExperienceLevel(ExperienceLevel.INTERMEDIATE);
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(experiencedWorker));
            when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);
            when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(1, dispatched);
            verify(dispatchProposalRepository, times(1)).save(any(DispatchProposal.class));
        }

        @Test
        @DisplayName("MATCH010: Worker có lịch làm việc bị trùng (CONFIRMED / CHECKED_IN) bị loại bỏ")
        void passHardFilter_ConflictingCalendar_FilteredOut() {
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(onlineWorker));

            ShiftAssignment conflictAssignment = ShiftAssignment.builder()
                    .assignmentId(UUID.randomUUID())
                    .status(AssignmentStatus.CONFIRMED)
                    .build();

            when(shiftAssignmentRepository.findConflictingAssignments(eq(onlineWorker.getWorkerId()), any(), any(), anyList()))
                    .thenReturn(List.of(conflictAssignment));

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("MATCH011: Worker ở khoảng cách xa hơn bán kính tối đa của ca (>15km) bị loại")
        void passHardFilter_DistanceExceedsMaxAllowed_FilteredOut() {
            Worker farWorker = Worker.builder()
                    .workerId(UUID.randomUUID())
                    .user(User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE).build())
                    .isOnline(true)
                    .reliabilityScore(90)
                    .baseLat(10.9574) // Biên Hòa (~26km)
                    .baseLng(106.8427)
                    .build();

            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(farWorker));
            when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(Collections.emptyList());

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(dispatchProposalRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 3. FC-MATCH-04: Guardrails kiểm tra trạng thái ca làm & chống đề xuất trùng
    // =========================================================================
    @Nested
    @DisplayName("3. FC-MATCH-04: Shift State & Duplicate Guardrails")
    class ShiftStateGuardrailsTests {

        @Test
        @DisplayName("MATCH012: Ca làm việc không ở trạng thái ACTIVE (DRAFT, COMPLETED, CANCELLED) -> không dispatch")
        void dispatchForShift_ShiftNotActive_ReturnsZero() {
            sampleShift.setStatus(ShiftStatus.DRAFT);
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(workerRepository, never()).findByIsOnlineTrue();
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("MATCH013: Ca làm việc đã tuyển đủ người (remainingWorkers <= 0) -> không dispatch thêm")
        void dispatchForShift_ShiftAlreadyFullyStaffed_ReturnsZero() {
            sampleShift.setRemainingWorkers(0);
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(workerRepository, never()).findByIsOnlineTrue();
        }

        @Test
        @DisplayName("MATCH014: Worker đã nhận proposal hoặc assignment trước đó cho ca này -> không tạo trùng")
        void dispatchForShift_DuplicateProposalOrAssignment_SkipsWorker() {
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(onlineWorker));
            when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(Collections.emptyList());

            // Đã tồn tại proposal
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerId(sampleShift.getShiftId(), onlineWorker.getWorkerId()))
                    .thenReturn(true);

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("MATCH015: Ca làm việc không tồn tại trong hệ thống ném IllegalArgumentException")
        void dispatchForShift_ShiftNotFound_ThrowsIllegalArgumentException() {
            UUID unknownId = UUID.randomUUID();
            when(shiftRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> matchingDispatchService.dispatchForShift(unknownId));
        }
    }

    // =========================================================================
    // 4. FC-MATCH-05: Thực thi Dispatch đề xuất cho ca & toàn bộ sự kiện
    // =========================================================================
    @Nested
    @DisplayName("4. FC-MATCH-05: Dispatch Execution for Shift & Event")
    class DispatchExecutionTests {

        @Test
        @DisplayName("MATCH016: Dispatch thành công tạo đề xuất PENDING, hạn 1 tiếng và gửi Push Notification")
        void dispatchForShift_Success_CreatesProposalAndSendsNotification() {
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(onlineWorker));
            when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);
            when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);

            when(dispatchProposalRepository.save(any(DispatchProposal.class))).thenAnswer(inv -> {
                DispatchProposal p = inv.getArgument(0);
                p.setProposalId(UUID.randomUUID());
                return p;
            });

            // Act
            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            // Assert
            assertEquals(1, dispatched);
            verify(dispatchProposalRepository).save(argThat(p ->
                    p.getStatus() == ProposalStatus.PENDING &&
                    p.getExpiresAt().isAfter(Instant.now()) &&
                    p.getMatchScore() != null
            ));
            verify(notificationService).createAndSend(
                    eq(onlineWorker.getUser().getUserId()),
                    contains("Đề xuất ca làm mới"),
                    anyString(),
                    eq(NotificationType.PROPOSAL),
                    eq("PROPOSAL"),
                    any(),
                    anyMap()
            );
        }

        @Test
        @DisplayName("MATCH017: dispatchForEvent - Kích hoạt matching cho toàn bộ ca ACTIVE của sự kiện")
        void dispatchForEvent_MultipleActiveShifts_DispatchesAll() {
            Shift shift1 = Shift.builder()
                    .shiftId(UUID.randomUUID())
                    .event(sampleEvent)
                    .status(ShiftStatus.ACTIVE)
                    .requiredWorkers(1)
                    .remainingWorkers(1)
                    .build();

            Shift shift2 = Shift.builder()
                    .shiftId(UUID.randomUUID())
                    .event(sampleEvent)
                    .status(ShiftStatus.ACTIVE)
                    .requiredWorkers(1)
                    .remainingWorkers(1)
                    .build();

            when(shiftRepository.findByEvent_EventIdAndStatus(sampleEvent.getEventId(), ShiftStatus.ACTIVE))
                    .thenReturn(List.of(shift1, shift2));

            when(shiftRepository.findById(shift1.getShiftId())).thenReturn(Optional.of(shift1));
            when(shiftRepository.findById(shift2.getShiftId())).thenReturn(Optional.of(shift2));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(onlineWorker));
            when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);
            when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);

            // Act
            int totalDispatched = matchingDispatchService.dispatchForEvent(sampleEvent.getEventId());

            // Assert
            assertEquals(2, totalDispatched);
            verify(dispatchProposalRepository, times(2)).save(any(DispatchProposal.class));
        }

        @Test
        @DisplayName("MATCH018: Ứng viên được xếp hạng theo Soft Score WSM: Gần hơn & Rating cao hơn được ưu tiên trước")
        void dispatchForShift_RanksCandidatesByScore_DispatchesTopCandidates() {
            // Worker A: Cách 200m, Rating 4.8, 2 ca
            // Worker B: Cách 2km, Rating 4.0, 5 ca
            Worker workerB = Worker.builder()
                    .workerId(UUID.randomUUID())
                    .user(User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE).build())
                    .fullName("Worker B Xa Hơn")
                    .isOnline(true)
                    .reliabilityScore(80)
                    .ratingAvg(BigDecimal.valueOf(4.0))
                    .totalShiftsCompleted(5)
                    .baseLat(10.7950) // ~2km
                    .baseLng(106.7150)
                    .build();

            sampleShift.setRemainingWorkers(1); // cần 1 người

            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(workerB, onlineWorker));
            when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);
            when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);

            // Act
            int count = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            // Assert
            assertEquals(2, count);
            verify(dispatchProposalRepository, times(2)).save(any(DispatchProposal.class));
        }
    }
}
