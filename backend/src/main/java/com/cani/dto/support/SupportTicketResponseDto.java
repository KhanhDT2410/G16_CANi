package com.cani.dto.support;

import com.cani.entity.enums.TicketCategory;
import com.cani.entity.enums.TicketPriority;
import com.cani.entity.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO chi tiết ticket hỗ trợ kèm danh sách thread trao đổi (UC-106 / SCR-26).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketResponseDto {

    private UUID id;
    private String referenceNo;
    private UUID userId;
    private String userName;
    private String userPhone;
    private String userEmail;
    private String userAvatar;
    private TicketCategory category;
    private TicketPriority priority;
    private String subject;
    private String description;
    private List<String> attachments;
    private TicketStatus status;
    private UUID assignedTo;
    private String assignedName;
    private String assignedRole;
    private Instant slaDeadline;
    private UUID resolvedBy;
    private Instant closedAt;
    private Boolean slaAtRisk;
    private Boolean canReply;
    private Instant createdAt;
    private Instant updatedAt;

    private List<MessageDto> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageDto {
        private UUID id;
        private UUID ticketId;
        private UUID senderId;
        private String body;
        private Boolean isInternal;
        private String attachmentUrl;
        private Instant createdAt;
    }
}

