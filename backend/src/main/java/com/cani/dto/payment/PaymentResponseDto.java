package com.cani.dto.payment;

import com.cani.entity.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDto {

    private UUID paymentId;
    private UUID eventId;
    private UUID shiftId;
    private UUID assignmentId;
    private UUID eoId;
    private String eoCompanyName;
    private UUID workerId;
    private String workerName;
    private BigDecimal agreedAmount;
    private BigDecimal declaredAmount;
    private PaymentStatus status;
    private String paymentMethod;
    private Instant declaredAt;
    private String externalRef;
    private String paymentProofUrl;
    private Instant workerConfirmedAt;
    private String workerConfirmStatus;
    private String workerComment;
    private Instant createdAt;
}
