package com.cani.dto.event;

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
public class EoWorkerReviewItemDto {
    private UUID assignmentId;
    private UUID eventId;
    private String eventName;
    private UUID shiftId;
    private String jobType;
    private Instant shiftStartTime;
    private Instant shiftEndTime;

    private UUID workerId;
    private UUID userId;
    private String workerName;
    private String workerPhone;
    private String avatarUrl;
    private Integer workerReliabilityScore;
    private BigDecimal workerRatingAvg;
    private String performedSkillName;
    private String confirmedPerformedSkillName;

    private String assignmentStatus;
    private Instant checkinTime;
    private Instant checkoutTime;
    private boolean eoConfirmed;

    private boolean hasReviewedWorker;
    private boolean canReview;
    private String reviewBlockReason;

    private Integer rating;
    private Integer criteriaWorkCompletion;
    private Integer criteriaPunctuality;
    private Integer criteriaProfessionalism;
    private String comment;
    private String[] tags;
    private Instant reviewedAt;
}
