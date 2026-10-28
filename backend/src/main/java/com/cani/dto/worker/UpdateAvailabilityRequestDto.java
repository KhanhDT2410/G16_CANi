package com.cani.dto.worker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAvailabilityRequestDto {

    @NotNull(message = "Danh sách lịch rảnh không được để trống")
    @Valid
    private List<WorkerAvailabilityDto> schedules;
}
