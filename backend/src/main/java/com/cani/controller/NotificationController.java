package com.cani.controller;

import com.cani.dto.notification.NotificationResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.exception.BadRequestException;
import com.cani.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Lấy danh sách thông báo quả chuông (In-App Notifications) của người dùng
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponseDto>>> getMyNotifications(
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        List<NotificationResponseDto> list = notificationService.getMyNotifications(userId);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách thông báo thành công"));
    }

    /**
     * Đếm số lượng thông báo chưa đọc (để hiển thị Badge đỏ trên quả chuông 🔔)
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.ok(count, "Lấy số lượng thông báo chưa đọc thành công"));
    }

    /**
     * Đánh dấu 1 thông báo là đã đọc
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable("id") UUID notificationId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã đánh dấu thông báo là đã đọc"));
    }

    /**
     * Đánh dấu tất cả thông báo là đã đọc
     */
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã đánh dấu tất cả thông báo là đã đọc"));
    }

    private UUID resolveUserId(Authentication authentication, UUID explicitUserId) {
        if (explicitUserId != null) {
            return explicitUserId;
        }
        if (authentication != null && authentication.getPrincipal() instanceof User u) {
            return u.getUserId();
        }
        if (authentication != null && authentication.getName() != null) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (Exception ignored) {}
        }
        throw new BadRequestException("Vui lòng đăng nhập hoặc cung cấp userId hợp lệ");
    }
}
