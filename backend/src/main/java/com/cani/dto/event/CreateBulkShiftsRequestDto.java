package com.cani.dto.event;

import com.cani.entity.enums.ExperienceLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBulkShiftsRequestDto {

    @NotBlank(message = "Vị trí công việc (Job Type) không được để trống")
    private String jobType;

    private String description;

    @NotNull(message = "Số lượng nhân sự cần tuyển mỗi ca không được để trống")
    @Min(value = 1, message = "Số lượng nhân sự phải từ 1 người trở lên")
    @Builder.Default
    private Integer requiredWorkers = 1;

    @NotNull(message = "Mức lương/giờ không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Mức lương theo giờ phải lớn hơn 0")
    private BigDecimal hourlyWage;

    @Builder.Default
    private BigDecimal allowance = BigDecimal.ZERO;

    private String locationAddress;
    private Double locationLat;
    private Double locationLng;

    private UUID[] requiredSkills;

    private UUID performedSkillId;

    private List<ShiftSkillRequirementDto> skillRequirements;

    private String customSkillNotes;

    @Builder.Default
    private Integer minReliabilityScore = 60;

    @Builder.Default
    private Double maxDistanceKm = 15.0;

    @Builder.Default
    private ExperienceLevel minExperienceLevel = ExperienceLevel.BEGINNER;

    private String checklist;

    @NotEmpty(message = "Danh sách khung giờ không được để trống")
    @Valid
    private List<TimeSlotItem> timeSlots;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSlotItem {
        @NotNull(message = "Thời gian bắt đầu ca không được để trống")
        private Instant startTime;

        @NotNull(message = "Thời gian kết thúc ca không được để trống")
        private Instant endTime;
    }
}
