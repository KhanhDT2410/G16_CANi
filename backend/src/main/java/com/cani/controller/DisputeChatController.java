package com.cani.controller;

import com.cani.dto.dispute.DisputeChatMessageDto;
import com.cani.dto.dispute.DisputeDualChatDto;
import com.cani.dto.dispute.DisputeUnreadSummaryDto;
import com.cani.dto.dispute.SendDisputeChatMessageDto;
import com.cani.dto.response.ApiResponse;
import com.cani.security.SecurityUtils;
import com.cani.service.DisputeChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller cho hội thoại phân xử tranh chấp (Dispute Dual Chat).
 * Cho phép Admin/CSKH trao đổi đa bên (với cả Worker & EO).
 * Worker và EO trao đổi độc lập với Trọng tài.
 */
@Slf4j
@RestController
@RequestMapping({ "/api/v1/disputes", "/v1/disputes" })
@RequiredArgsConstructor
public class DisputeChatController {

    private final DisputeChatService disputeChatService;

    /**
     * Thống kê tổng số tranh chấp có tin nhắn mới chưa đọc cho Slidebar/Menu.
     * GET /api/v1/disputes/unread-summary
     */
    @GetMapping("/unread-summary")
    public ResponseEntity<ApiResponse<DisputeUnreadSummaryDto>> getUnreadSummary(
            Authentication authentication) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        DisputeUnreadSummaryDto response = disputeChatService.getUnreadSummary(userId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy thống kê tin nhắn chưa đọc thành công"));
    }

    /**
     * Lấy dữ liệu hội thoại tranh chấp theo vai trò người gọi.
     * GET /api/v1/disputes/{disputeId}/chat
     */
    @GetMapping("/{disputeId}/chat")
    public ResponseEntity<ApiResponse<DisputeDualChatDto>> getDisputeChat(
            @PathVariable("disputeId") UUID disputeId,
            Authentication authentication) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        DisputeDualChatDto response = disputeChatService.getDisputeChat(disputeId, userId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy thông tin hội thoại tranh chấp thành công"));
    }

    /**
     * Gửi tin nhắn trong phiên tranh chấp.
     * POST /api/v1/disputes/{disputeId}/chat/messages
     */
    @PostMapping("/{disputeId}/chat/messages")
    public ResponseEntity<ApiResponse<DisputeChatMessageDto>> sendMessage(
            @PathVariable("disputeId") UUID disputeId,
            @Valid @RequestBody SendDisputeChatMessageDto request,
            Authentication authentication) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        DisputeChatMessageDto response = disputeChatService.sendMessage(disputeId, userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Gửi tin nhắn thành công"));
    }

    /**
     * Đánh dấu đã đọc tin nhắn trong phiên tranh chấp (theo bên chỉ định hoặc toàn bộ).
     * POST /api/v1/disputes/{disputeId}/chat/read?party=WORKER|EO
     */
    @PostMapping("/{disputeId}/chat/read")
    public ResponseEntity<ApiResponse<Void>> markChatRead(
            @PathVariable("disputeId") UUID disputeId,
            @RequestParam(name = "party", required = false) String party,
            Authentication authentication) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        disputeChatService.markChatRead(disputeId, userId, party);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã đánh dấu đọc tin nhắn"));
    }

    /**
     * Tải lên tài liệu / hình ảnh đính kèm trong phiên tranh chấp.
     * POST /api/v1/disputes/{disputeId}/chat/upload
     */
    @PostMapping(value = "/{disputeId}/chat/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> uploadAttachment(
            @PathVariable("disputeId") UUID disputeId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String url = disputeChatService.uploadAttachment(disputeId, file);
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("url", url), "Tải tệp đính kèm thành công"));
    }
}

