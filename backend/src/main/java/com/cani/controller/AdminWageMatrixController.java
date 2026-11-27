package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.dto.response.PageResponse;
import com.cani.dto.wage.UpdateWageRegionRequestDto;
import com.cani.dto.wage.WageMatrixRequestDto;
import com.cani.dto.wage.WageMatrixResponseDto;
import com.cani.dto.wage.WageRegionDto;
import com.cani.entity.enums.JobTier;
import com.cani.entity.enums.WageRegionCode;
import com.cani.service.WageMatrixService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/wage-matrix")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Wage Matrix", description = "Quản trị biểu ma trận thù lao Region x JobTier theo NĐ 293/2025/NĐ-CP (UC-30, UC-31)")
public class AdminWageMatrixController {

    private final WageMatrixService wageMatrixService;

    @Operation(summary = "Danh sách 4 vùng lương pháp lý", description = "Lấy cấu hình sàn tối thiểu giờ và hệ số thị trường của 4 vùng theo NĐ 293/2025/NĐ-CP.")
    @GetMapping("/regions")
    public ResponseEntity<ApiResponse<List<WageRegionDto>>> getRegions() {
        return ResponseEntity.ok(ApiResponse.ok(wageMatrixService.getAllRegions(), "Lấy danh sách vùng lương thành công"));
    }

    @Operation(summary = "Cập nhật cấu hình vùng lương", description = "Cập nhật sàn lương tối thiểu giờ hoặc hệ số thị trường cho một vùng.")
    @PutMapping("/regions/{code}")
    public ResponseEntity<ApiResponse<WageRegionDto>> updateRegion(
            @PathVariable WageRegionCode code,
            @Valid @RequestBody UpdateWageRegionRequestDto request
    ) {
        WageRegionDto updated = wageMatrixService.updateRegion(code, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Cập nhật vùng lương thành công"));
    }

    @Operation(summary = "Xem danh sách ma trận Region x JobTier", description = "Lấy danh sách các nhóm bậc lương theo vùng và trạng thái.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WageMatrixResponseDto>>> getWageMatrix(
            @RequestParam(required = false) WageRegionCode regionCode,
            @RequestParam(required = false) JobTier jobTier,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(page = 0, size = 30, sort = "regionCode", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<WageMatrixResponseDto> response = wageMatrixService.filterWageMatrix(regionCode, jobTier, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách ma trận lương thành công"));
    }

    @Operation(summary = "Xem chi tiết một dòng ma trận", description = "Xem chi tiết hệ số thù lao của một cặp Region x JobTier.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WageMatrixResponseDto>> getWageMatrixById(@PathVariable UUID id) {
        WageMatrixResponseDto dto = wageMatrixService.getWageMatrixById(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy chi tiết cấu hình thù lao thành công"));
    }

    @Operation(summary = "Tạo mới cấu hình Region x JobTier", description = "Thêm cấu hình hệ số thù lao cho một cặp Vùng x Nhóm công việc.")
    @PostMapping
    public ResponseEntity<ApiResponse<WageMatrixResponseDto>> createWageMatrix(
            @Valid @RequestBody WageMatrixRequestDto request
    ) {
        WageMatrixResponseDto dto = wageMatrixService.createWageMatrix(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(dto, "Tạo mới cấu hình ma trận lương thành công"));
    }

    @Operation(summary = "Cập nhật cấu hình Region x JobTier", description = "Chỉnh sửa hệ số nhân, đơn giá ghim cố định hoặc mô tả của nhóm công việc theo vùng.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WageMatrixResponseDto>> updateWageMatrix(
            @PathVariable UUID id,
            @Valid @RequestBody WageMatrixRequestDto request
    ) {
        WageMatrixResponseDto dto = wageMatrixService.updateWageMatrix(id, request);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Cập nhật cấu hình ma trận lương thành công"));
    }

    @Operation(summary = "Bật/Tắt hoạt động cấu hình", description = "Chuyển đổi trạng thái hoạt động của một dòng ma trận lương.")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<ApiResponse<WageMatrixResponseDto>> toggleActive(@PathVariable UUID id) {
        WageMatrixResponseDto dto = wageMatrixService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Cập nhật trạng thái biểu lương thành công"));
    }

    @Operation(summary = "Xóa cấu hình ma trận", description = "Xóa vĩnh viễn một bản ghi ma trận lương.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWageMatrix(@PathVariable UUID id) {
        wageMatrixService.deleteWageMatrix(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Xóa cấu hình ma trận lương thành công"));
    }
}
