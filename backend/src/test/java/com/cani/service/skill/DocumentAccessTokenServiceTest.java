package com.cani.service.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DocumentAccessTokenService — Signed URL HMAC-SHA256 & Expiry Tests")
class DocumentAccessTokenServiceTest {

    private static final String TEST_SECRET = "cani-test-hmac-secret-key-256bit-strong";

    @Test
    @DisplayName("Signed URL hợp lệ trong thời hạn TTL và xác thực thành công cho đúng filename")
    void generateAndVerifySignedUrl_ValidWithinTtl() {
        Instant fixedNow = Instant.parse("2026-11-22T00:00:00Z");
        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        DocumentAccessTokenService service = new DocumentAccessTokenService(TEST_SECRET, 300, fixedClock);

        String signedUrl = service.generateSignedDocumentUrl("/api/v1/workers/skills/documents/cert_abc123.jpg");
        assertTrue(signedUrl.startsWith("/api/v1/workers/skills/documents/cert_abc123.jpg?expires="));
        assertTrue(signedUrl.contains("&sig="));

        String query = signedUrl.substring(signedUrl.indexOf('?') + 1);
        String expires = query.split("&")[0].split("=")[1];
        String sig = query.split("&")[1].split("=")[1];

        assertTrue(service.isValidSignature("cert_abc123.jpg", expires, sig));
        // Chữ ký cho cert_abc123.jpg KHÔNG dùng được cho tệp khác
        assertFalse(service.isValidSignature("cert_other999.jpg", expires, sig));
    }

    @Test
    @DisplayName("Truy cập Signed URL đã hết hạn hoặc bị sửa tham số -> trả về false")
    void verifySignedUrl_ExpiredOrTampered_ReturnsFalse() {
        Instant t0 = Instant.parse("2026-11-22T00:00:00Z");
        DocumentAccessTokenService issuer = new DocumentAccessTokenService(TEST_SECRET, 60, Clock.fixed(t0, ZoneOffset.UTC));

        String signedUrl = issuer.generateSignedDocumentUrl("cert_abc123.jpg");
        String query = signedUrl.substring(signedUrl.indexOf('?') + 1);
        String expires = query.split("&")[0].split("=")[1];
        String sig = query.split("&")[1].split("=")[1];

        // Sau 61 giây -> đã hết hạn
        DocumentAccessTokenService verifierAfterExpiry = new DocumentAccessTokenService(
                TEST_SECRET, 60, Clock.fixed(t0.plusSeconds(61), ZoneOffset.UTC)
        );
        assertFalse(verifierAfterExpiry.isValidSignature("cert_abc123.jpg", expires, sig));

        // Sửa thời gian expires -> chữ ký không khớp
        long tamperedExpires = Long.parseLong(expires) + 9999;
        assertFalse(issuer.isValidSignature("cert_abc123.jpg", String.valueOf(tamperedExpires), sig));

        // Path traversal trong filename -> từ chối
        assertFalse(issuer.isValidSignature("../cert_abc123.jpg", expires, sig));
    }
}
