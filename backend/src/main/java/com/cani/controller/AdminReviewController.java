package com.cani.controller;

import com.cani.dto.admin.review.AdminModerateReviewRequestDto;
import com.cani.dto.admin.review.AdminReviewFilterRequestDto;
import com.cani.dto.admin.review.AdminReviewResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.security.SecurityUtils;
import com.cani.service.AdminReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
@Tag(name = "Admin Reviews", description = "API quản trị, kiểm duyệt và điều chỉnh đánh giá cho Admin & Customer Care")
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    @Operation(summary = "Lấy danh sách đánh giá có phân trang và bộ lọc")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminReviewResponseDto>>> getReviews(
            AdminReviewFilterRequestDto filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AdminReviewResponseDto> page = adminReviewService.getReviews(filter, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page, "Lấy danh sách đánh giá thành công"));
    }

    @Operation(summary = "Xem chi tiết đánh giá")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminReviewResponseDto>> getReviewDetail(
            @PathVariable("id") UUID id
    ) {
        AdminReviewResponseDto dto = adminReviewService.getReviewDetail(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy chi tiết đánh giá thành công"));
    }

    @Operation(summary = "Ẩn/gỡ bỏ đánh giá sai lệch, tự động tính lại điểm trung bình và hoàn trả uy tín")
    @PostMapping("/{id}/hide")
    public ResponseEntity<ApiResponse<AdminReviewResponseDto>> hideReview(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AdminModerateReviewRequestDto request,
            Authentication authentication
    ) {
        Authentication auth = authentication != null ? authentication : org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        UUID adminUserId = SecurityUtils.getCurrentUserId(auth);
        AdminReviewResponseDto response = adminReviewService.hideReview(adminUserId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã ẩn đánh giá và cập nhật lại điểm số thành công"));
    }

    @Operation(summary = "Khôi phục hiển thị đánh giá đã ẩn và tính toán lại điểm số")
    @PostMapping("/{id}/unhide")
    public ResponseEntity<ApiResponse<AdminReviewResponseDto>> unhideReview(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        Authentication auth = authentication != null ? authentication : org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        UUID adminUserId = SecurityUtils.getCurrentUserId(auth);
        AdminReviewResponseDto response = adminReviewService.unhideReview(adminUserId, id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã khôi phục đánh giá và cập nhật lại điểm số thành công"));
    }

    @Operation(summary = "Gắn cờ cảnh báo đánh giá có dấu hiệu vi phạm/spam")
    @PostMapping("/{id}/flag")
    public ResponseEntity<ApiResponse<AdminReviewResponseDto>> flagReview(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AdminModerateReviewRequestDto request,
            Authentication authentication
    ) {
        Authentication auth = authentication != null ? authentication : org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        UUID adminUserId = SecurityUtils.getCurrentUserId(auth);
        AdminReviewResponseDto response = adminReviewService.flagReview(adminUserId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã gắn cờ đánh giá thành công"));
    }

    @Operation(summary = "Gỡ cờ cảnh báo của đánh giá")
    @PostMapping("/{id}/unflag")
    public ResponseEntity<ApiResponse<AdminReviewResponseDto>> unflagReview(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        Authentication auth = authentication != null ? authentication : org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        UUID adminUserId = SecurityUtils.getCurrentUserId(auth);
        AdminReviewResponseDto response = adminReviewService.unflagReview(adminUserId, id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã gỡ cờ đánh giá thành công"));
    }
}
