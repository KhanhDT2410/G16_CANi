package com.cani.dto.profile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload cập nhật hồ sơ cá nhân của chính Worker.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkerProfileRequestDto {

    @Size(max = 120, message = "Họ tên không được vượt quá 120 ký tự")
    private String fullName;

    @Size(max = 1000, message = "Giới thiệu bản thân tối đa 1000 ký tự")
    private String bio;

    @Min(value = 0, message = "Số năm kinh nghiệm phải >= 0")
    @Max(value = 60, message = "Số năm kinh nghiệm tối đa 60 năm")
    private Integer yearsExperience;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String baseAddress;

    @Size(max = 500, message = "Đường dẫn ảnh đại diện tối đa 500 ký tự")
    private String avatarUrl;
}
