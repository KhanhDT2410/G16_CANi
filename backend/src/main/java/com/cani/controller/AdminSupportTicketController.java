package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.dto.support.*;
import com.cani.entity.User;
import com.cani.service.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller Support Ticket — phía Customer Care & Admin (SRS SCR-25 / SCR-26).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/support/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
public class AdminSupportTicketController {

    private final SupportTicketService supportTicketService;

    /**
     * SCR-25: Hàng đợi ticket cần xử lý (ưu tiên theo SLA risk -> tin nhắn cũ nhất).
     * GET /api/v1/admin/support/tickets/queue
     */
    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<List<SupportTicketQueueItemDto>>> getQueue() {
        List<SupportTicketQueueItemDto> queue = supportTicketService.getQueue();
        return ResponseEntity.ok(ApiResponse.ok(queue, "Lấy hàng đợi ticket thành công"));
    }

    /**
     * SCR-25 (phân tầng CC): Hàng đợi chỉ gồm các phiếu đang được giao cho CC hiện tại xử lý.
     * GET /api/v1/admin/support/tickets/queue/mine
     */
    @GetMapping("/queue/mine")
    public ResponseEntity<ApiResponse<List<SupportTicketQueueItemDto>>> getMyQueue(
            Authentication authentication
    ) {
        UUID actorId = currentUserId(authentication);
        List<SupportTicketQueueItemDto> queue = supportTicketService.getQueueForCc(actorId);
        return ResponseEntity.ok(ApiResponse.ok(queue, "Lấy hàng đợi phiếu hỗ trợ được giao thành công"));
    }

    /**
     * SCR-25 / SCR-26: Danh sách nhân sự Customer Care để chuyển phiếu.
     * GET /api/v1/admin/support/tickets/customer-care-staff
     */
    @GetMapping("/customer-care-staff")
    public ResponseEntity<ApiResponse<List<CustomerCareStaffDto>>> getCustomerCareStaff() {
        List<CustomerCareStaffDto> staff = supportTicketService.getCustomerCareStaff();
        return ResponseEntity.ok(ApiResponse.ok(staff, "Lấy danh sách nhân sự Chăm sóc khách hàng thành công"));
    }

    /**
     * SCR-25 / SCR-26: Phân công hoặc CC tự nhận ticket (OPEN -> IN_PROGRESS).
     * POST /api/v1/admin/support/tickets/{id}/assign
     */
    @PostMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<SupportTicketResponseDto>> assign(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) AssignTicketRequestDto request,
            Authentication authentication
    ) {
        UUID actorId = currentUserId(authentication);
        SupportTicketResponseDto response = supportTicketService.assignTicket(id, actorId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã phân công ticket thành công"));
    }

    /**
     * SCR-26: CC/Admin phản hồi hoặc thêm ghi chú nội bộ (internal note).
     * POST /api/v1/admin/support/tickets/{id}/reply
     */
    @PostMapping("/{id}/reply")
    public ResponseEntity<ApiResponse<SupportTicketResponseDto>> reply(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReplyTicketRequestDto request,
            Authentication authentication
    ) {
        UUID actorId = currentUserId(authentication);
        SupportTicketResponseDto response = supportTicketService.reply(id, actorId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Phản hồi ticket thành công"));
    }

    /**
     * SCR-26: CC/Admin đóng ticket kèm lý do (rationale >= 30 ký tự).
     * POST /api/v1/admin/support/tickets/{id}/close
     */
    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<SupportTicketResponseDto>> close(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CloseTicketRequestDto request,
            Authentication authentication
    ) {
        UUID actorId = currentUserId(authentication);
        SupportTicketResponseDto response = supportTicketService.closeTicket(id, actorId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã đóng ticket thành công"));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User u)) {
            return null;
        }
        return u.getUserId();
    }
}

