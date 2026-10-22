package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.dto.skill.*;
import com.cani.entity.Skill;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.UserRole;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.service.SkillVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workers/skills")
@RequiredArgsConstructor
@Slf4j
public class SkillVerificationController {

    private final SkillVerificationService verificationService;

    /**
     * Tier 1: Worker Self-Declared Skills (UC-32 & UC-42)
     */
    @PostMapping("/declare")
    public ResponseEntity<ApiResponse<List<WorkerSkillResponseDto>>> declareSkills(
            @Valid @RequestBody DeclareSkillRequestDto request,
            @RequestParam(value = "workerId", required = false) UUID explicitWorkerId,
            Authentication authentication
    ) {
        UUID workerId = resolveWorkerId(authentication, explicitWorkerId);
        List<WorkerSkillResponseDto> declared = verificationService.declareSkills(workerId, request);
        return ResponseEntity.ok(ApiResponse.ok(declared, "Khai báo kỹ năng thành công (Tier 1: Tự khai báo, 0 SP_verify)"));
    }

    /**
     * Get all skills (Tier 1 self-declared, Tier 2 AI verified, Tier 3 proven) for the worker
     */
    @GetMapping("/my-skills")
    public ResponseEntity<ApiResponse<List<WorkerSkillResponseDto>>> getMySkills(
            @RequestParam(value = "workerId", required = false) UUID explicitWorkerId,
            Authentication authentication
    ) {
        UUID workerId = resolveWorkerId(authentication, explicitWorkerId);
        List<WorkerSkillResponseDto> list = verificationService.getWorkerSkills(workerId);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách kỹ năng thành công"));
    }

    /**
     * Tier 2: Submit certificate/credential for AI Verification (UC-43 & UC-46)
     */
    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SkillVerificationResponseDto>> submitVerification(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "skillId", required = false) UUID skillId,
            @RequestParam(value = "workerId", required = false) UUID explicitWorkerId,
            @RequestParam(value = "notes", required = false) String notes,
            Authentication authentication
    ) {
        UUID workerId = resolveWorkerId(authentication, explicitWorkerId);

        log.info("Received skill verification submission for workerId: {}, skillId: {}", workerId, skillId);

        SkillVerificationResponseDto response = verificationService.submitVerification(workerId, file, skillId, notes);

        String message = switch (response.getRoutingDecision()) {
            case AUTO_APPROVE -> "Xác thực AI thành công! Kỹ năng đã được phê duyệt và cộng SP_verify.";
            case ADMIN_REVIEW -> "Tài liệu hợp lệ ở mức trung bình. Đã chuyển hồ sơ sang hàng đợi Admin duyệt.";
            case REJECT -> "Tài liệu không đạt chuẩn xác thực: " + response.getRoutingReason();
        };

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, message));
    }

    /**
     * Get verification history for the worker
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<SkillVerificationResponseDto>>> getHistory(
            @RequestParam(value = "workerId", required = false) UUID explicitWorkerId,
            Authentication authentication
    ) {
        UUID workerId = resolveWorkerId(authentication, explicitWorkerId);
        List<SkillVerificationResponseDto> history = verificationService.getWorkerVerificationHistory(workerId);
        return ResponseEntity.ok(ApiResponse.ok(history, "Lấy lịch sử xác thực kỹ năng thành công"));
    }

    /**
     * Get skill points and tiers for the worker
     */
    @GetMapping("/points")
    public ResponseEntity<ApiResponse<List<WorkerSkillPointDto>>> getSkillPoints(
            @RequestParam(value = "workerId", required = false) UUID explicitWorkerId,
            Authentication authentication
    ) {
        UUID workerId = resolveWorkerId(authentication, explicitWorkerId);
        List<WorkerSkillPointDto> points = verificationService.getWorkerSkillPoints(workerId);
        return ResponseEntity.ok(ApiResponse.ok(points, "Lấy bảng điểm kỹ năng thành công"));
    }

    /**
     * Get active Skill Taxonomy of the platform
     */
    @GetMapping("/taxonomy")
    public ResponseEntity<ApiResponse<List<Skill>>> getTaxonomy() {
        List<Skill> skills = verificationService.getTaxonomy();
        return ResponseEntity.ok(ApiResponse.ok(skills, "Lấy danh mục kỹ năng chuẩn thành công"));
    }

    /**
     * Get standard Tier configuration and thresholds (BR-64 Single Source of Truth)
     */
    @GetMapping("/tiers/config")
    public ResponseEntity<ApiResponse<List<TierConfigDto>>> getTierConfig() {
        List<TierConfigDto> configs = verificationService.getTierConfigs();
        return ResponseEntity.ok(ApiResponse.ok(configs, "Lấy cấu hình ngưỡng Tier thành công"));
    }

    /**
     * Admin manual review for Medium-confidence submissions (UC-47)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/review")
    public ResponseEntity<ApiResponse<SkillVerificationResponseDto>> adminReview(
            @PathVariable("id") UUID verificationId,
            @RequestParam("approved") boolean approved,
            @RequestParam(value = "reviewNotes", required = false) String reviewNotes,
            Authentication authentication
    ) {
        UUID adminUserId = (authentication != null && authentication.getPrincipal() instanceof User u)
                ? u.getUserId()
                : null;

        SkillVerificationResponseDto response = verificationService.adminReview(verificationId, adminUserId, approved, reviewNotes);
        String msg = approved ? "Admin đã phê duyệt kỹ năng và cộng +25 SP_verify" : "Admin đã từ chối yêu cầu xác thực";
        return ResponseEntity.ok(ApiResponse.ok(response, msg));
    }

    private UUID resolveWorkerId(Authentication authentication, UUID explicitWorkerId) {
        User user = (authentication != null && authentication.getPrincipal() instanceof User u) ? u : null;
        return verificationService.resolveWorkerId(user, explicitWorkerId);
    }
}
