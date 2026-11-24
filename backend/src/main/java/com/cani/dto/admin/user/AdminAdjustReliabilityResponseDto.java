package com.cani.dto.admin.user;

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
public class AdminAdjustReliabilityResponseDto {

    private UUID logId;
    private UUID workerId;
    private UUID userId;
    private Integer scoreBefore;
    private Integer scoreAfter;
    private Integer delta;
    private String reason;
    private String referenceType;
    private UUID referenceId;
    private Instant createdAt;
}
