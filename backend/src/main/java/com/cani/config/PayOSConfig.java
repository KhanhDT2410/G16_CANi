package com.cani.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cani.payos")
public class PayOSConfig {

    private String endpoint = "https://api-merchant.payos.vn";
    private String clientId;
    private String apiKey;
    private String checksumKey;
    private String returnUrl;
    private String cancelUrl;

    /**
     * Tạo chữ ký số HMAC-SHA256 cho yêu cầu tạo link thanh toán (5 trường chuẩn PayOS)
     * amount=${amount}&cancelUrl=${cancelUrl}&description=${description}&orderCode=${orderCode}&returnUrl=${returnUrl}
     */
    public String createRequestSignature(int amount, String cancelUrl, String description, long orderCode, String returnUrl) {
        String data = "amount=" + amount +
                "&cancelUrl=" + cancelUrl +
                "&description=" + description +
                "&orderCode=" + orderCode +
                "&returnUrl=" + returnUrl;
        return hmacSha256(data, this.checksumKey);
    }

    /**
     * Xác thực chữ ký số từ Webhook PayOS gửi sang
     */
    public boolean verifyWebhookSignature(Map<String, Object> data, String signature) {
        if (data == null || signature == null) {
            return false;
        }

        // 1. Sắp xếp các key của data theo thứ tự A-Z
        List<String> sortedKeys = new ArrayList<>(data.keySet());
        Collections.sort(sortedKeys);

        // 2. Nối chuỗi dạng key1=value1&key2=value2...
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sortedKeys.size(); i++) {
            String key = sortedKeys.get(i);
            Object val = data.get(key);
            sb.append(key).append("=").append(val != null ? val.toString() : "");
            if (i < sortedKeys.size() - 1) {
                sb.append("&");
            }
        }

        String calculatedSignature = hmacSha256(sb.toString(), this.checksumKey);
        return calculatedSignature.equalsIgnoreCase(signature);
    }

    public static String hmacSha256(String data, String key) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Lỗi tạo HMAC-SHA256: {}", e.getMessage());
            throw new RuntimeException("Lỗi sinh chữ ký bảo mật PayOS", e);
        }
    }
}

