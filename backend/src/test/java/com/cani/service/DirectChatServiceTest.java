package com.cani.service;

import com.cani.dto.directchat.ChatConversationDetailDto;
import com.cani.dto.directchat.ChatConversationSummaryDto;
import com.cani.dto.directchat.ChatMessageDto;
import com.cani.dto.directchat.SendChatMessageDto;
import com.cani.entity.*;
import com.cani.entity.enums.UserRole;
import com.cani.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectChatServiceTest {

    @Mock
    private ChatConversationRepository conversationRepository;

    @Mock
    private ChatParticipantRepository participantRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @InjectMocks
    private DirectChatService directChatService;

    private UUID userEoId;
    private UUID userWorkerId;
    private UUID conversationId;
    private User mockEo;
    private User mockWorker;
    private ChatConversation mockConv;
    private ChatParticipant mockParticipantEo;
    private ChatParticipant mockParticipantWorker;

    @BeforeEach
    void setUp() {
        userEoId = UUID.randomUUID();
        userWorkerId = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        mockEo = User.builder()
                .userId(userEoId)
                .fullName("Trần Ban Tổ Chức")
                .email("eo@cani.vn")
                .role(UserRole.EVENT_ORGANIZER)
                .build();

        mockWorker = User.builder()
                .userId(userWorkerId)
                .fullName("Duy Hưng Phạm")
                .email("worker@gmail.com")
                .role(UserRole.WORKER)
                .build();

        mockConv = ChatConversation.builder()
                .id(conversationId)
                .type("DIRECT")
                .lastMessagePreview("Chào em")
                .lastMessageAt(Instant.now())
                .build();

        mockParticipantEo = ChatParticipant.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .userId(userEoId)
                .unreadCount(0)
                .build();

        mockParticipantWorker = ChatParticipant.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .userId(userWorkerId)
                .unreadCount(1)
                .build();
    }

    @Test
    @DisplayName("1. getOrCreateDirectConversation mở thành công phòng chat 1-1")
    void getOrCreateDirectConversation_Success() {
        when(userRepository.findById(userWorkerId)).thenReturn(Optional.of(mockWorker));
        when(participantRepository.findDirectConversationIds(userEoId, userWorkerId)).thenReturn(List.of(conversationId));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(mockConv));
        when(participantRepository.findByConversationIdAndUserId(conversationId, userEoId))
                .thenReturn(Optional.of(mockParticipantEo));
        when(participantRepository.findByConversationId(conversationId))
                .thenReturn(List.of(mockParticipantEo, mockParticipantWorker));
        when(userRepository.findById(userWorkerId)).thenReturn(Optional.of(mockWorker));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(Collections.emptyList());

        ChatConversationDetailDto result = directChatService.getOrCreateDirectConversation(userEoId, userWorkerId);

        assertThat(result).isNotNull();
        assertThat(result.getConversation().getType()).isEqualTo("DIRECT");
        assertThat(result.getConversation().getTargetUserName()).isEqualTo("Duy Hưng Phạm");
    }

    @Test
    @DisplayName("2. sendMessage lưu tin nhắn và tăng unread cho bên kia")
    void sendMessage_Success_IncrementsOtherParticipantUnread() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(mockConv));
        when(participantRepository.existsByConversationIdAndUserId(conversationId, userEoId)).thenReturn(true);
        when(userRepository.findById(userEoId)).thenReturn(Optional.of(mockEo));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });
        when(participantRepository.findByConversationId(conversationId))
                .thenReturn(List.of(mockParticipantEo, mockParticipantWorker));

        SendChatMessageDto req = SendChatMessageDto.builder()
                .messageText("Em nhớ mang theo thẻ nhé")
                .build();

        ChatMessageDto sent = directChatService.sendMessage(userEoId, conversationId, req);

        assertThat(sent).isNotNull();
        assertThat(sent.getMessageText()).isEqualTo("Em nhớ mang theo thẻ nhé");
        assertThat(sent.getSenderName()).isEqualTo("Trần Ban Tổ Chức");
        verify(participantRepository).save(mockParticipantWorker);
        assertThat(mockParticipantWorker.getUnreadCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("3. getMyConversations trả về danh sách hội thoại có đối thoại và preview")
    void getMyConversations_ReturnsList() {
        when(participantRepository.findByUserId(userEoId)).thenReturn(List.of(mockParticipantEo));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(mockConv));
        when(participantRepository.findByConversationId(conversationId))
                .thenReturn(List.of(mockParticipantEo, mockParticipantWorker));
        when(userRepository.findById(userWorkerId)).thenReturn(Optional.of(mockWorker));

        List<ChatConversationSummaryDto> list = directChatService.getMyConversations(userEoId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTargetUserName()).isEqualTo("Duy Hưng Phạm");
        assertThat(list.get(0).getLastMessagePreview()).isEqualTo("Chào em");
    }
}
