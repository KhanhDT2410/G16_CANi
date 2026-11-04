package com.cani.dto.subscription;

import com.cani.entity.enums.PlanType;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO trả về URL phiên thanh toán VNPay và thông tin giao dịch (UC-11)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseSubscriptionResponseDto {
    private String paymentUrl;
    private String txnRef;
    private PlanType planType;
    private String planName;
    private BigDecimal amount;
    private String orderInfo;
    private boolean isDemo;
}

