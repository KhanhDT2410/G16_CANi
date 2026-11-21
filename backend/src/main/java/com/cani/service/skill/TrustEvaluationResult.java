package com.cani.service.skill;

import com.cani.dto.skill.TrustScoreBreakdownDto;
import com.cani.entity.Skill;
import com.cani.entity.enums.DocumentAuthenticityStatus;
import com.cani.entity.enums.IdentityVerificationStatus;
import com.cani.entity.enums.IssuerVerificationStatus;
import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillVerificationStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

/**
 * Kết quả quyết định cuối cùng từ Backend Skill Trust & Verification Engine (V2).
 */
@Value
@Builder
public class TrustEvaluationResult {
    double trustScore;
    TrustScoreBreakdownDto breakdown;
    IdentityVerificationStatus identityStatus;
    IssuerVerificationStatus issuerStatus;
    DocumentAuthenticityStatus authenticityStatus;
    String verificationSource;
    String verificationReference;
    RoutingDecision finalRoutingDecision;
    SkillVerificationStatus finalStatus;
    String finalReason;
    List<Skill> matchedApprovedSkills;
    double sanitizedConfidence;
    LocalDate parsedExpiryDate;
}
