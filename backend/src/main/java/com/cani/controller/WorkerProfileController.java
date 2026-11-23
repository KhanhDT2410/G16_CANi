package com.cani.controller;

import com.cani.dto.profile.EoPublicProfileResponseDto;
import com.cani.dto.profile.UpdateWorkerProfileRequestDto;
import com.cani.dto.profile.WorkerPublicProfileResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.security.SecurityUtils;
import com.cani.service.WorkerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Profile Management", description = "Xem hồ sơ công khai & nghề nghiệp giữa Worker và Event Organizer")
public class WorkerProfileController {

    private final WorkerProfileService workerProfileService;

    /**
     * Worker xem hồ sơ cá nhân của chính mình.
     */
    @Operation(summary = "Worker xem hồ sơ cá nhân", description = "Lấy đầy đủ thông tin hồ sơ năng lực, kỹ năng, uy tín và đánh giá của chính Worker đang đăng nhập.")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/workers/me/profile")
    public ResponseEntity<ApiResponse<WorkerPublicProfileResponseDto>> getMyProfile(Authentication authentication) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        WorkerPublicProfileResponseDto profile = workerProfileService.getMyProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(profile, "Lấy hồ sơ cá nhân thành công"));
    }

    /**
     * Worker cập nhật hồ sơ cá nhân của chính mình.
     */
    @Operation(summary = "Worker cập nhật hồ sơ cá nhân", description = "Cập nhật họ tên, giới thiệu, kinh nghiệm, địa chỉ và ảnh đại diện của Worker.")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PutMapping("/workers/me/profile")
    public ResponseEntity<ApiResponse<WorkerPublicProfileResponseDto>> updateMyProfile(
            @Valid @RequestBody UpdateWorkerProfileRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        WorkerPublicProfileResponseDto profile = workerProfileService.updateMyProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(profile, "Cập nhật hồ sơ cá nhân thành công"));
    }

    /**
     * EO hoặc Admin xem hồ sơ nghề nghiệp chi tiết của Worker.
     */
    @Operation(summary = "EO xem hồ sơ Worker", description = "Lấy hồ sơ năng lực đầy đủ của Worker (kỹ năng, tích xanh AI, SP, điểm uy tín, reviews, trạng thái My Team).")
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/eo/workers/{workerId}/profile")
    public ResponseEntity<ApiResponse<WorkerPublicProfileResponseDto>> getWorkerProfileForEo(
            @PathVariable UUID workerId,
            Authentication authentication
    ) {
        UUID eoUserId = SecurityUtils.getCurrentUserId(authentication);
        WorkerPublicProfileResponseDto profile = workerProfileService.getWorkerProfileForEo(eoUserId, workerId);
        return ResponseEntity.ok(ApiResponse.ok(profile, "Lấy hồ sơ nhân sự thành công"));
    }

    /**
     * Endpoint công khai cho phép người dùng đã đăng nhập tra cứu hồ sơ Worker.
     */
    @Operation(summary = "Xem hồ sơ công khai của Worker", description = "Lấy hồ sơ năng lực công khai của Worker.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/workers/{workerId}/public-profile")
    public ResponseEntity<ApiResponse<WorkerPublicProfileResponseDto>> getWorkerPublicProfile(
            @PathVariable UUID workerId
    ) {
        WorkerPublicProfileResponseDto profile = workerProfileService.getWorkerProfileForEo(null, workerId);
        return ResponseEntity.ok(ApiResponse.ok(profile, "Lấy hồ sơ nhân sự thành công"));
    }

    /**
     * Worker hoặc bất kỳ người dùng đã đăng nhập xem hồ sơ công khai của Nhà tổ chức sự kiện (EO).
     * Tuyệt đối không để lộ mã số thuế (taxId).
     */
    @Operation(summary = "Xem hồ sơ công khai của EO", description = "Lấy thông tin công khai của Nhà tổ chức sự kiện (tên đơn vị, tích xanh xác thực pháp lý, rating, số sự kiện hoàn thành).")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/eo/{eoId}/public-profile")
    public ResponseEntity<ApiResponse<EoPublicProfileResponseDto>> getEoPublicProfile(
            @PathVariable UUID eoId
    ) {
        EoPublicProfileResponseDto profile = workerProfileService.getEoPublicProfile(eoId);
        return ResponseEntity.ok(ApiResponse.ok(profile, "Lấy hồ sơ nhà tổ chức sự kiện thành công"));
    }
}
