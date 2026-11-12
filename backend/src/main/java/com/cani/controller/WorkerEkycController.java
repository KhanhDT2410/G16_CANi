package com.cani.controller;

import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
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
            Authentication authentication) {

        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        EkycVerificationResultDto result = ekycService.submitEkyc(
                userId, frontImage, backImage, selfieImage,
                cccdNumber, fullName, dob, gender,
                originAddress, residenceAddress, issueDate, expiryDate);

        String message;
        if ("APPROVED".equals(result.getStatus())) {
            message = "Xác thực danh tính eKYC thành công! Tài khoản của bạn đã được cấp tích xanh định danh.";
        } else if ("PENDING_REVIEW".equals(result.getStatus())) {
            message = "Hồ sơ eKYC của bạn đã được tiếp nhận và đang chờ đội ngũ CSKH/Admin phê duyệt.";
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
        return ResponseEntity.ok(ApiResponse.ok(null, "Reset trạng thái eKYC thành công. Bạn có thể tiến hành định danh lại."));
    }

    /**
     * Admin / Customer Care xem danh sách hồ sơ eKYC chờ thẩm định
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    @GetMapping("/admin/ekyc/queue")
    public ResponseEntity<ApiResponse<List<EkycVerificationResultDto>>> getPendingEkycQueue() {
        List<EkycVerificationResultDto> list = ekycService.getPendingEkycQueue();
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách hàng đợi thẩm định eKYC thành công"));
    }

    /**
     * Admin / Customer Care duyệt thủ công hồ sơ eKYC
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    @PostMapping("/admin/ekyc/{verificationId}/review")
    public ResponseEntity<ApiResponse<EkycVerificationResultDto>> adminReviewEkyc(
            @PathVariable UUID verificationId,
            @Valid @RequestBody EkycAdminReviewRequestDto request,
            Authentication authentication) {

        UUID adminUserId = SecurityUtils.getCurrentUserId(authentication);
        EkycVerificationResultDto result = ekycService.adminReviewEkyc(adminUserId, verificationId, request);
        return ResponseEntity.ok(ApiResponse.ok(result, "Phê duyệt hồ sơ eKYC thành công: " + result.getStatus()));
    }
}
