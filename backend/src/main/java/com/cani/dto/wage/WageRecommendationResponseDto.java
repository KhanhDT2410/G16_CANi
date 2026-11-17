package com.cani.dto.wage;

import com.cani.entity.enums.City;
import com.cani.entity.enums.LeadTime;
import com.cani.entity.enums.TimeSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WageRecommendationResponseDto {

    private UUID recommendationId;
    private UUID shiftId;
    private City city;
    private String jobType;
    private TimeSlot timeSlot;
    private LeadTime leadTime;
    private BigDecimal suggestedWage;
    private BigDecimal minWage;
    private BigDecimal maxWage;
    private String explanation;
    private UUID matchedRowId;
    private Integer fallbackLevel;
}
