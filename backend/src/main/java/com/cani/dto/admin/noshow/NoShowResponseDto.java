package com.cani.dto.admin.noshow;

import com.cani.entity.enums.NoShowDetectedBy;
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
public class NoShowResponseDto {

    private UUID id;
    private UUID assignmentId;
    private UUID workerId;
    private UUID workerUserId;
    private String workerName;
    private String workerEmail;
    private String workerPhone;
    private String workerAvatar;

    private UUID shiftId;
    private String shiftRole;
    private UUID eventId;
    private String eventName;
    private Instant shiftStartTime;
    private Instant shiftEndTime;

    private NoShowDetectedBy detectedBy;
    private Instant detectedAt;

    private Integer penaltyAmount;
    private Integer reliabilityScoreBefore;
    private Integer reliabilityScoreAfter;

    private Boolean isAccountLocked;
    private Boolean emergencyDispatched;
    private String notes;

    private UUID appealId;
    private String appealStatus;

    private Instant createdAt;
}

