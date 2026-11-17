package com.cani.dto.wage;

import com.cani.entity.enums.City;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WageRecommendationRequestDto {

    private UUID shiftId;

    @NotNull(message = "Thành phố không được để trống")
    private City city;

    @NotBlank(message = "Loại công việc không được để trống")
    private String jobType;

    @NotNull(message = "Thời gian bắt đầu ca không được để trống")
    private Instant startTime;

    @NotNull(message = "Thời gian kết thúc ca không được để trống")
    private Instant endTime;
}
