package com.cani.controller;

import com.cani.dto.admin.noshow.*;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.enums.NoShowDetectedBy;
import com.cani.entity.enums.UserRole;
import com.cani.security.SecurityUtils;
import com.cani.service.NoShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/no-shows")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
public class AdminNoShowController {

    private final NoShowService noShowService;

    /**
     * Lấy danh sách phân trang các ca No-show kèm bộ lọc.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NoShowResponseDto>>> getNoShows(
            @ModelAttribute NoShowFilterRequestDto filter) {
        Page<NoShowResponseDto> result = noShowService.getNoShows(filter);
        return ResponseEntity.ok(ApiResponse.ok(result, "Lấy danh sách vi phạm No-show thành công"));
    }

    /**
     * Lấy số liệu thống kê tổng hợp No-show.
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<NoShowStatsDto>> getNoShowStats() {
        NoShowStatsDto stats = noShowService.getNoShowStats();
        return ResponseEntity.ok(ApiResponse.ok(stats, "Lấy thống kê No-show thành công"));
    }

    /**
     * Lấy chi tiết một ca No-show theo ID bản ghi.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NoShowResponseDto>> getNoShowDetail(@PathVariable UUID id) {
        NoShowResponseDto detail = noShowService.getNoShowDetail(id);
        return ResponseEntity.ok(ApiResponse.ok(detail, "Lấy chi tiết vi phạm No-show thành công"));
    }

    /**
     * Admin hoặc Customer Care đánh dấu No-show thủ công cho một ca làm việc.
     */
    @PostMapping("/{assignmentId}/mark")
    public ResponseEntity<ApiResponse<NoShowResponseDto>> manualMarkNoShow(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody(required = false) ManualMarkNoShowRequestDto request,
            Authentication authentication) {

        UUID actorUserId = SecurityUtils.getCurrentUserId(authentication);
        NoShowDetectedBy detectedBy = SecurityUtils.isAdmin(authentication)
                ? NoShowDetectedBy.ADMIN
                : NoShowDetectedBy.CUSTOMER_CARE;

        String notes = request != null ? request.getNotes() : null;
        NoShowResponseDto result = noShowService.markNoShow(assignmentId, detectedBy, actorUserId, notes);

        return ResponseEntity.ok(ApiResponse.ok(result, "Xác nhận nhân sự vắng mặt (No-show) thành công"));
    }

    /**
     * Kích hoạt quét No-show toàn hệ thống tức thì (phục vụ kiểm thử, demo & vận hành).
     */
    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<NoShowScanResultDto>> triggerScan() {
        NoShowScanResultDto scanResult = noShowService.scanAndProcessNoShows();
        return ResponseEntity.ok(ApiResponse.ok(scanResult, scanResult.getMessage()));
    }
}
