package com.cani.service;

import com.cani.entity.enums.City;
import com.cani.entity.enums.WageRegionCode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phân giải địa bàn sự kiện toàn quốc về 4 Vùng lương tối thiểu theo Nghị định 293/2025/NĐ-CP.
 * Là Single Source of Truth cho cả WageRecommendationService và EoEventService.
 */
@Component
public class GeoWageRegionResolver {

    public record ResolvedRegion(WageRegionCode regionCode, String regionLabel, City legacyCity) {}

    private static final List<String> REGION_1_KEYWORDS = List.of(
            "ha noi", "hanoi", "ho chi minh", "tp hcm", "tphcm", "tp.hcm", "sai gon", "saigon",
            "secc", "thu duc", "hoan kiem", "ba dinh", "dong da", "cau giay", "hai ba trung",
            "thanh xuan", "tay ho", "nam tu liem", "bac tu liem", "long bien", "ha dong",
            "hoang mai", "gia lam", "dong anh", "soc son", "hoa lac", "thach that",
            "quan 1", "quan 3", "quan 4", "quan 5", "quan 6", "quan 7", "quan 8", "quan 10",
            "quan 11", "quan 12", "binh thanh", "phu nhuan", "tan binh", "go vap", "tan phu",
            "binh tan", "nha be", "binh chanh", "cu chi", "hoc mon",
            "hai phong", "binh duong", "thu dau mot", "di an", "thuan an", "dong nai", "bien hoa",
            "ba ria", "vung tau", "quang ninh", "ha long", "mong cai", "cam pha"
    );

    private static final List<String> REGION_2_KEYWORDS = List.of(
            "da nang", "danang", "hai chau", "son tra", "thanh khe", "ngu hanh son",
            "lien chieu", "cam le", "hoa vang", "can tho", "thai nguyen", "bac ninh",
            "bac giang", "hai duong", "hung yen", "vinh phuc", "viet tri", "phu tho",
            "ninh binh", "thanh hoa", "vinh", "nghe an", "hue", "thua thien hue",
            "nha trang", "khanh hoa", "da lat", "lam dong", "quy nhon", "binh dinh",
            "buon ma thuot", "dak lak", "long an", "tan an", "tien giang", "my tho",
            "tay ninh", "long xuyen", "an giang", "kien giang", "phu quoc", "rach gia",
            "thai binh", "nam dinh", "quang nam", "tam ky", "hoi an"
    );

    private static final List<String> REGION_3_KEYWORDS = List.of(
            "ha nam", "phu ly", "ninh thuan", "phan rang", "binh thuan", "phan thiet",
            "quang ngai", "phu yen", "tuy hoa", "quang binh", "dong hoi", "quang tri",
            "dong ha", "ha tinh", "lao cai", "sapa", "sa pa", "hoa binh", "tuyen quang",
            "lang son", "yen bai", "gia lai", "pleiku", "kon tum", "binh phuoc",
            "dong xoai", "ben tre", "tra vinh", "vinh long", "dong thap", "cao lanh",
            "sa dec", "bac lieu", "ca mau", "soc trang"
    );

    private static final List<String> REGION_4_KEYWORDS = List.of(
            "ha giang", "cao bang", "bac kan", "dien bien", "lai chau", "son la",
            "dak nong", "gia nghia", "hau giang", "vi thanh"
    );

