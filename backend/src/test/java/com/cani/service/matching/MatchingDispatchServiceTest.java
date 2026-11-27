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
 * Exhaustive Unit Test Suite for MatchingDispatchService (On-Demand Workforce
 * Matching & Dispatch Engine).
 * Covers FC-MATCH-01 through FC-MATCH-05 conforming to IEEE 829 and
 * Report-5.1_Unit Test.xlsx standard.
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
                ReflectionTestUtils.setField(matchingDispatchService, "workerAvailabilityRepository",
                                workerAvailabilityRepository);

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
                User user1 = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE)
                                .build();
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
                User user2 = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE)
                                .build();
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
                User user3 = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE)
                                .build();
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
                User user4 = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE)
                                .build();
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
                        // Tọa độ Chợ Bến Thành (10.7719, 106.6983) và Nhà Thờ Đức Bà (10.7797,
                        // 106.6990)
                        double distance = MatchingDispatchService.calculateDistance(10.7719, 106.6983, 10.7797,
                                        106.6990);
                        assertThat(distance).isGreaterThan(0.7).isLessThan(1.2);
                }

                @Test
                @DisplayName("MATCH002: Tọa độ trùng nhau trả về khoảng cách đúng bằng 0.0 km")
                void calculateDistance_IdenticalCoordinates_ReturnsZero() {
                        double distance = MatchingDispatchService.calculateDistance(10.7769, 106.7009, 10.7769,
                                        106.7009);
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
                        // Bến Thành (10.7769, 106.7009) đến Biên Hòa Đồng Nai (10.9574, 106.8427)
                        // ~25-30km
                        double distance = MatchingDispatchService.calculateDistance(10.7769, 106.7009, 10.9574,
                                        106.8427);
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
                        User lockedUser = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER)
                                        .status(UserStatus.LOCKED).build();
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
                        User activeUser = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER)
                                        .status(UserStatus.ACTIVE).build();
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
                @DisplayName("MATCH009b: Ca làm việc có requiredSkills -> Chỉ Worker có đủ kỹ năng mới được nhận đề xuất")
                void passHardFilter_RequiredSkills_FiltersCorrectly() {
                        UUID reqSkillId = UUID.randomUUID();
                        Skill reqSkill = Skill.builder().skillId(reqSkillId).name("Lễ tân / Check-in").build();
                        sampleShift.setRequiredSkills(new UUID[] { reqSkillId });

                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerRepository.findByIsOnlineTrue())
                                        .thenReturn(List.of(onlineWorker, experiencedWorker));
                        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any()))
                                        .thenReturn(Collections.emptyList());

                        // onlineWorker chưa có skill; experiencedWorker có đủ skill (AI_DOCUMENT)
                        when(workerSkillRepository.findByWorker_WorkerId(onlineWorker.getWorkerId()))
                                        .thenReturn(Collections.emptyList());
                        when(workerSkillRepository.findByWorker_WorkerId(experiencedWorker.getWorkerId()))
                                        .thenReturn(List.of(
                                                        WorkerSkill.builder()
                                                                        .worker(experiencedWorker)
                                                                        .skill(reqSkill)
                                                                        .verificationTier(VerificationTier.AI_DOCUMENT)
                                                                        .build()));
                        when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(any(), any(),
                                        anyList())).thenReturn(false);
                        when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any()))
                                        .thenReturn(false);

                        int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

                        assertEquals(1, dispatched);
                        verify(dispatchProposalRepository, times(1)).save(argThat(
                                        p -> p.getWorker().getWorkerId().equals(experiencedWorker.getWorkerId())));
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

                        when(shiftAssignmentRepository.findConflictingAssignments(eq(onlineWorker.getWorkerId()), any(),
                                        any(), anyList()))
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
                                        .user(User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER)
                                                        .status(UserStatus.ACTIVE).build())
                                        .kycStatus(KycStatus.APPROVED)
                                        .isOnline(true)
                                        .reliabilityScore(90)
                                        .baseLat(10.9574) // Biên Hòa (~26km)
                                        .baseLng(106.8427)
                                        .build();

                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerRepository.findByIsOnlineTrue()).thenReturn(List.of(farWorker));
                        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any()))
                                        .thenReturn(Collections.emptyList());

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
                        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any()))
                                        .thenReturn(Collections.emptyList());

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
                        when(workerAvailabilityRepository.findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(
                                        onlineWorker.getWorkerId()))
                                        .thenReturn(List.of(slot));

                        int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

                        assertEquals(0, dispatched);
                        verify(dispatchProposalRepository, never()).save(any());
                }

                @Test
                @DisplayName("MATCH015: Worker có lịch rảnh cố định bao trọn ca làm việc -> Được đề xuất thành công")
                void passHardFilter_ShiftWithinWorkerWeeklyAvailability_PassesFilter() {
                        ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");
                        java.time.LocalDate tomorrow = java.time.LocalDate.now(vnZone).plusDays(1);
                        sampleShift.setStartTime(tomorrow.atTime(8, 0).atZone(vnZone).toInstant());
                        sampleShift.setEndTime(tomorrow.atTime(12, 0).atZone(vnZone).toInstant());
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
                        when(workerAvailabilityRepository.findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(
                                        onlineWorker.getWorkerId()))
                                        .thenReturn(List.of(slot));
                        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any()))
                                        .thenReturn(Collections.emptyList());
                        when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(any(), any(),
                                        anyList())).thenReturn(false);
                        when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any()))
                                        .thenReturn(false);

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
                        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any()))
                                        .thenReturn(Collections.emptyList());

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
                        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any()))
                                        .thenReturn(Collections.emptyList());

                        // Không có proposal nào đang PENDING hoặc ACCEPTED
                        when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(
                                        eq(sampleShift.getShiftId()), eq(onlineWorker.getWorkerId()), anyList()))
                                        .thenReturn(false);
                        when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any()))
                                        .thenReturn(false);

                        int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

                        assertEquals(1, dispatched);
                        verify(dispatchProposalRepository, times(1)).save(any(DispatchProposal.class));
                }

                @Test
                @DisplayName("MATCH015: Ca làm việc không tồn tại trong hệ thống ném IllegalArgumentException")
                void dispatchForShift_ShiftNotFound_ThrowsIllegalArgumentException() {
                        UUID unknownId = UUID.randomUUID();
                        when(shiftRepository.findById(unknownId)).thenReturn(Optional.empty());

                        assertThrows(IllegalArgumentException.class,
                                        () -> matchingDispatchService.dispatchForShift(unknownId));
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
                        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any()))
                                        .thenReturn(Collections.emptyList());
                        when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(any(), any(),
                                        anyList())).thenReturn(false);
                        when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any()))
                                        .thenReturn(false);

                        when(dispatchProposalRepository.save(any(DispatchProposal.class))).thenAnswer(inv -> {
                                DispatchProposal p = inv.getArgument(0);
                                p.setProposalId(UUID.randomUUID());
                                return p;
                        });

                        // Act
                        int dispatched = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

                        // Assert
                        assertEquals(1, dispatched);
                        verify(dispatchProposalRepository).save(argThat(p -> p.getStatus() == ProposalStatus.PENDING &&
                                        p.getExpiresAt().isAfter(Instant.now()) &&
                                        p.getMatchScore() != null));
                        verify(notificationService).createAndSend(
                                        eq(onlineWorker.getUser().getUserId()),
                                        contains("Đề xuất ca làm mới"),
                                        anyString(),
                                        eq(NotificationType.PROPOSAL),
                                        eq("PROPOSAL"),
                                        any(),
                                        anyMap());
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
                        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any()))
                                        .thenReturn(Collections.emptyList());
                        when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(any(), any(),
                                        anyList())).thenReturn(false);
                        when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any()))
                                        .thenReturn(false);

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
                                        .user(User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER)
                                                        .status(UserStatus.ACTIVE).build())
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
                        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any()))
                                        .thenReturn(Collections.emptyList());
                        when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(any(), any(),
                                        anyList())).thenReturn(false);
                        when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any()))
                                        .thenReturn(false);

                        // Act
                        int count = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

                        // Assert
                        assertEquals(2, count);
                        verify(dispatchProposalRepository, times(2)).save(any(DispatchProposal.class));
                }
        }

        // =========================================================================
        // 5. Group Dispatch Channel: dispatchGroupsForShift (kênh group song song kênh
        // cá nhân)
        // =========================================================================
        @Nested
        @DisplayName("5. Group Dispatch Channel: dispatchGroupsForShift")
        class GroupDispatchChannelTests {

                private record Fixture(WorkerGroup group, Worker leader, Worker member) {
                }

                private Worker groupWorker(String name, KycStatus kyc, Boolean online, int reliabilityScore) {
                        return Worker.builder()
                                        .workerId(UUID.randomUUID())
                                        .user(User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER)
                                                        .status(UserStatus.ACTIVE).build())
                                        .fullName(name)
                                        .kycStatus(kyc)
                                        .isOnline(online)
                                        .reliabilityScore(reliabilityScore)
                                        .totalShiftsCompleted(5)
                                        .baseLat(10.7769)
                                        .baseLng(106.7009)
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
                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
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
                        when(workerGroupRepository.findAllByIsActiveTrue())
                                        .thenReturn(List.of(g1.group(), g2.group(), g3));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(List.of(membership(g1.group(), g1.member()),
                                                        membership(g2.group(), g2.member()),
                                                        membership(g3, badMember)));
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
                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));

                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(List.of(membership(group, m1), membership(group, m2),
                                                        membership(group, m3), membership(group, m4)));
                        lenient().when(shiftAssignmentRepository.findConflictingAssignments(eq(m4.getWorkerId()), any(),
                                        any(), anyList()))
                                        .thenReturn(List.of(ShiftAssignment.builder().assignmentId(UUID.randomUUID())
                                                        .status(AssignmentStatus.CONFIRMED).build()));

                        // Act & Assert: không ném exception, 0 proposal
                        assertEquals(0, matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(),
                                        false));
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
                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));

                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
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
                        assertTrue(captor.getValue().getScoreBreakdown()
                                        .contains(offlineMember.getWorkerId().toString()));
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
                        when(workerGroupRepository.findAllByIsActiveTrue())
                                        .thenReturn(List.of(hot.group(), old.group()));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(List.of(membership(hot.group(), hot.member()),
                                                        membership(old.group(), old.member())));
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

                        when(dispatchProposalRepository.findByStatusAndExpiresAtBefore(eq(ProposalStatus.PENDING),
                                        any(Instant.class)))
                                        .thenReturn(List.of());
                        when(dispatchProposalRepository.findByStatusAndGroupIdIsNotNullAndCreatedAtAfter(
                                        eq(ProposalStatus.PENDING), any(Instant.class)))
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

                        when(dispatchProposalRepository.findByStatusAndExpiresAtBefore(eq(ProposalStatus.PENDING),
                                        any(Instant.class)))
                                        .thenReturn(List.of());
                        when(dispatchProposalRepository.findByStatusAndGroupIdIsNotNullAndCreatedAtAfter(
                                        eq(ProposalStatus.PENDING), any(Instant.class)))
                                        .thenReturn(List.of(valid));
                        when(workerGroupRepository.findAllById(List.of(g.getGroupId()))).thenReturn(List.of(g));

                        matchingDispatchService.expireStalePendingProposals();

                        assertEquals(ProposalStatus.PENDING, valid.getStatus());
                        verify(dispatchProposalRepository, never()).saveAll(anyList());
                }

                @Test
                @DisplayName("F5: Group isActive=false, proposal PENDING còn hạn, leader khớp -> scheduler vẫn chuyển sang EXPIRED")
                void expireStalePendingProposals_groupInactive_marksProposalExpired() {
                        Worker leader = groupWorker("Leader", KycStatus.APPROVED, true, 90);
                        WorkerGroup inactiveGroup = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội Đã Giải Tán")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .isActive(false)
                                        .build();

                        Instant now = Instant.now();
                        DispatchProposal proposal = DispatchProposal.builder()
                                        .shift(sampleShift)
                                        .worker(leader)
                                        .groupId(inactiveGroup.getGroupId())
                                        .status(ProposalStatus.PENDING)
                                        .createdAt(now)
                                        .expiresAt(now.plus(Duration.ofHours(1)))
                                        .build();

                        when(dispatchProposalRepository.findByStatusAndExpiresAtBefore(eq(ProposalStatus.PENDING),
                                        any(Instant.class)))
                                        .thenReturn(List.of());
                        when(dispatchProposalRepository.findByStatusAndGroupIdIsNotNullAndCreatedAtAfter(
                                        eq(ProposalStatus.PENDING), any(Instant.class)))
                                        .thenReturn(List.of(proposal));
                        when(workerGroupRepository.findAllById(List.of(inactiveGroup.getGroupId())))
                                        .thenReturn(List.of(inactiveGroup));

                        matchingDispatchService.expireStalePendingProposals();

                        assertEquals(ProposalStatus.EXPIRED, proposal.getStatus());
                        verify(dispatchProposalRepository).saveAll(List.of(proposal));
                }

                @Test
                @DisplayName("GROUP004: emergency=true -> proposal hết hạn ~30 phút (không phải 1 giờ), tiêu đề có tiền tố 🚨 GẤP")
                void dispatch_groups_emergency_expiry_30min() {
                        Fixture g = eligibleGroup("Đội Gấp");
                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(g.group()));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
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

                @Test
                @DisplayName("SQUAD001: 5 ACTIVE đủ điều kiện, remaining 5 -> 1 proposal, packSize 5, breakdown chứa packWorkerIds và packSize")
                void dispatch_squad_pack_5_active_remaining_5_creates_proposal() {
                        sampleShift.setRemainingWorkers(5);
                        Worker leader = groupWorker("Leader Squad", KycStatus.APPROVED, true, 90);
                        List<Worker> members = List.of(
                                        groupWorker("Member 1", KycStatus.APPROVED, true, 85),
                                        groupWorker("Member 2", KycStatus.APPROVED, true, 80),
                                        groupWorker("Member 3", KycStatus.APPROVED, true, 82),
                                        groupWorker("Member 4", KycStatus.APPROVED, true, 88),
                                        groupWorker("Member 5", KycStatus.APPROVED, true, 86));
                        WorkerGroup group = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội 5 Người")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .isActive(true)
                                        .build();
                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));

                        List<WorkerGroupMembership> memberships = members.stream()
                                        .map(m -> membership(group, m))
                                        .toList();
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(memberships);

                        when(dispatchProposalRepository.save(any(DispatchProposal.class))).thenAnswer(inv -> {
                                DispatchProposal p = inv.getArgument(0);
                                p.setProposalId(UUID.randomUUID());
                                return p;
                        });

                        int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

                        assertEquals(1, count);
                        ArgumentCaptor<DispatchProposal> captor = ArgumentCaptor.forClass(DispatchProposal.class);
                        verify(dispatchProposalRepository).save(captor.capture());
                        DispatchProposal saved = captor.getValue();
                        assertEquals(AssignmentSource.GROUP, saved.getSource());
                        assertTrue(saved.getScoreBreakdown().contains("\"packSize\":5"));
                        assertTrue(saved.getScoreBreakdown().contains("packWorkerIds"));
                        for (Worker m : members) {
                                assertTrue(saved.getScoreBreakdown().contains(m.getWorkerId().toString()));
                        }
                }

                @Test
                @DisplayName("SQUAD002: 5 ACTIVE, remaining 4 -> 0 proposal (không đủ chỗ cho cả đội)")
                void dispatch_squad_pack_5_active_remaining_4_skips_group() {
                        sampleShift.setRemainingWorkers(4);
                        Worker leader = groupWorker("Leader Squad", KycStatus.APPROVED, true, 90);
                        List<Worker> members = List.of(
                                        groupWorker("Member 1", KycStatus.APPROVED, true, 85),
                                        groupWorker("Member 2", KycStatus.APPROVED, true, 80),
                                        groupWorker("Member 3", KycStatus.APPROVED, true, 82),
                                        groupWorker("Member 4", KycStatus.APPROVED, true, 88),
                                        groupWorker("Member 5", KycStatus.APPROVED, true, 86));
                        WorkerGroup group = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội 5 Người")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .isActive(true)
                                        .build();
                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(members.stream().map(m -> membership(group, m)).toList());

                        int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

                        assertEquals(0, count);
                        verify(dispatchProposalRepository, never()).save(any());
                }

                @Test
                @DisplayName("SQUAD003: 5 ACTIVE, 1 conflict -> 0 proposal (KHÔNG proposal 4 người)")
                void dispatch_squad_pack_5_active_1_conflict_skips_group() {
                        sampleShift.setRemainingWorkers(5);
                        Worker leader = groupWorker("Leader Squad", KycStatus.APPROVED, true, 90);
                        Worker conflicted = groupWorker("Member Trùng Lịch", KycStatus.APPROVED, true, 85);
                        List<Worker> members = List.of(
                                        conflicted,
                                        groupWorker("Member 2", KycStatus.APPROVED, true, 80),
                                        groupWorker("Member 3", KycStatus.APPROVED, true, 82),
                                        groupWorker("Member 4", KycStatus.APPROVED, true, 88),
                                        groupWorker("Member 5", KycStatus.APPROVED, true, 86));
                        WorkerGroup group = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội 5 Người")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .isActive(true)
                                        .build();
                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(members.stream().map(m -> membership(group, m)).toList());
                        ShiftAssignment conflictAssignment = ShiftAssignment.builder()
                                        .assignmentId(UUID.randomUUID())
                                        .worker(conflicted)
                                        .status(AssignmentStatus.CONFIRMED)
                                        .build();
                        lenient().when(shiftAssignmentRepository.findConflictingAssignments(eq(conflicted.getWorkerId()), any(),
                                        any(), anyList()))
                                        .thenReturn(List.of(conflictAssignment));
                        when(shiftAssignmentRepository.findConflictingAssignmentsForWorkers(anyCollection(), any(),
                                        any(), anyList()))
                                        .thenReturn(List.of(conflictAssignment));

                        int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

                        assertEquals(0, count);
                        verify(dispatchProposalRepository, never()).save(any());
                }

                @Test
                @DisplayName("SQUAD004: 1 member thiếu location -> 0 proposal (không fallback GPS leader)")
                void dispatch_squad_pack_1_member_missing_location_skips_group() {
                        sampleShift.setRemainingWorkers(5);
                        Worker leader = groupWorker("Leader Squad", KycStatus.APPROVED, true, 90);
                        Worker missingLoc = groupWorker("Member Mất Tọa Độ", KycStatus.APPROVED, true, 85);
                        missingLoc.setBaseLat(null);
                        missingLoc.setBaseLng(null);
                        List<Worker> members = List.of(
                                        missingLoc,
                                        groupWorker("Member 2", KycStatus.APPROVED, true, 80),
                                        groupWorker("Member 3", KycStatus.APPROVED, true, 82),
                                        groupWorker("Member 4", KycStatus.APPROVED, true, 88),
                                        groupWorker("Member 5", KycStatus.APPROVED, true, 86));
                        WorkerGroup group = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội Thiếu GPS")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .isActive(true)
                                        .build();
                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(members.stream().map(m -> membership(group, m)).toList());

                        int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

                        assertEquals(0, count);
                        verify(dispatchProposalRepository, never()).save(any());
                }

                @Test
                @DisplayName("SQUAD005: 1 member khoảng cách > maxDistance -> 0 proposal (hard safety check)")
                void dispatch_squad_pack_1_member_exceeds_radius_skips_group() {
                        sampleShift.setRemainingWorkers(5);
                        Worker leader = groupWorker("Leader Squad", KycStatus.APPROVED, true, 90);
                        Worker farAway = groupWorker("Member Ở Quá Xa", KycStatus.APPROVED, true, 85);
                        // Hà Nội vs TP.HCM -> ~1140 km > 15 km
                        farAway.setBaseLat(21.0285);
                        farAway.setBaseLng(105.8542);
                        List<Worker> members = List.of(
                                        farAway,
                                        groupWorker("Member 2", KycStatus.APPROVED, true, 80),
                                        groupWorker("Member 3", KycStatus.APPROVED, true, 82),
                                        groupWorker("Member 4", KycStatus.APPROVED, true, 88),
                                        groupWorker("Member 5", KycStatus.APPROVED, true, 86));
                        WorkerGroup group = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội Có Người Ở Xa")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .isActive(true)
                                        .build();
                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(members.stream().map(m -> membership(group, m)).toList());

                        int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

                        assertEquals(0, count);
                        verify(dispatchProposalRepository, never()).save(any());
                }

                @Test
                @DisplayName("SQUAD006: Membership ACTIVE có worker == null (ghost member) -> log cảnh báo và bỏ qua cả nhóm")
                void dispatch_squad_pack_ghost_member_null_worker_skips_group() {
                        sampleShift.setRemainingWorkers(5);
                        Worker leader = groupWorker("Leader Squad", KycStatus.APPROVED, true, 90);
                        List<Worker> members = List.of(
                                        groupWorker("Member 1", KycStatus.APPROVED, true, 85),
                                        groupWorker("Member 2", KycStatus.APPROVED, true, 80),
                                        groupWorker("Member 3", KycStatus.APPROVED, true, 82),
                                        groupWorker("Member 4", KycStatus.APPROVED, true, 88));
                        WorkerGroup group = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội Có Ghost Member")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .isActive(true)
                                        .build();
                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));

                        List<WorkerGroupMembership> memberships = new ArrayList<>(
                                        members.stream().map(m -> membership(group, m)).toList());
                        memberships.add(WorkerGroupMembership.builder()
                                        .membershipId(UUID.randomUUID())
                                        .workerGroup(group)
                                        .worker(null)
                                        .status(MembershipStatus.ACTIVE)
                                        .roleInGroup(GroupRole.MEMBER)
                                        .build());

                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(memberships);

                        int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

                        assertEquals(0, count);
                        verify(dispatchProposalRepository, never()).save(any());
                }

                @Test
                @DisplayName("SQUAD007: Shift có maxDistanceKm tùy chỉnh -> scoring normalization dùng effectiveMaxDist thay vì hằng số 15km")
                void dispatch_groups_uses_effective_max_distance_for_normalization() {
                        sampleShift.setRemainingWorkers(5);
                        sampleShift.setMaxDistanceKm(10.0);

                        Worker leader = groupWorker("Leader Squad", KycStatus.APPROVED, true, 90);
                        List<Worker> members = List.of(
                                        groupWorker("Member 1", KycStatus.APPROVED, true, 85),
                                        groupWorker("Member 2", KycStatus.APPROVED, true, 80),
                                        groupWorker("Member 3", KycStatus.APPROVED, true, 82),
                                        groupWorker("Member 4", KycStatus.APPROVED, true, 88),
                                        groupWorker("Member 5", KycStatus.APPROVED, true, 86));
                        WorkerGroup group = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội Custom Radius")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .isActive(true)
                                        .build();
                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));

                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(members.stream().map(m -> membership(group, m)).toList());

                        when(dispatchProposalRepository.save(any(DispatchProposal.class))).thenAnswer(inv -> {
                                DispatchProposal p = inv.getArgument(0);
                                p.setProposalId(UUID.randomUUID());
                                return p;
                        });

                        int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

                        assertEquals(1, count);
                        verify(dispatchProposalRepository).save(any(DispatchProposal.class));
                }

                @Test
                @DisplayName("SQUAD008: Group có member no-show trong 20 phút qua -> loại cả nhóm (F3)")
                void dispatch_skips_group_containing_no_show_member_within_20min() {
                        sampleShift.setRemainingWorkers(5);

                        Worker leader = groupWorker("Leader Squad", KycStatus.APPROVED, true, 90);
                        Worker noShowWorker = groupWorker("Worker No-show", KycStatus.APPROVED, true, 80);
                        List<Worker> members = List.of(
                                        leader,
                                        noShowWorker,
                                        groupWorker("Member 2", KycStatus.APPROVED, true, 82),
                                        groupWorker("Member 3", KycStatus.APPROVED, true, 88),
                                        groupWorker("Member 4", KycStatus.APPROVED, true, 86));
                        WorkerGroup group = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội Có Người No-Show")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .isActive(true)
                                        .build();

                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(members.stream().map(m -> membership(group, m)).toList());

                        // noShowWorker vi phạm NO_SHOW trong 20 phút qua
                        when(shiftAssignmentRepository.findWorkerIdsByStatusAndUpdatedAtAfter(
                                        eq(AssignmentStatus.NO_SHOW), any(Instant.class)))
                                        .thenReturn(List.of(noShowWorker.getWorkerId()));

                        int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

                        assertEquals(0, count);
                        verify(dispatchProposalRepository, never()).save(any());
                }

                @Test
                @DisplayName("SQUAD009: Group có member no-show quá 20 phút trước -> vẫn được xét bình thường (F3)")
                void dispatch_includes_group_after_no_show_20min_passed() {
                        sampleShift.setRemainingWorkers(5);

                        Worker leader = groupWorker("Leader Squad", KycStatus.APPROVED, true, 90);
                        List<Worker> members = List.of(
                                        leader,
                                        groupWorker("Member 1", KycStatus.APPROVED, true, 80),
                                        groupWorker("Member 2", KycStatus.APPROVED, true, 82),
                                        groupWorker("Member 3", KycStatus.APPROVED, true, 88),
                                        groupWorker("Member 4", KycStatus.APPROVED, true, 86));
                        WorkerGroup group = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội Hết Cooldown No-Show")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .isActive(true)
                                        .build();

                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(members.stream().map(m -> membership(group, m)).toList());

                        // Không có worker nào trong danh sách 20 phút gần nhất
                        when(shiftAssignmentRepository.findWorkerIdsByStatusAndUpdatedAtAfter(
                                        eq(AssignmentStatus.NO_SHOW), any(Instant.class)))
                                        .thenReturn(Collections.emptyList());

                        when(dispatchProposalRepository.save(any(DispatchProposal.class))).thenAnswer(inv -> {
                                DispatchProposal p = inv.getArgument(0);
                                p.setProposalId(UUID.randomUUID());
                                return p;
                        });

                        int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

                        assertEquals(1, count);
                        verify(dispatchProposalRepository).save(any(DispatchProposal.class));
                }

                @Test
                @DisplayName("SQUAD010: Group có avgRating = 0.0 (dữ liệu cũ) -> scoring xem như 5.0 trung tính (F2)")
                void dispatch_group_with_zero_avg_rating_uses_neutral_5_0() {
                        sampleShift.setRemainingWorkers(5);

                        Worker leader = groupWorker("Leader Squad", KycStatus.APPROVED, true, 90);
                        List<Worker> members = List.of(
                                        leader,
                                        groupWorker("Member 1", KycStatus.APPROVED, true, 80),
                                        groupWorker("Member 2", KycStatus.APPROVED, true, 82),
                                        groupWorker("Member 3", KycStatus.APPROVED, true, 88),
                                        groupWorker("Member 4", KycStatus.APPROVED, true, 86));
                        WorkerGroup group = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội Dữ Liệu Cũ Rating Zero")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .avgRating(BigDecimal.ZERO)
                                        .isActive(true)
                                        .build();

                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(members.stream().map(m -> membership(group, m)).toList());

                        when(dispatchProposalRepository.save(any(DispatchProposal.class))).thenAnswer(inv -> {
                                DispatchProposal p = inv.getArgument(0);
                                p.setProposalId(UUID.randomUUID());
                                return p;
                        });

                        int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

                        assertEquals(1, count);
                        ArgumentCaptor<DispatchProposal> captor = ArgumentCaptor.forClass(DispatchProposal.class);
                        verify(dispatchProposalRepository).save(captor.capture());
                        assertTrue(captor.getValue().getMatchScore().compareTo(BigDecimal.valueOf(50)) > 0);
                }

                @Test
                @DisplayName("F1: dispatch_groups_considers_group_beyond_arbitrary_pool_limit -> gọi findAllByIsActiveTrue không bị giới hạn 50")
                void dispatch_groups_considers_group_beyond_arbitrary_pool_limit() {
                        sampleShift.setRemainingWorkers(5);

                        Worker leader = groupWorker("Leader Squad", KycStatus.APPROVED, true, 90);
                        List<Worker> members = List.of(
                                        leader,
                                        groupWorker("Member 1", KycStatus.APPROVED, true, 80),
                                        groupWorker("Member 2", KycStatus.APPROVED, true, 82),
                                        groupWorker("Member 3", KycStatus.APPROVED, true, 88),
                                        groupWorker("Member 4", KycStatus.APPROVED, true, 86));
                        WorkerGroup group = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội Party Nằm Ngoài Top 50 Cũ")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .isActive(true)
                                        .build();

                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));
                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(members.stream().map(m -> membership(group, m)).toList());

                        when(dispatchProposalRepository.save(any(DispatchProposal.class))).thenAnswer(inv -> {
                                DispatchProposal p = inv.getArgument(0);
                                p.setProposalId(UUID.randomUUID());
                                return p;
                        });

                        int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

                        assertEquals(1, count);
                        verify(workerGroupRepository).findAllByIsActiveTrue();
                        verify(dispatchProposalRepository).save(any(DispatchProposal.class));
                }

                @Test
                @DisplayName("GROUP004: Leader isOnline=false -> bỏ qua không gửi đề xuất & không bắn thông báo")
                void dispatch_groups_skips_when_leader_is_offline() {
                        Worker leader = groupWorker("Leader Offline", KycStatus.APPROVED, false, 90);
                        Worker member = groupWorker("Member Online", KycStatus.APPROVED, true, 88);
                        WorkerGroup group = WorkerGroup.builder()
                                        .groupId(UUID.randomUUID())
                                        .name("Đội Leader Offline")
                                        .leaderWorkerId(leader.getWorkerId())
                                        .isActive(true)
                                        .build();
                        lenient().when(workerRepository.findById(leader.getWorkerId())).thenReturn(Optional.of(leader));

                        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
                        when(workerGroupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
                        when(workerGroupMembershipRepository.findByWorkerGroup_GroupIdInAndStatus(anyList(),
                                        eq(MembershipStatus.ACTIVE)))
                                        .thenReturn(List.of(membership(group, member)));

                        int count = matchingDispatchService.dispatchGroupsForShift(sampleShift.getShiftId(), false);

                        assertEquals(0, count);
                        verify(dispatchProposalRepository, never()).save(any(DispatchProposal.class));
                        verify(notificationService, never()).createAndSend(any(), any(), any(), any(), any(), any(), any());
                }
        }
}
