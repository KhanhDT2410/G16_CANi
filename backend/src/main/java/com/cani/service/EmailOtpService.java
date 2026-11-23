package com.cani.service;

import com.cani.entity.EmailOtpVerification;
import com.cani.exception.BadRequestException;
import com.cani.repository.EmailOtpVerificationRepository;
import com.cani.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOtpService {

    private final EmailOtpVerificationRepository otpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    @Value("${cani.security.ekyc-hash-pepper:}")
    private String otpHashPepper;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 3;
    private static final int RETENTION_HOURS_AFTER_EXPIRY = 24;

    /**
     * Tạo và gửi mã OTP 6 số đến email người dùng (chỉ lưu mã băm trong DB).
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

        // 3. Sinh ngẫu nhiên mã OTP 6 chữ số và lưu bản băm vào CSDL
        String otpCode = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES);

        EmailOtpVerification verification = EmailOtpVerification.builder()
                .email(email)
                .otpCode(hashOtp(email, otpType, otpCode))
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
     * Lưu ý: Không bọc @Transactional ở method này và tách bước ghi nhận attempts sai sang REQUIRES_NEW
     * để việc ném BadRequestException ở transaction cha (AuthService) không làm rollback số lần thử sai.
     */
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

        if (!matchesOtp(verification.getOtpCode(), email, otpType, otpCode.trim())) {
            int nextAttempts = currentAttempts + 1;
            verification.setAttempts(nextAttempts);
            persistFailedAttemptInNewTransaction(verification, nextAttempts);
            int remaining = Math.max(0, MAX_ATTEMPTS - nextAttempts);
            throw new BadRequestException("Mã xác thực OTP không chính xác. Bạn còn " + remaining + " lần thử.");
        }

        // Đánh dấu mã OTP đã được sử dụng thành công
        verification.setIsUsed(true);
        otpRepository.saveAndFlush(verification);
        log.info("Xác thực OTP thành công cho email: {}, loại: {}", email, otpType);
    }

    /**
     * Dọn dẹp định kỳ các bản ghi OTP đã hết hạn quá 24 giờ để giảm phình bảng và dữ liệu tồn dư.
     */
    @Scheduled(cron = "${cani.auth.otp-cleanup-cron:0 15 * * * *}")
    @Transactional
    public void purgeExpiredOtps() {
        Instant cutoff = Instant.now().minus(RETENTION_HOURS_AFTER_EXPIRY, ChronoUnit.HOURS);
        otpRepository.deleteByExpiresAtBefore(cutoff);
    }

    private void persistFailedAttemptInNewTransaction(EmailOtpVerification verification, int nextAttempts) {
        if (transactionManager != null && verification.getId() != null) {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            UUID recordId = verification.getId();
            template.executeWithoutResult(status -> otpRepository.findById(recordId).ifPresent(fresh -> {
                fresh.setAttempts(nextAttempts);
                otpRepository.saveAndFlush(fresh);
            }));
            return;
        }
        otpRepository.saveAndFlush(verification);
    }

    private boolean matchesOtp(String storedValue, String email, String otpType, String submittedOtp) {
        if (storedValue == null || submittedOtp == null) {
            return false;
        }
        String candidateHash = hashOtp(email, otpType, submittedOtp);
        if (constantTimeEquals(storedValue, candidateHash)) {
            return true;
        }
        // Tương thích các bản ghi 6 chữ số cũ trước thời điểm bật băm OTP
        return storedValue.length() == 6
                && storedValue.chars().allMatch(Character::isDigit)
                && constantTimeEquals(storedValue, submittedOtp);
    }

    private String hashOtp(String email, String otpType, String otpCode) {
        String payload = otpType + ":" + email + ":" + otpCode;
        try {
            if (otpHashPepper != null && !otpHashPepper.isBlank()) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(otpHashPepper.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(("CANI_OTP:" + payload).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo mã băm OTP an toàn.", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
