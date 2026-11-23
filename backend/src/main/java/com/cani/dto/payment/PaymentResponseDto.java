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
    private String eventName;
    private UUID shiftId;
    private String jobType;
    private Instant shiftStartTime;
    private Instant shiftEndTime;
    private UUID assignmentId;
    private UUID eoId;
    private String eoCompanyName;
    private UUID workerId;
    private String workerName;
    private String workerCccd;
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

    // Thông tin tài khoản ngân hàng thụ hưởng & mã VietQR của Worker
    private String bankCode;
    private String bankAccountNumber;
    private String bankAccountName;
    private String workerVietQrUrl;

    // Thông tin Tranh chấp & Kết quả phân xử (SRS FE-09 ↔ FE-11: UC-87, UC-96, UC-100, UC-102)
    private UUID disputeId;
    private String disputeStatus;
    private String disputeOutcome;
    private String disputeRationale;
    private String disputeResolution;
    private Instant disputeResolvedAt;
}
