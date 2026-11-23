package com.cani.service.ekyc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Chuẩn hóa, băm bảo mật (HMAC-SHA256 khi có pepper hoặc SHA-256 tương thích ngược)
 * và che số CCCD tuân thủ Nghị định 13/2023/NĐ-CP.
 */
@Slf4j
@Component
public class EkycIdentityCryptoService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final String hashPepper;

    @Autowired
    public EkycIdentityCryptoService(
            @Value("${cani.security.ekyc-hash-pepper:}") String hashPepper
    ) {
        this.hashPepper = hashPepper != null ? hashPepper.trim() : "";
        if (this.hashPepper.isEmpty()) {
            log.warn("CCCD indexing is unavailable until EKYC_HASH_PEPPER is configured.");
        }
    }

    public String normalizeCccd(String rawCccd) {
        if (rawCccd == null || rawCccd.isBlank()) {
            return null;
        }
        String digits = rawCccd.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    public boolean isValidCccdFormat(String normalizedCccd) {
        return normalizedCccd != null && normalizedCccd.length() >= 9 && normalizedCccd.length() <= 12;
    }

    /**
     * Tính mã băm chính để lưu vào DB.
     * Nếu EKYC_HASH_PEPPER được cấu hình -> HMAC-SHA256 chống brute-force rainbow table.
     * Nếu chưa cấu hình -> SHA-256 tương thích với dữ liệu hiện tại.
     */
    public String hashCccd(String rawCccd) {
        String normalized = normalizeCccd(rawCccd);
        if (normalized == null) {
            return null;
        }
        if (!hashPepper.isEmpty()) {
            return hmacSha256Hex(normalized, hashPepper);
        }
        throw new IllegalStateException("EKYC_HASH_PEPPER is required for identity verification");
    }

    /**
     * Trả về tập hợp hash cần tra cứu trùng lặp (bao gồm cả HMAC hiện tại và SHA-256 cũ nếu đang bật pepper)
     * để đảm bảo tương thích 100% với bản ghi cũ đã lưu trước khi bật pepper.
     */
    public Set<String> computeLookupHashes(String rawCccd) {
        String normalized = normalizeCccd(rawCccd);
        if (normalized == null) {
            return Set.of();
        }
        Set<String> hashes = new LinkedHashSet<>();
        String primary = hashCccd(normalized);
        if (primary != null) {
            hashes.add(primary);
        }
        if (!hashPepper.isEmpty()) {
            hashes.add(sha256Hex(normalized));
        }
        return hashes;
    }

    /**
     * Che số CCCD cho hiển thị UI và API response (ví dụ: 079201******345).
     */
    public String maskCccd(String rawCccd) {
        if (rawCccd == null || rawCccd.isBlank()) {
            return null;
        }
        String clean = rawCccd.replaceAll("\\D", "");
        if (clean.length() < 6) {
            return "******";
        }
        return "********" + clean.substring(clean.length() - 4);
    }

    /**
     * Sinh các biến thể masked cũ để đối soát cột workers.cccd_number legacy.
     */
    public Set<String> buildLegacyCandidateStrings(String rawCccd) {
        String normalized = normalizeCccd(rawCccd);
        if (normalized == null || normalized.length() < 9) {
            return Set.of();
        }
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);
        String masked = normalized.substring(0, 6) + "******" + (normalized.length() > 9 ? normalized.substring(normalized.length() - 3) : "");
        if (masked != null) {
            candidates.add(masked);
        }
        candidates.add(normalized.substring(0, 3) + "******" + normalized.substring(normalized.length() - 3));
        return candidates;
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tính HMAC-SHA256 cho CCCD", e);
        }
    }

    private String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tính SHA-256 cho CCCD", e);
        }
    }
}
