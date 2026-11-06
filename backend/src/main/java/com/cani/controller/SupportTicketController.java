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

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User u)) {
            return null;
        }
        return u.getUserId();
    }
}

