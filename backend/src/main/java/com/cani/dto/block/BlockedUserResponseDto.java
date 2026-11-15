package com.cani.dto.block;

import com.cani.entity.enums.UserRole;
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
public class BlockedUserResponseDto {

    private UUID blockId;
    private UUID blockedUserId;
    private String fullName;
    private String email;
    private String phone;
    private UserRole role;
    private String avatarUrl;
    private String reason;
    private Instant blockedAt;
}
