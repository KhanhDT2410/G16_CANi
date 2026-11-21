package com.cani.dto.event;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportNoShowRequestDto {

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String notes;
}
