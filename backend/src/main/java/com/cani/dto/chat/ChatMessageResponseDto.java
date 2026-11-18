package com.cani.dto.chat;

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
public class ChatMessageResponseDto {
    private UUID id;
    private UUID conversationId;
    private UUID senderId;
    private String senderRole;
    private String senderName;
    private String messageText;
    private String attachmentUrl;
    private UUID shiftId;
    private String shiftCode;
    private String shiftName;
    private Boolean isRead;
    private Instant createdAt;
}
