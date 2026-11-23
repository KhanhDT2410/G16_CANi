package com.cani.service;

import com.cani.dto.dispute.DisputeChatMessageDto;
import com.cani.dto.dispute.DisputeDualChatDto;
import com.cani.dto.dispute.SendDisputeChatMessageDto;
import com.cani.entity.*;
import com.cani.entity.enums.DisputeStatus;
import com.cani.entity.enums.DisputeType;
import com.cani.entity.enums.UserRole;
import com.cani.exception.BadRequestException;
import com.cani.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeChatServiceTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private SupportConversationRepository supportConversationRepository;
    @Mock private SupportMessageRepository supportMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private DisputeChatService disputeChatService;

    private UUID disputeId;
    private UUID workerUserId;
    private UUID eoUserId;
    private UUID adminUserId;
    private UUID outsiderUserId;

    private User workerUser;
    private User eoUser;
    private User adminUser;
    private User outsiderUser;
    private Dispute dispute;

    @BeforeEach
    void setUp() {
        disputeId = UUID.randomUUID();
        workerUserId = UUID.randomUUID();
        eoUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        outsiderUserId = UUID.randomUUID();

        workerUser = User.builder().userId(workerUserId).fullName("Nguyễn Văn Worker").role(UserRole.WORKER).build();
        eoUser = User.builder().userId(eoUserId).fullName("Trần Thị EO").role(UserRole.EVENT_ORGANIZER).build();
        adminUser = User.builder().userId(adminUserId).fullName("Admin Hệ Thống").role(UserRole.ADMIN).build();
        outsiderUser = User.builder().userId(outsiderUserId).fullName("Kẻ Ngoại Cuộc").role(UserRole.WORKER).build();

        dispute = Dispute.builder()
                .disputeId(disputeId)
                .title("Tranh chấp tiền tip và làm thêm giờ")
                .type(DisputeType.PAYMENT)
                .complainantId(workerUserId)
                .respondentId(eoUserId)
                .status(DisputeStatus.INVESTIGATING)
                .build();
    }

    @Test
    @DisplayName("Admin xem hội thoại: Nhận được đầy đủ cả 2 kênh (Worker và EO)")
    void getDisputeChat_asAdmin_returnsBothWorkerAndEoConversations() {
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(workerUserId)).thenReturn(Optional.of(workerUser));
        when(userRepository.findById(eoUserId)).thenReturn(Optional.of(eoUser));

        SupportConversation workerConv = SupportConversation.builder()
                .id(UUID.randomUUID()).disputeId(disputeId).userId(workerUserId).disputeParty("WORKER").build();
        SupportConversation eoConv = SupportConversation.builder()
                .id(UUID.randomUUID()).disputeId(disputeId).userId(eoUserId).disputeParty("EO").build();

        when(supportConversationRepository.findByDisputeIdAndDisputeParty(disputeId, "WORKER"))
                .thenReturn(Optional.of(workerConv));
        when(supportConversationRepository.findByDisputeIdAndDisputeParty(disputeId, "EO"))
                .thenReturn(Optional.of(eoConv));

        when(supportMessageRepository.findByConversationIdOrderByCreatedAtAsc(workerConv.getId()))
                .thenReturn(Collections.emptyList());
        when(supportMessageRepository.findByConversationIdOrderByCreatedAtAsc(eoConv.getId()))
                .thenReturn(Collections.emptyList());

        DisputeDualChatDto result = disputeChatService.getDisputeChat(disputeId, adminUserId);

        assertThat(result).isNotNull();
        assertThat(result.getDisputeId()).isEqualTo(disputeId);
        assertThat(result.getWorkerConversation()).isNotNull();
        assertThat(result.getWorkerConversation().getParty()).isEqualTo("WORKER");
        assertThat(result.getEoConversation()).isNotNull();
        assertThat(result.getEoConversation().getParty()).isEqualTo("EO");
    }

    @Test
    @DisplayName("Worker xem hội thoại: Chỉ nhận được kênh của Worker với Admin (EO bị ẩn hoàn toàn)")
    void getDisputeChat_asWorker_returnsOnlyWorkerConversation() {
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(userRepository.findById(workerUserId)).thenReturn(Optional.of(workerUser));
        when(userRepository.findById(eoUserId)).thenReturn(Optional.of(eoUser));

        SupportConversation workerConv = SupportConversation.builder()
                .id(UUID.randomUUID()).disputeId(disputeId).userId(workerUserId).disputeParty("WORKER").build();

        when(supportConversationRepository.findByDisputeIdAndDisputeParty(disputeId, "WORKER"))
                .thenReturn(Optional.of(workerConv));
        when(supportMessageRepository.findByConversationIdOrderByCreatedAtAsc(workerConv.getId()))
                .thenReturn(Collections.emptyList());

        DisputeDualChatDto result = disputeChatService.getDisputeChat(disputeId, workerUserId);

        assertThat(result).isNotNull();
        assertThat(result.getWorkerConversation()).isNotNull();
        assertThat(result.getEoConversation()).isNull(); // Đảm bảo cô lập tuyệt đối
    }

    @Test
    @DisplayName("Người ngoài không có quyền xem tranh chấp: Bị từ chối AccessDeniedException")
    void getDisputeChat_asOutsider_throwsAccessDenied() {
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(userRepository.findById(outsiderUserId)).thenReturn(Optional.of(outsiderUser));
        when(userRepository.findById(workerUserId)).thenReturn(Optional.of(workerUser));
        when(userRepository.findById(eoUserId)).thenReturn(Optional.of(eoUser));

        assertThatThrownBy(() -> disputeChatService.getDisputeChat(disputeId, outsiderUserId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Admin gửi chỉ đạo cho Worker: Tin nhắn được ghi nhận vào kênh Worker")
    void sendMessage_asAdminToWorker_createsMessage() {
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(workerUserId)).thenReturn(Optional.of(workerUser));
        when(userRepository.findById(eoUserId)).thenReturn(Optional.of(eoUser));

        SupportConversation workerConv = SupportConversation.builder()
                .id(UUID.randomUUID()).disputeId(disputeId).userId(workerUserId).disputeParty("WORKER").build();
        when(supportConversationRepository.findByDisputeIdAndDisputeParty(disputeId, "WORKER"))
                .thenReturn(Optional.of(workerConv));

        when(supportMessageRepository.save(any(SupportMessage.class))).thenAnswer(invocation -> {
            SupportMessage m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });

        SendDisputeChatMessageDto req = SendDisputeChatMessageDto.builder()
                .targetParty("WORKER")
                .messageText("Yêu cầu bạn bổ sung ảnh chụp hợp đồng trước 17:00")
                .build();

        DisputeChatMessageDto response = disputeChatService.sendMessage(disputeId, adminUserId, req);

        assertThat(response).isNotNull();
        assertThat(response.getMessageText()).isEqualTo("Yêu cầu bạn bổ sung ảnh chụp hợp đồng trước 17:00");
        assertThat(response.getSenderName()).isEqualTo("Admin Hệ Thống");
        verify(supportMessageRepository).save(any(SupportMessage.class));
        verify(supportConversationRepository).save(workerConv);
    }

    @Test
    @DisplayName("Vụ việc đã có phán quyết cuối cùng (FINAL_RESOLVED): Từ chối gửi tin nhắn mới (BR-68)")
    void sendMessage_whenDisputeFinalResolved_throwsBadRequest() {
        dispute.setStatus(DisputeStatus.FINAL_RESOLVED);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        SendDisputeChatMessageDto req = SendDisputeChatMessageDto.builder()
                .targetParty("WORKER")
                .messageText("Thử nhắn tin khi đã đóng")
                .build();

        assertThatThrownBy(() -> disputeChatService.sendMessage(disputeId, adminUserId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FINAL_RESOLVED");
    }

    @Test
    @DisplayName("Retention Scheduler: Dọn dẹp tin nhắn thường quá 7 ngày và dispute đã xong quá 5 ngày")
    void retention_purgesExpectedMessages() {
        when(chatMessageRepository.deleteByCreatedAtBefore(any(Instant.class))).thenReturn(15);
        when(supportConversationRepository.findByDisputeIdIsNotNull()).thenReturn(Collections.emptyList());

        disputeChatService.purgeExpiredRegularChats();
        disputeChatService.cleanupResolvedDisputeChats();

        verify(chatMessageRepository).deleteByCreatedAtBefore(any(Instant.class));
    }
}
