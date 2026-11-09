package com.cani.dto.support;

import com.cani.entity.enums.TicketCategory;
import com.cani.entity.enums.TicketPriority;
import com.cani.entity.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Item trong hàng đợi ticket cho Customer Care (SCR-25).
 * Sắp xếp theo nguy cơ vi phạm SLA (slaAtRisk) -> tin nhắn cũ nhất (lastMessageAt / createdAt).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketQueueItemDto {

    private UUID id;
    private String referenceNo;
    private TicketCategory category;
    private TicketPriority priority;
    private String subject;
    private TicketStatus status;
    private UUID userId;
    private UUID assignedTo;
    private Instant slaDeadline;
    private Instant lastMessageAt;
    private Boolean slaAtRisk;
    private Instant createdAt;
}

