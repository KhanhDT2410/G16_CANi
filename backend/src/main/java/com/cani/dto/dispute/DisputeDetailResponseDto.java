package com.cani.dto.dispute;

import com.cani.entity.enums.DisputeEvidenceType;
import com.cani.entity.enums.DisputeOutcome;
import com.cani.entity.enums.DisputeStatus;
import com.cani.entity.enums.DisputeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiết một tranh chấp (SRS UC-97/104) — trả về cho hai bên liên quan, CC và Admin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeDetailResponseDto {

    private UUID id;
    private UUID assignmentId;
    private UUID complainantId;
    private UUID respondentId;
    private DisputeType type;
    private String title;
    private String description;
    private DisputeStatus status;
    private DisputeOutcome outcome;
    private Instant slaDeadline;
    private String resolution;
    private String decisionRationale;
    private String escalationReason;
    private Instant escalatedAt;
    private String finalDecisionRationale;
    private Instant finalDecidedAt;
    private Boolean rsPenaltyApplied;
    private Instant createdAt;
    private Instant updatedAt;

    private List<EvidenceDto> evidences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceDto {
        private UUID id;
        private UUID party;
        private DisputeEvidenceType fileType;
        private String fileUrl;
        private String label;
        private Instant createdAt;
    }
}
