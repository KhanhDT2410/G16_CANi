package com.cani.dto.event;

import com.cani.entity.enums.ExperienceLevel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateShiftRequestDto {

    @NotBlank(message = "Vị trí công việc không được để trống")
    private String jobType;

    private String description;

    @NotNull(message = "Thời gian bắt đầu ca không được để trống")
    private Instant startTime;

    @NotNull(message = "Thời gian kết thúc ca không được để trống")
    private Instant endTime;

    @NotNull(message = "Số lượng nhân sự không được để trống")
    @Min(value = 1, message = "Cần tối thiểu 1 nhân sự")
    private Integer requiredWorkers;

    @NotNull(message = "Mức lương theo giờ không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Mức lương phải lớn hơn 0")
    private BigDecimal hourlyWage;

    private BigDecimal allowance;

    private Integer minReliabilityScore;

    private Double maxDistanceKm;

    private ExperienceLevel minExperienceLevel;

    private String dressCode;

    private String locationAddress;

    private Double locationLat;

    private Double locationLng;
}
