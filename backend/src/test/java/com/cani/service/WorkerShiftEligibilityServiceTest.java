package com.cani.service;

import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkerShiftEligibilityService V3 Unit Tests")
class WorkerShiftEligibilityServiceTest {

    @Mock
    private WorkerSkillRepository workerSkillRepository;

    @Mock
    private ShiftSkillRequirementRepository shiftSkillRequirementRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private WorkerAvailabilityRepository workerAvailabilityRepository;

    @Mock
    private BlockListService blockListService;

    @Mock
    private SkillRepository skillRepository;

    private WorkerShiftEligibilityService service;
    private Worker worker;
    private Shift shift;
    private Skill skillRequired;
    private Skill skillPreferred;

    @BeforeEach
    void setUp() {
        service = new WorkerShiftEligibilityService(
                workerSkillRepository,
                shiftSkillRequirementRepository,
                shiftAssignmentRepository,
                workerAvailabilityRepository,
                blockListService,
                skillRepository
        );

        UUID eoUserId = UUID.randomUUID();
        User eoUser = User.builder().userId(eoUserId).status(UserStatus.ACTIVE).build();
        EventOrganizer eo = EventOrganizer.builder().eoId(UUID.randomUUID()).user(eoUser).build();
        Event event = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(eo).status(EventStatus.ACTIVE).build();

        skillRequired = Skill.builder().skillId(UUID.randomUUID()).name("Phục vụ bàn").isActive(true).build();
        skillPreferred = Skill.builder().skillId(UUID.randomUUID()).name("Tiếng Anh giao tiếp").isActive(true).build();

        User wUser = User.builder().userId(UUID.randomUUID()).status(UserStatus.ACTIVE).build();
        worker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(wUser)
                .kycStatus(KycStatus.APPROVED)
                .isOnline(true)
                .reliabilityScore(90)
                .totalShiftsCompleted(5)
                .minWage(BigDecimal.ZERO)
                .build();

        shift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(event)
                .jobType("Phục vụ bàn")
                .status(ShiftStatus.ACTIVE)
                .startTime(Instant.now().plus(12, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(16, ChronoUnit.HOURS))
                .hourlyWage(BigDecimal.valueOf(60000))
                .minReliabilityScore(70)
                .emergency(true)
                .build();
    }

    @Test
    @DisplayName("Emergency Apply KHÔNG được bypass kỹ năng bắt buộc, eKYC hay điểm uy tín")
    void emergencyApply_enforcesRequiredSkillsAndEkyc() {
        ShiftSkillRequirement req = ShiftSkillRequirement.builder()
                .shift(shift)
                .skill(skillRequired)
                .requirementType(SkillRequirementType.REQUIRED)
                .minVerificationTier(VerificationTier.AI_DOCUMENT)
                .requiresValidCertificate(false)
                .build();

        when(shiftSkillRequirementRepository.findByShiftIdWithSkill(shift.getShiftId())).thenReturn(List.of(req));
        when(workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId())).thenReturn(List.of(
                WorkerSkill.builder()
                        .worker(worker)
                        .skill(skillRequired)
                        .verificationTier(VerificationTier.SELF_DECLARED)
                        .build()
        ));

        WorkerShiftEligibilityService.EligibilityResult res = service.evaluate(
                worker, shift, WorkerShiftEligibilityService.MatchingChannel.EMERGENCY_APPLY
        );
        assertFalse(res.eligible());
        assertEquals("INSUFFICIENT_VERIFICATION_TIER", res.reasonCode());

        // Nâng lên AI_DOCUMENT còn hạn -> pass
        when(workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId())).thenReturn(List.of(
                WorkerSkill.builder()
                        .worker(worker)
                        .skill(skillRequired)
                        .verificationTier(VerificationTier.AI_DOCUMENT)
                        .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                        .build()
        ));

