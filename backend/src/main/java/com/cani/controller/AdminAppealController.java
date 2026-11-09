package com.cani.controller;

import com.cani.dto.admin.appeal.ReviewRsAppealRequestDto;
import com.cani.dto.admin.appeal.RsAppealResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.enums.RsAppealStatus;
import com.cani.security.SecurityUtils;
import com.cani.service.RsAppealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Controller phục vụ Admin và Customer Care thẩm định đơn kháng cáo điểm uy tín Worker (UC-94).
 * Customer Care và Admin cùng có quyền xem hàng đợi và xử lý duyệt/từ chối kháng cáo.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/appeals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
public class AdminAppealController {

    private final RsAppealService rsAppealService;

    /**
     * Hàng đợi đơn kháng cáo điểm uy tín kèm bộ lọc trạng thái và phân trang.
     * GET /api/v1/admin/appeals
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<RsAppealResponseDto>>> getAppeals(
            @RequestParam(value = "status", required = false) RsAppealStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<RsAppealResponseDto> page = rsAppealService.getAdminAppeals(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page, "Lấy danh sách kháng cáo điểm uy tín thành công"));
    }

    /**
     * Lấy chi tiết một đơn kháng cáo theo ID.
     * GET /api/v1/admin/appeals/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RsAppealResponseDto>> getAppealById(
            @PathVariable("id") UUID id
    ) {
        RsAppealResponseDto dto = rsAppealService.getAppealById(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy chi tiết đơn kháng cáo thành công"));
    }

    /**
     * Thẩm định và ra quyết định phê duyệt hoặc từ chối đơn kháng cáo.
     * Phê duyệt sẽ tự động khôi phục điểm RS và cập nhật nhật ký điểm uy tín.
     * POST /api/v1/admin/appeals/{id}/review
     */
    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<RsAppealResponseDto>> reviewAppeal(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReviewRsAppealRequestDto request,
            Authentication authentication
    ) {
        UUID reviewerUserId = SecurityUtils.getCurrentUserId(authentication);
        RsAppealResponseDto response = rsAppealService.reviewAppeal(reviewerUserId, id, request);
        String msg = Boolean.TRUE.equals(request.getApproved())
                ? "Chấp thuận kháng cáo và khôi phục điểm uy tín thành công"
                : "Từ chối kháng cáo điểm uy tín thành công";
        return ResponseEntity.ok(ApiResponse.ok(response, msg));
    }
}

