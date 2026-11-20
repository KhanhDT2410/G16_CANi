package com.cani.dto.directchat;

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
public class ChatMessageDto {
    private UUID id;
    private UUID conversationId;
    private UUID senderId;
    private String senderName;
    private String senderRole;
    private String senderAvatar;
    private String messageText;
    private String attachmentUrl;
    private Instant createdAt;
    private Boolean isMe;
}
