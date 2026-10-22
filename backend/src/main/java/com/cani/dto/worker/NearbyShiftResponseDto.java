package com.cani.dto.worker;

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
public class NearbyShiftResponseDto {
    private UUID shiftId;
    private UUID eventId;
    private String eventName;
    private String jobType;
    private String description;
    private String locationAddress;
    private Double locationLat;
    private Double locationLng;
    private BigDecimal hourlyWage;
    private BigDecimal allowance;
    private Instant startTime;
    private Instant endTime;
    private Double distanceKm;
    private Integer requiredWorkers;
    private Integer confirmedWorkers;
    private Integer remainingWorkers;
}
