package com.cani.dto.admin;

import com.cani.entity.enums.PlanType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPlanRequestDto {

    @NotBlank(message = "Tên gói cước không được để trống")
    private String planName;

    @NotNull(message = "Loại gói không được để trống")
    private PlanType planType;

    @NotNull(message = "Giá tiền không được để trống")
    @Min(value = 0, message = "Giá tiền không được âm")
    private BigDecimal price;

    @NotNull(message = "Thời hạn gói (ngày) không được để trống")
    @Min(value = 1, message = "Thời hạn tối thiểu là 1 ngày")
    private Integer durationDays;

    @NotNull(message = "Hạn mức sự kiện không được để trống")
    @Min(value = 1, message = "Hạn mức tối thiểu là 1 sự kiện")
    private Integer maxEvents;

    @Min(value = 0, message = "Hạn mức ca làm việc không được âm")
    private Integer maxShifts;

    @Min(value = 0, message = "Hạn mức nhân sự không được âm")
    private Integer maxWorkers;

    @Min(value = 0, message = "Hạn mức Matching cơ bản không được âm")
    private Integer baseMatchingQuota;

    private List<String> features;
}
