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

import java.util.List;
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
     * Lấy lịch sử tin nhắn trực tiếp trong một Ca làm việc (giữa EO, Worker và CSKH).
     * GET /api/v1/support/chat/shift/{shiftId}
     */
    @GetMapping("/shift/{shiftId}")
    public ResponseEntity<ApiResponse<List<ChatMessageResponseDto>>> getShiftMessages(
            @PathVariable UUID shiftId
    ) {
        List<ChatMessageResponseDto> list = supportChatService.getShiftMessages(shiftId);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách tin nhắn ca làm thành công"));
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

    /**
     * Tải tệp đính kèm / hình ảnh cho phiên chat hỗ trợ.
     * POST /api/v1/support/chat/upload
     */
    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> uploadAttachment(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        String url = supportChatService.uploadChatAttachment(file);
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "tệp đính kèm";
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("url", url, "name", name), "Tải tệp đính kèm thành công"));
    }

    /**
     * Tải / xem tệp đính kèm phiên chat trực tuyến.
     * GET /api/v1/support/chat/attachments/{filename}
     */
    @GetMapping("/attachments/{filename:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getAttachment(
            @PathVariable("filename") String filename
    ) {
        org.springframework.core.io.Resource resource = supportChatService.loadChatAttachment(filename);
        String lower = filename.toLowerCase();
        String contentType = "application/octet-stream";
        if (lower.endsWith(".png")) contentType = "image/png";
        else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) contentType = "image/jpeg";
        else if (lower.endsWith(".webp")) contentType = "image/webp";
        else if (lower.endsWith(".gif")) contentType = "image/gif";
        else if (lower.endsWith(".pdf")) contentType = "application/pdf";

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
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
