package com.cani.service.worker;

import com.cani.service.*;

import com.cani.client.skill.SkillVisionAiClient;
import com.cani.dto.skill.*;
import com.cani.entity.Skill;
import com.cani.entity.SkillVerificationRequest;
import com.cani.entity.Worker;
import com.cani.entity.WorkerSkill;
import com.cani.entity.WorkerSkillPoint;
import com.cani.entity.enums.ExperienceLevel;
import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.SkillVerificationStatus;
import com.cani.entity.enums.VerificationTier;
import com.cani.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillVerificationServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private WorkerSkillRepository workerSkillRepository;

    @Mock
    private WorkerSkillPointRepository workerSkillPointRepository;

    @Mock
    private SkillVerificationRequestRepository verificationRequestRepository;

    @Mock
    private SkillVisionAiClient skillVisionAiClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SkillVerificationService verificationService;

    private UUID workerId;
    private Worker mockWorker;
    private Skill mockSkill;

    @BeforeEach
    void setUp() {
        workerId = UUID.randomUUID();
        mockWorker = Worker.builder()
                .workerId(workerId)
                .fullName("Nguyen Van An")
                .skillTier(SkillTier.STARTER)
                .build();


        mockSkill = Skill.builder()
                .skillId(UUID.randomUUID())
                .name("Pha chế (Bartender/Barista)")
                .category("F&B")
                .build();
    }

    @Test
    @DisplayName("Should auto-approve certificate, award +25 SP, create WorkerSkill record when confidence >= 0.85")
    void testSubmitVerification_AutoApprove() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cert.jpg", "image/jpeg", "dummy-certificate-bytes".getBytes()
        );

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findById(mockSkill.getSkillId())).thenReturn(Optional.of(mockSkill));
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(mockSkill));

        ExtractedSkillDto extracted = ExtractedSkillDto.builder()
                .skillName("Bartender")
                .taxonomyRole("Pha chế (Bartender/Barista)")
                .confidenceScore(0.95)
                .evidenceSnippet("Completed 40h bartender course")
                .build();

        SkillVerificationAiResultDto aiResult = SkillVerificationAiResultDto.builder()
                .recipientName("Nguyen Van An")
                .institution("BarSaver Academy")
                .issueDate("15/08/2024")
                .hasSealOrSignature(true)
                .overallConfidence(0.95)
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .routingReason("Valid official certificate")
                .extractedSkills(List.of(extracted))
                .build();

        when(skillVisionAiClient.verifyDocument(any(), any(), any())).thenReturn(aiResult);
        when(workerSkillPointRepository.findByWorker_WorkerIdAndJobType(eq(workerId), anyString()))
                .thenReturn(Optional.empty());

        when(verificationRequestRepository.save(any(SkillVerificationRequest.class)))
                .thenAnswer(invocation -> {
                    SkillVerificationRequest req = invocation.getArgument(0);
                    req.setId(UUID.randomUUID());
                    return req;
                });

        when(workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(eq(workerId), eq(mockSkill.getSkillId())))
                .thenReturn(Optional.empty());

        SkillVerificationResponseDto response = verificationService.submitVerification(
                workerId, file, mockSkill.getSkillId(), "Test note"
        );

        assertNotNull(response);
        assertEquals(RoutingDecision.AUTO_APPROVE, response.getRoutingDecision());
        assertEquals(SkillVerificationStatus.APPROVED, response.getStatus());
        assertEquals(25, response.getSpAwarded());

        verify(workerSkillRepository, times(1)).save(any(WorkerSkill.class));
        verify(workerSkillPointRepository, times(1)).save(any(WorkerSkillPoint.class));
        verify(workerRepository, times(1)).save(any(Worker.class));
        verify(verificationRequestRepository, times(1)).save(any(SkillVerificationRequest.class));
    }

    @Test
    @DisplayName("Should route to ADMIN_REVIEW when confidence is between 0.60 and 0.84")
    void testSubmitVerification_AdminReview() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cert_dim.jpg", "image/jpeg", "dim-certificate-bytes".getBytes()
        );

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(mockSkill));

        SkillVerificationAiResultDto aiResult = SkillVerificationAiResultDto.builder()
                .recipientName("Nguyen Van An")
                .institution("Training Center")
                .overallConfidence(0.72)
                .routingDecision(RoutingDecision.ADMIN_REVIEW)
                .routingReason("Evidence valid but seal is blurred")
                .build();

        when(skillVisionAiClient.verifyDocument(any(), any(), any())).thenReturn(aiResult);
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class)))
                .thenAnswer(invocation -> {
                    SkillVerificationRequest req = invocation.getArgument(0);
                    req.setId(UUID.randomUUID());
                    return req;
                });

        SkillVerificationResponseDto response = verificationService.submitVerification(
                workerId, file, null, "Medium confidence"
        );

        assertNotNull(response);
        assertEquals(RoutingDecision.ADMIN_REVIEW, response.getRoutingDecision());
        assertEquals(SkillVerificationStatus.PENDING_REVIEW, response.getStatus());
        assertEquals(0, response.getSpAwarded());

        verify(workerSkillPointRepository, never()).save(any(WorkerSkillPoint.class));
        verify(workerSkillRepository, never()).save(any(WorkerSkill.class));
    }

    @Test
    @DisplayName("Tier 1: Worker self-declares skills with proficiency, awarded 0 SP_verify (BR-16, BR-65)")
    void testDeclareSkills_Tier1_SelfDeclared() {
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findById(mockSkill.getSkillId())).thenReturn(Optional.of(mockSkill));
        when(workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, mockSkill.getSkillId()))
                .thenReturn(Optional.empty());

        when(workerSkillRepository.save(any(WorkerSkill.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeclareSkillRequestDto request = DeclareSkillRequestDto.builder()
                .skills(List.of(
                        DeclareSkillRequestDto.SkillItem.builder()
                                .skillId(mockSkill.getSkillId())
                                .proficiency(ExperienceLevel.INTERMEDIATE)
                                .build()
                ))
                .build();

        List<WorkerSkillResponseDto> result = verificationService.declareSkills(workerId, request);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(mockSkill.getSkillId(), result.get(0).getSkillId());
        assertEquals(ExperienceLevel.INTERMEDIATE, result.get(0).getProficiency());
        assertEquals(VerificationTier.SELF_DECLARED, result.get(0).getVerificationTier());
        assertEquals(0, result.get(0).getSpVerifyAwarded());

        verify(workerSkillRepository, times(1)).save(any(WorkerSkill.class));
    }


    @Test
    @DisplayName("BR-64 Single Source of Truth: Tier thresholds must match BR-64 specification")
    void testGetTierConfigs_BR64() {
        List<TierConfigDto> configs = verificationService.getTierConfigs();
        assertEquals(7, configs.size());

        assertEquals(SkillTier.STARTER, configs.get(0).getTier());
        assertEquals(0, configs.get(0).getMinSp());
        assertEquals(100, configs.get(0).getNextTierMinSp());

        assertEquals(SkillTier.BRONZE, configs.get(1).getTier());
        assertEquals(100, configs.get(1).getMinSp());
        assertEquals(200, configs.get(1).getNextTierMinSp());

        assertEquals(SkillTier.SILVER, configs.get(2).getTier());
        assertEquals(200, configs.get(2).getMinSp());
        assertEquals(350, configs.get(2).getNextTierMinSp());

        assertEquals(SkillTier.GOLD, configs.get(3).getTier());
        assertEquals(350, configs.get(3).getMinSp());
        assertEquals(500, configs.get(3).getNextTierMinSp());

        assertEquals(SkillTier.PLATINUM, configs.get(4).getTier());
        assertEquals(500, configs.get(4).getMinSp());
        assertEquals(700, configs.get(4).getNextTierMinSp());

        assertEquals(SkillTier.DIAMOND, configs.get(5).getTier());
        assertEquals(700, configs.get(5).getMinSp());
        assertEquals(900, configs.get(5).getNextTierMinSp());

        assertEquals(SkillTier.MASTER, configs.get(6).getTier());
        assertEquals(900, configs.get(6).getMinSp());
        assertNull(configs.get(6).getNextTierMinSp());
    }

    @Test
    @DisplayName("Algorithm Test: Fuzzy Vietnamese name matching handles accents, uppercase, and minor variations")
    void testNameMatchingAlgorithm() {
        assertTrue(SkillVerificationService.isNameMatching("Phạm Duy Hưng", "PHAM DUY HUNG"));
        assertTrue(SkillVerificationService.isNameMatching("Nguyen Van An", "nguyen van an"));
        assertTrue(SkillVerificationService.isNameMatching("Nguyễn Thị Mai", "NGUYỄN THỊ MAI"));
        assertTrue(SkillVerificationService.isNameMatching("Lê Văn Đức", "Le Duc")); // middle name omitted

        assertFalse(SkillVerificationService.isNameMatching("Nguyen Van An", "Tran Van B"));
        assertFalse(SkillVerificationService.isNameMatching("Phạm Duy Hưng", "Nguyen Van An"));
        assertFalse(SkillVerificationService.isNameMatching(null, "Nguyen Van An"));
        assertFalse(SkillVerificationService.isNameMatching("Nguyen Van An", null));
    }

    @Test
    @DisplayName("Fraud Defense: Identity mismatch between certificate and worker profile must force REJECT")
    void testSubmitVerification_NameMismatch_ForcesReject() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cert.jpg", "image/jpeg", "dummy-cert-bytes".getBytes()
        );

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(mockSkill));

        // Certificate belongs to another person: "Trần Văn Khác"
        SkillVerificationAiResultDto aiResult = SkillVerificationAiResultDto.builder()
                .recipientName("Trần Văn Khác")
                .institution("Trường Du lịch Saigontourist")
                .hasSealOrSignature(true)
                .overallConfidence(0.95)
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .routingReason("Valid certificate")
                .extractedSkills(List.of(ExtractedSkillDto.builder()
                        .skillName("Bartender")
                        .taxonomyRole("Pha chế (Bartender/Barista)")
                        .confidenceScore(0.95)
                        .build()))
                .build();

        when(skillVisionAiClient.verifyDocument(any(), any(), any())).thenReturn(aiResult);
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class)))
                .thenAnswer(invocation -> {
                    SkillVerificationRequest req = invocation.getArgument(0);
                    req.setId(UUID.randomUUID());
                    return req;
                });

        SkillVerificationResponseDto response = verificationService.submitVerification(
                workerId, file, mockSkill.getSkillId(), "Borrowing friend's cert"
        );

        assertNotNull(response);
        assertEquals(RoutingDecision.REJECT, response.getRoutingDecision());
        assertEquals(SkillVerificationStatus.REJECTED, response.getStatus());
        assertEquals(0, response.getSpAwarded());
        assertTrue(response.getRoutingReason().contains("không trùng khớp"));

        verify(workerSkillRepository, never()).save(any(WorkerSkill.class));
        verify(workerSkillPointRepository, never()).save(any(WorkerSkillPoint.class));
    }

    @Test
    @DisplayName("Admin Post-Audit: Admin revoking an approved verification deducts awarded SP and downgrades skill")
    void testRevokeVerification_AdminRevokeApprovedSkill() {
        UUID reqId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        SkillVerificationRequest approvedReq = SkillVerificationRequest.builder()
                .id(reqId)
                .worker(mockWorker)
                .skill(mockSkill)
                .jobType("Pha chế (Bartender/Barista)")
                .status(SkillVerificationStatus.APPROVED)
                .spAwarded(25)
                .build();

        WorkerSkillPoint wsp = WorkerSkillPoint.builder()
                .worker(mockWorker)
                .jobType("Pha chế (Bartender/Barista)")
                .spVerify(25)
                .totalSp(25)
                .tier(SkillTier.STARTER)
                .build();

        WorkerSkill ws = WorkerSkill.builder()
                .worker(mockWorker)
                .skill(mockSkill)
                .verificationTier(VerificationTier.AI_DOCUMENT)
                .spVerifyAwarded(25)
                .build();

        when(verificationRequestRepository.findById(reqId)).thenReturn(Optional.of(approvedReq));
        when(workerSkillPointRepository.findByWorker_WorkerIdAndJobType(workerId, "Pha chế (Bartender/Barista)"))
                .thenReturn(Optional.of(wsp));
        when(workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, mockSkill.getSkillId()))
                .thenReturn(Optional.of(ws));
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class))).thenAnswer(i -> i.getArgument(0));

        SkillVerificationResponseDto res = verificationService.revokeVerification(reqId, adminId, "Phát hiện chứng chỉ photoshop");

        assertNotNull(res);
        assertEquals(SkillVerificationStatus.REJECTED, res.getStatus());
        assertEquals(0, res.getSpAwarded());
        assertEquals(0, wsp.getSpVerify());
        assertEquals(0, wsp.getTotalSp());
        assertEquals(VerificationTier.SELF_DECLARED, ws.getVerificationTier());
        assertEquals(0, ws.getSpVerifyAwarded());
    }

    @Test
    @DisplayName("Admin Audit Log: Admin retrieves all verification records with full AI breakdown")
    void testGetAllVerificationsForAdmin() {
        SkillVerificationRequest req1 = SkillVerificationRequest.builder()
                .id(UUID.randomUUID())
                .worker(mockWorker)
                .status(SkillVerificationStatus.APPROVED)
                .institution("Trường Saigontourist")
                .hasSealOrSignature(true)
                .overallConfidence(java.math.BigDecimal.valueOf(0.92))
                .build();

        when(verificationRequestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(req1));

        List<SkillVerificationResponseDto> all = verificationService.getAllVerificationsForAdmin(null);
        assertEquals(1, all.size());
        assertEquals("Trường Saigontourist", all.get(0).getInstitution());
        assertTrue(all.get(0).getHasSealOrSignature());
    }
}
