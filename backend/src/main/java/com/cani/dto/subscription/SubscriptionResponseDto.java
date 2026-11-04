package com.cani.dto.subscription;

import com.cani.entity.enums.PlanType;
import com.cani.entity.enums.SubscriptionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO trả về thông tin Gói cước và Hạn mức Quota còn lại của EO (UC-10, UC-12, BR-08)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponseDto {
    private UUID subscriptionId;
    private UUID eoId;
    private String planName;
    private PlanType planType;
    private BigDecimal price;
    private Integer durationDays;
    private Integer maxEvents;
    private Integer eventsUsed;
    private Integer remainingEvents;
    private Integer maxShifts;
    private Integer shiftsUsed;
    private Integer remainingShifts;
    private Integer maxWorkers;
    private SubscriptionStatus status;
    private Instant startsAt;
    private Instant expiresAt;
    private boolean isExpired;
    private String features;
    private Instant createdAt;
}

