package com.cani.dto.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCustomSkillRequestDto {

    @NotBlank(message = "Tên kỹ năng không được để trống")
    @Size(max = 100, message = "Tên kỹ năng không được vượt quá 100 ký tự")
    private String name;

    @Size(max = 100, message = "Danh mục kỹ năng không được vượt quá 100 ký tự")
    private String category;
}
