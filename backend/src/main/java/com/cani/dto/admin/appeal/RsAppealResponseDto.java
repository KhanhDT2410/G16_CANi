package com.cani.dto.admin.appeal;

import com.cani.entity.enums.RsAppealStatus;
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
public class RsAppealResponseDto {
    private UUID id;
    private UUID workerId;
    private String workerName;
    private String workerEmail;
    private Integer currentReliabilityScore;

    private UUID reliabilityLogId;
    private Integer penaltyAmount;
    private Instant deductedAt;
    private String deductionReason;

    private String reasonCode;
    private String reasonDetail;
    private String evidence;
    private RsAppealStatus status;

    private UUID reviewerId;
    private String reviewerName;
    private String decisionNote;
    private Instant decidedAt;

    private Instant createdAt;
    private Instant updatedAt;
}

