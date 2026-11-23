package com.cani.dto.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendDisputeChatMessageDto {
    @NotNull(message = "Bên nhận không được để trống (WORKER hoặc EO)")
    private String targetParty;

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự")
    private String messageText;

    @Size(max = 500, message = "Đường dẫn đính kèm không được vượt quá 500 ký tự")
    private String attachmentUrl;
}
