package com.cani.dto.skill;

import com.cani.entity.enums.SkillTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard Tier configuration based on BR-64 Single Source of Truth
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierConfigDto {
    private SkillTier tier;
    private int minSp;
    private Integer nextTierMinSp;
}
