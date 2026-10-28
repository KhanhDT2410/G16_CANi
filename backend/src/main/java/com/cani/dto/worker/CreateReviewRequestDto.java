package com.cani.dto.worker;

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
public class CreateReviewRequestDto {

    @NotNull(message = "Số sao đánh giá không được để trống")
    @Min(value = 1, message = "Số sao tối thiểu là 1")
    @Max(value = 5, message = "Số sao tối đa là 5")
    private Integer rating;

    private String comment;

    @Min(value = 1, message = "Điểm tiêu chí tối thiểu là 1")
    @Max(value = 5, message = "Điểm tiêu chí tối đa là 5")
    private Integer criteriaWorkEnvironment;

    @Min(value = 1, message = "Điểm tiêu chí tối thiểu là 1")
    @Max(value = 5, message = "Điểm tiêu chí tối đa là 5")
    private Integer criteriaSupport;

    @Min(value = 1, message = "Điểm tiêu chí tối thiểu là 1")
    @Max(value = 5, message = "Điểm tiêu chí tối đa là 5")
    private Integer criteriaPaymentTiming;

    private String[] tags;
}
