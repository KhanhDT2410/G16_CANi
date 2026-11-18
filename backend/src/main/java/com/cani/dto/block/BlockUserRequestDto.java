package com.cani.dto.block;

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
public class BlockUserRequestDto {

    @NotNull(message = "ID người dùng cần chặn không được để trống")
    private UUID blockedUserId;

    @Size(max = 500, message = "Lý do chặn không được vượt quá 500 ký tự")
    private String reason;
}
