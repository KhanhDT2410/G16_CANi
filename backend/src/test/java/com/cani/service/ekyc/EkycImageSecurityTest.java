package com.cani.service.ekyc;

import com.cani.config.SecurityConfig;
import com.cani.controller.EkycImageController;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerEkycVerification;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.BadRequestException;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerEkycVerificationRepository;
import com.cani.security.RestAccessDeniedHandler;
import com.cani.security.RestAuthenticationEntryPoint;
import com.cani.security.UserAuthorityFilter;
import com.cani.service.ekyc.storage.EkycImageAccessTokenService;
import com.cani.service.ekyc.storage.EkycImageRetentionService;
import com.cani.service.ekyc.storage.EkycImageStorage;
import com.cani.service.ekyc.storage.EkycImageValidator;
import com.cani.service.ekyc.storage.LocalPrivateEkycImageStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EkycImageController.class)
@Import({
        SecurityConfig.class,
        UserAuthorityFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        EkycImageSecurityTest.SignedUrlTestConfig.class
})
class EkycImageSecurityTest {

    private static final byte[] VALID_JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10
    };

    private static final byte[] VALID_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
    };

    @TestConfiguration
    static class SignedUrlTestConfig {
        @Bean
        EkycImageAccessTokenService ekycImageAccessTokenService() {
            return new EkycImageAccessTokenService("unit-test-ekyc-secret-key", 180);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EkycImageAccessTokenService tokenService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private WorkerEkycVerificationRepository workerEkycRepository;

    @MockitoBean
    private EoEkycVerificationRepository eoEkycRepository;

    @MockitoBean
    private EkycImageStorage ekycImageStorage;

    @Test
    @DisplayName("Worker không được truy cập ảnh CCCD của Worker khác (403 Forbidden), còn chủ sở hữu và Admin thì được phép (200 OK)")
    void shouldEnforceOwnershipAndRoleOnPrivateEkycImages() throws Exception {
        UUID verificationId = UUID.randomUUID();
        User ownerWorkerUser = buildUser(UserRole.WORKER);
        User otherWorkerUser = buildUser(UserRole.WORKER);
        User adminUser = buildUser(UserRole.ADMIN);

        String objectKey = "worker/" + UUID.randomUUID() + "/abc_front.jpg";
        Worker ownerWorker = Worker.builder().workerId(UUID.randomUUID()).user(ownerWorkerUser).build();
        WorkerEkycVerification record = WorkerEkycVerification.builder()
                .id(verificationId)
                .worker(ownerWorker)
                .frontImageUrl(objectKey)
                .build();

        when(workerEkycRepository.findById(verificationId)).thenReturn(Optional.of(record));
        when(ekycImageStorage.isManagedObjectKey(objectKey)).thenReturn(true);
        when(ekycImageStorage.loadAsResource(objectKey)).thenReturn(new ByteArrayResource(VALID_JPEG));

        // Worker khác bị chặn 403
        mockMvc.perform(get("/api/v1/ekyc/images/worker/{id}/front", verificationId)
                        .with(authentication(authOf(otherWorkerUser))))
                .andExpect(status().isForbidden());

        // Chính chủ sở hữu được phép (200) kèm header no-store
        mockMvc.perform(get("/api/v1/ekyc/images/worker/{id}/front", verificationId)
                        .with(authentication(authOf(ownerWorkerUser))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store, max-age=0"));

        // Admin được phép (200)
        mockMvc.perform(get("/api/v1/ekyc/images/worker/{id}/front", verificationId)
                        .with(authentication(authOf(adminUser))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Không có token: chặn truy cập công khai, chỉ cho phép Signed URL HMAC-SHA256 hợp lệ và chưa hết hạn")
    void shouldAllowValidSignedUrlAndBlockUnsignedOrTampered() throws Exception {
        UUID verificationId = UUID.randomUUID();
        String objectKey = "worker/" + UUID.randomUUID() + "/abc_selfie.jpg";
        WorkerEkycVerification record = WorkerEkycVerification.builder()
                .id(verificationId)
                .worker(Worker.builder().workerId(UUID.randomUUID()).user(buildUser(UserRole.WORKER)).build())
                .selfieImageUrl(objectKey)
                .build();

        when(workerEkycRepository.findById(verificationId)).thenReturn(Optional.of(record));
        when(ekycImageStorage.isManagedObjectKey(objectKey)).thenReturn(true);
        when(ekycImageStorage.loadAsResource(objectKey)).thenReturn(new ByteArrayResource(VALID_JPEG));

        // Không có auth và không có chữ ký -> 401
        mockMvc.perform(get("/api/v1/ekyc/images/worker/{id}/selfie", verificationId))
                .andExpect(status().isUnauthorized());

        // Chữ ký giả -> 401
        mockMvc.perform(get("/api/v1/ekyc/images/worker/{id}/selfie", verificationId)
                        .param("expires", String.valueOf(Instant.now().getEpochSecond() + 120))
                        .param("sig", "tampered-signature"))
                .andExpect(status().isUnauthorized());

        // Signed URL hợp lệ -> 200 OK
        String signedUrl = tokenService.generateSignedImageUrl("worker", verificationId, "selfie");
        mockMvc.perform(get(signedUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("EkycImageValidator kiểm tra Magic Bytes, chống extension spoofing và giới hạn kích thước")
    void shouldValidateMagicBytesAndRejectSpoofedExtensions() {
        EkycImageValidator validator = new EkycImageValidator(1024);

        MockMultipartFile validJpeg = new MockMultipartFile("front", "front.jpg", "image/jpeg", VALID_JPEG);
        assertThat(validator.validate(validJpeg, "Mặt trước").getDetectedMimeType()).isEqualTo("image/jpeg");

        MockMultipartFile pngAsJpg = new MockMultipartFile("front", "spoofed.jpg", "image/jpeg", VALID_PNG);
        assertThatThrownBy(() -> validator.validate(pngAsJpg, "Mặt trước"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không khớp");

        MockMultipartFile pdfAsJpg = new MockMultipartFile("front", "doc.jpg", "image/jpeg", "%PDF-1.4\n...".getBytes());
        assertThatThrownBy(() -> validator.validate(pdfAsJpg, "Mặt trước"))
                .isInstanceOf(BadRequestException.class);

        byte[] oversized = new byte[2048];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        MockMultipartFile tooBig = new MockMultipartFile("front", "big.jpg", "image/jpeg", oversized);
        assertThatThrownBy(() -> validator.validate(tooBig, "Mặt trước"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vượt quá dung lượng");
    }

    @Test
    @DisplayName("LocalPrivateEkycImageStorage từ chối URL giả storage.cani.vn và dọn ảnh hết hạn retention")
    void shouldStorePrivateKeysRejectFakeUrlsAndPurgeExpiredImages(@TempDir Path tempDir) {
        LocalPrivateEkycImageStorage storage = new LocalPrivateEkycImageStorage(tempDir.toString());
        assertThat(storage.isManagedObjectKey("https://storage.cani.vn/ekyc/front_123.jpg")).isFalse();
        assertThat(storage.isManagedObjectKey("../secret.jpg")).isFalse();

        UUID workerId = UUID.randomUUID();
        EkycImageStorage.StoredObject stored = storage.store("worker", workerId, "front", VALID_JPEG, "jpg", "image/jpeg");
        assertThat(storage.isManagedObjectKey(stored.getObjectKey())).isTrue();
        assertThat(storage.exists(stored.getObjectKey())).isTrue();

        WorkerEkycVerificationRepository wRepo = mock(WorkerEkycVerificationRepository.class);
        EoEkycVerificationRepository eRepo = mock(EoEkycVerificationRepository.class);
        WorkerEkycVerification expired = WorkerEkycVerification.builder()
                .id(UUID.randomUUID())
                .frontImageUrl(stored.getObjectKey())
                .imageRetentionUntil(Instant.now().minusSeconds(60))
                .build();
        when(wRepo.findByImagesPurgedAtIsNullAndImageRetentionUntilBefore(any())).thenReturn(List.of(expired));
        when(eRepo.findByImagesPurgedAtIsNullAndImageRetentionUntilBefore(any())).thenReturn(List.of());

        EkycImageRetentionService retentionService = new EkycImageRetentionService(
                storage, wRepo, eRepo, 30, 7, 3, 30, true);
        int purged = retentionService.purgeExpiredImages();

        assertThat(purged).isEqualTo(1);
        assertThat(storage.exists(stored.getObjectKey())).isFalse();
        assertThat(expired.getFrontImageUrl()).isNull();
        assertThat(expired.getImagesPurgedAt()).isNotNull();
        verify(wRepo).save(expired);
    }

    private static User buildUser(UserRole role) {
        return User.builder()
                .userId(UUID.randomUUID())
                .email(role.name().toLowerCase() + "@cani.vn")
                .fullName("Test " + role.name())
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private static UsernamePasswordAuthenticationToken authOf(User user) {
        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
