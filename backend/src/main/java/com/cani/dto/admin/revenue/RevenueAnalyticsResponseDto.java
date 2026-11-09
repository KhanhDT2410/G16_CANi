package com.cani.dto.admin.revenue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueAnalyticsResponseDto {
    private String period; // "day", "week", "month"
    private Instant from;
    private Instant to;
    private List<RevenueAnalyticsPointDto> dataPoints;
    private BigDecimal totalPackageRevenue;
    private BigDecimal totalPaymentVolume;
    private BigDecimal totalCombinedVolume;
}

