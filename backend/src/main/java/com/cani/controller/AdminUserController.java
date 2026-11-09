package com.cani.controller;

import com.cani.dto.admin.user.UpdateUserStatusRequestDto;
import com.cani.dto.admin.user.UserDetailResponseDto;
import com.cani.dto.admin.user.UserFilterRequest;
import com.cani.dto.admin.user.UserSummaryResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.dto.response.PageResponse;
import com.cani.entity.User;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller phục vụ UC-87: Quản trị Người dùng (User Management) dành cho Admin.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * API 1: Lấy danh sách người dùng có phân trang, tìm kiếm theo tên/email/phone, lọc theo role và trạng thái.
     * GET /api/v1/admin/users
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserSummaryResponseDto>>> getUsers(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "role", required = false) UserRole role,
            @RequestParam(value = "status", required = false) UserStatus status,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UserFilterRequest filter = UserFilterRequest.builder()
                .search(search)
                .role(role)
                .status(status)
                .build();

        PageResponse<UserSummaryResponseDto> response = adminUserService.getUsers(filter, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách người dùng thành công"));
    }

    /**
     * API 2: Xem chi tiết thông tin tài khoản người dùng kèm thông tin mở rộng theo vai trò (Worker / Event Organizer).
     * GET /api/v1/admin/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDetailResponseDto>> getUserById(
            @PathVariable("id") UUID id
    ) {
        UserDetailResponseDto response = adminUserService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy thông tin chi tiết người dùng thành công"));
    }

    /**
     * API 3: Cập nhật trạng thái tài khoản (Khóa LOCKED / Mở khóa ACTIVE) kèm lý do.
     * PATCH /api/v1/admin/users/{id}/status
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserDetailResponseDto>> updateUserStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateUserStatusRequestDto request,
            @RequestParam(value = "adminId", required = false) UUID explicitAdminId,
            Authentication authentication
    ) {
        UUID adminUserId = resolveAdminId(authentication, explicitAdminId);
        UserDetailResponseDto updatedUser = adminUserService.updateUserStatus(id, adminUserId, request);

        String actionDesc = request.getStatus() == UserStatus.LOCKED
                ? "Khóa tài khoản"
                : "Mở khóa / Cập nhật trạng thái tài khoản";

        return ResponseEntity.ok(ApiResponse.ok(updatedUser, actionDesc + " người dùng thành công"));
    }

    private UUID resolveAdminId(Authentication authentication, UUID explicitAdminId) {
        if (explicitAdminId != null) {
            return explicitAdminId;
        }
        if (authentication != null && authentication.getPrincipal() instanceof User u) {
            return u.getUserId();
        }
        if (authentication != null && authentication.getName() != null) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
