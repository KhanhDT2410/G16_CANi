package com.cani.dto.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Chi tiết minh bạch cách tính Trust Score (0-100) do Backend thực hiện độc lập với AI Confidence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustScoreBreakdownDto {
    private int identityScore;
    private int documentAuthenticityScore;
    private int skillRelevanceScore;
    private int issuerVerificationScore;
    private double weightedTrustScore;

    @Builder.Default
    private List<String> hardGatesTriggered = new ArrayList<>();

    @Builder.Default
    private List<String> riskFlags = new ArrayList<>();

    @Builder.Default
    private List<String> missingInformation = new ArrayList<>();

    private String explanation;
}
