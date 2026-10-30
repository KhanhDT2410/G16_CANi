package com.cani.dto.dispute;

import com.cani.entity.enums.DisputeOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request CC/Admin ra quyết định xử lý tranh chấp
 * (SRS UC-100: CC, UC-102: Admin final — rationale tối thiểu 100 ký tự, BR-55/BR-68).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueDecisionRequestDto {

    @NotNull(message = "Kết quả xử lý không được để trống")
    private DisputeOutcome outcome;

    @NotBlank(message = "Lý do quyết định không được để trống")
    @Size(min = 100, max = 5000, message = "Lý do quyết định phải từ 100 đến 5000 ký tự")
    private String rationale;

    /**
     * BR-55: CC có thể miễn penalty RS kèm lý do (mặc định áp dụng −10 nếu outcome = WORKER_FAULT).
     */
    @Size(max = 500, message = "Lý do miễn penalty không được vượt quá 500 ký tự")
    private String penaltyWaiverReason;
}
