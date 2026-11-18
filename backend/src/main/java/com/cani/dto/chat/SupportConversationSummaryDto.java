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
public class SupportConversationSummaryDto {
    private UUID id;
    private UUID userId;
    private String userName;
    private String userPhone;
    private String userEmail;
    private String userRole;
    private String userAvatar;
    private String lastMessagePreview;
    private Instant lastMessageAt;
    private Integer unreadStaffCount;
    private Integer unreadUserCount;
    private String status;

    // Ngữ cảnh ca làm việc gắn kèm (nếu người dùng đang trong ca làm việc hoặc có tin nhắn gần nhất từ ca)
    private UUID currentShiftId;
    private String currentShiftCode;
    private String currentShiftName;
}
