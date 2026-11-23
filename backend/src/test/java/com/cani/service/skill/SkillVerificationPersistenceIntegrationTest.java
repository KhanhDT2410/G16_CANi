package com.cani.service.skill;

import com.cani.dto.skill.SkillVerificationResponseDto;
import com.cani.entity.Skill;
import com.cani.entity.SkillVerificationRequest;
import com.cani.entity.Worker;
import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillVerificationStatus;
import com.cani.exception.AppException;
import com.cani.repository.AuditLogRepository;
import com.cani.repository.SkillRepository;
import com.cani.repository.SkillVerificationRequestRepository;
import com.cani.repository.WorkerRepository;
import com.cani.service.SkillVerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test (H2 profile): Xác thực toàn bộ Spring Context V2, JPA mapping các cột V2,
 * lưu trữ thực tế vào CSDL (skill_verification_requests + audit_logs), chống nộp trùng SHA-256
 * và Optimistic Locking (@Version) khi xử lý đồng thời.
 */
@SpringBootTest(properties = {
        "cani.firebase.enabled=false",
        "cani.storage.certificate-dir=target/test-uploads/certificates"
})
@ActiveProfiles("h2")
@DisplayName("SkillVerification V2 — Real JPA Persistence, Duplicate Hash & Optimistic Locking Integration Tests")
class SkillVerificationPersistenceIntegrationTest {

    @Autowired
    private SkillVerificationService verificationService;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private com.cani.repository.UserRepository userRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillVerificationRequestRepository requestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static byte[] validJpegBytes(byte marker) {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, marker};
    }

    @Test
    @DisplayName("Persistence & Honest Lookup: Boot không cần GEMINI_API_KEY, nguồn chưa tích hợp -> REJECTED ('Chưa thể xác minh tự động', 0 SP)")
    void submitAndVerifyRealDatabasePersistenceAndDuplicateGuard() {
        Worker worker = workerRepository.findAll().stream().findFirst().orElseThrow();
        Skill barista = skillRepository.findByName("Pha chế (Bartender/Barista)").orElseThrow();

        long auditCountBefore = auditLogRepository.count();

        byte[] uniqueImage = validJpegBytes((byte) 0x77);
        MockMultipartFile file = new MockMultipartFile("file", "cert_real_persist.jpg", "image/jpeg", uniqueImage);

        SkillVerificationResponseDto res = verificationService.submitVerification(
                worker.getWorkerId(), file, barista.getSkillId(), "Integration test"
        );

        // 1. Query trực tiếp DB để xác nhận bền vững và tuyệt đối không tự duyệt khi thiếu nguồn tra cứu
        SkillVerificationRequest persisted = requestRepository.findById(res.getId()).orElseThrow();
        assertEquals(SkillVerificationStatus.REJECTED, persisted.getStatus());
        assertEquals(RoutingDecision.REJECT, persisted.getRoutingDecision());
        assertEquals("UNSUPPORTED_SOURCE", persisted.getVerificationSource());
        assertTrue(persisted.getRoutingReason().contains("Chưa thể xác minh tự động"));
        assertEquals(0, persisted.getSpAwarded());
        assertNotNull(persisted.getDocumentHash());
        assertEquals(64, persisted.getDocumentHash().length());
        assertNotNull(persisted.getVersion());
        assertTrue(auditLogRepository.count() > auditCountBefore);

        // 2. Nộp lại tệp mới qua resubmitVerification -> vẫn kiểm tra trung thực và cập nhật DB
        byte[] secondImage = validJpegBytes((byte) 0x88);
        MockMultipartFile secondFile = new MockMultipartFile("file", "cert_resubmit.jpg", "image/jpeg", secondImage);
        SkillVerificationResponseDto resubmitted = verificationService.resubmitVerification(
                persisted.getId(), worker.getWorkerId(), secondFile, "Resubmit");
        assertEquals(SkillVerificationStatus.REJECTED, resubmitted.getStatus());
        assertEquals(0, resubmitted.getSpAwarded());
        assertTrue(resubmitted.getRoutingReason().contains("Chưa thể xác minh tự động"));
    }

    @Test
    @DisplayName("Legacy Readability & Retry: Hồ sơ VERIFICATION_FAILED cũ đọc được nguyên vẹn và Worker thử lại không qua Admin")
    void legacyVerificationFailed_ReadableAndRetryable() {
        Worker worker = workerRepository.findAll().stream().findFirst().orElseThrow();
        SkillVerificationRequest legacyFailed = requestRepository.saveAndFlush(SkillVerificationRequest.builder()
                .worker(worker)
                .jobType("Pha chế (Bartender/Barista)")
                .modelName("legacy-ai-model")
                .promptVersion("v1.0.0")
                .failureReason("API_TIMEOUT: Legacy timeout")
                .routingDecision(RoutingDecision.ADMIN_REVIEW)
                .status(SkillVerificationStatus.VERIFICATION_FAILED)
                .spAwarded(0)
                .build());

        com.cani.entity.User adminReviewer = com.cani.entity.User.builder()
                .userId(UUID.randomUUID())
                .role(com.cani.entity.enums.UserRole.ADMIN)
                .build();
        SkillVerificationResponseDto detail = verificationService.getVerificationDetail(
                legacyFailed.getId(), adminReviewer);
        assertEquals("legacy-ai-model", detail.getModelName());
        assertEquals(SkillVerificationStatus.VERIFICATION_FAILED, detail.getStatus());

        byte[] newImage = validJpegBytes((byte) 0x99);
        MockMultipartFile replacement = new MockMultipartFile("file", "cert_legacy_resubmit.jpg", "image/jpeg", newImage);
        SkillVerificationResponseDto resubmitted = verificationService.resubmitVerification(
                legacyFailed.getId(), worker.getWorkerId(), replacement, "Nộp lại hồ sơ cũ");
        assertEquals(SkillVerificationStatus.REJECTED, resubmitted.getStatus());
        assertEquals("UNSUPPORTED_SOURCE", resubmitted.getVerificationSource());
        assertEquals(0, resubmitted.getSpAwarded());
    }

    @Test
    @DisplayName("Concurrency: Optimistic Locking (@Version) chặn ghi đè đồng thời trên cùng SkillVerificationRequest")
    void optimisticLocking_ConcurrentModificationThrowsException() {
        Worker worker = workerRepository.findAll().stream().findFirst().orElseThrow();
        SkillVerificationRequest created = requestRepository.saveAndFlush(SkillVerificationRequest.builder()
                .worker(worker)
                .jobType("Pha chế (Bartender/Barista)")
                .routingDecision(RoutingDecision.ADMIN_REVIEW)
                .status(SkillVerificationStatus.PENDING_REVIEW)
                .spAwarded(0)
                .build());

        UUID id = created.getId();
        SkillVerificationRequest copy1 = requestRepository.findById(id).orElseThrow();
        SkillVerificationRequest copy2 = requestRepository.findById(id).orElseThrow();

        copy1.setReviewNotes("Admin 1 updated");
        requestRepository.saveAndFlush(copy1);

        copy2.setReviewNotes("Admin 2 stale update");
        assertThrows(ObjectOptimisticLockingFailureException.class, () ->
                requestRepository.saveAndFlush(copy2));
    }
}
