package com.cani.dto.wage;

import com.cani.entity.enums.WageRegionCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WageRegionDto {
    private WageRegionCode code;
    private String name;
    private BigDecimal legalFloorHourly;
    private BigDecimal marketMultiplier;
    private String description;
    private Boolean isActive;
    private Instant updatedAt;
}
