package com.cani.service;

import com.cani.entity.EmailOtpVerification;
import com.cani.exception.BadRequestException;
import com.cani.repository.EmailOtpVerificationRepository;
import com.cani.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOtpService {

    private final EmailOtpVerificationRepository otpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Tạo và gửi mã OTP 6 số đến email người dùng.
     */
    @Transactional
    public void sendOtp(String rawEmail, String rawType) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new BadRequestException("Email không được để trống");
        }
        String email = rawEmail.trim().toLowerCase();
        String otpType = (rawType == null || rawType.isBlank()) ? "REGISTER" : rawType.toUpperCase().trim();

        // 1. Nếu là đăng ký mới, kiểm tra email đã tồn tại hay chưa
        if ("REGISTER".equalsIgnoreCase(otpType) && userRepository.existsByEmail(email)) {
            throw new BadRequestException("Địa chỉ email này đã được sử dụng bởi một tài khoản khác.");
        }

        // 2. Chống spam gửi mã liên tục (Cooldown 60s)
        otpRepository.findTopByEmailIgnoreCaseAndOtpTypeAndIsUsedFalseOrderByCreatedAtDesc(email, otpType)
                .ifPresent(existing -> {
                    if (existing.getCreatedAt() != null) {
                        long elapsed = Duration.between(existing.getCreatedAt(), Instant.now()).getSeconds();
                        if (elapsed < COOLDOWN_SECONDS) {
                            long remaining = COOLDOWN_SECONDS - elapsed;
                            throw new BadRequestException("Vui lòng đợi " + remaining + " giây trước khi yêu cầu mã xác thực mới.");
                        }
                    }
                });

        // 3. Sinh ngẫu nhiên mã OTP 6 chữ số
        String otpCode = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES);

        EmailOtpVerification verification = EmailOtpVerification.builder()
                .email(email)
                .otpCode(otpCode)
                .otpType(otpType)
                .expiresAt(expiresAt)
                .isUsed(false)
                .attempts(0)
                .build();

        otpRepository.save(verification);

        // 4. Gửi email
        emailService.sendOtpEmail(email, otpCode);
        log.info("Đã tạo và gửi OTP cho email: {}, loại: {}", email, otpType);
    }

    /**
     * Xác thực mã OTP 6 số từ người dùng.
     */
    @Transactional
    public void verifyOtp(String rawEmail, String otpCode, String rawType) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new BadRequestException("Email không được để trống");
        }
        if (otpCode == null || otpCode.isBlank()) {
            throw new BadRequestException("Mã OTP không được để trống");
        }

        String email = rawEmail.trim().toLowerCase();
        String otpType = (rawType == null || rawType.isBlank()) ? "REGISTER" : rawType.toUpperCase().trim();

        EmailOtpVerification verification = otpRepository
                .findTopByEmailIgnoreCaseAndOtpTypeAndIsUsedFalseOrderByCreatedAtDesc(email, otpType)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy mã OTP hoặc mã xác thực đã được sử dụng. Vui lòng gửi lại mã mới."));

        if (verification.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Mã xác thực OTP đã hết hạn (quá 5 phút). Vui lòng yêu cầu mã mới.");
        }

        int currentAttempts = verification.getAttempts() == null ? 0 : verification.getAttempts();
        if (currentAttempts >= MAX_ATTEMPTS) {
            throw new BadRequestException("Bạn đã nhập sai mã OTP quá " + MAX_ATTEMPTS + " lần. Vui lòng yêu cầu gửi lại mã mới.");
        }

        if (!verification.getOtpCode().equals(otpCode.trim())) {
            verification.setAttempts(currentAttempts + 1);
            otpRepository.save(verification);
            int remaining = Math.max(0, MAX_ATTEMPTS - (currentAttempts + 1));
            throw new BadRequestException("Mã xác thực OTP không chính xác. Bạn còn " + remaining + " lần thử.");
        }

        // Đánh dấu mã OTP đã được sử dụng thành công
        verification.setIsUsed(true);
        otpRepository.save(verification);
        log.info("Xác thực OTP thành công cho email: {}, loại: {}", email, otpType);
    }
}
