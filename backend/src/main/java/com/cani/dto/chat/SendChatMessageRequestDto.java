package com.cani.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendChatMessageRequestDto {

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    private String messageText;

    private String attachmentUrl;
}
