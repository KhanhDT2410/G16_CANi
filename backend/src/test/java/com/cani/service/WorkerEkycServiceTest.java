package com.cani.service;

import com.cani.client.ekyc.EkycProvider;
import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycVerificationResultDto;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerEkycVerification;
import com.cani.entity.enums.KycStatus;
import com.cani.repository.WorkerEkycVerificationRepository;
import com.cani.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerEkycServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private WorkerEkycVerificationRepository ekycVerificationRepository;

    @Mock
    private EkycProvider ekycProvider;

    @InjectMocks
    private WorkerEkycService workerEkycService;

    private User workerUser;
    private Worker worker;
    private MockMultipartFile frontFile;
    private MockMultipartFile backFile;
    private MockMultipartFile selfieFile;

    @BeforeEach
    void setUp() {
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

        frontFile = new MockMultipartFile("frontImage", "front.jpg", "image/jpeg", "fake_front_bytes".getBytes());
        backFile = new MockMultipartFile("backImage", "back.jpg", "image/jpeg", "fake_back_bytes".getBytes());
        selfieFile = new MockMultipartFile("selfieImage", "selfie.jpg", "image/jpeg", "fake_selfie_bytes".getBytes());
    }

    @Test
    @DisplayName("Tự động duyệt eKYC thành công khi AI trả về độ khớp khuôn mặt và OCR cao")
    void shouldAutoApproveEkycWhenAiReturnsHighScore() {
        EkycIdentityResultDto aiResult = EkycIdentityResultDto.builder()
                .idNumber("079201012345")
                .fullName("TRẦN THỊ HOA")
                .dob("15/05/2001")
                .gender("Nữ")
                .ocrConfidence(0.95)
                .faceMatchScore(92.0)
                .routingDecision("AUTO_APPROVED")
                .routingReason("Ảnh CCCD rõ nét, khớp khuôn mặt 92%")
                .isCutCorner(false)
                .isPhotoshoped(false)
                .isScreenCaptured(false)
                .isExpired(false)
                .build();

        when(workerRepository.findByUser_UserId(workerUser.getUserId())).thenReturn(Optional.of(worker));
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(aiResult);
        when(ekycVerificationRepository.existsByCccdNumberHashAndStatus(any(), eq("APPROVED"))).thenReturn(false);
        when(ekycVerificationRepository.save(any(WorkerEkycVerification.class))).thenAnswer(i -> {
            WorkerEkycVerification v = i.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        EkycVerificationResultDto result = workerEkycService.submitEkyc(workerUser.getUserId(), frontFile, backFile, selfieFile);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getRoutingDecision()).isEqualTo("AUTO_APPROVED");
        assertThat(result.getCccdNumberMasked()).startsWith("079201******");
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.APPROVED);
        verify(workerRepository, times(1)).save(worker);
    }

    @Test
    @DisplayName("Chuyển hàng đợi Admin duyệt khi ảnh hơi mờ hoặc điểm khớp khuôn mặt trung bình")
    void shouldSetPendingReviewWhenAiConfidenceIsMedium() {
        EkycIdentityResultDto aiResult = EkycIdentityResultDto.builder()
                .idNumber("079201012345")
                .fullName("TRẦN THỊ HOA")
                .ocrConfidence(0.75)
                .faceMatchScore(72.0)
                .routingDecision("MANUAL_REVIEW")
                .routingReason("Ảnh selfie có bóng mờ nhẹ, cần Admin thẩm định thủ công")
                .build();

        when(workerRepository.findByUser_UserId(workerUser.getUserId())).thenReturn(Optional.of(worker));
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(aiResult);
        when(ekycVerificationRepository.existsByCccdNumberHashAndStatus(any(), eq("APPROVED"))).thenReturn(false);
        when(ekycVerificationRepository.save(any(WorkerEkycVerification.class))).thenAnswer(i -> {
            WorkerEkycVerification v = i.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        EkycVerificationResultDto result = workerEkycService.submitEkyc(workerUser.getUserId(), frontFile, backFile, selfieFile);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(result.getRoutingDecision()).isEqualTo("MANUAL_REVIEW");
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    @DisplayName("Từ chối eKYC khi phát hiện số CCCD đã được tài khoản khác xác thực trước đó")
    void shouldRejectWhenDuplicateCccdDetected() {
        EkycIdentityResultDto aiResult = EkycIdentityResultDto.builder()
                .idNumber("079201012345")
                .ocrConfidence(0.95)
                .faceMatchScore(90.0)
                .routingDecision("AUTO_APPROVED")
                .build();

        when(workerRepository.findByUser_UserId(workerUser.getUserId())).thenReturn(Optional.of(worker));
        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(aiResult);
        when(ekycVerificationRepository.existsByCccdNumberHashAndStatus(any(), eq("APPROVED"))).thenReturn(true);
        when(ekycVerificationRepository.save(any(WorkerEkycVerification.class))).thenAnswer(i -> {
            WorkerEkycVerification v = i.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        EkycVerificationResultDto result = workerEkycService.submitEkyc(workerUser.getUserId(), frontFile, backFile, selfieFile);

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getRoutingReason()).contains("liên kết với một tài khoản Worker khác");
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.REJECTED);
    }

    @Test
    @DisplayName("Admin phê duyệt hồ sơ eKYC thủ công thành công")
    void shouldAdminApproveSuccessfully() {
        UUID verificationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        WorkerEkycVerification verification = WorkerEkycVerification.builder()
                .id(verificationId)
                .worker(worker)
                .status("PENDING_REVIEW")
                .cccdNumberMasked("079201******345")
                .frontImageUrl("https://storage.cani.vn/front.jpg")
                .backImageUrl("https://storage.cani.vn/back.jpg")
                .selfieImageUrl("https://storage.cani.vn/selfie.jpg")
                .build();

        EkycAdminReviewRequestDto request = EkycAdminReviewRequestDto.builder()
                .decision("APPROVED")
                .reviewNotes("Đã đối soát mắt người rõ ràng.")
                .build();

        when(ekycVerificationRepository.findById(verificationId)).thenReturn(Optional.of(verification));
        when(ekycVerificationRepository.save(any(WorkerEkycVerification.class))).thenAnswer(i -> i.getArgument(0));

        EkycVerificationResultDto result = workerEkycService.adminReviewEkyc(adminId, verificationId, request);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(worker.getKycStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(worker.getCccdNumber()).isEqualTo("079201******345");
        verify(workerRepository, times(1)).save(worker);
    }
}
