package com.cani.service.ekyc;

import com.cani.client.ekyc.EkycProvider;
import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.*;
import com.cani.entity.*;
import com.cani.entity.enums.UserRole;
import com.cani.repository.*;
import com.cani.service.WorkerEkycService;
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

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true", "cani.security.ekyc-hash-pepper=synthetic-test-only",
        "spring.datasource.driver-class-name=org.postgresql.Driver", "cani.ekyc.strict=false"})
@ActiveProfiles("pg-audit")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({WorkerEkycService.class, EkycDecisionEngine.class, VnptEkycProperties.class,
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
    @Autowired EkycImageStorage images;
    @Autowired JdbcTemplate db;
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
        var first = service.submitEkyc(id, file, file, file);
        UUID recordId = first.getVerificationId();
        String sql = "select front_image_url from worker_ekyc_verifications where id=?";
        String firstKey = db.queryForObject(sql, String.class, recordId);
        assertThat(images.exists(firstKey)).isTrue();
        assertThat(service.getMyEkycStatus(id).getStatus()).isEqualTo("TECHNICAL_FAILURE");
        assertThat(users.findById(id).orElseThrow().getFullName()).isEqualTo("Synthetic");
        var retried = service.submitEkyc(id, file, file, file);
        assertThat(retried.getVerificationId()).isEqualTo(recordId);
        assertThat(service.getMyEkycStatus(id).getStatus()).isEqualTo("APPROVED");
        assertThat(users.findById(id).orElseThrow().getFullName()).isEqualTo("Verified Synthetic");
        assertThat(db.queryForObject("select count(*) from worker_ekyc_verifications where worker_id=?", Long.class, worker.getWorkerId())).isOne();
        assertThat(images.exists(firstKey)).isFalse();
        try (var stream = images.loadAsResource(db.queryForObject(sql, String.class, recordId)).getInputStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(bytes);
        }
    }
}
