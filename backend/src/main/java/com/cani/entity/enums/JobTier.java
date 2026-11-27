package com.cani.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

/**
 * 6 Nhóm công việc sự kiện với hệ số tham chiếu theo Bảng A (dựa trên sàn vùng NĐ 293/2025/NĐ-CP).
 */
@Getter
@RequiredArgsConstructor
public enum JobTier {
    TIER_1_GENERAL(
            "Phổ thông",
            "Phát tờ rơi, hậu cần nhẹ, trực khu vực",
            new BigDecimal("1.00"),
            new BigDecimal("1.15"),
            new BigDecimal("1.30")
    ),
    TIER_2_SERVICE(
            "Giao tiếp & Dịch vụ",
            "Lễ tân, check-in, hướng dẫn khách, phục vụ bàn, thu ngân",
            new BigDecimal("1.30"),
            new BigDecimal("1.55"),
            new BigDecimal("1.80")
    ),
    TIER_3_PHYSICAL(
            "Thể lực / An ninh",
            "Bốc xếp, setup sân khấu, bảo vệ, an ninh",
            new BigDecimal("1.50"),
            new BigDecimal("1.85"),
            new BigDecimal("2.20")
    ),
    TIER_4_PROMOTION(
            "PG / PB bán hàng & Pha chế",
            "PG/PB bán hàng, sampling, roadshow, pha chế (Bartender/Barista)",
            new BigDecimal("2.00"),
            new BigDecimal("2.80"),
            new BigDecimal("4.00")
    ),
    TIER_5_TECHNICAL(
            "Điều phối & Kỹ thuật",
            "Điều phối sự kiện (Coordinator), kỹ thuật âm thanh - ánh sáng",
            new BigDecimal("2.50"),
            new BigDecimal("3.75"),
            new BigDecimal("5.00")
    ),
    TIER_6_SPECIALIST(
            "Chuyên nghiệp / Ngoại ngữ",
            "PG/PB sự kiện chuyên nghiệp, có ngoại ngữ, MC song ngữ",
            new BigDecimal("4.50"),
            new BigDecimal("6.50"),
            new BigDecimal("10.00")
    );

    private final String label;
    private final String defaultDescription;
    private final BigDecimal defaultMinMultiplier;
    private final BigDecimal defaultRecommendedMultiplier;
    private final BigDecimal defaultMaxMultiplier;
}
