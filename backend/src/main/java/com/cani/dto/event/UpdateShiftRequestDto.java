package com.cani.dto.event;

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
import java.util.List;
import java.util.UUID;

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

    /** Giờ mở cửa điểm danh (check-in gate); null -> mặc định = startTime */
    private Instant arrivalTime;

    private UUID[] requiredSkills;

    private UUID performedSkillId;

    private List<ShiftSkillRequirementDto> skillRequirements;

    private String customSkillNotes;

    @NotNull(message = "Số lượng nhân sự không được để trống")
    @Min(value = 1, message = "Cần tối thiểu 1 nhân sự")
    private Integer requiredWorkers;

    @NotNull(message = "Mức lương theo giờ không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Mức lương phải lớn hơn 0")
    private BigDecimal hourlyWage;

    private BigDecimal allowance;

    private Integer minReliabilityScore;

    private Double maxDistanceKm;

    private String dressCode;

    private String imageUrl;

    private String locationAddress;

    private Double locationLat;

    private Double locationLng;

    private String checklist;
}
