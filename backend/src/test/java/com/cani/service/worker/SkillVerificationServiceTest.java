package com.cani.service.worker;

import com.cani.client.skill.AiProviderException;
import com.cani.client.skill.SkillVisionAiClient;
import com.cani.dto.skill.*;
import com.cani.entity.AuditLog;
import com.cani.entity.Skill;
import com.cani.entity.SkillVerificationRequest;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerSkill;
import com.cani.entity.WorkerSkillPoint;
import com.cani.entity.enums.AdminReviewDecision;
import com.cani.entity.enums.ExperienceLevel;
import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.SkillVerificationStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.VerificationTier;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.repository.*;
import com.cani.service.AuditLogService;
import com.cani.service.SkillVerificationService;
import com.cani.service.skill.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SkillVerificationService V2 — Trust Engine, Fail-Closed, Admin Review, Concurrency & Security Tests")
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
    private CertificateStorageService certificateStorageService;

    @Mock
    private AuditLogService auditLogService;

    private SkillVerificationService verificationService;
    private DocumentAccessTokenService documentAccessTokenService;

    private UUID workerId;
    private UUID userId;
    private User mockUser;
    private Worker mockWorker;
    private Skill mockSkill;

    private static byte[] validJpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};
    }

    private static byte[] validPdfBytes() {
        return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);
    }

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        FileSecurityValidator fileSecurityValidator = new FileSecurityValidator();
        documentAccessTokenService = new DocumentAccessTokenService("unit-test-signing-secret-key", 300);
        SkillTrustScoringEngine trustScoringEngine = new SkillTrustScoringEngine(
                new AiOutputConsistencyValidator(),
                new TrustedIssuerRegistry()
        );

        verificationService = new SkillVerificationService(
                workerRepository,
                skillRepository,
                workerSkillRepository,
                workerSkillPointRepository,
                verificationRequestRepository,
                skillVisionAiClient,
                objectMapper,
                fileSecurityValidator,
                certificateStorageService,
                documentAccessTokenService,
                trustScoringEngine,
                auditLogService,
                null
        );

        userId = UUID.randomUUID();
        workerId = UUID.randomUUID();
        mockUser = User.builder()
                .userId(userId)
                .fullName("Nguyen Van An")
                .role(UserRole.WORKER)
                .build();

        mockWorker = Worker.builder()
                .workerId(workerId)
                .user(mockUser)
                .fullName("Nguyen Van An")
                .skillTier(SkillTier.STARTER)
                .build();

        mockSkill = Skill.builder()
                .skillId(UUID.randomUUID())
                .name("Pha chế (Bartender/Barista)")
                .category("F&B")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("AUTO_APPROVE: Truyền đủ expectedWorkerName vào AI Client, đạt TrustScore >= 85 -> APPROVED & +25 SP & AuditLog")
    void testSubmitVerification_AutoApprove() {
        MockMultipartFile file = new MockMultipartFile("file", "cert.jpg", "image/jpeg", validJpegBytes());

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findById(mockSkill.getSkillId())).thenReturn(Optional.of(mockSkill));
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(mockSkill));
        when(certificateStorageService.storeDocument(any(), eq("jpg")))
                .thenReturn("/api/v1/workers/skills/documents/cert_111.jpg");

        ExtractedSkillDto extracted = ExtractedSkillDto.builder()
                .skillName("Bartender")
                .taxonomyRole("Pha chế (Bartender/Barista)")
                .confidenceScore(0.95)
                .evidenceSnippet("Completed 40h bartender course")
                .build();

        SkillVerificationAiResultDto aiResult = SkillVerificationAiResultDto.builder()
                .documentType("CERTIFICATE")
                .documentReadable(true)
                .recipientName("Nguyen Van An")
                .institution("Trường Trung cấp Du lịch Saigontourist")
                .certificateNumber("SGT-2025-888")
                .issueDate("15/08/2025")
                .hasSeal(true)
                .hasSignature(true)
                .hasSealOrSignature(true)
                .identityMatch("EXACT")
                .documentAuthenticity("LIKELY_GENUINE_FORMAT")
                .skillRelevance("MATCH")
                .overallConfidence(0.95)
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .routingReason("Chứng chỉ hợp lệ từ Saigontourist")
                .extractedSkills(List.of(extracted))
                .modelName("gemini-2.5-flash")
                .promptVersion("v2.0.0")
                .build();

        when(skillVisionAiClient.verifyDocument(any(), eq("image/jpeg"), anyList(), eq("cert.jpg"), eq("Nguyen Van An")))
                .thenReturn(aiResult);
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
        assertNotNull(response.getTrustScore());
        assertTrue(response.getTrustScore() >= 85.0);
        assertTrue(response.getDocumentUrl().contains("expires=") && response.getDocumentUrl().contains("sig="));

        // Xác minh Bug 4.1 đã sửa: truyền đủ expectedWorkerName & filename
        verify(skillVisionAiClient).verifyDocument(any(), eq("image/jpeg"), anyList(), eq("cert.jpg"), eq("Nguyen Van An"));
        verify(workerSkillRepository, times(1)).save(any(WorkerSkill.class));
        verify(workerSkillPointRepository, times(1)).save(any(WorkerSkillPoint.class));
        verify(auditLogService, times(1)).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("Autonomous Engine: Hồ sơ độ tin cậy trung bình / tổ chức chưa xác minh -> REJECTED (0 SP) với hướng dẫn cụ thể, không bắt Admin duyệt tay")
    void testSubmitVerification_UnverifiedIssuer_PersistsAsRejectedWithFeedback() {
        MockMultipartFile file = new MockMultipartFile("file", "cert_dim.jpg", "image/jpeg", validJpegBytes());

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(mockSkill));
        when(certificateStorageService.storeDocument(any(), eq("jpg")))
                .thenReturn("/api/v1/workers/skills/documents/cert_222.jpg");

        SkillVerificationAiResultDto aiResult = SkillVerificationAiResultDto.builder()
                .documentType("CERTIFICATE")
                .documentReadable(true)
                .recipientName("Nguyen Van An")
                .institution("Independent Coffee Studio")
                .issueDate("10/10/2025")
                .hasSignature(true)
                .hasSealOrSignature(true)
                .overallConfidence(0.75)
                .routingDecision(RoutingDecision.REJECT)
                .routingReason("Tổ chức chưa xác minh và thiếu con dấu chính quy")
                .extractedSkills(List.of(ExtractedSkillDto.builder()
                        .skillName("Barista")
                        .taxonomyRole("Pha chế (Bartender/Barista)")
                        .confidenceScore(0.85)
                        .build()))
                .build();

        when(skillVisionAiClient.verifyDocument(any(), eq("image/jpeg"), anyList(), eq("cert_dim.jpg"), eq("Nguyen Van An")))
                .thenReturn(aiResult);
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
        assertEquals(RoutingDecision.REJECT, response.getRoutingDecision());
        assertEquals(SkillVerificationStatus.REJECTED, response.getStatus());
        assertEquals(0, response.getSpAwarded());

        verify(workerSkillPointRepository, never()).save(any(WorkerSkillPoint.class));
        verify(workerSkillRepository, never()).save(any(WorkerSkill.class));
    }

    @Test
    @DisplayName("Fail-Closed (Bug 4.2 Fixed): Khi AI gặp lỗi timeout/5xx/thiếu key -> lưu VERIFICATION_FAILED, 0 SP, KHÔNG tự duyệt")
    void testSubmitVerification_AiProviderFailure_FailClosed() {
        MockMultipartFile file = new MockMultipartFile("file", "cert.jpg", "image/jpeg", validJpegBytes());

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(mockSkill));
        when(certificateStorageService.storeDocument(any(), eq("jpg")))
                .thenReturn("/api/v1/workers/skills/documents/cert_fail.jpg");
        when(skillVisionAiClient.verifyDocument(any(), any(), anyList(), anyString(), anyString()))
                .thenThrow(new AiProviderException("API_TIMEOUT", true, "Quá thời gian chờ AI"));
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class)))
                .thenAnswer(inv -> {
                    SkillVerificationRequest r = inv.getArgument(0);
                    r.setId(UUID.randomUUID());
                    return r;
                });

        SkillVerificationResponseDto response = verificationService.submitVerification(workerId, file, null, null);

        assertEquals(SkillVerificationStatus.VERIFICATION_FAILED, response.getStatus());
        assertEquals(0, response.getSpAwarded());
        assertNotNull(response.getFailureReason());
        assertTrue(response.getFailureReason().contains("API_TIMEOUT"));
        assertNull(response.getRecipientName());
        assertNull(response.getInstitution());

        verify(workerSkillRepository, never()).save(any());
        verify(workerSkillPointRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotency: Chặn nộp trùng cùng tệp chứng chỉ (SHA-256) đang APPROVED/PENDING_REVIEW -> 409 CONFLICT")
    void testSubmitVerification_DuplicateDocumentHash_ThrowsConflict() {
        MockMultipartFile file = new MockMultipartFile("file", "cert.jpg", "image/jpeg", validJpegBytes());
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(verificationRequestRepository.existsByWorker_WorkerIdAndDocumentHashAndStatusIn(
                eq(workerId), anyString(), anyCollection())).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () ->
                verificationService.submitVerification(workerId, file, mockSkill.getSkillId(), null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(skillVisionAiClient, never()).verifyDocument(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Admin Review V2: Hỗ trợ APPROVE, REJECT, NEEDS_MORE_INFO và chặn cấp điểm 2 lần")
    void testAdminReview_AllDecisionsAndDoubleApproveGuard() {
        UUID reqId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        SkillVerificationRequest pendingReq = SkillVerificationRequest.builder()
                .id(reqId)
                .worker(mockWorker)
                .skill(mockSkill)
                .jobType("Pha chế (Bartender/Barista)")
                .status(SkillVerificationStatus.PENDING_REVIEW)
                .routingDecision(RoutingDecision.ADMIN_REVIEW)
                .spAwarded(0)
                .build();

        when(verificationRequestRepository.findById(reqId)).thenReturn(Optional.of(pendingReq));
        when(verificationRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // 1. NEEDS_MORE_INFO thiếu ghi chú -> BadRequest
        assertThrows(BadRequestException.class, () ->
                verificationService.adminReview(reqId, adminId, AdminReviewDecision.NEEDS_MORE_INFO, "  "));

        // 2. NEEDS_MORE_INFO có ghi chú -> thành công, 0 SP
        SkillVerificationResponseDto moreInfoRes = verificationService.adminReview(
                reqId, adminId, AdminReviewDecision.NEEDS_MORE_INFO, "Ảnh bị lóa góc phải, vui lòng chụp lại rõ nét.");
        assertEquals(SkillVerificationStatus.NEEDS_MORE_INFO, moreInfoRes.getStatus());
        assertEquals(0, moreInfoRes.getSpAwarded());
        assertTrue(moreInfoRes.getHumanReviewed());

        // 3. Đưa lại trạng thái PENDING_REVIEW rồi APPROVE
        pendingReq.setStatus(SkillVerificationStatus.PENDING_REVIEW);
        when(workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, mockSkill.getSkillId()))
                .thenReturn(Optional.empty());
        when(workerSkillPointRepository.findByWorker_WorkerIdAndJobType(workerId, "Pha chế (Bartender/Barista)"))
                .thenReturn(Optional.empty());

        SkillVerificationResponseDto approvedRes = verificationService.adminReview(
                reqId, adminId, AdminReviewDecision.APPROVE, "Đã kiểm tra mã chứng chỉ hợp lệ");
        assertEquals(SkillVerificationStatus.APPROVED, approvedRes.getStatus());
        assertEquals(25, approvedRes.getSpAwarded());
        assertNotNull(approvedRes.getReviewedAt());

        // 4. Gọi APPROVE lần 2 trên hồ sơ đã APPROVED -> bị chặn (chống cấp điểm nhiều lần)
        assertThrows(BadRequestException.class, () ->
                verificationService.adminReview(reqId, adminId, AdminReviewDecision.APPROVE, "Duyệt lại"));
    }

    @Test
    @DisplayName("Controlled Retry: Admin retry hồ sơ VERIFICATION_FAILED và chặn khi vượt quá 3 lần")
    void testRetryFailedVerification_ControlledRetryAndMaxLimit() {
        UUID reqId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        SkillVerificationRequest failedReq = SkillVerificationRequest.builder()
                .id(reqId)
                .worker(mockWorker)
                .skill(mockSkill)
                .jobType("Pha chế (Bartender/Barista)")
                .documentUrl("/api/v1/workers/skills/documents/cert_retry.jpg")
                .documentName("cert_retry.jpg")
                .documentType("image/jpeg")
                .status(SkillVerificationStatus.VERIFICATION_FAILED)
                .routingDecision(RoutingDecision.ADMIN_REVIEW)
                .retryCount(3)
                .spAwarded(0)
                .build();

        when(verificationRequestRepository.findById(reqId)).thenReturn(Optional.of(failedReq));

        // Đã chạm trần 3 lần -> chặn retry
        assertThrows(BadRequestException.class, () ->
                verificationService.retryFailedVerification(reqId, adminId));

        // Giảm retryCount về 1 -> retry thành công
        failedReq.setRetryCount(1);
        when(certificateStorageService.readBytes("cert_retry.jpg")).thenReturn(validJpegBytes());
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(mockSkill));
        when(skillVisionAiClient.verifyDocument(any(), eq("image/jpeg"), anyList(), eq("cert_retry.jpg"), eq("Nguyen Van An")))
                .thenReturn(SkillVerificationAiResultDto.builder()
                        .documentType("CERTIFICATE")
                        .documentReadable(true)
                        .recipientName("Nguyen Van An")
                        .institution("Saigontourist")
                        .issueDate("15/08/2025")
                        .hasSeal(true)
                        .hasSealOrSignature(true)
                        .overallConfidence(0.94)
                        .routingDecision(RoutingDecision.AUTO_APPROVE)
                        .extractedSkills(List.of(ExtractedSkillDto.builder()
                                .skillName("Barista")
                                .taxonomyRole("Pha chế (Bartender/Barista)")
                                .confidenceScore(0.95)
                                .build()))
                        .build());
        when(verificationRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, mockSkill.getSkillId()))
                .thenReturn(Optional.empty());
        when(workerSkillPointRepository.findByWorker_WorkerIdAndJobType(workerId, "Pha chế (Bartender/Barista)"))
                .thenReturn(Optional.empty());

        SkillVerificationResponseDto res = verificationService.retryFailedVerification(reqId, adminId);
        assertEquals(SkillVerificationStatus.APPROVED, res.getStatus());
        assertEquals(2, res.getRetryCount());
        assertEquals(25, res.getSpAwarded());
        assertNull(res.getFailureReason());
    }

    @Test
    @DisplayName("Tier 1: Worker self-declares skills with proficiency, awarded 0 SP_verify (BR-16, BR-65)")
    void testDeclareSkills_Tier1_SelfDeclared() {
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findById(mockSkill.getSkillId())).thenReturn(Optional.of(mockSkill));
        when(workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, mockSkill.getSkillId()))
                .thenReturn(Optional.empty());
        when(workerSkillRepository.save(any(WorkerSkill.class))).thenAnswer(i -> i.getArgument(0));

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
        assertEquals(VerificationTier.SELF_DECLARED, result.get(0).getVerificationTier());
        assertEquals(0, result.get(0).getSpVerifyAwarded());
    }

    @Test
    @DisplayName("BR-64 Single Source of Truth: Tier thresholds must match BR-64 specification")
    void testGetTierConfigs_BR64() {
        List<TierConfigDto> configs = verificationService.getTierConfigs();
        assertEquals(7, configs.size());
        assertEquals(SkillTier.STARTER, configs.get(0).getTier());
        assertEquals(0, configs.get(0).getMinSp());
        assertEquals(SkillTier.MASTER, configs.get(6).getTier());
        assertEquals(900, configs.get(6).getMinSp());
        assertNull(configs.get(6).getNextTierMinSp());
    }

    @Test
    @DisplayName("Algorithm Test: Strict Vietnamese name matching blocks conflicting middle names")
    void testNameMatchingAlgorithm() {
        assertTrue(SkillVerificationService.isNameMatching("Phạm Duy Hưng", "PHAM DUY HUNG"));
        assertTrue(SkillVerificationService.isNameMatching("Nguyen Van An", "nguyen van an"));
        assertTrue(SkillVerificationService.isNameMatching("Lê Văn Đức", "Le Duc"));

        assertFalse(SkillVerificationService.isNameMatching("Nguyễn Văn An", "Nguyễn Thị An"));
        assertFalse(SkillVerificationService.isNameMatching("Nguyen Van An", "Tran Van B"));
        assertFalse(SkillVerificationService.isNameMatching(null, "Nguyen Van An"));
    }

    @Test
    @DisplayName("Fraud Defense: Identity mismatch forces REJECT with 0 SP")
    void testSubmitVerification_NameMismatch_ForcesReject() {
        MockMultipartFile file = new MockMultipartFile("file", "cert.jpg", "image/jpeg", validJpegBytes());

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(mockSkill));
        when(certificateStorageService.storeDocument(any(), eq("jpg")))
                .thenReturn("/api/v1/workers/skills/documents/cert_mismatch.jpg");

        SkillVerificationAiResultDto aiResult = SkillVerificationAiResultDto.builder()
                .documentType("CERTIFICATE")
                .documentReadable(true)
                .recipientName("Trần Văn Khác")
                .institution("Trường Du lịch Saigontourist")
                .hasSeal(true)
                .hasSealOrSignature(true)
                .overallConfidence(0.95)
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .extractedSkills(List.of(ExtractedSkillDto.builder()
                        .skillName("Bartender")
                        .taxonomyRole("Pha chế (Bartender/Barista)")
                        .confidenceScore(0.95)
                        .build()))
                .build();

        when(skillVisionAiClient.verifyDocument(any(), any(), anyList(), anyString(), anyString())).thenReturn(aiResult);
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class)))
                .thenAnswer(inv -> {
                    SkillVerificationRequest req = inv.getArgument(0);
                    req.setId(UUID.randomUUID());
                    return req;
                });

        SkillVerificationResponseDto response = verificationService.submitVerification(
                workerId, file, mockSkill.getSkillId(), "Borrowing friend's cert"
        );

        assertEquals(RoutingDecision.REJECT, response.getRoutingDecision());
        assertEquals(SkillVerificationStatus.REJECTED, response.getStatus());
        assertEquals(0, response.getSpAwarded());
        assertTrue(response.getRoutingReason().contains("không trùng khớp"));
    }

    @Test
    @DisplayName("Admin Revoke V2: Chuyển sang REVOKED, trừ SP, hạ SkillTier, ghi AuditLog và chặn thu hồi lần 2")
    void testRevokeVerification_AdminRevokeApprovedSkill() {
        UUID reqId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        mockWorker.setSkillTier(SkillTier.BRONZE);

        SkillVerificationRequest approvedReq = SkillVerificationRequest.builder()
                .id(reqId)
                .worker(mockWorker)
                .skill(mockSkill)
                .jobType("Pha chế (Bartender/Barista)")
                .status(SkillVerificationStatus.APPROVED)
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .spAwarded(25)
                .build();

        WorkerSkillPoint wsp = WorkerSkillPoint.builder()
                .worker(mockWorker)
                .jobType("Pha chế (Bartender/Barista)")
                .spVerify(25)
                .spExperience(80)
                .spRating(0)
                .totalSp(105)
                .tier(SkillTier.BRONZE)
                .build();

        WorkerSkill ws = WorkerSkill.builder()
                .worker(mockWorker)
                .skill(mockSkill)
                .verificationTier(VerificationTier.AI_DOCUMENT)
                .spVerifyAwarded(25)
                .evidenceRequestId(reqId)
                .build();

        when(verificationRequestRepository.findById(reqId)).thenReturn(Optional.of(approvedReq));
        when(workerSkillRepository.findByEvidenceRequestId(reqId)).thenReturn(List.of(ws));
        when(workerSkillPointRepository.findByWorker_WorkerIdAndJobType(workerId, "Pha chế (Bartender/Barista)"))
                .thenReturn(Optional.of(wsp));
        when(workerSkillPointRepository.findByWorker_WorkerId(workerId)).thenReturn(List.of(wsp));
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class))).thenAnswer(i -> i.getArgument(0));

        SkillVerificationResponseDto res = verificationService.revokeVerification(reqId, adminId, "Phát hiện chứng chỉ photoshop");

        assertEquals(SkillVerificationStatus.REVOKED, res.getStatus());
        assertEquals(0, res.getSpAwarded());
        assertEquals(0, wsp.getSpVerify());
        assertEquals(80, wsp.getTotalSp());
        assertEquals(SkillTier.STARTER, wsp.getTier());
        assertEquals(SkillTier.STARTER, mockWorker.getSkillTier());
        assertEquals(VerificationTier.SELF_DECLARED, ws.getVerificationTier());
        assertEquals(0, ws.getSpVerifyAwarded());
        verify(auditLogService, times(1)).logAction(any(AuditLog.class));

        // Chặn thu hồi lần 2
        assertThrows(BadRequestException.class, () ->
                verificationService.revokeVerification(reqId, adminId, "Thu hồi lại"));
    }

    @Test
    @DisplayName("Document Ownership & Signed URL Security: Chặn Worker khác, chặn path traversal, cho phép Owner & Admin")
    void testLoadAuthorizedCertificateDocument_SecurityChecks() {
        String filename = "cert_owner123.jpg";
        String storedPath = "/api/v1/workers/skills/documents/" + filename;

        // 1. Path traversal -> BadRequest
        assertThrows(BadRequestException.class, () ->
                verificationService.loadAuthorizedCertificateDocument("../secret.jpg", mockUser, null, null));

        // 2. Worker khác truy cập -> 403 FORBIDDEN
        User otherUser = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).build();
        Worker otherWorker = Worker.builder().workerId(UUID.randomUUID()).user(otherUser).build();
        when(workerRepository.findByUser_UserId(otherUser.getUserId())).thenReturn(Optional.of(otherWorker));
        when(verificationRequestRepository.existsByWorker_WorkerIdAndDocumentUrl(otherWorker.getWorkerId(), storedPath))
                .thenReturn(false);

        AppException forbidden = assertThrows(AppException.class, () ->
                verificationService.loadAuthorizedCertificateDocument(filename, otherUser, null, null));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());

        // 3. Owner truy cập -> OK
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockWorker));
        when(verificationRequestRepository.existsByWorker_WorkerIdAndDocumentUrl(workerId, storedPath))
                .thenReturn(true);
        when(certificateStorageService.loadAsResource(filename)).thenReturn(new ByteArrayResource(validJpegBytes()));

        assertNotNull(verificationService.loadAuthorizedCertificateDocument(filename, mockUser, null, null));

        // 4. Người chưa đăng nhập không có signed token -> 403 FORBIDDEN
        assertThrows(AppException.class, () ->
                verificationService.loadAuthorizedCertificateDocument(filename, null, null, null));
    }

    @Test
    @DisplayName("Document URL & JobType Integrity: PDF CV bị từ chối có Signed documentUrl và jobType 'Chưa xác định'")
    void testSubmitVerification_PopulatesSignedDocumentUrlAndUnspecifiedJobType() {
        MockMultipartFile file = new MockMultipartFile("file", "random_cv.pdf", "application/pdf", validPdfBytes());
        SkillVerificationAiResultDto rejectAi = SkillVerificationAiResultDto.builder()
                .documentType("CV")
                .overallConfidence(0.20)
                .routingDecision(RoutingDecision.REJECT)
                .routingReason("Tài liệu tải lên là CV tự khai.")
                .extractedSkills(List.of())
                .build();

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(mockSkill));
        when(certificateStorageService.storeDocument(any(), eq("pdf")))
                .thenReturn("/api/v1/workers/skills/documents/cert_cv999.pdf");
        when(skillVisionAiClient.verifyDocument(any(), eq("application/pdf"), anyList(), eq("random_cv.pdf"), eq("Nguyen Van An")))
                .thenReturn(rejectAi);
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class))).thenAnswer(i -> {
            SkillVerificationRequest req = i.getArgument(0);
            req.setId(UUID.randomUUID());
            return req;
        });

        SkillVerificationResponseDto response = verificationService.submitVerification(workerId, file, null, null);

        assertNotNull(response.getDocumentUrl());
        assertTrue(response.getDocumentUrl().startsWith("/api/v1/workers/skills/documents/cert_cv999.pdf?expires="));
        assertEquals("Chưa xác định", response.getJobType());
        assertEquals(SkillVerificationStatus.REJECTED, response.getStatus());
    }

    @Test
    @DisplayName("getOrCreateCustomSkill: trả về kỹ năng tồn tại hoặc tạo mới, chặn rỗng")
    void getOrCreateCustomSkill_Tests() {
        when(skillRepository.findByNameIgnoreCase("pha chế (bartender/barista)")).thenReturn(Optional.of(mockSkill));
        Skill existing = verificationService.getOrCreateCustomSkill("  pha chế (bartender/barista)  ", "Đồ uống");
        assertEquals(mockSkill.getSkillId(), existing.getSkillId());
        verify(skillRepository, never()).save(any(Skill.class));

        when(skillRepository.findByNameIgnoreCase("Lái xe nâng")).thenReturn(Optional.empty());
        when(skillRepository.save(any(Skill.class))).thenAnswer(i -> {
            Skill s = i.getArgument(0);
            s.setSkillId(UUID.randomUUID());
            return s;
        });
        Skill created = verificationService.getOrCreateCustomSkill("Lái xe nâng", null);
        assertEquals("Lái xe nâng", created.getName());
        assertEquals("Tự chọn", created.getCategory());

        assertThrows(BadRequestException.class, () -> verificationService.getOrCreateCustomSkill("   ", null));
    }
}
