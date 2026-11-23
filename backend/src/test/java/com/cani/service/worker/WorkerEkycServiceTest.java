package com.cani.service.worker;

import com.cani.client.ekyc.EkycProvider;
import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.dto.ekyc.EkycIdentityResultDto.ExpiryDateStatus;
import com.cani.dto.ekyc.EkycIdentityResultDto.FaceCompareStatus;
import com.cani.dto.ekyc.EkycVerificationResultDto;
import com.cani.entity.EkycConsentRecord;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerEkycVerification;
import com.cani.entity.enums.KycStatus;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.repository.EkycConsentRecordRepository;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.SystemConfigRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerEkycServiceTest {

    private static final String CONSENT_VERSION = "2026.11-nd13-v1";
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
    private EkycConsentRecordRepository consentRecordRepository;

    @Mock
    private SystemConfigRepository systemConfigRepository;

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
                imageStorage, ekycVerificationRepository, eoEkycVerificationRepository, 0, 0, 3, 14, true);
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
        ReflectionTestUtils.setField(workerEkycService, "consentRecordRepository", consentRecordRepository);
        ReflectionTestUtils.setField(workerEkycService, "systemConfigRepository", systemConfigRepository);
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
    @DisplayName("Bắt buộc đồng ý Nghị định 13/2023/NĐ-CP trước khi lưu ảnh hoặc gọi VNPT")
    void shouldRequireConsentBeforeStoringImagesOrCallingVnpt() {
        assertThatThrownBy(() -> workerEkycService.submitEkyc(
                workerUser.getUserId(), frontFile, backFile, selfieFile))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> workerEkycService.submitEkyc(
                workerUser.getUserId(), frontFile, backFile, selfieFile,
                null, null, null, null, null, null, null, null,
                true, "outdated-version"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(ekycProvider);
        verifyNoInteractions(consentRecordRepository);
    }

    @Test
    @DisplayName("VNPT APPROVED -> giảm thiểu OCR thô, kiểm tra đủ tuổi, lưu consent và xóa ảnh ngay sau commit khi retention=0")
    void shouldApproveWithOcrMinimizationAndImmediatePurge() {
        EkycIdentityResultDto vnptResult = validVnptMatchResult("079201012345", "TRẦN THỊ HOA");

        stubHappyRepositoryFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(vnptResult);

        EkycVerificationResultDto result = submitWithConsent(workerUser.getUserId(), null, null);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getRoutingDecision()).isEqualTo("AUTO_APPROVED");
        assertThat(result.getCccdNumberMasked()).isEqualTo("********2345");
        assertThat(result.getMeetsMinimumAge()).isTrue();
        assertThat(result.getConsentNoticeVersion()).isEqualTo(CONSENT_VERSION);
        assertThat(result.getConsentedAt()).isNotNull();
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.APPROVED);

        verify(consentRecordRepository, times(1)).save(any(EkycConsentRecord.class));
        ArgumentCaptor<WorkerEkycVerification> captor = ArgumentCaptor.forClass(WorkerEkycVerification.class);
        verify(ekycVerificationRepository, atLeast(2)).save(captor.capture());
        WorkerEkycVerification savedFinal = captor.getValue();

        // Đã ngừng ghi các trường OCR thô không cần thiết
        assertThat(savedFinal.getDob()).isNull();
        assertThat(savedFinal.getGender()).isNull();
        assertThat(savedFinal.getNationality()).isNull();
        assertThat(savedFinal.getOriginAddress()).isNull();
        assertThat(savedFinal.getResidenceAddress()).isNull();
        assertThat(savedFinal.getIssueDate()).isNull();
        assertThat(savedFinal.getExpiryDate()).isNull();
        assertThat(savedFinal.getMeetsMinimumAge()).isTrue();
        assertThat(savedFinal.getImageRetentionUntil()).isNotNull();
        // Chính sách approvedRetentionDays=0 -> xóa ngay, ghi images_purged_at
        assertThat(savedFinal.getImagesPurgedAt()).isNotNull();
        assertThat(savedFinal.getFrontImageUrl()).isNull();
    }

    @Test
    @DisplayName("Ảnh mờ hoặc thiếu ngày hết hạn -> REJECTED với lý do cụ thể chụp lại và xóa ảnh ngay (không qua Admin)")
    void shouldRejectWithSpecificReasonWhenExpiryUnreadableAndPurgeImages() {
        EkycIdentityResultDto vnptResult = validVnptMatchResult("079201012345", "TRẦN THỊ HOA");
        vnptResult.setExpiryDate(null);
        vnptResult.setExpiryStatus(ExpiryDateStatus.UNREADABLE);

        stubHappyRepositoryFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(vnptResult);

        EkycVerificationResultDto result = submitWithConsent(workerUser.getUserId(), null, null);

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getRoutingDecision()).isEqualTo("REJECTED");
        assertThat(result.getRoutingReason()).contains("ngày hết hạn");
        assertThat(result.getImagesAvailable()).isFalse();
        assertThat(result.getFrontImageUrl()).isNull();
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.REJECTED);
    }

    @Test
    @DisplayName("Lỗi kỹ thuật từ VNPT -> TECHNICAL_FAILURE (giữ ảnh 3 ngày để đối soát), không lưu REJECTED")
    void shouldReturnTechnicalFailureWhenProviderFailsTechnically() {
        EkycIdentityResultDto techFailure = EkycIdentityResultDto.builder()
                .providerName("VNPT_EKYC")
                .technicalFailure(true)
                .technicalErrorCode("VNPT_TIMEOUT")
                .routingReason("Kết nối VNPT quá thời gian chờ")
                .build();

        stubHappyRepositoryFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(techFailure);

        EkycVerificationResultDto result = submitWithConsent(workerUser.getUserId(), null, null);

        assertThat(result.getStatus()).isEqualTo("TECHNICAL_FAILURE");
        assertThat(result.getFailureCode()).isEqualTo("VNPT_TIMEOUT");
        assertThat(result.getRetryable()).isTrue();
        assertThat(result.getFaceMatchScore()).isNull();
        assertThat(result.getImageRetentionUntil()).isAfter(Instant.now().plus(2, ChronoUnit.DAYS));
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

        EkycVerificationResultDto result = submitWithConsent(workerUser.getUserId(), null, null);

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getRoutingReason()).contains("Mỗi CCCD chỉ được định danh cho duy nhất 1 tài khoản");
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.REJECTED);
    }

    @Test
    @DisplayName("Dữ liệu QR / nhập tay từ client KHÔNG được ghi đè kết quả VNPT OCR — lệch thông tin trả REJECTED")
    void shouldNotOverwriteVnptOcrWithClientQrOrManualInput() {
        EkycIdentityResultDto vnptResult = validVnptMatchResult("079201012345", "TRẦN THỊ HOA");

        stubHappyRepositoryFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(vnptResult);

        EkycVerificationResultDto result = submitWithConsent(
                workerUser.getUserId(), "099999999999", "NGUYỄN VĂN GIẢ");

        assertThat(result.getCccdNumberMasked()).isEqualTo("********2345");
        assertThat(result.getFullName()).isEqualTo("TRẦN THỊ HOA");
        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getRoutingReason()).contains("không trùng khớp");
        assertThat(result.getFraudFlags()).containsEntry("userInputMismatch", true);
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.REJECTED);
    }

    @Test
    @DisplayName("Tài khoản Worker đã APPROVED KHÔNG bị hạ trạng thái khi lần xác minh mới gặp lỗi kỹ thuật")
    void shouldNotDowngradeAlreadyApprovedWorkerOnTechnicalFailure() {
        worker.setKycStatus(KycStatus.APPROVED);
        worker.setCccdNumber("********2345");

        EkycIdentityResultDto techFailure = EkycIdentityResultDto.builder()
                .providerName("VNPT_EKYC")
                .technicalFailure(true)
                .technicalErrorCode("VNPT_CONNECTION_ERROR")
                .routingReason("Mất kết nối VNPT")
                .build();

        stubHappyRepositoryFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(techFailure);

        EkycVerificationResultDto result = submitWithConsent(workerUser.getUserId(), null, null);

        assertThat(result.getStatus()).isEqualTo("TECHNICAL_FAILURE");
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(worker.getCccdNumber()).isEqualTo("********2345");
    }

    @Test
    @DisplayName("Retry sau TECHNICAL_FAILURE tái sử dụng bản ghi cũ, tăng retryCount và xóa ảnh cũ")
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
        when(ekycVerificationRepository.findTopByWorker_WorkerIdOrderByCreatedAtDesc(
                eq(worker.getWorkerId()))).thenReturn(Optional.of(existingFailed));
        when(ekycVerificationRepository.findByIdForUpdate(existingVerificationId)).thenReturn(Optional.of(existingFailed));
        lenient().when(ekycVerificationRepository.findById(existingVerificationId)).thenReturn(Optional.of(existingFailed));
        when(ekycVerificationRepository.existsByCccdNumberHashInAndStatusInAndWorker_WorkerIdNot(any(), any(), any()))
                .thenReturn(false);
        when(eoEkycVerificationRepository.existsByCccdNumberHashInAndStatusInAndEventOrganizer_User_UserIdNot(any(), any(), any()))
                .thenReturn(false);
        when(workerRepository.existsByCccdNumberInAndWorkerIdNot(any(), any())).thenReturn(false);
        when(ekycVerificationRepository.save(any(WorkerEkycVerification.class))).thenAnswer(i -> i.getArgument(0));
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(validVnptMatchResult("079201012345", "TRẦN THỊ HOA"));

        EkycVerificationResultDto result = submitWithConsent(workerUser.getUserId(), null, null);

        assertThat(result.getVerificationId()).isEqualTo(existingVerificationId);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(imageStorage.exists(oldFront.getObjectKey())).isFalse();
    }

    @Test
    @DisplayName("Chặn 409 CONFLICT khi PROCESSING đang chạy; thu hồi PROCESSING quá hạn và cho phép nộp lại sau hồ sơ cũ hết hạn")
    void shouldBlockActiveProcessingAndRecoverExpiredProcessingAndAllowExpiredPendingResubmit() {
        WorkerEkycVerification activeProcessing = WorkerEkycVerification.builder()
                .id(UUID.randomUUID())
                .worker(worker)
                .status("PROCESSING")
                .processingStartedAt(Instant.now().minusSeconds(10))
                .build();
        when(workerRepository.findByUser_UserId(workerUser.getUserId())).thenReturn(Optional.of(worker));
        when(ekycVerificationRepository.findTopByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId()))
                .thenReturn(Optional.of(activeProcessing));

        assertThatThrownBy(() -> submitWithConsent(workerUser.getUserId(), null, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(ekycProvider);

        // Hết hạn PROCESSING -> getMyEkycStatus tự động phục hồi sang TECHNICAL_FAILURE
        WorkerEkycVerification staleProcessing = WorkerEkycVerification.builder()
                .id(UUID.randomUUID())
                .worker(worker)
                .status("PROCESSING")
                .processingStartedAt(Instant.now().minusSeconds(300))
                .build();
        when(ekycVerificationRepository.findTopByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId()))
                .thenReturn(Optional.of(staleProcessing));
        when(ekycVerificationRepository.save(any(WorkerEkycVerification.class))).thenAnswer(i -> i.getArgument(0));

        EkycVerificationResultDto recoveredDto = workerEkycService.getMyEkycStatus(workerUser.getUserId());
        assertThat(recoveredDto.getStatus()).isEqualTo("TECHNICAL_FAILURE");
        assertThat(recoveredDto.getFailureCode()).isEqualTo("PROCESSING_TIMEOUT");
        assertThat(recoveredDto.getRetryable()).isTrue();
    }

    @Test
    @DisplayName("Worker có thể nộp lại ngay khi hồ sơ PENDING_REVIEW trước đó đã hết hạn lưu trữ")
    void shouldAllowWorkerResubmitWhenPreviousPendingReviewExpired() {
        UUID expiredId = UUID.randomUUID();
        WorkerEkycVerification expiredPending = WorkerEkycVerification.builder()
                .id(expiredId)
                .worker(worker)
                .status("PENDING_REVIEW")
                .imageRetentionUntil(Instant.now().minusSeconds(60))
                .imagesPurgedAt(Instant.now().minusSeconds(30))
                .build();

        when(workerRepository.findByUser_UserId(workerUser.getUserId())).thenReturn(Optional.of(worker));
        when(ekycVerificationRepository.findTopByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId()))
                .thenReturn(Optional.of(expiredPending));
        when(ekycVerificationRepository.save(any(WorkerEkycVerification.class))).thenAnswer(i -> {
            WorkerEkycVerification v = i.getArgument(0);
            if (v.getId() == null) {
                UUID nextId = UUID.randomUUID();
                v.setId(nextId);
                lenient().when(ekycVerificationRepository.findByIdForUpdate(nextId)).thenReturn(Optional.of(v));
                lenient().when(ekycVerificationRepository.findById(nextId)).thenReturn(Optional.of(v));
            }
            return v;
        });
        when(ekycVerificationRepository.existsByCccdNumberHashInAndStatusInAndWorker_WorkerIdNot(any(), any(), any()))
                .thenReturn(false);
        when(eoEkycVerificationRepository.existsByCccdNumberHashInAndStatusInAndEventOrganizer_User_UserIdNot(any(), any(), any()))
                .thenReturn(false);
        when(workerRepository.existsByCccdNumberInAndWorkerIdNot(any(), any())).thenReturn(false);
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(validVnptMatchResult("079201012345", "TRẦN THỊ HOA"));

        EkycVerificationResultDto resubmitted = submitWithConsent(workerUser.getUserId(), null, null);
        assertThat(expiredPending.getStatus()).isEqualTo("REJECTED");
        assertThat(resubmitted.getVerificationId()).isNotEqualTo(expiredId);
        assertThat(resubmitted.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Từ chối tệp giả mạo magic bytes (HTML/text đổi đuôi .jpg) trước khi gọi VNPT")
    void shouldRejectSpoofedImageFileBeforeCallingVnpt() {
        MockMultipartFile fakeJpg = new MockMultipartFile("frontImage", "fake.jpg", "image/jpeg", "<html>not-an-image</html>".getBytes());

        assertThatThrownBy(() -> workerEkycService.submitEkyc(
                workerUser.getUserId(), fakeJpg, backFile, selfieFile,
                null, null, null, null, null, null, null, null,
                true, CONSENT_VERSION))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(ekycProvider);
    }

    private EkycVerificationResultDto submitWithConsent(UUID userId, String confirmedCccd, String confirmedName) {
        return workerEkycService.submitEkyc(
                userId, frontFile, backFile, selfieFile,
                confirmedCccd, confirmedName, null, null, null, null, null, null,
                true, CONSENT_VERSION);
    }

    private void stubHappyRepositoryFlow() {
        UUID generatedId = UUID.randomUUID();
        final WorkerEkycVerification[] holder = new WorkerEkycVerification[1];
        when(workerRepository.findByUser_UserId(workerUser.getUserId())).thenReturn(Optional.of(worker));
        when(ekycVerificationRepository.findTopByWorker_WorkerIdOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.empty());
        when(ekycVerificationRepository.save(any(WorkerEkycVerification.class))).thenAnswer(i -> {
            WorkerEkycVerification v = i.getArgument(0);
            if (v.getId() == null) {
                v.setId(generatedId);
            }
            holder[0] = v;
            return v;
        });
        lenient().when(ekycVerificationRepository.findByIdForUpdate(generatedId)).thenAnswer(i -> Optional.ofNullable(holder[0]));
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
