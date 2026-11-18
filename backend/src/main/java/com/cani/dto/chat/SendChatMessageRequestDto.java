package com.cani.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendChatMessageRequestDto {

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    private String messageText;

    private String attachmentUrl;

    /**
     * ID ca làm việc gắn kèm tin nhắn (nếu gửi từ trong ca làm việc; null nếu là chat tự do).
     */
    private UUID shiftId;
}
