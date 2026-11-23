package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.dto.skill.AdminSkillRequestDto;
import com.cani.dto.skill.AdminSkillResponseDto;
import com.cani.service.AdminSkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller Quản trị Danh mục Kỹ năng (Skills Management) dành cho Admin & CSKH.
 */
@RestController
@RequestMapping("/api/v1/admin/skills")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
public class AdminSkillController {

    private final AdminSkillService adminSkillService;

    /**
     * Lấy danh sách kỹ năng có tìm kiếm, lọc danh mục, trạng thái và phân trang.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminSkillResponseDto>>> getSkills(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<AdminSkillResponseDto> page = adminSkillService.getSkills(keyword, category, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page, "Lấy danh sách kỹ năng thành công"));
    }

    /**
     * Lấy danh sách các danh mục kỹ năng hiện có trong hệ thống.
     */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> getCategories() {
        List<String> categories = adminSkillService.getCategories();
        return ResponseEntity.ok(ApiResponse.ok(categories, "Lấy danh mục kỹ năng thành công"));
    }

    /**
     * Lấy chi tiết kỹ năng theo ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminSkillResponseDto>> getSkillById(@PathVariable("id") UUID id) {
        AdminSkillResponseDto dto = adminSkillService.getSkillById(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy thông tin kỹ năng thành công"));
    }

    /**
     * Tạo mới một kỹ năng vào danh mục chuẩn.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AdminSkillResponseDto>> createSkill(
            @Valid @RequestBody AdminSkillRequestDto request
    ) {
        AdminSkillResponseDto created = adminSkillService.createSkill(request);
        return ResponseEntity.ok(ApiResponse.ok(created, "Thêm kỹ năng thành công"));
    }

    /**
     * Cập nhật thông tin kỹ năng.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminSkillResponseDto>> updateSkill(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AdminSkillRequestDto request
    ) {
        AdminSkillResponseDto updated = adminSkillService.updateSkill(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Cập nhật kỹ năng thành công"));
    }

    /**
     * Bật / Tắt trạng thái hoạt động của kỹ năng.
     */
    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<ApiResponse<AdminSkillResponseDto>> toggleStatus(@PathVariable("id") UUID id) {
        AdminSkillResponseDto updated = adminSkillService.toggleStatus(id);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Thay đổi trạng thái kỹ năng thành công"));
    }
}
