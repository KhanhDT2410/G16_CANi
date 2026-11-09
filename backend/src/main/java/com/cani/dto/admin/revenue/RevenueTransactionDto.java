package com.cani.dto.admin.revenue;

import com.cani.entity.enums.PlanType;
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
public class RevenueTransactionDto {
    private UUID id;
    private String transactionType; // "PACKAGE_SALE" hoặc "DIRECT_PAYMENT"
    private UUID organizerId;
    private String organizerName;
    private UUID workerId;
    private String workerName;
    private UUID eventId;
    private String eventName;
    private PlanType planType;
    private String planName;
    private BigDecimal amount;
    private String status;
    private String paymentMethod;
    private String externalRef;
    private boolean isFlagged;
    private String flagReason;
    private Instant createdAt;
}

