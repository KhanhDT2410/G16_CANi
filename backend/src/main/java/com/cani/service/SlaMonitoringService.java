package com.cani.service;

import com.cani.dto.admin.sla.SlaScanReportDto;
import com.cani.entity.AuditLog;
import com.cani.entity.Dispute;
import com.cani.entity.SlaAlertLog;
import com.cani.entity.SupportTicket;
import com.cani.entity.enums.DisputeStatus;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.TicketStatus;
import com.cani.entity.enums.UserRole;
import com.cani.repository.DisputeRepository;
import com.cani.repository.SlaAlertLogRepository;
import com.cani.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service chạy ngầm giám sát hạn chót SLA của Phiếu hỗ trợ và Tranh chấp.
 * - Quét định kỳ tự động qua @Scheduled (mặc định mỗi 15 phút, tùy biến qua cani.sla-scan.cron).
 * - Đảm bảo tính Idempotent 100%: Sử dụng bảng sla_alert_logs để không bao giờ gửi cảnh báo lặp lại.
 * - Tuyệt đối KHÔNG tự ý đóng ticket/dispute (chỉ cảnh báo nhân sự và ghi nhận nhật ký kiểm toán).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaMonitoringService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final SupportTicketRepository supportTicketRepository;
    private final DisputeRepository disputeRepository;
    private final SlaAlertLogRepository slaAlertLogRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Value("${cani.sla-scan.enabled:true}")
    private boolean slaScanEnabled;

    /**
     * Tác vụ quét nền được lập lịch.
     * Tự động bỏ qua khi cấu hình cani.sla-scan.enabled = false.
     */
    @Scheduled(cron = "${cani.sla-scan.cron:0 */15 * * * *}")
    public void scheduledSlaScan() {
        if (!slaScanEnabled) {
            log.debug("Bỏ qua quét SLA do cani.sla-scan.enabled = false");
            return;
        }
        log.info("Bắt đầu tác vụ chạy nền kiểm soát vi phạm hạn chót SLA...");
        scanAndAlertOverdueSla();
    }

    /**
     * Thực thi quét và gửi cảnh báo vi phạm SLA.
     * Phương thức public để kiểm thử trực tiếp mà không cần chờ cron.
     */
    @Transactional
    public SlaScanReportDto scanAndAlertOverdueSla() {
        Instant now = Instant.now();

        int ticketBreaches = 0;
        int ticketAlerts = 0;

        int disputeCcBreaches = 0;
        int disputeCcAlerts = 0;

        int disputeAdminBreaches = 0;
        int disputeAdminAlerts = 0;

        // ---------------------------------------------------------------------
        // 1. Quét phiếu hỗ trợ (Support Tickets): Trạng thái OPEN, IN_PROGRESS quá hạn SLA
        // ---------------------------------------------------------------------
        List<SupportTicket> overdueTickets = supportTicketRepository.findByStatusInAndSlaDeadlineBefore(
                List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS), now
        );
        ticketBreaches = overdueTickets.size();

        for (SupportTicket ticket : overdueTickets) {
            boolean alreadyAlerted = slaAlertLogRepository.existsByEntityTypeAndEntityIdAndBreachStage(
                    "SUPPORT_TICKET", ticket.getId(), "TICKET_OVERDUE"
            );
            if (alreadyAlerted) {
                continue;
            }

            String deadlineText = TIME_FORMATTER.format(ticket.getSlaDeadline());
            String title = "Cảnh báo quá hạn SLA phiếu hỗ trợ";
            String body = String.format("Phiếu hỗ trợ #%s ('%s') đã quá hạn xử lý (Hạn chót SLA: %s). Vui lòng tiếp nhận và xử lý ngay.",
                    ticket.getReferenceNo(), ticket.getSubject(), deadlineText);

            if (ticket.getAssignedTo() != null) {
                notificationService.sendNotification(ticket.getAssignedTo(), title, body, NotificationType.SUPPORT_TICKET, "SUPPORT_TICKET", ticket.getId());
            } else {
                notificationService.notifyRole(UserRole.CUSTOMER_CARE, title, body, NotificationType.SUPPORT_TICKET, "SUPPORT_TICKET", ticket.getId());
            }

            auditLogService.logAction(AuditLog.builder()
                    .action("SLA_BREACH_TICKET")
                    .entityType("SUPPORT_TICKET")
                    .entityId(ticket.getId())
                    .newValues(String.format("{\"referenceNo\":\"%s\",\"slaDeadline\":\"%s\",\"status\":\"%s\"}",
                            ticket.getReferenceNo(), ticket.getSlaDeadline(), ticket.getStatus()))
                    .build());

            slaAlertLogRepository.save(SlaAlertLog.builder()
                    .entityType("SUPPORT_TICKET")
                    .entityId(ticket.getId())
                    .breachStage("TICKET_OVERDUE")
                    .build());

            ticketAlerts++;
            log.warn("Cảnh báo SLA Ticket #{}: quá hạn {}", ticket.getReferenceNo(), deadlineText);
        }

        // ---------------------------------------------------------------------
        // 2. Quét tranh chấp cấp Customer Care: Trạng thái OPEN, INVESTIGATING quá hạn SLA
        // ---------------------------------------------------------------------
        List<Dispute> overdueCcDisputes = disputeRepository.findByStatusInAndSlaDeadlineBefore(
                List.of(DisputeStatus.OPEN, DisputeStatus.INVESTIGATING), now
        );
        disputeCcBreaches = overdueCcDisputes.size();

        for (Dispute dispute : overdueCcDisputes) {
            boolean alreadyAlerted = slaAlertLogRepository.existsByEntityTypeAndEntityIdAndBreachStage(
                    "DISPUTE", dispute.getDisputeId(), "DISPUTE_CC_OVERDUE"
            );
            if (alreadyAlerted) {
                continue;
            }

            String deadlineText = dispute.getSlaDeadline() != null ? TIME_FORMATTER.format(dispute.getSlaDeadline()) : "Chưa xác định";
            String title = "Cảnh báo quá hạn SLA tranh chấp";
            String body = String.format("Tranh chấp '%s' (Mã: %s) đã quá hạn xử lý sơ bộ theo SLA (Hạn chót: %s). Yêu cầu Chăm sóc khách hàng ưu tiên thẩm định.",
                    dispute.getTitle(), dispute.getDisputeId(), deadlineText);

            if (dispute.getAssignedTo() != null) {
                notificationService.sendNotification(dispute.getAssignedTo(), title, body, NotificationType.DISPUTE, "DISPUTE", dispute.getDisputeId());
            } else {
                notificationService.notifyRole(UserRole.CUSTOMER_CARE, title, body, NotificationType.DISPUTE, "DISPUTE", dispute.getDisputeId());
            }

            auditLogService.logAction(AuditLog.builder()
                    .action("SLA_BREACH_DISPUTE_CC")
                    .entityType("DISPUTE")
                    .entityId(dispute.getDisputeId())
                    .newValues(String.format("{\"title\":\"%s\",\"slaDeadline\":\"%s\",\"status\":\"%s\"}",
                            dispute.getTitle(), dispute.getSlaDeadline(), dispute.getStatus()))
                    .build());

            slaAlertLogRepository.save(SlaAlertLog.builder()
                    .entityType("DISPUTE")
                    .entityId(dispute.getDisputeId())
                    .breachStage("DISPUTE_CC_OVERDUE")
                    .build());

            disputeCcAlerts++;
            log.warn("Cảnh báo SLA Dispute CC {}: quá hạn {}", dispute.getDisputeId(), deadlineText);
        }

        // ---------------------------------------------------------------------
        // 3. Quét tranh chấp khiếu nại cấp Quản trị viên (ESCALATED quá hạn)
        // ---------------------------------------------------------------------
        List<Dispute> escalatedDisputes = disputeRepository.findByStatusIn(List.of(DisputeStatus.ESCALATED));
        for (Dispute dispute : escalatedDisputes) {
            boolean isOverdue = false;
            if (dispute.getSlaDeadline() != null && dispute.getSlaDeadline().isBefore(now)) {
                isOverdue = true;
            } else if (dispute.getEscalatedAt() != null && dispute.getEscalatedAt().plus(Duration.ofDays(7)).isBefore(now)) {
                isOverdue = true;
            }

            if (!isOverdue) {
                continue;
            }
            disputeAdminBreaches++;

            boolean alreadyAlerted = slaAlertLogRepository.existsByEntityTypeAndEntityIdAndBreachStage(
                    "DISPUTE", dispute.getDisputeId(), "DISPUTE_ESCALATED_OVERDUE"
            );
            if (alreadyAlerted) {
                continue;
            }

            String title = "Cảnh báo quá hạn xử lý tranh chấp khiếu nại (Admin)";
            String body = String.format("Tranh chấp khiếu nại '%s' (Mã: %s) đã quá hạn phân xử cấp Quản trị viên. Yêu cầu Admin đưa ra phán quyết cuối cùng.",
                    dispute.getTitle(), dispute.getDisputeId());

            notificationService.notifyRole(UserRole.ADMIN, title, body, NotificationType.DISPUTE, "DISPUTE", dispute.getDisputeId());

            auditLogService.logAction(AuditLog.builder()
                    .action("SLA_BREACH_DISPUTE_ESCALATED")
                    .entityType("DISPUTE")
                    .entityId(dispute.getDisputeId())
                    .newValues(String.format("{\"title\":\"%s\",\"status\":\"ESCALATED\",\"escalatedAt\":\"%s\"}",
                            dispute.getTitle(), dispute.getEscalatedAt()))
                    .build());

            slaAlertLogRepository.save(SlaAlertLog.builder()
                    .entityType("DISPUTE")
                    .entityId(dispute.getDisputeId())
                    .breachStage("DISPUTE_ESCALATED_OVERDUE")
                    .build());

            disputeAdminAlerts++;
            log.warn("Cảnh báo SLA Dispute ESCALATED {}: quá hạn Admin xử lý", dispute.getDisputeId());
        }

        int totalAlerts = ticketAlerts + disputeCcAlerts + disputeAdminAlerts;
        log.info("Hoàn tất quét SLA: phát hiện {} vi phạm ticket (gửi {} cảnh báo), {} vi phạm dispute CC (gửi {}), {} vi phạm dispute Admin (gửi {}). Tổng cảnh báo phát đi: {}",
                ticketBreaches, ticketAlerts, disputeCcBreaches, disputeCcAlerts, disputeAdminBreaches, disputeAdminAlerts, totalAlerts);

        return SlaScanReportDto.builder()
                .scannedAt(now)
                .ticketBreachesFound(ticketBreaches)
                .ticketAlertsSent(ticketAlerts)
                .disputeCcBreachesFound(disputeCcBreaches)
                .disputeCcAlertsSent(disputeCcAlerts)
                .disputeAdminBreachesFound(disputeAdminBreaches)
                .disputeAdminAlertsSent(disputeAdminAlerts)
                .totalAlertsSent(totalAlerts)
                .build();
    }
}

