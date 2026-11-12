package com.cani.controller;

import com.cani.dto.notification.DeviceTokenRegistrationDto;
import com.cani.dto.response.ApiResponse;
import com.cani.security.SecurityUtils;
import com.cani.service.FcmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        fcmService.registerDeviceToken(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đăng ký FCM Device Token thành công"));
    }

    /**
     * Hủy kích hoạt FCM Device Token khi người dùng đăng xuất (Logout)
     */
    @DeleteMapping("/tokens")
    public ResponseEntity<ApiResponse<Void>> unregisterToken(
            @RequestParam("fcmToken") String fcmToken,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        fcmService.unregisterDeviceToken(userId, fcmToken);
        return ResponseEntity.ok(ApiResponse.ok(null, "Hủy kích hoạt FCM Device Token thành công"));
    }
}
