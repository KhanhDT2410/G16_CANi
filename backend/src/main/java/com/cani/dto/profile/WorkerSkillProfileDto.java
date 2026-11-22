package com.cani.dto.profile;

import com.cani.entity.enums.ExperienceLevel;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.VerificationTier;
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
public class WorkerSkillProfileDto {
    private UUID skillId;
    private String skillName;
    private String category;
    private ExperienceLevel proficiency;
    private VerificationTier verificationTier;
    private SkillTier skillTier;
    private Integer totalSp;
    private Integer totalShiftsCompleted;
    private BigDecimal avgRating;
    private Integer ratingCount;
    private Boolean hasValidCertificate;
    private Instant certificateExpiresAt;
}
