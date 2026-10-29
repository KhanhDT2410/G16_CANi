package com.cani.controller;

import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EoEkycVerificationResultDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.service.EventOrganizerEkycService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
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

    /**
     * Nhà tổ chức sự kiện nộp hồ sơ định danh eKYC doanh nghiệp
     */
    @PostMapping(value = "/eo/ekyc/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<EoEkycVerificationResultDto>> submitEkyc(
            @RequestParam("frontImage") MultipartFile frontImage,
            @RequestParam("backImage") MultipartFile backImage,
            @RequestParam("selfieImage") MultipartFile selfieImage,
            @RequestParam(value = "businessLicenseImage", required = false) MultipartFile businessLicenseImage,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication) {

        UUID userId = resolveUserId(authentication, explicitUserId);
        EoEkycVerificationResultDto result = eoEkycService.submitEkyc(
                userId, frontImage, backImage, selfieImage, businessLicenseImage
        );

        String message;
        if ("APPROVED".equals(result.getStatus())) {
            message = "Định danh doanh nghiệp eKYC thành công! Đơn vị của bạn đã được chứng thực uy tín.";
        } else if ("PENDING_REVIEW".equals(result.getStatus())) {
            message = "Hồ sơ eKYC doanh nghiệp đã được gửi và đang chờ Admin / CSKH thẩm định.";
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
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication) {

        UUID userId = resolveUserId(authentication, explicitUserId);
        EoEkycVerificationResultDto result = eoEkycService.getMyEkycStatus(userId);
        return ResponseEntity.ok(ApiResponse.ok(result, "Lấy thông tin trạng thái eKYC doanh nghiệp thành công"));
    }

    /**
     * Admin / CSKH xem danh sách hồ sơ eKYC doanh nghiệp chờ phê duyệt
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    @GetMapping("/admin/eo-ekyc/queue")
    public ResponseEntity<ApiResponse<List<EoEkycVerificationResultDto>>> getPendingEkycQueue() {
        List<EoEkycVerificationResultDto> list = eoEkycService.getPendingEkycQueue();
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách hồ sơ eKYC doanh nghiệp chờ duyệt thành công"));
    }

    /**
     * Admin / CSKH duyệt thủ công hồ sơ eKYC doanh nghiệp
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    @PostMapping("/admin/eo-ekyc/{verificationId}/review")
    public ResponseEntity<ApiResponse<EoEkycVerificationResultDto>> adminReviewEkyc(
            @PathVariable UUID verificationId,
            @Valid @RequestBody EkycAdminReviewRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication) {

        UUID adminUserId = resolveUserId(authentication, explicitUserId);
        EoEkycVerificationResultDto result = eoEkycService.adminReviewEkyc(adminUserId, verificationId, request);
        return ResponseEntity.ok(ApiResponse.ok(result, "Phê duyệt hồ sơ eKYC doanh nghiệp thành công: " + result.getStatus()));
    }

    private UUID resolveUserId(Authentication authentication, UUID explicitUserId) {
        UUID callerId = null;
        if (authentication != null && authentication.getPrincipal() instanceof User u) {
            callerId = u.getUserId();
        } else if (authentication != null && authentication.getName() != null) {
            try {
                callerId = UUID.fromString(authentication.getName());
            } catch (Exception ignored) {}
        }
        if (callerId == null) {
            throw new BadRequestException("Vui lòng đăng nhập hoặc cung cấp userId hợp lệ");
        }
        // An toàn: chặn IDOR — chỉ được thao tác với đúng userId của người đang đăng nhập.
        if (explicitUserId != null && !explicitUserId.equals(callerId)) {
            throw new AppException("Không có quyền thao tác thay cho người dùng khác", HttpStatus.FORBIDDEN);
        }
        return callerId;
    }
}
