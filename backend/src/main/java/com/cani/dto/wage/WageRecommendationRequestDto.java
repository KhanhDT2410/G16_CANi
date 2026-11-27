package com.cani.dto.wage;

import com.cani.entity.enums.City;
import com.cani.entity.enums.JobTier;
import com.cani.entity.enums.WageRegionCode;
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

    // Giữ optional để tương thích ngược với client cũ; ưu tiên dùng regionCode hoặc venueAddress + tọa độ
    private City city;

    private WageRegionCode regionCode;

    private String venueAddress;

    private Double venueLat;

    private Double venueLng;

    @NotBlank(message = "Loại công việc không được để trống")
    private String jobType;

    // Tùy chọn khi EO chủ động chọn lại nhóm tính chất công việc trên UI
    private JobTier jobTier;

    @NotNull(message = "Thời gian bắt đầu ca không được để trống")
    private Instant startTime;

    @NotNull(message = "Thời gian kết thúc ca không được để trống")
    private Instant endTime;
}
