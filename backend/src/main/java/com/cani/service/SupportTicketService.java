package com.cani.service;

import com.cani.dto.support.*;
import com.cani.entity.AuditLog;
import com.cani.entity.Notification;
import com.cani.entity.SupportTicket;
import com.cani.entity.SupportTicketMessage;
import com.cani.entity.User;
import com.cani.entity.enums.*;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.NotificationRepository;
import com.cani.repository.SupportTicketMessageRepository;
import com.cani.repository.SupportTicketRepository;
import com.cani.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Xử lý nghiệp vụ Support Tickets (SRS FE-12, UC-105 / UC-106 · SCR-25 / SCR-26).
 * Quy tắc SLA (BR-58):
 * - HIGH (BILLING, ACCOUNT): phản hồi 4h
 * - MEDIUM (TECHNICAL, GENERAL): phản hồi 1 ngày
 * - LOW (OTHER): phản hồi 3 ngày
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketMessageRepository supportTicketMessageRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    // ------------------------------------------------------------------
    // UC-105: Nộp ticket hỗ trợ
    // ------------------------------------------------------------------

    @Transactional
    public SupportTicketResponseDto createTicket(UUID userId, CreateTicketRequestDto req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));

        TicketPriority priority = mapCategoryToPriority(req.getCategory());
        Instant slaDeadline = calculateSlaDeadline(priority);
        String referenceNo = generateReferenceNo();

        String attachmentsJson = null;
        if (req.getAttachments() != null && !req.getAttachments().isEmpty()) {
            try {
                attachmentsJson = objectMapper.writeValueAsString(req.getAttachments());
            } catch (JsonProcessingException e) {
                log.warn("Không thể serialize attachments ticket: {}", e.getMessage());
            }
        }

        SupportTicket ticket = SupportTicket.builder()
                .referenceNo(referenceNo)
                .userId(userId)
                .category(req.getCategory())
                .priority(priority)
                .subject(req.getSubject())
                .description(req.getDescription())
                .attachments(attachmentsJson)
                .status(TicketStatus.OPEN)
                .slaDeadline(slaDeadline)
                .build();

        ticket = supportTicketRepository.save(ticket);

        // Tạo tin nhắn mở đầu thread từ mô tả của user
        SupportTicketMessage initialMsg = SupportTicketMessage.builder()
                .ticketId(ticket.getId())
                .senderId(userId)
                .body(req.getDescription())
                .isInternal(false)
                .build();
        supportTicketMessageRepository.save(initialMsg);

        // Thông báo đội Customer Care
        notifyCcTeam(ticket);

        // Ghi Audit log (BR-70)
        recordAudit(userId, "SUBMIT_TICKET", ticket, null, Map.of(
                "referenceNo", ticket.getReferenceNo(),
                "category", ticket.getCategory().name(),
                "priority", ticket.getPriority().name()
        ));

        return toResponseDto(ticket, false);
    }

    // ------------------------------------------------------------------
    // UC-106: Danh sách ticket của tôi
    // ------------------------------------------------------------------

    public List<SupportTicketResponseDto> getMyTickets(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Người dùng", "id", userId);
        }
        return supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ticket -> toResponseDto(ticket, false))
                .toList();
    }

    // ------------------------------------------------------------------
    // UC-106 / SCR-26: Xem chi tiết ticket
    // ------------------------------------------------------------------

    public SupportTicketResponseDto getTicketDetail(UUID ticketId, UUID requesterId) {
        SupportTicket ticket = getTicketOrThrow(ticketId);
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", requesterId));

        boolean isOwner = ticket.getUserId().equals(requesterId);
        boolean isStaff = isStaff(requester);

        if (!isOwner && !isStaff) {
            throw new BadRequestException("Bạn không có quyền xem ticket này (MSG34)");
        }

        return toResponseDto(ticket, isStaff);
    }

    // ------------------------------------------------------------------
    // UC-106 / SCR-26: Phản hồi ticket (Reply & Internal Note)
    // ------------------------------------------------------------------

    @Transactional
    public SupportTicketResponseDto reply(UUID ticketId, UUID requesterId, ReplyTicketRequestDto req) {
        SupportTicket ticket = getTicketOrThrow(ticketId);

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BadRequestException("Ticket đã đóng, không thể phản hồi");
        }

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", requesterId));

        boolean isOwner = ticket.getUserId().equals(requesterId);
        boolean isStaff = isStaff(requester);

        if (!isOwner && !isStaff) {
            throw new BadRequestException("Bạn không có quyền phản hồi ticket này");
        }

        boolean isInternal = Boolean.TRUE.equals(req.getIsInternal());
        if (isInternal && !isStaff) {
            throw new BadRequestException("Người dùng thường không được tạo ghi chú nội bộ");
        }

        SupportTicketMessage msg = SupportTicketMessage.builder()
                .ticketId(ticket.getId())
                .senderId(requesterId)
                .body(req.getBody())
                .isInternal(isInternal)
                .attachmentUrl(req.getAttachmentUrl())
                .build();
        supportTicketMessageRepository.save(msg);

        // Thông báo phía đối diện
        if (isStaff) {
            if (!isInternal) {
                notifyUser(ticket.getUserId(), "Có phản hồi mới cho ticket " + ticket.getReferenceNo(),
                        "Chuyên viên hỗ trợ đã phản hồi: " + previewText(req.getBody()), ticket);
            }
        } else {
            if (ticket.getAssignedTo() != null) {
                notifyUser(ticket.getAssignedTo(), "Người dùng đã phản hồi ticket " + ticket.getReferenceNo(),
                        "Nội dung phản hồi: " + previewText(req.getBody()), ticket);
            } else {
                notifyCcTeam(ticket);
            }
        }

        // Ghi Audit log (BR-70)
        recordAudit(requesterId, "REPLY_TICKET", ticket, null, Map.of(
                "isInternal", String.valueOf(isInternal)
        ));

        return toResponseDto(ticket, isStaff);
    }

    // ------------------------------------------------------------------
    // UC-106: Người dùng đánh dấu đã giải quyết (Mark Resolved)
    // ------------------------------------------------------------------

    @Transactional
    public SupportTicketResponseDto markResolved(UUID ticketId, UUID userId) {
        SupportTicket ticket = getTicketOrThrow(ticketId);

        if (!ticket.getUserId().equals(userId)) {
            throw new BadRequestException("Chỉ người nộp ticket mới được đánh dấu đã giải quyết");
        }

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BadRequestException("Ticket đã được đóng trước đó");
        }

        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setResolvedBy(userId);
        ticket.setClosedAt(Instant.now());
        ticket = supportTicketRepository.save(ticket);

        supportTicketMessageRepository.save(SupportTicketMessage.builder()
                .ticketId(ticket.getId())
                .senderId(userId)
                .body("Người dùng đã xác nhận giải quyết vấn đề và đóng ticket.")
                .isInternal(false)
                .build());

        if (ticket.getAssignedTo() != null) {
            notifyUser(ticket.getAssignedTo(), "Ticket " + ticket.getReferenceNo() + " đã được người dùng đóng",
                    "Người dùng đã xác nhận vấn đề được giải quyết.", ticket);
        }

        recordAudit(userId, "CLOSE_TICKET", ticket,
                Map.of("status", oldStatus.name()),
                Map.of("status", TicketStatus.CLOSED.name(), "resolvedBy", userId.toString()));

        return toResponseDto(ticket, false);
    }

    // ------------------------------------------------------------------
    // SCR-26: Customer Care / Admin đóng ticket kèm lý do
    // ------------------------------------------------------------------

    @Transactional
    public SupportTicketResponseDto closeTicket(UUID ticketId, UUID ccId, CloseTicketRequestDto req) {
        SupportTicket ticket = getTicketOrThrow(ticketId);
        User cc = userRepository.findById(ccId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", ccId));

        if (!isStaff(cc)) {
            throw new BadRequestException("Chỉ Chăm sóc khách hàng hoặc Admin mới có quyền đóng ticket");
        }

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BadRequestException("Ticket đã được đóng trước đó");
        }

        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setResolvedBy(ccId);
        ticket.setClosedAt(Instant.now());
        ticket = supportTicketRepository.save(ticket);

        supportTicketMessageRepository.save(SupportTicketMessage.builder()
                .ticketId(ticket.getId())
                .senderId(ccId)
                .body("Đóng ticket: " + req.getRationale())
                .isInternal(false)
                .build());

        notifyUser(ticket.getUserId(), "Ticket " + ticket.getReferenceNo() + " đã được đóng",
                "Lý do: " + req.getRationale(), ticket);

        recordAudit(ccId, "CLOSE_TICKET", ticket,
                Map.of("status", oldStatus.name()),
                Map.of("status", TicketStatus.CLOSED.name(), "rationale", req.getRationale()));

        return toResponseDto(ticket, true);
    }

    // ------------------------------------------------------------------
    // SCR-25 / SCR-26: Phân công hoặc nhận ticket
    // ------------------------------------------------------------------

    @Transactional
    public SupportTicketResponseDto assignTicket(UUID ticketId, UUID actorId, AssignTicketRequestDto req) {
        SupportTicket ticket = getTicketOrThrow(ticketId);
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", actorId));

        if (!isStaff(actor)) {
            throw new BadRequestException("Chỉ Chăm sóc khách hàng hoặc Admin mới có quyền phân công ticket");
        }

        UUID targetCcUserId = (req != null && req.getTargetCcUserId() != null)
                ? req.getTargetCcUserId()
                : actorId;

        User targetCc = userRepository.findById(targetCcUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", targetCcUserId));

        if (!isStaff(targetCc)) {
            throw new BadRequestException("Người nhận phân công phải có vai trò Chăm sóc khách hàng hoặc Quản trị viên");
        }

        UUID oldAssigned = ticket.getAssignedTo();
        ticket.setAssignedTo(targetCcUserId);
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        ticket = supportTicketRepository.save(ticket);

        notifyUser(targetCcUserId, "Bạn được phân công xử lý ticket " + ticket.getReferenceNo(),
                "Vui lòng kiểm tra và xử lý ticket theo quy chuẩn SLA.", ticket);

        recordAudit(actorId, "ASSIGN_TICKET", ticket,
                Map.of("assignedTo", oldAssigned != null ? oldAssigned.toString() : "null"),
                Map.of("assignedTo", targetCcUserId.toString(), "status", ticket.getStatus().name()));

        return toResponseDto(ticket, true);
    }

    // ------------------------------------------------------------------
    // SCR-25: Hàng đợi ticket cho Customer Care
    // ------------------------------------------------------------------

    public List<SupportTicketQueueItemDto> getQueue() {
        List<SupportTicket> activeTickets = supportTicketRepository.findByStatusInOrderBySlaDeadlineAsc(
                List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)
        );

        Instant now = Instant.now();
        Instant riskThreshold = now.plus(24, ChronoUnit.HOURS);

        return activeTickets.stream()
                .map(ticket -> {
                    // TODO (Optimization): N+1 query chấp nhận được ở quy mô MVP (queue limit 50). Khi scale lớn,
                    // chuyển sang @Query với MAX(created_at) GROUP BY ticket_id để nạp toàn bộ lastMessageAt trong 1 truy vấn.
                    Instant lastMessageAt = supportTicketMessageRepository
                            .findFirstByTicketIdOrderByCreatedAtDesc(ticket.getId())
                            .map(SupportTicketMessage::getCreatedAt)
                            .orElse(ticket.getCreatedAt());

                    boolean slaAtRisk = ticket.getSlaDeadline().isBefore(riskThreshold);

                    return SupportTicketQueueItemDto.builder()
                            .id(ticket.getId())
                            .referenceNo(ticket.getReferenceNo())
                            .category(ticket.getCategory())
                            .priority(ticket.getPriority())
                            .subject(ticket.getSubject())
                            .status(ticket.getStatus())
                            .userId(ticket.getUserId())
                            .assignedTo(ticket.getAssignedTo())
                            .slaDeadline(ticket.getSlaDeadline())
                            .lastMessageAt(lastMessageAt)
                            .slaAtRisk(slaAtRisk)
                            .createdAt(ticket.getCreatedAt())
                            .build();
                })
                .sorted((a, b) -> {
                    // 1) slaAtRisk trước
                    int riskCompare = Boolean.compare(b.getSlaAtRisk(), a.getSlaAtRisk());
                    if (riskCompare != 0) {
                        return riskCompare;
                    }
                    // 2) Cũ nhất trước theo createdAt
                    if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
                        return a.getCreatedAt().compareTo(b.getCreatedAt());
                    }
                    return 0;
                })
                .limit(50)
                .toList();
    }

    // ------------------------------------------------------------------
    // Helpers & Mappers
    // ------------------------------------------------------------------

    public TicketPriority mapCategoryToPriority(TicketCategory category) {
        if (category == null) {
            return TicketPriority.LOW;
        }
        return switch (category) {
            case BILLING, ACCOUNT -> TicketPriority.HIGH;
            case TECHNICAL, GENERAL -> TicketPriority.MEDIUM;
            case OTHER -> TicketPriority.LOW;
        };
    }

    public Instant calculateSlaDeadline(TicketPriority priority) {
        Instant now = Instant.now();
        if (priority == null) {
            return now.plus(3, ChronoUnit.DAYS);
        }
        return switch (priority) {
            case HIGH -> now.plus(4, ChronoUnit.HOURS);
            case MEDIUM -> now.plus(1, ChronoUnit.DAYS);
            case LOW -> now.plus(3, ChronoUnit.DAYS);
        };
    }

    private String generateReferenceNo() {
        String dateStr = DATE_FORMATTER.format(Instant.now());
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "TKT-" + dateStr + "-" + randomSuffix;
    }

    private boolean isStaff(User user) {
        return user != null && (user.getRole() == UserRole.CUSTOMER_CARE || user.getRole() == UserRole.ADMIN);
    }

    private String previewText(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }

    private SupportTicket getTicketOrThrow(UUID ticketId) {
        return supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket hỗ trợ", "id", ticketId));
    }

    private void saveNotification(User user, String title, String body, SupportTicket ticket) {
        notificationRepository.save(Notification.builder()
                .user(user)
                .title(title)
                .body(body)
                .type(NotificationType.SUPPORT_TICKET)
                .referenceType("SUPPORT_TICKET")
                .referenceId(ticket.getId())
                .build());
    }

    private void notifyUser(UUID userId, String title, String body, SupportTicket ticket) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(u -> saveNotification(u, title, body, ticket));
    }

    private void notifyCcTeam(SupportTicket ticket) {
        userRepository.findByRole(UserRole.CUSTOMER_CARE).forEach(cc ->
                saveNotification(cc, "Có ticket hỗ trợ mới trong hàng đợi",
                        "Ticket " + ticket.getReferenceNo() + " (" + ticket.getCategory() + ") cần tiếp nhận.",
                        ticket));
    }

    private void recordAudit(UUID actorUserId, String action, SupportTicket ticket,
                             Map<String, String> oldValues, Map<String, String> newValues) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(actorUserId)
                    .action(action)
                    .entityType("SUPPORT_TICKET")
                    .entityId(ticket.getId())
                    .oldValues(oldValues != null ? objectMapper.writeValueAsString(oldValues) : null)
                    .newValues(newValues != null ? objectMapper.writeValueAsString(newValues) : null)
                    .build();
            auditLogService.logAction(entry);
        } catch (JsonProcessingException e) {
            log.error("Không thể chuẩn bị AuditLog cho ticket {}: {}", ticket.getId(), e.getMessage());
        }
    }

    private SupportTicketResponseDto toResponseDto(SupportTicket ticket, boolean isStaff) {
        List<String> attachmentsList = Collections.emptyList();
        if (ticket.getAttachments() != null && !ticket.getAttachments().isBlank()) {
            try {
                attachmentsList = objectMapper.readValue(ticket.getAttachments(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("Không thể parse attachments cho ticket {}: {}", ticket.getId(), e.getMessage());
            }
        }

        List<SupportTicketMessage> messages = supportTicketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());

        List<SupportTicketResponseDto.MessageDto> messageDtos = messages.stream()
                .filter(m -> isStaff || !Boolean.TRUE.equals(m.getIsInternal()))
                .map(m -> SupportTicketResponseDto.MessageDto.builder()
                        .id(m.getId())
                        .ticketId(m.getTicketId())
                        .senderId(m.getSenderId())
                        .body(m.getBody())
                        .isInternal(m.getIsInternal())
                        .attachmentUrl(m.getAttachmentUrl())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();

        boolean slaAtRisk = ticket.getStatus() != TicketStatus.CLOSED
                && ticket.getSlaDeadline() != null
                && ticket.getSlaDeadline().isBefore(Instant.now().plus(24, ChronoUnit.HOURS));

        boolean canReply = ticket.getStatus() != TicketStatus.CLOSED;

        return SupportTicketResponseDto.builder()
                .id(ticket.getId())
                .referenceNo(ticket.getReferenceNo())
                .userId(ticket.getUserId())
                .category(ticket.getCategory())
                .priority(ticket.getPriority())
                .subject(ticket.getSubject())
                .description(ticket.getDescription())
                .attachments(attachmentsList)
                .status(ticket.getStatus())
                .assignedTo(ticket.getAssignedTo())
                .slaDeadline(ticket.getSlaDeadline())
                .resolvedBy(ticket.getResolvedBy())
                .closedAt(ticket.getClosedAt())
                .slaAtRisk(slaAtRisk)
                .canReply(canReply)
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .messages(messageDtos)
                .build();
    }
}

