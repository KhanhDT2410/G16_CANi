package com.cani.dto.worker.appeal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitRsAppealRequestDto {

    @NotNull(message = "ID bản ghi trừ điểm không được để trống")
    private UUID reliabilityLogId;

    @NotBlank(message = "Mã lý do kháng cáo không được để trống")
    private String reasonCode;

    @NotBlank(message = "Chi tiết lý do kháng cáo không được để trống")
    @Size(min = 10, max = 2000, message = "Chi tiết lý do kháng cáo phải từ 10 đến 2000 ký tự")
    private String reasonDetail;

    private String evidence;
}

