package com.cani.controller;

import com.cani.dto.admin.config.BulkUpdateSystemConfigRequestDto;
import com.cani.dto.admin.config.SystemConfigResponseDto;
import com.cani.dto.admin.config.UpdateSystemConfigRequestDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.service.SystemConfigService;
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
 * Controller phục vụ UC-114: Cấu hình Tham số Hệ thống (Configure System Parameters) dành cho Admin.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * API 1: Lấy danh sách toàn bộ các tham số cấu hình hệ thống.
     * GET /api/v1/admin/configs
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemConfigResponseDto>>> getAllConfigs() {
        List<SystemConfigResponseDto> configs = systemConfigService.getAllConfigs();
        return ResponseEntity.ok(ApiResponse.ok(configs, "Lấy danh sách cấu hình hệ thống thành công"));
    }

    /**
     * API 2: Lấy chi tiết thông tin một tham số cấu hình theo key.
     * GET /api/v1/admin/configs/{key}
     */
    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemConfigResponseDto>> getConfigByKey(
            @PathVariable("key") String key
    ) {
        SystemConfigResponseDto config = systemConfigService.getConfigByKey(key);
        return ResponseEntity.ok(ApiResponse.ok(config, "Lấy thông tin chi tiết cấu hình thành công"));
    }

    /**
     * API 3: Cập nhật giá trị một tham số cấu hình đơn lẻ theo key.
     * PUT /api/v1/admin/configs/{key}
     */
    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemConfigResponseDto>> updateConfig(
            @PathVariable("key") String key,
            @Valid @RequestBody UpdateSystemConfigRequestDto request,
            @RequestParam(value = "adminId", required = false) UUID explicitAdminId,
            Authentication authentication
    ) {
        UUID adminUserId = resolveAdminId(authentication, explicitAdminId);
        SystemConfigResponseDto updated = systemConfigService.updateConfig(key, request, adminUserId);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Cập nhật cấu hình hệ thống thành công"));
    }

    /**
     * API 4: Cập nhật hàng loạt nhiều tham số cấu hình cùng lúc.
     * PUT /api/v1/admin/configs
     */
    @PutMapping
    public ResponseEntity<ApiResponse<List<SystemConfigResponseDto>>> bulkUpdateConfigs(
            @Valid @RequestBody BulkUpdateSystemConfigRequestDto request,
            @RequestParam(value = "adminId", required = false) UUID explicitAdminId,
            Authentication authentication
    ) {
        UUID adminUserId = resolveAdminId(authentication, explicitAdminId);
        List<SystemConfigResponseDto> updatedList = systemConfigService.bulkUpdateConfigs(request, adminUserId);
        return ResponseEntity.ok(ApiResponse.ok(updatedList, "Cập nhật hàng loạt cấu hình hệ thống thành công"));
    }

    /**
     * Phân giải UUID của Admin từ Authentication principal hoặc query param dự phòng (dùng trong test/automation).
     */
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

