package com.cani.dto.admin.revenue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodMetricsDto {
    private Instant from;
    private Instant to;
    private BigDecimal totalPackageRevenue;
    private long totalPackageSales;
    private BigDecimal totalPaymentVolume;
    private long totalPaymentTransactions;
    private BigDecimal combinedVolume;
}

