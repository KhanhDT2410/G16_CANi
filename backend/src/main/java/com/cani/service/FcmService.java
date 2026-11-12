package com.cani.service;

import com.cani.dto.notification.DeviceTokenRegistrationDto;
import com.cani.entity.User;
import com.cani.entity.UserDeviceToken;
import com.cani.repository.UserDeviceTokenRepository;
import com.cani.repository.UserRepository;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FcmService {

    private final UserDeviceTokenRepository tokenRepository;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    /**
     * Đăng ký hoặc làm mới Device Token khi người dùng mở app hoặc đăng nhập
     */
    @Transactional
    public void registerDeviceToken(UUID userId, DeviceTokenRegistrationDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Optional<UserDeviceToken> existingOpt = tokenRepository.findByFcmToken(dto.getFcmToken());
        if (existingOpt.isPresent()) {
            UserDeviceToken existing = existingOpt.get();
            existing.setUser(user);
            existing.setDeviceType(dto.getDeviceType().toUpperCase());
            existing.setDeviceName(dto.getDeviceName());
            existing.setAppVersion(dto.getAppVersion());
            existing.setIsActive(true);
            existing.setLastActiveAt(Instant.now());
            tokenRepository.save(existing);
            log.info("[FcmService] Refreshed active device token for user: {}", userId);
        } else {
            UserDeviceToken newToken = UserDeviceToken.builder()
                    .user(user)
                    .fcmToken(dto.getFcmToken())
                    .deviceType(dto.getDeviceType().toUpperCase())
                    .deviceName(dto.getDeviceName())
                    .appVersion(dto.getAppVersion())
                    .isActive(true)
                    .lastActiveAt(Instant.now())
                    .build();
            tokenRepository.save(newToken);
            log.info("[FcmService] Registered new device token for user: {}", userId);
        }

        // Đồng bộ trường fcm_token trên bảng users để tương thích ngược
        user.setFcmToken(dto.getFcmToken());
        userRepository.save(user);
    }

    /**
     * Vô hiệu hóa token khi người dùng đăng xuất (Logout)
     */
    @Transactional
    public void unregisterDeviceToken(UUID userId, String fcmToken) {
        tokenRepository.deactivateTokenForUser(userId, fcmToken);
        log.info("[FcmService] Deactivated FCM token for user: {}", userId);
    }

    /**
     * Gửi Push Notification thời gian thực đến tất cả các thiết bị hoạt động của User
     */
    public void sendPushToUser(UUID userId, String title, String body, Map<String, String> dataPayload) {
        if (firebaseMessaging == null) {
            log.warn("[FcmService] FirebaseMessaging is null. Skipping push for user: {}", userId);
            return;
        }

        List<UserDeviceToken> activeTokens = tokenRepository.findByUser_UserIdAndIsActiveTrue(userId);
        if (activeTokens.isEmpty()) {
            log.debug("[FcmService] No active device tokens found for user: {}", userId);
            return;
        }

        List<String> tokens = activeTokens.stream()
                .map(UserDeviceToken::getFcmToken)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (tokens.isEmpty()) {
            return;
        }

        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(notification);

        if (dataPayload != null && !dataPayload.isEmpty()) {
            messageBuilder.putAllData(dataPayload);
        }

        MulticastMessage message = messageBuilder.build();

        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            log.info("[FcmService] Successfully dispatched push to user {}: {} success, {} failure",
                    userId, response.getSuccessCount(), response.getFailureCount());

            // Tự động thu hồi token đã bị Firebase khai tử (UNREGISTERED)
            if (response.getFailureCount() > 0) {
                for (int i = 0; i < response.getResponses().size(); i++) {
                    SendResponse sendResponse = response.getResponses().get(i);
                    if (!sendResponse.isSuccessful()) {
                        FirebaseMessagingException ex = sendResponse.getException();
                        if (ex != null && ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                            String staleToken = tokens.get(i);
                            log.info("[FcmService] Token unregistered by Firebase, deactivating: {}", staleToken);
                            tokenRepository.deactivateToken(staleToken);
                        }
                    }
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("[FcmService] Error while sending multicast FCM message: {}", e.getMessage(), e);
        }
    }
}
