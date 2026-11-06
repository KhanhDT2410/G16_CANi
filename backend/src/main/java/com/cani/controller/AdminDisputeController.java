package com.cani.controller;

import com.cani.dto.dispute.*;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.service.DisputeService;
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
 * Controller Dispute — phía Customer Care & Admin (SRS UC-99/100/102/109).
 * UC-100 (CC ra quyết định) và UC-102 (Admin final decision) dùng chung
 * {@link DisputeService#issueDecision} — service tự kiểm tra role + trạng thái hợp lệ.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/disputes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
public class AdminDisputeController {

    private final DisputeService disputeService;

    /**
     * UC-109: Hàng đợi tranh chấp chưa xử lý (ưu tiên theo BR-57).
     * Bao gồm cả đơn ESCALATED để Admin ra quyết định cuối (UC-102).
     * GET /api/v1/admin/disputes/queue
     */
    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<List<DisputeQueueItemDto>>> getQueue() {
        List<DisputeQueueItemDto> queue = disputeService.getDisputeQueue();
        return ResponseEntity.ok(ApiResponse.ok(queue, "Lấy hàng đợi tranh chấp thành công"));
    }

    /**
     * UC-109 (phân tầng CC): Hàng đợi chỉ gồm đơn đang được giao cho CC này.
     * GET /api/v1/admin/disputes/queue/mine
     */
    @GetMapping("/queue/mine")
    public ResponseEntity<ApiResponse<List<DisputeQueueItemDto>>> getMyQueue(
            Authentication authentication
    ) {
        UUID actorId = currentUserId(authentication);
        List<DisputeQueueItemDto> queue = disputeService.getDisputeQueueForCc(actorId);
        return ResponseEntity.ok(ApiResponse.ok(queue, "Lấy hàng đợi tranh chấp được giao thành công"));
    }

    /**
     * UC-99: Danh sách nhân sự Customer Care để chuyển đơn.
     * GET /api/v1/admin/disputes/customer-care-staff
     */
    @GetMapping("/customer-care-staff")
    public ResponseEntity<ApiResponse<List<CustomerCareStaffDto>>> getCustomerCareStaff() {
        List<CustomerCareStaffDto> staff = disputeService.getCustomerCareStaff();
        return ResponseEntity.ok(ApiResponse.ok(staff, "Lấy danh sách nhân sự Chăm sóc khách hàng thành công"));
    }

    /**
     * UC-99: CC tự nhận đơn hoặc chuyển cho CC khác (chuyển đơn → INVESTIGATING nếu đang OPEN).
     * POST /api/v1/admin/disputes/{id}/assign
     */
    @PostMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<DisputeDetailResponseDto>> assign(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) AssignDisputeRequestDto request,
            Authentication authentication
    ) {
        UUID actorId = currentUserId(authentication);
        UUID target = request != null ? request.getTargetCcUserId() : null;
        DisputeDetailResponseDto response = disputeService.assignDispute(actorId, id, target);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã phân công tranh chấp thành công"));
    }

    /**
     * UC-100 (CC) / UC-102 (Admin): Ra quyết định xử lý tranh chấp.
     * POST /api/v1/admin/disputes/{id}/decision
     */
    @PostMapping("/{id}/decision")
    public ResponseEntity<ApiResponse<DisputeDetailResponseDto>> issueDecision(
            @PathVariable("id") UUID id,
            @Valid @RequestBody IssueDecisionRequestDto request,
            Authentication authentication
    ) {
        UUID actorId = currentUserId(authentication);
        DisputeDetailResponseDto response = disputeService.issueDecision(actorId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã ghi nhận quyết định xử lý tranh chấp"));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User u)) {
            return null;
        }
        return u.getUserId();
    }
}
