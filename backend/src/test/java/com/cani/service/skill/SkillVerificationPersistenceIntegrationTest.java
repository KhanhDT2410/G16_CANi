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
    @DisplayName("Persistence & Idempotency: Hồ sơ mới vào PENDING_REVIEW duyệt thủ công + AuditLog + chặn nộp trùng SHA-256")
    void submitAndVerifyRealDatabasePersistenceAndDuplicateGuard() {
        Worker worker = workerRepository.findAll().stream().findFirst().orElseThrow();
        Skill barista = skillRepository.findByName("Pha chế (Bartender/Barista)").orElseThrow();

        long auditCountBefore = auditLogRepository.count();

        byte[] uniqueImage = validJpegBytes((byte) 0x77);
        MockMultipartFile file = new MockMultipartFile("file", "cert_real_persist.jpg", "image/jpeg", uniqueImage);

        SkillVerificationResponseDto res = verificationService.submitVerification(
                worker.getWorkerId(), file, barista.getSkillId(), "Integration test"
        );

        // 1. Query trực tiếp DB để xác nhận bền vững (Điều 8)
        SkillVerificationRequest persisted = requestRepository.findById(res.getId()).orElseThrow();
        assertEquals(SkillVerificationStatus.PENDING_REVIEW, persisted.getStatus());
        assertEquals(RoutingDecision.ADMIN_REVIEW, persisted.getRoutingDecision());
        assertEquals("MANUAL_REVIEW", persisted.getVerificationSource());
        assertEquals(0, persisted.getSpAwarded());
        assertNotNull(persisted.getDocumentHash());
        assertEquals(64, persisted.getDocumentHash().length());
        assertNotNull(persisted.getVersion());
        assertTrue(auditLogRepository.count() > auditCountBefore);

        // 2. Nộp lại y hệt file đó -> DB phát hiện trùng SHA-256 -> 409 CONFLICT
        MockMultipartFile dupFile = new MockMultipartFile("file", "cert_dup.jpg", "image/jpeg", uniqueImage);
        AppException conflict = assertThrows(AppException.class, () ->
                verificationService.submitVerification(worker.getWorkerId(), dupFile, barista.getSkillId(), "Dup"));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatus());
    }

    @Test
    @DisplayName("Legacy Readability & Manual Move: Hồ sơ lỗi cũ đọc được nguyên vẹn và chuyển sang PENDING_REVIEW")
    void legacyVerificationFailed_ReadableAndMovedToManualReview() {
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

        SkillVerificationResponseDto moved = verificationService.moveFailedToManualReview(
                legacyFailed.getId(), UUID.randomUUID());
        assertEquals(SkillVerificationStatus.PENDING_REVIEW, moved.getStatus());
        assertEquals(RoutingDecision.ADMIN_REVIEW, moved.getRoutingDecision());
        assertEquals("MANUAL_REVIEW", moved.getVerificationSource());
        assertNull(moved.getFailureReason());
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
