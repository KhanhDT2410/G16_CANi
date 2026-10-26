package com.cani.dto.admin.user;

import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFilterRequest {

    private String search;
    private UserRole role;
    private UserStatus status;
}
