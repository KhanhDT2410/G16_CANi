package com.cani.dto.group;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class CreateWorkerGroupRequestDto {

    @NotBlank(message = "Tên nhóm thợ không được để trống")
    @Size(max = 255, message = "Tên nhóm không được vượt quá 255 ký tự")
    private String name;

    private String description;

    @Size(max = 100, message = "Lĩnh vực kỹ năng không được vượt quá 100 ký tự")
    private String skillFocus;

    @Min(value = 2, message = "Số lượng thành viên tối thiểu là 2")
    @Builder.Default
    private Integer minSize = 5;

    @Max(value = 100, message = "Số lượng thành viên tối đa là 100")
    @Builder.Default
    private Integer maxSize = 20;
}
