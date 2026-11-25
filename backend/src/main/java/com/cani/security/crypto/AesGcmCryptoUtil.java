package com.cani.security.crypto;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Tiện ích mã hóa mức trường (Field-Level Encryption - FLE) chuẩn AES-256-GCM.
 * Áp dụng cho dữ liệu nhạy cảm tài chính (Số tài khoản ngân hàng của Worker)
 * tuân thủ Nghị định 13/2023/NĐ-CP và tiêu chuẩn an toàn dữ liệu cá nhân.
 */
@Slf4j
public final class AesGcmCryptoUtil {

    public static final String MAGIC_PREFIX = "enc:v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    // Khóa fallback 256-bit (32 bytes) cho môi trường Dev/Test khi chưa cấu hình biến môi trường
    private static final String DEFAULT_DEV_KEY = "CANI_DEV_SECRET_BANK_KEY_32BYTES!";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmCryptoUtil() {
    }

    private static SecretKey getSecretKey() {
        String envKey = System.getenv("CANI_BANK_CRYPTO_KEY");
        if (envKey != null && !envKey.isBlank()) {
            byte[] keyBytes = envKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length >= 32) {
                return new SecretKeySpec(keyBytes, 0, 32, "AES");
            }
        }
        String propKey = System.getProperty("cani.bank.crypto-key");
        if (propKey != null && !propKey.isBlank()) {
            byte[] keyBytes = propKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length >= 32) {
                return new SecretKeySpec(keyBytes, 0, 32, "AES");
            }
        }
        byte[] defaultBytes = DEFAULT_DEV_KEY.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(defaultBytes, 0, 32, "AES");
    }

    /**
     * Mã hóa chuỗi plaintext sang chuỗi ciphertext có tiền tố `enc:v1:` kèm IV ngẫu nhiên.
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        if (plainText.startsWith(MAGIC_PREFIX)) {
            return plainText; // Đã mã hóa trước đó
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec);

            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherBytes);

            return MAGIC_PREFIX + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Lỗi khi mã hóa dữ liệu tài chính AES-GCM: {}", e.getMessage());
            throw new IllegalStateException("Không thể mã hóa dữ liệu nhạy cảm", e);
        }
    }

    /**
     * Giải mã chuỗi ciphertext về plaintext gốc.
     * Có hỗ trợ tương thích ngược (Backward Compatibility): Nếu không có tiền tố `enc:v1:`,
     * coi đây là dữ liệu cũ chưa mã hóa và trả về nguyên bản.
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return cipherText;
        }
        if (!cipherText.startsWith(MAGIC_PREFIX)) {
            // Tương thích ngược: dữ liệu plaintext cũ trong DB
            return cipherText;
        }
        try {
            String rawBase64 = cipherText.substring(MAGIC_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(rawBase64);

            if (combined.length < GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
                log.warn("Dữ liệu mã hóa không hợp lệ về độ dài: {}", cipherText);
                return cipherText;
            }

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);

            byte[] plainBytes = cipher.doFinal(combined, iv.length, combined.length - iv.length);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Lỗi khi giải mã dữ liệu tài chính AES-GCM: {}", e.getMessage());
            // Fallback an toàn tránh làm sập ứng dụng
            return cipherText;
        }
    }

    /**
     * Che mờ (Mask) số tài khoản ngân hàng để hiển thị an toàn trên giao diện UI:
     * Ví dụ: "1029384756" -> "••••••4756"
     */
    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "";
        }
        String clean = accountNumber.trim().replaceAll("\\s+", "");
        if (clean.length() <= 4) {
            return clean;
        }
        int visibleCount = 4;
        int maskLength = Math.min(clean.length() - visibleCount, 8);
        return "•".repeat(maskLength) + clean.substring(clean.length() - visibleCount);
    }
}
