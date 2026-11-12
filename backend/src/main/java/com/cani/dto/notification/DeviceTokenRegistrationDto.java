package com.cani.dto.notification;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenRegistrationDto {

    @NotBlank(message = "FCM token is required")
    private String fcmToken;

    @NotBlank(message = "Device type is required (ANDROID, IOS, WEB)")
    private String deviceType;

    private String deviceName;

    private String appVersion;
}
