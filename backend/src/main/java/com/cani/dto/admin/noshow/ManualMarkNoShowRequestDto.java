package com.cani.dto.admin.noshow;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualMarkNoShowRequestDto {

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String notes;
}

