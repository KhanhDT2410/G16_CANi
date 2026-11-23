package com.cani.dto.admin.shift;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminShiftActionRequestDto {

    @NotBlank(message = "Lý do hoặc nội dung xử lý không được để trống")
    @Size(min = 5, max = 500, message = "Nội dung phải từ 5 đến 500 ký tự")
    private String reason;
}
