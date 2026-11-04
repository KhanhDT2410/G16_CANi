package com.cani.dto.subscription;

import com.cani.entity.enums.PlanType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * DTO yêu cầu mua gói cước dịch vụ (UC-11: Purchase Package via VNPay)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseSubscriptionRequestDto {

    @NotNull(message = "Loại gói không được để trống (INVITE_ONLY hoặc MATCHING_PLUS)")
    private PlanType planType;

    private String returnUrl;
}

