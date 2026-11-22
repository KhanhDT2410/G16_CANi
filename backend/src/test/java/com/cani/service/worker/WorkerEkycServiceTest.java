package com.cani.service.worker;

import com.cani.client.ekyc.EkycProvider;
import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.dto.ekyc.EkycIdentityResultDto.ExpiryDateStatus;
import com.cani.dto.ekyc.EkycIdentityResultDto.FaceCompareStatus;
import com.cani.dto.ekyc.EkycVerificationResultDto;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerEkycVerification;
import com.cani.entity.enums.KycStatus;
import com.cani.exception.BadRequestException;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerEkycVerificationRepository;
import com.cani.repository.WorkerRepository;
import com.cani.service.WorkerEkycService;
import com.cani.service.ekyc.EkycCccdLockManager;
import com.cani.service.ekyc.EkycDecisionEngine;
import com.cani.service.ekyc.EkycIdentityCryptoService;
import com.cani.service.ekyc.storage.EkycImageAccessTokenService;
import com.cani.service.ekyc.storage.EkycImageRetentionService;
import com.cani.service.ekyc.storage.EkycImageStorage;
import com.cani.service.ekyc.storage.EkycImageValidator;
import com.cani.service.ekyc.storage.LocalPrivateEkycImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerEkycServiceTest {

    private static final byte[] VALID_JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46
    };

    @TempDir
    Path tempDir;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private WorkerEkycVerificationRepository ekycVerificationRepository;

    @Mock
    private EoEkycVerificationRepository eoEkycVerificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EkycProvider ekycProvider;

    private WorkerEkycService workerEkycService;
    private LocalPrivateEkycImageStorage imageStorage;
    private EkycIdentityCryptoService cryptoService;

    private User workerUser;
    private Worker worker;
    private MockMultipartFile frontFile;
    private MockMultipartFile backFile;
    private MockMultipartFile selfieFile;

    @BeforeEach
    void setUp() {
        VnptEkycProperties vnptProperties = new VnptEkycProperties();
        vnptProperties.setVerifyBackCard(true);
        cryptoService = new EkycIdentityCryptoService("test-pepper-secret");
        EkycDecisionEngine decisionEngine = new EkycDecisionEngine(vnptProperties, cryptoService);
        EkycImageValidator imageValidator = new EkycImageValidator(5L * 1024L * 1024L);
        imageStorage = new LocalPrivateEkycImageStorage(tempDir.toString());
        EkycImageAccessTokenService accessTokenService = new EkycImageAccessTokenService("test-signing-secret", 180);
        EkycImageRetentionService retentionService = new EkycImageRetentionService(
                imageStorage, ekycVerificationRepository, eoEkycVerificationRepository, 30, 7, 3, 30, true);
        EkycCccdLockManager lockManager = new EkycCccdLockManager();

        workerEkycService = new WorkerEkycService(
                workerRepository,
                ekycVerificationRepository,
                userRepository,
                ekycProvider,
                decisionEngine,
                cryptoService,
                imageValidator,
                imageStorage,
                accessTokenService,
                retentionService,
                lockManager
        );
        ReflectionTestUtils.setField(workerEkycService, "eoEkycVerificationRepository", eoEkycVerificationRepository);
        ReflectionTestUtils.setField(workerEkycService, "ekycStrict", false);

        lenient().when(ekycProvider.getProviderName()).thenReturn("VNPT_EKYC");

        workerUser = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Trần Thị Hoa")
                .build();

        worker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(workerUser)
                .fullName("Trần Thị Hoa")
                .kycStatus(KycStatus.PENDING)
                .build();

        frontFile = new MockMultipartFile("frontImage", "front.jpg", "image/jpeg", VALID_JPEG);
        backFile = new MockMultipartFile("backImage", "back.jpg", "image/jpeg", VALID_JPEG);
        selfieFile = new MockMultipartFile("selfieImage", "selfie.jpg", "image/jpeg", VALID_JPEG);
    }

    @Test
    @DisplayName("Xác minh thành công qua VNPT -> APPROVED, lưu object key riêng tư (không URL giả), trả Signed URL")
    void shouldApproveWhenVnptVerificationValid() {
        EkycIdentityResultDto vnptResult = validVnptMatchResult("079201012345", "TRẦN THỊ HOA");

        stubHappyRepositoryFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(vnptResult);

        EkycVerificationResultDto result = workerEkycService.submitEkyc(
                workerUser.getUserId(), frontFile, backFile, selfieFile);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getRoutingDecision()).isEqualTo("AUTO_APPROVED");
        assertThat(result.getCccdNumberMasked()).isEqualTo("079201******345");
        assertThat(result.getFrontImageUrl()).startsWith("/api/v1/ekyc/images/worker/");
        assertThat(result.getFrontImageUrl()).contains("sig=");
        assertThat(result.getFrontImageUrl()).doesNotContain("storage.cani.vn");
        assertThat(result.getImagesAvailable()).isTrue();
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.APPROVED);

        ArgumentCaptor<WorkerEkycVerification> captor = ArgumentCaptor.forClass(WorkerEkycVerification.class);
        verify(ekycVerificationRepository, atLeast(2)).save(captor.capture());
        WorkerEkycVerification savedFinal = captor.getValue();
        assertThat(savedFinal.getFrontImageUrl()).startsWith("worker/" + worker.getWorkerId() + "/");
        assertThat(imageStorage.exists(savedFinal.getFrontImageUrl())).isTrue();
        assertThat(imageStorage.exists(savedFinal.getBackImageUrl())).isTrue();
        assertThat(imageStorage.exists(savedFinal.getSelfieImageUrl())).isTrue();
    }

    @Test
    @DisplayName("Cần Admin review khi ngày hết hạn CCCD không đọc được -> PENDING_REVIEW (không ép thành REJECTED)")
    void shouldRouteToPendingReviewWhenExpiryUnreadable() {
        EkycIdentityResultDto vnptResult = validVnptMatchResult("079201012345", "TRẦN THỊ HOA");
        vnptResult.setExpiryDate(null);
        vnptResult.setExpiryStatus(ExpiryDateStatus.UNREADABLE);

        stubHappyRepositoryFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(vnptResult);

        EkycVerificationResultDto result = workerEkycService.submitEkyc(
                workerUser.getUserId(), frontFile, backFile, selfieFile);

        assertThat(result.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(result.getRoutingDecision()).isEqualTo("MANUAL_REVIEW");
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    @DisplayName("Lỗi kỹ thuật từ VNPT -> TECHNICAL_FAILURE (cho phép retry), không lưu REJECTED")
    void shouldReturnTechnicalFailureWhenProviderFailsTechnically() {
        EkycIdentityResultDto techFailure = EkycIdentityResultDto.builder()
                .providerName("VNPT_EKYC")
                .technicalFailure(true)
                .technicalErrorCode("VNPT_TIMEOUT")
                .routingReason("Kết nối VNPT quá thời gian chờ")
                .build();

        stubHappyRepositoryFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(techFailure);

        EkycVerificationResultDto result = workerEkycService.submitEkyc(
                workerUser.getUserId(), frontFile, backFile, selfieFile);

        assertThat(result.getStatus()).isEqualTo("TECHNICAL_FAILURE");
        assertThat(result.getFailureCode()).isEqualTo("VNPT_TIMEOUT");
        assertThat(result.getRetryable()).isTrue();
        assertThat(result.getFaceMatchScore()).isNull();
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    @DisplayName("CCCD bị trùng với tài khoản Worker hoặc EO khác -> REJECTED")
    void shouldRejectWhenDuplicateCccdDetected() {
        EkycIdentityResultDto vnptResult = validVnptMatchResult("079201012345", "TRẦN THỊ HOA");

        stubHappyRepositoryFlow();
        when(ekycVerificationRepository.existsByCccdNumberHashInAndStatusInAndWorker_WorkerIdNot(any(), any(), any()))
                .thenReturn(true);
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(vnptResult);

        EkycVerificationResultDto result = workerEkycService.submitEkyc(
                workerUser.getUserId(), frontFile, backFile, selfieFile);

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getRoutingReason()).contains("Mỗi CCCD chỉ được định danh cho duy nhất 1 tài khoản");
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.REJECTED);
    }

    @Test
    @DisplayName("Dữ liệu QR / nhập tay từ client KHÔNG được ghi đè kết quả VNPT OCR — lệch thông tin chuyển PENDING_REVIEW")
    void shouldNotOverwriteVnptOcrWithClientQrOrManualInput() {
        EkycIdentityResultDto vnptResult = validVnptMatchResult("079201012345", "TRẦN THỊ HOA");

        stubHappyRepositoryFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(vnptResult);

        // Client cố tình truyền số CCCD và họ tên giả khác với kết quả VNPT OCR
        EkycVerificationResultDto result = workerEkycService.submitEkyc(
                workerUser.getUserId(),
                frontFile, backFile, selfieFile,
                "099999999999", "NGUYỄN VĂN GIẢ",
                null, null, null, null, null, null
        );

        assertThat(result.getCccdNumberMasked()).isEqualTo("079201******345");
        assertThat(result.getFullName()).isEqualTo("TRẦN THỊ HOA");
        assertThat(result.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(result.getFraudFlags()).containsEntry("userInputMismatch", true);
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    @DisplayName("Tài khoản Worker đã APPROVED KHÔNG bị hạ trạng thái khi lần xác minh mới gặp lỗi kỹ thuật")
    void shouldNotDowngradeAlreadyApprovedWorkerOnTechnicalFailure() {
        worker.setKycStatus(KycStatus.APPROVED);
        worker.setCccdNumber("079201******345");

        EkycIdentityResultDto techFailure = EkycIdentityResultDto.builder()
                .providerName("VNPT_EKYC")
                .technicalFailure(true)
                .technicalErrorCode("VNPT_CONNECTION_ERROR")
                .routingReason("Mất kết nối VNPT")
                .build();

        stubHappyRepositoryFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(techFailure);

        EkycVerificationResultDto result = workerEkycService.submitEkyc(
                workerUser.getUserId(), frontFile, backFile, selfieFile);

        assertThat(result.getStatus()).isEqualTo("TECHNICAL_FAILURE");
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(worker.getCccdNumber()).isEqualTo("079201******345");
    }

    @Test
    @DisplayName("Retry sau TECHNICAL_FAILURE tái sử dụng bản ghi cũ, tăng retryCount và không tạo bản ghi trùng")
    void shouldReuseTechnicalFailureRecordOnRetryWithoutCreatingDuplicates() {
        UUID existingVerificationId = UUID.randomUUID();
        EkycImageStorage.StoredObject oldFront = imageStorage.store("worker", worker.getWorkerId(), "front", VALID_JPEG, "jpg", "image/jpeg");
        EkycImageStorage.StoredObject oldBack = imageStorage.store("worker", worker.getWorkerId(), "back", VALID_JPEG, "jpg", "image/jpeg");
        EkycImageStorage.StoredObject oldSelfie = imageStorage.store("worker", worker.getWorkerId(), "selfie", VALID_JPEG, "jpg", "image/jpeg");

        WorkerEkycVerification existingFailed = WorkerEkycVerification.builder()
                .id(existingVerificationId)
                .worker(worker)
                .status("TECHNICAL_FAILURE")
                .routingDecision("TECHNICAL_FAILURE")
                .failureCode("VNPT_TIMEOUT")
                .retryCount(0)
                .frontImageUrl(oldFront.getObjectKey())
                .backImageUrl(oldBack.getObjectKey())
                .selfieImageUrl(oldSelfie.getObjectKey())
                .build();

        when(workerRepository.findByUser_UserId(workerUser.getUserId())).thenReturn(Optional.of(worker));
        when(ekycVerificationRepository.findTopByWorker_WorkerIdAndStatusInOrderByCreatedAtDesc(
                eq(worker.getWorkerId()), any())).thenReturn(Optional.of(existingFailed));
        when(ekycVerificationRepository.findById(existingVerificationId)).thenReturn(Optional.of(existingFailed));
        when(ekycVerificationRepository.existsByCccdNumberHashInAndStatusInAndWorker_WorkerIdNot(any(), any(), any()))
                .thenReturn(false);
        when(eoEkycVerificationRepository.existsByCccdNumberHashInAndStatusInAndEventOrganizer_User_UserIdNot(any(), any(), any()))
                .thenReturn(false);
        when(workerRepository.existsByCccdNumberInAndWorkerIdNot(any(), any())).thenReturn(false);
        when(ekycVerificationRepository.save(any(WorkerEkycVerification.class))).thenAnswer(i -> i.getArgument(0));
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(validVnptMatchResult("079201012345", "TRẦN THỊ HOA"));

        EkycVerificationResultDto result = workerEkycService.submitEkyc(
                workerUser.getUserId(), frontFile, backFile, selfieFile);

        assertThat(result.getVerificationId()).isEqualTo(existingVerificationId);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo("APPROVED");
        // Ảnh cũ của lần lỗi trước phải được xóa sạch
        assertThat(imageStorage.exists(oldFront.getObjectKey())).isFalse();
    }

    @Test
    @DisplayName("Admin approve và reject hồ sơ PENDING_REVIEW vẫn hoạt động chuẩn xác")
    void shouldAdminApproveAndRejectSuccessfully() {
        UUID verificationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        EkycImageStorage.StoredObject storedFront = imageStorage.store("worker", worker.getWorkerId(), "front", VALID_JPEG, "jpg", "image/jpeg");
        WorkerEkycVerification pending = WorkerEkycVerification.builder()
                .id(verificationId)
                .worker(worker)
                .status("PENDING_REVIEW")
                .cccdNumberMasked("079201******345")
                .cccdNumberHash(cryptoService.hashCccd("079201012345"))
                .frontImageUrl(storedFront.getObjectKey())
                .build();

        when(ekycVerificationRepository.findById(verificationId)).thenReturn(Optional.of(pending));
        when(ekycVerificationRepository.save(any(WorkerEkycVerification.class))).thenAnswer(i -> i.getArgument(0));

        EkycVerificationResultDto approved = workerEkycService.adminReviewEkyc(
                adminId,
                verificationId,
                EkycAdminReviewRequestDto.builder().decision("APPROVED").reviewNotes("Hợp lệ").build());

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getRoutingDecision()).isEqualTo("MANUAL_APPROVED");
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(worker.getCccdNumber()).isEqualTo("079201******345");
        assertThat(approved.getFrontImageUrl()).startsWith("/api/v1/ekyc/images/worker/");

        // Reject một hồ sơ PENDING_REVIEW khác của worker chưa approved
        Worker unverifiedWorker = Worker.builder().workerId(UUID.randomUUID()).user(workerUser).kycStatus(KycStatus.PENDING).build();
        UUID rejectId = UUID.randomUUID();
        WorkerEkycVerification pending2 = WorkerEkycVerification.builder()
                .id(rejectId).worker(unverifiedWorker).status("PENDING_REVIEW").build();
        when(ekycVerificationRepository.findById(rejectId)).thenReturn(Optional.of(pending2));

        EkycVerificationResultDto rejected = workerEkycService.adminReviewEkyc(
                adminId, rejectId, EkycAdminReviewRequestDto.builder().decision("REJECTED").reviewNotes("Ảnh mờ").build());
        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        assertThat(unverifiedWorker.getKycStatus()).isEqualTo(KycStatus.REJECTED);
    }

    @Test
    @DisplayName("Từ chối tệp giả mạo magic bytes (HTML/text đổi đuôi .jpg) trước khi gọi VNPT")
    void shouldRejectSpoofedImageFileBeforeCallingVnpt() {
        MockMultipartFile fakeJpg = new MockMultipartFile("frontImage", "fake.jpg", "image/jpeg", "<html>not-an-image</html>".getBytes());

        assertThatThrownBy(() -> workerEkycService.submitEkyc(workerUser.getUserId(), fakeJpg, backFile, selfieFile))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(ekycProvider);
    }

    private void stubHappyRepositoryFlow() {
        UUID generatedId = UUID.randomUUID();
        final WorkerEkycVerification[] holder = new WorkerEkycVerification[1];
        when(workerRepository.findByUser_UserId(workerUser.getUserId())).thenReturn(Optional.of(worker));
        when(ekycVerificationRepository.findTopByWorker_WorkerIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(ekycVerificationRepository.save(any(WorkerEkycVerification.class))).thenAnswer(i -> {
            WorkerEkycVerification v = i.getArgument(0);
            if (v.getId() == null) {
                v.setId(generatedId);
            }
            holder[0] = v;
            return v;
        });
        lenient().when(ekycVerificationRepository.findById(generatedId)).thenAnswer(i -> Optional.ofNullable(holder[0]));
        lenient().when(ekycVerificationRepository.existsByCccdNumberHashInAndStatusInAndWorker_WorkerIdNot(any(), any(), any()))
                .thenReturn(false);
        lenient().when(eoEkycVerificationRepository.existsByCccdNumberHashInAndStatusInAndEventOrganizer_User_UserIdNot(any(), any(), any()))
                .thenReturn(false);
        lenient().when(workerRepository.existsByCccdNumberInAndWorkerIdNot(any(), any())).thenReturn(false);
    }

    private EkycIdentityResultDto validVnptMatchResult(String cccd, String fullName) {
        return EkycIdentityResultDto.builder()
                .providerName("VNPT_EKYC")
                .providerReference("CANI_EKYC_TEST_1")
                .idNumber(cccd)
                .fullName(fullName)
                .dob("15/05/2001")
                .gender("Nữ")
                .nationality("Việt Nam")
                .expiryDate("15/05/2041")
                .issueDate("10/05/2021")
                .expiryStatus(ExpiryDateStatus.VALID)
                .faceCompareStatus(FaceCompareStatus.MATCH)
                .faceCompareMessage("MATCH")
                .faceMatchScore(92.0)
                .backCardVerified(true)
                .documentLegal(true)
                .fakeDocument(false)
                .technicalFailure(false)
                .build();
    }
}
