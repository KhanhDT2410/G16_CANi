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
    private final com.cani.repository.EoStrikeRepository eoStrikeRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final SupportConversationRepository supportConversationRepository;

    private static final List<DisputeStatus> OPEN_STATUSES =
            List.of(DisputeStatus.OPEN, DisputeStatus.INVESTIGATING);

    /**
     * Hàng đợi CC/Admin xử lý (UC-109).
     * Bao gồm ESCALATED: đơn đã kháng nghị phải nằm trong hàng đợi để Admin
     * ra quyết định cuối (UC-102, BR-68) — nếu thiếu, đơn sẽ biến mất khỏi UI.
     */
    private static final List<DisputeStatus> QUEUE_STATUSES =
            List.of(DisputeStatus.OPEN, DisputeStatus.INVESTIGATING, DisputeStatus.ESCALATED);
    private static final List<DisputeStatus> RESOLVED_STATUSES =
            List.of(DisputeStatus.RESOLVED, DisputeStatus.UNVERIFIED);
    private static final List<DisputeStatus> COMPLETED_STATUSES =
            List.of(DisputeStatus.RESOLVED, DisputeStatus.UNVERIFIED, DisputeStatus.FINAL_RESOLVED);

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
                    "Đã có hồ sơ tranh chấp đang được xử lý cho phân công ca làm việc này (BR-52: không cho phép trùng lặp hồ sơ đang mở)");
        }

        Dispute dispute = Dispute.builder()
                .assignmentId(assignment.getAssignmentId())
                .complainantId(requesterId)
                .respondentId(parties.otherPartyId)
                .type(request.getType())
                .title(request.getTitle())
                .description(request.getDescription())
                .evidence(normalizeEvidenceJson(request.getEvidence()))
                .status(DisputeStatus.OPEN)
                .rsPenaltyApplied(false)
                .slaDeadline(Instant.now().plus(Duration.ofDays(CC_DECISION_SLA_DAYS)))
                .build();
        dispute = disputeRepository.save(dispute);

        // Lưu ảnh/bằng chứng ban đầu vào danh sách bằng chứng có cấu trúc
        if (request.getEvidence() != null && !request.getEvidence().isBlank()) {
            DisputeEvidence initialEvidence = DisputeEvidence.builder()
                    .disputeId(dispute.getDisputeId())
                    .party(requesterId)
                    .fileType(DisputeEvidenceType.IMAGE)
                    .fileUrl(request.getEvidence().trim())
                    .label("Ảnh minh chứng kèm theo đơn")
                    .build();
            evidenceRepository.save(initialEvidence);
        }

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

    /**
     * Lấy danh sách ca phân công của Worker hoặc EO hiện tại để chọn khi tạo tranh chấp.
     */
    @Transactional(readOnly = true)
    public List<DisputeEligibleAssignmentDto> getEligibleAssignmentsForDispute(UUID requesterId) {
        User user = currentUserOrThrow(requesterId);
        List<ShiftAssignment> assignments = new ArrayList<>();

        if (user.getRole() == UserRole.EVENT_ORGANIZER) {
            eventOrganizerRepository.findByUser_UserId(requesterId)
                    .ifPresent(eo -> assignments.addAll(shiftAssignmentRepository.findAllWithDetailsByEoId(eo.getEoId())));
        } else if (user.getRole() == UserRole.WORKER) {
            workerRepository.findByUser_UserId(requesterId)
                    .ifPresent(w -> assignments.addAll(shiftAssignmentRepository.findAllWithDetailsByWorkerId(w.getWorkerId())));
        }

        return assignments.stream()
                .filter(sa -> sa.getStatus() != AssignmentStatus.PENDING)
                .limit(50)
                .map(sa -> {
                    Shift shift = sa.getShift();
                    Event event = shift != null ? shift.getEvent() : null;
                    String counterpartyName;
                    String counterpartyPhone = null;

                    if (user.getRole() == UserRole.EVENT_ORGANIZER) {
                        Worker w = sa.getWorker();
                        User wu = w != null ? w.getUser() : null;
                        counterpartyName = w != null && w.getFullName() != null
                                ? w.getFullName()
                                : (wu != null ? wu.getFullName() : "Nhân sự");
                        counterpartyPhone = wu != null ? wu.getPhone() : null;
                    } else {
                        EventOrganizer eo = event != null ? event.getEventOrganizer() : null;
                        User eu = eo != null ? eo.getUser() : null;
                        counterpartyName = eo != null && eo.getCompanyName() != null
                                ? eo.getCompanyName()
                                : (eu != null ? eu.getFullName() : "Ban Tổ Chức");
                        counterpartyPhone = eu != null ? eu.getPhone() : null;
                    }

                    return DisputeEligibleAssignmentDto.builder()
                            .assignmentId(sa.getAssignmentId())
                            .eventName(event != null ? event.getName() : "Sự kiện")
                            .jobType(shift != null ? shift.getJobType() : "Ca làm")
                            .counterpartyName(counterpartyName)
                            .counterpartyPhone(counterpartyPhone)
                            .startTime(shift != null ? shift.getStartTime() : sa.getCreatedAt())
                            .endTime(shift != null ? shift.getEndTime() : null)
                            .status(sa.getStatus() != null ? sa.getStatus().name() : null)
                            .build();
                })
                .toList();
    }

    // =========================================================================
    // UC-97/104: Xem chi tiết + lịch sử tranh chấp của một người dùng
    // =========================================================================

    @Transactional(readOnly = true)
    public DisputeDetailResponseDto getDisputeDetail(UUID disputeId, UUID requesterId) {
        Dispute dispute = getDisputeOrThrow(disputeId);

        boolean isParty = Objects.equals(dispute.getComplainantId(), requesterId)
                || Objects.equals(dispute.getRespondentId(), requesterId);
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

    @Transactional(readOnly = true)
    public List<DisputeDetailResponseDto> getMyDisputes(UUID userId) {
        List<Dispute> rawOwn = disputeRepository.findByComplainantIdOrRespondentIdOrderByCreatedAtDesc(userId, userId);
        if (rawOwn == null || rawOwn.isEmpty()) {
            return List.of();
        }
        Set<UUID> seen = new HashSet<>();
        List<Dispute> own = rawOwn.stream()
                .filter(d -> d != null && d.getDisputeId() != null && seen.add(d.getDisputeId()))
                .sorted(Comparator.comparing(Dispute::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        return toDetailDtosBatch(own, userId);
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
        return toQueueItems(disputeRepository.findByStatusIn(QUEUE_STATUSES));
    }

    /**
     * Danh sách tranh chấp đã giải quyết / hoàn thiện (RESOLVED, FINAL_RESOLVED, UNVERIFIED).
     */
    public List<DisputeQueueItemDto> getResolvedDisputes() {
        return toQueueItems(disputeRepository.findByStatusIn(COMPLETED_STATUSES));
    }

    /**
     * UC-109 (phân tầng CC): hàng đợi tranh chấp được gán cho đúng CC này.
     * Khi đội CC có nhiều người, mỗi CC chỉ thấy đơn "của tôi đang xử lý".
     */
    public List<DisputeQueueItemDto> getDisputeQueueForCc(UUID ccUserId) {
        return toQueueItems(disputeRepository.findByStatusInAndAssignedTo(QUEUE_STATUSES, ccUserId));
    }

    /**
     * Đơn mở chưa ai nhận (assignedTo = null) — dùng để CC chọn "đơn chưa gán" khi tự nhận.
     */
    public List<DisputeQueueItemDto> getUnassignedQueue() {
        return toQueueItems(disputeRepository.findByStatusInAndAssignedToIsNull(QUEUE_STATUSES));
    }

    private List<DisputeQueueItemDto> toQueueItems(List<Dispute> disputes) {
        Instant slaRiskCutoff = Instant.now().plus(Duration.ofHours(24));
        List<DisputeQueueItemDto> items = disputes.stream()
                .map(d -> toQueueItemDto(d, slaRiskCutoff))
                .toList();

        return items.stream()
                .sorted((a, b) -> {
                    // 1. Đơn có tin nhắn mới chưa đọc luôn nhảy lên trên cùng
                    boolean aUnread = a.getUnreadCount() != null && a.getUnreadCount() > 0;
                    boolean bUnread = b.getUnreadCount() != null && b.getUnreadCount() > 0;
                    if (aUnread != bUnread) {
                        return aUnread ? -1 : 1;
                    }

                    // 2. Ca có tin nhắn mới nhất (lastMessageAt DESC) luôn lên trước
                    Instant aMsg = a.getLastMessageAt();
                    Instant bMsg = b.getLastMessageAt();
                    if (aMsg != null || bMsg != null) {
                        if (aMsg != null && bMsg != null) {
                            int cmp = bMsg.compareTo(aMsg);
                            if (cmp != 0) return cmp;
                        } else {
                            return aMsg != null ? -1 : 1;
                        }
                    }

                    // 3. Nếu chưa có tin nhắn nào, ca có rủi ro SLA ưu tiên trước
                    if (a.isSlaAtRisk() != b.isSlaAtRisk()) {
                        return a.isSlaAtRisk() ? -1 : 1;
                    }

                    // 4. Mới tạo lên trước (createdAt DESC)
                    Instant aCreated = a.getCreatedAt();
                    Instant bCreated = b.getCreatedAt();
                    if (aCreated != null && bCreated != null) {
                        return bCreated.compareTo(aCreated);
                    }
                    return 0;
                })
                .toList();
    }

    /**
     * UC-99: Danh sách nhân sự Customer Care để CC chuyển đơn cho nhau.
     * Kèm số đơn đang giao để CC cân bằng tải.
     */
    public List<CustomerCareStaffDto> getCustomerCareStaff() {
        return userRepository.findByRole(UserRole.CUSTOMER_CARE).stream()
                .map(cc -> CustomerCareStaffDto.builder()
                        .userId(cc.getUserId())
                        .fullName(cc.getFullName())
                        .email(cc.getEmail())
                        .assignedCount(disputeRepository.findByStatusInAndAssignedTo(QUEUE_STATUSES, cc.getUserId()).size())
                        .build())
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
        if (request.getOutcome() == DisputeOutcome.EO_FAULT) {
            recordEoStrikeIfAbsent(dispute, request.getRationale());
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

        // SRS FE-09 ↔ FE-11 (UC-87 / UC-100 / UC-102): Đồng bộ kết quả phân xử về bản ghi Payment
        syncPaymentAfterDisputeDecision(dispute.getAssignmentId(), request.getOutcome());

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

    private UUID resolveEoPartyId(UUID complainantId, UUID respondentId) {
        if (complainantId != null && eventOrganizerRepository.findByUser_UserId(complainantId).isPresent()) {
            return complainantId;
        }
        if (respondentId != null && eventOrganizerRepository.findByUser_UserId(respondentId).isPresent()) {
            return respondentId;
        }
        return null;
    }

    private void recordEoStrikeIfAbsent(Dispute dispute, String rationale) {
        if (eoStrikeRepository.existsByDispute_DisputeId(dispute.getDisputeId())) {
            log.info("Dispute {} đã có Strike ghi nhận cho EO, bỏ qua dedup", dispute.getDisputeId());
            return;
        }
        UUID eoUserId = resolveEoPartyId(dispute.getComplainantId(), dispute.getRespondentId());
        if (eoUserId == null) {
            log.warn("Dispute {}: outcome EO_FAULT nhưng không xác định được EO liên quan", dispute.getDisputeId());
            return;
        }
        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(eoUserId).orElse(null);
        if (eo == null) {
            log.warn("Dispute {}: không tìm thấy hồ sơ EO cho userId {}", dispute.getDisputeId(), eoUserId);
            return;
        }
        EoStrike strike = EoStrike.builder()
                .eo(eo)
                .dispute(dispute)
                .reason("Bị kết luận vi phạm trong tranh chấp: " + (rationale != null ? rationale : "EO_FAULT"))
                .build();
        eoStrikeRepository.save(strike);
        log.info("Ghi nhận Strike cho EO {} từ tranh chấp {}", eo.getEoId(), dispute.getDisputeId());
    }

    public EoStrikeSummaryDto getEoStrikesForDispute(UUID disputeId) {
        Dispute dispute = getDisputeOrThrow(disputeId);
        UUID eoUserId = resolveEoPartyId(dispute.getComplainantId(), dispute.getRespondentId());
        if (eoUserId == null) {
            return EoStrikeSummaryDto.builder()
                    .strikeCount90d(0L)
                    .strikes(List.of())
                    .build();
        }
        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(eoUserId).orElse(null);
        if (eo == null) {
            return EoStrikeSummaryDto.builder()
                    .eoUserId(eoUserId)
                    .strikeCount90d(0L)
                    .strikes(List.of())
                    .build();
        }
        Instant since90d = Instant.now().minus(java.time.Duration.ofDays(90));
        long count = eoStrikeRepository.countByEo_EoIdAndCreatedAtAfter(eo.getEoId(), since90d);
        List<EoStrike> list = eoStrikeRepository.findByEo_EoIdAndCreatedAtAfterOrderByCreatedAtDesc(eo.getEoId(), since90d);

        List<EoStrikeSummaryDto.StrikeItemDto> items = list.stream()
                .map(s -> EoStrikeSummaryDto.StrikeItemDto.builder()
                        .strikeId(s.getStrikeId())
                        .disputeId(s.getDispute().getDisputeId())
                        .reason(s.getReason())
                        .createdAt(s.getCreatedAt())
                        .build())
                .toList();

        return EoStrikeSummaryDto.builder()
                .eoId(eo.getEoId())
                .eoUserId(eoUserId)
                .strikeCount90d(count)
                .strikes(items)
                .build();
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

    /**
     * SRS FE-09 ↔ FE-11 (UC-87 / UC-100 / UC-102): Cập nhật trạng thái thanh toán sau phán quyết của CSKH/Admin
     */
    private void syncPaymentAfterDisputeDecision(UUID assignmentId, DisputeOutcome outcome) {
        if (assignmentId == null || paymentRepository == null) return;
        paymentRepository.findByAssignmentId(assignmentId).ifPresent(payment -> {
            PaymentStatus oldStatus = payment.getStatus();
            if (outcome == DisputeOutcome.WORKER_FAULT) {
                payment.setStatus(PaymentStatus.CONFIRMED_FULL);
            } else if (outcome == DisputeOutcome.PARTIAL) {
                payment.setStatus(PaymentStatus.CONFIRMED_PARTIAL);
            } else if (outcome == DisputeOutcome.EO_FAULT) {
                payment.setStatus(PaymentStatus.DISPUTED);
            }
            paymentRepository.save(payment);
            log.info("Payment {} đồng bộ phán quyết Dispute: {} -> {} (assignment {})",
                    payment.getPaymentId(), oldStatus, payment.getStatus(), assignmentId);
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

    private String normalizeEvidenceJson(String rawEvidence) {
        if (rawEvidence == null || rawEvidence.isBlank()) {
            return null;
        }
        String trimmed = rawEvidence.trim();
        if ((trimmed.startsWith("[") && trimmed.endsWith("]")) || (trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            return trimmed;
        }
        try {
            return objectMapper.writeValueAsString(List.of(trimmed));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void saveNotification(User user, String title, String body, Dispute dispute) {
        try {
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .title(title)
                    .body(body)
                    .type(NotificationType.DISPUTE)
                    .referenceType("DISPUTE")
                    .referenceId(dispute.getDisputeId())
                    .build());
        } catch (Exception ex) {
            log.warn("Không thể lưu thông báo tranh chấp {}: {}",
                    dispute != null ? dispute.getDisputeId() : null, ex.getMessage());
        }
    }

    private void notifyDisputeParties(Dispute dispute, PartyRef parties, String title, String body) {
        notifyParty(dispute.getComplainantId(), title, body, dispute);
        notifyParty(dispute.getRespondentId(), title, body, dispute);
    }

    private void notifyParty(UUID userId, String title, String body, Dispute dispute) {
        if (userId == null) {
            return;
        }
        try {
            userRepository.findById(userId).ifPresent(u -> saveNotification(u, title, body, dispute));
        } catch (Exception ex) {
            log.warn("Không thể thông báo tới user {}: {}", userId, ex.getMessage());
        }
    }

    private void notifyCcTeam(Dispute dispute) {
        try {
            userRepository.findByRole(UserRole.CUSTOMER_CARE).forEach(cc ->
                    saveNotification(cc, "Có tranh chấp mới trong hàng đợi",
                            "Tranh chấp " + dispute.getDisputeId() + " (" + dispute.getType() + ") đang chờ phân công.",
                            dispute));
        } catch (Exception ex) {
            log.warn("Không thể thông báo đội CSKH về tranh chấp {}: {}",
                    dispute != null ? dispute.getDisputeId() : null, ex.getMessage());
        }
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

    private List<DisputeDetailResponseDto> toDetailDtosBatch(List<Dispute> disputes, UUID currentUserIdForUnread) {
        if (disputes == null || disputes.isEmpty()) {
            return List.of();
        }

        List<UUID> disputeIds = disputes.stream()
                .map(Dispute::getDisputeId)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, List<DisputeEvidence>> evidencesByDisputeId = disputeIds.isEmpty()
                ? Map.of()
                : evidenceRepository.findByDisputeIdInOrderByCreatedAtAsc(disputeIds).stream()
                        .filter(e -> e.getDisputeId() != null)
                        .collect(Collectors.groupingBy(DisputeEvidence::getDisputeId));

        Set<UUID> assignmentIds = disputes.stream()
                .map(Dispute::getAssignmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, ShiftAssignment> assignmentsById = assignmentIds.isEmpty()
                ? Map.of()
                : shiftAssignmentRepository.findByAssignmentIdIn(assignmentIds).stream()
                        .filter(sa -> sa.getAssignmentId() != null)
                        .collect(Collectors.toMap(ShiftAssignment::getAssignmentId, sa -> sa, (a, b) -> a));

        Set<UUID> userIds = new HashSet<>();
        Set<UUID> partyUserIds = new HashSet<>();
        for (Dispute d : disputes) {
            if (d.getComplainantId() != null) {
                userIds.add(d.getComplainantId());
                partyUserIds.add(d.getComplainantId());
            }
            if (d.getRespondentId() != null) {
                userIds.add(d.getRespondentId());
                partyUserIds.add(d.getRespondentId());
            }
            if (d.getAssignedTo() != null) {
                userIds.add(d.getAssignedTo());
            }
        }
        Map<UUID, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .filter(u -> u.getUserId() != null)
                        .collect(Collectors.toMap(User::getUserId, u -> u, (a, b) -> a));

        Map<UUID, List<SupportConversation>> convsByDisputeId = disputeIds.isEmpty()
                ? Map.of()
                : supportConversationRepository.findByDisputeIdIn(disputeIds).stream()
                        .filter(c -> c.getDisputeId() != null)
                        .collect(Collectors.groupingBy(SupportConversation::getDisputeId));

        Map<UUID, EventOrganizer> eoByUserId = partyUserIds.isEmpty()
                ? Map.of()
                : eventOrganizerRepository.findByUser_UserIdIn(partyUserIds).stream()
                        .filter(eo -> eo.getUser() != null && eo.getUser().getUserId() != null)
                        .collect(Collectors.toMap(eo -> eo.getUser().getUserId(), eo -> eo, (a, b) -> a));

        Map<UUID, Long> strikeCountByEoId = new HashMap<>();
        Instant since90d = Instant.now().minus(Duration.ofDays(90));
        for (EventOrganizer eo : eoByUserId.values()) {
            if (eo.getEoId() != null && !strikeCountByEoId.containsKey(eo.getEoId())) {
                strikeCountByEoId.put(eo.getEoId(), eoStrikeRepository.countByEo_EoIdAndCreatedAtAfter(eo.getEoId(), since90d));
            }
        }

        return disputes.stream().map(dispute -> {
            UUID dId = dispute.getDisputeId();
            EventOrganizer eo = eoByUserId.get(dispute.getComplainantId());
            if (eo == null) {
                eo = eoByUserId.get(dispute.getRespondentId());
            }
            Long eoStrikeCount90d = (eo != null && eo.getEoId() != null)
                    ? strikeCountByEoId.get(eo.getEoId())
                    : null;

            return buildDetailResponseDto(
                    dispute,
                    evidencesByDisputeId.getOrDefault(dId, List.of()),
                    dispute.getAssignmentId() != null ? assignmentsById.get(dispute.getAssignmentId()) : null,
                    usersById,
                    convsByDisputeId.getOrDefault(dId, List.of()),
                    eoStrikeCount90d,
                    currentUserIdForUnread
            );
        }).toList();
    }

    private DisputeDetailResponseDto toDetailDto(Dispute dispute) {
        List<DisputeEvidence> rawEvidences = evidenceRepository.findByDisputeIdOrderByCreatedAtAsc(dispute.getDisputeId());
        ShiftAssignment assignment = dispute.getAssignmentId() != null
                ? shiftAssignmentRepository.findById(dispute.getAssignmentId()).orElse(null)
                : null;

        Map<UUID, User> usersById = new HashMap<>();
        if (dispute.getComplainantId() != null) {
            userRepository.findById(dispute.getComplainantId()).ifPresent(u -> usersById.put(u.getUserId(), u));
        }
        if (dispute.getRespondentId() != null) {
            userRepository.findById(dispute.getRespondentId()).ifPresent(u -> usersById.put(u.getUserId(), u));
        }
        if (dispute.getAssignedTo() != null) {
            userRepository.findById(dispute.getAssignedTo()).ifPresent(u -> usersById.put(u.getUserId(), u));
        }

        List<SupportConversation> convs = supportConversationRepository.findByDisputeId(dispute.getDisputeId());

        Long eoStrikeCount90d = null;
        UUID eoPartyUserId = resolveEoPartyId(dispute.getComplainantId(), dispute.getRespondentId());
        if (eoPartyUserId != null) {
            EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(eoPartyUserId).orElse(null);
            if (eo != null) {
                eoStrikeCount90d = eoStrikeRepository.countByEo_EoIdAndCreatedAtAfter(
                        eo.getEoId(), Instant.now().minus(Duration.ofDays(90)));
            }
        }

        return buildDetailResponseDto(dispute, rawEvidences, assignment, usersById, convs, eoStrikeCount90d, null);
    }

    private DisputeDetailResponseDto buildDetailResponseDto(
            Dispute dispute,
            List<DisputeEvidence> rawEvidences,
            ShiftAssignment assignment,
            Map<UUID, User> usersById,
            List<SupportConversation> convs,
            Long eoStrikeCount90d,
            UUID currentUserIdForUnread
    ) {
        List<DisputeDetailResponseDto.EvidenceDto> evidences = new ArrayList<>();
        if (rawEvidences != null) {
            for (DisputeEvidence e : rawEvidences) {
                evidences.add(DisputeDetailResponseDto.EvidenceDto.builder()
                        .id(e.getEvidenceId())
                        .party(e.getParty())
                        .fileType(e.getFileType())
                        .fileUrl(e.getFileUrl())
                        .label(e.getLabel())
                        .createdAt(e.getCreatedAt())
                        .build());
            }
        }

        // Đảm bảo nếu bằng chứng được lưu ở cột evidence của đơn thì Admin vẫn xem được đầy đủ
        if (evidences.isEmpty() && dispute.getEvidence() != null && !dispute.getEvidence().isBlank()) {
            evidences.add(DisputeDetailResponseDto.EvidenceDto.builder()
                    .party(dispute.getComplainantId())
                    .fileType(DisputeEvidenceType.IMAGE)
                    .fileUrl(dispute.getEvidence().trim())
                    .label("Ảnh minh chứng kèm theo đơn")
                    .createdAt(dispute.getCreatedAt())
                    .build());
        }

        String eventName = null;
        String jobType = null;
        Instant shiftStartTime = null;
        Instant shiftEndTime = null;
        String shiftLocation = null;

        if (assignment != null && assignment.getShift() != null) {
            Shift shift = assignment.getShift();
            jobType = shift.getJobType();
            shiftStartTime = shift.getStartTime();
            shiftEndTime = shift.getEndTime();
            shiftLocation = shift.getLocationAddress();
            if (shift.getEvent() != null) {
                eventName = shift.getEvent().getName();
            }
        }

        User complainant = dispute.getComplainantId() != null ? usersById.get(dispute.getComplainantId()) : null;
        String complainantName = complainant != null ? complainant.getFullName() : null;
        String complainantRole = complainant != null && complainant.getRole() != null ? complainant.getRole().name() : null;
        String complainantPhone = complainant != null ? complainant.getPhone() : null;

        User respondent = dispute.getRespondentId() != null ? usersById.get(dispute.getRespondentId()) : null;
        String respondentName = respondent != null ? respondent.getFullName() : null;
        String respondentRole = respondent != null && respondent.getRole() != null ? respondent.getRole().name() : null;
        String respondentPhone = respondent != null ? respondent.getPhone() : null;

        User assignedUser = dispute.getAssignedTo() != null ? usersById.get(dispute.getAssignedTo()) : null;
        String assignedToName = assignedUser != null ? assignedUser.getFullName() : null;

        int unreadStaff = 0;
        int workerUnread = 0;
        int eoUnread = 0;
        String lastSender = null;
        Instant lastMsgAt = null;
        Integer userUnreadOverride = null;

        if (convs != null) {
            for (SupportConversation c : convs) {
                if (c.getUnreadStaffCount() != null) {
                    unreadStaff += c.getUnreadStaffCount();
                    if ("WORKER".equalsIgnoreCase(c.getDisputeParty())) {
                        workerUnread += c.getUnreadStaffCount();
                    } else if ("EO".equalsIgnoreCase(c.getDisputeParty())) {
                        eoUnread += c.getUnreadStaffCount();
                    }
                }
                if (currentUserIdForUnread != null
                        && Objects.equals(c.getUserId(), currentUserIdForUnread)
                        && ("EO".equalsIgnoreCase(c.getDisputeParty()) || "WORKER".equalsIgnoreCase(c.getDisputeParty()))) {
                    userUnreadOverride = c.getUnreadUserCount() != null ? c.getUnreadUserCount() : 0;
                }
                if (c.getLastMessageAt() != null) {
                    if (lastMsgAt == null || c.getLastMessageAt().isAfter(lastMsgAt)) {
                        lastMsgAt = c.getLastMessageAt();
                        lastSender = c.getDisputeParty();
                    }
                }
            }
        }

        int finalUnreadCount = userUnreadOverride != null ? userUnreadOverride : unreadStaff;

        return DisputeDetailResponseDto.builder()
                .id(dispute.getDisputeId())
                .assignmentId(dispute.getAssignmentId())
                .complainantId(dispute.getComplainantId())
                .respondentId(dispute.getRespondentId())
                .assignedTo(dispute.getAssignedTo())
                .assignedToName(assignedToName)
                .complainantName(complainantName)
                .complainantRole(complainantRole)
                .complainantPhone(complainantPhone)
                .respondentName(respondentName)
                .respondentRole(respondentRole)
                .respondentPhone(respondentPhone)
                .eventName(eventName)
                .jobType(jobType)
                .shiftStartTime(shiftStartTime)
                .shiftEndTime(shiftEndTime)
                .shiftLocation(shiftLocation)
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
                .eoStrikeCount90d(eoStrikeCount90d)
                .createdAt(dispute.getCreatedAt())
                .updatedAt(dispute.getUpdatedAt())
                .unreadCount(finalUnreadCount)
                .workerUnreadCount(workerUnread)
                .eoUnreadCount(eoUnread)
                .lastMessageSenderParty(lastSender)
                .lastMessageAt(lastMsgAt)
                .evidences(evidences)
                .build();
    }

    private DisputeQueueItemDto toQueueItemDto(Dispute dispute, Instant slaRiskCutoff) {
        List<SupportConversation> convs = supportConversationRepository.findByDisputeId(dispute.getDisputeId());
        int unreadStaff = 0;
        int workerUnread = 0;
        int eoUnread = 0;
        String lastSender = null;
        Instant lastMsgAt = null;

        for (SupportConversation c : convs) {
            if (c.getUnreadStaffCount() != null) {
                unreadStaff += c.getUnreadStaffCount();
                if ("WORKER".equalsIgnoreCase(c.getDisputeParty())) {
                    workerUnread += c.getUnreadStaffCount();
                } else if ("EO".equalsIgnoreCase(c.getDisputeParty())) {
                    eoUnread += c.getUnreadStaffCount();
                }
            }
            if (c.getLastMessageAt() != null) {
                if (lastMsgAt == null || c.getLastMessageAt().isAfter(lastMsgAt)) {
                    lastMsgAt = c.getLastMessageAt();
                    lastSender = c.getDisputeParty();
                }
            }
        }

        String workerName = null;
        String eoName = null;

        if (dispute.getAssignmentId() != null) {
            ShiftAssignment sa = shiftAssignmentRepository.findById(dispute.getAssignmentId()).orElse(null);
            if (sa != null) {
                if (sa.getWorker() != null) {
                    workerName = sa.getWorker().getFullName() != null ? sa.getWorker().getFullName()
                            : (sa.getWorker().getUser() != null ? sa.getWorker().getUser().getFullName() : null);
                }
                if (sa.getShift() != null && sa.getShift().getEvent() != null
                        && sa.getShift().getEvent().getEventOrganizer() != null) {
                    EventOrganizer eo = sa.getShift().getEvent().getEventOrganizer();
                    eoName = eo.getCompanyName() != null ? eo.getCompanyName()
                            : (eo.getUser() != null ? eo.getUser().getFullName() : null);
                }
            }
        }

        if (workerName == null || eoName == null) {
            User comp = dispute.getComplainantId() != null ? userRepository.findById(dispute.getComplainantId()).orElse(null) : null;
            User resp = dispute.getRespondentId() != null ? userRepository.findById(dispute.getRespondentId()).orElse(null) : null;
            if (comp != null) {
                if (comp.getRole() == UserRole.WORKER) workerName = comp.getFullName();
                else if (comp.getRole() == UserRole.EVENT_ORGANIZER) eoName = comp.getFullName();
            }
            if (resp != null) {
                if (resp.getRole() == UserRole.WORKER) workerName = resp.getFullName();
                else if (resp.getRole() == UserRole.EVENT_ORGANIZER) eoName = resp.getFullName();
            }
        }

        String lastSenderName = null;
        if ("WORKER".equalsIgnoreCase(lastSender)) {
            lastSenderName = workerName;
        } else if ("EO".equalsIgnoreCase(lastSender)) {
            lastSenderName = eoName;
        }

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
                .escalated(dispute.getStatus() == DisputeStatus.ESCALATED)
                .slaAtRisk(slaAtRisk(dispute, slaRiskCutoff))
                .unreadCount(unreadStaff)
                .workerUnreadCount(workerUnread)
                .eoUnreadCount(eoUnread)
                .workerName(workerName)
                .eoName(eoName)
                .lastMessageSenderParty(lastSender)
                .lastMessageSenderName(lastSenderName)
                .lastMessageAt(lastMsgAt)
                .build();
    }

    private String fmt(Instant instant) {
        return instant != null ? instant.toString() : "—";
    }
}
