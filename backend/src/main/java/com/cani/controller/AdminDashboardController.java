package com.cani.controller;

import com.cani.dto.admin.dashboard.AdminDashboardMetricsResponseDto;
import com.cani.dto.admin.dashboard.AdminRecentActivityResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller phục vụ UC-86: Bảng Điều Khiển & Số Liệu Vận Hành Admin (Admin Dashboard & Metrics Analytics).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * API 1: Tổng hợp và trả về các chỉ số vận hành nền tảng:
     * - Thống kê User (Tổng người dùng, Worker, Event Organizer, Customer Care, Locked)
     * - Thống kê Thẩm định chờ xử lý (Worker eKYC, EO eKYC, Skill Certs)
     * - Thống kê Sự kiện & Ca làm việc (Tổng ca, ca đang tuyển, tổng sự kiện, sự kiện hoạt động)
     * 
     * GET /api/v1/admin/dashboard/metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<AdminDashboardMetricsResponseDto>> getDashboardMetrics() {
        AdminDashboardMetricsResponseDto metrics = adminDashboardService.getDashboardMetrics();
        return ResponseEntity.ok(ApiResponse.ok(metrics, "Lấy số liệu vận hành dashboard thành công"));
    }

    /**
     * API 2: Lấy 10 hoạt động quản trị gần nhất từ bảng audit_logs kèm thông tin Admin thực hiện.
     * 
     * GET /api/v1/admin/dashboard/recent-activities
     */
    @GetMapping("/recent-activities")
    public ResponseEntity<ApiResponse<List<AdminRecentActivityResponseDto>>> getRecentActivities() {
        List<AdminRecentActivityResponseDto> activities = adminDashboardService.getRecentActivities();
        return ResponseEntity.ok(ApiResponse.ok(activities, "Lấy danh sách hoạt động gần nhất thành công"));
    }
}
