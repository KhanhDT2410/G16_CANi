package com.cani.dto.notification;

import com.cani.entity.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDto {

    private UUID notificationId;
    private String title;
    private String body;
    private NotificationType type;
    private Boolean isRead;
    private String referenceType;
    private UUID referenceId;
    private String data;
    private Instant createdAt;
}
