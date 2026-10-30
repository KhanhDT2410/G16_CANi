package com.cani.controller;

import com.cani.dto.dispute.*;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller Dispute — endpoint cho hai bên liên quan (Worker / Event Organizer).
 * SRS UC-96 (nộp), UC-97/104 (xem chi tiết + lịch sử), UC-98 (bằng chứng), UC-101 (kháng nghị).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    /**
     * UC-96: Nộp tranh chấp.
     * POST /api/v1/disputes
     */
    @PostMapping
    public ResponseEntity<ApiResponse<DisputeDetailResponseDto>> createDispute(
            @Valid @RequestBody CreateDisputeRequestDto request,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        DisputeDetailResponseDto response = disputeService.createDispute(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response,
                "Tranh chấp đã được tạo và chuyển đến bộ phận Chăm sóc khách hàng"));
    }

    /**
     * UC-97: Xem chi tiết tranh chấp (2 bên liên quan / CC / Admin).
     * GET /api/v1/disputes/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DisputeDetailResponseDto>> getDisputeDetail(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        DisputeDetailResponseDto response = disputeService.getDisputeDetail(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy chi tiết tranh chấp thành công"));
    }

    /**
     * UC-104: Lịch sử tranh chấp của người dùng.
     * GET /api/v1/disputes/my
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<DisputeDetailResponseDto>>> getMyDisputes(
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        List<DisputeDetailResponseDto> response = disputeService.getMyDisputes(userId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy lịch sử tranh chấp thành công"));
    }

    /**
     * UC-98: Nạp bằng chứng (bên liên quan).
     * POST /api/v1/disputes/{id}/evidence
     */
    @PostMapping("/{id}/evidence")
    public ResponseEntity<ApiResponse<DisputeDetailResponseDto>> addEvidence(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AddDisputeEvidenceRequestDto request,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        DisputeDetailResponseDto response = disputeService.addEvidence(id, userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã nạp bằng chứng cho tranh chấp"));
    }

    /**
     * UC-101: Kháng nghị quyết định CC lên Admin (BR-54: 7 ngày).
     * POST /api/v1/disputes/{id}/escalate
     */
    @PostMapping("/{id}/escalate")
    public ResponseEntity<ApiResponse<DisputeDetailResponseDto>> escalateDispute(
            @PathVariable("id") UUID id,
            @Valid @RequestBody EscalateDisputeRequestDto request,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        DisputeDetailResponseDto response = disputeService.escalateDispute(id, userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response,
                "Đã kháng nghị lên quản trị viên. Quyết định cuối sẽ được thông báo sớm"));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User u)) {
            return null;
        }
        return u.getUserId();
    }
}
