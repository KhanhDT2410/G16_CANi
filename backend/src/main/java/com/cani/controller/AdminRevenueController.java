package com.cani.controller;

import com.cani.dto.admin.revenue.RevenueAnalyticsResponseDto;
import com.cani.dto.admin.revenue.RevenueSummaryResponseDto;
import com.cani.dto.admin.revenue.RevenueTransactionDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.enums.PlanType;
import com.cani.service.RevenueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Controller phục vụ UC-108: Xem Bảng Doanh Thu & Chỉ Số Tài Chính (Revenue Dashboard).
 * Phân quyền: ADMIN (Tương ứng vai trò Admin / Finance theo đặc tả SRS).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/revenue")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRevenueController {

    private final RevenueService revenueService;

    /**
     * API 1: Lấy tổng quan doanh thu, so sánh kỳ trước, phân tích loại gói, hình thức sự kiện,
     * trạng thái VNPay và các giao dịch cần theo dõi.
     * GET /api/v1/admin/revenue/summary?from=&to=
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<RevenueSummaryResponseDto>> getRevenueSummary(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        RevenueSummaryResponseDto summary = revenueService.getRevenueSummary(from, to);
        return ResponseEntity.ok(ApiResponse.ok(summary, "Lấy dữ liệu tổng quan doanh thu thành công"));
    }

    /**
     * API 2: Bảng chi tiết các giao dịch doanh thu / thanh toán kèm bộ lọc và phân trang.
     * GET /api/v1/admin/revenue/transactions?from=&to=&status=&packageType=&page=&size=
     */
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<RevenueTransactionDto>>> getRevenueTransactions(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "packageType", required = false) PlanType packageType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<RevenueTransactionDto> page = revenueService.getRevenueTransactions(from, to, status, packageType, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page, "Lấy danh sách giao dịch doanh thu thành công"));
    }

    /**
     * API 3: Xu hướng biến động doanh thu theo chu kỳ (ngày, tuần, tháng).
     * GET /api/v1/admin/revenue/analytics?period=day|week|month&from=&to=
     */
    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<RevenueAnalyticsResponseDto>> getRevenueAnalytics(
            @RequestParam(value = "period", required = false, defaultValue = "day") String period,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        RevenueAnalyticsResponseDto analytics = revenueService.getRevenueAnalytics(period, from, to);
        return ResponseEntity.ok(ApiResponse.ok(analytics, "Lấy dữ liệu phân tích doanh thu thành công"));
    }
}

