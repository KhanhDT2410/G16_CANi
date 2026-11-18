package com.cani.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests for QrCryptoUtil (HMAC-SHA256 Anti-spoofing)")
class QrCryptoUtilTest {

    @Test
    @DisplayName("Should generate 16-hex char signature and verify successfully")
    void testGenerateAndVerifySignature_Success() {
        String data = "CANI_CHECKIN:123e4567-e89b-12d3-a456-426614174000:1763223000";
        String signature = QrCryptoUtil.generateSignature(data);

        assertNotNull(signature);
        assertEquals(16, signature.length(), "HMAC signature should be exactly 16 hex chars");
        assertTrue(QrCryptoUtil.verifySignature(data, signature), "Signature verification must succeed for identical data");
    }

    @Test
    @DisplayName("Should reject signature if payload data is tampered")
    void testVerifySignature_TamperedData_Fails() {
        String originalData = "CANI_CHECKIN:123e4567-e89b-12d3-a456-426614174000:1763223000";
        String signature = QrCryptoUtil.generateSignature(originalData);

        String tamperedData = "CANI_CHECKIN:123e4567-e89b-12d3-a456-426614174000:1763223999";
        assertFalse(QrCryptoUtil.verifySignature(tamperedData, signature), "Signature verification must fail for tampered timestamp");

        String tamperedShift = "CANI_CHECKIN:999e4567-e89b-12d3-a456-426614174999:1763223000";
        assertFalse(QrCryptoUtil.verifySignature(tamperedShift, signature), "Signature verification must fail for tampered shiftId");
    }

    @Test
    @DisplayName("Should reject blank, null or wrong signature")
    void testVerifySignature_NullOrBlank_Fails() {
        String data = "CANI_CHECKOUT:123e4567-e89b-12d3-a456-426614174000:1763223000";
        assertFalse(QrCryptoUtil.verifySignature(data, null));
        assertFalse(QrCryptoUtil.verifySignature(data, ""));
        assertFalse(QrCryptoUtil.verifySignature(data, "   "));
        assertFalse(QrCryptoUtil.verifySignature(data, "invalid_sig_1234"));
    }
}
