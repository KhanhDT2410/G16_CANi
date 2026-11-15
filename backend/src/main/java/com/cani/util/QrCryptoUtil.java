package com.cani.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Tiện ích mã hóa và xác thực chữ ký số HMAC-SHA256 cho Mã QR Điểm danh (Anti-spoofing).
 * Ngăn chặn tuyệt đối việc Worker tự sinh mã QR hoặc can thiệp API từ xa mà không có mặt tại sự kiện.
 */
@Slf4j
public final class QrCryptoUtil {

    private static final String DEFAULT_SECRET = "CANI_QR_HMAC_SECRET_SEP490_SPRING_2026";
    private static final String HMAC_ALGO = "HmacSHA256";

    private QrCryptoUtil() {}

    /**
     * Sinh chữ ký HMAC-SHA256 độ dài 16 ký tự hex gọn nhẹ, tối ưu render QR Code trên camera di động.
     *
     * @param data Dữ liệu cần ký (ví dụ: CANI_CHECKIN:<shiftId>:<epochSeconds>)
     * @return 16 ký tự hex của mã băm HMAC
     */
    public static String generateSignature(String data) {
        if (data == null) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec secretKeySpec = new SecretKeySpec(DEFAULT_SECRET.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes).substring(0, 16);
        } catch (Exception e) {
            log.error("Lỗi khi sinh chữ ký HMAC cho dữ liệu QR: {}", e.getMessage());
            return Integer.toHexString(data.hashCode());
        }
    }

    /**
     * Xác thực chữ ký HMAC-SHA256 sử dụng so sánh an toàn thời gian không đổi (Constant-time comparison)
     * chống tấn công Timing Attacks.
     *
     * @param data Dữ liệu gốc đã ký
     * @param expectedSignature Chữ ký gửi kèm cần kiểm tra
     * @return true nếu chữ ký hợp lệ và khớp 100%
     */
    public static boolean verifySignature(String data, String expectedSignature) {
        if (data == null || expectedSignature == null || expectedSignature.isBlank()) {
            return false;
        }
        String actualSignature = generateSignature(data);
        return MessageDigest.isEqual(
                actualSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
}
