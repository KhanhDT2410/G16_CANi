package com.cani.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportConversationDetailDto {
    private SupportConversationSummaryDto conversation;
    private List<ChatMessageResponseDto> messages;
}
