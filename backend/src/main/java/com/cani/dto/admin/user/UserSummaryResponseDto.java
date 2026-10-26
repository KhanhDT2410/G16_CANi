package com.cani.dto.admin.user;

import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
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
public class UserSummaryResponseDto {

    private UUID id;
    private String email;
    private String phone;
    private String fullName;
    private UserRole role;
    private UserStatus status;
    private String avatarUrl;
    private Boolean emailVerified;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;
}
