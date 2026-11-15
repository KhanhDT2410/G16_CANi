package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.dto.response.PageResponse;
import com.cani.dto.wage.WageMatrixRequestDto;
import com.cani.dto.wage.WageMatrixResponseDto;
import com.cani.entity.enums.City;
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

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/wage-matrix")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Wage Matrix", description = "Quản trị biểu ma trận thù lao theo chuẩn nền tảng (UC-34)")
public class AdminWageMatrixController {

    private final WageMatrixService wageMatrixService;

    /**
     * Lấy danh sách ma trận lương có phân trang và bộ lọc
     */
    @Operation(summary = "Xem danh sách ma trận lương", description = "Lấy danh sách các biểu lương giờ theo thành phố, vị trí công việc và trạng thái.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WageMatrixResponseDto>>> getWageMatrix(
            @RequestParam(required = false) City city,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(page = 0, size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<WageMatrixResponseDto> response = wageMatrixService.filterWageMatrix(city, jobType, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách ma trận lương thành công"));
    }

    /**
     * Xem chi tiết một bản ghi ma trận lương
     */
    @Operation(summary = "Xem chi tiết ma trận lương", description = "Xem chi tiết cấu hình thù lao của một bản ghi.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WageMatrixResponseDto>> getWageMatrixById(@PathVariable UUID id) {
        WageMatrixResponseDto dto = wageMatrixService.getWageMatrixById(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy chi tiết cấu hình thù lao thành công"));
    }

    /**
     * Tạo mới cấu hình ma trận lương
     */
    @Operation(summary = "Tạo mới biểu lương", description = "Thêm một mức lương chuẩn mới theo tổ hợp (City, JobType, TimeSlot, LeadTime).")
    @PostMapping
    public ResponseEntity<ApiResponse<WageMatrixResponseDto>> createWageMatrix(
            @Valid @RequestBody WageMatrixRequestDto request
    ) {
        WageMatrixResponseDto dto = wageMatrixService.createWageMatrix(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(dto, "Tạo mới cấu hình ma trận lương thành công"));
    }

    /**
     * Cập nhật cấu hình ma trận lương
     */
    @Operation(summary = "Cập nhật biểu lương", description = "Chỉnh sửa mức thù lao mỗi giờ hoặc thông tin của cấu hình ma trận lương.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WageMatrixResponseDto>> updateWageMatrix(
            @PathVariable UUID id,
            @Valid @RequestBody WageMatrixRequestDto request
    ) {
        WageMatrixResponseDto dto = wageMatrixService.updateWageMatrix(id, request);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Cập nhật cấu hình ma trận lương thành công"));
    }

    /**
     * Bật / tắt trạng thái kích hoạt của biểu lương
     */
    @Operation(summary = "Bật/Tắt hoạt động biểu lương", description = "Chuyển đổi trạng thái hoạt động của một dòng ma trận lương.")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<ApiResponse<WageMatrixResponseDto>> toggleActive(@PathVariable UUID id) {
        WageMatrixResponseDto dto = wageMatrixService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Cập nhật trạng thái biểu lương thành công"));
    }

    /**
     * Xóa cấu hình ma trận lương
     */
    @Operation(summary = "Xóa biểu lương", description = "Xóa vĩnh viễn một bản ghi ma trận lương.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWageMatrix(@PathVariable UUID id) {
        wageMatrixService.deleteWageMatrix(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Xóa cấu hình ma trận lương thành công"));
    }
}
