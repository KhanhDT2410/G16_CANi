package com.cani.service;

import com.cani.dto.dispute.*;
import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.exception.BadRequestException;
import com.cani.repository.*;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private DisputeEvidenceRepository evidenceRepository;
    @Mock private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private WorkerRepository workerRepository;
    @Mock private EventOrganizerRepository eventOrganizerRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReliabilityLogRepository reliabilityLogRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AuditLogService auditLogService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DisputeService disputeService;

    private UUID workerUserId;
    private UUID eoUserId;
    private Worker worker;
    private EventOrganizer eo;
    private Event event;
    private Shift shift;
    private ShiftAssignment assignment;
    private Payment payment;

    @BeforeEach
    void setUp() {
        workerUserId = UUID.randomUUID();
        eoUserId = UUID.randomUUID();

        User workerUser = User.builder().userId(workerUserId).role(UserRole.WORKER).build();
        User eoUser = User.builder().userId(eoUserId).role(UserRole.EVENT_ORGANIZER).build();

        worker = Worker.builder().workerId(UUID.randomUUID()).user(workerUser).reliabilityScore(80).build();
        eo = EventOrganizer.builder().eoId(UUID.randomUUID()).user(eoUser).build();
        event = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(eo).build();
        shift = Shift.builder().shiftId(UUID.randomUUID()).event(event)
                .startTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .endTime(Instant.now()) // vừa kết thúc → nằm trong cửa sổ 14 ngày (BR-52)
                .build();
        assignment = ShiftAssignment.builder().assignmentId(UUID.randomUUID()).shift(shift).worker(worker).build();
        payment = Payment.builder().paymentId(UUID.randomUUID()).assignmentId(assignment.getAssignmentId())
                .status(PaymentStatus.NOT_DECLARED).build();
    }

    private CreateDisputeRequestDto paymentDisputeRequest() {
        return CreateDisputeRequestDto.builder()
                .assignmentId(assignment.getAssignmentId())
                .type(DisputeType.PAYMENT)
                .title("Chưa nhận tiền công ca Lễ tân")
                .description("Nhà tổ chức chưa thanh toán tiền công và phụ cấp sau khi ca kết thúc (mô tả đủ 50 ký tự).")
                .build();
    }

    @Test
    @DisplayName("UC-96: Worker nộp tranh chấp loại PAYMENT → dispute OPEN, payment chuyển DISPUTED")
    void shouldCreateDisputeAndMarkPaymentDisputed() {
        when(shiftAssignmentRepository.findById(assignment.getAssignmentId())).thenReturn(Optional.of(assignment));
        when(workerRepository.findByUser_UserId(workerUserId)).thenReturn(Optional.of(worker));
        when(disputeRepository.existsByAssignmentIdAndStatusIn(eq(assignment.getAssignmentId()), anyList()))
                .thenReturn(false);
        when(paymentRepository.findByAssignmentId(assignment.getAssignmentId())).thenReturn(Optional.of(payment));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(workerUserId)).thenReturn(Optional.of(User.builder().userId(workerUserId).build()));
        when(userRepository.findById(eoUserId)).thenReturn(Optional.of(User.builder().userId(eoUserId).build()));
        when(userRepository.findByRole(UserRole.CUSTOMER_CARE)).thenReturn(List.of());

        DisputeDetailResponseDto result = disputeService.createDispute(workerUserId, paymentDisputeRequest());

        assertThat(result.getStatus()).isEqualTo(DisputeStatus.OPEN);
        assertThat(result.getComplainantId()).isEqualTo(workerUserId);
        assertThat(result.getRespondentId()).isEqualTo(eoUserId);
        assertThat(result.getSlaDeadline()).isAfter(Instant.now());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DISPUTED);
        verify(disputeRepository).save(any(Dispute.class));
    }

    @Test
    @DisplayName("UC-96: Tranh chấp trùng lặp chưa xử lý → 400")
    void shouldRejectDuplicateOpenDispute() {
        when(shiftAssignmentRepository.findById(assignment.getAssignmentId())).thenReturn(Optional.of(assignment));
        when(workerRepository.findByUser_UserId(workerUserId)).thenReturn(Optional.of(worker));
        when(disputeRepository.existsByAssignmentIdAndStatusIn(eq(assignment.getAssignmentId()), anyList()))
                .thenReturn(true);

        assertThatThrownBy(() -> disputeService.createDispute(workerUserId, paymentDisputeRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("trùng lặp");
    }

    @Test
    @DisplayName("BR-52: Quá cửa sổ 14 ngày kể từ ca kết thúc → 400")
    void shouldRejectDisputeOutsideWindow() {
        shift.setEndTime(Instant.now().minus(20, ChronoUnit.DAYS));
        when(shiftAssignmentRepository.findById(assignment.getAssignmentId())).thenReturn(Optional.of(assignment));
        when(workerRepository.findByUser_UserId(workerUserId)).thenReturn(Optional.of(worker));

        assertThatThrownBy(() -> disputeService.createDispute(workerUserId, paymentDisputeRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("14 ngày");
    }

    @Test
    @DisplayName("UC-99: CC tự nhận đơn OPEN → chuyển INVESTIGATING, gán assignedTo = CC")
    void shouldAssignDisputeToCc() {
        UUID ccUserId = UUID.randomUUID();
        Dispute dispute = openDispute();
        when(disputeRepository.findById(dispute.getDisputeId())).thenReturn(Optional.of(dispute));
        when(userRepository.findById(ccUserId)).thenReturn(
                Optional.of(User.builder().userId(ccUserId).role(UserRole.CUSTOMER_CARE).build()));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        disputeService.assignDispute(ccUserId, dispute.getDisputeId(), null);

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.INVESTIGATING);
        assertThat(dispute.getAssignedTo()).isEqualTo(ccUserId);
    }

    @Test
    @DisplayName("UC-100: CC quyết định WORKER_FAULT → RESOLVED, −10 RS + ReliabilityLog (BR-55)")
    void shouldApplyRsPenaltyOnWorkerFault() {
        UUID ccUserId = UUID.randomUUID();
        Dispute dispute = openDispute();
        dispute.setStatus(DisputeStatus.INVESTIGATING);
        dispute.setAssignedTo(ccUserId);
        when(disputeRepository.findById(dispute.getDisputeId())).thenReturn(Optional.of(dispute));
        when(userRepository.findById(ccUserId)).thenReturn(
                Optional.of(User.builder().userId(ccUserId).role(UserRole.CUSTOMER_CARE).build()));
        when(workerRepository.findByUser_UserId(workerUserId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(any(Worker.class))).thenAnswer(inv -> inv.getArgument(0));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueDecisionRequestDto request = IssueDecisionRequestDto.builder()
                .outcome(DisputeOutcome.WORKER_FAULT)
                .rationale("Qua đối chiếu nhật ký điểm danh QR và bằng chứng thanh toán, xác nhận worker bỏ ca không thông báo, lý do quyết định đã đủ 100 ký tự theo quy định của hệ thống.")
                .build();

        disputeService.issueDecision(ccUserId, dispute.getDisputeId(), request);

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(dispute.getOutcome()).isEqualTo(DisputeOutcome.WORKER_FAULT);
        assertThat(dispute.getResolvedBy()).isEqualTo(ccUserId);
        assertThat(dispute.getRsPenaltyApplied()).isTrue();
        assertThat(worker.getReliabilityScore()).isEqualTo(70);

        ArgumentCaptor<ReliabilityLog> logCaptor = ArgumentCaptor.forClass(ReliabilityLog.class);
        verify(reliabilityLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getChangeAmount()).isEqualTo(-10);
        assertThat(logCaptor.getValue().getReferenceType()).isEqualTo("DISPUTE");
    }

    @Test
    @DisplayName("UC-102: Admin quyết định cuối cho đơn ESCALATED → FINAL_RESOLVED (BR-68)")
    void shouldIssueAdminFinalDecision() {
        UUID adminUserId = UUID.randomUUID();
        Dispute dispute = openDispute();
        dispute.setStatus(DisputeStatus.ESCALATED);
        when(disputeRepository.findById(dispute.getDisputeId())).thenReturn(Optional.of(dispute));
        when(userRepository.findById(adminUserId)).thenReturn(
                Optional.of(User.builder().userId(adminUserId).role(UserRole.ADMIN).build()));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueDecisionRequestDto request = IssueDecisionRequestDto.builder()
                .outcome(DisputeOutcome.UPHOLD)
                .rationale("Quản trị viên đã xem xét toàn bộ hồ sơ, bằng chứng của hai bên và quyết định của CSCĐ trước đó, giữ nguyên kết quả (lý do đủ 100 ký tự như yêu cầu nghiệp vụ).")
                .build();

        disputeService.issueDecision(adminUserId, dispute.getDisputeId(), request);

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.FINAL_RESOLVED);
        assertThat(dispute.getFinalDecidedBy()).isEqualTo(adminUserId);
        assertThat(dispute.getFinalDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("UC-100: CC ra quyết định khi đơn chưa INVESTIGATING → 400")
    void shouldRejectDecisionAtWrongStatus() {
        UUID ccUserId = UUID.randomUUID();
        Dispute dispute = openDispute(); // vẫn OPEN
        when(disputeRepository.findById(dispute.getDisputeId())).thenReturn(Optional.of(dispute));
        when(userRepository.findById(ccUserId)).thenReturn(
                Optional.of(User.builder().userId(ccUserId).role(UserRole.CUSTOMER_CARE).build()));

        IssueDecisionRequestDto request = IssueDecisionRequestDto.builder()
                .outcome(DisputeOutcome.EO_FAULT)
                .rationale("Lý do quyết định đã đủ độ dài tối thiểu 100 ký tự theo quy định để kiểm thử luồng trạng thái không hợp lệ của hệ thống.")
                .build();

        assertThatThrownBy(() -> disputeService.issueDecision(ccUserId, dispute.getDisputeId(), request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("UC-101: Kháng nghị trong 7 ngày → ESCALATED; quá hạn → 400 (BR-54)")
    void shouldEscalateWithinWindowAndRejectExpired() {
        Dispute dispute = openDispute();
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolvedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        when(disputeRepository.findById(dispute.getDisputeId())).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        EscalateDisputeRequestDto escalate = EscalateDisputeRequestDto.builder()
                .reason("Không đồng ý với kết quả quyết định của bộ phận chăm sóc khách hàng.")
                .build();

        disputeService.escalateDispute(dispute.getDisputeId(), workerUserId, escalate);
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.ESCALATED);
        assertThat(dispute.getEscalatedBy()).isEqualTo(workerUserId);

        // Đơn thứ hai: đã quá 7 ngày kể từ quyết định
        Dispute expired = openDispute();
        expired.setStatus(DisputeStatus.RESOLVED);
        expired.setResolvedAt(Instant.now().minus(8, ChronoUnit.DAYS));
        when(disputeRepository.findById(expired.getDisputeId())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> disputeService.escalateDispute(expired.getDisputeId(), workerUserId, escalate))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("7 ngày");
    }

    @Test
    @DisplayName("UC-109: Hàng đợi CC — đơn sắp quá SLA xếp trước đơn nộp cũ hơn (BR-57)")
    void shouldOrderQueueBySlaRiskThenAge() {
        Dispute urgent = openDispute();
        urgent.setCreatedAt(Instant.now().plus(1, ChronoUnit.HOURS)); // nộp mới
        urgent.setSlaDeadline(Instant.now().plus(1, ChronoUnit.HOURS)); // sắp quá SLA

        Dispute older = openDispute();
        older.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS)); // nộp rất cũ
        older.setSlaDeadline(Instant.now().plus(5, ChronoUnit.DAYS));

        when(disputeRepository.findByStatusIn(anyList())).thenReturn(List.of(older, urgent));

        List<DisputeQueueItemDto> queue = disputeService.getDisputeQueue();

        assertThat(queue).hasSize(2);
        assertThat(queue.get(0).getId()).isEqualTo(urgent.getDisputeId());
        assertThat(queue.get(0).isSlaAtRisk()).isTrue();
        assertThat(queue.get(1).getId()).isEqualTo(older.getDisputeId());
    }

    // ------------------------------------------------------------------

    private Dispute openDispute() {
        return Dispute.builder()
                .disputeId(UUID.randomUUID())
                .assignmentId(assignment.getAssignmentId())
                .complainantId(workerUserId)
                .respondentId(eoUserId)
                .type(DisputeType.PAYMENT)
                .title("Tranh chấp thanh toán")
                .description("Mô tả chi tiết tranh chấp (đủ 50 ký tự cho kiểm thử).")
                .status(DisputeStatus.OPEN)
                .createdAt(Instant.now())
                .build();
    }
}
