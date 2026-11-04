package com.cani.dto.invite;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinInviteRequestDto {

    @NotBlank(message = "Mã mời không được để trống")
    private String code;
}

