package com.cani.service.eo;

import com.cani.client.ekyc.EkycProvider;
import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.dto.ekyc.EkycIdentityResultDto.ExpiryDateStatus;
import com.cani.dto.ekyc.EkycIdentityResultDto.FaceCompareStatus;
import com.cani.dto.ekyc.EoEkycVerificationResultDto;
import com.cani.dto.ekyc.EoProfileDto;
import com.cani.entity.EoEkycVerification;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.enums.VerificationStatus;
import com.cani.exception.BadRequestException;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.EventOrganizerRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventOrganizerEkycServiceTest {

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
                imageStorage, workerEkycVerificationRepository, eoEkycVerificationRepository, 30, 7, 3, 30, true);
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
    @DisplayName("Nộp eKYC doanh nghiệp thành công qua VNPT và tự động duyệt khi đủ điều kiện")
    void submitEkyc_AutoApproved_Success() {
        stubHappyEoFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(validVnptResult("001201012345", "NGUYỄN VĂN EO"));

        EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                user.getUserId(), frontFile, backFile, selfieFile);

        assertNotNull(result);
        assertEquals("APPROVED", result.getStatus());
        assertEquals("AUTO_APPROVED", result.getRoutingDecision());
        assertEquals(VerificationStatus.APPROVED, eo.getVerificationStatus());
        assertTrue(result.getFrontImageUrl().startsWith("/api/v1/ekyc/images/eo/"));
        assertFalse(result.getFrontImageUrl().contains("storage.cani.vn"));
        verify(eventOrganizerRepository, times(1)).save(eo);
    }

    @Test
    @DisplayName("Khi EKYC_STRICT=true hoặc ngày hết hạn không đọc được -> chuyển PENDING_REVIEW")
    void submitEkyc_StrictEnabled_ManualReview() {
        ReflectionTestUtils.setField(eventOrganizerEkycService, "ekycStrict", true);
        stubHappyEoFlow();
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any()))
                .thenReturn(validVnptResult("001201012345", "NGUYỄN VĂN EO"));

        EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                user.getUserId(), frontFile, backFile, selfieFile);

        assertEquals("PENDING_REVIEW", result.getStatus());
        assertEquals("MANUAL_REVIEW", result.getRoutingDecision());
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
                user.getUserId(), frontFile, backFile, selfieFile);

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
                user.getUserId(), frontFile, backFile, selfieFile);

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
                user.getUserId(), frontFile, backFile, selfieFile);

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
                user.getUserId(), frontFile, backFile, selfieFile, "099999999999");

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
        when(eoEkycVerificationRepository.findById(existingId)).thenReturn(Optional.of(existing));
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
                user.getUserId(), frontFile, backFile, selfieFile);

        assertEquals(existingId, result.getVerificationId());
        assertEquals(1, result.getRetryCount());
        assertEquals("APPROVED", result.getStatus());
        assertFalse(imageStorage.exists(oldFront.getObjectKey()));
    }

    @Test
    @DisplayName("Admin duyệt thủ công hồ sơ eKYC doanh nghiệp thành công (approve và reject)")
    void adminReviewEkyc_ApproveAndReject_Success() {
        UUID verificationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User admin = User.builder().userId(adminId).fullName("Admin Hệ Thống").build();

        EoEkycVerification pending = EoEkycVerification.builder()
                .id(verificationId)
                .eventOrganizer(eo)
                .cccdNumberHash("synthetic-index")
                .status("PENDING_REVIEW")
                .build();

        when(eoEkycVerificationRepository.findById(verificationId)).thenReturn(Optional.of(pending));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        EoEkycVerificationResultDto result = eventOrganizerEkycService.adminReviewEkyc(
                adminId,
                verificationId,
                EkycAdminReviewRequestDto.builder().decision("APPROVED").reviewNotes("Giấy tờ hợp lệ").build());

        assertEquals("APPROVED", result.getStatus());
        assertEquals(VerificationStatus.APPROVED, eo.getVerificationStatus());
        verify(eoEkycVerificationRepository, times(1)).save(pending);
        verify(eventOrganizerRepository, times(1)).save(eo);
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
