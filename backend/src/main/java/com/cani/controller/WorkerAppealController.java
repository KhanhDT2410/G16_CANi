package com.cani.controller;

import com.cani.dto.admin.appeal.RsAppealResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.dto.worker.appeal.SubmitRsAppealRequestDto;
import com.cani.security.SecurityUtils;
import com.cani.service.RsAppealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller phục vụ Worker nộp và theo dõi kháng cáo điểm uy tín RS (UC-94, SRS FE-10).
 * Bắt buộc bảo vệ IDOR: Tất cả workerId/userId được trích xuất an toàn từ Authentication context.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workers/appeals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WORKER')")
public class WorkerAppealController {

    private final RsAppealService rsAppealService;

    /**
     * Nộp đơn kháng cáo điểm phạt uy tín trong vòng 7 ngày kể từ thời điểm bị trừ.
     * POST /api/v1/workers/appeals
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RsAppealResponseDto>> submitAppeal(
            @Valid @RequestBody SubmitRsAppealRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        RsAppealResponseDto response = rsAppealService.submitAppeal(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Nộp đơn kháng cáo điểm uy tín thành công"));
    }

    /**
     * Lấy danh sách toàn bộ các đơn kháng cáo của chính Worker đang đăng nhập.
     * GET /api/v1/workers/appeals
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RsAppealResponseDto>>> getMyAppeals(
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<RsAppealResponseDto> appeals = rsAppealService.getWorkerAppeals(userId);
        return ResponseEntity.ok(ApiResponse.ok(appeals, "Lấy danh sách kháng cáo điểm uy tín thành công"));
    }
}

