package com.cani.dto.admin.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class AdminAdjustReliabilityRequestDto {

    @NotNull(message = "Mức thay đổi điểm (delta) không được để trống")
    @Min(value = -50, message = "Mức trừ điểm tối đa là -50 điểm")
    @Max(value = 50, message = "Mức cộng điểm tối đa là +50 điểm")
    private Integer delta;

    @NotBlank(message = "Lý do điều chỉnh không được để trống")
    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;

    @Size(max = 50, message = "Loại tham chiếu không vượt quá 50 ký tự")
    private String referenceType;

    private UUID referenceId;
}
