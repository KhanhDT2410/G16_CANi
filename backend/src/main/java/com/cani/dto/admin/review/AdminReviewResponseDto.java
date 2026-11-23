package com.cani.dto.admin.review;

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
public class AdminReviewResponseDto {
    private UUID reviewId;
    private UUID assignmentId;

    // Shift & Event context
    private UUID shiftId;
    private String shiftTitle;
    private UUID eventId;
    private String eventTitle;

    // Reviewer details
    private UUID reviewerId;
    private String reviewerName;
    private String reviewerEmail;
    private String reviewerRole;

    // Reviewee details
    private UUID revieweeId;
    private String revieweeName;
    private String revieweeEmail;
    private String revieweeRole;

    // Rating & Criteria
    private Integer rating;
    private Integer criteriaWorkCompletion;
    private Integer criteriaPunctuality;
    private Integer criteriaProfessionalism;
    private Integer criteriaVenueRules;
    private Integer criteriaTeamwork;
    private Integer criteriaShiftClarity;
    private Integer criteriaTiming;
    private Integer criteriaWorkEnvironment;
    private Integer criteriaSupport;
    private Integer criteriaPaymentTiming;

    private String comment;
    private String[] tags;

    // Moderation & Audit details
    private Boolean isHidden;
    private String hiddenReason;
    private UUID hiddenByAdminId;
    private String hiddenByAdminName;
    private Instant hiddenAt;

    private Boolean isFlagged;
    private String flagReason;

    private Boolean isAutoClosed;
    private Instant createdAt;
}

