package com.cani.security;

import com.cani.security.crypto.AesGcmCryptoUtil;
import com.cani.security.crypto.BankAccountEncryptionConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BankAccountEncryptionConverterTest {

    private final BankAccountEncryptionConverter converter = new BankAccountEncryptionConverter();

    @Test
    @DisplayName("UTC-CONV-01: convertToDatabaseColumn phải mã hóa chuỗi với tiền tố enc:v1:")
    void testConvertToDatabaseColumn() {
        String plain = "0987654321";
        String dbData = converter.convertToDatabaseColumn(plain);

        assertThat(dbData).isNotNull();
        assertThat(dbData).startsWith(AesGcmCryptoUtil.MAGIC_PREFIX);
    }

    @Test
    @DisplayName("UTC-CONV-02: convertToEntityAttribute phải giải mã về plaintext gốc")
    void testConvertToEntityAttribute() {
        String plain = "0987654321";
        String dbData = converter.convertToDatabaseColumn(plain);
        String restored = converter.convertToEntityAttribute(dbData);

        assertThat(restored).isEqualTo(plain);
    }

    @Test
    @DisplayName("UTC-CONV-03: Xử lý null an toàn")
    void testNullHandling() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
