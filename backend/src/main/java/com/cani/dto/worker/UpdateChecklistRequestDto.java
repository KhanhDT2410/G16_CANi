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
public class UpdateChecklistRequestDto {

    @NotNull(message = "Danh sách checklist không được để trống")
    @Valid
    private List<ShiftChecklistItemDto> items;
}
