package com.cani.controller;

import com.cani.dto.chat.ChatMessageResponseDto;
import com.cani.dto.chat.SendChatMessageRequestDto;
import com.cani.dto.chat.StaffChatUnreadSummaryDto;
import com.cani.dto.chat.SupportConversationDetailDto;
import com.cani.dto.chat.SupportConversationSummaryDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.service.SupportChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller Chat trực tuyến dành cho Customer Care & Admin.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/support/chat")
@RequiredArgsConstructor
public class AdminSupportChatController {

    private final SupportChatService supportChatService;

    /**
     * Lấy tóm tắt số tin nhắn chưa đọc và danh sách người gửi gần nhất cho icon Header & Sidebar.
     * GET /api/v1/admin/support/chat/unread-summary
     */
    @GetMapping("/unread-summary")
    public ResponseEntity<ApiResponse<StaffChatUnreadSummaryDto>> getUnreadSummary() {
        StaffChatUnreadSummaryDto dto = supportChatService.getStaffUnreadSummary();
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy tóm tắt tin nhắn chưa đọc thành công"));
    }

    /**
     * CSKH lấy danh sách hội thoại của người dùng (hỗ trợ tìm kiếm theo tên, SĐT, email).
     * GET /api/v1/admin/support/chat/conversations
     */
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<SupportConversationSummaryDto>>> getConversations(
            @RequestParam(value = "q", required = false) String query
    ) {
        List<SupportConversationSummaryDto> list = supportChatService.getStaffConversations(query);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách hội thoại thành công"));
    }

    /**
     * CSKH bắt đầu / mở phiên chat trực tiếp với một Người dùng (EO / Worker) theo userId.
     * POST /api/v1/admin/support/chat/initiate/{userId}
     */
    @PostMapping("/initiate/{userId}")
    public ResponseEntity<ApiResponse<SupportConversationDetailDto>> initiateConversation(
            @PathVariable("userId") UUID userId,
            Authentication authentication
    ) {
        UUID staffId = currentUserId(authentication);
        SupportConversationDetailDto dto = supportChatService.getOrCreateStaffConversationForUser(userId, staffId);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Mở phiên trò chuyện trực tiếp thành công"));
    }

    /**
     * CSKH mở chi tiết hội thoại để đọc tin nhắn và đánh dấu đã đọc.
     * GET /api/v1/admin/support/chat/conversations/{id}
     */
    @GetMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<SupportConversationDetailDto>> getConversationDetail(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        UUID staffId = currentUserId(authentication);
        SupportConversationDetailDto dto = supportChatService.getStaffConversationDetail(id, staffId);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy chi tiết tin nhắn thành công"));
    }

    /**
     * CSKH phản hồi tin nhắn cho Người dùng.
     * POST /api/v1/admin/support/chat/conversations/{id}/reply
     */
    @PostMapping("/conversations/{id}/reply")
    public ResponseEntity<ApiResponse<ChatMessageResponseDto>> replyMessage(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SendChatMessageRequestDto request,
            Authentication authentication
    ) {
        UUID staffId = currentUserId(authentication);
        ChatMessageResponseDto dto = supportChatService.sendStaffMessage(staffId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Phản hồi tin nhắn thành công"));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User u)) {
            throw new com.cani.exception.AppException(
                    "Phiên đăng nhập không hợp lệ hoặc đã hết hạn",
                    org.springframework.http.HttpStatus.UNAUTHORIZED
            );
        }
        return u.getUserId();
    }
}
