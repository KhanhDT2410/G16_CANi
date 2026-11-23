package com.cani.dto.dispute;

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
public class DisputePartyChatDto {
    private UUID conversationId;
    private String party; // "WORKER" | "EO"
    private UUID userId;
    private String userName;
    private String userPhone;
    private String userAvatar;
    private String status;
    private Integer unreadCount;
    private Instant lastMessageAt;
    private List<DisputeChatMessageDto> messages;
}
