package com.cani.dto.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEoReviewRequestDto {

    @NotNull(message = "Số sao đánh giá không được để trống")
    @Min(value = 1, message = "Số sao tối thiểu là 1")
    @Max(value = 5, message = "Số sao tối đa là 5")
    private Integer rating;

    private String comment;

    @Min(1) @Max(5)
    private Integer criteriaWorkCompletion;

    @Min(1) @Max(5)
    private Integer criteriaPunctuality;

    @Min(1) @Max(5)
    private Integer criteriaProfessionalism;

    private String[] tags;
}

