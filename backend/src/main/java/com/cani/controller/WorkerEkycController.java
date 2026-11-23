package com.cani.controller;

import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycConsentPolicyDto;
import com.cani.dto.ekyc.EkycLegacyRetentionInventoryDto;
import com.cani.dto.ekyc.EkycLegalHoldRequestDto;
import com.cani.dto.ekyc.EkycVerificationResultDto;
import com.cani.dto.response.ApiResponse;
import com.cani.security.SecurityUtils;
import com.cani.service.WorkerEkycService;
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
public class WorkerEkycController {

    private final WorkerEkycService ekycService;

    @GetMapping("/ekyc/consent-policy")
    public ResponseEntity<ApiResponse<EkycConsentPolicyDto>> getConsentPolicy() {
        return ResponseEntity.ok(ApiResponse.ok(
                ekycService.getConsentPolicy(),
                "Lấy thông báo xử lý dữ liệu cá nhân & sinh trắc học eKYC thành công"));
    }

    /**
     * Worker nộp hồ sơ eKYC (Mặt trước CCCD, Mặt sau CCCD, Ảnh chân dung Selfie)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping(value = "/workers/ekyc/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<EkycVerificationResultDto>> submitEkyc(
            @RequestParam("frontImage") MultipartFile frontImage,
            @RequestParam("backImage") MultipartFile backImage,
            @RequestParam("selfieImage") MultipartFile selfieImage,
            @RequestParam(value = "cccdNumber", required = false) String cccdNumber,
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "dob", required = false) String dob,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "originAddress", required = false) String originAddress,
            @RequestParam(value = "residenceAddress", required = false) String residenceAddress,
            @RequestParam(value = "issueDate", required = false) String issueDate,
            @RequestParam(value = "expiryDate", required = false) String expiryDate,
            @RequestParam(value = "consentAccepted", required = false) Boolean consentAccepted,
            @RequestParam(value = "consentNoticeVersion", required = false) String consentNoticeVersion,
            Authentication authentication) {

        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        EkycVerificationResultDto result = ekycService.submitEkyc(
                userId, frontImage, backImage, selfieImage,
                cccdNumber, fullName, dob, gender,
                originAddress, residenceAddress, issueDate, expiryDate,
                consentAccepted, consentNoticeVersion);

        String message;
        if ("APPROVED".equals(result.getStatus())) {
            message = "Xác thực danh tính eKYC thành công! Tài khoản của bạn đã được cấp tích xanh định danh.";
        } else if ("TECHNICAL_FAILURE".equals(result.getStatus())) {
            message = "Hệ thống xác thực VNPT eKYC đang gặp sự cố kỹ thuật tạm thời. Hồ sơ chưa bị từ chối, vui lòng thử lại.";
        } else if ("PROCESSING".equals(result.getStatus()) || "PENDING".equals(result.getStatus())) {
            message = "Hồ sơ eKYC đang được xử lý. Vui lòng chờ trong giây lát.";
        } else {
            message = "Xác thực eKYC không thành công: " + result.getRoutingReason();
        }

        return ResponseEntity.ok(ApiResponse.ok(result, message));
    }

    /**
     * Worker kiểm tra trạng thái hồ sơ eKYC
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/workers/ekyc/status")
    public ResponseEntity<ApiResponse<EkycVerificationResultDto>> getMyEkycStatus(
            Authentication authentication) {

        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        EkycVerificationResultDto result = ekycService.getMyEkycStatus(userId);
        return ResponseEntity.ok(ApiResponse.ok(result, "Lấy thông tin trạng thái eKYC thành công"));
    }

    /**
     * Worker yêu cầu reset lại hồ sơ eKYC để định danh lại từ đầu
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/workers/ekyc/reset")
    public ResponseEntity<ApiResponse<Void>> resetMyEkyc(Authentication authentication) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        ekycService.resetMyEkyc(userId);
        return ResponseEntity
                .ok(ApiResponse.ok(null, "Reset trạng thái eKYC thành công. Bạn có thể tiến hành định danh lại."));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/ekyc/{verificationId}/legal-hold")
    public ResponseEntity<ApiResponse<EkycVerificationResultDto>> updateLegalHold(
            @PathVariable UUID verificationId,
            @Valid @RequestBody EkycLegalHoldRequestDto request,
            Authentication authentication) {
        UUID adminUserId = SecurityUtils.getCurrentUserId(authentication);
        EkycVerificationResultDto result = ekycService.updateLegalHold(
                adminUserId, verificationId, Boolean.TRUE.equals(request.getLegalHold()), request.getReason());
        return ResponseEntity.ok(ApiResponse.ok(result, "Cập nhật trạng thái bảo lưu pháp lý hồ sơ eKYC thành công"));
    }

    /**
     * Truy vấn kiểm kê chỉ trả số đếm các hồ sơ eKYC cũ có image_retention_until IS NULL.
     * Tuyệt đối không gán hạn hay xóa tệp thật.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/ekyc/retention/legacy-inventory")
    public ResponseEntity<ApiResponse<EkycLegacyRetentionInventoryDto>> getLegacyNullRetentionInventory() {
        return ResponseEntity.ok(ApiResponse.ok(
                ekycService.getLegacyNullRetentionInventory(),
                "Kiểm kê số lượng hồ sơ eKYC cũ chưa có hạn lưu giữ ảnh"));
    }
}
