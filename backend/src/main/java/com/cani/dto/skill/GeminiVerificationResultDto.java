package com.cani.dto.skill;

import com.cani.entity.enums.RoutingDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiVerificationResultDto {
    private String recipientName;
    private String identityNumber;
    private String institution;
    private String issueDate;

    @Builder.Default
    private List<ExtractedSkillDto> extractedSkills = new ArrayList<>();

    @Builder.Default
    private Boolean hasSealOrSignature = false;

    @Builder.Default
    private Double overallConfidence = 0.0;

    private RoutingDecision routingDecision;
    private String routingReason;
}
