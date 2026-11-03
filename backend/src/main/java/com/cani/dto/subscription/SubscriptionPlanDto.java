package com.cani.dto.subscription;

import com.cani.entity.enums.PlanType;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO mô tả thông tin gói dịch vụ (UC-10: View Package Plans)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlanDto {
    private PlanType planType;
    private String name;
    private BigDecimal price;
    private Integer durationDays;
    private Integer maxEvents;
    private Integer maxShifts;
    private Integer maxWorkers;
    private String description;
    private List<String> features;
    private boolean isRecommended;
}

