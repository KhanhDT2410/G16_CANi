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
public class ChatConversationSummaryDto {
    private UUID id;
    private String type; // 'DIRECT' | 'SHIFT'
    private UUID shiftId;
    private String shiftCode;
    private String shiftName;
    private UUID targetUserId;
    private String targetUserName;
    private String targetUserAvatar;
    private String targetUserRole;
    private String targetUserPhone;
    private String lastMessagePreview;
    private Instant lastMessageAt;
    private Integer unreadCount;
}
