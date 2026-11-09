package com.cani.dto.admin.revenue;

import com.cani.entity.enums.EventMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventModeRevenueDto {
    private EventMode mode;
    private String modeName;
    private long eventCount;
    private BigDecimal totalPaymentAmount;
    private long transactionCount;
}

