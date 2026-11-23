package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.dto.support.CreateTicketRequestDto;
import com.cani.dto.support.ReplyTicketRequestDto;
import com.cani.dto.support.SupportTicketResponseDto;
import com.cani.entity.User;
import com.cani.service.SupportTicketService;
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
 * Controller Support Ticket — endpoint cho người dùng (Worker / Event Organizer).
 * SRS UC-105 (nộp ticket), UC-106 (danh sách, chi tiết, reply, đóng ticket).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/support/tickets")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    /**
     * UC-105: Nộp ticket hỗ trợ.
     * POST /api/v1/support/tickets
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SupportTicketResponseDto>> createTicket(
            @Valid @RequestBody CreateTicketRequestDto request,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        SupportTicketResponseDto response = supportTicketService.createTicket(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Ticket hỗ trợ đã được tạo thành công"));
    }

    /**
     * UC-106: Danh sách ticket của tôi.
     * GET /api/v1/support/tickets/my
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<SupportTicketResponseDto>>> getMyTickets(
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        List<SupportTicketResponseDto> response = supportTicketService.getMyTickets(userId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách ticket thành công"));
    }

    /**
     * UC-106: Xem chi tiết ticket (kèm thread).
     * GET /api/v1/support/tickets/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupportTicketResponseDto>> getTicketDetail(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        SupportTicketResponseDto response = supportTicketService.getTicketDetail(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy chi tiết ticket thành công"));
    }

    /**
     * UC-106: Phản hồi ticket.
     * POST /api/v1/support/tickets/{id}/reply
     */
    @PostMapping("/{id}/reply")
    public ResponseEntity<ApiResponse<SupportTicketResponseDto>> reply(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReplyTicketRequestDto request,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        SupportTicketResponseDto response = supportTicketService.reply(id, userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Phản hồi ticket thành công"));
    }

    /**
     * UC-106: Người dùng đánh dấu đã giải quyết (đóng ticket).
     * POST /api/v1/support/tickets/{id}/resolve
     */
    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<SupportTicketResponseDto>> markResolved(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        SupportTicketResponseDto response = supportTicketService.markResolved(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã đóng ticket thành công"));
    }

    /**
     * Tải tệp đính kèm / hình ảnh cho ticket hỗ trợ.
     * POST /api/v1/support/tickets/upload
     */
    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadAttachment(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        String url = supportTicketService.uploadAttachment(file);
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "tệp đính kèm";
        long size = file.getSize();
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("url", url, "name", name, "size", size),
                "Tải tệp đính kèm thành công"
        ));
    }

    /**
     * Tải / xem tệp đính kèm ticket hỗ trợ.
     * GET /api/v1/support/tickets/attachments/{filename}
     */
    @GetMapping("/attachments/{filename:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getAttachment(
            @PathVariable("filename") String filename
    ) {
        org.springframework.core.io.Resource resource = supportTicketService.loadAttachment(filename);
        String lower = filename.toLowerCase();
        String contentType = "application/octet-stream";
        if (lower.endsWith(".png")) contentType = "image/png";
        else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) contentType = "image/jpeg";
        else if (lower.endsWith(".webp")) contentType = "image/webp";
        else if (lower.endsWith(".gif")) contentType = "image/gif";
        else if (lower.endsWith(".pdf")) contentType = "application/pdf";
        else if (lower.endsWith(".txt")) contentType = "text/plain; charset=UTF-8";

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User u)) {
            return null;
        }
        return u.getUserId();
    }
}

