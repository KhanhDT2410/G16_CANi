package com.cani.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

/**
 * 4 Vùng lương tối thiểu theo Nghị định 293/2025/NĐ-CP (hiệu lực từ 01/01/2026).
 */
@Getter
@RequiredArgsConstructor
public enum WageRegionCode {
    REGION_1("Vùng I", new BigDecimal("25500.00")),
    REGION_2("Vùng II", new BigDecimal("22700.00")),
    REGION_3("Vùng III", new BigDecimal("20000.00")),
    REGION_4("Vùng IV", new BigDecimal("17800.00"));

    private final String displayName;
    private final BigDecimal defaultStatutoryFloor;
}
