package com.cani.dto.worker;

import com.cani.entity.enums.SkillTier;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerExperienceDto {

    @Min(value = 0, message = "Số năm kinh nghiệm không thể là số âm")
    @Max(value = 50, message = "Số năm kinh nghiệm tối đa là 50")
    private Integer yearsExperience;

    @Size(max = 2000, message = "Giới thiệu bản thân và kinh nghiệm tối đa 2000 ký tự")
    private String bio;

    private Integer totalShiftsCompleted;

    private SkillTier skillTier;

    private BigDecimal ratingAvg;

    private Integer reliabilityScore;
}
