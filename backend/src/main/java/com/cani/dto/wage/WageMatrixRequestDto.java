package com.cani.dto.wage;

import com.cani.entity.enums.City;
import com.cani.entity.enums.LeadTime;
import com.cani.entity.enums.TimeSlot;
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

    @NotNull(message = "Thành phố không được để trống")
    private City city;

    @NotBlank(message = "Loại công việc không được để trống")
    private String jobType;

    @NotNull(message = "Khung thời gian (TimeSlot) không được để trống")
    private TimeSlot timeSlot;

    @NotNull(message = "Thời gian chuẩn bị (LeadTime) không được để trống")
    private LeadTime leadTime;

    @NotNull(message = "Mức lương mỗi giờ không được để trống")
    @DecimalMin(value = "10000.00", message = "Mức lương tối thiểu là 10,000 VNĐ/giờ")
    private BigDecimal wagePerHour;

    @Builder.Default
    private Boolean isActive = true;
}
