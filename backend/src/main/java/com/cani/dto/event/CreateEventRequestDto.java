package com.cani.dto.event;

import com.cani.entity.enums.EventMode;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequestDto {

    @NotBlank(message = "Tên sự kiện không được để trống")
    private String name;

    private String description;

    @NotBlank(message = "Địa điểm tổ chức không được để trống")
    private String venueAddress;

    private Double venueLat;
    private Double venueLng;

    private LocalDate eventDate;

    @Builder.Default
    private EventMode mode = EventMode.MATCHING;
}
