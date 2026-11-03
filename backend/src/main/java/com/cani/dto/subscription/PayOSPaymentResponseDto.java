package com.cani.dto.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayOSPaymentResponseDto {

    private Long orderCode;
    private Integer amount;
    private String description;
    private String accountNumber;
    private String accountName;
    private String bin;
    private String checkoutUrl;
    private String qrCode;
    private String status;
}

