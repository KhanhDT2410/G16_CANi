package com.cani.security;

import com.cani.security.crypto.AesGcmCryptoUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AesGcmCryptoUtilTest {

    @Test
    @DisplayName("UTC-SEC-01: Round-trip Encryption & Decryption phải trả về đúng chuỗi ban đầu")
    void testRoundTripEncryption() {
        String plain = "1029384756";
        String encrypted = AesGcmCryptoUtil.encrypt(plain);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).startsWith(AesGcmCryptoUtil.MAGIC_PREFIX);
        assertThat(encrypted).isNotEqualTo(plain);

        String decrypted = AesGcmCryptoUtil.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    @DisplayName("UTC-SEC-02: IV ngẫu nhiên - Mã hóa 2 lần liên tiếp cùng 1 số tài khoản phải sinh ra 2 ciphertext khác nhau")
    void testIvRandomization() {
        String plain = "1029384756";
        String cipher1 = AesGcmCryptoUtil.encrypt(plain);
        String cipher2 = AesGcmCryptoUtil.encrypt(plain);

        assertThat(cipher1).isNotEqualTo(cipher2);
        assertThat(AesGcmCryptoUtil.decrypt(cipher1)).isEqualTo(plain);
        assertThat(AesGcmCryptoUtil.decrypt(cipher2)).isEqualTo(plain);
    }

    @Test
    @DisplayName("UTC-SEC-03: Backward Compatibility - Dữ liệu cũ chưa có tiền tố enc:v1: phải trả về nguyên bản")
    void testBackwardCompatibility() {
        String legacyPlain = "9988776655";
        String result = AesGcmCryptoUtil.decrypt(legacyPlain);

        assertThat(result).isEqualTo(legacyPlain);
    }

    @Test
    @DisplayName("UTC-SEC-04: Xử lý chuỗi null hoặc rỗng an toàn")
    void testNullOrEmptyHandling() {
        assertThat(AesGcmCryptoUtil.encrypt(null)).isNull();
        assertThat(AesGcmCryptoUtil.encrypt("")).isEmpty();
        assertThat(AesGcmCryptoUtil.encrypt("   ")).isEqualTo("   ");

        assertThat(AesGcmCryptoUtil.decrypt(null)).isNull();
        assertThat(AesGcmCryptoUtil.decrypt("")).isEmpty();
    }

    @Test
    @DisplayName("UTC-SEC-05: Masking số tài khoản ngân hàng để hiển thị an toàn trên giao diện")
    void testMasking() {
        assertThat(AesGcmCryptoUtil.mask("1029384756")).isEqualTo("••••••4756");
        assertThat(AesGcmCryptoUtil.mask("12345")).isEqualTo("•2345");
        assertThat(AesGcmCryptoUtil.mask("1234")).isEqualTo("1234");
        assertThat(AesGcmCryptoUtil.mask(null)).isEmpty();
        assertThat(AesGcmCryptoUtil.mask("")).isEmpty();
    }
}
