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
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.cani.repository.SupportConversationRepository supportConversationRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.cani.repository.SupportMessageRepository supportMessageRepository;

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

        if (ticket.getConversationId() != null && supportConversationRepository != null) {
            Optional<com.cani.entity.SupportConversation> convOpt = supportConversationRepository.findById(ticket.getConversationId());
            if (convOpt.isPresent()) {
                com.cani.entity.SupportConversation conv = convOpt.get();
                if (ticket.getId().equals(conv.getActiveTicketId())) {
                    conv.setActiveTicketId(null);
                    supportConversationRepository.save(conv);
                }
            }
        }

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

        // Tự động gỡ active_ticket_id và bắn thông báo vào Live Chat nếu có liên kết
        if (ticket.getConversationId() != null && supportConversationRepository != null) {
            Optional<com.cani.entity.SupportConversation> convOpt = supportConversationRepository.findById(ticket.getConversationId());
            if (convOpt.isPresent()) {
                com.cani.entity.SupportConversation conv = convOpt.get();
                if (ticket.getId().equals(conv.getActiveTicketId())) {
                    conv.setActiveTicketId(null);
                    supportConversationRepository.save(conv);
                }
                if (supportMessageRepository != null) {
                    supportMessageRepository.save(com.cani.entity.SupportMessage.builder()
                            .conversationId(conv.getId())
                            .senderId(ccId)
                            .senderRole("CUSTOMER_CARE")
                            .senderName("Hệ thống CANi Support")
                            .messageText("✅ Phiếu hỗ trợ #" + ticket.getReferenceNo() + " đã được xử lý hoàn tất và đóng. Lý do: " + req.getRationale())
                            .isRead(false)
                            .build());
                }
            }
        }

        return toResponseDto(ticket, true);
    }

    // ------------------------------------------------------------------
    // OMNICHANNEL: Leo thang phiên Live Chat thành Ticket SLA (Escalate to Ticket)
    // ------------------------------------------------------------------
    @Transactional
    public SupportTicketResponseDto escalateChatToTicket(UUID conversationId, EscalateChatToTicketRequestDto req, UUID staffId) {
        if (supportConversationRepository == null) {
            throw new IllegalStateException("Hệ thống hội thoại chưa được cấu hình");
        }
        com.cani.entity.SupportConversation conv = supportConversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Hội thoại", "id", conversationId));

        if (conv.getActiveTicketId() != null) {
            Optional<SupportTicket> existingTicketOpt = supportTicketRepository.findById(conv.getActiveTicketId());
            if (existingTicketOpt.isPresent() && existingTicketOpt.get().getStatus() != TicketStatus.CLOSED) {
                throw new BadRequestException("Phiên chat này đã có ticket đang xử lý (#" + existingTicketOpt.get().getReferenceNo() + ")");
            }
        }

        TicketPriority priority = req.getPriority() != null ? req.getPriority() : mapCategoryToPriority(req.getCategory());
        Instant slaDeadline = calculateSlaDeadline(priority);
        String referenceNo = generateReferenceNo();

        // Lấy tóm tắt tin nhắn gần nhất nếu không có customDescription
        String description = req.getCustomDescription();
        if (description == null || description.isBlank()) {
            if (supportMessageRepository != null) {
                List<com.cani.entity.SupportMessage> recentMsgs = supportMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
                if (!recentMsgs.isEmpty()) {
                    int startIdx = Math.max(0, recentMsgs.size() - 8);
                    StringBuilder sb = new StringBuilder("--- Ngữ cảnh trích xuất từ phiên Live Chat ---\n");
                    for (int i = startIdx; i < recentMsgs.size(); i++) {
                        com.cani.entity.SupportMessage m = recentMsgs.get(i);
                        sb.append("[").append(m.getSenderRole()).append("] ")
                                .append(m.getSenderName() != null ? m.getSenderName() : "User")
                                .append(": ").append(m.getMessageText()).append("\n");
                    }
                    description = sb.toString();
                } else {
                    description = "Chuyển đổi từ phiên Live Chat.";
                }
            } else {
                description = "Chuyển đổi từ phiên Live Chat.";
            }
        }

        SupportTicket ticket = SupportTicket.builder()
                .referenceNo(referenceNo)
                .userId(conv.getUserId())
                .conversationId(conversationId)
                .category(req.getCategory())
                .priority(priority)
                .subject(req.getSubject())
                .description(description)
                .status(TicketStatus.IN_PROGRESS)
                .assignedTo(staffId)
                .slaDeadline(slaDeadline)
                .build();

        ticket = supportTicketRepository.save(ticket);

        // Tạo tin nhắn mở đầu thread
        supportTicketMessageRepository.save(SupportTicketMessage.builder()
                .ticketId(ticket.getId())
                .senderId(staffId)
                .body("Chuyên viên CSKH đã chuyển sự cố từ phiên Live Chat sang Ticket SLA #" + referenceNo + ".\n" + description)
                .isInternal(false)
                .build());

        // Cập nhật active_ticket_id cho conversation
        conv.setActiveTicketId(ticket.getId());
        supportConversationRepository.save(conv);

        // Bắn tin nhắn bot vào Live Chat để thông báo cho User
        if (supportMessageRepository != null) {
            supportMessageRepository.save(com.cani.entity.SupportMessage.builder()
                    .conversationId(conversationId)
                    .senderId(staffId)
                    .senderRole("CUSTOMER_CARE")
                    .senderName("Hệ thống CANi Support")
                    .messageText("🔔 Yêu cầu hỗ trợ của bạn đã được chuyển thành Phiếu hỗ trợ chính thức mã #" + referenceNo
                            + " (" + req.getCategory() + " · Mức độ: " + priority + "). Chuyên viên đang thụ lý.")
                    .isRead(false)
                    .build());
        }

        // Ghi Audit log
        recordAudit(staffId, "ESCALATE_CHAT_TO_TICKET", ticket, null, Map.of(
                "conversationId", conversationId.toString(),
                "referenceNo", referenceNo
        ));

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
        return toQueueItems(activeTickets);
    }

    /**
     * SCR-25 (phân tầng CC): Hàng đợi chỉ gồm các phiếu đang được giao cho CC này.
     */
    public List<SupportTicketQueueItemDto> getQueueForCc(UUID ccUserId) {
        List<SupportTicket> activeTickets = supportTicketRepository.findByStatusInAndAssignedTo(
                List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS), ccUserId
        );
        return toQueueItems(activeTickets);
    }

    /**
     * SCR-25 / SCR-26: Danh sách nhân sự Customer Care kèm số lượng phiếu đang xử lý để cân bằng tải.
     */
    public List<CustomerCareStaffDto> getCustomerCareStaff() {
        return userRepository.findByRole(UserRole.CUSTOMER_CARE).stream()
                .map(cc -> CustomerCareStaffDto.builder()
                        .userId(cc.getUserId())
                        .fullName(cc.getFullName())
                        .email(cc.getEmail())
                        .assignedCount(supportTicketRepository.countByStatusInAndAssignedTo(
                                List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS), cc.getUserId()
                        ))
                        .build())
                .toList();
    }

    private List<SupportTicketQueueItemDto> toQueueItems(List<SupportTicket> activeTickets) {
        Instant now = Instant.now();
        Instant riskThreshold = now.plus(24, ChronoUnit.HOURS);

        Set<UUID> allUserIds = activeTickets.stream()
                .flatMap(t -> java.util.stream.Stream.of(t.getUserId(), t.getAssignedTo()))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, User> userMap = userRepository.findAllById(allUserIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getUserId, u -> u, (a, b) -> a));

        return activeTickets.stream()
                .map(ticket -> {
                    // TODO (Optimization): N+1 query chấp nhận được ở quy mô MVP (queue limit 50). Khi scale lớn,
                    // chuyển sang @Query với MAX(created_at) GROUP BY ticket_id để nạp toàn bộ lastMessageAt trong 1 truy vấn.
                    Instant lastMessageAt = supportTicketMessageRepository
                            .findFirstByTicketIdOrderByCreatedAtDesc(ticket.getId())
                            .map(SupportTicketMessage::getCreatedAt)
                            .orElse(ticket.getCreatedAt());

                    boolean slaAtRisk = ticket.getSlaDeadline().isBefore(riskThreshold);
                    User customer = ticket.getUserId() != null ? userMap.get(ticket.getUserId()) : null;
                    User staff = ticket.getAssignedTo() != null ? userMap.get(ticket.getAssignedTo()) : null;
                    String assignedName = staff != null ? staff.getFullName() : null;
                    String assignedRole = staff != null ? (staff.getRole() != null && staff.getRole().name().equals("CUSTOMER_CARE") ? "CSKH" : (staff.getRole() != null ? staff.getRole().name() : "CSKH")) : null;

                    return SupportTicketQueueItemDto.builder()
                            .id(ticket.getId())
                            .referenceNo(ticket.getReferenceNo())
                            .conversationId(ticket.getConversationId())
                            .category(ticket.getCategory())
                            .priority(ticket.getPriority())
                            .subject(ticket.getSubject())
                            .status(ticket.getStatus())
                            .userId(ticket.getUserId())
                            .userName(customer != null ? customer.getFullName() : null)
                            .userPhone(customer != null ? customer.getPhone() : null)
                            .userEmail(customer != null ? customer.getEmail() : null)
                            .userAvatar(customer != null ? customer.getAvatarUrl() : null)
                            .assignedTo(ticket.getAssignedTo())
                            .assignedName(assignedName)
                            .assignedRole(assignedRole)
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
        if (category == TicketCategory.BILLING || category == TicketCategory.ACCOUNT) {
            return TicketPriority.HIGH;
        }
        if (category == TicketCategory.TECHNICAL || category == TicketCategory.GENERAL) {
            return TicketPriority.MEDIUM;
        }
        return TicketPriority.LOW;
    }

    public Instant calculateSlaDeadline(TicketPriority priority) {
        Instant now = Instant.now();
        if (priority == TicketPriority.HIGH) {
            return now.plus(4, ChronoUnit.HOURS);
        }
        if (priority == TicketPriority.MEDIUM) {
            return now.plus(1, ChronoUnit.DAYS);
        }
        return now.plus(3, ChronoUnit.DAYS);
    }

    private String generateReferenceNo() {
        String dateStr = DATE_FORMATTER.format(Instant.now());
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
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
        try {
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .title(title)
                    .body(body)
                    .type(NotificationType.SUPPORT_TICKET)
                    .referenceType("SUPPORT_TICKET")
                    .referenceId(ticket.getId())
                    .build());
        } catch (Exception ex) {
            // Thông báo phụ trợ không được làm rollback giao dịch tạo/phản hồi ticket
            log.warn("Không thể lưu thông báo Support Ticket {} cho user {}: {}",
                    ticket != null ? ticket.getId() : null,
                    user != null ? user.getUserId() : null,
                    ex.getMessage());
        }
    }

    private void notifyUser(UUID userId, String title, String body, SupportTicket ticket) {
        if (userId == null) {
            return;
        }
        try {
            userRepository.findById(userId).ifPresent(u -> saveNotification(u, title, body, ticket));
        } catch (Exception ex) {
            log.warn("Không thể gửi thông báo Support Ticket tới user {}: {}", userId, ex.getMessage());
        }
    }

    private void notifyCcTeam(SupportTicket ticket) {
        try {
            userRepository.findByRole(UserRole.CUSTOMER_CARE).forEach(cc ->
                    saveNotification(cc, "Có ticket hỗ trợ mới trong hàng đợi",
                            "Ticket " + ticket.getReferenceNo() + " (" + ticket.getCategory() + ") cần tiếp nhận.",
                            ticket));
        } catch (Exception ex) {
            log.warn("Không thể thông báo tới đội CSKH cho ticket {}: {}",
                    ticket != null ? ticket.getReferenceNo() : null, ex.getMessage());
        }
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

    @SuppressWarnings("unchecked")
    private SupportTicketResponseDto toResponseDto(SupportTicket ticket, boolean isStaff) {
        List<String> attachmentsList = Collections.emptyList();
        if (ticket.getAttachments() != null && !ticket.getAttachments().isBlank()) {
            try {
                attachmentsList = objectMapper.readValue(ticket.getAttachments(), List.class);
            } catch (Exception e) {
                log.warn("Không thể parse attachments cho ticket {}: {}", ticket.getId(), e.getMessage());
            }
        }

        List<SupportTicketMessage> messages = supportTicketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());

        Set<UUID> senderIds = messages.stream()
                .map(SupportTicketMessage::getSenderId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, User> senderMap = userRepository.findAllById(senderIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getUserId, u -> u, (a, b) -> a));

        List<SupportTicketResponseDto.MessageDto> messageDtos = messages.stream()
                .filter(m -> isStaff || !Boolean.TRUE.equals(m.getIsInternal()))
                .map(m -> {
                    User sender = m.getSenderId() != null ? senderMap.get(m.getSenderId()) : null;
                    boolean staffMsg = isStaff(sender);
                    String senderRoleName = staffMsg ? "CUSTOMER_CARE" : "USER";
                    String senderDisplayName = staffMsg ? "Chuyên viên hỗ trợ" : "Người gửi";

                    return SupportTicketResponseDto.MessageDto.builder()
                            .id(m.getId())
                            .ticketId(m.getTicketId())
                            .senderId(m.getSenderId())
                            .senderName(senderDisplayName)
                            .senderRole(senderRoleName)
                            .isStaffReply(staffMsg)
                            .body(m.getBody())
                            .message(m.getBody())
                            .isInternal(m.getIsInternal())
                            .attachmentUrl(m.getAttachmentUrl())
                            .createdAt(m.getCreatedAt())
                            .build();
                })
                .toList();

        boolean slaAtRisk = ticket.getStatus() != TicketStatus.CLOSED
                && ticket.getSlaDeadline() != null
                && ticket.getSlaDeadline().isBefore(Instant.now().plus(24, ChronoUnit.HOURS));

        boolean canReply = ticket.getStatus() != TicketStatus.CLOSED;
        User customer = ticket.getUserId() != null ? userRepository.findById(ticket.getUserId()).orElse(null) : null;
        User staff = ticket.getAssignedTo() != null ? userRepository.findById(ticket.getAssignedTo()).orElse(null) : null;
        String assignedName = staff != null ? staff.getFullName() : null;
        String assignedRole = staff != null ? (staff.getRole() != null && staff.getRole().name().equals("CUSTOMER_CARE") ? "CSKH" : (staff.getRole() != null ? staff.getRole().name() : "CSKH")) : null;

        return SupportTicketResponseDto.builder()
                .id(ticket.getId())
                .referenceNo(ticket.getReferenceNo())
                .conversationId(ticket.getConversationId())
                .userId(ticket.getUserId())
                .userName(customer != null ? customer.getFullName() : null)
                .userPhone(customer != null ? customer.getPhone() : null)
                .userEmail(customer != null ? customer.getEmail() : null)
                .userAvatar(customer != null ? customer.getAvatarUrl() : null)
                .category(ticket.getCategory())
                .priority(ticket.getPriority())
                .subject(ticket.getSubject())
                .description(ticket.getDescription())
                .attachments(attachmentsList)
                .status(ticket.getStatus())
                .assignedTo(ticket.getAssignedTo())
                .assignedName(assignedName)
                .assignedRole(assignedRole)
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

    private static final String TICKET_UPLOAD_DIR = "uploads/support-tickets";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "pdf", "doc", "docx", "xls", "xlsx", "txt", "csv", "zip"
    );

    /**
     * Tải tệp đính kèm / hình ảnh cho ticket hỗ trợ (UC-105 / UC-106).
     * Hỗ trợ cả hình ảnh và các tệp tài liệu văn bản (PDF, DOCX, TXT, XLS...).
     */
    public String uploadAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tải lên không được để trống");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BadRequestException("Chỉ chấp nhận các định dạng tệp: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BadRequestException("Dung lượng tệp không được vượt quá 10MB");
        }

        try {
            Path uploadPath = Paths.get(TICKET_UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String savedFilename = "tkt_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path targetPath = uploadPath.resolve(savedFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return "/api/v1/support/tickets/attachments/" + savedFilename;
        } catch (IOException e) {
            log.error("Lỗi khi lưu tệp đính kèm ticket: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể lưu tệp đính kèm: " + e.getMessage());
        }
    }

    /**
     * Tải tài nguyên tệp đính kèm đã lưu theo tên file.
     */
    public Resource loadAttachment(String filename) {
        try {
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                throw new BadRequestException("Tên tệp không hợp lệ");
            }
            Path filePath = Paths.get(TICKET_UPLOAD_DIR).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Không tìm thấy tệp đính kèm: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Đường dẫn tệp không hợp lệ: " + filename);
        }
    }
}

