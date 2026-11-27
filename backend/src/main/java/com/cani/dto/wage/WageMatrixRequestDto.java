package com.cani.dto.wage;

import com.cani.entity.enums.JobTier;
import com.cani.entity.enums.WageRegionCode;
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
public class WageMatrixRequestDto {

    @NotNull(message = "Vùng lương không được để trống")
    private WageRegionCode regionCode;

    @NotNull(message = "Nhóm bậc công việc không được để trống")
    private JobTier jobTier;

    @NotBlank(message = "Tên hiển thị nhóm công việc không được để trống")
    private String tierLabel;

    private String description;

    @NotNull(message = "Hệ số tối thiểu không được để trống")
    @DecimalMin(value = "1.00", message = "Hệ số tối thiểu phải >= 1.00")
    private BigDecimal minMultiplier;

    @NotNull(message = "Hệ số khuyên dùng không được để trống")
    @DecimalMin(value = "1.00", message = "Hệ số khuyên dùng phải >= 1.00")
    private BigDecimal recommendedMultiplier;

    @NotNull(message = "Hệ số tối đa không được để trống")
    @DecimalMin(value = "1.00", message = "Hệ số tối đa phải >= 1.00")
    private BigDecimal maxMultiplier;

    private BigDecimal overrideBaseWage;

    @Builder.Default
    private Boolean isActive = true;
}
