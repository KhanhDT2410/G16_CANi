package com.cani.dto.event;

import com.cani.entity.enums.SkillRequirementType;
import com.cani.entity.enums.VerificationTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftSkillRequirementDto {

    private UUID skillId;

    private String skillName;

    @Builder.Default
    private SkillRequirementType requirementType = SkillRequirementType.REQUIRED;

    @Builder.Default
    private VerificationTier minVerificationTier = VerificationTier.SELF_DECLARED;

    @Builder.Default
    private Boolean requiresValidCertificate = false;
}
