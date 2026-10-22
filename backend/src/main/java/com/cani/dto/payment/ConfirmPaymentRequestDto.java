package com.cani.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmPaymentRequestDto {

    /**
     * RECEIVED_FULL, RECEIVED_PARTIAL, NOT_RECEIVED
     */
    @NotBlank(message = "Trạng thái xác nhận không được để trống")
    private String confirmStatus;

    private String comment;
}