        WorkerShiftEligibilityService.EligibilityResult ok = service.evaluate(
                worker, shift, WorkerShiftEligibilityService.MatchingChannel.EMERGENCY_APPLY
        );
        assertTrue(ok.eligible());
    }

    @Test
    @DisplayName("Proposal Acceptance chặn nhận đề xuất cũ nếu chứng chỉ kỹ năng đã hết hạn/bị thu hồi")
    void proposalAcceptance_blocksWhenCertExpiredEvenIfWorkerOffline() {
        worker.setIsOnline(false); // Chấp nhận proposal không bắt buộc worker phải giữ online

        ShiftSkillRequirement req = ShiftSkillRequirement.builder()
                .shift(shift)
                .skill(skillRequired)
                .requirementType(SkillRequirementType.REQUIRED)
                .minVerificationTier(VerificationTier.AI_DOCUMENT)
                .requiresValidCertificate(true)
                .build();

        when(shiftSkillRequirementRepository.findByShiftIdWithSkill(shift.getShiftId())).thenReturn(List.of(req));
        when(workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId())).thenReturn(List.of(
                WorkerSkill.builder()
                        .worker(worker)
                        .skill(skillRequired)
                        .verificationTier(VerificationTier.AI_DOCUMENT)
                        .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                        .evidenceRequestId(UUID.randomUUID())
                        .build()
        ));

        WorkerShiftEligibilityService.EligibilityResult res = service.evaluate(
                worker, shift, WorkerShiftEligibilityService.MatchingChannel.PROPOSAL_ACCEPTANCE
        );
        assertFalse(res.eligible());
        assertEquals("CERTIFICATE_REQUIRED_OR_EXPIRED", res.reasonCode());
    }

    @Test
    @DisplayName("Invite Mode không bao giờ bypass eKYC hoặc chứng chỉ an toàn, nhưng cho phép override kỹ năng thường nếu có lý do")
    void inviteMode_rulesAndOverrideBehavior() {
        ShiftSkillRequirement nonSafetyReq = ShiftSkillRequirement.builder()
                .shift(shift)
                .skill(skillRequired)
                .requirementType(SkillRequirementType.REQUIRED)
                .minVerificationTier(VerificationTier.SELF_DECLARED)
                .requiresValidCertificate(false)
                .build();

        when(shiftSkillRequirementRepository.findByShiftIdWithSkill(shift.getShiftId())).thenReturn(List.of(nonSafetyReq));
        when(workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId())).thenReturn(List.of());

        // Không có overrideReason -> fail
        assertFalse(service.evaluate(worker, shift, WorkerShiftEligibilityService.MatchingChannel.INVITE_MODE, null).eligible());

        // Có overrideReason -> pass với flag overrideApplied = true
        WorkerShiftEligibilityService.EligibilityResult overridden = service.evaluate(
                worker, shift, WorkerShiftEligibilityService.MatchingChannel.INVITE_MODE, "Nhân sự quen thuộc từng làm sự kiện"
        );
        assertTrue(overridden.eligible());
        assertTrue(overridden.overrideApplied());

        // Chứng chỉ bắt buộc an toàn (requiresValidCertificate = true) -> từ chối KỂ CẢ KHI có overrideReason
        nonSafetyReq.setRequiresValidCertificate(true);
        WorkerShiftEligibilityService.EligibilityResult blockedSafety = service.evaluate(
                worker, shift, WorkerShiftEligibilityService.MatchingChannel.INVITE_MODE, "Ngoại lệ"
        );
        assertFalse(blockedSafety.eligible());
        assertEquals("CERTIFICATE_REQUIRED_OR_EXPIRED", blockedSafety.reasonCode());

        // Chưa eKYC -> từ chối KỂ CẢ KHI có overrideReason
        nonSafetyReq.setRequiresValidCertificate(false);
        worker.setKycStatus(KycStatus.PENDING);
        WorkerShiftEligibilityService.EligibilityResult blockedKyc = service.evaluate(
                worker, shift, WorkerShiftEligibilityService.MatchingChannel.INVITE_MODE, "Ngoại lệ"
        );
        assertFalse(blockedKyc.eligible());
        assertEquals("EKYC_REQUIRED", blockedKyc.reasonCode());
    }

    @Test
    @DisplayName("SkillFitBonus tính đúng 6 * RequiredQuality + 4 * PreferredFit, bằng 0 khi không có yêu cầu")
    void skillFitBonus_computesExactFormula() {
        // 1. Ca không có yêu cầu -> bonus = 0
        when(shiftSkillRequirementRepository.findByShiftIdWithSkill(shift.getShiftId())).thenReturn(List.of());
        assertEquals(0.0, service.evaluateSkillFit(worker, shift).skillFitBonus(), 0.001);

        // 2. Ca có 1 REQUIRED (AI_DOCUMENT -> 0.5) + 1 PREFERRED (PLATFORM_PROVEN -> 1.0)
        ShiftSkillRequirement r1 = ShiftSkillRequirement.builder()
                .shift(shift).skill(skillRequired)
                .requirementType(SkillRequirementType.REQUIRED)
                .minVerificationTier(VerificationTier.SELF_DECLARED).build();
        ShiftSkillRequirement r2 = ShiftSkillRequirement.builder()
                .shift(shift).skill(skillPreferred)
                .requirementType(SkillRequirementType.PREFERRED)
                .minVerificationTier(VerificationTier.SELF_DECLARED).build();

        when(shiftSkillRequirementRepository.findByShiftIdWithSkill(shift.getShiftId())).thenReturn(List.of(r1, r2));
        when(workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId())).thenReturn(List.of(
                WorkerSkill.builder().worker(worker).skill(skillRequired)
                        .verificationTier(VerificationTier.AI_DOCUMENT)
                        .expiresAt(Instant.now().plus(10, ChronoUnit.DAYS)).build(),
                WorkerSkill.builder().worker(worker).skill(skillPreferred)
                        .verificationTier(VerificationTier.PLATFORM_PROVEN).build()
        ));

        WorkerShiftEligibilityService.SkillFitResult fit = service.evaluateSkillFit(worker, shift);
        assertEquals(0.5, fit.requiredQuality(), 0.001);
        assertEquals(1.0, fit.preferredFit(), 0.001);
        assertEquals(7.0, fit.skillFitBonus(), 0.001); // 6*0.5 + 4*1.0 = 7.0
    }
}
