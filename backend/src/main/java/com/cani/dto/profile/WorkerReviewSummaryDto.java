package com.cani.dto.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerReviewSummaryDto {
    private UUID reviewId;
    private Integer rating;
    private Integer criteriaWorkCompletion;
    private Integer criteriaPunctuality;
    private Integer criteriaProfessionalism;
    private Integer criteriaVenueRules;
    private Integer criteriaTeamwork;
    private String comment;
    private String reviewerName;
    private String companyName;
    private Instant createdAt;
}
