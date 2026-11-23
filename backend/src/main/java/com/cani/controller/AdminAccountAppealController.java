package com.cani.controller;

import com.cani.dto.appeal.AccountAppealResponseDto;
import com.cani.dto.appeal.ReviewAccountAppealRequestDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.enums.AccountAppealStatus;
import com.cani.security.SecurityUtils;
import com.cani.service.AccountAppealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller phục vụ Admin & Customer Care thẩm định các đơn giải trình khóa tài khoản.
 */
@RestController
@RequestMapping("/api/v1/admin/account-appeals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
public class AdminAccountAppealController {

    private final AccountAppealService accountAppealService;

    /**
     * Đếm số lượng đơn giải trình tài khoản đang chờ thẩm định (phục vụ badge thông báo Admin)
     */
    @GetMapping("/pending-count")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getPendingCount() {
        long count = accountAppealService.countPendingAppeals();
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("pendingCount", count), "Lấy số lượng đơn giải trình chờ xử lý thành công"));
    }

    /**
     * Lấy danh sách hàng đợi các đơn giải trình tài khoản
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AccountAppealResponseDto>>> getAppeals(
            @RequestParam(value = "status", required = false) AccountAppealStatus status,
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AccountAppealResponseDto> page = accountAppealService.getAdminAppeals(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page, "Lấy danh sách đơn giải trình thành công"));
    }

    /**
     * Lấy chi tiết đơn giải trình
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountAppealResponseDto>> getAppealDetail(
            @PathVariable("id") UUID id
    ) {
        AccountAppealResponseDto dto = accountAppealService.getAppealDetail(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy chi tiết đơn giải trình thành công"));
    }

    /**
     * Admin thẩm định đơn giải trình: APPROVED (Mở khóa tài khoản) hoặc REJECTED (Từ chối)
     */
    @PutMapping("/{id}/review")
    public ResponseEntity<ApiResponse<AccountAppealResponseDto>> reviewAppeal(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReviewAccountAppealRequestDto request,
            Authentication authentication
    ) {
        UUID adminUserId = SecurityUtils.getCurrentUserId(authentication);
        AccountAppealResponseDto result = accountAppealService.reviewAppeal(adminUserId, id, request);

        String message = "APPROVED".equalsIgnoreCase(request.getDecision())
                ? "Chấp thuận đơn giải trình và mở khóa tài khoản thành công!"
                : "Từ chối đơn giải trình tài khoản thành công!";

        return ResponseEntity.ok(ApiResponse.ok(result, message));
    }
}
