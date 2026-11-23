package com.cani.dto.ekyc;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EkycLegalHoldRequestDto {

    @NotNull(message = "Trạng thái legalHold không được để trống")
    private Boolean legalHold;

    private String reason;
}
