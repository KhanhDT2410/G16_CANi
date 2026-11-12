package com.cani.dto.worker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerIncomeSummaryDto {

    private BigDecimal totalEarnings;

    private BigDecimal pendingEarnings;

    private Integer completedShiftsCount;

    private BigDecimal totalHoursWorked;

    private BigDecimal avgRating;

    private Integer reliabilityScore;
}
