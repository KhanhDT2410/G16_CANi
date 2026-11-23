package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.entity.EoEkycVerification;
import com.cani.entity.User;
import com.cani.entity.WorkerEkycVerification;
import com.cani.entity.enums.UserRole;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.WorkerEkycVerificationRepository;
import com.cani.service.ekyc.storage.EkycImageAccessTokenService;
import com.cani.service.ekyc.storage.EkycImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Controller phục vụ truy cập ảnh CCCD / Selfie riêng tư có kiểm soát phân quyền chặt chẽ:
 * - Admin / Customer Care: được phép xem mọi hồ sơ phục vụ thẩm định.
 * - Worker / EO: CHỈ được phép xem ảnh của chính bản ghi thuộc sở hữu tài khoản mình (chặn IDOR/BOLA).
 * - Trình duyệt tải thẻ <img>: chỉ được phép khi có Signed URL HMAC-SHA256 hợp lệ và còn thời hạn ngắn.
 */
@RestController
@RequestMapping("/api/v1/ekyc/images")
@RequiredArgsConstructor
public class EkycImageController {

    private final WorkerEkycVerificationRepository workerEkycRepository;
    private final EoEkycVerificationRepository eoEkycRepository;
    private final EkycImageStorage ekycImageStorage;
    private final EkycImageAccessTokenService accessTokenService;

    @GetMapping("/{targetType}/{verificationId}/{slot}")
    public ResponseEntity<Resource> getPrivateEkycImage(
            @PathVariable String targetType,
            @PathVariable UUID verificationId,
            @PathVariable String slot,
            @RequestParam(value = "expires", required = false) String expires,
            @RequestParam(value = "sig", required = false) String sig,
            Authentication authentication
    ) {
        String normTarget = normalizeTarget(targetType);
        String normSlot = normalizeSlot(slot);

        User currentUser = extractUser(authentication);
        ResolvedImageRecord record = resolveRecord(normTarget, verificationId, normSlot);

        enforceAccessControl(currentUser, record, normTarget, verificationId, normSlot, expires, sig);

        if (record.purged() || !ekycImageStorage.isManagedObjectKey(record.objectKey())) {
            throw new ResourceNotFoundException("Ảnh xác minh eKYC không tồn tại hoặc đã bị xóa theo chính sách lưu giữ.");
        }

        Resource resource = ekycImageStorage.loadAsResource(record.objectKey());
        MediaType mediaType = resolveMediaType(record.objectKey());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .header("Pragma", "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .body(resource);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{targetType}/{verificationId}/{slot}/signed-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> issueSignedImageUrl(
            @PathVariable String targetType,
            @PathVariable UUID verificationId,
            @PathVariable String slot,
            Authentication authentication
    ) {
        String normTarget = normalizeTarget(targetType);
        String normSlot = normalizeSlot(slot);

        User currentUser = extractUser(authentication);
        if (currentUser == null) {
            throw new AppException("Vui lòng đăng nhập để lấy liên kết xem ảnh.", HttpStatus.UNAUTHORIZED);
        }

        ResolvedImageRecord record = resolveRecord(normTarget, verificationId, normSlot);
        enforceAuthenticatedAuthorization(currentUser, record);

        if (record.purged() || !ekycImageStorage.isManagedObjectKey(record.objectKey()) || !ekycImageStorage.exists(record.objectKey())) {
            throw new ResourceNotFoundException("Ảnh xác minh eKYC không còn tồn tại trong kho lưu trữ bảo mật.");
        }

        String signedUrl = accessTokenService.generateSignedImageUrl(normTarget, verificationId, normSlot, record.objectKey(), record.status());
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("signedUrl", signedUrl),
                "Cấp liên kết xem ảnh bảo mật ngắn hạn thành công"
        ));
    }

    private void enforceAccessControl(
            User currentUser,
            ResolvedImageRecord record,
            String targetType,
            UUID verificationId,
            String slot,
            String expires,
            String sig
    ) {
        if (currentUser != null) {
            enforceAuthenticatedAuthorization(currentUser, record);
            return;
        }
        if (!accessTokenService.isValidSignature(targetType, verificationId.toString(), slot, expires, sig) || !accessTokenService.matchesRecord(sig, record.objectKey(), record.status())) {
            throw new AppException("Bạn không có quyền truy cập ảnh xác minh eKYC này hoặc liên kết đã hết hạn.", HttpStatus.FORBIDDEN);
        }
    }

    private void enforceAuthenticatedAuthorization(User currentUser, ResolvedImageRecord record) {
        if (currentUser.getRole() == UserRole.ADMIN) {
            return;
        }
        if (record.ownerUserId() != null && record.ownerUserId().equals(currentUser.getUserId())) {
            return;
        }
        throw new AppException("Bạn không có quyền truy cập ảnh xác minh danh tính của tài khoản khác.", HttpStatus.FORBIDDEN);
    }

    private ResolvedImageRecord resolveRecord(String targetType, UUID verificationId, String slot) {
        if ("worker".equals(targetType)) {
            WorkerEkycVerification v = workerEkycRepository.findById(verificationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ eKYC Worker."));
            UUID ownerUserId = (v.getWorker() != null && v.getWorker().getUser() != null)
                    ? v.getWorker().getUser().getUserId()
                    : null;
            String key = switch (slot) {
                case "front" -> v.getFrontImageUrl();
                case "back" -> v.getBackImageUrl();
                case "selfie" -> v.getSelfieImageUrl();
                default -> null;
            };
            return new ResolvedImageRecord(ownerUserId, key, v.getImagesPurgedAt() != null, v.getStatus());
        }

        EoEkycVerification v = eoEkycRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ eKYC Nhà tổ chức."));
        UUID ownerUserId = (v.getEventOrganizer() != null && v.getEventOrganizer().getUser() != null)
                ? v.getEventOrganizer().getUser().getUserId()
                : null;
        String key = switch (slot) {
            case "front" -> v.getFrontImageUrl();
            case "back" -> v.getBackImageUrl();
            case "selfie" -> v.getSelfieImageUrl();
            default -> null;
        };
        return new ResolvedImageRecord(ownerUserId, key, v.getImagesPurgedAt() != null, v.getStatus());
    }

    private static String normalizeTarget(String raw) {
        if (raw == null) {
            throw new BadRequestException("Loại hồ sơ ảnh eKYC không hợp lệ.");
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        if (!"worker".equals(norm) && !"eo".equals(norm)) {
            throw new BadRequestException("Loại hồ sơ ảnh eKYC chỉ hỗ trợ worker hoặc eo.");
        }
        return norm;
    }

    private static String normalizeSlot(String raw) {
        if (raw == null) {
            throw new BadRequestException("Vị trí ảnh eKYC không hợp lệ.");
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        if (!"front".equals(norm) && !"back".equals(norm) && !"selfie".equals(norm)) {
            throw new BadRequestException("Vị trí ảnh eKYC chỉ hỗ trợ front, back hoặc selfie.");
        }
        return norm;
    }

    private static User extractUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private static MediaType resolveMediaType(String objectKey) {
        String lower = objectKey.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

    private record ResolvedImageRecord(UUID ownerUserId, String objectKey, boolean purged, String status) {}
}
