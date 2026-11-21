package com.cani.service.eo;

import com.cani.service.*;

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
import org.springframework.test.util.ReflectionTestUtils;

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
                when(eoEkycVerificationRepository
                                .existsByCccdNumberHashAndStatusInAndEventOrganizer_EoIdNot(anyString(), any(), any()))
                                .thenReturn(false);
                when(eoEkycVerificationRepository.save(any(EoEkycVerification.class)))
                                .thenAnswer(invocation -> {
                                        EoEkycVerification v = invocation.getArgument(0);
                                        v.setId(UUID.randomUUID());
                                        return v;
                                });

                EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                                user.getUserId(), frontFile, backFile, selfieFile);

                assertNotNull(result);
                assertEquals("APPROVED", result.getStatus());
                assertEquals(VerificationStatus.APPROVED, eo.getVerificationStatus());
                verify(eventOrganizerRepository, times(1)).save(eo);
        }

        @Test
        @DisplayName("Admin duyệt thủ công hồ sơ eKYC thành công")
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

                EoEkycVerificationResultDto result = eventOrganizerEkycService.adminReviewEkyc(adminId, verificationId,
                                reviewRequest);

                assertEquals("APPROVED", result.getStatus());
                assertEquals(VerificationStatus.APPROVED, eo.getVerificationStatus());
                verify(eoEkycVerificationRepository, times(1)).save(pending);
                verify(eventOrganizerRepository, times(1)).save(eo);
        }

        @Test
        @DisplayName("Nộp eKYC doanh nghiệp qua Mock Provider và bật EKYC_STRICT=true thì chuyển PENDING_REVIEW")
        void submitEkyc_MockProvider_StrictEnabled_ManualReview() {
                ReflectionTestUtils.setField(eventOrganizerEkycService, "ekycStrict", true);

                when(eventOrganizerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(eo));

                EkycIdentityResultDto mockResult = EkycIdentityResultDto.builder()
                                .idNumber("001201012345")
                                .fullName("MOCK NAME")
                                .faceMatchScore(95.0)
                                .ocrConfidence(0.98)
                                .routingDecision("AUTO_APPROVED")
                                .build();

                when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(mockResult);
                when(eoEkycVerificationRepository
                                .existsByCccdNumberHashAndStatusInAndEventOrganizer_EoIdNot(anyString(), any(), any()))
                                .thenReturn(false);
                when(eoEkycVerificationRepository.save(any(EoEkycVerification.class)))
                                .thenAnswer(invocation -> {
                                        EoEkycVerification v = invocation.getArgument(0);
                                        v.setId(UUID.randomUUID());
                                        return v;
                                });

                EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                                user.getUserId(), frontFile, backFile, selfieFile);

                assertNotNull(result);
                assertEquals("PENDING_REVIEW", result.getStatus());
                assertEquals("MANUAL_REVIEW", result.getRoutingDecision());
                assertEquals("MOCK NAME", result.getRepresentativeName());
                assertTrue(result.getCccdNumberMasked().startsWith("001201******"));
                assertEquals(VerificationStatus.PENDING, eo.getVerificationStatus());
        }

        @Test
        @DisplayName("Nộp eKYC doanh nghiệp qua Mock Provider và EKYC_STRICT=false thì tự động duyệt và đồng bộ tên user")
        void submitEkyc_MockProvider_NotStrict_AutoApproved() {
                ReflectionTestUtils.setField(eventOrganizerEkycService, "ekycStrict", false);

                when(eventOrganizerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(eo));

                EkycIdentityResultDto mockResult = EkycIdentityResultDto.builder()
                                .idNumber("001201012345")
                                .fullName("MOCK NAME")
                                .faceMatchScore(95.0)
                                .ocrConfidence(0.98)
                                .routingDecision("AUTO_APPROVED")
                                .build();

                when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(mockResult);
                when(eoEkycVerificationRepository
                                .existsByCccdNumberHashAndStatusInAndEventOrganizer_EoIdNot(anyString(), any(), any()))
                                .thenReturn(false);
                when(eoEkycVerificationRepository.save(any(EoEkycVerification.class)))
                                .thenAnswer(invocation -> {
                                        EoEkycVerification v = invocation.getArgument(0);
                                        v.setId(UUID.randomUUID());
                                        return v;
                                });

                EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                                user.getUserId(), frontFile, backFile, selfieFile);

                assertNotNull(result);
                assertEquals("APPROVED", result.getStatus());
                assertEquals("AUTO_APPROVED", result.getRoutingDecision());
                assertEquals("MOCK NAME", result.getRepresentativeName());
                assertTrue(result.getCccdNumberMasked().startsWith("001201******"));
                assertEquals(VerificationStatus.APPROVED, eo.getVerificationStatus());
                verify(eventOrganizerRepository, times(1)).save(eo);
        }

        @Test
        @DisplayName("Từ chối eKYC khi phát hiện số CCCD đã được tài khoản khác sử dụng")
        void submitEkyc_DuplicateCccd_Rejected() {
                when(eventOrganizerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(eo));

                EkycIdentityResultDto providerResult = EkycIdentityResultDto.builder()
                                .idNumber("001201012345")
                                .fullName("Đại diện Doanh Nghiệp")
                                .dob("01/01/1990")
                                .gender("Nam")
                                .faceMatchScore(92.5)
                                .ocrConfidence(0.95)
                                .routingDecision("AUTO_APPROVED")
                                .build();

                when(ekycProvider.verifyIdentity(any(), any(), any(), any(), any(), any())).thenReturn(providerResult);
                when(eoEkycVerificationRepository
                                .existsByCccdNumberHashAndStatusInAndEventOrganizer_EoIdNot(anyString(), any(), any()))
                                .thenReturn(true);
                when(eoEkycVerificationRepository.save(any(EoEkycVerification.class)))
                                .thenAnswer(invocation -> {
                                        EoEkycVerification v = invocation.getArgument(0);
                                        v.setId(UUID.randomUUID());
                                        return v;
                                });

                EoEkycVerificationResultDto result = eventOrganizerEkycService.submitEkyc(
                                user.getUserId(), frontFile, backFile, selfieFile);

                assertNotNull(result);
                assertEquals("REJECTED", result.getStatus());
                assertEquals("REJECTED", result.getRoutingDecision());
                assertTrue(result.getRoutingReason().contains("Mỗi CCCD chỉ được định danh cho duy nhất 1 tài khoản"));
                assertEquals(VerificationStatus.REJECTED, eo.getVerificationStatus());
                verify(eventOrganizerRepository, times(1)).save(eo);
        }

        @Test
        @DisplayName("Cập nhật thông tin profile thất bại và ném BadRequestException tiếng Việt khi số điện thoại bị trùng với tài khoản khác")
        void updateEoProfile_DuplicatePhone_ThrowsBadRequestException() {
                when(eventOrganizerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(eo));

                User otherUser = User.builder()
                                .userId(UUID.randomUUID())
                                .phone("0328396095")
                                .fullName("Người Dùng Khác")
                                .build();
                when(userRepository.findByPhone("0328396095")).thenReturn(Optional.of(otherUser));

                com.cani.dto.ekyc.EoProfileDto dto = com.cani.dto.ekyc.EoProfileDto.builder()
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

                com.cani.dto.ekyc.EoProfileDto dto = com.cani.dto.ekyc.EoProfileDto.builder()
                                .phone("0328396095")
                                .fullName("Tên Mới")
                                .companyName("Công Ty Mới")
                                .build();

                com.cani.dto.ekyc.EoProfileDto updated = eventOrganizerEkycService.updateEoProfile(user.getUserId(), dto);
                assertNotNull(updated);
                assertEquals("0328396095", user.getPhone());
                verify(userRepository, times(1)).save(user);
        }
}
