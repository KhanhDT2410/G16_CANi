package com.cani.dto.event;

import com.cani.entity.enums.EventMode;
import com.cani.entity.enums.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponseDto {
    private UUID eventId;
    private String name;
    private String description;
    private String venueAddress;
    private Double venueLat;
    private Double venueLng;
    private LocalDate eventDate;
    private EventMode mode;
    private EventStatus status;
    private Integer readinessScore;
    private Integer totalShifts;
    private Integer totalRequiredWorkers;
    private Integer totalConfirmedWorkers;
    private BigDecimal totalEstimatedCost;
    private BigDecimal totalPaid;
    private Instant createdAt;
    private List<ShiftResponseDto> shifts;
}
