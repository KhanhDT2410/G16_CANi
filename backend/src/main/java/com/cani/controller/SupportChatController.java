package com.cani.controller;

import com.cani.dto.chat.ChatMessageResponseDto;
import com.cani.dto.chat.SendChatMessageRequestDto;
import com.cani.dto.chat.SupportConversationDetailDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.service.SupportChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller Chat trực tuyến dành cho Người dùng (Worker / Event Organizer).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/support/chat")
@RequiredArgsConstructor
public class SupportChatController {

    private final SupportChatService supportChatService;

    /**
     * Lấy phiên chat và lịch sử tin nhắn của Người dùng hiện tại.
     * GET /api/v1/support/chat/my
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<SupportConversationDetailDto>> getMyConversation(
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        SupportConversationDetailDto dto = supportChatService.getMyConversation(userId);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy thông tin hội thoại thành công"));
    }

    /**
     * Người dùng gửi tin nhắn hỗ trợ.
     * POST /api/v1/support/chat/send
     */
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<ChatMessageResponseDto>> sendMessage(
            @Valid @RequestBody SendChatMessageRequestDto request,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        ChatMessageResponseDto dto = supportChatService.sendUserMessage(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Gửi tin nhắn thành công"));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User u)) {
            return null;
        }
        return u.getUserId();
    }
}
