package com.cani.service;

import com.cani.dto.support.AssignTicketRequestDto;
import com.cani.dto.support.CloseTicketRequestDto;
import com.cani.dto.support.CreateTicketRequestDto;
import com.cani.dto.support.ReplyTicketRequestDto;
import com.cani.dto.support.SupportTicketQueueItemDto;
import com.cani.dto.support.SupportTicketResponseDto;
import com.cani.entity.AuditLog;
import com.cani.entity.Notification;
import com.cani.entity.SupportTicket;
import com.cani.entity.SupportTicketMessage;
import com.cani.entity.User;
import com.cani.entity.enums.TicketCategory;
import com.cani.entity.enums.TicketPriority;
import com.cani.entity.enums.TicketStatus;
import com.cani.entity.enums.UserRole;
import com.cani.exception.BadRequestException;
import com.cani.repository.NotificationRepository;
import com.cani.repository.SupportTicketMessageRepository;
import com.cani.repository.SupportTicketRepository;
import com.cani.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock private SupportTicketRepository supportTicketRepository;
    @Mock private SupportTicketMessageRepository supportTicketMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AuditLogService auditLogService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SupportTicketService supportTicketService;

    private UUID userId;
    private UUID ccId;
    private UUID strangerId;
    private User normalUser;
    private User ccUser;
    private User strangerUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        ccId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        normalUser = User.builder().userId(userId).role(UserRole.WORKER).build();
        ccUser = User.builder().userId(ccId).role(UserRole.CUSTOMER_CARE).build();
        strangerUser = User.builder().userId(strangerId).role(UserRole.WORKER).build();
    }

    @Test
    @DisplayName("1. Submit BILLING -> Priority HIGH, deadline ~ now + 4h, reference bắt đầu bằng TKT-")
    void testCreateTicketBilling_HighPriority() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(normalUser));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        CreateTicketRequestDto req = CreateTicketRequestDto.builder()
                .category(TicketCategory.BILLING)
                .subject("Lỗi thanh toán ca làm việc")
                .description("Tôi đã hoàn thành ca làm việc nhưng chưa nhận được tiền thanh toán từ nhà tổ chức")
                .build();

        SupportTicketResponseDto res = supportTicketService.createTicket(userId, req);

        assertThat(res).isNotNull();
        assertThat(res.getCategory()).isEqualTo(TicketCategory.BILLING);
        assertThat(res.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(res.getReferenceNo()).startsWith("TKT-");
        assertThat(res.getSlaDeadline()).isAfter(Instant.now().plus(3, ChronoUnit.HOURS));
        assertThat(res.getSlaDeadline()).isBefore(Instant.now().plus(5, ChronoUnit.HOURS));

        verify(supportTicketRepository).save(any(SupportTicket.class));
        verify(supportTicketMessageRepository).save(any(SupportTicketMessage.class));
        verify(auditLogService).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("2. Submit OTHER -> Priority LOW, deadline ~ now + 3d")
    void testCreateTicketOther_LowPriority() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(normalUser));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        CreateTicketRequestDto req = CreateTicketRequestDto.builder()
                .category(TicketCategory.OTHER)
                .subject("Thắc mắc góp ý ứng dụng")
                .description("Ứng dụng chạy rất tốt nhưng giao diện nên bổ sung chế độ tối (dark mode) trong tương lai")
                .build();

        SupportTicketResponseDto res = supportTicketService.createTicket(userId, req);

        assertThat(res.getPriority()).isEqualTo(TicketPriority.LOW);
        assertThat(res.getSlaDeadline()).isAfter(Instant.now().plus(2, ChronoUnit.DAYS));
        assertThat(res.getSlaDeadline()).isBefore(Instant.now().plus(4, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("3. Submit TECHNICAL -> Priority MEDIUM, deadline ~ now + 1d")
    void testCreateTicketTechnical_MediumPriority() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(normalUser));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        CreateTicketRequestDto req = CreateTicketRequestDto.builder()
                .category(TicketCategory.TECHNICAL)
                .subject("Không thể tải lên ảnh bằng chứng")
                .description("Hệ thống liên tục báo lỗi kết nối khi tôi ấn nút tải ảnh lên trong phần hồ sơ")
                .build();

        SupportTicketResponseDto res = supportTicketService.createTicket(userId, req);

        assertThat(res.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(res.getSlaDeadline()).isAfter(Instant.now().plus(20, ChronoUnit.HOURS));
        assertThat(res.getSlaDeadline()).isBefore(Instant.now().plus(28, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("4. User thường reply với isInternal=true -> ném BadRequestException")
    void testReplyNormalUser_WithInternalFlag_ThrowsBadRequest() {
        UUID ticketId = UUID.randomUUID();
        SupportTicket ticket = SupportTicket.builder()
                .id(ticketId)
                .userId(userId)
                .status(TicketStatus.OPEN)
                .build();

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(userId)).thenReturn(Optional.of(normalUser));

        ReplyTicketRequestDto req = ReplyTicketRequestDto.builder()
                .body("Ghi chú thử nghiệm nội bộ")
                .isInternal(true)
                .build();

        assertThatThrownBy(() -> supportTicketService.reply(ticketId, userId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Người dùng thường không được tạo ghi chú nội bộ");
    }

    @Test
    @DisplayName("5. Reply khi ticket CLOSED -> ném BadRequestException")
    void testReplyClosedTicket_ThrowsBadRequest() {
        UUID ticketId = UUID.randomUUID();
        SupportTicket ticket = SupportTicket.builder()
                .id(ticketId)
                .userId(userId)
                .status(TicketStatus.CLOSED)
                .build();

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ReplyTicketRequestDto req = ReplyTicketRequestDto.builder()
                .body("Tôi vẫn muốn hỏi thêm thông tin")
                .build();

        assertThatThrownBy(() -> supportTicketService.reply(ticketId, userId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ticket đã đóng, không thể phản hồi");
    }

    @Test
    @DisplayName("6. User lạ không có quyền xem hoặc reply ticket -> ném BadRequestException")
    void testStrangerAccessTicket_ThrowsBadRequest() {
        UUID ticketId = UUID.randomUUID();
        SupportTicket ticket = SupportTicket.builder()
                .id(ticketId)
                .userId(userId)
                .status(TicketStatus.OPEN)
                .build();

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(strangerId)).thenReturn(Optional.of(strangerUser));

        assertThatThrownBy(() -> supportTicketService.getTicketDetail(ticketId, strangerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bạn không có quyền xem ticket này");

        ReplyTicketRequestDto req = ReplyTicketRequestDto.builder()
                .body("Tôi trả lời hộ người khác")
                .build();

        assertThatThrownBy(() -> supportTicketService.reply(ticketId, strangerId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bạn không có quyền phản hồi ticket này");
    }

    @Test
    @DisplayName("7. Phân công ticket: OPEN -> IN_PROGRESS; phân công role không phải CC/Admin -> ném BadRequestException")
    void testAssignTicket() {
        UUID ticketId = UUID.randomUUID();
        SupportTicket ticket = SupportTicket.builder()
                .id(ticketId)
                .userId(userId)
                .status(TicketStatus.OPEN)
                .referenceNo("TKT-20261031-ABCD")
                .build();

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(ccId)).thenReturn(Optional.of(ccUser));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // CC tự nhận xử lý (targetCcUserId = null)
        SupportTicketResponseDto res = supportTicketService.assignTicket(ticketId, ccId, new AssignTicketRequestDto(null));

        assertThat(res.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(res.getAssignedTo()).isEqualTo(ccId);
        verify(notificationRepository).save(any(Notification.class));

        // Thử phân công cho người dùng bình thường (không phải CC)
        when(userRepository.findById(strangerId)).thenReturn(Optional.of(strangerUser));
        AssignTicketRequestDto invalidAssignReq = new AssignTicketRequestDto(strangerId);

        assertThatThrownBy(() -> supportTicketService.assignTicket(ticketId, ccId, invalidAssignReq))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Người nhận phân công phải có vai trò Chăm sóc khách hàng hoặc Quản trị viên");
    }

    @Test
    @DisplayName("8. Hàng đợi: Ticket có SLA risk xếp trước ticket không risk")
    void testGetQueue_SlaRiskSorting() {
        Instant now = Instant.now();

        // Ticket 1: Hết hạn trong 2 giờ tới (SLA at risk)
        SupportTicket ticket1 = SupportTicket.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(TicketStatus.OPEN)
                .slaDeadline(now.plus(2, ChronoUnit.HOURS))
                .createdAt(now.minus(2, ChronoUnit.HOURS))
                .build();

        // Ticket 2: Hạn còn 48 giờ (Không risk), nhưng tạo sớm hơn
        SupportTicket ticket2 = SupportTicket.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(TicketStatus.OPEN)
                .slaDeadline(now.plus(48, ChronoUnit.HOURS))
                .createdAt(now.minus(5, ChronoUnit.HOURS))
                .build();

        when(supportTicketRepository.findByStatusInOrderBySlaDeadlineAsc(anyList()))
                .thenReturn(List.of(ticket2, ticket1));

        List<SupportTicketQueueItemDto> queue = supportTicketService.getQueue();

        assertThat(queue).hasSize(2);
        // Ticket 1 phải được xếp lên đầu do slaAtRisk = true
        assertThat(queue.get(0).getId()).isEqualTo(ticket1.getId());
        assertThat(queue.get(0).getSlaAtRisk()).isTrue();
        assertThat(queue.get(1).getId()).isEqualTo(ticket2.getId());
        assertThat(queue.get(1).getSlaAtRisk()).isFalse();
    }

    @Test
    @DisplayName("9. Customer Care đóng ticket -> CLOSED + resolvedBy + closedAt")
    void testCloseTicket_ByCustomerCare() {
        UUID ticketId = UUID.randomUUID();
        SupportTicket ticket = SupportTicket.builder()
                .id(ticketId)
                .userId(userId)
                .referenceNo("TKT-20261031-1111")
                .status(TicketStatus.IN_PROGRESS)
                .build();

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(ccId)).thenReturn(Optional.of(ccUser));
        when(userRepository.findById(userId)).thenReturn(Optional.of(normalUser));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CloseTicketRequestDto req = CloseTicketRequestDto.builder()
                .rationale("Đã hướng dẫn người dùng khôi phục tài khoản thành công qua số điện thoại")
                .build();

        SupportTicketResponseDto res = supportTicketService.closeTicket(ticketId, ccId, req);

        assertThat(res.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(res.getResolvedBy()).isEqualTo(ccId);
        assertThat(res.getClosedAt()).isNotNull();

        verify(supportTicketRepository).save(any(SupportTicket.class));
        verify(supportTicketMessageRepository).save(any(SupportTicketMessage.class));
        verify(notificationRepository).save(any(Notification.class));
        verify(auditLogService).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("10. Người dùng nộp tự markResolved -> CLOSED + resolvedBy = userId")
    void testMarkResolved_ByUser() {
        UUID ticketId = UUID.randomUUID();
        SupportTicket ticket = SupportTicket.builder()
                .id(ticketId)
                .userId(userId)
                .referenceNo("TKT-20261031-2222")
                .status(TicketStatus.OPEN)
                .build();

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupportTicketResponseDto res = supportTicketService.markResolved(ticketId, userId);

        assertThat(res.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(res.getResolvedBy()).isEqualTo(userId);
        assertThat(res.getClosedAt()).isNotNull();

        // Nếu người lạ thử gọi markResolved -> ném lỗi
        SupportTicket ticketToFail = SupportTicket.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(TicketStatus.OPEN)
                .build();
        when(supportTicketRepository.findById(ticketToFail.getId())).thenReturn(Optional.of(ticketToFail));

        assertThatThrownBy(() -> supportTicketService.markResolved(ticketToFail.getId(), strangerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chỉ người nộp ticket mới được đánh dấu đã giải quyết");
    }

    @Test
    @DisplayName("11. Lọc tin nhắn: Người dùng thường không thấy internal note, CC/Admin thấy đầy đủ")
    void testInternalNoteFiltering_UserCannotSee_CcCanSee() {
        UUID ticketId = UUID.randomUUID();
        SupportTicket ticket = SupportTicket.builder()
                .id(ticketId)
                .userId(userId)
                .referenceNo("TKT-20261031-3333")
                .status(TicketStatus.OPEN)
                .build();

        SupportTicketMessage publicMsg = SupportTicketMessage.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .senderId(userId)
                .body("Tin nhắn công khai từ người dùng")
                .isInternal(false)
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        SupportTicketMessage internalMsg = SupportTicketMessage.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .senderId(ccId)
                .body("Ghi chú nội bộ của chuyên viên CSKH: Cần check thêm log thanh toán")
                .isInternal(true)
                .createdAt(Instant.now())
                .build();

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(userId)).thenReturn(Optional.of(normalUser));
        when(userRepository.findById(ccId)).thenReturn(Optional.of(ccUser));
        when(supportTicketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId))
                .thenReturn(List.of(publicMsg, internalMsg));

        // 1. User thường (owner) xem chi tiết -> chỉ thấy 1 tin nhắn công khai, không thấy internal note
        SupportTicketResponseDto userView = supportTicketService.getTicketDetail(ticketId, userId);
        assertThat(userView.getMessages()).hasSize(1);
        assertThat(userView.getMessages().get(0).getBody()).isEqualTo("Tin nhắn công khai từ người dùng");
        assertThat(userView.getMessages().get(0).getIsInternal()).isFalse();

        // 2. Customer Care xem chi tiết -> thấy đầy đủ 2 tin nhắn (bao gồm cả internal note)
        SupportTicketResponseDto ccView = supportTicketService.getTicketDetail(ticketId, ccId);
        assertThat(ccView.getMessages()).hasSize(2);
        assertThat(ccView.getMessages()).anyMatch(m -> Boolean.TRUE.equals(m.getIsInternal()));
    }
}
