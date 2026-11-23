package com.cani.service.ekyc;

import com.cani.client.ekyc.EkycProvider;
import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.*;
import com.cani.entity.*;
import com.cani.entity.enums.UserRole;
import com.cani.repository.*;
import com.cani.service.WorkerEkycService;
import com.cani.service.EventOrganizerEkycService;
import com.cani.service.AuditLogService;
import com.cani.service.ekyc.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;

import java.nio.file.Path;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@org.springframework.boot.autoconfigure.ImportAutoConfiguration(org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class)
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true", "spring.flyway.target=58", "cani.security.ekyc-hash-pepper=synthetic-test-only",
        "spring.datasource.driver-class-name=org.postgresql.Driver", "cani.ekyc.strict=false",
        "cani.ekyc.vnpt.require-liveness-for-auto-approve=true",
        "cani.ekyc.retention.approved-image-days=30"})
@ActiveProfiles("pg-audit")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({WorkerEkycService.class, EventOrganizerEkycService.class, EkycDecisionEngine.class, VnptEkycProperties.class,
        EkycIdentityCryptoService.class, EkycImageValidator.class, LocalPrivateEkycImageStorage.class,
        EkycImageAccessTokenService.class, EkycImageRetentionService.class, EkycCccdLockManager.class, AuditLogService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "CANI_AUDIT_PG_PORT", matches = "[0-9]+")
class EkycPostgresPersistenceTest {
    @TempDir static Path storage;
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + System.getenv("CANI_AUDIT_PG_PORT") + "/cani_audit");
        p.add("spring.datasource.username", () -> "postgres");
        p.add("spring.datasource.password", () -> System.getenv("CANI_AUDIT_PG_PASSWORD"));
        p.add("cani.storage.ekyc-dir", () -> storage.toString());
    }
    @Autowired UserRepository users;
    @Autowired WorkerRepository workers;
    @Autowired WorkerEkycService service;
    @Autowired EventOrganizerEkycService eoService;
    @Autowired EventOrganizerRepository organizers;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AuditLogService audit;
    @Autowired EkycImageStorage images;
    @Autowired JdbcTemplate db;
    @Autowired org.springframework.transaction.support.TransactionTemplate transaction;
    @MockitoBean EkycProvider provider;

    @Test void commitRetryAndReadInIndependentTransactions() throws Exception {
        UUID id = UUID.randomUUID();
        users.save(User.builder().userId(id).email(id + "@example.invalid").fullName("Synthetic")
                .role(UserRole.WORKER).build());
        assertThat(users.findById(id).orElseThrow().getFullName()).isEqualTo("Synthetic");
        var worker = workers.save(Worker.builder().user(users.getReferenceById(id)).fullName("Synthetic").build());
        worker.setBio("Updated profile");
        workers.save(worker);
        assertThat(workers.findById(worker.getWorkerId()).orElseThrow().getBio()).isEqualTo("Updated profile");

        byte[] bytes = {(byte)255, (byte)216, (byte)255, (byte)224, 0, 12, 0, 0};
        var file = new MockMultipartFile("image", "fixture.jpg", "image/jpeg", bytes);
        var unavailable = EkycIdentityResultDto.builder().technicalFailure(true).technicalErrorCode("VNPT_TIMEOUT").build();
        var approved = EkycIdentityResultDto.builder().providerName("VNPT_EKYC")
                .idNumber(String.format("%012d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000_000L)))
                .fullName("Verified Synthetic").documentLegal(true).backCardVerified(true)
                .expiryStatus(EkycIdentityResultDto.ExpiryDateStatus.VALID)
                .faceCompareStatus(EkycIdentityResultDto.FaceCompareStatus.MATCH).faceMatchScore(0.9)
                .livenessStatus(EkycIdentityResultDto.LivenessStatus.PASSED).build();
        when(provider.getProviderName()).thenReturn("VNPT_EKYC");
        when(provider.verifyIdentity(any(),any(),any(),any(),any(),any())).thenReturn(unavailable, approved);
        var first = service.submitEkyc(id, file, file, file,
                null, null, null, null, null, null, null, null, true, "2026.11-nd13-v1");
        UUID recordId = first.getVerificationId();
        String sql = "select front_image_url from worker_ekyc_verifications where id=?";
        String firstKey = db.queryForObject(sql, String.class, recordId);
        assertThat(images.exists(firstKey)).isTrue();
        assertThat(service.getMyEkycStatus(id).getStatus()).isEqualTo("TECHNICAL_FAILURE");
        assertThat(users.findById(id).orElseThrow().getFullName()).isEqualTo("Synthetic");
        var retried = service.submitEkyc(id, file, file, file,
                null, null, null, null, null, null, null, null, true, "2026.11-nd13-v1");
        assertThat(retried.getVerificationId()).isEqualTo(recordId);
        assertThat(service.getMyEkycStatus(id).getStatus()).isEqualTo("APPROVED");
        assertThat(users.findById(id).orElseThrow().getFullName()).isEqualTo("Verified Synthetic");
        assertThat(db.queryForObject("select count(*) from worker_ekyc_verifications where worker_id=?", Long.class, worker.getWorkerId())).isOne();
        assertThat(images.exists(firstKey)).isFalse();
        assertThat(db.queryForObject("select new_values->>'status' from audit_logs where entity_id=? and new_values->>'status'='APPROVED'", String.class, recordId)).isEqualTo("APPROVED");
        try (var stream = images.loadAsResource(db.queryForObject(sql, String.class, recordId)).getInputStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(bytes);
        }
    }
    @Test void eoManualReviewCommitsWithAuditAndPreservesCompanyAddress() {
        UUID id = UUID.randomUUID();
        User owner = users.save(User.builder().userId(id).email(id + "@example.invalid")
                .fullName("Synthetic EO").role(UserRole.EVENT_ORGANIZER).build());
        organizers.save(EventOrganizer.builder().user(owner).companyName("Synthetic Company").build());
        UUID reviewerId = UUID.randomUUID();
        users.save(User.builder().userId(reviewerId).email(reviewerId + "@example.invalid").fullName("Synthetic Reviewer").role(UserRole.CUSTOMER_CARE).build());
        when(provider.getProviderName()).thenReturn("VNPT_EKYC");
        when(provider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(
                approvedIdentity(id).livenessStatus(EkycIdentityResultDto.LivenessStatus.NOT_EVALUATED)
                        .residenceAddress("Synthetic residential address").build());
        var image = fixture();
        var pending = eoService.submitEkyc(id, image, image, image, null, true, "2026.11-nd13-v1");
        assertThat(pending.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(users.findById(id).orElseThrow().getFullName()).isEqualTo("Synthetic EO");
        assertThat(eoService.getPendingReviewDetails(pending.getVerificationId()).getFrontImageUrl()).doesNotContain("sig=");
        var decision = EkycAdminReviewRequestDto.builder().decision("APPROVED").build();
        var reviewed = eoService.adminReviewEkyc(reviewerId, pending.getVerificationId(), decision);
        assertThat(reviewed.getFrontImageUrl()).isNull();
        assertThat(reviewed.getReviewedAt()).isNotNull();
        assertThat(eoService.getMyEkycStatus(id).getReviewedBy()).isEqualTo(reviewerId);
        assertThat(users.findById(id).orElseThrow().getFullName()).isEqualTo("Verified Synthetic");
        assertThat(db.queryForObject("select address from event_organizers where user_id=?", String.class, id)).isNull();
        assertThat(db.queryForObject("select count(*) from audit_logs where entity_id=? and user_id=?", Long.class,
                pending.getVerificationId(), reviewerId)).isOne();
    }

    @Test void rollbackPreservesReferencedFilesAndDeletesStagedFiles() throws Exception {
        UUID id = UUID.randomUUID();
        users.save(User.builder().userId(id).email(id + "@example.invalid").fullName("Synthetic Worker").role(UserRole.WORKER).build());
        when(provider.getProviderName()).thenReturn("VNPT_EKYC");
        when(provider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(
                EkycIdentityResultDto.builder().technicalFailure(true).technicalErrorCode("VNPT_TIMEOUT").build(),
                approvedIdentity(id).build());
        var image = fixture();
        UUID verification = service.submitEkyc(id, image, image, image,
                null, null, null, null, null, null, null, null, true, "2026.11-nd13-v1").getVerificationId();
        String sql = "select front_image_url from worker_ekyc_verifications where id=?";
        String key = db.queryForObject(sql, String.class, verification);
        transaction.executeWithoutResult(status ->
                doThrow(new IllegalStateException("Simulated audit persistence failure")).when(audit)
                        .logEkycAction(eq(id), eq("WORKER_EKYC_SUBMITTED"), anyString(), eq(verification), anyMap()));
        assertThatThrownBy(() -> service.submitEkyc(id, image, image, image,
                null, null, null, null, null, null, null, null, true, "2026.11-nd13-v1"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(db.queryForObject(sql, String.class, verification)).isEqualTo(key);
        assertThat(images.exists(key)).isTrue();
        assertThat(service.getMyEkycStatus(id).getStatus()).isEqualTo("TECHNICAL_FAILURE");
        try (var files = java.nio.file.Files.list(storage.resolve(key).getParent())) {
            assertThat(files.count()).isEqualTo(3);
        }
    }

    private EkycIdentityResultDto.EkycIdentityResultDtoBuilder approvedIdentity(UUID id) {
        return EkycIdentityResultDto.builder().providerName("VNPT_EKYC")
                .idNumber(String.format("%012d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000_000L)))
                .fullName("Verified Synthetic").documentLegal(true).backCardVerified(true)
                .expiryStatus(EkycIdentityResultDto.ExpiryDateStatus.VALID)
                .faceCompareStatus(EkycIdentityResultDto.FaceCompareStatus.MATCH).faceMatchScore(0.9)
                .livenessStatus(EkycIdentityResultDto.LivenessStatus.PASSED);
    }

    private MockMultipartFile fixture() {
        return new MockMultipartFile("image", "fixture.jpg", "image/jpeg",
                new byte[] {(byte) 255, (byte) 216, (byte) 255, (byte) 224, 0, 12, 0, 0});
    }
}
