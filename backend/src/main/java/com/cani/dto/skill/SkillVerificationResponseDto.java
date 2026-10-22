package com.cani.dto.skill;

import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.SkillVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillVerificationResponseDto {
    private UUID id;
    private UUID workerId;
    private UUID skillId;
    private String skillName;
    private String jobType;
    private String recipientName;
    private String identityNumber;
    private String institution;
    private String issueDate;
    private List<ExtractedSkillDto> extractedSkills;
    private Boolean hasSealOrSignature;
    private Double overallConfidence;
    private RoutingDecision routingDecision;
    private String routingReason;
    private SkillVerificationStatus status;
    private Integer spAwarded;
    private Integer currentTotalSp;
    private SkillTier currentTier;
    private Instant createdAt;
}
