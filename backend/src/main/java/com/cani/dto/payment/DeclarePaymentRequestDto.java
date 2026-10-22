package com.cani.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeclarePaymentRequestDto {

    @NotNull(message = "Assignment ID không được để trống")
    private UUID assignmentId;

    @NotNull(message = "Số tiền khai báo không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Số tiền phải lớn hơn 0")
    private BigDecimal declaredAmount;

    @NotBlank(message = "Phương thức thanh toán không được để trống (CASH, BANK_TRANSFER)")
    private String paymentMethod;

    private String externalRef;

    private String paymentProofUrl;
}
