package com.cani.dto.event;

import com.cani.entity.enums.ExperienceLevel;
import com.cani.entity.enums.ShiftStatus;
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
public class ShiftResponseDto {
    private UUID shiftId;
    private UUID eventId;
    private String jobType;
    private String description;
    private Instant startTime;
    private Instant endTime;
    private Integer requiredWorkers;
    private Integer confirmedWorkers;
    private Integer remainingWorkers;
    private BigDecimal hourlyWage;
    private BigDecimal allowance;
    private BigDecimal totalCost;
    private ShiftStatus status;
    private Integer riskScore;
    private String locationAddress;
    private Double locationLat;
    private Double locationLng;
    private Integer minReliabilityScore;
    private ExperienceLevel minExperienceLevel;
    private String dressCode;
}
