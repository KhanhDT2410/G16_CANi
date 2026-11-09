package com.cani.dto.admin.appeal;

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
public class ReviewRsAppealRequestDto {

    @NotNull(message = "Quyết định phê duyệt (approved) không được để trống")
    private Boolean approved;

    @NotBlank(message = "Ghi chú giải trình quyết định không được để trống")
    @Size(min = 5, max = 2000, message = "Ghi chú quyết định phải từ 5 đến 2000 ký tự")
    private String decisionNote;
}

