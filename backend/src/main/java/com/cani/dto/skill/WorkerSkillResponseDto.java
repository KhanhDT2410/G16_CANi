package com.cani.dto.skill;

import com.cani.entity.enums.ExperienceLevel;
import com.cani.entity.enums.VerificationTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Detailed representation of a Worker's declared or verified skill (BR-16, BR-17)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerSkillResponseDto {
    private UUID id;
    private UUID skillId;
    private String skillName;
    private String category;
    private ExperienceLevel proficiency;
    private VerificationTier verificationTier;
    private Integer spVerifyAwarded;
    private Instant verifiedAt;
    private Instant expiresAt;
    private Boolean isExpired;
}
