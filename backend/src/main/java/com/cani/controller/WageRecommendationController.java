package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.dto.wage.WageRecommendationRequestDto;
import com.cani.dto.wage.WageRecommendationResponseDto;
import com.cani.service.WageRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/wage-recommendations")
@RequiredArgsConstructor
@Tag(name = "Wage Recommendation", description = "Đề xuất mức thù lao thị trường cho ca làm việc (UC-14)")
public class WageRecommendationController {

    private final WageRecommendationService wageRecommendationService;

    /**
     * UC-14: Nhận khuyến nghị thù lao chuẩn cho ca làm việc
     */
    @Operation(summary = "Gợi ý mức thù lao ca làm", description = "Tính toán và khuyến nghị mức thù lao tối ưu kèm khoảng giá tham chiếu dựa trên ma trận thị trường, thời điểm ca và độ khẩn cấp.")
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<WageRecommendationResponseDto>> recommendWage(
            @Valid @RequestBody WageRecommendationRequestDto request
    ) {
        WageRecommendationResponseDto response = wageRecommendationService.recommendWage(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Tính toán mức thù lao khuyến nghị thành công"));
    }
}
