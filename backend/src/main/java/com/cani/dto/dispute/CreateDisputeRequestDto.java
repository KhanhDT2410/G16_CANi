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

/**
 * Request nộp tranh chấp (SRS UC-96, BR-52: cửa sổ 14 ngày kể từ kết thúc ca,
 * mô tả tối thiểu 50 ký tự).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDisputeRequestDto {

    @NotNull(message = "ID ca làm việc (assignment) không được để trống")
    private UUID assignmentId;

    @NotNull(message = "Danh mục tranh chấp không được để trống")
    private DisputeType type;

    @NotBlank(message = "Tiêu đề tranh chấp không được để trống")
    @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
    private String title;

    @NotBlank(message = "Mô tả tranh chấp không được để trống")
    @Size(min = 50, max = 3000, message = "Mô tả tranh chấp phải từ 50 đến 3000 ký tự (BR-52)")
    private String description;
}
