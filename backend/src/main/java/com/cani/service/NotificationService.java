package com.cani.service;

import com.cani.dto.notification.NotificationResponseDto;
import com.cani.entity.Notification;
import com.cani.entity.User;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.NotificationRepository;
import com.cani.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FcmService fcmService;

    /**
     * Tạo thông báo lưu vào Database (In-App) và đồng thời bắn Push Notification (FCM) đến điện thoại
     */
    @Transactional
    public Notification createAndSend(
            UUID targetUserId,
            String title,
            String body,
            NotificationType type,
            String referenceType,
            UUID referenceId,
            Map<String, String> dataPayload
    ) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetUserId));

        // 1. Lưu vào Database (Kênh In-App Notification vĩnh viễn)
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .body(body)
                .type(type)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .isRead(false)
                .data(dataPayload != null ? dataPayload.toString() : null)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("[NotificationService] In-App Notification saved for user {}: {}", targetUserId, title);

        // 2. Bắn Push Notification qua Firebase (Kênh tức thời - Lockscreen)
        if (fcmService != null) {
            fcmService.sendPushToUser(targetUserId, title, body, dataPayload);
        }

        return saved;
    }

    /**
     * Gửi thông báo đến 1 User (Lưu DB In-App và bắn FCM)
     */
    @Transactional
    public Notification sendNotification(User user, String title, String body, NotificationType type, String refType, UUID refId) {
        if (user == null) {
            log.warn("Bỏ qua gửi notification vì user null [title={}]", title);
            return null;
        }
        try {
            Notification notification = Notification.builder()
                    .user(user)
                    .title(title)
                    .body(body)
                    .type(type)
                    .referenceType(refType)
                    .referenceId(refId)
                    .isRead(false)
                    .build();

            Notification saved = notificationRepository.save(notification);
            log.info("[NotificationService] In-App Notification saved for user {}: {}", user.getUserId(), title);

            if (fcmService != null && user.getUserId() != null) {
                try {
                    fcmService.sendPushToUser(user.getUserId(), title, body, null);
                } catch (Exception ex) {
                    log.warn("Lỗi khi gửi FCM push cho user {}: {}", user.getUserId(), ex.getMessage());
                }
            }

            return saved;
        } catch (Exception e) {
            log.error("Lỗi khi lưu thông báo cho user {}: {}", user.getUserId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gửi thông báo đến User theo UUID
     */
    @Transactional
    public Notification sendNotification(UUID userId, String title, String body, NotificationType type, String refType, UUID refId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(user -> sendNotification(user, title, body, type, refType, refId))
                .orElse(null);
    }

    /**
     * Gửi thông báo đến tất cả User thuộc một Role cụ thể đang ACTIVE
     */
    @Transactional
    public void notifyRole(UserRole role, String title, String body, NotificationType type, String refType, UUID refId) {
        try {
            List<User> targetUsers = userRepository.findByRoleAndStatus(role, UserStatus.ACTIVE);
            for (User u : targetUsers) {
                sendNotification(u, title, body, type, refType, refId);
            }
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo đến nhóm role {}: {}", role, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getMyNotifications(UUID userId) {
        return notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUser_UserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(UUID userId, UUID notificationId) {
        notificationRepository.markAsRead(notificationId, userId);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
    }

    private NotificationResponseDto toDto(Notification n) {
        return NotificationResponseDto.builder()
                .notificationId(n.getNotificationId())
                .title(n.getTitle())
                .body(n.getBody())
                .type(n.getType())
                .isRead(n.getIsRead())
                .referenceType(n.getReferenceType())
                .referenceId(n.getReferenceId())
                .data(n.getData())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
