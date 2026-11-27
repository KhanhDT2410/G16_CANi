package com.cani.service;

import com.cani.entity.enums.JobTier;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bộ phân loại vị trí công việc (kể cả vị trí EO nhập tự do ngoài danh mục chuẩn)
 * về một trong 6 Nhóm bậc lương (JobTier) theo Bảng A.
 */
@Component
public class JobTierClassifier {

    private static final Map<String, JobTier> EXACT_CANONICAL_ROLE_MAP = Map.ofEntries(
            Map.entry("phat to roi", JobTier.TIER_1_GENERAL),
            Map.entry("hau can / setup su kien", JobTier.TIER_3_PHYSICAL),
            Map.entry("phuc vu ban", JobTier.TIER_2_SERVICE),
            Map.entry("phu bep", JobTier.TIER_2_SERVICE),
            Map.entry("thu ngan", JobTier.TIER_2_SERVICE),
            Map.entry("le tan / check-in", JobTier.TIER_2_SERVICE),
            Map.entry("bao ve / an ninh", JobTier.TIER_3_PHYSICAL),
            Map.entry("pha che", JobTier.TIER_4_PROMOTION),
            Map.entry("pha che (bartender/barista)", JobTier.TIER_4_PROMOTION),
            Map.entry("pg / pb", JobTier.TIER_4_PROMOTION),
            Map.entry("dieu phoi su kien", JobTier.TIER_5_TECHNICAL),
            Map.entry("ky thuat am thanh - anh sang", JobTier.TIER_5_TECHNICAL)
    );

    private static final List<String> TIER_6_KEYWORDS = List.of(
            "mc", "song ngu", "ngoai ngu", "phien dich", "thong dich", "tieng anh",
            "tieng trung", "tieng han", "tieng nhat", "bilingual", "english",
            "chuyen nghiep", "vip host", "host su kien", "ca si", "ban nhac"
    );

    private static final List<String> TIER_5_KEYWORDS = List.of(
            "dieu phoi", "coordinator", "ky thuat", "am thanh", "anh sang",
            "led", "av", "quay phim", "chup anh", "nhiep anh", "cameraman",
            "photographer", "videographer", "livestream", "truyen thong", "giam sat"
    );

    private static final List<String> TIER_4_KEYWORDS = List.of(
            "pg", "pb", "pha che", "bartender", "barista", "sampling",
            "roadshow", "ban hang", "tu van", "chot don", "sales", "kol", "koc",
            "activation", "gioi thieu san pham"
    );

    private static final List<String> TIER_3_KEYWORDS = List.of(
            "boc xep", "setup", "san khau", "bao ve", "an ninh", "security",
            "van chuyen", "khuon vac", "lap dat", "thao do", "giu xe", "the luc"
    );

    private static final List<String> TIER_2_KEYWORDS = List.of(
            "le tan", "check-in", "checkin", "phuc vu", "thu ngan", "cashier",
            "pos", "phu bep", "huong dan", "tiep tan", "chay ban", "bung be",
            "usher", "soat ve", "cham soc khach", "cskh", "ban ve"
    );

    public JobTier classify(String jobType, JobTier explicitTier) {
        if (explicitTier != null) {
            return explicitTier;
        }
        String normalized = normalizeAscii(jobType);
        if (normalized.isEmpty()) {
            return JobTier.TIER_1_GENERAL;
        }

        JobTier exact = EXACT_CANONICAL_ROLE_MAP.get(normalized);
        if (exact != null) {
            return exact;
        }

        if (containsAnyKeyword(normalized, TIER_6_KEYWORDS)) {
            return JobTier.TIER_6_SPECIALIST;
        }
        if (containsAnyKeyword(normalized, TIER_5_KEYWORDS)) {
            return JobTier.TIER_5_TECHNICAL;
        }
        if (containsAnyKeyword(normalized, TIER_4_KEYWORDS)) {
            return JobTier.TIER_4_PROMOTION;
        }
        if (containsAnyKeyword(normalized, TIER_3_KEYWORDS)) {
            return JobTier.TIER_3_PHYSICAL;
        }
        if (containsAnyKeyword(normalized, TIER_2_KEYWORDS)) {
            return JobTier.TIER_2_SERVICE;
        }
        return JobTier.TIER_1_GENERAL;
    }

    public boolean isCanonicalRole(String jobType) {
        return EXACT_CANONICAL_ROLE_MAP.containsKey(normalizeAscii(jobType));
    }

    private boolean containsAnyKeyword(String normalized, List<String> keywords) {
        for (String kw : keywords) {
            if (kw.length() <= 3 && !kw.contains(" ")) {
                if ((" " + normalized.replaceAll("[^a-z0-9]+", " ") + " ").contains(" " + kw + " ")) {
                    return true;
                }
            } else if (normalized.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeAscii(String input) {
        if (input == null) return "";
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
