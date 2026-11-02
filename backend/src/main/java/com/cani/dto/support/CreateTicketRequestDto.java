package com.cani.dto.support;

import com.cani.entity.enums.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request nộp ticket hỗ trợ (UC-105, BR/MSG151: mô tả tối thiểu 30 ký tự).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketRequestDto {

    @NotNull(message = "Danh mục hỗ trợ không được để trống")
    private TicketCategory category;

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
    private String subject;

    @NotBlank(message = "Mô tả không được để trống")
    @Size(min = 30, max = 3000, message = "Mô tả yêu cầu hỗ trợ phải từ 30 đến 3000 ký tự (MSG151)")
    private String description;

    private List<String> attachments;
}

