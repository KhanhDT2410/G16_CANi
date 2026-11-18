package com.cani.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO tóm tắt trạng thái tin nhắn chưa đọc cho thanh thông báo Header & Sidebar của Admin / CSKH.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffChatUnreadSummaryDto {
    private Integer totalUnreadCount;
    private Integer unreadConversationsCount;
    private List<SupportConversationSummaryDto> recentConversations;
}
