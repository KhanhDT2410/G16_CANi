package com.cani.service.eo;

import com.cani.client.ekyc.EkycProvider;
import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.dto.ekyc.EkycIdentityResultDto.ExpiryDateStatus;
import com.cani.dto.ekyc.EkycIdentityResultDto.FaceCompareStatus;
import com.cani.dto.ekyc.EoEkycVerificationResultDto;
import com.cani.dto.ekyc.EoProfileDto;
import com.cani.entity.EkycConsentRecord;
import com.cani.entity.EoEkycVerification;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.enums.VerificationStatus;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.repository.EkycConsentRecordRepository;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.SystemConfigRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerEkycVerificationRepository;
import com.cani.repository.WorkerRepository;
import com.cani.service.EventOrganizerEkycService;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventOrganizerEkycServiceTest {

    private static final String CONSENT_VERSION = "2026.11-nd13-v1";
    private static final byte[] VALID_JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46
    };

    @TempDir
    Path tempDir;

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private EoEkycVerificationRepository eoEkycVerificationRepository;

    @Mock
    private WorkerEkycVerificationRepository workerEkycVerificationRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EkycConsentRecordRepository consentRecordRepository;

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @Mock
    private EkycProvider ekycProvider;

    private EventOrganizerEkycService eventOrganizerEkycService;
    private LocalPrivateEkycImageStorage imageStorage;

    private User user;
    private EventOrganizer eo;
    private MockMultipartFile frontFile;
    private MockMultipartFile backFile;
    private MockMultipartFile selfieFile;

    @BeforeEach
    void setUp() {
        VnptEkycProperties vnptProperties = new VnptEkycProperties();
        vnptProperties.setVerifyBackCard(true);
        EkycIdentityCryptoService cryptoService = new EkycIdentityCryptoService("test-eo-pepper");
        EkycDecisionEngine decisionEngine = new EkycDecisionEngine(vnptProperties, cryptoService);
        EkycImageValidator imageValidator = new EkycImageValidator(5L * 1024L * 1024L);
        imageStorage = new LocalPrivateEkycImageStorage(tempDir.toString());
        EkycImageAccessTokenService accessTokenService = new EkycImageAccessTokenService("test-eo-signing", 180);
        EkycImageRetentionService retentionService = new EkycImageRetentionService(
                imageStorage, workerEkycVerificationRepository, eoEkycVerificationRepository, 0, 0, 3, 14, true);
        EkycCccdLockManager lockManager = new EkycCccdLockManager();

        eventOrganizerEkycService = new EventOrganizerEkycService(
                eventOrganizerRepository,
                eoEkycVerificationRepository,
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
        ReflectionTestUtils.setField(eventOrganizerEkycService, "workerEkycVerificationRepository", workerEkycVerificationRepository);
        ReflectionTestUtils.setField(eventOrganizerEkycService, "workerRepository", workerRepository);
        ReflectionTestUtils.setField(eventOrganizerEkycService, "consentRecordRepository", consentRecordRepository);
        ReflectionTestUtils.setField(eventOrganizerEkycService, "systemConfigRepository", systemConfigRepository);
        ReflectionTestUtils.setField(eventOrganizerEkycService, "ekycStrict", false);

        lenient().when(ekycProvider.getProviderName()).thenReturn("VNPT_EKYC");

        user = User.builder()
                .userId(UUID.randomUUID())
                .email("eo@cani.vn")
                .fullName("Đại diện Doanh Nghiệp")
                .build();

        eo = EventOrganizer.builder()
                .eoId(UUID.randomUUID())
                .user(user)
                .companyName("Công ty TNHH Sự Kiện Sáng Tạo")
                .verificationStatus(VerificationStatus.PENDING)
                .build();

        frontFile = new MockMultipartFile("front", "front.jpg", "image/jpeg", VALID_JPEG);
        backFile = new MockMultipartFile("back", "back.jpg", "image/jpeg", VALID_JPEG);
        selfieFile = new MockMultipartFile("selfie", "selfie.jpg", "image/jpeg", VALID_JPEG);
    }

    @Test
    @DisplayName("Bắt buộc đồng ý Nghị định 13/2023/NĐ-CP trước khi nộp EO eKYC")
    void submitEkyc_RequiresConsent() {
        assertThrows(BadRequestException.class, () ->
                eventOrganizerEkycService.submitEkyc(user.getUserId(), frontFile, backFile, selfieFile));
        verifyNoInteractions(ekycProvider);
        verifyNoInteractions(consentRecordRepository);
    }

    @Test
    @DisplayName("Nộp eKYC EO thành công -> giảm thiểu OCR thô, lưu consent và xóa ảnh ngay sau commit khi retention=0")
    void submitEkyc_AutoApproved_SuccessWithMinimization() {
        stubHappyEoFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(validVnptResult("001201012345", "NGUYỄN VĂN EO"));

        EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                user.getUserId(), frontFile, backFile, selfieFile, null, true, CONSENT_VERSION);

        assertNotNull(result);
        assertEquals("APPROVED", result.getStatus());
        assertEquals("AUTO_APPROVED", result.getRoutingDecision());
        assertEquals(VerificationStatus.APPROVED, eo.getVerificationStatus());
        assertEquals(Boolean.TRUE, result.getMeetsMinimumAge());
        assertEquals(CONSENT_VERSION, result.getConsentNoticeVersion());
        assertNull(result.getDob());
        assertNull(result.getGender());
        assertNull(result.getResidenceAddress());
        verify(consentRecordRepository, times(1)).save(any(EkycConsentRecord.class));
        verify(eventOrganizerRepository, times(1)).save(eo);
    }

    @Test
    @DisplayName("Khi EKYC_STRICT=true -> PENDING_REVIEW giữ tạm ảnh 14 ngày kèm Signed URL")
    void submitEkyc_StrictEnabled_ManualReview() {
        ReflectionTestUtils.setField(eventOrganizerEkycService, "ekycStrict", true);
        stubHappyEoFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(validVnptResult("001201012345", "NGUYỄN VĂN EO"));

        EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                user.getUserId(), frontFile, backFile, selfieFile, null, true, CONSENT_VERSION);

        assertEquals("PENDING_REVIEW", result.getStatus());
        assertEquals("MANUAL_REVIEW", result.getRoutingDecision());
        assertEquals(Boolean.TRUE, result.getImagesAvailable());
        assertEquals(Boolean.FALSE, result.getImagesExpired());
        assertTrue(result.getFrontImageUrl().startsWith("/api/v1/ekyc/images/eo/"));
        assertEquals(VerificationStatus.PENDING, eo.getVerificationStatus());
    }

    @Test
    @DisplayName("Lỗi kỹ thuật từ VNPT -> TECHNICAL_FAILURE (cho phép retry), không chuyển EO sang REJECTED")
    void submitEkyc_TechnicalFailure_Retryable() {
        stubHappyEoFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(EkycIdentityResultDto.builder()
                        .providerName("VNPT_EKYC")
                        .technicalFailure(true)
                        .technicalErrorCode("VNPT_FACE_COMPARE_TIMEOUT")
                        .routingReason("Face Compare quá thời gian chờ")
                        .build());

        EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                user.getUserId(), frontFile, backFile, selfieFile, null, true, CONSENT_VERSION);

        assertEquals("TECHNICAL_FAILURE", result.getStatus());
        assertEquals("VNPT_FACE_COMPARE_TIMEOUT", result.getFailureCode());
        assertEquals(Boolean.TRUE, result.getRetryable());
        assertEquals(VerificationStatus.PENDING, eo.getVerificationStatus());
    }

    @Test
    @DisplayName("Tài khoản EO đã APPROVED không bị hạ cấp khi lần xác minh mới gặp lỗi kỹ thuật")
    void submitEkyc_AlreadyApproved_NotDowngradedOnTechnicalFailure() {
        eo.setVerificationStatus(VerificationStatus.APPROVED);
        stubHappyEoFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(EkycIdentityResultDto.builder()
                        .providerName("VNPT_EKYC")
                        .technicalFailure(true)
                        .technicalErrorCode("VNPT_CONNECTION_ERROR")
                        .routingReason("Mất kết nối VNPT")
                        .build());

        EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                user.getUserId(), frontFile, backFile, selfieFile, null, true, CONSENT_VERSION);

        assertEquals("TECHNICAL_FAILURE", result.getStatus());
        assertEquals(VerificationStatus.APPROVED, eo.getVerificationStatus());
    }

    @Test
    @DisplayName("Từ chối eKYC khi phát hiện CCCD trùng với tài khoản khác (EO hoặc Worker)")
    void submitEkyc_DuplicateCccd_Rejected() {
        stubHappyEoFlow();
        when(eoEkycVerificationRepository
                .existsByCccdNumberHashInAndStatusInAndEventOrganizer_EoIdNot(any(), any(), any()))
                .thenReturn(true);
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(validVnptResult("001201012345", "NGUYỄN VĂN EO"));

        EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                user.getUserId(), frontFile, backFile, selfieFile, null, true, CONSENT_VERSION);

        assertEquals("REJECTED", result.getStatus());
        assertTrue(result.getRoutingReason().contains("Mỗi CCCD chỉ được định danh cho duy nhất 1 tài khoản"));
        assertEquals(VerificationStatus.REJECTED, eo.getVerificationStatus());
    }

    @Test
    @DisplayName("confirmedCccd từ client KHÔNG được ghi đè kết quả VNPT OCR -> lệch thì chuyển PENDING_REVIEW")
    void submitEkyc_ClientCccdDoesNotOverwriteVnptOcr() {
        stubHappyEoFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(validVnptResult("001201012345", "NGUYỄN VĂN EO"));

        EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                user.getUserId(), frontFile, backFile, selfieFile, "099999999999", true, CONSENT_VERSION);

        assertEquals("********2345", result.getCccdNumberMasked());
        assertEquals("PENDING_REVIEW", result.getStatus());
        assertEquals(Boolean.TRUE, result.getFraudFlags().get("userInputMismatch"));
        assertEquals(VerificationStatus.PENDING, eo.getVerificationStatus());
    }

    @Test
    @DisplayName("Retry sau TECHNICAL_FAILURE tái sử dụng bản ghi cũ, tăng retryCount và không tạo bản ghi mới")
    void submitEkyc_RetryReusesTechnicalFailureRecord() {
        UUID existingId = UUID.randomUUID();
        EkycImageStorage.StoredObject oldFront = imageStorage.store("eo", eo.getEoId(), "front", VALID_JPEG, "jpg", "image/jpeg");
        EoEkycVerification existing = EoEkycVerification.builder()
                .id(existingId)
                .eventOrganizer(eo)
                .status("TECHNICAL_FAILURE")
                .routingDecision("TECHNICAL_FAILURE")
                .retryCount(0)
                .frontImageUrl(oldFront.getObjectKey())
                .build();

        when(eventOrganizerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(eo));
        when(eoEkycVerificationRepository.findTopByEventOrganizer_EoIdOrderByCreatedAtDesc(
                eq(eo.getEoId()))).thenReturn(Optional.of(existing));
        when(eoEkycVerificationRepository.findByIdForUpdate(existingId)).thenReturn(Optional.of(existing));
        lenient().when(eoEkycVerificationRepository.findById(existingId)).thenReturn(Optional.of(existing));
        when(eoEkycVerificationRepository.save(any(EoEkycVerification.class))).thenAnswer(i -> i.getArgument(0));
        when(eoEkycVerificationRepository.existsByCccdNumberHashInAndStatusInAndEventOrganizer_EoIdNot(any(), any(), any()))
                .thenReturn(false);
        when(eoEkycVerificationRepository.existsByCccdNumberHashInAndStatus(any(), any())).thenReturn(false);
        when(workerEkycVerificationRepository.existsByCccdNumberHashInAndStatusInAndWorker_User_UserIdNot(any(), any(), any()))
                .thenReturn(false);
        when(workerRepository.existsByCccdNumberIn(any())).thenReturn(false);
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(validVnptResult("001201012345", "NGUYỄN VĂN EO"));

        EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                user.getUserId(), frontFile, backFile, selfieFile, null, true, CONSENT_VERSION);

        assertEquals(existingId, result.getVerificationId());
        assertEquals(1, result.getRetryCount());
        assertEquals("APPROVED", result.getStatus());
        assertFalse(imageStorage.exists(oldFront.getObjectKey()));
    }

    @Test
    @DisplayName("Admin duyệt thủ công EO yêu cầu đủ 3 ảnh đọc được; chặn APPROVED nếu thiếu/hết hạn ảnh")
    void adminReviewEkyc_RequiresReadableImagesAndBlocksExpired() {
        UUID verificationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User admin = User.builder().userId(adminId).fullName("Admin Hệ Thống").build();

        EkycImageStorage.StoredObject frontObj = imageStorage.store("eo", eo.getEoId(), "front", VALID_JPEG, "jpg", "image/jpeg");
        EkycImageStorage.StoredObject backObj = imageStorage.store("eo", eo.getEoId(), "back", VALID_JPEG, "jpg", "image/jpeg");
        EkycImageStorage.StoredObject selfieObj = imageStorage.store("eo", eo.getEoId(), "selfie", VALID_JPEG, "jpg", "image/jpeg");

        EoEkycVerification pending = EoEkycVerification.builder()
                .id(verificationId)
                .eventOrganizer(eo)
                .cccdNumberHash("synthetic-index")
                .frontImageUrl(frontObj.getObjectKey())
                .backImageUrl(backObj.getObjectKey())
                .selfieImageUrl(selfieObj.getObjectKey())
                .imageRetentionUntil(Instant.now().minusSeconds(60))
                .status("PENDING_REVIEW")
                .build();

        when(eoEkycVerificationRepository.findByIdForUpdate(verificationId)).thenReturn(Optional.of(pending));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        assertThrows(BadRequestException.class, () -> eventOrganizerEkycService.adminReviewEkyc(
                adminId, verificationId,
                EkycAdminReviewRequestDto.builder().decision("APPROVED").build()));

        pending.setImageRetentionUntil(Instant.now().plus(7, ChronoUnit.DAYS));
        when(eoEkycVerificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        EoEkycVerificationResultDto result = eventOrganizerEkycService.adminReviewEkyc(
                adminId, verificationId,
                EkycAdminReviewRequestDto.builder().decision("APPROVED").reviewNotes("Giấy tờ hợp lệ").build());

        assertEquals("APPROVED", result.getStatus());
        assertEquals(VerificationStatus.APPROVED, eo.getVerificationStatus());
        assertFalse(imageStorage.exists(frontObj.getObjectKey()));
    }

    @Test
    @DisplayName("EO có thể nộp lại sau khi PENDING_REVIEW trước đó hết hạn và bị chặn 409 nếu đang PROCESSING")
    void submitEkyc_BlocksActiveProcessingAndAllowsExpiredPendingResubmit() {
        EoEkycVerification active = EoEkycVerification.builder()
                .id(UUID.randomUUID())
                .eventOrganizer(eo)
                .status("PROCESSING")
                .processingStartedAt(Instant.now().minusSeconds(10))
                .build();
        when(eventOrganizerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(eo));
        when(eoEkycVerificationRepository.findTopByEventOrganizer_EoIdOrderByCreatedAtDesc(eo.getEoId()))
                .thenReturn(Optional.of(active));

        AppException conflict = assertThrows(AppException.class, () -> eventOrganizerEkycService.submitEkyc(
                user.getUserId(), frontFile, backFile, selfieFile, null, true, CONSENT_VERSION));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatus());

        UUID oldExpiredId = UUID.randomUUID();
        EoEkycVerification expiredPending = EoEkycVerification.builder()
                .id(oldExpiredId)
                .eventOrganizer(eo)
                .status("PENDING_REVIEW")
                .imagesPurgedAt(Instant.now().minusSeconds(30))
                .imageRetentionUntil(Instant.now().minusSeconds(60))
                .build();
        when(eoEkycVerificationRepository.findTopByEventOrganizer_EoIdOrderByCreatedAtDesc(eo.getEoId()))
                .thenReturn(Optional.of(expiredPending));
        when(eoEkycVerificationRepository.save(any(EoEkycVerification.class))).thenAnswer(i -> {
            EoEkycVerification v = i.getArgument(0);
            if (v.getId() == null) {
                UUID nextId = UUID.randomUUID();
                v.setId(nextId);
                lenient().when(eoEkycVerificationRepository.findByIdForUpdate(nextId)).thenReturn(Optional.of(v));
                lenient().when(eoEkycVerificationRepository.findById(nextId)).thenReturn(Optional.of(v));
            }
            return v;
        });
        when(eoEkycVerificationRepository.existsByCccdNumberHashInAndStatusInAndEventOrganizer_EoIdNot(any(), any(), any()))
                .thenReturn(false);
        when(eoEkycVerificationRepository.existsByCccdNumberHashInAndStatus(any(), any())).thenReturn(false);
        when(workerEkycVerificationRepository.existsByCccdNumberHashInAndStatusInAndWorker_User_UserIdNot(any(), any(), any()))
                .thenReturn(false);
        when(workerRepository.existsByCccdNumberIn(any())).thenReturn(false);
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(validVnptResult("001201012345", "NGUYỄN VĂN EO"));

        EoEkycVerificationResultDto resubmitted = eventOrganizerEkycService.submitEkyc(
                user.getUserId(), frontFile, backFile, selfieFile, null, true, CONSENT_VERSION);
        assertEquals("REJECTED", expiredPending.getStatus());
        assertNotEquals(oldExpiredId, resubmitted.getVerificationId());
        assertEquals("APPROVED", resubmitted.getStatus());
    }

    @Test
    @DisplayName("Cập nhật thông tin profile thất bại khi số điện thoại bị trùng với tài khoản khác")
    void updateEoProfile_DuplicatePhone_ThrowsBadRequestException() {
        when(eventOrganizerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(eo));

        User otherUser = User.builder()
                .userId(UUID.randomUUID())
                .phone("0328396095")
                .fullName("Người Dùng Khác")
                .build();
        when(userRepository.findByPhone("0328396095")).thenReturn(Optional.of(otherUser));

        EoProfileDto dto = EoProfileDto.builder()
                .phone("0328396095")
                .fullName("Tên Mới")
                .companyName("Công Ty Mới")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                eventOrganizerEkycService.updateEoProfile(user.getUserId(), dto));

        assertTrue(ex.getMessage().contains("Số điện thoại này đã được sử dụng bởi một tài khoản khác"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Cập nhật thông tin profile thành công khi số điện thoại thuộc về chính user hiện tại")
    void updateEoProfile_SameUserPhone_Success() {
        user.setPhone("0328396095");
        when(eventOrganizerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(eo));
        when(userRepository.findByPhone("0328396095")).thenReturn(Optional.of(user));

        EoProfileDto dto = EoProfileDto.builder()
                .phone("0328396095")
                .fullName("Tên Mới")
                .companyName("Công Ty Mới")
                .build();

        EoProfileDto updated = eventOrganizerEkycService.updateEoProfile(user.getUserId(), dto);
        assertNotNull(updated);
        assertEquals("0328396095", user.getPhone());
        verify(userRepository, times(1)).save(user);
    }

    private void stubHappyEoFlow() {
        UUID generatedId = UUID.randomUUID();
        final EoEkycVerification[] holder = new EoEkycVerification[1];
        when(eventOrganizerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(eo));
        when(eoEkycVerificationRepository.findTopByEventOrganizer_EoIdOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.empty());
        when(eoEkycVerificationRepository.save(any(EoEkycVerification.class))).thenAnswer(i -> {
            EoEkycVerification v = i.getArgument(0);
            if (v.getId() == null) {
                v.setId(generatedId);
            }
            holder[0] = v;
            return v;
        });
        lenient().when(eoEkycVerificationRepository.findByIdForUpdate(generatedId)).thenAnswer(i -> Optional.ofNullable(holder[0]));
        lenient().when(eoEkycVerificationRepository.findById(generatedId)).thenAnswer(i -> Optional.ofNullable(holder[0]));
        lenient().when(eoEkycVerificationRepository.existsByCccdNumberHashInAndStatusInAndEventOrganizer_EoIdNot(any(), any(), any()))
                .thenReturn(false);
        lenient().when(eoEkycVerificationRepository.existsByCccdNumberHashInAndStatus(any(), any())).thenReturn(false);
        lenient().when(workerEkycVerificationRepository.existsByCccdNumberHashInAndStatusInAndWorker_User_UserIdNot(any(), any(), any()))
                .thenReturn(false);
        lenient().when(workerRepository.existsByCccdNumberIn(any())).thenReturn(false);
    }

    private EkycIdentityResultDto validVnptResult(String cccd, String fullName) {
        return EkycIdentityResultDto.builder()
                .providerName("VNPT_EKYC")
                .providerReference("CANI_EKYC_EO_1")
                .idNumber(cccd)
                .fullName(fullName)
                .dob("01/01/1990")
                .gender("Nam")
                .nationality("Việt Nam")
                .expiryDate("01/01/2040")
                .issueDate("10/05/2021")
                .expiryStatus(ExpiryDateStatus.VALID)
                .faceCompareStatus(FaceCompareStatus.MATCH)
                .faceCompareMessage("MATCH")
                .faceMatchScore(92.5)
                .backCardVerified(true)
                .documentLegal(true)
                .fakeDocument(false)
                .technicalFailure(false)
                .build();
    }
}
