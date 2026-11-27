package com.cani.dto.wage;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWageRegionRequestDto {

    @NotBlank(message = "Tên vùng không được để trống")
    private String name;

    @NotNull(message = "Sàn lương tối thiểu giờ không được để trống")
    @DecimalMin(value = "10000.00", message = "Sàn lương tối thiểu phải từ 10,000 VNĐ/giờ")
    private BigDecimal legalFloorHourly;

    @NotNull(message = "Hệ số thị trường không được để trống")
    @DecimalMin(value = "0.50", message = "Hệ số thị trường tối thiểu là 0.50")
    private BigDecimal marketMultiplier;

    private String description;

    @Builder.Default
    private Boolean isActive = true;
}
