package com.cani.service;

import com.cani.dto.chat.ChatMessageResponseDto;
import com.cani.dto.chat.SendChatMessageRequestDto;
import com.cani.dto.chat.StaffChatUnreadSummaryDto;
import com.cani.dto.chat.SupportConversationDetailDto;
import com.cani.dto.chat.SupportConversationSummaryDto;
import com.cani.entity.SupportConversation;
import com.cani.entity.SupportMessage;
import com.cani.entity.User;
import com.cani.entity.enums.UserRole;
import com.cani.repository.SupportConversationRepository;
import com.cani.repository.SupportMessageRepository;
import com.cani.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportChatServiceTest {

        @Mock
        private SupportConversationRepository conversationRepository;

        @Mock
        private SupportMessageRepository messageRepository;

        @Mock
        private UserRepository userRepository;

        @InjectMocks
        private SupportChatService supportChatService;

        private UUID userId;
        private UUID staffId;
        private UUID conversationId;
        private User mockUser;
        private User mockStaff;
        private SupportConversation mockConv;

        @BeforeEach
        void setUp() {
                userId = UUID.randomUUID();
                staffId = UUID.randomUUID();
                conversationId = UUID.randomUUID();

                mockUser = User.builder()
                                .userId(userId)
                                .fullName("Nguyễn Văn An")
                                .email("an@gmail.com")
                                .phone("0987654321")
                                .role(UserRole.WORKER)
                                .build();

                mockStaff = User.builder()
                                .userId(staffId)
                                .fullName("Lê CSKH")
                                .email("cskh@cani.vn")
                                .role(UserRole.CUSTOMER_CARE)
                                .build();

                mockConv = SupportConversation.builder()
                                .id(conversationId)
                                .userId(userId)
                                .status("ACTIVE")
                                .lastMessagePreview("Chào admin")
                                .lastMessageAt(Instant.now())
                                .unreadStaffCount(1)
                                .unreadUserCount(0)
                                .build();
        }

        @Test
        @DisplayName("1. getMyConversation khi đã có hội thoại trả về chi tiết hội thoại và tin nhắn")
        void getMyConversation_Existing_ReturnsDetail() {
                when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
                when(conversationRepository.findFirstByUserIdAndDisputeIdIsNullOrderByCreatedAtAsc(userId))
                                .thenReturn(Optional.of(mockConv));
                when(messageRepository.findByConversationIdAndIsReadFalseAndSenderRole(any(), any()))
                                .thenReturn(new ArrayList<>());
                when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                                .thenReturn(List.of(
                                                SupportMessage.builder()
                                                                .id(UUID.randomUUID())
                                                                .conversationId(conversationId)
                                                                .senderId(userId)
                                                                .senderRole("USER")
                                                                .senderName("Nguyễn Văn An")
                                                                .messageText("Chào admin")
                                                                .createdAt(Instant.now())
                                                                .build()));

                SupportConversationDetailDto result = supportChatService.getMyConversation(userId);

                assertThat(result).isNotNull();
                assertThat(result.getConversation().getUserName()).isEqualTo("Nguyễn Văn An");
                assertThat(result.getMessages()).hasSize(1);
        }

        @Test
        @DisplayName("2. sendUserMessage lưu tin nhắn và tăng unreadStaffCount")
        void sendUserMessage_Success_IncrementsStaffUnread() {
                when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
                when(conversationRepository.findFirstByUserIdAndDisputeIdIsNullOrderByCreatedAtAsc(userId))
                                .thenReturn(Optional.of(mockConv));
                when(messageRepository.save(any(SupportMessage.class))).thenAnswer(invocation -> {
                        SupportMessage m = invocation.getArgument(0);
                        m.setId(UUID.randomUUID());
                        m.setCreatedAt(Instant.now());
                        return m;
                });

                SendChatMessageRequestDto req = SendChatMessageRequestDto.builder()
                                .messageText("Em cần hỗ trợ gấp")
                                .build();

                ChatMessageResponseDto result = supportChatService.sendUserMessage(userId, req);

                assertThat(result).isNotNull();
                assertThat(result.getMessageText()).isEqualTo("Em cần hỗ trợ gấp");
                verify(conversationRepository).save(mockConv);
                assertThat(mockConv.getUnreadStaffCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("3. getStaffConversations trả về danh sách toàn bộ người dùng EO và Worker")
        void getStaffConversations_ReturnsList() {
                when(userRepository.findByRoleIn(any())).thenReturn(List.of(mockUser));
                when(conversationRepository.findByDisputeIdIsNullOrderByCreatedAtAsc())
                                .thenReturn(List.of(mockConv));

                List<SupportConversationSummaryDto> list = supportChatService.getStaffConversations(null);

                assertThat(list).hasSize(1);
                assertThat(list.get(0).getUserName()).isEqualTo("Nguyễn Văn An");
                assertThat(list.get(0).getUserPhone()).isEqualTo("0987654321");
        }

        @Test
        @DisplayName("4. sendStaffMessage gửi phản hồi và tăng unreadUserCount")
        void sendStaffMessage_Success_IncrementsUserUnread() {
                when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(mockConv));
                when(userRepository.findById(staffId)).thenReturn(Optional.of(mockStaff));
                when(messageRepository.save(any(SupportMessage.class))).thenAnswer(invocation -> {
                        SupportMessage m = invocation.getArgument(0);
                        m.setId(UUID.randomUUID());
                        m.setCreatedAt(Instant.now());
                        return m;
                });

                SendChatMessageRequestDto req = SendChatMessageRequestDto.builder()
                                .messageText("Chào An, mình xử lý ngay nhé")
                                .build();

                ChatMessageResponseDto result = supportChatService.sendStaffMessage(staffId, conversationId, req);

                assertThat(result).isNotNull();
                assertThat(result.getMessageText()).isEqualTo("Chào An, mình xử lý ngay nhé");
                assertThat(result.getSenderName()).isEqualTo("Lê CSKH");
                verify(conversationRepository).save(mockConv);
                assertThat(mockConv.getUnreadUserCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("5. getOrCreateStaffConversationForUser tạo mới nếu chưa có và trả về chi tiết")
        void getOrCreateStaffConversationForUser_Success() {
                when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
                when(conversationRepository.findFirstByUserIdAndDisputeIdIsNullOrderByCreatedAtAsc(userId))
                                .thenReturn(Optional.empty());
                when(conversationRepository.save(any(SupportConversation.class))).thenReturn(mockConv);
                when(messageRepository.findByConversationIdAndIsReadFalseAndSenderRole(any(), any()))
                                .thenReturn(new ArrayList<>());
                when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                                .thenReturn(Collections.emptyList());

                SupportConversationDetailDto result = supportChatService.getOrCreateStaffConversationForUser(userId,
                                staffId);

                assertThat(result).isNotNull();
                assertThat(result.getConversation().getUserName()).isEqualTo("Nguyễn Văn An");
                verify(conversationRepository).save(any(SupportConversation.class));
        }

        @Test
        @DisplayName("6. getStaffUnreadSummary tính toán đúng tổng số tin nhắn chưa đọc")
        void getStaffUnreadSummary_ReturnsCorrectCounts() {
                when(userRepository.findByRoleIn(any())).thenReturn(List.of(mockUser));
                when(conversationRepository.findByDisputeIdIsNullOrderByCreatedAtAsc())
                                .thenReturn(List.of(mockConv));

                StaffChatUnreadSummaryDto summary = supportChatService.getStaffUnreadSummary();

                assertThat(summary).isNotNull();
                assertThat(summary.getTotalUnreadCount()).isEqualTo(1);
                assertThat(summary.getUnreadConversationsCount()).isEqualTo(1);
                assertThat(summary.getRecentConversations()).hasSize(1);
        }

        @Test
        @DisplayName("7. getStaffConversations bỏ qua phòng chat tranh chấp (disputeId != null)")
        void getStaffConversations_IgnoresDisputeConversations() {
                SupportConversation disputeConv = SupportConversation.builder()
                                .id(UUID.randomUUID())
                                .userId(userId)
                                .disputeId(UUID.randomUUID())
                                .disputeParty("WORKER")
                                .status("ACTIVE")
                                .lastMessagePreview("Tin nhắn tranh chấp")
                                .lastMessageAt(Instant.now())
                                .unreadStaffCount(5)
                                .unreadUserCount(0)
                                .build();

                when(userRepository.findByRoleIn(any())).thenReturn(List.of(mockUser));
                when(conversationRepository.findByDisputeIdIsNullOrderByCreatedAtAsc())
                                .thenReturn(List.of(disputeConv, mockConv));

                List<SupportConversationSummaryDto> list = supportChatService.getStaffConversations(null);

                assertThat(list).hasSize(1);
                assertThat(list.get(0).getId()).isEqualTo(conversationId);
                assertThat(list.get(0).getLastMessagePreview()).isEqualTo("Chào admin");
                assertThat(list.get(0).getUnreadStaffCount()).isEqualTo(1);
        }
}
