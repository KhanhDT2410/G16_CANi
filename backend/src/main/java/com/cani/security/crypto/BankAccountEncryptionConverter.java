package com.cani.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter tự động mã hóa AES-256-GCM khi ghi vào DB
 * và giải mã khi đọc lên Java Entity cho trường số tài khoản ngân hàng.
 * Hoàn toàn trong suốt với tầng Service và Controller.
 */
@Converter
public class BankAccountEncryptionConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return AesGcmCryptoUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return AesGcmCryptoUtil.decrypt(dbData);
    }
}
