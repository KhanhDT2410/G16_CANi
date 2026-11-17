package com.cani.dto.group;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteGroupMemberRequestDto {

    @NotNull(message = "ID Worker cần mời không được để trống")
    private UUID workerId;

    @Size(max = 100, message = "Chức danh trong nhóm không vượt quá 100 ký tự")
    private String roleTitle;
}
