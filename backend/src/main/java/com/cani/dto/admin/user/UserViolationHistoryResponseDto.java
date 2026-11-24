package com.cani.dto.admin.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserViolationHistoryResponseDto {

    private UUID userId;
    private Integer days;
    private Long totalViolations;
    private Long noShowCount;
    private Long disputeCount;
    private Long adminAdjustCount;
    private List<ReliabilityViolationItemDto> reliabilityLogs;
    private List<AuditViolationItemDto> auditLogs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReliabilityViolationItemDto {
        private UUID logId;
        private Integer changeAmount;
        private String reason;
        private String referenceType;
        private UUID referenceId;
        private Integer scoreBefore;
        private Integer scoreAfter;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditViolationItemDto {
        private UUID auditId;
        private String action;
        private String entityType;
        private UUID entityId;
        private String details;
        private Instant createdAt;
    }
}
