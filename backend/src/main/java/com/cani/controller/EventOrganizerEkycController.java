package com.cani.controller;

import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycConsentPolicyDto;
import com.cani.dto.ekyc.EkycLegalHoldRequestDto;
import com.cani.dto.ekyc.EoEkycVerificationResultDto;
import com.cani.dto.ekyc.EoProfileDto;
import com.cani.dto.event.EoReviewResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.security.SecurityUtils;
import com.cani.service.EventOrganizerEkycService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventOrganizerEkycController {

    private final EventOrganizerEkycService eoEkycService;

    @GetMapping("/eo/ekyc/consent-policy")
    public ResponseEntity<ApiResponse<EkycConsentPolicyDto>> getConsentPolicy() {
        return ResponseEntity.ok(ApiResponse.ok(
                eoEkycService.getConsentPolicy(),
                "Lấy thông báo xử lý dữ liệu cá nhân & sinh trắc học eKYC thành công"));
    }

    /**
     * Nhà tổ chức sự kiện nộp hồ sơ định danh eKYC cá nhân
     */
    @PostMapping(value = "/eo/ekyc/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<EoEkycVerificationResultDto>> submitEkyc(
            @RequestParam("frontImage") MultipartFile frontImage,
            @RequestParam("backImage") MultipartFile backImage,
            @RequestParam("selfieImage") MultipartFile selfieImage,
            @RequestParam(value = "businessLicenseImage", required = false) MultipartFile businessLicenseImage,
            @RequestParam(value = "cccdNumber", required = false) String cccdNumber,
            @RequestParam(value = "consentAccepted", required = false) Boolean consentAccepted,
            @RequestParam(value = "consentNoticeVersion", required = false) String consentNoticeVersion,
            Authentication authentication) {

        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        EoEkycVerificationResultDto result = eoEkycService.submitEkyc(
                userId, frontImage, backImage, selfieImage, cccdNumber, consentAccepted, consentNoticeVersion);

        String message;
        if ("APPROVED".equals(result.getStatus())) {
            message = "Định danh eKYC thành công! Bạn đã được chứng thực uy tín.";
        } else if ("TECHNICAL_FAILURE".equals(result.getStatus())) {
            message = "Hệ thống xác thực VNPT eKYC đang gặp sự cố kỹ thuật tạm thời. Hồ sơ chưa bị từ chối, vui lòng thử lại.";
        } else if ("PROCESSING".equals(result.getStatus()) || "PENDING".equals(result.getStatus())) {
            message = "Hồ sơ eKYC đang được xử lý. Vui lòng chờ trong giây lát.";
        } else {
            message = "Định danh eKYC không thành công: " + result.getRoutingReason();
        }

        return ResponseEntity.ok(ApiResponse.ok(result, message));
    }

    /**
     * Nhà tổ chức kiểm tra trạng thái định danh eKYC của đơn vị mình
     */
    @GetMapping("/eo/ekyc/status")
    public ResponseEntity<ApiResponse<EoEkycVerificationResultDto>> getMyEkycStatus(
            Authentication authentication) {

        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        EoEkycVerificationResultDto result = eoEkycService.getMyEkycStatus(userId);
        return ResponseEntity.ok(ApiResponse.ok(result, "Lấy thông tin trạng thái eKYC doanh nghiệp thành công"));
    }

    /**
     * Nhà tổ chức lấy thông tin hồ sơ doanh nghiệp (Profile)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/eo/profile")
    public ResponseEntity<ApiResponse<EoProfileDto>> getMyProfile(
            Authentication authentication) {

        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        EoProfileDto profile = eoEkycService.getEoProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(profile, "Lấy thông tin hồ sơ doanh nghiệp thành công"));
    }

    /**
     * Nhà tổ chức cập nhật thông tin hồ sơ doanh nghiệp (Profile)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PutMapping("/eo/profile")
    public ResponseEntity<ApiResponse<EoProfileDto>> updateMyProfile(
            @RequestBody EoProfileDto request,
            Authentication authentication) {

        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        EoProfileDto profile = eoEkycService.updateEoProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(profile, "Cập nhật thông tin hồ sơ doanh nghiệp thành công"));
    }

    /**
     * Nhà tổ chức xem danh sách các đánh giá từ người lao động (Worker)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/eo/reviews")
    public ResponseEntity<ApiResponse<List<EoReviewResponseDto>>> getMyReviews(
            Authentication authentication) {

        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<EoReviewResponseDto> reviews = eoEkycService.getEoReviews(userId);
        return ResponseEntity.ok(ApiResponse.ok(reviews, "Lấy danh sách đánh giá từ người lao động thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/eo-ekyc/{verificationId}/legal-hold")
    public ResponseEntity<ApiResponse<EoEkycVerificationResultDto>> updateLegalHold(
            @PathVariable UUID verificationId,
            @Valid @RequestBody EkycLegalHoldRequestDto request,
            Authentication authentication) {
        UUID adminUserId = SecurityUtils.getCurrentUserId(authentication);
        EoEkycVerificationResultDto result = eoEkycService.updateLegalHold(
                adminUserId, verificationId, Boolean.TRUE.equals(request.getLegalHold()), request.getReason());
        return ResponseEntity.ok(ApiResponse.ok(result, "Cập nhật trạng thái bảo lưu pháp lý hồ sơ eKYC doanh nghiệp thành công"));
    }
}
