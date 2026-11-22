package com.cani.service.ekyc.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Cấp và xác thực Signed URL ngắn hạn (HMAC-SHA256) cho ảnh CCCD / Selfie trong kho bảo mật.
 * - Đường dẫn: /api/v1/ekyc/images/{targetType}/{verificationId}/{slot}?expires=...&sig=...
 * - Không bao giờ cấp public URL; chữ ký gắn chặt với targetType + verificationId + slot + expires.
 * - Không hardcode secret; tự sinh khóa ngẫu nhiên theo phiên JVM nếu môi trường chưa đặt biến.
 */
@Slf4j
@Component
public class EkycImageAccessTokenService {

    public static final String IMAGE_BASE_PATH = "/api/v1/ekyc/images/";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Set<String> VALID_TARGETS = Set.of("worker", "eo");
    private static final Set<String> VALID_SLOTS = Set.of("front", "back", "selfie");

    private final byte[] signingSecret;
    private final long defaultTtlSeconds;
    private final Clock clock;

    @Autowired
    public EkycImageAccessTokenService(
            @Value("${cani.security.ekyc-image-signing-secret:${cani.security.document-signing-secret:}}") String configuredSecret,
            @Value("${cani.security.ekyc-image-url-ttl-seconds:180}") long defaultTtlSeconds
    ) {
        this(configuredSecret, defaultTtlSeconds, Clock.systemUTC());
    }

    EkycImageAccessTokenService(String configuredSecret, long defaultTtlSeconds, Clock clock) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            this.signingSecret = configuredSecret.trim().getBytes(StandardCharsets.UTF_8);
        } else {
            byte[] ephemeral = new byte[32];
            new SecureRandom().nextBytes(ephemeral);
            this.signingSecret = ephemeral;
            log.warn("cani.security.ekyc-image-signing-secret chưa được cấu hình; đang dùng khóa ngẫu nhiên theo phiên JVM.");
        }
        this.defaultTtlSeconds = defaultTtlSeconds > 0 ? defaultTtlSeconds : 180L;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public String generateSignedImageUrl(String targetType, UUID verificationId, String slot, String key, String status) {
        if (verificationId == null) {
            return null;
        }
        String normTarget = normalizeTarget(targetType);
        String normSlot = normalizeSlot(slot);
        if (normTarget == null || normSlot == null) {
            return null;
        }
        long expires = Instant.now(clock).getEpochSecond() + defaultTtlSeconds;
        String revision = revision(key, status);
        String sig = revision + "." + sign(normTarget, verificationId.toString(), normSlot + ":" + revision, expires);
        return IMAGE_BASE_PATH + normTarget + "/" + verificationId + "/" + normSlot
                + "?expires=" + expires + "&sig=" + sig;
    }

    public boolean isValidSignature(String targetType, String verificationId, String slot, String expiresParam, String signatureParam) {
        String normTarget = normalizeTarget(targetType);
        String normSlot = normalizeSlot(slot);
        if (normTarget == null || normSlot == null
                || verificationId == null || verificationId.isBlank()
                || expiresParam == null || expiresParam.isBlank()
                || signatureParam == null || signatureParam.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(verificationId.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        long expires;
        try {
            expires = Long.parseLong(expiresParam.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        long now = Instant.now(clock).getEpochSecond();
        if (now >= expires) {
            return false;
        }
        int split = signatureParam.indexOf('.');
        if (split != 43) return false;
        String revision = signatureParam.substring(0, split);
        String expected = revision + "." + sign(normTarget, verificationId.trim(), normSlot + ":" + revision, expires);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureParam.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Trích xuất và xác thực chữ ký từ URI dạng /api/v1/ekyc/images/{target}/{uuid}/{slot}.
     */
    public boolean isValidRequestUri(String requestUri, String expiresParam, String signatureParam) {
        if (requestUri == null || !requestUri.startsWith(IMAGE_BASE_PATH)) {
            return false;
        }
        String remainder = requestUri.substring(IMAGE_BASE_PATH.length());
        int qIdx = remainder.indexOf('?');
        if (qIdx >= 0) {
            remainder = remainder.substring(0, qIdx);
        }
        String[] parts = remainder.split("/");
        if (parts.length != 3) {
            return false;
        }
        return isValidSignature(parts[0], parts[1], parts[2], expiresParam, signatureParam);
    }

    public boolean matchesRecord(String signature, String key, String status) {
        return signature != null && signature.startsWith(revision(key, status) + ".");
    }

    private String revision(String key, String status) {
        return sign("revision", key, status, 0);
    }

    private String normalizeTarget(String target) {
        if (target == null) return null;
        String lower = target.trim().toLowerCase(Locale.ROOT);
        return VALID_TARGETS.contains(lower) ? lower : null;
    }

    private String normalizeSlot(String slot) {
        if (slot == null) return null;
        String lower = slot.trim().toLowerCase(Locale.ROOT);
        return VALID_SLOTS.contains(lower) ? lower : null;
    }

    private String sign(String target, String verificationId, String slot, long expiresEpochSec) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret, HMAC_ALGORITHM));
            String payload = "ekyc:" + target + ":" + verificationId + ":" + slot + ":" + expiresEpochSec;
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể ký URL bảo mật cho ảnh eKYC", e);
        }
    }
}
