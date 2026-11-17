package com.cani.dto.group;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkerGroupRequestDto {

    @Size(max = 255, message = "Tên nhóm không được vượt quá 255 ký tự")
    private String name;

    private String description;

    @Size(max = 100, message = "Lĩnh vực kỹ năng không được vượt quá 100 ký tự")
    private String skillFocus;

    @Min(value = 2, message = "Số lượng thành viên tối thiểu là 2")
    private Integer minSize;

    @Max(value = 100, message = "Số lượng thành viên tối đa là 100")
    private Integer maxSize;

    private Boolean isActive;
}
