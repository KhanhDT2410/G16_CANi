package com.cani.service.worker;

import com.cani.client.skill.CertificateLookupSource;
import com.cani.client.skill.CertificateLookupSource.CertificateLookupOutcome;
import com.cani.client.skill.CertificateLookupSource.CertificateLookupResult;
import com.cani.client.skill.UnsupportedCertificateLookupSource;
import com.cani.dto.skill.*;
import com.cani.entity.AuditLog;
import com.cani.entity.Skill;
import com.cani.entity.SkillVerificationRequest;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerSkill;
import com.cani.entity.WorkerSkillPoint;
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
import com.cani.service.skill.CertificateStorageService;
import com.cani.service.skill.DocumentAccessTokenService;
import com.cani.service.skill.FileSecurityValidator;
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
@DisplayName("SkillVerificationService — Automated Lookup Verification, Retry, Resubmit & Security Tests")
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
    private CertificateStorageService certificateStorageService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private CertificateLookupSource certificateLookupSource;

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

        lenient().when(certificateLookupSource.verifyCertificate(any()))
                .thenAnswer(inv -> new UnsupportedCertificateLookupSource().verifyCertificate(inv.getArgument(0)));

        verificationService = new SkillVerificationService(
                workerRepository,
                skillRepository,
                workerSkillRepository,
                workerSkillPointRepository,
                verificationRequestRepository,
                objectMapper,
                fileSecurityValidator,
                certificateStorageService,
                documentAccessTokenService,
                auditLogService,
                null,
                certificateLookupSource
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
    @DisplayName("Nguồn tra cứu xác thực thành công + khớp hồ sơ -> APPROVED (+25 SP), nâng cấp WorkerSkill & SkillPoint")
    void testSubmitVerification_VerifiedSourceMatch_ApprovedAndAwardsSp() {
        MockMultipartFile file = new MockMultipartFile("file", "cert_qr.jpg", "image/jpeg", validJpegBytes());

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findById(mockSkill.getSkillId())).thenReturn(Optional.of(mockSkill));
        when(certificateStorageService.storeDocument(any(), eq("jpg")))
                .thenReturn("/api/v1/workers/skills/documents/cert_verified.jpg");
        when(certificateLookupSource.verifyCertificate(any())).thenReturn(
                CertificateLookupResult.builder()
                        .outcome(CertificateLookupOutcome.VERIFIED)
                        .verificationSource("QR_OFFICIAL_REGISTRY")
                        .verificationReference("https://verify.example.invalid/cert/888")
                        .recipientName("NGUYEN VAN AN")
                        .institution("Saigon Bartender Academy")
                        .certificateNumber("SB-2026-888")
                        .expiryDate("31/12/2030")
                        .matchedSkillNames(List.of("Pha chế (Bartender/Barista)"))
                        .build()
        );
        when(workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, mockSkill.getSkillId()))
                .thenReturn(Optional.empty());
        when(workerSkillRepository.save(any(WorkerSkill.class))).thenAnswer(i -> i.getArgument(0));
        when(workerSkillPointRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, mockSkill.getSkillId()))
                .thenReturn(Optional.empty());
        when(workerSkillPointRepository.findByWorker_WorkerIdAndJobType(workerId, "Pha chế (Bartender/Barista)"))
                .thenReturn(Optional.empty());
        when(workerSkillPointRepository.save(any(WorkerSkillPoint.class))).thenAnswer(i -> i.getArgument(0));
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class))).thenAnswer(inv -> {
            SkillVerificationRequest req = inv.getArgument(0);
            if (req.getId() == null) req.setId(UUID.randomUUID());
            return req;
        });

        SkillVerificationResponseDto response = verificationService.submitVerification(
                workerId, file, mockSkill.getSkillId(), "QR certificate"
        );

        assertEquals(SkillVerificationStatus.APPROVED, response.getStatus());
        assertEquals(RoutingDecision.AUTO_APPROVE, response.getRoutingDecision());
        assertEquals("QR_OFFICIAL_REGISTRY", response.getVerificationSource());
        assertEquals(25, response.getSpAwarded());
        verify(workerSkillRepository, times(1)).save(any(WorkerSkill.class));
        verify(workerSkillPointRepository, times(1)).save(any(WorkerSkillPoint.class));
        verify(auditLogService, times(1)).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("Thông tin trên nguồn tra cứu không khớp tên Worker -> REJECTED và tuyệt đối không cộng SP")
    void testSubmitVerification_HolderMismatch_RejectedWithZeroSp() {
        MockMultipartFile file = new MockMultipartFile("file", "cert_other.jpg", "image/jpeg", validJpegBytes());

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findById(mockSkill.getSkillId())).thenReturn(Optional.of(mockSkill));
        when(certificateStorageService.storeDocument(any(), eq("jpg")))
                .thenReturn("/api/v1/workers/skills/documents/cert_mismatch.jpg");
        when(certificateLookupSource.verifyCertificate(any())).thenReturn(
                CertificateLookupResult.builder()
                        .outcome(CertificateLookupOutcome.VERIFIED)
                        .verificationSource("QR_OFFICIAL_REGISTRY")
                        .recipientName("TRAN THI BICH")
                        .institution("Saigon Bartender Academy")
                        .matchedSkillNames(List.of("Pha chế (Bartender/Barista)"))
                        .build()
        );
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class))).thenAnswer(inv -> {
            SkillVerificationRequest req = inv.getArgument(0);
            if (req.getId() == null) req.setId(UUID.randomUUID());
            return req;
        });

        SkillVerificationResponseDto response = verificationService.submitVerification(
                workerId, file, mockSkill.getSkillId(), null
        );

        assertEquals(SkillVerificationStatus.REJECTED, response.getStatus());
        assertEquals(RoutingDecision.REJECT, response.getRoutingDecision());
        assertEquals(0, response.getSpAwarded());
        assertTrue(response.getRoutingReason().contains("không khớp"));
        verify(workerSkillRepository, never()).save(any(WorkerSkill.class));
        verify(workerSkillPointRepository, never()).save(any(WorkerSkillPoint.class));
    }

    @Test
    @DisplayName("Thiếu nguồn tra cứu hỗ trợ -> REJECTED ('Chưa thể xác minh tự động'), không vào Admin queue, 0 SP")
    void testSubmitVerification_UnsupportedSource_ReturnsUnverifiedMessage() {
        MockMultipartFile file = new MockMultipartFile("file", "cert.jpg", "image/jpeg", validJpegBytes());

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findById(mockSkill.getSkillId())).thenReturn(Optional.of(mockSkill));
        when(certificateStorageService.storeDocument(any(), eq("jpg")))
                .thenReturn("/api/v1/workers/skills/documents/cert_111.jpg");
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class)))
                .thenAnswer(invocation -> {
                    SkillVerificationRequest req = invocation.getArgument(0);
                    if (req.getId() == null) req.setId(UUID.randomUUID());
                    return req;
                });

        SkillVerificationResponseDto response = verificationService.submitVerification(
                workerId, file, mockSkill.getSkillId(), "Chứng chỉ pha chế"
        );

        assertNotNull(response);
        assertEquals(RoutingDecision.REJECT, response.getRoutingDecision());
        assertEquals(SkillVerificationStatus.REJECTED, response.getStatus());
        assertEquals("UNSUPPORTED_SOURCE", response.getVerificationSource());
        assertTrue(response.getRoutingReason().contains("Chưa thể xác minh tự động"));
        assertEquals(0, response.getSpAwarded());
        assertTrue(response.getDocumentUrl().contains("expires=") && response.getDocumentUrl().contains("sig="));

        verify(workerSkillRepository, never()).save(any(WorkerSkill.class));
        verify(workerSkillPointRepository, never()).save(any(WorkerSkillPoint.class));
        verify(auditLogService, times(1)).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("Lỗi nguồn tra cứu tạm thời -> VERIFICATION_FAILED (0 SP) và Worker retry thành công -> APPROVED")
    void testSubmitAndRetry_TemporaryFailureRecoverableByWorkerRetry() {
        MockMultipartFile file = new MockMultipartFile("file", "cert_retry.jpg", "image/jpeg", validJpegBytes());
        UUID reqId = UUID.randomUUID();
        final SkillVerificationRequest[] storedReq = new SkillVerificationRequest[1];

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(skillRepository.findById(mockSkill.getSkillId())).thenReturn(Optional.of(mockSkill));
        when(certificateStorageService.storeDocument(any(), eq("jpg")))
                .thenReturn("/api/v1/workers/skills/documents/cert_retry.jpg");
        when(certificateStorageService.readBytes("cert_retry.jpg")).thenReturn(validJpegBytes());
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class))).thenAnswer(inv -> {
            SkillVerificationRequest req = inv.getArgument(0);
            if (req.getId() == null) req.setId(reqId);
            storedReq[0] = req;
            return req;
        });
        lenient().when(verificationRequestRepository.findById(reqId)).thenAnswer(i -> Optional.ofNullable(storedReq[0]));

        when(certificateLookupSource.verifyCertificate(any()))
                .thenReturn(CertificateLookupResult.builder()
                        .outcome(CertificateLookupOutcome.TEMPORARY_FAILURE)
                        .verificationSource("ISSUER_API")
                        .failureCode("ISSUER_API_TIMEOUT")
                        .reason("API đơn vị cấp tạm thời quá tải")
                        .build())
                .thenReturn(CertificateLookupResult.builder()
                        .outcome(CertificateLookupOutcome.VERIFIED)
                        .verificationSource("ISSUER_API")
                        .recipientName("Nguyen Van An")
                        .institution("Saigon Bartender Academy")
                        .matchedSkillNames(List.of("Pha chế (Bartender/Barista)"))
                        .build());

        SkillVerificationResponseDto first = verificationService.submitVerification(
                workerId, file, mockSkill.getSkillId(), "Retry test");
        assertEquals(SkillVerificationStatus.VERIFICATION_FAILED, first.getStatus());
        assertEquals(0, first.getSpAwarded());

        when(workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, mockSkill.getSkillId()))
                .thenReturn(Optional.empty());
        when(workerSkillRepository.save(any(WorkerSkill.class))).thenAnswer(i -> i.getArgument(0));
        when(workerSkillPointRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, mockSkill.getSkillId()))
                .thenReturn(Optional.empty());
        when(workerSkillPointRepository.findByWorker_WorkerIdAndJobType(workerId, "Pha chế (Bartender/Barista)"))
                .thenReturn(Optional.empty());
        when(workerSkillPointRepository.save(any(WorkerSkillPoint.class))).thenAnswer(i -> i.getArgument(0));

        SkillVerificationResponseDto retried = verificationService.retryVerification(reqId, workerId);
        assertEquals(SkillVerificationStatus.APPROVED, retried.getStatus());
        assertEquals(1, retried.getRetryCount());
        assertEquals(25, retried.getSpAwarded());
    }

    @Test
    @DisplayName("Resubmit từ REJECTED -> tra cứu lại tự động và cấp Signed URL mới")
    void testResubmitVerification_ReevaluatesWithLookupSource() {
        UUID reqId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "cert_new.pdf", "application/pdf", validPdfBytes());

        SkillVerificationRequest existing = SkillVerificationRequest.builder()
                .id(reqId)
                .worker(mockWorker)
                .skill(mockSkill)
                .jobType("Pha chế (Bartender/Barista)")
                .status(SkillVerificationStatus.REJECTED)
                .routingDecision(RoutingDecision.REJECT)
                .documentUrl("/api/v1/workers/skills/documents/old_cert.jpg")
                .spAwarded(0)
                .retryCount(0)
                .build();

        when(verificationRequestRepository.findById(reqId)).thenReturn(Optional.of(existing));
        when(certificateStorageService.storeDocument(any(), eq("pdf")))
                .thenReturn("/api/v1/workers/skills/documents/cert_new_999.pdf");
        when(certificateLookupSource.verifyCertificate(any())).thenReturn(
                CertificateLookupResult.builder()
                        .outcome(CertificateLookupOutcome.VERIFIED)
                        .verificationSource("DIGITAL_SIGNATURE")
                        .recipientName("Nguyen Van An")
                        .institution("Saigon Bartender Academy")
                        .matchedSkillNames(List.of("Pha chế (Bartender/Barista)"))
                        .build()
        );
        when(workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, mockSkill.getSkillId()))
                .thenReturn(Optional.empty());
        when(workerSkillRepository.save(any(WorkerSkill.class))).thenAnswer(i -> i.getArgument(0));
        when(workerSkillPointRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, mockSkill.getSkillId()))
                .thenReturn(Optional.empty());
        when(workerSkillPointRepository.findByWorker_WorkerIdAndJobType(workerId, "Pha chế (Bartender/Barista)"))
                .thenReturn(Optional.empty());
        when(workerSkillPointRepository.save(any(WorkerSkillPoint.class))).thenAnswer(i -> i.getArgument(0));
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class))).thenAnswer(i -> i.getArgument(0));

        SkillVerificationResponseDto response = verificationService.resubmitVerification(reqId, workerId, file, "Bản PDF có chữ ký số");

        assertEquals(SkillVerificationStatus.APPROVED, response.getStatus());
        assertEquals(RoutingDecision.AUTO_APPROVE, response.getRoutingDecision());
        assertEquals("DIGITAL_SIGNATURE", response.getVerificationSource());
        assertEquals(25, response.getSpAwarded());
        assertTrue(response.getDocumentUrl().startsWith("/api/v1/workers/skills/documents/cert_new_999.pdf?expires="));
        verify(auditLogService, times(1)).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("Idempotency: Chặn nộp trùng cùng tệp chứng chỉ (SHA-256) đã APPROVED -> 409 CONFLICT")
    void testSubmitVerification_DuplicateDocumentHash_ThrowsConflict() {
        MockMultipartFile file = new MockMultipartFile("file", "cert.jpg", "image/jpeg", validJpegBytes());
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(verificationRequestRepository.existsByWorker_WorkerIdAndDocumentHashAndStatusIn(
                eq(workerId), anyString(), anyCollection())).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () ->
                verificationService.submitVerification(workerId, file, mockSkill.getSkillId(), null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(certificateStorageService, never()).storeDocument(any(), any());
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
                .routingDecision(RoutingDecision.ADMIN_REVIEW)
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

        assertThrows(BadRequestException.class, () ->
                verificationService.revokeVerification(reqId, adminId, "Thu hồi lại"));
    }

    @Test
    @DisplayName("Document Ownership & Signed URL Security: Chặn Worker khác, chặn path traversal, cho phép Owner & Admin")
    void testLoadAuthorizedCertificateDocument_SecurityChecks() {
        String filename = "cert_owner123.jpg";
        String storedPath = "/api/v1/workers/skills/documents/" + filename;

        assertThrows(BadRequestException.class, () ->
                verificationService.loadAuthorizedCertificateDocument("../secret.jpg", mockUser, null, null));

        User otherUser = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).build();
        Worker otherWorker = Worker.builder().workerId(UUID.randomUUID()).user(otherUser).build();
        when(workerRepository.findByUser_UserId(otherUser.getUserId())).thenReturn(Optional.of(otherWorker));
        when(verificationRequestRepository.existsByWorker_WorkerIdAndDocumentUrl(otherWorker.getWorkerId(), storedPath))
                .thenReturn(false);

        AppException forbidden = assertThrows(AppException.class, () ->
                verificationService.loadAuthorizedCertificateDocument(filename, otherUser, null, null));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockWorker));
        when(verificationRequestRepository.existsByWorker_WorkerIdAndDocumentUrl(workerId, storedPath))
                .thenReturn(true);
        when(certificateStorageService.loadAsResource(filename)).thenReturn(new ByteArrayResource(validJpegBytes()));

        assertNotNull(verificationService.loadAuthorizedCertificateDocument(filename, mockUser, null, null));

        assertThrows(AppException.class, () ->
                verificationService.loadAuthorizedCertificateDocument(filename, null, null, null));
    }

    @Test
    @DisplayName("Document URL & JobType Integrity: PDF không chọn skillId có Signed documentUrl, jobType 'Chưa xác định' và REJECTED (UNSUPPORTED_SOURCE)")
    void testSubmitVerification_PopulatesSignedDocumentUrlAndUnspecifiedJobType() {
        MockMultipartFile file = new MockMultipartFile("file", "random_cv.pdf", "application/pdf", validPdfBytes());

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
        when(certificateStorageService.storeDocument(any(), eq("pdf")))
                .thenReturn("/api/v1/workers/skills/documents/cert_cv999.pdf");
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class))).thenAnswer(i -> {
            SkillVerificationRequest req = i.getArgument(0);
            if (req.getId() == null) req.setId(UUID.randomUUID());
            return req;
        });

        SkillVerificationResponseDto response = verificationService.submitVerification(workerId, file, null, null);

        assertNotNull(response.getDocumentUrl());
        assertTrue(response.getDocumentUrl().startsWith("/api/v1/workers/skills/documents/cert_cv999.pdf?expires="));
        assertEquals("Chưa xác định", response.getJobType());
        assertEquals(SkillVerificationStatus.REJECTED, response.getStatus());
        assertEquals("UNSUPPORTED_SOURCE", response.getVerificationSource());
        assertTrue(response.getRoutingReason().contains("Chưa thể xác minh tự động"));
        assertEquals(0, response.getSpAwarded());
    }

    @Test
    @DisplayName("getOrCreateCustomSkill: trả về kỹ năng chuẩn theo alias hoặc tạo đề xuất isActive=false, chặn rỗng")
    void getOrCreateCustomSkill_Tests() {
        when(skillRepository.findByNameIgnoreCase("pha chế (bartender/barista)")).thenReturn(Optional.of(mockSkill));
        Skill existing = verificationService.getOrCreateCustomSkill("  pha chế (bartender/barista)  ", "Đồ uống");
        assertEquals(mockSkill.getSkillId(), existing.getSkillId());
        verify(skillRepository, never()).save(any(Skill.class));

        when(skillRepository.findByNameIgnoreCase("Bartender")).thenReturn(Optional.empty());
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(mockSkill));
        Skill aliased = verificationService.getOrCreateCustomSkill("Bartender", null);
        assertEquals(mockSkill.getSkillId(), aliased.getSkillId());
        verify(skillRepository, never()).save(any(Skill.class));

        when(skillRepository.findByNameIgnoreCase("Lái xe nâng")).thenReturn(Optional.empty());
        when(skillRepository.save(any(Skill.class))).thenAnswer(i -> {
            Skill s = i.getArgument(0);
            s.setSkillId(UUID.randomUUID());
            return s;
        });
        Skill created = verificationService.getOrCreateCustomSkill("Lái xe nâng", null);
        assertEquals("Lái xe nâng", created.getName());
        assertEquals("Đề xuất", created.getCategory());
        assertFalse(Boolean.TRUE.equals(created.getIsActive()));

        assertThrows(BadRequestException.class, () -> verificationService.getOrCreateCustomSkill("   ", null));
    }
}
