package com.cani.dto.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminSkillRequestDto {

    @NotBlank(message = "Tên kỹ năng không được để trống")
    @Size(max = 100, message = "Tên kỹ năng không được vượt quá 100 ký tự")
    private String name;

    @Size(max = 100, message = "Danh mục không được vượt quá 100 ký tự")
    private String category;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    @Builder.Default
    private Boolean isActive = true;
}
