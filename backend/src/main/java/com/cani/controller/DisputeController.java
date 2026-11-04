package com.cani.controller;

import com.cani.dto.dispute.CreateDisputeRequestDto;
import com.cani.dto.dispute.DisputeResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.security.SecurityUtils;
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
 * Controller phục vụ Quản lý & Giải quyết Khiếu nại Tranh chấp 3 bên (UC-96, UC-97, UC-104, FE-11)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    /**
     * UC-96: Gửi khiếu nại tranh chấp (Worker hoặc EO)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<DisputeResponseDto>> submitDispute(
            @Valid @RequestBody CreateDisputeRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        DisputeResponseDto response = disputeService.submitDispute(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã gửi khiếu nại thành công! Ban Chăm sóc Khách hàng (Customer Care) sẽ tiếp nhận xử lý trong vòng 24 giờ."));
    }

    /**
     * UC-104: Xem lịch sử danh sách khiếu nại của tôi
     */
    @PreAuthorize("hasAnyRole('WORKER', 'EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<DisputeResponseDto>>> getMyDisputes(
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<DisputeResponseDto> list = disputeService.getMyDisputes(userId);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách khiếu nại thành công"));
    }

    /**
     * UC-97: Xem chi tiết một khiếu nại
     */
    @PreAuthorize("hasAnyRole('WORKER', 'EVENT_ORGANIZER', 'CUSTOMER_CARE', 'ADMIN')")
    @GetMapping("/{disputeId}")
    public ResponseEntity<ApiResponse<DisputeResponseDto>> getDisputeDetail(
            @PathVariable("disputeId") UUID disputeId,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        DisputeResponseDto response = disputeService.getDisputeDetail(userId, disputeId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy chi tiết khiếu nại thành công"));
    }
}
