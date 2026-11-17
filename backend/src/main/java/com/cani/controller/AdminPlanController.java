package com.cani.controller;

import com.cani.dto.admin.AdminPlanRequestDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.Plan;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.PlanRepository;
import com.cani.repository.SubscriptionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Subscription Plans", description = "Quản trị danh mục gói dịch vụ do Quản trị viên quy định")
public class AdminPlanController {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Lấy danh sách tất cả gói dịch vụ")
    @GetMapping
    public ResponseEntity<ApiResponse<List<Plan>>> getAllPlans() {
        List<Plan> plans = planRepository.findAll();
        return ResponseEntity.ok(ApiResponse.ok(plans, "Lấy danh mục gói cước thành công"));
    }

    @Operation(summary = "Lấy thông tin chi tiết một gói dịch vụ")
    @GetMapping("/{planId}")
    public ResponseEntity<ApiResponse<Plan>> getPlanById(@PathVariable UUID planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói cước: " + planId));
        return ResponseEntity.ok(ApiResponse.ok(plan, "Lấy chi tiết gói cước thành công"));
    }

    @Operation(summary = "Tạo mới một gói dịch vụ")
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<Plan>> createPlan(@Valid @RequestBody AdminPlanRequestDto request) {
        planRepository.findByPlanName(request.getPlanName()).ifPresent(p -> {
            throw new BadRequestException("Tên gói cước '" + request.getPlanName() + "' đã tồn tại");
        });

        String featuresJson = serializeFeatures(request.getFeatures());

        Plan plan = Plan.builder()
                .planName(request.getPlanName().trim())
                .planType(request.getPlanType())
                .price(request.getPrice())
                .durationDays(request.getDurationDays())
                .maxEvents(request.getMaxEvents())
                .maxShifts(request.getMaxShifts() != null ? request.getMaxShifts() : 0)
                .maxWorkers(request.getMaxWorkers() != null ? request.getMaxWorkers() : 0)
                .baseMatchingQuota(request.getBaseMatchingQuota() != null ? request.getBaseMatchingQuota() : request.getMaxEvents())
                .features(featuresJson)
                .build();

        Plan saved = planRepository.save(plan);
        log.info("Admin đã tạo gói cước mới: {} (Loại: {}, Giá: {})", saved.getPlanName(), saved.getPlanType(), saved.getPrice());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(saved, "Tạo gói cước mới thành công"));
    }

    @Operation(summary = "Cập nhật thông tin gói dịch vụ")
    @PutMapping("/{planId}")
    @Transactional
    public ResponseEntity<ApiResponse<Plan>> updatePlan(
            @PathVariable UUID planId,
            @Valid @RequestBody AdminPlanRequestDto request
    ) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói cước: " + planId));

        // Kiểm tra trùng tên gói nếu thay đổi
        if (!plan.getPlanName().equalsIgnoreCase(request.getPlanName())) {
            planRepository.findByPlanName(request.getPlanName()).ifPresent(p -> {
                throw new BadRequestException("Tên gói cước '" + request.getPlanName() + "' đã tồn tại");
            });
        }

        plan.setPlanName(request.getPlanName().trim());
        plan.setPlanType(request.getPlanType());
        plan.setPrice(request.getPrice());
        plan.setDurationDays(request.getDurationDays());
        plan.setMaxEvents(request.getMaxEvents());
        plan.setMaxShifts(request.getMaxShifts() != null ? request.getMaxShifts() : 0);
        plan.setMaxWorkers(request.getMaxWorkers() != null ? request.getMaxWorkers() : 0);
        if (request.getBaseMatchingQuota() != null) {
            plan.setBaseMatchingQuota(request.getBaseMatchingQuota());
        }
        if (request.getFeatures() != null) {
            plan.setFeatures(serializeFeatures(request.getFeatures()));
        }

        Plan updated = planRepository.save(plan);
        log.info("Admin đã cập nhật gói cước {}: Tên={}, Giá={}, MaxEvents={}",
                planId, updated.getPlanName(), updated.getPrice(), updated.getMaxEvents());
        return ResponseEntity.ok(ApiResponse.ok(updated, "Cập nhật gói cước thành công"));
    }

    @Operation(summary = "Xóa gói dịch vụ (Nếu chưa có EO nào đăng ký)")
    @DeleteMapping("/{planId}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable UUID planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói cước: " + planId));

        try {
            planRepository.delete(plan);
            log.info("Admin đã xóa gói cước: {}", planId);
            return ResponseEntity.ok(ApiResponse.ok(null, "Xóa gói cước thành công"));
        } catch (Exception ex) {
            throw new BadRequestException("Không thể xóa gói cước này vì đã có dữ liệu đăng ký hoặc liên kết ràng buộc.");
        }
    }

    private String serializeFeatures(List<String> features) {
        if (features == null || features.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(features);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
