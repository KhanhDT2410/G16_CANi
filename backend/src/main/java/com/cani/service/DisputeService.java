package com.cani.service;

import com.cani.dto.dispute.*;
import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý nghiệp vụ Dispute & Resolution (SRS FE-11, UC-96 → UC-104).
 *
 * Quy trình:
 * - Worker/EO nộp tranh chấp (UC-96, BR-52: cửa sổ 14 ngày, mô tả tối thiểu 50 ký tự)
 * - Nạp bằng chứng (UC-98)
 * - CC nhận đơn và điều tra (UC-99, BR-53 SLA)
 * - CC ra quyết định (UC-100) → bên thua có quyền khiếu nại trong 7 ngày (UC-101, BR-54)
 * - Admin quyết định cuối (UC-102, BR-68, ràng buộc không khiếu nại thêm)
 * - Penalty RS tự động khi quyết định WORKER_FAULT (UC-103, BR-55: −10 điểm)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisputeService {

    /** BR-52: tranh chấp chỉ được nộp trong 14 ngày kể từ khi ca kết thúc. */
    private static final int DISPUTE_WINDOW_DAYS = 14;
    /** BR-53: CC ra quyết định trong 7 ngày làm việc (xấp xỉ 7 ngày lịch trong MVP). */
    private static final int CC_DECISION_SLA_DAYS = 7;
    /** BR-54: bên liên quan được khiếu nại quyết định CC trong 7 ngày. */
    private static final int ESCALATION_WINDOW_DAYS = 7;
    /** BR-55: mức trừ RS khi quyết định tìm thấy lỗi thuộc về Worker. */
    private static final int DISPUTE_RS_PENALTY = 10;

    /** BR-60/61: RS floor/ceiling. */
    private static final int RS_FLOOR = 0;
    private static final int RS_CEILING = 100;

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository evidenceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final PaymentRepository paymentRepository;
    private final WorkerRepository workerRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final UserRepository userRepository;
    private final ReliabilityLogRepository reliabilityLogRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    private static final List<DisputeStatus> OPEN_STATUSES =
            List.of(DisputeStatus.OPEN, DisputeStatus.INVESTIGATING);
    private static final List<DisputeStatus> RESOLVED_STATUSES =
            List.of(DisputeStatus.RESOLVED, DisputeStatus.UNVERIFIED);

    // =========================================================================
    // UC-96: Nộp tranh chấp (Worker / Event Organizer)
    // =========================================================================

    @Transactional
    public DisputeDetailResponseDto createDispute(UUID requesterId, CreateDisputeRequestDto request) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(request.getAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc", "id", request.getAssignmentId()));

        PartyRef parties = resolveParties(requesterId, assignment);
        enforceDisputeWindow(assignment.getShift(), request.getType());

        if (disputeRepository.existsByAssignmentIdAndStatusIn(assignment.getAssignmentId(), OPEN_STATUSES)) {
            throw new BadRequestException(
                    "Đã có tranh chấp chưa được xử lý cho ca làm việc này (BR-52: không cho phép trùng lặp)");
        }

        Dispute dispute = Dispute.builder()
                .assignmentId(assignment.getAssignmentId())
                .complainantId(requesterId)
                .respondentId(parties.otherPartyId)
                .type(request.getType())
                .title(request.getTitle())
                .description(request.getDescription())
                .status(DisputeStatus.OPEN)
                .slaDeadline(Instant.now().plus(Duration.ofDays(CC_DECISION_SLA_DAYS)))
                .build();
        dispute = disputeRepository.save(dispute);

        // UC-96 bước 7: tranh chấp liên quan thanh toán → đặt payment về DISPUTED
        if (request.getType() == DisputeType.PAYMENT) {
            markPaymentDisputed(assignment.getAssignmentId());
        }

        notifyDisputeParties(dispute, parties, "Tranh chấp mới được tạo",
                "Bên liên quan đã nộp tranh chấp \"" + dispute.getTitle() + "\" (ID: " + dispute.getDisputeId()
                        + "). Hệ thống đã chuyển hồ sơ đến bộ phận Chăm sóc khách hàng.");
        notifyCcTeam(dispute);

        recordAudit(requesterId, "SUBMIT_DISPUTE", dispute, null,
                Map.of("type", request.getType().name(), "assignmentId", String.valueOf(assignment.getAssignmentId())));

        log.info("User {} tạo tranh chấp {} (loại {}, ca {})", requesterId, dispute.getDisputeId(),
                request.getType(), assignment.getAssignmentId());
        return toDetailDto(dispute);
    }

    // =========================================================================
    // UC-97/104: Xem chi tiết + lịch sử tranh chấp của một người dùng
    // =========================================================================

    public DisputeDetailResponseDto getDisputeDetail(UUID disputeId, UUID requesterId) {
        Dispute dispute = getDisputeOrThrow(disputeId);
        DisputeStatus status = dispute.getStatus();

        boolean isParty = dispute.getComplainantId().equals(requesterId) || dispute.getRespondentId().equals(requesterId);
        if (isParty) {
            return toDetailDto(dispute);
        }
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", requesterId));
        if (requester.getRole() != UserRole.CUSTOMER_CARE && requester.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("Bạn không có quyền xem tranh chấp này (MSG34)");
        }
        return toDetailDto(dispute);
    }

    public List<DisputeDetailResponseDto> getMyDisputes(UUID userId) {
        List<Dispute> own = new ArrayList<>();
        own.addAll(disputeRepository.findByComplainantIdOrderByCreatedAtDesc(userId));
        own.addAll(disputeRepository.findByRespondentIdOrderByCreatedAtDesc(userId));
        Set<UUID> seen = new HashSet<>();
        return own.stream()
                .filter(d -> seen.add(d.getDisputeId()))
                .sorted(Comparator.comparing(Dispute::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toDetailDto)
                .toList();
    }

    // =========================================================================
    // UC-98: Nạp bằng chứng
    // =========================================================================

    @Transactional
    public DisputeDetailResponseDto addEvidence(UUID disputeId, UUID partyId, AddDisputeEvidenceRequestDto request) {
        Dispute dispute = getDisputeOrThrow(disputeId);

        if (!isOpenForEvidence(dispute.getStatus())) {
            throw new BadRequestException(
                    "Tranh chấp ở trạng thái " + dispute.getStatus() + " không thể nạp thêm bằng chứng (MSG145)");
        }
        if (!dispute.getComplainantId().equals(partyId) && !dispute.getRespondentId().equals(partyId)) {
            throw new BadRequestException("Chỉ bên liên quan mới được nạp bằng chứng cho tranh chấp này");
        }

        DisputeEvidence evidence = DisputeEvidence.builder()
                .disputeId(dispute.getDisputeId())
                .party(partyId)
                .fileType(request.getFileType())
                .fileUrl(request.getFileUrl())
                .label(request.getLabel())
                .build();
        evidence = evidenceRepository.save(evidence);

        notifyDisputeParties(dispute, null, "Bằng chứng mới được bổ sung",
                "Bên " + partyId + " vừa nạp bằng chứng mới cho tranh chấp " + dispute.getDisputeId());
        recordAudit(partyId, "ADD_DISPUTE_EVIDENCE", dispute, null,
                Map.of("evidenceType", String.valueOf(request.getFileType())));

        return toDetailDto(dispute);
    }

    // =========================================================================
    // UC-101: Kháng nghị quyết định CC lên Admin (BR-54: cửa sổ 7 ngày)
    // =========================================================================

    @Transactional
    public DisputeDetailResponseDto escalateDispute(UUID disputeId, UUID partyId, EscalateDisputeRequestDto request) {
        Dispute dispute = getDisputeOrThrow(disputeId);

        if (!RESOLVED_STATUSES.contains(dispute.getStatus())) {
            throw new BadRequestException(
                    "Chỉ tranh chấp đã được CC quyết định (" + dispute.getStatus()
                            + ") mới có thể kháng nghị lên quản trị viên");
        }
        if (!isParty(dispute, partyId)) {
            throw new BadRequestException("Chỉ bên liên quan mới được kháng nghị quyết định tranh chấp này");
        }
        if (dispute.getResolvedAt() != null
                && dispute.getResolvedAt().plus(Duration.ofDays(ESCALATION_WINDOW_DAYS)).isBefore(Instant.now())) {
            throw new BadRequestException(
                    "Đã quá thời hạn kháng nghị 7 ngày (BR-54). Quyết định của Chăm sóc khách hàng đã trở nên cuối cùng (MSG141)");
        }

        DisputeStatus oldStatus = dispute.getStatus();
        dispute.setStatus(DisputeStatus.ESCALATED);
        dispute.setEscalationReason(request.getReason());
        dispute.setEscalatedBy(partyId);
        dispute.setEscalatedAt(Instant.now());
        dispute = disputeRepository.save(dispute);

        notifyDisputeParties(dispute, null, "Tranh chấp được kháng nghị lên quản trị viên",
                "Bên liên quan đã kháng nghị quyết định đối với tranh chấp " + dispute.getDisputeId()
                        + ". Quản trị viên sẽ ra quyết định cuối cùng trong thời gian sớm nhất.");
        recordAudit(partyId, "ESCALATE_DISPUTE", dispute,
                Map.of("status", oldStatus.name()), Map.of("status", dispute.getStatus().name()));

        log.info("Tranh chấp {} được kháng nghị bởi user {} sau quyết định CC", dispute.getDisputeId(), partyId);
        return toDetailDto(dispute);
    }

    // =========================================================================
    // UC-99: CC nhận đơn / chuyển đơn
    // =========================================================================

    @Transactional
    public DisputeDetailResponseDto assignDispute(UUID actorId, UUID disputeId, UUID targetCcUserId) {
        Dispute dispute = getDisputeOrThrow(disputeId);
        User actor = currentUserOrThrow(actorId);
        if (actor.getRole() != UserRole.CUSTOMER_CARE && actor.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("Chỉ Chăm sóc khách hàng hoặc Quản trị viên mới được phân công tranh chấp");
        }

        if (!OPEN_STATUSES.contains(dispute.getStatus())) {
            throw new BadRequestException(
                    "Tranh chấp đã ở trạng thái " + dispute.getStatus() + ", không thể phân công lại");
        }

        // targetCcUserId = null → CC tự nhận đơn; khác null → chuyển đơn cho CC khác
        UUID assigneeId = targetCcUserId != null ? targetCcUserId : actorId;
        if (targetCcUserId != null) {
            User target = currentUserOrThrow(assigneeId);
            if (target.getRole() != UserRole.CUSTOMER_CARE && target.getRole() != UserRole.ADMIN) {
                throw new BadRequestException("Người nhận phân công phải có vai trò Chăm sóc khách hàng hoặc Quản trị viên");
            }
        }

        DisputeStatus oldStatus = dispute.getStatus();
        dispute.setAssignedTo(assigneeId);
        if (oldStatus == DisputeStatus.OPEN) {
            dispute.setStatus(DisputeStatus.INVESTIGATING);
        }
        dispute = disputeRepository.save(dispute);

        // BR-53: thông báo thời hạn phản hồi cho bên được gán
        User assignee = userRepository.findById(assigneeId).orElse(null);
        if (assignee != null) {
            saveNotification(assignee, "Tranh chấp được phân công",
                    "Bạn được phân công xử lý tranh chấp " + dispute.getDisputeId()
                            + ". Hạn xử lý: " + fmt(dispute.getSlaDeadline()) + ".",
                    dispute);
        }
        recordAudit(actorId, "ASSIGN_DISPUTE", dispute,
                Map.of("status", oldStatus.name(), "assignedTo", String.valueOf(dispute.getAssignedTo())),
                Map.of("status", dispute.getStatus().name(), "assignedTo", String.valueOf(assigneeId)));

        log.info("CC {} phân công tranh chấp {} cho user {}", actorId, disputeId, assigneeId);
        return toDetailDto(dispute);
    }

    // =========================================================================
    // UC-99/109: Hàng đợi tranh chấp cho CC (BR-57: ưu tiên = SLA rủi ro, rồi đến nộp cũ nhất)
    // =========================================================================

    public List<DisputeQueueItemDto> getDisputeQueue() {
        Instant slaRiskCutoff = Instant.now().plus(Duration.ofHours(24));
        return disputeRepository.findByStatusIn(OPEN_STATUSES).stream()
                .sorted(Comparator
                        .<Dispute>comparingInt(d -> slaAtRisk(d, slaRiskCutoff) ? 0 : 1)
                        .thenComparing(Dispute::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(d -> toQueueItemDto(d, slaRiskCutoff))
                .toList();
    }

    // =========================================================================
    // UC-100/102: Ra quyết định (CC ở mức INVESTIGATING; Admin ở mức ESCALATED)
    // =========================================================================

    @Transactional
    public DisputeDetailResponseDto issueDecision(UUID actorId, UUID disputeId, IssueDecisionRequestDto request) {
        Dispute dispute = getDisputeOrThrow(disputeId);
        User actor = currentUserOrThrow(actorId);

        boolean isAdminFinal = actor.getRole() == UserRole.ADMIN && dispute.getStatus() == DisputeStatus.ESCALATED;
        boolean isCcDecision = (actor.getRole() == UserRole.CUSTOMER_CARE || actor.getRole() == UserRole.ADMIN)
                && dispute.getStatus() == DisputeStatus.INVESTIGATING;
        if (!isAdminFinal && !isCcDecision) {
            throw new BadRequestException(String.format(
                    "Tranh chấp đang ở trạng thái %s, không chấp nhận quyết định từ vai trò %s tại thời điểm này",
                    dispute.getStatus(), actor.getRole()));
        }

        DisputeStatus oldStatus = dispute.getStatus();
        DisputeStatus newStatus = isAdminFinal ? DisputeStatus.FINAL_RESOLVED
                : (request.getOutcome() == DisputeOutcome.UNVERIFIED ? DisputeStatus.UNVERIFIED
                        : DisputeStatus.RESOLVED);

        // BR-55: tự động trừ −10 RS nếu kết quả tìm thấy lỗi thuộc về Worker (trừ khi được miễn)
        boolean penaltyWaived = request.getPenaltyWaiverReason() != null
                && !request.getPenaltyWaiverReason().isBlank();
        boolean penaltyApplied = false;
        if (request.getOutcome() == DisputeOutcome.WORKER_FAULT && !penaltyWaived && !Boolean.TRUE.equals(dispute.getRsPenaltyApplied())) {
            penaltyApplied = applyWorkerRsPenalty(dispute, actorId, request.getRationale());
            dispute.setRsPenaltyApplied(true);
        }

        dispute.setStatus(newStatus);
        dispute.setOutcome(request.getOutcome());
        dispute.setDecisionRationale(request.getRationale());
        if (penaltyWaived) {
            dispute.setResolution("Miễn trừ penalty RS: " + request.getPenaltyWaiverReason());
        }
        if (isAdminFinal) {
            dispute.setFinalDecisionRationale(request.getRationale());
            dispute.setFinalDecidedBy(actorId);
            dispute.setFinalDecidedAt(Instant.now());
        } else {
            dispute.setResolvedBy(actorId);
            dispute.setResolvedAt(Instant.now());
        }
        dispute = disputeRepository.save(dispute);

        notifyDisputeParties(dispute, null, "Tranh chấp " + newStatus.name() + " được quyết định",
                "Quản trị/CSCĐ đã quyết định tranh chấp " + dispute.getDisputeId()
                        + " với kết quả: " + request.getOutcome().name()
                        + ". Lý do: " + request.getRationale());
        recordAudit(actorId, isAdminFinal ? "DISPUTE_FINAL_DECISION" : "ISSUE_DISPUTE_DECISION", dispute,
                Map.of("status", oldStatus.name()),
                Map.of("status", newStatus.name(), "outcome", request.getOutcome().name(),
                        "rsPenaltyApplied", String.valueOf(penaltyApplied)));

        log.info("User {} (role {}) ra quyết định cho tranh chấp {}: outcome={}, status={}, penalty={}",
                actorId, actor.getRole(), disputeId, request.getOutcome(), newStatus, penaltyApplied);
        return toDetailDto(dispute);
    }

    // =========================================================================
    // Internal
    // =========================================================================

    /**
     * BR-55/BR-61: khấu trừ −10 điểm RS của Worker khi bị kết tội, ghi ReliabilityLog,
     * chận trong [0,100]. Không thay đổi gì nếu worker không tồn tại (ví dụ tài khoản đã bị xóa).
     */
    private boolean applyWorkerRsPenalty(Dispute dispute, UUID decidedBy, String rationale) {
        UUID workerPartyId = resolveWorkerPartyId(dispute.getComplainantId(), dispute.getRespondentId());
        if (workerPartyId == null) {
            log.warn("Tranh chấp {}: outcome WORKER_FAULT nhưng không xác định được Worker liên quan",
                    dispute.getDisputeId());
            return false;
        }
        Worker worker = workerRepository.findByUser_UserId(workerPartyId).orElse(null);
        if (worker == null) {
            log.warn("Tranh chấp {}: không tìm thấy hồ sơ Worker (userId={}), bỏ qua penalty RS",
                    dispute.getDisputeId(), workerPartyId);
            return false;
        }

        int before = worker.getReliabilityScore() != null ? worker.getReliabilityScore() : RS_CEILING;
        int after = Math.max(RS_FLOOR, before - DISPUTE_RS_PENALTY);

        worker.setReliabilityScore(after);
        workerRepository.save(worker);

        ReliabilityLog logEntry = ReliabilityLog.builder()
                .worker(worker)
                .changeAmount(-DISPUTE_RS_PENALTY)
                .reason("Bị kết tội trong tranh chấp (DISPUTE): " + rationale)
                .referenceType("DISPUTE")
                .referenceId(dispute.getDisputeId())
                .scoreBefore(before)
                .scoreAfter(after)
                .build();
        reliabilityLogRepository.save(logEntry);

        log.info("Tranh chấp {}: khấu trừ {} RS từ {} (workerId={}, userId={})",
                dispute.getDisputeId(), DISPUTE_RS_PENALTY, before, worker.getWorkerId(), workerPartyId);
        return true;
    }

    /**
     * Xác định party nào là Worker (bị trừ RS khi WORKER_FAULT).
     */
    private UUID resolveWorkerPartyId(UUID complainantId, UUID respondentId) {
        if (workerRepository.findByUser_UserId(complainantId).isPresent()) {
            return complainantId;
        }
        if (workerRepository.findByUser_UserId(respondentId).isPresent()) {
            return respondentId;
        }
        return null;
    }

    private void markPaymentDisputed(UUID assignmentId) {
        paymentRepository.findByAssignmentId(assignmentId).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.DISPUTED) {
                PaymentStatus oldStatus = payment.getStatus();
                payment.setStatus(PaymentStatus.DISPUTED);
                paymentRepository.save(payment);
                log.info("Payment {} chuyển từ {} sang DISPUTED (assignment {})",
                        payment.getPaymentId(), oldStatus, assignmentId);
            }
        });
    }

    private void enforceDisputeWindow(Shift shift, DisputeType type) {
        if (shift == null || shift.getEndTime() == null) {
            return;
        }
        Instant windowEnd = shift.getEndTime().plus(Duration.ofDays(DISPUTE_WINDOW_DAYS));
        if (Instant.now().isAfter(windowEnd)) {
            throw new BadRequestException(
                    String.format("Đã quá thời hạn tranh chấp 14 ngày kể từ khi ca kết thúc (BR-52, ca kết lúc %s)",
                            fmt(shift.getEndTime())));
        }
    }

    /**
     * Xác định hai bên của một ca làm việc: complainant = user gọi API, respondent = bên còn lại.
     */
    private PartyRef resolveParties(UUID requesterId, ShiftAssignment assignment) {
        boolean requesterIsWorker = workerRepository.findByUser_UserId(requesterId).isPresent();
        boolean requesterIsEo = eventOrganizerRepository.findByUser_UserId(requesterId).isPresent();

        if (!requesterIsWorker && !requesterIsEo) {
            throw new BadRequestException("Người dùng không có quyền nộp tranh chấp cho ca làm việc này");
        }

        UUID workerUserId = assignment.getWorker() != null && assignment.getWorker().getUser() != null
                ? assignment.getWorker().getUser().getUserId() : null;
        UUID eoUserId = null;
        if (assignment.getShift() != null && assignment.getShift().getEvent() != null
                && assignment.getShift().getEvent().getEventOrganizer() != null
                && assignment.getShift().getEvent().getEventOrganizer().getUser() != null) {
            eoUserId = assignment.getShift().getEvent().getEventOrganizer().getUser().getUserId();
        }

        if (requesterIsWorker) {
            if (!Objects.equals(requesterId, workerUserId)) {
                throw new BadRequestException("Bạn không phải là Worker của ca làm việc này");
            }
            return new PartyRef(requesterId, eoUserId);
        } else {
            if (!Objects.equals(requesterId, eoUserId)) {
                throw new BadRequestException("Bạn không phải là Nhà tổ chức của ca làm việc này");
            }
            return new PartyRef(requesterId, workerUserId);
        }
    }

    private record PartyRef(UUID userId, UUID otherPartyId) {
    }

    private boolean isOpenForEvidence(DisputeStatus status) {
        return status == DisputeStatus.OPEN || status == DisputeStatus.INVESTIGATING;
    }

    private boolean isParty(Dispute dispute, UUID userId) {
        return dispute.getComplainantId().equals(userId) || dispute.getRespondentId().equals(userId);
    }

    private boolean slaAtRisk(Dispute dispute, Instant cutoff) {
        return dispute.getSlaDeadline() != null && dispute.getSlaDeadline().isBefore(cutoff);
    }

    private User currentUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));
    }

    private Dispute getDisputeOrThrow(UUID disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tranh chấp", "id", disputeId));
    }

    private void saveNotification(User user, String title, String body, Dispute dispute) {
        notificationRepository.save(Notification.builder()
                .user(user)
                .title(title)
                .body(body)
                .type(NotificationType.DISPUTE)
                .referenceType("DISPUTE")
                .referenceId(dispute.getDisputeId())
                .build());
    }

    private void notifyDisputeParties(Dispute dispute, PartyRef parties, String title, String body) {
        notifyParty(dispute.getComplainantId(), title, body, dispute);
        notifyParty(dispute.getRespondentId(), title, body, dispute);
    }

    private void notifyParty(UUID userId, String title, String body, Dispute dispute) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(u -> saveNotification(u, title, body, dispute));
    }

    private void notifyCcTeam(Dispute dispute) {
        userRepository.findByRole(UserRole.CUSTOMER_CARE).forEach(cc ->
                saveNotification(cc, "Có tranh chấp mới trong hàng đợi",
                        "Tranh chấp " + dispute.getDisputeId() + " (" + dispute.getType() + ") đang chờ phân công.",
                        dispute));
    }

    // ------------------------------------------------------------------
    // Audit
    // ------------------------------------------------------------------

    private void recordAudit(UUID actorUserId, String action, Dispute dispute,
                             Map<String, String> oldValues, Map<String, String> newValues) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(actorUserId)
                    .action(action)
                    .entityType("DISPUTE")
                    .entityId(dispute.getDisputeId())
                    .oldValues(oldValues != null ? objectMapper.writeValueAsString(oldValues) : null)
                    .newValues(newValues != null ? objectMapper.writeValueAsString(newValues) : null)
                    .build();
            auditLogService.logAction(entry);
        } catch (JsonProcessingException e) {
            log.error("Không thể chuẩn bị AuditLog cho tranh chấp {}: {}", dispute.getDisputeId(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private DisputeDetailResponseDto toDetailDto(Dispute dispute) {
        List<DisputeDetailResponseDto.EvidenceDto> evidences =
                evidenceRepository.findByDisputeIdOrderByCreatedAtAsc(dispute.getDisputeId()).stream()
                        .map(e -> DisputeDetailResponseDto.EvidenceDto.builder()
                                .id(e.getEvidenceId())
                                .party(e.getParty())
                                .fileType(e.getFileType())
                                .fileUrl(e.getFileUrl())
                                .label(e.getLabel())
                                .createdAt(e.getCreatedAt())
                                .build())
                        .toList();

        return DisputeDetailResponseDto.builder()
                .id(dispute.getDisputeId())
                .assignmentId(dispute.getAssignmentId())
                .complainantId(dispute.getComplainantId())
                .respondentId(dispute.getRespondentId())
                .type(dispute.getType())
                .title(dispute.getTitle())
                .description(dispute.getDescription())
                .status(dispute.getStatus())
                .outcome(dispute.getOutcome())
                .slaDeadline(dispute.getSlaDeadline())
                .resolution(dispute.getResolution())
                .decisionRationale(dispute.getDecisionRationale())
                .escalationReason(dispute.getEscalationReason())
                .escalatedAt(dispute.getEscalatedAt())
                .finalDecisionRationale(dispute.getFinalDecisionRationale())
                .finalDecidedAt(dispute.getFinalDecidedAt())
                .rsPenaltyApplied(dispute.getRsPenaltyApplied())
                .createdAt(dispute.getCreatedAt())
                .updatedAt(dispute.getUpdatedAt())
                .evidences(evidences)
                .build();
    }

    private DisputeQueueItemDto toQueueItemDto(Dispute dispute, Instant slaRiskCutoff) {
        return DisputeQueueItemDto.builder()
                .id(dispute.getDisputeId())
                .type(dispute.getType())
                .title(dispute.getTitle())
                .status(dispute.getStatus())
                .slaDeadline(dispute.getSlaDeadline())
                .createdAt(dispute.getCreatedAt())
                .complainantId(dispute.getComplainantId())
                .respondentId(dispute.getRespondentId())
                .assignedTo(dispute.getAssignedTo())
                .escalated(false)
                .slaAtRisk(slaAtRisk(dispute, slaRiskCutoff))
                .build();
    }

    private String fmt(Instant instant) {
        return instant != null ? instant.toString() : "—";
    }
}
