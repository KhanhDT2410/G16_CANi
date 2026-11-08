package com.cani.service;

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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

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
            return notificationRepository.save(notification);
        } catch (Exception e) {
            log.error("Lỗi khi lưu thông báo cho user {}: {}", user.getUserId(), e.getMessage(), e);
            return null;
        }
    }

    @Transactional
    public Notification sendNotification(UUID userId, String title, String body, NotificationType type, String refType, UUID refId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(user -> sendNotification(user, title, body, type, refType, refId))
                .orElse(null);
    }

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
}

