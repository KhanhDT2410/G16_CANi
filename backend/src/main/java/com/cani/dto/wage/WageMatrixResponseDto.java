package com.cani.dto.wage;

import com.cani.entity.enums.City;
import com.cani.entity.enums.LeadTime;
import com.cani.entity.enums.TimeSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WageMatrixResponseDto {

    private UUID matrixId;
    private City city;
    private String jobType;
    private TimeSlot timeSlot;
    private LeadTime leadTime;
    private BigDecimal wagePerHour;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
