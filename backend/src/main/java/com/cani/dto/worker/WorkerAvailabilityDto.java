package com.cani.dto.worker;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerAvailabilityDto {

    @NotNull(message = "Thứ trong tuần không được để trống (0: Chủ Nhật, 1: Thứ 2, ..., 6: Thứ 7)")
    @Min(value = 0, message = "dayOfWeek tối thiểu là 0 (Chủ Nhật)")
    @Max(value = 6, message = "dayOfWeek tối đa là 6 (Thứ Bảy)")
    private Integer dayOfWeek;

    @NotNull(message = "Giờ bắt đầu không được để trống")
    @JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime startTime;

    @NotNull(message = "Giờ kết thúc không được để trống")
    @JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime endTime;

    @Builder.Default
    private Boolean isAvailable = true;
}
