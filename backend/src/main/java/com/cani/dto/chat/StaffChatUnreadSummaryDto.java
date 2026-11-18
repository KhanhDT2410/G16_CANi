package com.cani.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO tóm tắt tin nhắn chưa đọc & hội thoại mới nhất dành cho Header / Sidebar của Admin & CSKH.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffChatUnreadSummaryDto {
    private Integer totalUnreadCount;
    private Integer unreadConversationCount;
    private List<SupportConversationSummaryDto> recentConversations;
}
