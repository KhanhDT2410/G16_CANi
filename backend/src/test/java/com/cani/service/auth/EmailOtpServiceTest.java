package com.cani.service.auth;

import com.cani.service.*;

import com.cani.entity.EmailOtpVerification;
import com.cani.exception.BadRequestException;
import com.cani.repository.EmailOtpVerificationRepository;
import com.cani.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOtpServiceTest {

    @Mock
    private EmailOtpVerificationRepository otpRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailOtpService emailOtpService;

    private final String testEmail = "test@example.com";

    @Test
    @DisplayName("Gửi OTP thành công khi email chưa đăng ký và không bị cooldown")
    void sendOtp_Success() {
        when(userRepository.existsByEmail(testEmail)).thenReturn(false);
        when(otpRepository.findTopByEmailIgnoreCaseAndOtpTypeAndIsUsedFalseOrderByCreatedAtDesc(eq(testEmail), eq("REGISTER")))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> emailOtpService.sendOtp(testEmail, "REGISTER"));

        verify(otpRepository, times(1)).save(any(EmailOtpVerification.class));
        verify(emailService, times(1)).sendOtpEmail(eq(testEmail), anyString());
    }

    @Test
    @DisplayName("Gửi OTP thất bại khi email đã tồn tại trong hệ thống")
    void sendOtp_EmailAlreadyExists_ThrowsBadRequest() {
        when(userRepository.existsByEmail(testEmail)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> emailOtpService.sendOtp(testEmail, "REGISTER"));

        assertTrue(ex.getMessage().contains("đã được sử dụng"));
        verify(otpRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(any(), any());
    }

    @Test
    @DisplayName("Gửi OTP thất bại khi vi phạm thời gian chờ gửi lại (cooldown 60s)")
    void sendOtp_CooldownViolation_ThrowsBadRequest() {
        when(userRepository.existsByEmail(testEmail)).thenReturn(false);

        EmailOtpVerification recentOtp = EmailOtpVerification.builder()
                .email(testEmail)
                .otpCode("123456")
                .otpType("REGISTER")
                .createdAt(Instant.now().minus(20, ChronoUnit.SECONDS))
                .build();

        when(otpRepository.findTopByEmailIgnoreCaseAndOtpTypeAndIsUsedFalseOrderByCreatedAtDesc(eq(testEmail), eq("REGISTER")))
                .thenReturn(Optional.of(recentOtp));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> emailOtpService.sendOtp(testEmail, "REGISTER"));

        assertTrue(ex.getMessage().contains("Vui lòng đợi"));
        verify(emailService, never()).sendOtpEmail(any(), any());
    }

    @Test
    @DisplayName("Xác thực OTP thành công khi mã khớp và còn hạn")
    void verifyOtp_Success() {
        EmailOtpVerification validOtp = EmailOtpVerification.builder()
                .id(UUID.randomUUID())
                .email(testEmail)
                .otpCode("654321")
                .otpType("REGISTER")
                .expiresAt(Instant.now().plus(4, ChronoUnit.MINUTES))
                .attempts(0)
                .isUsed(false)
                .build();

        when(otpRepository.findTopByEmailIgnoreCaseAndOtpTypeAndIsUsedFalseOrderByCreatedAtDesc(eq(testEmail), eq("REGISTER")))
                .thenReturn(Optional.of(validOtp));

        assertDoesNotThrow(() -> emailOtpService.verifyOtp(testEmail, "654321", "REGISTER"));

        assertTrue(validOtp.getIsUsed());
        verify(otpRepository, times(1)).saveAndFlush(validOtp);
    }

    @Test
    @DisplayName("Xác thực OTP thất bại khi mã OTP đã hết hạn")
    void verifyOtp_Expired_ThrowsBadRequest() {
        EmailOtpVerification expiredOtp = EmailOtpVerification.builder()
                .email(testEmail)
                .otpCode("654321")
                .otpType("REGISTER")
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .attempts(0)
                .isUsed(false)
                .build();

        when(otpRepository.findTopByEmailIgnoreCaseAndOtpTypeAndIsUsedFalseOrderByCreatedAtDesc(eq(testEmail), eq("REGISTER")))
                .thenReturn(Optional.of(expiredOtp));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> emailOtpService.verifyOtp(testEmail, "654321", "REGISTER"));

        assertTrue(ex.getMessage().contains("hết hạn"));
    }

    @Test
    @DisplayName("Xác thực OTP thất bại khi nhập sai mã -> tăng số lần thử")
    void verifyOtp_WrongCode_IncrementsAttempts() {
        EmailOtpVerification otp = EmailOtpVerification.builder()
                .email(testEmail)
                .otpCode("111111")
                .otpType("REGISTER")
                .expiresAt(Instant.now().plus(4, ChronoUnit.MINUTES))
                .attempts(1)
                .isUsed(false)
                .build();

        when(otpRepository.findTopByEmailIgnoreCaseAndOtpTypeAndIsUsedFalseOrderByCreatedAtDesc(eq(testEmail), eq("REGISTER")))
                .thenReturn(Optional.of(otp));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> emailOtpService.verifyOtp(testEmail, "999999", "REGISTER"));

        assertTrue(ex.getMessage().contains("không chính xác"));
        assertEquals(2, otp.getAttempts());
        verify(otpRepository, times(1)).saveAndFlush(otp);
    }

    @Test
    @DisplayName("Gửi OTP lưu mã băm 64 ký tự (không lưu plaintext) và xác thực thành công bằng mã gốc")
    void sendOtp_StoresHashedDigest_AndVerifiesPlaintext() {
        ReflectionTestUtils.setField(emailOtpService, "otpHashPepper", "otp-test-pepper");
        when(userRepository.existsByEmail(testEmail)).thenReturn(false);
        when(otpRepository.findTopByEmailIgnoreCaseAndOtpTypeAndIsUsedFalseOrderByCreatedAtDesc(eq(testEmail), eq("REGISTER")))
                .thenReturn(Optional.empty());

        emailOtpService.sendOtp(testEmail, "REGISTER");

        ArgumentCaptor<EmailOtpVerification> savedCaptor = ArgumentCaptor.forClass(EmailOtpVerification.class);
        ArgumentCaptor<String> plainCaptor = ArgumentCaptor.forClass(String.class);
        verify(otpRepository).save(savedCaptor.capture());
        verify(emailService).sendOtpEmail(eq(testEmail), plainCaptor.capture());

        EmailOtpVerification stored = savedCaptor.getValue();
        String plaintext = plainCaptor.getValue();
        assertEquals(6, plaintext.length());
        assertEquals(64, stored.getOtpCode().length());
        assertNotEquals(plaintext, stored.getOtpCode());

        stored.setId(UUID.randomUUID());
        when(otpRepository.findTopByEmailIgnoreCaseAndOtpTypeAndIsUsedFalseOrderByCreatedAtDesc(eq(testEmail), eq("REGISTER")))
                .thenReturn(Optional.of(stored));

        assertDoesNotThrow(() -> emailOtpService.verifyOtp(testEmail, plaintext, "REGISTER"));
        assertTrue(stored.getIsUsed());
    }

    @Test
    @DisplayName("Nhập sai OTP khi có TransactionManager -> cập nhật attempts trong REQUIRES_NEW transaction")
    void verifyOtp_WrongCodeWithTransactionManager_CommitsAttemptsInRequiresNew() {
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        ReflectionTestUtils.setField(emailOtpService, "transactionManager", txManager);

        UUID otpId = UUID.randomUUID();
        EmailOtpVerification otp = EmailOtpVerification.builder()
                .id(otpId)
                .email(testEmail)
                .otpCode("0000000000000000000000000000000000000000000000000000000000000000")
                .otpType("REGISTER")
                .expiresAt(Instant.now().plus(4, ChronoUnit.MINUTES))
                .attempts(0)
                .isUsed(false)
                .build();

        when(otpRepository.findTopByEmailIgnoreCaseAndOtpTypeAndIsUsedFalseOrderByCreatedAtDesc(eq(testEmail), eq("REGISTER")))
                .thenReturn(Optional.of(otp));
        when(otpRepository.findById(otpId)).thenReturn(Optional.of(otp));

        assertThrows(BadRequestException.class,
                () -> emailOtpService.verifyOtp(testEmail, "999999", "REGISTER"));

        ArgumentCaptor<TransactionDefinition> defCaptor = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(txManager).getTransaction(defCaptor.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, defCaptor.getValue().getPropagationBehavior());
        assertEquals(1, otp.getAttempts());
        verify(otpRepository).saveAndFlush(otp);
    }

    @Test
    @DisplayName("Dọn dẹp OTP đã hết hạn quá 24 giờ gọi deleteByExpiresAtBefore")
    void purgeExpiredOtps_DeletesOldRecords() {
        emailOtpService.purgeExpiredOtps();
        verify(otpRepository).deleteByExpiresAtBefore(any(Instant.class));
    }
}
