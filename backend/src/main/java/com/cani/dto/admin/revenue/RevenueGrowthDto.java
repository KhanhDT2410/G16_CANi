package com.cani.dto.admin.revenue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueGrowthDto {
    private Double packageRevenueGrowthRate;
    private Double packageSalesGrowthRate;
    private Double paymentVolumeGrowthRate;
    private Double paymentTransactionsGrowthRate;
    private Double combinedVolumeGrowthRate;
}

