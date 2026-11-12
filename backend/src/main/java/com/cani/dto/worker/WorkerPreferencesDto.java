package com.cani.dto.worker;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerPreferencesDto {

    private String baseAddress;

    private Double baseLat;

    private Double baseLng;

    @DecimalMin(value = "0.0", message = "Mức lương tối thiểu không được âm")
    private BigDecimal minWage;

    @Min(value = 1, message = "Bán kính tối thiểu là 1 km")
    @Max(value = 50, message = "Bán kính tối đa là 50 km")
    private Double maxDistanceKm;
}
