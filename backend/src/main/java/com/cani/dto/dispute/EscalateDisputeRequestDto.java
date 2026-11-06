package com.cani.dto.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request khiếu nại (escalate) quyết định của Customer Care lên Admin
 * (SRS UC-101, BR-54: trong vòng 7 ngày kể từ quyết định của CC).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscalateDisputeRequestDto {

    @NotBlank(message = "Lý do khiếu nại không được để trống")
    @Size(max = 2000, message = "Lý do khiếu nại không được vượt quá 2000 ký tự")
    private String reason;
}
