package com.cani.service;

import com.cani.dto.admin.sla.SlaScanReportDto;
import com.cani.entity.AuditLog;
import com.cani.entity.Dispute;
import com.cani.entity.SlaAlertLog;
import com.cani.entity.SupportTicket;
import com.cani.entity.enums.DisputeStatus;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.TicketPriority;
import com.cani.entity.enums.TicketStatus;
import com.cani.entity.enums.UserRole;
import com.cani.repository.DisputeRepository;
import com.cani.repository.SlaAlertLogRepository;
import com.cani.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlaMonitoringService — Kiểm thử Background Job Giám sát quá hạn SLA (Tickets & Disputes)")
class SlaMonitoringServiceTest {

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private SlaAlertLogRepository slaAlertLogRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private SlaMonitoringService slaMonitoringService;

    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        ReflectionTestUtils.setField(slaMonitoringService, "slaScanEnabled", true);
    }

    @Test
    @DisplayName("1. Quét Ticket quá hạn SLA: gửi cảnh báo tới nhân sự CC được phân công và ghi audit log")
    void shouldAlertAssignedCcWhenTicketOverdue() {
        UUID assignedCcId = UUID.randomUUID();
        SupportTicket overdueTicket = SupportTicket.builder()
                .id(UUID.randomUUID())
                .referenceNo("TCK-20261108-001")
                .subject("Lỗi thanh toán không nhận được tiền")
                .status(TicketStatus.IN_PROGRESS)
                .priority(TicketPriority.HIGH)
                .assignedTo(assignedCcId)
                .slaDeadline(now.minus(2, ChronoUnit.HOURS))
                .build();

        when(supportTicketRepository.findByStatusInAndSlaDeadlineBefore(any(), any()))
                .thenReturn(List.of(overdueTicket));
        when(disputeRepository.findByStatusInAndSlaDeadlineBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(disputeRepository.findByStatusIn(any()))
                .thenReturn(Collections.emptyList());

        when(slaAlertLogRepository.existsByEntityTypeAndEntityIdAndBreachStage(eq("SUPPORT_TICKET"), eq(overdueTicket.getId()), eq("TICKET_OVERDUE")))
                .thenReturn(false);

        SlaScanReportDto report = slaMonitoringService.scanAndAlertOverdueSla();

        assertThat(report.getTicketBreachesFound()).isEqualTo(1);
        assertThat(report.getTicketAlertsSent()).isEqualTo(1);
        assertThat(report.getTotalAlertsSent()).isEqualTo(1);

        verify(notificationService).sendNotification(
                eq(assignedCcId),
                contains("quá hạn SLA"),
                contains("TCK-20261108-001"),
                eq(NotificationType.SUPPORT_TICKET),
                eq("SUPPORT_TICKET"),
                eq(overdueTicket.getId())
        );

        verify(auditLogService).logAction(argThat(log -> "SLA_BREACH_TICKET".equals(log.getAction())));
        verify(slaAlertLogRepository).save(any(SlaAlertLog.class));

        // Đảm bảo không thay đổi trạng thái ticket (không tự ý đóng)
        assertThat(overdueTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("2. Quét Ticket quá hạn chưa có người nhận: gửi cảnh báo broadcast cho toàn bộ nhóm CUSTOMER_CARE")
    void shouldBroadcastToCustomerCareWhenTicketUnassigned() {
        SupportTicket unassignedTicket = SupportTicket.builder()
                .id(UUID.randomUUID())
                .referenceNo("TCK-20261108-002")
                .subject("Tài khoản bị khóa vô cớ")
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.HIGH)
                .assignedTo(null)
                .slaDeadline(now.minus(1, ChronoUnit.HOURS))
                .build();

        when(supportTicketRepository.findByStatusInAndSlaDeadlineBefore(any(), any()))
                .thenReturn(List.of(unassignedTicket));
        when(disputeRepository.findByStatusInAndSlaDeadlineBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(disputeRepository.findByStatusIn(any()))
                .thenReturn(Collections.emptyList());

        when(slaAlertLogRepository.existsByEntityTypeAndEntityIdAndBreachStage(eq("SUPPORT_TICKET"), eq(unassignedTicket.getId()), eq("TICKET_OVERDUE")))
                .thenReturn(false);

        SlaScanReportDto report = slaMonitoringService.scanAndAlertOverdueSla();

        assertThat(report.getTicketAlertsSent()).isEqualTo(1);
        verify(notificationService).notifyRole(
                eq(UserRole.CUSTOMER_CARE),
                contains("quá hạn SLA"),
                contains("TCK-20261108-002"),
                eq(NotificationType.SUPPORT_TICKET),
                eq("SUPPORT_TICKET"),
                eq(unassignedTicket.getId())
        );
    }

    @Test
    @DisplayName("3. Quét Dispute OPEN/INVESTIGATING quá hạn SLA: gửi thông báo cảnh báo cho Customer Care")
    void shouldAlertCcWhenDisputeOverdue() {
        Dispute overdueDispute = Dispute.builder()
                .disputeId(UUID.randomUUID())
                .title("Tranh chấp tiền thù lao ca làm")
                .status(DisputeStatus.INVESTIGATING)
                .slaDeadline(now.minus(3, ChronoUnit.HOURS))
                .build();

        when(supportTicketRepository.findByStatusInAndSlaDeadlineBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(disputeRepository.findByStatusInAndSlaDeadlineBefore(any(), any()))
                .thenReturn(List.of(overdueDispute));
        when(disputeRepository.findByStatusIn(any()))
                .thenReturn(Collections.emptyList());

        when(slaAlertLogRepository.existsByEntityTypeAndEntityIdAndBreachStage(eq("DISPUTE"), eq(overdueDispute.getDisputeId()), eq("DISPUTE_CC_OVERDUE")))
                .thenReturn(false);

        SlaScanReportDto report = slaMonitoringService.scanAndAlertOverdueSla();

        assertThat(report.getDisputeCcBreachesFound()).isEqualTo(1);
        assertThat(report.getDisputeCcAlertsSent()).isEqualTo(1);
        verify(notificationService).notifyRole(
                eq(UserRole.CUSTOMER_CARE),
                contains("quá hạn SLA tranh chấp"),
                contains("Tranh chấp tiền thù lao ca làm"),
                eq(NotificationType.DISPUTE),
                eq("DISPUTE"),
                eq(overdueDispute.getDisputeId())
        );
        verify(auditLogService).logAction(argThat(log -> "SLA_BREACH_DISPUTE_CC".equals(log.getAction())));
    }

    @Test
    @DisplayName("4. Quét Dispute ESCALATED quá hạn: gửi cảnh báo tới Quản trị viên (ADMIN)")
    void shouldAlertAdminWhenEscalatedDisputeOverdue() {
        Dispute escalatedDispute = Dispute.builder()
                .disputeId(UUID.randomUUID())
                .title("Kháng nghị phán quyết CC")
                .status(DisputeStatus.ESCALATED)
                .slaDeadline(now.minus(1, ChronoUnit.DAYS))
                .escalatedAt(now.minus(8, ChronoUnit.DAYS))
                .build();

        when(supportTicketRepository.findByStatusInAndSlaDeadlineBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(disputeRepository.findByStatusInAndSlaDeadlineBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(disputeRepository.findByStatusIn(eq(List.of(DisputeStatus.ESCALATED))))
                .thenReturn(List.of(escalatedDispute));

        when(slaAlertLogRepository.existsByEntityTypeAndEntityIdAndBreachStage(eq("DISPUTE"), eq(escalatedDispute.getDisputeId()), eq("DISPUTE_ESCALATED_OVERDUE")))
                .thenReturn(false);

        SlaScanReportDto report = slaMonitoringService.scanAndAlertOverdueSla();

        assertThat(report.getDisputeAdminBreachesFound()).isEqualTo(1);
        assertThat(report.getDisputeAdminAlertsSent()).isEqualTo(1);

        verify(notificationService).notifyRole(
                eq(UserRole.ADMIN),
                contains("quá hạn xử lý tranh chấp khiếu nại (Admin)"),
                contains("Kháng nghị phán quyết CC"),
                eq(NotificationType.DISPUTE),
                eq("DISPUTE"),
                eq(escalatedDispute.getDisputeId())
        );
        verify(auditLogService).logAction(argThat(log -> "SLA_BREACH_DISPUTE_ESCALATED".equals(log.getAction())));
    }

    @Test
    @DisplayName("5. Tính Idempotent: Chạy lại nhiều lần không gửi lặp thông báo (0 alerts sent khi đã cảnh báo)")
    void shouldBeIdempotentAndNotSpamAlerts() {
        SupportTicket overdueTicket = SupportTicket.builder()
                .id(UUID.randomUUID())
                .referenceNo("TCK-20261108-003")
                .subject("Hỗ trợ đối soát")
                .status(TicketStatus.OPEN)
                .slaDeadline(now.minus(1, ChronoUnit.HOURS))
                .build();

        when(supportTicketRepository.findByStatusInAndSlaDeadlineBefore(any(), any()))
                .thenReturn(List.of(overdueTicket));
        when(disputeRepository.findByStatusInAndSlaDeadlineBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(disputeRepository.findByStatusIn(any()))
                .thenReturn(Collections.emptyList());

        // Lần chạy này bản ghi đã tồn tại trong sla_alert_logs
        when(slaAlertLogRepository.existsByEntityTypeAndEntityIdAndBreachStage(eq("SUPPORT_TICKET"), eq(overdueTicket.getId()), eq("TICKET_OVERDUE")))
                .thenReturn(true);

        SlaScanReportDto report = slaMonitoringService.scanAndAlertOverdueSla();

        assertThat(report.getTicketBreachesFound()).isEqualTo(1);
        assertThat(report.getTicketAlertsSent()).isEqualTo(0);
        assertThat(report.getTotalAlertsSent()).isEqualTo(0);

        verify(notificationService, never()).sendNotification(any(UUID.class), any(), any(), any(), any(), any());
        verify(notificationService, never()).notifyRole(any(), any(), any(), any(), any(), any());
        verify(auditLogService, never()).logAction(any());
        verify(slaAlertLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("6. Tác vụ @Scheduled tự động dừng khi cấu hình cani.sla-scan.enabled=false")
    void shouldSkipScheduledScanWhenDisabled() {
        ReflectionTestUtils.setField(slaMonitoringService, "slaScanEnabled", false);

        slaMonitoringService.scheduledSlaScan();

        verify(supportTicketRepository, never()).findByStatusInAndSlaDeadlineBefore(any(), any());
        verify(disputeRepository, never()).findByStatusInAndSlaDeadlineBefore(any(), any());
    }
}

