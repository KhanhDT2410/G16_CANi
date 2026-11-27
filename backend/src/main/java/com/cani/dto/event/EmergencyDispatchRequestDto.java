package com.cani.dto.event;

import com.cani.entity.enums.EmergencySkillRelaxation;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyDispatchRequestDto {

    @DecimalMin(value = "1.0", message = "Bán kính khẩn cấp tối thiểu là 1 km")
    @DecimalMax(value = "50.0", message = "Bán kính khẩn cấp tối đa là 50 km")
    private Double radiusKm;

    /** Alias tương thích với payload cũ */
    private Double expandedRadiusKm;

    private EmergencySkillRelaxation skillRelaxation;

    @Min(value = 50, message = "Điểm uy tín tối thiểu trong chế độ khẩn cấp không được thấp hơn 50")
    @Max(value = 100, message = "Điểm uy tín tối đa là 100")
    private Integer minReliabilityScore;

    @DecimalMin(value = "0.0", message = "Phụ cấp khẩn cấp không được âm")
    private BigDecimal emergencyBonus;
}
