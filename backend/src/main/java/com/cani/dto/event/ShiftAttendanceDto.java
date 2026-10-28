package com.cani.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftAttendanceDto {

    private UUID assignmentId;
    private UUID workerId;
    private UUID userId;
    private String workerName;
    private String workerPhone;
    private String avatarUrl;
    private Integer workerReliabilityScore;
    private BigDecimal workerRatingAvg;
    private String assignmentStatus;
    private Instant checkinTime;
    private Instant checkoutTime;
    private Boolean isOnTime;
    private BigDecimal hoursWorked;
    private Boolean eoConfirmed;
    private Instant eoConfirmedAt;
    private Boolean hasReviewedWorker;
    private String checklistProgress;
    private String paymentStatus;
    private BigDecimal agreedWage;
}

