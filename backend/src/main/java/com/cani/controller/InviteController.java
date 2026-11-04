package com.cani.controller;

import com.cani.dto.invite.CreateInviteRequestDto;
import com.cani.dto.invite.InviteResponseDto;
import com.cani.dto.invite.JoinInviteRequestDto;
import com.cani.dto.invite.JoinInviteResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.service.InviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller phục vụ Quản lý & Tuyển dụng trực tiếp qua Mã mời (UC-72, UC-73, FE-08 Invite Mode)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    /**
     * UC-72: EO sinh Link & Mã mời trực tiếp cho Ca làm việc
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/api/v1/eo/shifts/{shiftId}/invites")
    public ResponseEntity<ApiResponse<InviteResponseDto>> createShiftInvite(
            @PathVariable("shiftId") UUID shiftId,
            @RequestBody(required = false) CreateInviteRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        InviteResponseDto response = inviteService.generateShiftInvite(userId, shiftId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Sinh mã mời và liên kết tham gia ca thành công!"));
    }

    /**
     * EO lấy mã mời đang hoạt động của ca
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/api/v1/eo/shifts/{shiftId}/invites")
    public ResponseEntity<ApiResponse<InviteResponseDto>> getShiftInvite(
            @PathVariable("shiftId") UUID shiftId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        InviteResponseDto response = inviteService.getShiftInvite(userId, shiftId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy thông tin mã mời thành công"));
    }

    /**
     * Tra cứu chi tiết thông tin ca làm trước khi nhập mã mời (Công khai cho Worker)
     */
    @GetMapping("/api/v1/invites/{code}")
    public ResponseEntity<ApiResponse<InviteResponseDto>> getInviteDetails(
            @PathVariable("code") String code
    ) {
        InviteResponseDto response = inviteService.getInviteDetails(code);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy thông tin lời mời thành công"));
    }

    /**
     * UC-73: Worker tham gia ca làm việc qua Mã mời hoặc Quét QR mời
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/api/v1/workers/invites/join")
    public ResponseEntity<ApiResponse<JoinInviteResponseDto>> joinViaInvite(
            @Valid @RequestBody JoinInviteRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        JoinInviteResponseDto response = inviteService.joinViaInviteCode(userId, request.getCode());
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMessage()));
    }

    private UUID resolveUserId(Authentication authentication, UUID explicitUserId) {
        UUID callerId = null;
        if (authentication != null && authentication.getPrincipal() instanceof User u) {
            callerId = u.getUserId();
        } else if (authentication != null && authentication.getName() != null) {
            try {
                callerId = UUID.fromString(authentication.getName());
            } catch (Exception ignored) {}
        }
        if (callerId == null) {
            throw new BadRequestException("Vui lòng đăng nhập hoặc cung cấp userId hợp lệ");
        }
        if (explicitUserId != null && !explicitUserId.equals(callerId)) {
            throw new AppException("Không có quyền thao tác thay cho người dùng khác", HttpStatus.FORBIDDEN);
        }
        return callerId;
    }
}

