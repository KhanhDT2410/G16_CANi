package com.cani.dto.skill;

import com.cani.entity.enums.SkillTier;
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
public class WorkerSkillPointDto {
    private UUID id;
    private String jobType;
    private Integer spVerify;
    private Integer spExperience;
    private Integer spRating;
    private Integer totalSp;
    private SkillTier tier;
    private Integer totalShiftsCompleted;
    private BigDecimal avgRating;
    private Instant updatedAt;
}
