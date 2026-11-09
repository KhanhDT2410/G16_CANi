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
public class RevenueAnalyticsPointDto {
    private String periodLabel;
    private Instant periodStart;
    private Instant periodEnd;
    private BigDecimal packageRevenue;
    private long packageSalesCount;
    private BigDecimal paymentVolume;
    private long paymentTransactionsCount;
    private BigDecimal totalCombinedVolume;
}

