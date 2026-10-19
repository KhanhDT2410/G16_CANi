package com.cani.dto.auth;

import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private String email;
    private String phone;
    private String fullName;
    private UserRole role;
    private UserStatus status;
    private String avatarUrl;
}
