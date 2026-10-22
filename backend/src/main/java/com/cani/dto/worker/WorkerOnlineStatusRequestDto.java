package com.cani.dto.worker;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerOnlineStatusRequestDto {

    @NotNull(message = "Trạng thái online không được để trống")
    private Boolean isOnline;

    private Double currentLat;
    private Double currentLng;
}
