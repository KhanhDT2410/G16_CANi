package com.cani.controller;

import com.cani.dto.notification.DeviceTokenRegistrationDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.service.FcmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final FcmService fcmService;

    /**
     * Đăng ký hoặc cập nhật FCM Device Token khi người dùng mở ứng dụng / đăng nhập
     */
    @PostMapping("/tokens")
    public ResponseEntity<ApiResponse<Void>> registerToken(
            @Valid @RequestBody DeviceTokenRegistrationDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        fcmService.registerDeviceToken(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đăng ký FCM Device Token thành công"));
    }

    /**
     * Hủy kích hoạt FCM Device Token khi người dùng đăng xuất (Logout)
     */
    @DeleteMapping("/tokens")
    public ResponseEntity<ApiResponse<Void>> unregisterToken(
            @RequestParam("fcmToken") String fcmToken,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        fcmService.unregisterDeviceToken(userId, fcmToken);
        return ResponseEntity.ok(ApiResponse.ok(null, "Hủy kích hoạt FCM Device Token thành công"));
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
