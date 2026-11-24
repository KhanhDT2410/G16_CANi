package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.dto.skill.*;
import com.cani.entity.Skill;
import com.cani.entity.User;
import com.cani.entity.enums.SkillVerificationStatus;
import com.cani.service.SkillVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
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
     * Tier 2: Submit certificate/credential for V2 Trust & Verification Pipeline (UC-43 & UC-46)
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

        log.info("Received skill verification submission [workerId={}, skillId={}]", workerId, skillId);

        SkillVerificationResponseDto response = verificationService.submitVerification(workerId, file, skillId, notes);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, buildVerificationMessage(response)));
    }

    /**
     * Worker xem chi tiết 1 yêu cầu xác minh và kết quả phân tích TrustScore (kiểm tra quyền sở hữu)
     */
    @GetMapping("/verifications/{id}")
    public ResponseEntity<ApiResponse<SkillVerificationResponseDto>> getVerificationDetail(
            @PathVariable("id") UUID verificationId,
            Authentication authentication
    ) {
        User user = extractUser(authentication);
        SkillVerificationResponseDto detail = verificationService.getVerificationDetail(verificationId, user);
        return ResponseEntity.ok(ApiResponse.ok(detail, "Lấy chi tiết yêu cầu xác minh kỹ năng thành công"));
    }

    /**
     * Worker bổ sung hoặc thay thế tài liệu khi chưa xác minh thành công (đè vào hồ sơ cũ)
     */
    @PostMapping(value = "/verifications/{id}/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SkillVerificationResponseDto>> resubmitVerification(
            @PathVariable("id") UUID verificationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "skillId", required = false) UUID skillId,
            @RequestParam(value = "workerId", required = false) UUID explicitWorkerId,
            @RequestParam(value = "notes", required = false) String notes,
            Authentication authentication
    ) {
        UUID workerId = resolveWorkerId(authentication, explicitWorkerId);
        SkillVerificationResponseDto response = verificationService.resubmitVerification(verificationId, workerId, file, skillId, notes);
        return ResponseEntity.ok(ApiResponse.ok(response, buildVerificationMessage(response)));
    }

    /**
     * Worker thử lại xác minh tự động khi nguồn tra cứu gặp lỗi kết nối tạm thời (VERIFICATION_FAILED)
     */
    @PostMapping("/verifications/{id}/retry")
    public ResponseEntity<ApiResponse<SkillVerificationResponseDto>> retryVerification(
            @PathVariable("id") UUID verificationId,
            @RequestParam(value = "workerId", required = false) UUID explicitWorkerId,
            Authentication authentication
    ) {
        UUID workerId = resolveWorkerId(authentication, explicitWorkerId);
        SkillVerificationResponseDto response = verificationService.retryVerification(verificationId, workerId);
        return ResponseEntity.ok(ApiResponse.ok(response, buildVerificationMessage(response)));
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
     * Create or retrieve dynamic custom skill (for EO / Event Shift creation & Worker)
     */
    @PostMapping("/custom")
    public ResponseEntity<ApiResponse<Skill>> createCustomSkill(
            @Valid @RequestBody CreateCustomSkillRequestDto request
    ) {
        Skill skill = verificationService.getOrCreateCustomSkill(request.getName(), request.getCategory());
        return ResponseEntity.ok(ApiResponse.ok(skill, "Lưu kỹ năng thành công"));
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
     * Serve uploaded certificate image/PDF document securely (Owner / ADMIN / CUSTOMER_CARE hoặc Signed URL còn hạn)
     */
    @GetMapping("/documents/{filename:.+}")
    public ResponseEntity<Resource> getCertificateDocument(
            @PathVariable("filename") String filename,
            @RequestParam(value = "expires", required = false) String expires,
            @RequestParam(value = "sig", required = false) String sig,
            Authentication authentication
    ) {
        User currentUser = extractUser(authentication);
        Resource file = verificationService.loadAuthorizedCertificateDocument(filename, currentUser, expires, sig);
        String lower = filename.toLowerCase();
        MediaType mediaType = MediaType.IMAGE_JPEG;
        if (lower.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if (lower.endsWith(".webp")) {
            mediaType = MediaType.parseMediaType("image/webp");
        } else if (lower.endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFilename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(mediaType)
                .body(file);
    }

    /**
     * Admin Skill Audit Log: Lấy toàn bộ lịch sử thẩm định của tất cả Worker để thanh tra, hậu kiểm
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<SkillVerificationResponseDto>>> getAllVerifications(
            @RequestParam(value = "status", required = false) SkillVerificationStatus status
    ) {
        List<SkillVerificationResponseDto> all = verificationService.getAllVerificationsForAdmin(status);
        return ResponseEntity.ok(ApiResponse.ok(all, "Lấy danh sách toàn bộ hồ sơ thẩm định kỹ năng thành công"));
    }

    /**
     * Admin xem chi tiết một yêu cầu thẩm định (kèm trạng thái xác minh lịch sử)
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    @GetMapping("/admin/{id}")
    public ResponseEntity<ApiResponse<SkillVerificationResponseDto>> getAdminVerificationDetail(
            @PathVariable("id") UUID verificationId,
            Authentication authentication
    ) {
        SkillVerificationResponseDto detail = verificationService.getVerificationDetail(verificationId, extractUser(authentication));
        return ResponseEntity.ok(ApiResponse.ok(detail, "Lấy chi tiết hồ sơ thẩm định thành công"));
    }

    /**
     * Admin Post-Audit Revocation: Thu hồi điểm kỹ năng nếu phát hiện chứng chỉ giả mạo
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    @PostMapping("/admin/{id}/revoke")
    public ResponseEntity<ApiResponse<SkillVerificationResponseDto>> revokeVerification(
            @PathVariable("id") UUID verificationId,
            @RequestParam(value = "reason", required = false) String reason,
            Authentication authentication
    ) {
        User admin = extractUser(authentication);
        UUID adminUserId = admin != null ? admin.getUserId() : null;

        SkillVerificationResponseDto response = verificationService.revokeVerification(verificationId, adminUserId, reason);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã thu hồi chứng nhận kỹ năng và khấu trừ điểm SP thành công"));
    }

    private String buildVerificationMessage(SkillVerificationResponseDto response) {
        if (response.getStatus() == SkillVerificationStatus.VERIFICATION_FAILED) {
            return "Nguồn tra cứu chứng chỉ đang gặp lỗi tạm thời: " + response.getRoutingReason();
        }
        if (response.getStatus() == SkillVerificationStatus.APPROVED) {
            return "Chứng chỉ kỹ năng đã được xác minh tự động thành công và cộng SP_verify.";
        }
        if (response.getStatus() == SkillVerificationStatus.NEEDS_MORE_INFO) {
            return "Yêu cầu bổ sung thêm tài liệu xác minh: " + response.getRoutingReason();
        }
        return response.getRoutingReason() != null && !response.getRoutingReason().isBlank()
                ? response.getRoutingReason()
                : "Chứng chỉ chưa đạt yêu cầu xác minh tự động.";
    }

    private User extractUser(Authentication authentication) {
        return (authentication != null && authentication.getPrincipal() instanceof User u) ? u : null;
    }

    private UUID resolveWorkerId(Authentication authentication, UUID explicitWorkerId) {
        return verificationService.resolveWorkerId(extractUser(authentication), explicitWorkerId);
    }
}
