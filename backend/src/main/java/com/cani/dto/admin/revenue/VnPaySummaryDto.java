package com.cani.dto.admin.revenue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VnPaySummaryDto {
    private long totalTransactions;
    private BigDecimal totalAmount;
    private long confirmedCount;
    private long pendingCount;
    private long flaggedCount;
    private String dataReliabilityNote;
}

