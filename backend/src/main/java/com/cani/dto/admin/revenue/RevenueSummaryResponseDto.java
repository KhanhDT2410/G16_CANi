package com.cani.dto.admin.revenue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueSummaryResponseDto {
    private PeriodMetricsDto currentPeriod;
    private PeriodMetricsDto previousPeriod;
    private RevenueGrowthDto growth;
    private List<PackageTypeRevenueDto> packageTypeBreakdown;
    private List<EventModeRevenueDto> eventModeBreakdown;
    private VnPaySummaryDto vnpaySummary;
    private List<RevenueTransactionDto> flaggedTransactions;
}

