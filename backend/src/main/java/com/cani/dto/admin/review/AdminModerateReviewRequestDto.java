package com.cani.dto.admin.review;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminModerateReviewRequestDto {

    @NotBlank(message = "Lý do kiểm duyệt không được để trống")
    private String reason;
}

