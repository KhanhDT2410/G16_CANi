package com.cani.controller;

import com.cani.dto.faq.*;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.entity.enums.FaqCategory;
import com.cani.entity.enums.FaqStatus;
import com.cani.service.FaqService;
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
 * Controller Quản lý FAQ dành cho Quản trị viên & Chăm sóc khách hàng (SRS UC-110a / UC-110c).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/faqs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
public class AdminFaqController {

    private final FaqService faqService;

    /**
     * Danh sách tất cả FAQ kèm bộ lọc trạng thái, phân loại và phân trang.
     * GET /api/v1/admin/faqs
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<FaqResponseDto>>> getAllFaqs(
            @RequestParam(value = "status", required = false) FaqStatus status,
            @RequestParam(value = "category", required = false) FaqCategory category,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<FaqResponseDto> page = faqService.getAllFaqs(status, category, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page, "Lấy danh sách quản lý FAQ thành công"));
    }

    /**
     * Lấy thông tin chi tiết một FAQ.
     * GET /api/v1/admin/faqs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FaqResponseDto>> getFaqById(
            @PathVariable("id") UUID id
    ) {
        FaqResponseDto dto = faqService.getFaqById(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy chi tiết FAQ thành công"));
    }

    /**
     * Tạo mới một câu hỏi thường gặp (trạng thái ban đầu DRAFT).
     * POST /api/v1/admin/faqs
     */
    @PostMapping
    public ResponseEntity<ApiResponse<FaqResponseDto>> createFaq(
            @Valid @RequestBody CreateFaqRequestDto req,
            Authentication authentication
    ) {
        UUID actorId = currentUserId(authentication);
        FaqResponseDto dto = faqService.createFaq(actorId, req);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Tạo câu hỏi thường gặp thành công"));
    }

    /**
     * Cập nhật nội dung câu hỏi thường gặp (chỉ khi DRAFT hoặc PUBLISHED).
     * PUT /api/v1/admin/faqs/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FaqResponseDto>> updateFaq(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateFaqRequestDto req,
            Authentication authentication
    ) {
        UUID actorId = currentUserId(authentication);
        FaqResponseDto dto = faqService.updateFaq(actorId, id, req);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Cập nhật câu hỏi thường gặp thành công"));
    }

    /**
     * Xuất bản FAQ lên Help Center (PUBLISHED).
     * POST /api/v1/admin/faqs/{id}/publish
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<FaqResponseDto>> publishFaq(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        UUID actorId = currentUserId(authentication);
        FaqResponseDto dto = faqService.publishFaq(actorId, id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Xuất bản câu hỏi thường gặp thành công"));
    }

    /**
     * Lưu trữ FAQ, ngừng hiển thị trên Help Center (ARCHIVED).
     * POST /api/v1/admin/faqs/{id}/archive
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<FaqResponseDto>> archiveFaq(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        UUID actorId = currentUserId(authentication);
        FaqResponseDto dto = faqService.archiveFaq(actorId, id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lưu trữ câu hỏi thường gặp thành công"));
    }

    /**
     * Tải ảnh minh họa FAQ trực tiếp từ thiết bị local lên máy chủ.
     * POST /api/v1/admin/faqs/upload-image
     */
    @PostMapping(value = "/upload-image", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> uploadFaqImage(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        String url = faqService.uploadFaqImage(file);
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("url", url), "Tải ảnh từ thiết bị thành công"));
    }

    /**
     * Thống kê lượt xem và tỷ lệ giảm tải ticket (Ticket Deflection Rate).
     * GET /api/v1/admin/faqs/analytics
     */
    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<FaqAnalyticsDto>> getAnalytics() {
        FaqAnalyticsDto dto = faqService.getAnalytics();
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy thống kê FAQ thành công"));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User u)) {
            return null;
        }
        return u.getUserId();
    }
}

