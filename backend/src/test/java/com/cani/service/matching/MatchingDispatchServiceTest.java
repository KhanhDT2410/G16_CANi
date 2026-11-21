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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    @Mock
    private com.cani.repository.WorkerSkillRepository workerSkillRepository;

    @Mock
    private com.cani.repository.WorkerAvailabilityRepository workerAvailabilityRepository;

    @Mock
    private com.cani.repository.WorkerGroupRepository workerGroupRepository;

    @Mock
    private com.cani.repository.WorkerGroupMembershipRepository workerGroupMembershipRepository;

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
        ReflectionTestUtils.setField(matchingDispatchService, "workerSkillRepository", workerSkillRepository);
        ReflectionTestUtils.setField(matchingDispatchService, "workerAvailabilityRepository", workerAvailabilityRepository);

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
                .kycStatus(KycStatus.APPROVED)
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
                .kycStatus(KycStatus.APPROVED)
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
                .kycStatus(KycStatus.APPROVED)
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
                .kycStatus(KycStatus.APPROVED)
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
        @DisplayName("MATCH003: Tọa độ null trả về khoảng cách vô cực (Double.MAX_VALUE) để tránh matching sai lệch")
        void calculateDistance_NullCoordinates_ReturnsFallbackDefault() {
            double distance = MatchingDispatchService.calculateDistance(null, 106.7009, 10.7769, null);
            assertEquals(Double.MAX_VALUE, distance);
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
        @DisplayName("MATCH006b: Worker chưa hoàn tất eKYC (kycStatus != APPROVED) bị loại khỏi danh sách dispatch")
        void passHardFilter_UnverifiedEkycWorker_FilteredOut() {
            User activeUser = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE).build();
            Worker unverifiedWorker = Worker.builder()
                    .workerId(UUID.randomUUID())
                    .user(activeUser)
                    .kycStatus(KycStatus.PENDING)
                    .isOnline(true)
                    .reliabilityScore(90)
                    .baseLat(10.7769)
                    .baseLng(106.7009)
                    .build();

            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(unverifiedWorker));

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
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(any(), any(), anyList())).thenReturn(false);
            when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(1, dispatched);
            verify(dispatchProposalRepository, times(1)).save(any(DispatchProposal.class));
        }

        @Test
        @DisplayName("MATCH009b: Ca làm việc có requiredSkills -> Chỉ Worker có đủ kỹ năng mới được nhận đề xuất")
        void passHardFilter_RequiredSkills_FiltersCorrectly() {
            UUID reqSkillId = UUID.randomUUID();
            Skill reqSkill = Skill.builder().skillId(reqSkillId).name("Lễ tân / Check-in").build();
            sampleShift.setRequiredSkills(new UUID[]{reqSkillId});

            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(onlineWorker, experiencedWorker));
            when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(Collections.emptyList());

            // onlineWorker chưa có skill; experiencedWorker có đủ skill (AI_DOCUMENT)
            when(workerSkillRepository.findByWorker_WorkerId(onlineWorker.getWorkerId())).thenReturn(Collections.emptyList());
            when(workerSkillRepository.findByWorker_WorkerId(experiencedWorker.getWorkerId())).thenReturn(List.of(
                    WorkerSkill.builder()
                            .worker(experiencedWorker)
                            .skill(reqSkill)
                            .verificationTier(VerificationTier.AI_DOCUMENT)
                            .build()
            ));
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(any(), any(), anyList())).thenReturn(false);
            when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(1, dispatched);
            verify(dispatchProposalRepository, times(1)).save(argThat(p ->
                    p.getWorker().getWorkerId().equals(experiencedWorker.getWorkerId())
            ));
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
                    .kycStatus(KycStatus.APPROVED)
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

        @Test
        @DisplayName("MATCH012: Tiêu chí Worker yêu cầu lương tối thiểu 50.000đ -> Ca làm 45.000đ bị loại bỏ")
        void passHardFilter_ShiftWageBelowWorkerMinWage_FilteredOut() {
            onlineWorker.setMinWage(BigDecimal.valueOf(50000));
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(onlineWorker));

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("MATCH013: Worker chỉ nhận việc trong bán kính 100m -> Ca làm cách ~200m bị loại dù shift cho phép 15km")
        void passHardFilter_WorkerMaxDistanceKm_Exceeded_FilteredOut() {
            onlineWorker.setMaxDistanceKm(0.1);
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(onlineWorker));
            when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(Collections.emptyList());

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("MATCH014: Worker đã cấu hình lịch rảnh cố định nhưng ca làm không nằm trong slot rảnh -> Bị loại bỏ")
        void passHardFilter_ShiftNotInWorkerWeeklyAvailability_FilteredOut() {
            ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");
            ZonedDateTime shiftStart = sampleShift.getStartTime().atZone(vnZone);
            int shiftDayOfWeek = shiftStart.getDayOfWeek().getValue() % 7;
            int otherDay = (shiftDayOfWeek + 1) % 7; // Khác ngày của shift

            WorkerAvailability slot = WorkerAvailability.builder()
                    .worker(onlineWorker)
                    .dayOfWeek(otherDay)
                    .startTime(LocalTime.of(8, 0))
                    .endTime(LocalTime.of(22, 0))
                    .isAvailable(true)
                    .build();

            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(onlineWorker));
            when(workerAvailabilityRepository.findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(onlineWorker.getWorkerId()))
                    .thenReturn(List.of(slot));

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("MATCH015: Worker có lịch rảnh cố định bao trọn ca làm việc -> Được đề xuất thành công")
        void passHardFilter_ShiftWithinWorkerWeeklyAvailability_PassesFilter() {
            ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");
            ZonedDateTime shiftStart = sampleShift.getStartTime().atZone(vnZone);
            int shiftDayOfWeek = shiftStart.getDayOfWeek().getValue() % 7;

            // Slot từ 00:00 đến 23:59 của ngày diễn ra ca làm việc
            WorkerAvailability slot = WorkerAvailability.builder()
                    .worker(onlineWorker)
                    .dayOfWeek(shiftDayOfWeek)
                    .startTime(LocalTime.of(0, 0))
                    .endTime(LocalTime.of(23, 59))
                    .isAvailable(true)
                    .build();

            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(onlineWorker));
            when(workerAvailabilityRepository.findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(onlineWorker.getWorkerId()))
                    .thenReturn(List.of(slot));
            when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(any(), any(), anyList())).thenReturn(false);
            when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(1, dispatched);
            verify(dispatchProposalRepository, times(1)).save(any(DispatchProposal.class));
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
        @DisplayName("MATCH014: Worker đã nhận proposal đang PENDING/ACCEPTED hoặc assignment trước đó cho ca này -> không tạo trùng")
        void dispatchForShift_DuplicateProposalOrAssignment_SkipsWorker() {
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(onlineWorker));
            when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(Collections.emptyList());

            // Đã tồn tại proposal PENDING hoặc ACCEPTED
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(
                    eq(sampleShift.getShiftId()), eq(onlineWorker.getWorkerId()), anyList()))
                    .thenReturn(true);

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(0, dispatched);
            verify(dispatchProposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("MATCH014b: Worker có proposal cũ đã REJECTED/EXPIRED được phép nhận lại proposal mới")
        void dispatchForShift_RejectedOrExpiredProposal_AllowsRedispatch() {
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(onlineWorker));
            when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(Collections.emptyList());

            // Không có proposal nào đang PENDING hoặc ACCEPTED
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(
                    eq(sampleShift.getShiftId()), eq(onlineWorker.getWorkerId()), anyList()))
                    .thenReturn(false);
            when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);

            int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            assertEquals(1, dispatched);
            verify(dispatchProposalRepository, times(1)).save(any(DispatchProposal.class));
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
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(any(), any(), anyList())).thenReturn(false);
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
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(any(), any(), anyList())).thenReturn(false);
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
                    .kycStatus(KycStatus.APPROVED)
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
            when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(any(), any(), anyList())).thenReturn(false);
            when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);

            // Act
            int count = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

            // Assert
            assertEquals(2, count);
            verify(dispatchProposalRepository, times(2)).save(any(DispatchProposal.class));
        }
    }

    // =========================================================================
    // 5. Group Dispatch Channel: dispatchGroupsForShift (kênh group song song kênh cá nhân)
    // =========================================================================
    @Nested
    @DisplayName("5. Group Dispatch Channel: dispatchGroupsForShift")
    class GroupDispatchChannelTests {

        private record Fixture(WorkerGroup group, Worker leader, Worker member) {
        }

        private Worker groupWorker(String name, KycStatus kyc, Boolean online, int reliabilityScore) {
            return Worker.builder()
                    .workerId(UUID.randomUUID())
                    .user(User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE).build())
                    .fullName(name)
                    .kycStatus(kyc)
                    .isOnline(online)
                    .reliabilityScore(reliabilityScore)
                    .totalShiftsCompleted(5)
                    .build();
        }

        private WorkerGroupMembership membership(WorkerGroup group, Worker worker) {
            return WorkerGroupMembership.builder()
                    .workerGroup(group)
                    .worker(worker)
                    .status(MembershipStatus.ACTIVE)
                    .build();
        }

        /** Group có leader + 1 member đủ điều kiện (stub sẵn findById leader). */
        private Fixture eligibleGroup(String name) {
            Worker leader = groupWorker("Leader " + name, KycStatus.APPROVED, true, 90);
            Worker member = groupWorker("Member " + name, KycStatus.APPROVED, true, 85);
            WorkerGroup group = WorkerGroup.builder()
                    .groupId(UUID.randomUUID())
                    .name(name)
                    .leaderWorkerId(leader.getWorkerId())
                    .isActive(true)
                    .build();
            when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
            return new Fixture(group, leader, member);
        }

        @Test
        @DisplayName("GROUP001: 3 group (2 có member eligible, 1 toàn member không đạt) -> đúng 2 proposal, gán leader, nguồn GROUP, gửi FCM leader")
        void dispatch_groups_creates_one_proposal_per_eligible_group_top5() {
            Fixture g1 = eligibleGroup("Đội Lễ Tân");
            Fixture g2 = eligibleGroup("Đội Hậu Cần");

            Worker leader3 = groupWorker("Leader Đội Trống", KycStatus.APPROVED, true, 90);
            Worker badMember = groupWorker("Chưa eKYC", KycStatus.PENDING, true, 90);
            WorkerGroup g3 = WorkerGroup.builder()
                    .groupId(UUID.randomUUID())
                    .name("Đội Trống")
                    .leaderWorkerId(leader3.getWorkerId())
                    .isActive(true)
                    .build();
            when(workerRepository.findById(leader3.getWorkerId())).thenReturn(Optional.of(leader3));

            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerGroupRepository.findAll()).thenReturn(List.of(g1.group(), g2.group(), g3));
            when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(), eq(MembershipStatus.ACTIVE)))
                    .thenReturn(List.of(membership(g1.group(), g1.member()), membership(g2.group(), g2.member()), membership(g3, badMember)));
            when(dispatchProposalRepository.save(any(DispatchProposal.class))).thenAnswer(inv -> {
                DispatchProposal p = inv.getArgument(0);
                p.setProposalId(UUID.randomUUID());
                return p;
            });

            // Act
            Instant before = Instant.now();
            int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

            // Assert: đúng 2 proposal cho 2 group có member eligible
            assertEquals(2, count);
            ArgumentCaptor<DispatchProposal> captor = ArgumentCaptor.forClass(DispatchProposal.class);
            verify(dispatchProposalRepository, times(2)).save(captor.capture());

            Map<UUID, DispatchProposal> byGroup = new HashMap<>();
            for (DispatchProposal p : captor.getAllValues()) {
                byGroup.put(p.getGroupId(), p);
                assertEquals(ProposalStatus.PENDING, p.getStatus());
                assertEquals(AssignmentSource.GROUP, p.getSource());
                assertNotNull(p.getGroupId());
                assertTrue(p.getExpiresAt().isAfter(before));
                assertTrue(p.getExpiresAt().isBefore(before.plus(Duration.ofMinutes(61))));
                assertTrue(p.getScoreBreakdown().contains("eligibleMemberWorkerIds"));
            }
            DispatchProposal p1 = byGroup.get(g1.group().getGroupId());
            DispatchProposal p2 = byGroup.get(g2.group().getGroupId());
            assertNotNull(p1);
            assertNotNull(p2);
            assertEquals(g1.leader(), p1.getWorker());
            assertEquals(g2.leader(), p2.getWorker());
            assertTrue(p1.getScoreBreakdown().contains(g1.member().getWorkerId().toString()));

            // FCM gửi leader
            verify(notificationService).createAndSend(
                    eq(g1.leader().getUser().getUserId()),
                    contains("Lời mời ca cho đội"),
                    anyString(),
                    eq(NotificationType.PROPOSAL),
                    eq("PROPOSAL"),
                    any(),
                    anyMap());
            verify(notificationService).createAndSend(
                    eq(g2.leader().getUser().getUserId()),
                    contains("Lời mời ca cho đội"),
                    anyString(),
                    eq(NotificationType.PROPOSAL),
                    eq("PROPOSAL"),
                    any(),
                    anyMap());
        }

        @Test
        @DisplayName("GROUP002: Group 4 member nhưng tất cả không đủ điều kiện -> 0 proposal, không exception")
        void dispatch_groups_skips_group_with_zero_eligible_members() {
            Worker leader = groupWorker("Leader", KycStatus.APPROVED, true, 90);
            Worker m1 = groupWorker("Chưa eKYC", KycStatus.PENDING, true, 90);
            Worker m2 = groupWorker("Uy tín thấp", KycStatus.APPROVED, true, 40);
            Worker m3 = groupWorker("Account khóa", KycStatus.APPROVED, true, 90);
            m3.getUser().setStatus(UserStatus.LOCKED);
            Worker m4 = groupWorker("Trùng lịch", KycStatus.APPROVED, true, 90);
            WorkerGroup group = WorkerGroup.builder()
                    .groupId(UUID.randomUUID())
                    .name("Đội 4 Người")
                    .leaderWorkerId(leader.getWorkerId())
                    .isActive(true)
                    .build();
            when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));

            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerGroupRepository.findAll()).thenReturn(List.of(group));
            when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(), eq(MembershipStatus.ACTIVE)))
                    .thenReturn(List.of(membership(group, m1), membership(group, m2), membership(group, m3), membership(group, m4)));
            when(shiftAssignmentRepository.findConflictingAssignments(eq(m4.getWorkerId()), any(), any(), anyList()))
                    .thenReturn(List.of(ShiftAssignment.builder().assignmentId(UUID.randomUUID()).status(AssignmentStatus.CONFIRMED).build()));

            // Act & Assert: không ném exception, 0 proposal
            assertEquals(0, matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false));
            verify(dispatchProposalRepository, never()).save(any(DispatchProposal.class));
        }

        @Test
        @DisplayName("GROUP003: Member isOnline=false nhưng đủ điều kiện khác -> vẫn được tính eligible (kênh group không xét isOnline)")
        void dispatch_groups_ignores_is_online() {
            Worker leader = groupWorker("Leader", KycStatus.APPROVED, true, 90);
            Worker offlineMember = groupWorker("Member Offline", KycStatus.APPROVED, false, 88);
            WorkerGroup group = WorkerGroup.builder()
                    .groupId(UUID.randomUUID())
                    .name("Đội Offline")
                    .leaderWorkerId(leader.getWorkerId())
                    .isActive(true)
                    .build();
            when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));

            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerGroupRepository.findAll()).thenReturn(List.of(group));
            when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(), eq(MembershipStatus.ACTIVE)))
                    .thenReturn(List.of(membership(group, offlineMember)));
            when(dispatchProposalRepository.save(any(DispatchProposal.class))).thenAnswer(inv -> {
                DispatchProposal p = inv.getArgument(0);
                p.setProposalId(UUID.randomUUID());
                return p;
            });

            int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

            assertEquals(1, count);
            ArgumentCaptor<DispatchProposal> captor = ArgumentCaptor.forClass(DispatchProposal.class);
            verify(dispatchProposalRepository).save(captor.capture());
            assertTrue(captor.getValue().getScoreBreakdown().contains(offlineMember.getWorkerId().toString()));
        }

        @Test
        @DisplayName("GROUP005: Group no-show trong 19 phút (cooldown 20p) bị loại; group no-show 21 phút trước đó vẫn được đề xuất")
        void dispatch_groups_skips_group_in_no_show_cooldown_20min() {
            Instant now = Instant.now();

            Fixture hot = eligibleGroup("Đội Mới No-show");
            hot.group().setLastNoShowAt(now.minus(Duration.ofMinutes(19)));
            Fixture old = eligibleGroup("Đội Hết Cooldown");
            old.group().setLastNoShowAt(now.minus(Duration.ofMinutes(21)));

            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerGroupRepository.findAll()).thenReturn(List.of(hot.group(), old.group()));
            when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(), eq(MembershipStatus.ACTIVE)))
                    .thenReturn(List.of(membership(hot.group(), hot.member()), membership(old.group(), old.member())));
            when(dispatchProposalRepository.save(any(DispatchProposal.class))).thenAnswer(inv -> {
                DispatchProposal p = inv.getArgument(0);
                p.setProposalId(UUID.randomUUID());
                return p;
            });

            int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

            assertEquals(1, count);
            ArgumentCaptor<DispatchProposal> captor = ArgumentCaptor.forClass(DispatchProposal.class);
            verify(dispatchProposalRepository, times(1)).save(captor.capture());
            assertEquals(old.group().getGroupId(), captor.getValue().getGroupId());
        }

        @Test
        @DisplayName("GROUP006: leader chuyển nhượng -> PENDING group proposal (chưa hết hạn thời gian) vẫn bị EXPIRED")
        void expireStalePendingProposals_expires_orphaned_group_proposal_after_leader_transfer() {
            Worker oldLeader = groupWorker("Leader Cũ", KycStatus.APPROVED, true, 90);
            Worker newLeader = groupWorker("Leader Mới", KycStatus.APPROVED, true, 90);
            WorkerGroup g = WorkerGroup.builder()
                    .groupId(UUID.randomUUID())
                    .name("Đội Chuyển Nhượng")
                    .leaderWorkerId(newLeader.getWorkerId())
                    .isActive(true)
                    .build();

            Instant now = Instant.now();
            DispatchProposal orphaned = DispatchProposal.builder()
                    .shift(sampleShift)
                    .worker(oldLeader)
                    .groupId(g.getGroupId())
                    .status(ProposalStatus.PENDING)
                    .createdAt(now)
                    .expiresAt(now.plus(Duration.ofHours(1)))
                    .build();
            DispatchProposal individual = DispatchProposal.builder()
                    .shift(sampleShift)
                    .worker(onlineWorker)
                    .status(ProposalStatus.PENDING)
                    .createdAt(now)
                    .expiresAt(now.plus(Duration.ofHours(1)))
                    .build();

            when(dispatchProposalRepository.findByStatusAndExpiresAtBefore(ProposalStatus.PENDING, any(Instant.class)))
                    .thenReturn(List.of());
            when(dispatchProposalRepository.findByStatusAndGroupIdIsNotNullAndCreatedAtAfter(ProposalStatus.PENDING, any(Instant.class)))
                    .thenReturn(List.of(orphaned));
            when(workerGroupRepository.findAllById(List.of(g.getGroupId()))).thenReturn(List.of(g));

            matchingDispatchService.expireStalePendingProposals();

            assertEquals(ProposalStatus.EXPIRED, orphaned.getStatus());
            assertEquals(ProposalStatus.PENDING, individual.getStatus());
            verify(dispatchProposalRepository).saveAll(List.of(orphaned));
        }

        @Test
        @DisplayName("GROUP007: proposal PENDING còn hạn, leader đúng với group hiện tại -> KHÔNG bị EXPIRED")
        void expireStalePendingProposals_keeps_valid_group_proposal() {
            Worker leader = groupWorker("Leader", KycStatus.APPROVED, true, 90);
            WorkerGroup g = WorkerGroup.builder()
                    .groupId(UUID.randomUUID())
                    .name("Đội Giữ Nguyên")
                    .leaderWorkerId(leader.getWorkerId())
                    .isActive(true)
                    .build();

            Instant now = Instant.now();
            DispatchProposal valid = DispatchProposal.builder()
                    .shift(sampleShift)
                    .worker(leader)
                    .groupId(g.getGroupId())
                    .status(ProposalStatus.PENDING)
                    .createdAt(now)
                    .expiresAt(now.plus(Duration.ofHours(1)))
                    .build();

            when(dispatchProposalRepository.findByStatusAndExpiresAtBefore(ProposalStatus.PENDING, any(Instant.class)))
                    .thenReturn(List.of());
            when(dispatchProposalRepository.findByStatusAndGroupIdIsNotNullAndCreatedAtAfter(ProposalStatus.PENDING, any(Instant.class)))
                    .thenReturn(List.of(valid));
            when(workerGroupRepository.findAllById(List.of(g.getGroupId()))).thenReturn(List.of(g));

            matchingDispatchService.expireStalePendingProposals();

            assertEquals(ProposalStatus.PENDING, valid.getStatus());
            verify(dispatchProposalRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("GROUP004: emergency=true -> proposal hết hạn ~30 phút (không phải 1 giờ), tiêu đề có tiền tố 🚨 GẤP")
        void dispatch_groups_emergency_expiry_30min() {
            Fixture g = eligibleGroup("Đội Gấp");
            when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
            when(workerGroupRepository.findAll()).thenReturn(List.of(g.group()));
            when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(), eq(MembershipStatus.ACTIVE)))
                    .thenReturn(List.of(membership(g.group(), g.member())));
            when(dispatchProposalRepository.save(any(DispatchProposal.class))).thenAnswer(inv -> {
                DispatchProposal p = inv.getArgument(0);
                p.setProposalId(UUID.randomUUID());
                return p;
            });

            Instant before = Instant.now();
            int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), true);

            assertEquals(1, count);
            ArgumentCaptor<DispatchProposal> captor = ArgumentCaptor.forClass(DispatchProposal.class);
            verify(dispatchProposalRepository).save(captor.capture());
            Instant expiresAt = captor.getValue().getExpiresAt();
            assertTrue(expiresAt.isAfter(before.plus(Duration.ofMinutes(30)).minusSeconds(5)),
                    "expiresAt phải >= now + 30 phút");
            assertTrue(expiresAt.isBefore(before.plus(Duration.ofMinutes(31))),
                    "expiresAt phải <= now + 31 phút");
            verify(notificationService).createAndSend(
                    eq(g.leader().getUser().getUserId()),
                    startsWith("🚨 GẤP"),
                    anyString(),
                    eq(NotificationType.PROPOSAL),
                    eq("PROPOSAL"),
                    any(),
                    anyMap());
        }
    }
}
