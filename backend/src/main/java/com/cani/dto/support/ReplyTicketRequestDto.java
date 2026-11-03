package com.cani.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request phản hồi trong ticket hỗ trợ (UC-106 / SCR-26).
 * isInternal: ghi chú nội bộ chỉ dành cho Customer Care / Admin (user gửi true sẽ bị từ chối 400).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyTicketRequestDto {

    @NotBlank(message = "Nội dung phản hồi không được để trống")
    @Size(min = 5, max = 3000, message = "Nội dung phản hồi phải từ 5 đến 3000 ký tự")
    private String body;

    private Boolean isInternal;

    @Size(max = 500, message = "URL đính kèm không được vượt quá 500 ký tự")
    private String attachmentUrl;
}

