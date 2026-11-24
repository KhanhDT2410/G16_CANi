package com.cani.dto.support;

import com.cani.entity.enums.TicketCategory;
import com.cani.entity.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO yêu cầu leo thang (Escalate) từ phiên Live Chat thành Phiếu hỗ trợ SLA (Support Ticket).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscalateChatToTicketRequestDto {

    @NotNull(message = "Danh mục ticket không được để trống")
    private TicketCategory category;

    @NotNull(message = "Mức độ ưu tiên không được để trống")
    private TicketPriority priority;

    @NotBlank(message = "Tiêu đề ticket không được để trống")
    @Size(max = 200, message = "Tiêu đề không vượt quá 200 ký tự")
    private String subject;

    /**
     * Mô tả tùy chọn từ nhân viên CSKH (nếu để trống, hệ thống tự động trích xuất các tin nhắn gần nhất)
     */
    private String customDescription;
}
