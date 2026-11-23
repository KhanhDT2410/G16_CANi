package com.cani.controller;

import com.cani.dto.directchat.ChatConversationDetailDto;
import com.cani.dto.directchat.ChatConversationSummaryDto;
import com.cani.dto.directchat.ChatMessageDto;
import com.cani.dto.directchat.SendChatMessageDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.service.DirectChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller Chat trực tiếp giữa Event Organizer (EO) và Nhân sự (Worker).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class DirectChatController {

    private final DirectChatService directChatService;

    /**
     * Lấy nhanh số lượng cuộc trò chuyện và tin nhắn chưa đọc của tôi.
     * GET /api/v1/chat/unread-count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        Map<String, Long> counts = directChatService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.ok(counts, "Lấy số lượng tin nhắn chưa đọc thành công"));
    }

    /**
     * Lấy danh sách các cuộc trò chuyện của tôi.
     * GET /api/v1/chat/conversations
     */
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ChatConversationSummaryDto>>> getMyConversations(
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        List<ChatConversationSummaryDto> list = directChatService.getMyConversations(userId);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách hội thoại thành công"));
    }

    /**
     * Mở hoặc tạo cuộc trò chuyện 1-1 với một người dùng (EO - Worker).
     * POST /api/v1/chat/direct/{targetUserId}
     */
    @PostMapping("/direct/{targetUserId}")
    public ResponseEntity<ApiResponse<ChatConversationDetailDto>> openDirectConversation(
            @PathVariable("targetUserId") UUID targetUserId,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        ChatConversationDetailDto dto = directChatService.getOrCreateDirectConversation(userId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Mở phòng chat 1-1 thành công"));
    }

    /**
     * Mở hoặc tạo cuộc trò chuyện theo Ca làm việc.
     * POST /api/v1/chat/shift/{shiftId}
     */
    @PostMapping("/shift/{shiftId}")
    public ResponseEntity<ApiResponse<ChatConversationDetailDto>> openShiftConversation(
            @PathVariable("shiftId") UUID shiftId,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        ChatConversationDetailDto dto = directChatService.getOrCreateShiftConversation(userId, shiftId);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Mở phòng chat ca làm việc thành công"));
    }

    /**
     * Lấy chi tiết tin nhắn trong một cuộc trò chuyện và đánh dấu đã đọc.
     * GET /api/v1/chat/conversations/{id}
     */
    @GetMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<ChatConversationDetailDto>> getConversationDetail(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        ChatConversationDetailDto dto = directChatService.getConversationDetail(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy chi tiết tin nhắn thành công"));
    }

    /**
     * Gửi tin nhắn vào cuộc trò chuyện.
     * POST /api/v1/chat/conversations/{id}/messages
     */
    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<ChatMessageDto>> sendMessage(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SendChatMessageDto request,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        ChatMessageDto dto = directChatService.sendMessage(userId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Gửi tin nhắn thành công"));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User u)) {
            return null;
        }
        return u.getUserId();
    }
}
