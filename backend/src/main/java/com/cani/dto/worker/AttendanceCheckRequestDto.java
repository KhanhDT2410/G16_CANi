package com.cani.dto.worker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCheckRequestDto {
    private String qrContent;
    private Double lat;
    private Double lng;
}
