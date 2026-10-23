package com.cani.service;

import com.cani.client.ekyc.EkycProvider;
import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.dto.ekyc.EoEkycVerificationResultDto;
import com.cani.entity.EoEkycVerification;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.enums.VerificationStatus;
import com.cani.exception.BadRequestException;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.UserRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventOrganizerEkycServiceTest {

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private EoEkycVerificationRepository eoEkycVerificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EkycProvider ekycProvider;

    @InjectMocks
    private EventOrganizerEkycService eventOrganizerEkycService;

    private User user;
    private EventOrganizer eo;
    private MockMultipartFile frontFile;
    private MockMultipartFile backFile;
    private MockMultipartFile selfieFile;

    @BeforeEach
    void setUp() {
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

        frontFile = new MockMultipartFile("front", "front.jpg", "image/jpeg", "front".getBytes());
        backFile = new MockMultipartFile("back", "back.jpg", "image/jpeg", "back".getBytes());
        selfieFile = new MockMultipartFile("selfie", "selfie.jpg", "image/jpeg", "selfie".getBytes());
    }

    @Test
    @DisplayName("Nộp eKYC doanh nghiệp thành công và tự động duyệt khi hồ sơ chuẩn")
    void submitEkyc_AutoApproved_Success() {
        when(eventOrganizerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(eo));

        EkycIdentityResultDto providerResult = EkycIdentityResultDto.builder()
                .idNumber("001201012345")
                .fullName("Đại diện Doanh Nghiệp")
                .dob("01/01/1990")
                .gender("Nam")
                .nationality("Việt Nam")
                .faceMatchScore(92.5)
                .ocrConfidence(0.95)
                .routingDecision("AUTO_APPROVED")
                .routingReason("Đầy đủ giấy tờ và khớp khuôn mặt")
                .build();

        when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(providerResult);
        when(eoEkycVerificationRepository.existsByCccdNumberHashAndStatus(anyString(), eq("APPROVED"))).thenReturn(false);
        when(eoEkycVerificationRepository.save(any(EoEkycVerification.class)))
                .thenAnswer(invocation -> {
                    EoEkycVerification v = invocation.getArgument(0);
                    v.setId(UUID.randomUUID());
                    return v;
                });

        EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                user.getUserId(), frontFile, backFile, selfieFile, null
        );

        assertNotNull(result);
        assertEquals("APPROVED", result.getStatus());
        assertEquals(VerificationStatus.APPROVED, eo.getVerificationStatus());
        verify(eventOrganizerRepository, times(1)).save(eo);
    }

    @Test
    @DisplayName("Admin duyệt thủ công hồ sơ eKYC doanh nghiệp thành công")
    void adminReviewEkyc_Approve_Success() {
        UUID verificationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User admin = User.builder().userId(adminId).fullName("Admin Hệ Thống").build();

        EoEkycVerification pending = EoEkycVerification.builder()
                .id(verificationId)
                .eventOrganizer(eo)
                .status("PENDING_REVIEW")
                .build();

        when(eoEkycVerificationRepository.findById(verificationId)).thenReturn(Optional.of(pending));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        EkycAdminReviewRequestDto reviewRequest = EkycAdminReviewRequestDto.builder()
                .decision("APPROVED")
                .reviewNotes("Giấy tờ hợp lệ và rõ ràng")
                .build();

        EoEkycVerificationResultDto result = eventOrganizerEkycService.adminReviewEkyc(adminId, verificationId, reviewRequest);

        assertEquals("APPROVED", result.getStatus());
        assertEquals(VerificationStatus.APPROVED, eo.getVerificationStatus());
        verify(eoEkycVerificationRepository, times(1)).save(pending);
        verify(eventOrganizerRepository, times(1)).save(eo);
    }
}
