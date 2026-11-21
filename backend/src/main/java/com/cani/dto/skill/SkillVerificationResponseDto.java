package com.cani.dto.skill;

import com.cani.entity.enums.DocumentAuthenticityStatus;
import com.cani.entity.enums.IdentityVerificationStatus;
import com.cani.entity.enums.IssuerVerificationStatus;
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
    private String workerName;
    private String documentUrl;
    private UUID skillId;
    private String skillName;
    private String jobType;
    private String recipientName;
    private String identityNumber;
    private String institution;
    private String certificateNumber;
    private String issueDate;
    private String expiryDate;
    private List<ExtractedSkillDto> extractedSkills;
    private Boolean hasSealOrSignature;
    private Double overallConfidence;
    private Double trustScore;
    private TrustScoreBreakdownDto trustScoreBreakdown;
    private IdentityVerificationStatus identityVerificationStatus;
    private IssuerVerificationStatus issuerVerificationStatus;
    private DocumentAuthenticityStatus documentAuthenticityStatus;
    private String verificationSource;
    private String verificationReference;
    private String modelName;
    private String promptVersion;
    private Boolean humanReviewed;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String reviewNotes;
    private String failureReason;
    private Integer retryCount;
    private RoutingDecision routingDecision;
    private String routingReason;
    private SkillVerificationStatus status;
    private Integer spAwarded;
    private Integer currentTotalSp;
    private SkillTier currentTier;
    private Instant createdAt;
}
