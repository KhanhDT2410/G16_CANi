package com.cani.dto.admin.user;

import com.cani.entity.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequestDto {

    @NotNull(message = "Trạng thái không được để trống")
    private UserStatus status;

    @NotBlank(message = "Lý do thay đổi trạng thái không được để trống")
    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;
}
