package com.cani.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request đóng ticket từ Customer Care / Admin (SCR-26).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloseTicketRequestDto {

    @NotBlank(message = "Lý do đóng ticket không được để trống")
    @Size(min = 30, max = 1000, message = "Lý do đóng ticket phải từ 30 đến 1000 ký tự")
    private String rationale;
}