    public ResolvedRegion resolve(
            WageRegionCode explicitRegion,
            City legacyCity,
            String address,
            Double lat,
            Double lng
    ) {
        if (explicitRegion != null) {
            City mappedCity = explicitRegion == WageRegionCode.REGION_2 ? City.DANANG : City.TP_HCM;
            return new ResolvedRegion(explicitRegion, explicitRegion.getDisplayName(), mappedCity);
        }

        if (lat != null && lng != null && (lat != 0 || lng != 0)) {
            if (lat >= 20.5 && lat <= 21.45 && lng >= 105.2 && lng <= 106.1) {
                return new ResolvedRegion(WageRegionCode.REGION_1, "Vùng I (Hà Nội)", City.HANOI);
            }
            if (lat >= 15.8 && lat <= 16.35 && lng >= 107.8 && lng <= 108.4) {
                return new ResolvedRegion(WageRegionCode.REGION_2, "Vùng II (Đà Nẵng)", City.DANANG);
            }
            if (lat >= 10.3 && lat <= 11.2 && lng >= 106.3 && lng <= 107.15) {
                return new ResolvedRegion(WageRegionCode.REGION_1, "Vùng I (TP. Hồ Chí Minh)", City.TP_HCM);
            }
            if (lat >= 20.7 && lat <= 21.05 && lng >= 106.5 && lng <= 107.0) {
                return new ResolvedRegion(WageRegionCode.REGION_1, "Vùng I (Hải Phòng)", City.HANOI);
            }
            if (lat >= 9.9 && lat <= 10.2 && lng >= 105.5 && lng <= 105.9) {
                return new ResolvedRegion(WageRegionCode.REGION_2, "Vùng II (Cần Thơ)", City.DANANG);
            }
        }

        String norm = JobTierClassifier.normalizeAscii(address);
        if (!norm.isEmpty()) {
            if (norm.contains("da nang") || norm.contains("danang") || containsAny(norm, List.of(
                    "hai chau", "son tra", "thanh khe", "ngu hanh son", "lien chieu", "cam le", "hoa vang"
            ))) {
                return new ResolvedRegion(WageRegionCode.REGION_2, "Vùng II (Đà Nẵng)", City.DANANG);
            }
            if (norm.contains("ha noi") || norm.contains("hanoi") || containsAny(norm, List.of(
                    "ba dinh", "cau giay", "hoan kiem", "dong da", "tay ho", "hai ba trung",
                    "nam tu liem", "bac tu liem", "thanh xuan", "ha dong", "long bien", "hoang mai"
            ))) {
                return new ResolvedRegion(WageRegionCode.REGION_1, "Vùng I (Hà Nội)", City.HANOI);
            }
            if (norm.contains("ho chi minh") || norm.contains("tp hcm") || norm.contains("tphcm")
                    || norm.contains("tp.hcm") || norm.contains("sai gon") || norm.contains("secc")) {
                return new ResolvedRegion(WageRegionCode.REGION_1, "Vùng I (TP. Hồ Chí Minh)", City.TP_HCM);
            }
            if (containsAny(norm, REGION_4_KEYWORDS)) {
                return new ResolvedRegion(WageRegionCode.REGION_4, "Vùng IV", null);
            }
            if (containsAny(norm, REGION_3_KEYWORDS)) {
                return new ResolvedRegion(WageRegionCode.REGION_3, "Vùng III", null);
            }
            if (containsAny(norm, REGION_2_KEYWORDS)) {
                return new ResolvedRegion(WageRegionCode.REGION_2, "Vùng II", City.DANANG);
            }
            if (containsAny(norm, REGION_1_KEYWORDS)) {
                return new ResolvedRegion(WageRegionCode.REGION_1, "Vùng I", City.TP_HCM);
            }
        }

        if (legacyCity == City.DANANG) {
            return new ResolvedRegion(WageRegionCode.REGION_2, "Vùng II (Đà Nẵng)", City.DANANG);
        }
        if (legacyCity == City.HANOI) {
            return new ResolvedRegion(WageRegionCode.REGION_1, "Vùng I (Hà Nội)", City.HANOI);
        }
        return new ResolvedRegion(WageRegionCode.REGION_1, "Vùng I (TP. Hồ Chí Minh)", City.TP_HCM);
    }

    private boolean containsAny(String normalizedAddress, List<String> keywords) {
        String padded = " " + normalizedAddress.replaceAll("[^a-z0-9]+", " ") + " ";
        for (String kw : keywords) {
            String cleanKw = kw.replaceAll("[^a-z0-9]+", " ").trim();
            if (padded.contains(" " + cleanKw + " ")) {
                return true;
            }
        }
        return false;
    }
}
