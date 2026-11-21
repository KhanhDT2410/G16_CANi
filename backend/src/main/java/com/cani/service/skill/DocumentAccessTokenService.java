package com.cani.service.skill;

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

/**
 * Cấp và xác thực Signed URL có thời hạn (HMAC-SHA256) cho tài liệu chứng chỉ của Worker.
 * Giúp hiển thị ảnh/PDF bảo mật trong khoảng thời gian ngắn (mặc định 5 phút) mà không công khai tệp.
 */
@Component
@Slf4j
public class DocumentAccessTokenService {

    public static final String DOCUMENT_BASE_PATH = "/api/v1/workers/skills/documents/";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] signingSecret;
    private final long defaultTtlSeconds;
    private final Clock clock;

    @Autowired
    public DocumentAccessTokenService(
            @Value("${cani.security.document-signing-secret:}") String configuredSecret,
            @Value("${cani.security.document-url-ttl-seconds:300}") long defaultTtlSeconds
    ) {
        this(configuredSecret, defaultTtlSeconds, Clock.systemUTC());
    }

    DocumentAccessTokenService(String configuredSecret, long defaultTtlSeconds, Clock clock) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            this.signingSecret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        } else {
            byte[] ephemeral = new byte[32];
            new SecureRandom().nextBytes(ephemeral);
            this.signingSecret = ephemeral;
            log.warn("cani.security.document-signing-secret chưa được cấu hình; đang dùng khóa ngẫu nhiên theo phiên JVM.");
        }
        this.defaultTtlSeconds = defaultTtlSeconds > 0 ? defaultTtlSeconds : 300L;
        this.clock = clock;
    }

    /**
     * Sinh đường dẫn Signed URL có thời hạn từ đường dẫn lưu trong DB hoặc tên tệp.
     */
    public String generateSignedDocumentUrl(String storedDocumentUrlOrFilename) {
        String filename = extractFilename(storedDocumentUrlOrFilename);
        if (filename == null || filename.isBlank()) {
            return storedDocumentUrlOrFilename;
        }
        long expires = Instant.now(clock).getEpochSecond() + defaultTtlSeconds;
        String sig = sign(filename, expires);
        return DOCUMENT_BASE_PATH + filename + "?expires=" + expires + "&sig=" + sig;
    }

    /**
     * Xác thực chữ ký và thời hạn truy cập cho đúng tên tệp được yêu cầu.
     */
    public boolean isValidSignature(String filename, String expiresParam, String signatureParam) {
        if (filename == null || filename.isBlank()
                || expiresParam == null || expiresParam.isBlank()
                || signatureParam == null || signatureParam.isBlank()) {
            return false;
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false;
        }
        long expires;
        try {
            expires = Long.parseLong(expiresParam.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        long now = Instant.now(clock).getEpochSecond();
        if (now > expires) {
            return false;
        }
        String expectedSig = sign(filename, expires);
        return MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                signatureParam.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Trích xuất tên tệp nội bộ từ URL lưu trữ (`/api/v1/workers/skills/documents/cert_xxx.jpg`).
     * Trả về null nếu là URL bên ngoài (https://...).
     */
    public String extractFilename(String documentUrl) {
        if (documentUrl == null || documentUrl.isBlank()) {
            return null;
        }
        String trimmed = documentUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return null;
        }
        String pathOnly = trimmed;
        int qIdx = pathOnly.indexOf('?');
        if (qIdx >= 0) {
            pathOnly = pathOnly.substring(0, qIdx);
        }
        if (pathOnly.startsWith(DOCUMENT_BASE_PATH)) {
            return pathOnly.substring(DOCUMENT_BASE_PATH.length());
        }
        if (!pathOnly.contains("/") && !pathOnly.contains("\\")) {
            return pathOnly;
        }
        return null;
    }

    private String sign(String filename, long expiresEpochSec) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret, HMAC_ALGORITHM));
            String payload = filename + ":" + expiresEpochSec;
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo chữ ký bảo mật tài liệu", e);
        }
    }
}
