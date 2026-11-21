package com.cani.service.skill;

import com.cani.entity.enums.IssuerVerificationStatus;
import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Đánh giá độ tin cậy của tổ chức cấp chứng chỉ (Issuer Verification).
 * Minh bạch hóa trạng thái xác minh: KHÔNG giả vờ đã đối soát online nếu không có nguồn tra cứu chính thức.
 */
@Component
public class TrustedIssuerRegistry {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /**
     * Danh sách tên miền xác minh chính thức của các tổ chức cấp chứng chỉ quốc tế phổ biến.
     */
    private static final Set<String> OFFICIAL_VERIFICATION_DOMAINS = Set.of(
            "sca.coffee",
            "wsetglobal.com",
            "coursera.org",
            "credly.com",
            "accredible.com",
            "credential.net",
            "edx.org",
            "ielts.org",
            "britishcouncil.org",
            "ets.org",
            "cloudskillsboost.google",
            "google.com"
    );

    /**
     * Danh mục cơ sở đào tạo / tổ chức uy tín đã được CANi kiểm duyệt nội bộ (chưa có public API tra cứu).
     */
    private static final List<String> KNOWN_TRUSTED_ISSUER_KEYWORDS = List.of(
            "saigontourist",
            "huong nghiep a au",
            "pha che a au",
            "a au",
            "cao dang du lich",
            "dai hoc du lich",
            "trung cap du lich",
            "bo cong an",
            "cuc canh sat",
            "cong an thanh pho",
            "specialty coffee association",
            "sca",
            "wset",
            "coursera",
            "british council",
            "idp education",
            "vma academy",
            "dai hoc van lang",
            "dai hoc hoa sen",
            "dai hoc ton duc thang",
            "dai hoc fpt",
            "dai hoc kinh te",
            "so lao dong",
            "tong cuc giao duc nghe nghiep",
            "truong trung cap nghe",
            "truong cao dang nghe",
            "hoi dau bep",
            "vietnam barista",
            "event club hcmc"
    );

    @Value
    @Builder
    public static class IssuerAssessment {
        IssuerVerificationStatus status;
        int score;
        String verificationSource;
        String verificationReference;
        boolean unofficialUrlDetected;
    }

    public IssuerAssessment assess(
            String issuerName,
            String verificationUrl,
            String certificateNumber,
            boolean hasVerificationQr
    ) {
        String trimmedIssuer = issuerName != null ? issuerName.trim() : "";
        String trimmedUrl = verificationUrl != null ? verificationUrl.trim() : "";
        String trimmedCertNo = certificateNumber != null ? certificateNumber.trim() : "";

        String matchedOfficialDomain = matchOfficialDomain(trimmedUrl);
        boolean hasUrl = !trimmedUrl.isBlank();
        boolean unofficialUrl = hasUrl && matchedOfficialDomain == null;

        // 1. Có URL xác minh thuộc tên miền chính thức trong whitelist + mã chứng chỉ hoặc đường dẫn định danh cụ thể
        if (matchedOfficialDomain != null && (!trimmedCertNo.isBlank() || hasSpecificUrlPath(trimmedUrl))) {
            return IssuerAssessment.builder()
                    .status(IssuerVerificationStatus.VERIFIED_ONLINE)
                    .score(100)
                    .verificationSource("OFFICIAL_DOMAIN_WHITELIST:" + matchedOfficialDomain)
                    .verificationReference(trimmedUrl)
                    .unofficialUrlDetected(false)
                    .build();
        }

        // 2. Tổ chức thuộc Trusted Issuer Registry nội bộ (nhưng chưa đối soát trực tuyến được)
        if (!trimmedIssuer.isBlank() && isKnownTrustedIssuer(trimmedIssuer)) {
            int score = (!trimmedCertNo.isBlank() || hasVerificationQr) ? 80 : 75;
            String ref = !trimmedCertNo.isBlank() ? trimmedCertNo : (hasUrl ? trimmedUrl : null);
            return IssuerAssessment.builder()
                    .status(IssuerVerificationStatus.KNOWN_ISSUER_UNVERIFIED_ONLINE)
                    .score(score)
                    .verificationSource("CANI_TRUSTED_ISSUER_REGISTRY")
                    .verificationReference(ref)
                    .unofficialUrlDetected(unofficialUrl)
                    .build();
        }

        // 3. Có tên tổ chức nhưng không nằm trong danh mục uy tín đã biết -> chưa xác minh nguồn phát hành
        if (trimmedIssuer.length() >= 3) {
            String ref = !trimmedCertNo.isBlank() ? trimmedCertNo : (hasUrl ? trimmedUrl : null);
            return IssuerAssessment.builder()
                    .status(IssuerVerificationStatus.UNVERIFIED)
                    .score(45)
                    .verificationSource("UNVERIFIED_EXTERNAL_ISSUER")
                    .verificationReference(ref)
                    .unofficialUrlDetected(unofficialUrl)
                    .build();
        }

        // 4. Không có tên tổ chức cấp
        return IssuerAssessment.builder()
                .status(IssuerVerificationStatus.UNKNOWN_ISSUER)
                .score(0)
                .verificationSource("NONE")
                .verificationReference(null)
                .unofficialUrlDetected(unofficialUrl)
                .build();
    }

    public boolean isKnownTrustedIssuer(String issuerName) {
        String normalized = normalizeVietnamese(issuerName);
        for (String keyword : KNOWN_TRUSTED_ISSUER_KEYWORDS) {
            if (keyword.length() <= 4) {
                // Tránh khớp nhầm chuỗi ngắn (vd "sca" trong "landscape"): kiểm tra biên từ
                if (Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b").matcher(normalized).find()) {
                    return true;
                }
            } else if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public String matchOfficialDomain(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            String candidate = rawUrl.trim();
            if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
                candidate = "https://" + candidate;
            }
            URI uri = URI.create(candidate);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            for (String domain : OFFICIAL_VERIFICATION_DOMAINS) {
                if (lowerHost.equals(domain) || lowerHost.endsWith("." + domain)) {
                    return domain;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasSpecificUrlPath(String rawUrl) {
        try {
            String candidate = rawUrl.startsWith("http") ? rawUrl : "https://" + rawUrl;
            URI uri = URI.create(candidate);
            String path = uri.getPath();
            return path != null && path.length() > 3 && !"/".equals(path.trim());
        } catch (Exception e) {
            return false;
        }
    }

    static String normalizeVietnamese(String input) {
        if (input == null) {
            return "";
        }
        String nfd = Normalizer.normalize(input.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return DIACRITICS.matcher(nfd).replaceAll("").replace('đ', 'd');
    }
}
