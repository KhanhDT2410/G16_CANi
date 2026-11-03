package com.cani.dto.dispute;

import com.cani.entity.enums.DisputeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDisputeRequestDto {

    @NotNull(message = "Mã phân công ca làm (assignmentId) không được để trống")
    private UUID assignmentId;

    @NotNull(message = "Loại khiếu nại không được để trống")
    private DisputeType type;

    @NotBlank(message = "Tiêu đề khiếu nại không được để trống")
    @Size(max = 200, message = "Tiêu đề không quá 200 ký tự")
    private String title;

    @NotBlank(message = "Nội dung mô tả chi tiết không được để trống")
    private String description;

    private String evidence;
}

