package com.cani.service;

import com.cani.entity.enums.JobTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobTierClassifierTest {

    private final JobTierClassifier classifier = new JobTierClassifier();

    @Test
    @DisplayName("Normal - Phân loại chính xác các vị trí chuẩn CANi vào 6 JobTier theo Bảng A")
    void classify_MapsCanonicalRolesAccurately() {
        assertEquals(JobTier.TIER_2_SERVICE, classifier.classify("Phục vụ bàn", null));
        assertEquals(JobTier.TIER_2_SERVICE, classifier.classify("Lễ tân / Check-in", null));
        assertEquals(JobTier.TIER_3_PHYSICAL, classifier.classify("Bảo vệ / An ninh", null));
        assertEquals(JobTier.TIER_3_PHYSICAL, classifier.classify("Hậu cần / Setup sự kiện", null));
        assertEquals(JobTier.TIER_4_PROMOTION, classifier.classify("Pha chế", null));
        assertEquals(JobTier.TIER_4_PROMOTION, classifier.classify("Pha chế (Bartender/Barista)", null));
        assertEquals(JobTier.TIER_4_PROMOTION, classifier.classify("PG / PB", null));
        assertEquals(JobTier.TIER_5_TECHNICAL, classifier.classify("Điều phối sự kiện", null));
        assertEquals(JobTier.TIER_5_TECHNICAL, classifier.classify("Kỹ thuật âm thanh - ánh sáng", null));
        assertTrue(classifier.isCanonicalRole("Pha chế"));
    }

    @Test
    @DisplayName("Normal - Tự động nhận diện vị trí nhập tự do ngoài 10 vị trí chuẩn bằng từ khóa")
    void classify_MapsCustomFreeTextRolesByKeywords() {
        assertEquals(JobTier.TIER_6_SPECIALIST, classifier.classify("MC song ngữ Anh - Việt", null));
        assertEquals(JobTier.TIER_5_TECHNICAL, classifier.classify("Thợ chụp ảnh & quay phim sự kiện", null));
        assertEquals(JobTier.TIER_4_PROMOTION, classifier.classify("Nhân viên sampling bán hàng", null));
        assertEquals(JobTier.TIER_3_PHYSICAL, classifier.classify("Nhân viên bốc xếp sân khấu", null));
        assertEquals(JobTier.TIER_2_SERVICE, classifier.classify("Nhân viên chạy bàn tiệc cưới", null));
        assertEquals(JobTier.TIER_1_GENERAL, classifier.classify("Phát tờ rơi khu vực cổng", null));
        assertFalse(classifier.isCanonicalRole("MC song ngữ Anh - Việt"));
    }

    @Test
    @DisplayName("Boundary - Ưu tiên explicitTier khi EO tự chọn lại nhóm công việc và fallback TIER_1_GENERAL khi chuỗi rỗng")
    void classify_PrefersExplicitTierAndHandlesBlank() {
        assertEquals(JobTier.TIER_6_SPECIALIST, classifier.classify("PG / PB", JobTier.TIER_6_SPECIALIST));
        assertEquals(JobTier.TIER_1_GENERAL, classifier.classify("   ", null));
        assertEquals(JobTier.TIER_1_GENERAL, classifier.classify(null, null));
    }
}
