package com.cani.controller;

import com.cani.dto.admin.shift.AdminShiftActionRequestDto;
import com.cani.dto.admin.shift.AdminShiftDetailDto;
import com.cani.dto.admin.shift.AdminShiftSummaryDto;
import com.cani.dto.response.ApiResponse;
import com.cani.dto.response.PageResponse;
import com.cani.entity.enums.ShiftStatus;
import com.cani.security.SecurityUtils;
import com.cani.service.AdminShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller Quản trị Ca làm việc dành cho Admin và Chăm sóc khách hàng.
 * Hỗ trợ tra cứu, đối soát, tạm đình chỉ, hủy cưỡng chế và cảnh báo EO.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/shifts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
public class AdminShiftController {

    private final AdminShiftService adminShiftService;

    /**
     * Lấy danh sách ca làm việc trên toàn hệ thống (hỗ trợ tìm kiếm, lọc theo trạng thái, phân trang).
     * GET /api/v1/admin/shifts
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminShiftSummaryDto>>> getShifts(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "status", required = false) ShiftStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<AdminShiftSummaryDto> pageResult = adminShiftService.getShifts(query, status, pageable);
        PageResponse<AdminShiftSummaryDto> response = PageResponse.from(pageResult);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách ca làm việc thành công"));
    }

    /**
     * Xem chi tiết ca làm việc kèm danh sách Worker tham gia.
     * GET /api/v1/admin/shifts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminShiftDetailDto>> getShiftDetail(
            @PathVariable("id") UUID id
    ) {
        AdminShiftDetailDto dto = adminShiftService.getShiftDetail(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy chi tiết ca làm việc thành công"));
    }

    /**
     * Hủy ca cưỡng chế kèm lý do (Admin Force Cancel Shift).
     * POST /api/v1/admin/shifts/{id}/cancel
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<AdminShiftDetailDto>> cancelShift(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AdminShiftActionRequestDto request,
            Authentication authentication
    ) {
        UUID adminUserId = resolveAdminUserId(authentication);
        AdminShiftDetailDto dto = adminShiftService.cancelShift(adminUserId, id, request.getReason());
        return ResponseEntity.ok(ApiResponse.ok(dto, "Hủy ca làm việc cưỡng chế thành công"));
    }

    /**
     * Tạm đình chỉ ca làm việc kèm lý do (Admin Suspend Shift).
     * POST /api/v1/admin/shifts/{id}/suspend
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/suspend")
    public ResponseEntity<ApiResponse<AdminShiftDetailDto>> suspendShift(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AdminShiftActionRequestDto request,
            Authentication authentication
    ) {
        UUID adminUserId = resolveAdminUserId(authentication);
        AdminShiftDetailDto dto = adminShiftService.suspendShift(adminUserId, id, request.getReason());
        return ResponseEntity.ok(ApiResponse.ok(dto, "Tạm đình chỉ ca làm việc thành công"));
    }

    /**
     * Gửi cảnh báo/nhắc nhở trực tiếp tới EO của ca (Admin Warn EO).
     * POST /api/v1/admin/shifts/{id}/warn
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    @PostMapping("/{id}/warn")
    public ResponseEntity<ApiResponse<AdminShiftDetailDto>> warnEo(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AdminShiftActionRequestDto request,
            Authentication authentication
    ) {
        UUID adminUserId = resolveAdminUserId(authentication);
        AdminShiftDetailDto dto = adminShiftService.warnEo(adminUserId, id, request.getReason());
        return ResponseEntity.ok(ApiResponse.ok(dto, "Đã gửi cảnh báo tới Nhà tổ chức sự kiện thành công"));
    }

    private UUID resolveAdminUserId(Authentication authentication) {
        if (authentication != null) {
            try {
                return SecurityUtils.getCurrentUserId(authentication);
            } catch (Exception ignored) {
                // Tiếp tục fallback bên dưới
            }
        }
        return UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    }
}
