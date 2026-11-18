package com.cani.dto.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportIncompleteWorkRequestDto {

    @NotBlank(message = "Lý do báo cáo công việc không đạt yêu cầu không được để trống")
    @Size(min = 20, max = 3000, message = "Lý do phải có độ dài từ 20 đến 3000 ký tự")
    private String reason;

    private List<String> proofUrls;

    private List<String> incompleteChecklistItems;
}
