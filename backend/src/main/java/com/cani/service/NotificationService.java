package com.cani.service;

import com.cani.dto.notification.NotificationResponseDto;
import com.cani.entity.Notification;
import com.cani.entity.User;
import com.cani.entity.enums.NotificationType;
import com.cani.repository.NotificationRepository;
import com.cani.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
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
        fcmService.sendPushToUser(targetUserId, title, body, dataPayload);

        return saved;
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
