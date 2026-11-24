package com.cani.dto.admin.disputerule;

import com.cani.entity.enums.DisputeOutcome;
import com.cani.entity.enums.DisputeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDisputeRuleRequestDto {

    @NotBlank(message = "Mã quy tắc không được để trống")
    @Size(max = 50, message = "Mã quy tắc tối đa 50 ký tự")
    private String code;

    @NotBlank(message = "Mô tả hành vi không được để trống")
    private String behavior;

    @NotBlank(message = "Bên vi phạm không được để trống (WORKER, EO, BOTH)")
    private String violatingParty;

    @NotBlank(message = "Hình thức chế tài không được để trống")
    private String sanctionForm;

    @NotBlank(message = "Chế tài cụ thể không được để trống")
    @Size(min = 10, message = "Chế tài cụ thể tối thiểu 10 ký tự")
    private String specificPenalty;

    @NotNull(message = "Loại tranh chấp (ruleType) không được để trống")
    private DisputeType ruleType;

    @NotNull(message = "Kết quả phán quyết (faultOutcome) không được để trống")
    private DisputeOutcome faultOutcome;

    private Integer repeatWindowDays;

    @Builder.Default
    private Integer repeatIndex = 1;

    @NotBlank(message = "Nội dung chế tài hiển thị không được để trống")
    @Size(min = 10, message = "Nội dung chế tài tối thiểu 10 ký tự")
    private String sanctionText;

    private String adminActionText;

    @Builder.Default
    private Boolean enabled = true;
}
