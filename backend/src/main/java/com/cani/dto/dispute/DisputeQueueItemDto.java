package com.cani.dto.dispute;

import com.cani.entity.enums.DisputeStatus;
import com.cani.entity.enums.DisputeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Hàng đợi tranh chấp cho Customer Care (SRS UC-109, BR-57:
 * thứ tự ưu tiên = (1) đã escalate, (2) rủi ro SLA < 1 ngày, (3) nộp cũ nhất).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeQueueItemDto {

    private UUID id;
    private DisputeType type;
    private String title;
    private DisputeStatus status;
    private Instant slaDeadline;
    private Instant createdAt;
    private UUID complainantId;
    private UUID respondentId;
    private UUID assignedTo;

    /**
     * true nếu đơn đã bị escalate lên Admin (ưu tiên cao nhất, BR-57).
     */
    private boolean escalated;

    /**
     * true nếu còn dưới 24h đến hạn SLA (rủi ro, BR-57).
     */
    private boolean slaAtRisk;
}
