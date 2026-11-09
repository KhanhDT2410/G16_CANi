package com.cani.controller;

import com.cani.dto.faq.FaqPublicResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.enums.FaqCategory;
import com.cani.service.FaqService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller Câu hỏi thường gặp FAQ công khai (Help Center) — SRS FE-12 / UC-110b.
 * Quyền truy cập: permitAll() — mọi người dùng có thể đọc câu hỏi thường gặp đã xuất bản.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    /**
     * Lấy danh sách câu hỏi thường gặp đã xuất bản (Help Center).
     * GET /api/v1/faqs?category=ACCOUNT
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FaqPublicResponseDto>>> getPublicFaqs(
            @RequestParam(value = "category", required = false) FaqCategory category
    ) {
        List<FaqPublicResponseDto> result = faqService.getPublicFaqs(category);
        return ResponseEntity.ok(ApiResponse.ok(result, "Lấy danh sách câu hỏi thường gặp thành công"));
    }

    /**
     * Xem chi tiết một câu hỏi thường gặp và tự động tăng lượt xem.
     * GET /api/v1/faqs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FaqPublicResponseDto>> getPublicFaqById(
            @PathVariable("id") UUID id
    ) {
        FaqPublicResponseDto result = faqService.getPublicFaqById(id);
        return ResponseEntity.ok(ApiResponse.ok(result, "Lấy thông tin chi tiết câu hỏi thường gặp thành công"));
    }
}
