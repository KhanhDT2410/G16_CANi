package com.cani.dto.dispute;

import com.cani.entity.enums.DisputeStatus;
import com.cani.entity.enums.DisputeType;
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
public class DisputeResponseDto {

    private UUID disputeId;
    private UUID assignmentId;
    private String eventName;
    private String jobType;
    private UUID complainantId;
    private String complainantName;
    private UUID respondentId;
    private String respondentName;
    private DisputeType type;
    private String title;
    private String description;
    private String evidence;
    private DisputeStatus status;
    private String resolution;
    private Instant resolvedAt;
    private Instant createdAt;
}

