package com.cani.dto.wage;

import com.cani.entity.enums.JobTier;
import com.cani.entity.enums.WageRegionCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WageMatrixResponseDto {

    private UUID matrixId;
    private WageRegionCode regionCode;
    private String regionName;
    private BigDecimal legalFloorHourly;
    private BigDecimal marketMultiplier;
    private JobTier jobTier;
    private String jobTierLabel;
    private String tierLabel;
    private String description;
    private BigDecimal minMultiplier;
    private BigDecimal recommendedMultiplier;
    private BigDecimal maxMultiplier;
    private BigDecimal overrideBaseWage;
    private BigDecimal computedMinWage;
    private BigDecimal computedSuggestedWage;
    private BigDecimal computedMaxWage;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
