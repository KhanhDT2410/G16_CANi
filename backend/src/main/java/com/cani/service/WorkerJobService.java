package com.cani.service;

import com.cani.dto.worker.*;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentSource;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.AttendanceStatus;
import com.cani.entity.enums.DisputeStatus;
import com.cani.entity.enums.PaymentStatus;
import com.cani.entity.enums.ProposalStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import com.cani.util.VietQrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerJobService {

    private static final double DEFAULT_CENTER_LAT = 10.7769;
    private static final double DEFAULT_CENTER_LNG = 106.7009;
    private static final double DEFAULT_SEARCH_RADIUS_KM = 15.0;
    private static final double DEFAULT_MAX_DISTANCE_KM = 20.0;
    private static final int MAX_RELIABILITY_SCORE = 100;
    private static final int MIN_RELIABILITY_SCORE = 0;
    private static final String PROOF_UPLOAD_DIR = "uploads/proofs";
    private static final java.util.List<String> ALLOWED_IMAGE_EXTS = java.util.List.of("jpg", "jpeg", "png", "webp", "gif");

    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final DispatchProposalRepository dispatchProposalRepository;
    private final EventRepository eventRepository;
    private final MatchingDispatchService matchingDispatchService;
    private final DirectPaymentService directPaymentService;
    private final AttendanceLogRepository attendanceLogRepository;
    private final WorkerAvailabilityRepository workerAvailabilityRepository;
    private final ReviewRepository reviewRepository;
    private final PaymentRepository paymentRepository;
    private final ProposalStatusUpdater proposalStatusUpdater;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private WorkerGroupMembershipRepository workerGroupMembershipRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private SkillProgressionService skillProgressionService;

    @Autowired(required = false)
    private jakarta.persistence.EntityManager entityManager;

    @Autowired(required = false)
    private EventOrganizerRepository eventOrganizerRepository;

    @Autowired(required = false)
    private WorkerSkillRepository workerSkillRepository;

    @Autowired(required = false)
    private BlockListService blockListService;

    @Autowired(required = false)
    private ShiftSkillRequirementRepository shiftSkillRequirementRepository;

    @Autowired(required = false)
    private DisputeRepository disputeRepository;

    @Autowired(required = false)
    private EkycConsentRecordRepository consentRecordRepository;

    @Autowired(required = false)
    private WorkerShiftEligibilityService workerShiftEligibilityService;

    @Autowired(required = false)
    private ShiftStaffingPolicy shiftStaffingPolicy;

    @Autowired(required = false)
    private WorkerNearbyShiftQueryService workerNearbyShiftQueryService;

    private ShiftStaffingPolicy getStaffingPolicy() {
        if (shiftStaffingPolicy != null) {
            return shiftStaffingPolicy;
        }
        return ShiftStaffingPolicy.withFallbackDependencies(
                shiftAssignmentRepository,
                dispatchProposalRepository
        );
    }

    private WorkerShiftEligibilityService getEligibilityService() {
        if (workerShiftEligibilityService != null) {
            return workerShiftEligibilityService;
        }
        return WorkerShiftEligibilityService.withFallbackDependencies(
                workerSkillRepository,
                shiftSkillRequirementRepository,
                shiftAssignmentRepository,
                workerAvailabilityRepository,
                blockListService,
                skillRepository
        );
    }

    private WorkerNearbyShiftQueryService getNearbyShiftQueryService() {
        if (workerNearbyShiftQueryService != null) {
            return workerNearbyShiftQueryService;
        }
        return WorkerNearbyShiftQueryService.withFallbackDependencies(
                workerRepository,
                shiftRepository,
                shiftAssignmentRepository,
                shiftSkillRequirementRepository,
                skillRepository,
                getEligibilityService(),
                workerSkillRepository,
                workerAvailabilityRepository,
                blockListService
        );
    }

    /**
     * Bật / tắt trạng thái "Bắt đầu kiếm việc" (Online status)
     */
    @Transactional
    public Worker setOnlineStatus(UUID userId, WorkerOnlineStatusRequestDto dto) {
        Worker worker = getWorkerOrThrow(userId);

        if (Boolean.TRUE.equals(dto.getIsOnline()) && worker.getKycStatus() != com.cani.entity.enums.KycStatus.APPROVED) {
            throw new BadRequestException("Tài khoản chưa hoàn tất định danh eKYC. Vui lòng xác thực CCCD để kích hoạt chế độ tìm việc.");
        }

        worker.setIsOnline(dto.getIsOnline());
        boolean hasSavedPreferencesLocation = worker.getBaseLat() != null
                && worker.getBaseLng() != null
                && Double.isFinite(worker.getBaseLat())
                && Double.isFinite(worker.getBaseLng());
        // Chỉ khởi tạo tọa độ cơ sở từ GPS khi Worker chưa thiết lập khu vực ưu tiên trong Preferences
        if (!hasSavedPreferencesLocation && dto.getCurrentLat() != null && dto.getCurrentLng() != null) {
            worker.setBaseLat(dto.getCurrentLat());
            worker.setBaseLng(dto.getCurrentLng());
        }
        worker = workerRepository.save(worker);
        log.info("Worker {} đã cập nhật trạng thái online: {}", userId, worker.getIsOnline());
        if (Boolean.TRUE.equals(worker.getIsOnline())) {
            try {
                matchingDispatchService.dispatchActiveShiftsForWorker(worker);
            } catch (Exception ex) {
                log.warn("Reverse-matching khi worker {} online gặp lỗi không nghiêm trọng: {}", userId, ex.getMessage());
            }
        }
        return worker;
    }

    /**
     * Lấy danh sách các đề xuất việc làm đang chờ Worker phản hồi (Status = PENDING)
     */
    @Transactional(readOnly = true)
    public List<JobProposalResponseDto> getMyProposals(UUID userId) {
        Worker worker = getWorkerOrThrow(userId);

        if (!Boolean.TRUE.equals(worker.getIsOnline())) {
            log.debug("Worker {} đang tạm tắt Nhận việc (isOnline=false), ẩn toàn bộ đề xuất chờ phản hồi.", userId);
            return Collections.emptyList();
        }

        List<DispatchProposal> proposals = dispatchProposalRepository.findByWorker_WorkerIdAndStatusOrderByCreatedAtDesc(
                worker.getWorkerId(), ProposalStatus.PENDING);

        List<WorkerAvailability> availabilitySlots = workerAvailabilityRepository != null
                ? workerAvailabilityRepository.findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(worker.getWorkerId())
                : Collections.emptyList();
        List<ShiftAssignment> activeAssignments = shiftAssignmentRepository != null
                ? shiftAssignmentRepository.findByWorker_WorkerIdAndStatusIn(
                        worker.getWorkerId(),
                        List.of(AssignmentStatus.CONFIRMED, AssignmentStatus.CHECKED_IN, AssignmentStatus.PENDING))
                : Collections.emptyList();
        WorkerShiftEligibilityService eligibilityService = getEligibilityService();

        Instant now = Instant.now();
        List<DispatchProposal> filteredProposals = proposals.stream()
                .filter(p -> p.getExpiresAt() == null || p.getExpiresAt().isAfter(now))
                .filter(p -> isProposalStillMatchingWorkerPreferences(p, worker, availabilitySlots, eligibilityService, now))
                .toList();
        if (filteredProposals.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> shiftIds = filteredProposals.stream()
                .map(DispatchProposal::getShift)
                .filter(Objects::nonNull)
                .map(Shift::getShiftId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, List<ShiftSkillRequirement>> reqsByShiftId = null;
        if (shiftSkillRequirementRepository != null && !shiftIds.isEmpty()) {
            List<ShiftSkillRequirement> allReqs = shiftSkillRequirementRepository.findByShiftIdsWithSkill(shiftIds);
            if (allReqs != null) {
                reqsByShiftId = allReqs.stream()
                        .filter(r -> r.getShift() != null && r.getShift().getShiftId() != null)
                        .collect(Collectors.groupingBy(r -> r.getShift().getShiftId()));
            }
        }

        Set<UUID> neededSkillIds = new java.util.HashSet<>();
        for (DispatchProposal dp : filteredProposals) {
            Shift s = dp.getShift();
            if (s == null) continue;
            if (s.getPerformedSkillId() != null) {
                neededSkillIds.add(s.getPerformedSkillId());
            }
            if (s.getRequiredSkills() != null) {
                for (UUID skId : s.getRequiredSkills()) {
                    if (skId != null) neededSkillIds.add(skId);
                }
            }
        }
        Map<UUID, String> skillNamesById = preloadSkillNames(neededSkillIds);
        List<WorkerSkill> workerSkills = (workerSkillRepository != null && worker.getWorkerId() != null)
                ? workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId())
                : null;

        final Map<UUID, List<ShiftSkillRequirement>> finalReqsByShiftId = reqsByShiftId;
        return filteredProposals.stream()
                .map(p -> mapToProposalDto(p, activeAssignments, eligibilityService, workerSkills, finalReqsByShiftId, skillNamesById))
                .collect(Collectors.toList());
    }

    private boolean isProposalStillMatchingWorkerPreferences(
            DispatchProposal proposal,
            Worker worker,
            List<WorkerAvailability> availabilitySlots,
            WorkerShiftEligibilityService eligibilityService,
            Instant now) {
        Shift shift = proposal.getShift();
        if (shift == null) {
            return true;
        }
        if (shift.getEndTime() != null && !now.isBefore(shift.getEndTime())) {
            return false;
        }
        if (!eligibilityService.isShiftCoveredByAvailability(availabilitySlots, shift)) {
            return false;
        }
        if (worker.getMinWage() != null && worker.getMinWage().compareTo(BigDecimal.ZERO) > 0
                && shift.getHourlyWage() != null
                && shift.getHourlyWage().compareTo(worker.getMinWage()) < 0) {
            return false;
        }
        if (worker.getMaxDistanceKm() != null && worker.getMaxDistanceKm() > 0) {
            Double sLat = shift.getLocationLat() != null ? shift.getLocationLat()
                    : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
            Double sLng = shift.getLocationLng() != null ? shift.getLocationLng()
                    : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);
            if (MatchingDispatchService.isValidCoordinate(worker.getBaseLat(), worker.getBaseLng())
                    && MatchingDispatchService.isValidCoordinate(sLat, sLng)) {
                double distanceKm = MatchingDispatchService.calculateDistance(
                        worker.getBaseLat(), worker.getBaseLng(), sLat, sLng);
                if (Double.isFinite(distanceKm) && distanceKm > worker.getMaxDistanceKm()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Worker chấp nhận lời mời làm việc (Accept Proposal)
     * Áp dụng Atomic Concurrency Locking để chống overbooking
     */
    @Transactional
    public ShiftAssignmentResponseDto acceptProposal(UUID userId, UUID proposalId) {
        Worker worker = getWorkerOrThrow(userId);

        if (worker.getUser() == null || worker.getUser().getStatus() != com.cani.entity.enums.UserStatus.ACTIVE) {
            throw new BadRequestException("Tài khoản của bạn hiện không ở trạng thái hoạt động (ACTIVE), không thể nhận việc.");
        }

        if (worker.getKycStatus() != com.cani.entity.enums.KycStatus.APPROVED) {
            throw new BadRequestException("Tài khoản chưa hoàn tất định danh eKYC. Vui lòng xác thực CCCD trước khi nhận việc.");
        }

        DispatchProposal proposal = getWorkerProposalOrThrow(proposalId, worker);

        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new BadRequestException("Đề xuất việc làm này đã được xử lý hoặc không còn khả dụng (Trạng thái: " + proposal.getStatus() + ")");
        }

        if (proposal.getExpiresAt() != null && proposal.getExpiresAt().isBefore(Instant.now())) {
            proposalStatusUpdater.updateStatusInNewTransaction(proposal.getProposalId(), ProposalStatus.EXPIRED);
            throw new BadRequestException("Rất tiếc! Đề xuất việc làm này đã hết hạn phản hồi.");
        }

        Shift shift = proposal.getShift();
        if (shift == null) {
            throw new BadRequestException("Ca làm việc của đề xuất này không còn tồn tại.");
        }

        // Chống tạo trùng Assignment cho cùng Worker & Shift
        Optional<ShiftAssignment> existingAssignment = shiftAssignmentRepository
                .findByShift_ShiftIdAndWorker_WorkerId(shift.getShiftId(), worker.getWorkerId());
        if (existingAssignment.isPresent() && existingAssignment.get().getStatus() != AssignmentStatus.CANCELLED) {
            throw new BadRequestException("Bạn đã xác nhận tham gia ca làm việc này trước đó.");
        }

        // Re-check toàn bộ điều kiện hợp lệ (kỹ năng, chứng chỉ, mức xác minh, eKYC, blocklist, xung đột ca)
        WorkerShiftEligibilityService.EligibilityResult eligibility = getEligibilityService().evaluate(
                worker,
                shift,
                WorkerShiftEligibilityService.MatchingChannel.PROPOSAL_ACCEPTANCE
        );
        if (!eligibility.eligible()) {
            throw new BadRequestException(eligibility.reasonMessageVi());
        }

        // 2. ATOMIC CONCURRENCY CHECK: Tăng confirmed_workers một cách nguyên tử
        int updatedRows = shiftRepository.incrementConfirmedWorkersAtomic(shift.getShiftId());
        if (updatedRows == 0) {
            proposalStatusUpdater.updateStatusInNewTransaction(proposal.getProposalId(), ProposalStatus.REJECTED);
            throw new BadRequestException("Rất tiếc! Ca làm việc này vừa được người khác nhận đủ trước bạn ít giây.");
        }
        shift = syncShiftAfterAtomicIncrement(shift);

        // 3. Cập nhật Proposal -> ACCEPTED
        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposal.setRespondedAt(Instant.now());
        dispatchProposalRepository.save(proposal);

        // 4. Tạo ShiftAssignment -> CONFIRMED
        ShiftAssignment assignment = ShiftAssignment.builder()
                .shift(shift)
                .worker(worker)
                .proposalId(proposal.getProposalId())
                .source(AssignmentSource.MATCHING)
                .status(AssignmentStatus.CONFIRMED)
                .eoConfirmed(false)
                .build();
        attachGroupInfoIfPresent(assignment, worker,
                proposal.getSource() == AssignmentSource.GROUP ? proposal.getGroupId() : null);
        assignment = shiftAssignmentRepository.save(assignment);

        // Tạo bản ghi theo dõi thanh toán trực tiếp (Non-Escrow)
        directPaymentService.createInitialPayment(assignment);
        notifyEoWorkerAcceptedShift(shift, worker, assignment);

        // 5. Cập nhật Event confirmed workers
        Event event = shift.getEvent();
        if (event != null) {
            event.setTotalConfirmedWorkers((event.getTotalConfirmedWorkers() != null ? event.getTotalConfirmedWorkers() : 0) + 1);
            eventRepository.save(event);
        }

        log.info("Worker {} đã nhận thành công ca làm việc {} (Assignment ID: {})",
                userId, shift.getShiftId(), assignment.getAssignmentId());

        return mapToAssignmentDto(assignment);
    }

    /**
     * Worker từ chối lời mời làm việc (Reject Proposal)
     */
    @Transactional
    public void rejectProposal(UUID userId, UUID proposalId, String reason) {
        Worker worker = getWorkerOrThrow(userId);
        DispatchProposal proposal = getWorkerProposalOrThrow(proposalId, worker);

        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new BadRequestException("Đề xuất việc làm này đã ở trạng thái: " + proposal.getStatus());
        }

        proposal.setStatus(ProposalStatus.REJECTED);
        proposal.setRespondedAt(Instant.now());
        dispatchProposalRepository.save(proposal);

        log.info("Worker {} đã từ chối đề xuất {}. Lý do: {}", userId, proposalId, reason);

        Shift rejectedShift = proposal.getShift();
        if (rejectedShift != null) {
            // Ticket EO-011: Thông báo cho EO khi Worker từ chối đề xuất ca khẩn cấp
            if (Boolean.TRUE.equals(rejectedShift.getEmergency())) {
                Event ev = rejectedShift.getEvent();
                String evName = ev != null ? ev.getName() : "Sự kiện";
                sendEoShiftNotification(
                        rejectedShift,
                        "⚠️ [CỨU TRỢ KHẨN CẤP] Worker từ chối lời mời nhận ca",
                        String.format("Worker %s đã từ chối đề xuất khẩn cấp ca \"%s\" (%s). Hệ thống tiếp tục điều phối ứng viên tiếp theo.",
                                worker.getFullName(), rejectedShift.getJobType(), evName),
                        com.cani.entity.enums.NotificationType.REMINDER,
                        null,
                        worker.getWorkerId(),
                        "EMERGENCY_PROPOSAL_REJECTED"
                );
            }

            if (rejectedShift.getEvent() == null || rejectedShift.getEvent().getMode() != com.cani.entity.enums.EventMode.INVITE) {
                try {
                    if (Boolean.TRUE.equals(rejectedShift.getEmergency())) {
                        matchingDispatchService.triggerEmergencyDispatchForSystem(rejectedShift.getShiftId(), null);
                    } else {
                        matchingDispatchService.dispatchForShift(rejectedShift.getShiftId());
                    }
                } catch (Exception e) {
                    log.warn("Lỗi khi dispatch lại sau khi reject: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Lịch làm việc của Worker (Các ca đã nhận)
     */
    @Transactional(readOnly = true)
    public List<ShiftAssignmentResponseDto> getMyAssignments(UUID userId) {
        Worker worker = getWorkerOrThrow(userId);
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId());
        return enrichAssignmentDtos(worker, assignments);
    }

    /**
     * Lịch sử các ca làm việc của Worker (Hỗ trợ phân trang, lọc trạng thái và tối ưu hóa O(1) batch query)
     */
    @Transactional(readOnly = true)
    public Page<ShiftAssignmentResponseDto> getAssignmentHistory(UUID userId, AssignmentStatus status, Pageable pageable) {
        Worker worker = getWorkerOrThrow(userId);
        List<AssignmentStatus> historyStatuses = List.of(
                AssignmentStatus.COMPLETED,
                AssignmentStatus.CANCELLED,
                AssignmentStatus.NO_SHOW
        );

        Page<ShiftAssignment> assignmentPage = shiftAssignmentRepository.findHistoryAssignments(
                worker.getWorkerId(),
                status,
                historyStatuses,
                pageable
        );

        if (assignmentPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<ShiftAssignmentResponseDto> dtoList = enrichAssignmentDtos(worker, assignmentPage.getContent());

        return new PageImpl<>(dtoList, pageable, assignmentPage.getTotalElements());
    }

    private List<ShiftAssignmentResponseDto> enrichAssignmentDtos(Worker worker, List<ShiftAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> assignmentIds = assignments.stream()
                .map(ShiftAssignment::getAssignmentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // 1. Batch Fetching: Chấm công (O(1) thay vì N query)
        Map<UUID, AttendanceLog> attendanceMap = (attendanceLogRepository != null && !assignmentIds.isEmpty())
                ? attendanceLogRepository.findByShiftAssignment_AssignmentIdIn(assignmentIds).stream()
                        .filter(a -> a.getShiftAssignment() != null && a.getShiftAssignment().getAssignmentId() != null)
                        .collect(Collectors.toMap(a -> a.getShiftAssignment().getAssignmentId(), a -> a, (k1, k2) -> k1))
                : Collections.emptyMap();

        // 2. Batch Fetching: Thanh toán (O(1) thay vì N query)
        Map<UUID, Payment> paymentMap = (paymentRepository != null && !assignmentIds.isEmpty())
                ? paymentRepository.findByAssignmentIdIn(assignmentIds).stream()
                        .filter(p -> p.getAssignmentId() != null)
                        .collect(Collectors.toMap(Payment::getAssignmentId, p -> p, (k1, k2) -> k1))
                : Collections.emptyMap();

        // 3. Batch Fetching: Đánh giá 2 chiều (EO <-> Worker)
        Map<UUID, List<Review>> reviewMap = (reviewRepository != null && !assignmentIds.isEmpty())
                ? reviewRepository.findByAssignmentIdIn(assignmentIds).stream()
                        .filter(r -> r.getAssignmentId() != null)
                        .collect(Collectors.groupingBy(Review::getAssignmentId))
                : Collections.emptyMap();

        // 4. Batch Fetching: Tên kỹ năng (O(1) thay vì N query trong stream)
        Set<UUID> skillIds = new java.util.HashSet<>();
        for (ShiftAssignment a : assignments) {
            if (a == null) continue;
            if (a.getConfirmedPerformedSkillId() != null) {
                skillIds.add(a.getConfirmedPerformedSkillId());
            }
            Shift s = a.getShift();
            if (s != null) {
                if (s.getPerformedSkillId() != null) {
                    skillIds.add(s.getPerformedSkillId());
                }
                if (s.getRequiredSkills() != null) {
                    for (UUID skId : s.getRequiredSkills()) {
                        if (skId != null) skillIds.add(skId);
                    }
                }
            }
        }
        Map<UUID, String> skillNamesById = preloadSkillNames(skillIds);

        UUID workerUserId = (worker != null && worker.getUser() != null) ? worker.getUser().getUserId() : null;

        return assignments.stream()
                .map(a -> mapToEnrichedAssignmentDto(
                        a,
                        attendanceMap.get(a.getAssignmentId()),
                        paymentMap.get(a.getAssignmentId()),
                        reviewMap.get(a.getAssignmentId()),
                        workerUserId,
                        skillNamesById
                ))
                .collect(Collectors.toList());
    }

    /**
     * Quét QR Check-in vào ca làm việc (Có xác thực nội dung QR ca làm & kiểm tra Geofence)
     */
    @Transactional
    public AttendanceCheckResponseDto checkinAssignment(UUID userId, UUID assignmentId, AttendanceCheckRequestDto request) {
        Worker worker = getWorkerOrThrow(userId);
        ShiftAssignment assignment = getWorkerAssignmentOrThrow(assignmentId, worker);
        if (assignment.getStatus() != AssignmentStatus.CONFIRMED) {
            throw new BadRequestException("Chỉ có thể check-in cho ca làm việc ở trạng thái CONFIRMED (Trạng thái hiện tại: " + assignment.getStatus() + ")");
        }

        Shift shift = assignment.getShift();
        Instant now = Instant.now();

        // 1. Xác thực mã QR Check-in bảo mật động (HMAC-SHA256 Anti-spoofing)
        String qrContent = request != null && request.getQrContent() != null ? request.getQrContent().trim() : "";
        if (qrContent.startsWith("CANI_CHECKOUT:")) {
            throw new BadRequestException("Đây là mã QR Check-out (Kết ca). Vui lòng quét mã QR Check-in để vào ca làm việc!");
        }
        if (shift == null || shift.getShiftId() == null) {
            throw new BadRequestException("Thông tin ca làm việc không hợp lệ.");
        }
        validateAndExtractShiftId(qrContent, "CANI_CHECKIN:", shift.getShiftId());

        // Gate thời gian: điểm danh chỉ mở từ giờ đến (arrivalTime ?: startTime) và kết thúc trước endTime
        Instant earliestCheckin = shift.getArrivalTime() != null ? shift.getArrivalTime() : shift.getStartTime();
        if (earliestCheckin != null && now.isBefore(earliestCheckin)) {
            String earliestLabel = earliestCheckin.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
            throw new BadRequestException("Ca chưa mở điểm danh. Vui lòng quay lại sau " + earliestLabel + ".");
        }
        if (shift.getEndTime() != null && now.isAfter(shift.getEndTime())) {
            throw new BadRequestException("Ca làm việc đã kết thúc, không thể quét QR điểm danh vào ca.");
        }

        // 2. Kiểm tra khoảng cách Geofence nếu có tọa độ
        if (request != null && request.getLat() != null && request.getLng() != null && shift != null) {
            Double targetLat = shift.getLocationLat() != null ? shift.getLocationLat()
                    : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
            Double targetLng = shift.getLocationLng() != null ? shift.getLocationLng()
                    : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);
            if (targetLat != null && targetLng != null) {
                double distanceKm = MatchingDispatchService.calculateDistance(
                        request.getLat(), request.getLng(), targetLat, targetLng);
                if (distanceKm > 0.2) {
                    throw new BadRequestException(String.format(
                            "Vị trí hiện tại của bạn cách điểm sự kiện %.2f km (vượt quá bán kính điểm danh 200m). Vui lòng đến đúng điểm sự kiện để check-in.",
                            distanceKm));
                }
            }
        }

        AttendanceLog attendanceLog = attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)
                .orElseGet(() -> AttendanceLog.builder()
                        .shiftAssignment(assignment)
                        .worker(worker)
                        .build());

        if (attendanceLog.getCheckinTime() != null) {
            throw new BadRequestException("Bạn đã thực hiện check-in vào ca làm việc này rồi.");
        }

        boolean isOnTime = shift == null || shift.getStartTime() == null ||
                !now.isAfter(shift.getStartTime().plus(Duration.ofMinutes(15)));

        AttendanceStatus status = isOnTime ? AttendanceStatus.ON_TIME : AttendanceStatus.LATE;

        attendanceLog.setQrGeneratedAt(now);
        attendanceLog.setQrExpiresAt(shift != null && shift.getEndTime() != null
                ? shift.getEndTime().plus(Duration.ofHours(2))
                : now.plus(Duration.ofHours(8)));
        attendanceLog.setCheckinTime(now);
        attendanceLog.setCheckinLat(request != null ? request.getLat() : null);
        attendanceLog.setCheckinLng(request != null ? request.getLng() : null);
        attendanceLog.setCheckinQrHash(!qrContent.isEmpty() ? hashString(qrContent) : "MANUAL_CHECKIN");
        attendanceLog.setIsOnTime(isOnTime);
        attendanceLog.setAttendanceStatus(status);

        attendanceLog = attendanceLogRepository.save(attendanceLog);

        assignment.setStatus(AssignmentStatus.CHECKED_IN);
        shiftAssignmentRepository.save(assignment);

        String timeTag = isOnTime ? "Đúng giờ" : "Trễ giờ";
        String evName = (shift != null && shift.getEvent() != null) ? shift.getEvent().getName() : "Sự kiện";
        sendEoShiftNotification(
                shift,
                "Nhân sự đã vào ca",
                worker.getFullName() + " vừa điểm danh vào ca \"" + (shift != null ? shift.getJobType() : "") + "\" (" + evName + ") - " + timeTag + ".",
                com.cani.entity.enums.NotificationType.ASSIGNMENT,
                assignmentId
        );

        String msg = isOnTime
                ? "Điểm danh Check-in thành công! Bạn đã có mặt đúng giờ."
                : "Điểm danh Check-in thành công (Ghi nhận có mặt muộn).";

        return AttendanceCheckResponseDto.builder()
                .assignmentId(assignmentId)
                .attendanceId(attendanceLog.getAttendanceId())
                .attendanceStatus(status)
                .assignmentStatus(assignment.getStatus().name())
                .checkinTime(now)
                .isOnTime(isOnTime)
                .message(msg)
                .build();
    }

    /**
     * Quét QR Check-out hoàn thành ca làm việc
     */
    @Transactional
    public AttendanceCheckResponseDto checkoutAssignment(UUID userId, UUID assignmentId, AttendanceCheckRequestDto request) {
        Worker worker = getWorkerOrThrow(userId);
        ShiftAssignment assignment = getWorkerAssignmentOrThrow(assignmentId, worker);
        if (assignment.getStatus() != AssignmentStatus.CHECKED_IN
                && !(assignment.getStatus() == AssignmentStatus.COMPLETED && Boolean.TRUE.equals(assignment.getEoConfirmed()))) {
            throw new BadRequestException("Chỉ có thể check-out cho ca làm việc đang ở trạng thái CHECKED_IN (Trạng thái hiện tại: " + assignment.getStatus() + ")");
        }
        Payment payment = paymentRepository != null
                ? paymentRepository.findByAssignmentId(assignmentId).orElse(null)
                : null;
        // Chỉ chặn khi EO ĐÃ KHAI BÁO THANH TOÁN mà worker chưa phản hồi —
        // Ca chưa được EO khai báo thanh toán (NOT_DECLARED) thì vẫn cho kết ca (lối thoát kết ca hợp lệ theo quy tắc dự án)
        if (payment != null
                && payment.getStatus() != com.cani.entity.enums.PaymentStatus.NOT_DECLARED
                && (payment.getWorkerConfirmStatus() == null || payment.getWorkerConfirmStatus().isBlank())) {
            throw new BadRequestException("Ban tổ chức đã khai báo thanh toán. Vui lòng phản hồi tình trạng nhận thù lao (Bước 4) trước khi quét QR kết ca.");
        }
        Shift shift = assignment.getShift();

        // 1. Xác thực mã QR Check-out bảo mật động (HMAC-SHA256 Anti-spoofing)
        String qrContent = request != null && request.getQrContent() != null ? request.getQrContent().trim() : "";
        if (qrContent.startsWith("CANI_CHECKIN:")) {
            throw new BadRequestException("Đây là mã QR Check-in (Vào ca). Vui lòng quét mã QR Check-out để kết thúc ca làm việc!");
        }
        if (shift == null || shift.getShiftId() == null) {
            throw new BadRequestException("Thông tin ca làm việc không hợp lệ.");
        }
        validateAndExtractShiftId(qrContent, "CANI_CHECKOUT:", shift.getShiftId());

        AttendanceLog attendanceLog = attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)
                .orElseThrow(() -> new BadRequestException("Bạn chưa check-in vào ca làm việc này nên không thể check-out."));

        if (attendanceLog.getCheckoutTime() != null) {
            throw new BadRequestException("Bạn đã check-out hoàn thành ca làm việc này rồi.");
        }

        Instant now = Instant.now();
        Instant checkin = attendanceLog.getCheckinTime() != null ? attendanceLog.getCheckinTime() : now.minus(Duration.ofHours(4));
        long minutes = Math.max(30, Duration.between(checkin, now).toMinutes());
        BigDecimal hoursWorked = BigDecimal.valueOf(minutes / 60.0);

        attendanceLog.setCheckoutTime(now);
        attendanceLog.setCheckoutQrHash(!qrContent.isEmpty() ? hashString(qrContent) : "MANUAL_CHECKOUT");
        attendanceLog.setHoursWorked(hoursWorked);
        attendanceLog.setAttendanceStatus(AttendanceStatus.CHECKED_OUT);

        if (request != null && request.getProofImageUrl() != null && !request.getProofImageUrl().isBlank()) {
            attendanceLog.setProofImageUrl(request.getProofImageUrl().trim());
            attendanceLog.setProofSubmittedAt(now);
        }

        attendanceLogRepository.save(attendanceLog);

        boolean wasAlreadyCompleted = (assignment.getStatus() == AssignmentStatus.COMPLETED);
        assignment.setStatus(AssignmentStatus.COMPLETED);
        shiftAssignmentRepository.save(assignment);

        if (!wasAlreadyCompleted) {
            worker.setTotalShiftsCompleted((worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0) + 1);
            workerRepository.save(worker);
        }
        // Chỉ cấp SP_experience khi EO đã xác nhận kỹ năng hoàn thành (hoặc đã EO verify trước checkout);
        // nếu chưa verify, giữ SP ở trạng thái chờ xác nhận, tuyệt đối không chặn checkout/payment.
        if (Boolean.TRUE.equals(assignment.getEoConfirmed()) && skillProgressionService != null) {
            skillProgressionService.recordCompletedShiftForAssignment(assignment, assignment.getConfirmedPerformedSkillId());
        }

        Shift completedShift = assignment.getShift();
        String evName = (completedShift != null && completedShift.getEvent() != null) ? completedShift.getEvent().getName() : "Sự kiện";
        sendEoShiftNotification(
                completedShift,
                "Nhân sự đã kết ca",
                worker.getFullName() + " đã hoàn thành và kết ca \"" + (completedShift != null ? completedShift.getJobType() : "") + "\" (" + evName + "). Tổng giờ làm: " + hoursWorked + " giờ.",
                com.cani.entity.enums.NotificationType.ASSIGNMENT,
                assignmentId
        );

        return AttendanceCheckResponseDto.builder()
                .assignmentId(assignmentId)
                .attendanceId(attendanceLog.getAttendanceId())
                .attendanceStatus(AttendanceStatus.CHECKED_OUT)
                .assignmentStatus(assignment.getStatus().name())
                .checkinTime(attendanceLog.getCheckinTime())
                .checkoutTime(now)
                .hoursWorked(hoursWorked)
                .isOnTime(attendanceLog.getIsOnTime())
                .proofImageUrl(attendanceLog.getProofImageUrl())
                .message("Điểm danh Check-out thành công! Cảm ơn bạn đã hoàn thành ca làm việc.")
                .build();
    }

    /**
     * Quét và tìm kiếm các ca làm việc xung quanh vị trí của Worker trên bản đồ
     */
    @Transactional(readOnly = true)
    public List<NearbyShiftResponseDto> getNearbyShifts(Double userLat, Double userLng, Double radiusKm) {
        return getNearbyShifts(null, userLat, userLng, radiusKm);
    }

    @Transactional(readOnly = true)
    public List<NearbyShiftResponseDto> getNearbyShifts(UUID userId, Double userLat, Double userLng, Double radiusKm) {
        return getNearbyShiftQueryService().getNearbyShifts(userId, userLat, userLng, radiusKm);
    }

    /** Map skillId -> tên kỹ năng (taxonomy) để mobile hiển thị badge kỹ năng bắt buộc */
    private List<String> resolveSkillNames(UUID[] skillIds) {
        if (skillIds == null || skillIds.length == 0) {
            return Collections.emptyList();
        }
        Set<UUID> ids = Arrays.stream(skillIds).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Skill> skills = skillRepository.findAllById(ids);
        return skills.stream().map(Skill::getName).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private String resolveSingleSkillName(UUID skillId) {
        if (skillId == null || skillRepository == null) {
            return null;
        }
        return skillRepository.findById(skillId).map(Skill::getName).orElse(null);
    }

    private String buildProposalRecommendationReason(
            WorkerShiftEligibilityService.SkillFitResult fit,
            Shift s,
            DispatchProposal p
    ) {
        if (fit != null && !fit.matchedSkillNames().isEmpty()) {
            String joined = String.join(", ", fit.matchedSkillNames().stream().limit(2).toList());
            if (fit.requiredQuality() >= 0.5 || fit.preferredFit() >= 0.5) {
                return "Kỹ năng đã xác thực phù hợp với ca (" + joined + ")";
            }
            return "Đáp ứng kỹ năng công việc (" + joined + ")";
        }
        if (p != null && p.getScoreBreakdown() != null && !p.getScoreBreakdown().isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(p.getScoreBreakdown());
                double distScore = node.path("distScore").asDouble(0.0);
                if (distScore >= 75.0) {
                    return "Vị trí làm việc gần bạn và hồ sơ uy tín phù hợp";
                }
            } catch (Exception ignored) {
            }
        }
        return s != null && s.getJobType() != null && !s.getJobType().isBlank()
                ? "Phù hợp hồ sơ và lịch rảnh cho vị trí " + s.getJobType()
                : "Đề xuất phù hợp theo hồ sơ của bạn";
    }

    /**
     * Worker chủ động ứng tuyển trực tiếp vào ca làm việc trên Job Radar (M5)
     */
    @Transactional
    public ShiftAssignmentResponseDto applyForShift(UUID userId, UUID shiftId) {
        Worker worker = getWorkerOrThrow(userId);

        if (worker.getKycStatus() != com.cani.entity.enums.KycStatus.APPROVED) {
            throw new BadRequestException("Tài khoản chưa hoàn tất định danh eKYC. Vui lòng xác thực CCCD trước khi ứng tuyển ca làm việc.");
        }

        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy ca làm việc ID: " + shiftId));

        if (shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(shiftId, worker.getWorkerId())) {
            throw new BadRequestException("Bạn đã nhận hoặc ứng tuyển vào ca làm việc này rồi!");
        }

        // Thống nhất kiểm tra điều kiện qua WorkerShiftEligibilityService (Emergency KHÔNG bypass kỹ năng/RS/eKYC/trùng ca)
        WorkerShiftEligibilityService.EligibilityResult eligibility = getEligibilityService().evaluate(
                worker,
                shift,
                WorkerShiftEligibilityService.MatchingChannel.EMERGENCY_APPLY
        );
        if (!eligibility.eligible()) {
            throw new BadRequestException(eligibility.reasonMessageVi());
        }

        int updatedRows = shiftRepository.incrementConfirmedWorkersAtomic(shiftId);
        if (updatedRows == 0) {
            throw new BadRequestException("Rất tiếc! Ca làm việc này vừa đủ số lượng nhân sự.");
        }
        shift = syncShiftAfterAtomicIncrement(shift);

        ShiftAssignment assignment = ShiftAssignment.builder()
                .shift(shift)
                .worker(worker)
                .source(AssignmentSource.DIRECT)
                .status(AssignmentStatus.CONFIRMED)
                .eoConfirmed(false)
                .build();
        // Ứng tuyển cá nhân (DIRECT) không gán group info
        assignment = shiftAssignmentRepository.save(assignment);

        directPaymentService.createInitialPayment(assignment);
        notifyEoWorkerAcceptedShift(shift, worker, assignment);

        Event event = shift.getEvent();
        if (event != null) {
            event.setTotalConfirmedWorkers((event.getTotalConfirmedWorkers() != null ? event.getTotalConfirmedWorkers() : 0) + 1);
            eventRepository.save(event);
        }

        log.info("Worker {} đã ứng tuyển trực tiếp thành công vào ca {} (Assignment ID: {})",
                userId, shiftId, assignment.getAssignmentId());

        return mapToAssignmentDto(assignment);
    }

    // =========================================================================
    // UC-25: KHAI BÁO & LẤY LỊCH RẢNH THEO TUẦN (WORKER AVAILABILITY)
    // =========================================================================
    @Transactional(readOnly = true)
    public List<WorkerAvailabilityDto> getWorkerAvailability(UUID userId) {
        Worker worker = getWorkerOrThrow(userId);

        return workerAvailabilityRepository
                .findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(worker.getWorkerId())
                .stream()
                .map(a -> WorkerAvailabilityDto.builder()
                        .dayOfWeek(a.getDayOfWeek())
                        .startTime(a.getStartTime())
                        .endTime(a.getEndTime())
                        .isAvailable(a.getIsAvailable())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public List<WorkerAvailabilityDto> updateWorkerAvailability(UUID userId, UpdateAvailabilityRequestDto request) {
        Worker worker = getWorkerOrThrow(userId);

        workerAvailabilityRepository.deleteByWorker_WorkerId(worker.getWorkerId());

        if (request.getSchedules() != null && !request.getSchedules().isEmpty()) {
            List<WorkerAvailability> toSave = request.getSchedules().stream().map(dto -> WorkerAvailability.builder()
                    .worker(worker)
                    .dayOfWeek(dto.getDayOfWeek())
                    .startTime(dto.getStartTime())
                    .endTime(dto.getEndTime())
                    .isAvailable(dto.getIsAvailable() != null ? dto.getIsAvailable() : true)
                    .build()).collect(Collectors.toList());
            workerAvailabilityRepository.saveAll(toSave);
        }

        log.info("Worker {} đã cập nhật thành công lịch rảnh tuần ({} khung giờ)", userId,
                request.getSchedules() != null ? request.getSchedules().size() : 0);

        return getWorkerAvailability(userId);
    }

    // =========================================================================
    // UC-26 & UC-34: CÀI ĐẶT BÁN KÍNH LÀM VIỆC & TIÊU CHÍ MATCHING (PREFERENCES)
    // =========================================================================
    @Transactional(readOnly = true)
    public WorkerPreferencesDto getWorkerPreferences(UUID userId) {
        Worker worker = getWorkerOrThrow(userId);

        return WorkerPreferencesDto.builder()
                .baseAddress(worker.getBaseAddress())
                .baseLat(worker.getBaseLat())
                .baseLng(worker.getBaseLng())
                .minWage(worker.getMinWage() != null ? worker.getMinWage() : BigDecimal.ZERO)
                .maxDistanceKm(worker.getMaxDistanceKm() != null ? worker.getMaxDistanceKm() : DEFAULT_MAX_DISTANCE_KM)
                .isOnline(Boolean.TRUE.equals(worker.getIsOnline()))
                .build();
    }

    @Transactional
    public WorkerPreferencesDto updateWorkerPreferences(UUID userId, WorkerPreferencesDto dto) {
        Worker worker = getWorkerOrThrow(userId);

        if (dto.getBaseAddress() != null) worker.setBaseAddress(dto.getBaseAddress());
        if (dto.getBaseLat() != null) worker.setBaseLat(dto.getBaseLat());
        if (dto.getBaseLng() != null) worker.setBaseLng(dto.getBaseLng());
        if (dto.getMinWage() != null) worker.setMinWage(dto.getMinWage());
        if (dto.getMaxDistanceKm() != null) worker.setMaxDistanceKm(dto.getMaxDistanceKm());

        Worker savedWorker = workerRepository.save(worker);
        if (savedWorker != null) {
            worker = savedWorker;
        }
        log.info("Worker {} đã cập nhật tiêu chí tìm việc (Bán kính: {} km, Lương tối thiểu: {})",
                userId, worker.getMaxDistanceKm(), worker.getMinWage());

        // Tự động quét lại các ca ACTIVE phù hợp với khu vực/tiêu chí vừa cập nhật nếu Worker đang bật nhận việc
        if (Boolean.TRUE.equals(worker.getIsOnline()) && matchingDispatchService != null) {
            try {
                matchingDispatchService.dispatchActiveShiftsForWorker(worker);
            } catch (Exception ex) {
                log.warn("Reverse-matching sau khi cập nhật Preferences cho worker {} gặp lỗi không nghiêm trọng: {}",
                        userId, ex.getMessage());
            }
        }

        return getWorkerPreferences(userId);
    }

    // =========================================================================
    // UC-37 (WBS 100.0): QUẢN LÝ KINH NGHIỆM LÀM VIỆC CỦA WORKER (MANAGE EXPERIENCE)
    // =========================================================================
    @Transactional(readOnly = true)
    public WorkerExperienceDto getWorkerExperience(UUID userId) {
        Worker worker = getWorkerOrThrow(userId);
        return WorkerExperienceDto.builder()
                .yearsExperience(worker.getYearsExperience() != null ? worker.getYearsExperience() : 0)
                .bio(worker.getBio())
                .totalShiftsCompleted(worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0)
                .skillTier(worker.getSkillTier())
                .ratingAvg(worker.getRatingAvg() != null ? worker.getRatingAvg() : BigDecimal.ZERO)
                .reliabilityScore(worker.getReliabilityScore() != null ? worker.getReliabilityScore() : 100)
                .build();
    }

    @Transactional
    public WorkerExperienceDto updateWorkerExperience(UUID userId, WorkerExperienceDto dto) {
        Worker worker = getWorkerOrThrow(userId);
        if (dto.getYearsExperience() != null) {
            worker.setYearsExperience(dto.getYearsExperience());
        }
        if (dto.getBio() != null) {
            worker.setBio(dto.getBio().trim());
        }
        workerRepository.save(worker);
        log.info("Worker {} đã cập nhật hồ sơ kinh nghiệm: {} năm kinh nghiệm", userId, worker.getYearsExperience());
        return getWorkerExperience(userId);
    }

    // =========================================================================
    // UC-BANK: QUẢN LÝ TÀI KHOẢN NGÂN HÀNG THỤ HƯỞNG (VIETQR NHẬN THÙ LAO)
    // =========================================================================
    @Transactional(readOnly = true)
    public WorkerBankAccountDto getBankAccount(UUID userId) {
        Worker worker = getWorkerOrThrow(userId);
        String name = worker.getBankAccountName();
        if ((name == null || name.isBlank()) && worker.getUser() != null) {
            name = worker.getUser().getFullName();
        }

        String vietQr = VietQrUtil.buildVietQrUrl(
                worker.getBankCode(),
                worker.getBankAccountNumber(),
                name,
                null
        );

        return WorkerBankAccountDto.builder()
                .bankCode(worker.getBankCode())
                .bankAccountNumber(worker.getBankAccountNumber())
                .bankAccountNumberMasked(worker.getBankAccountNumberMasked())
                .bankAccountName(name)
                .vietQrUrl(vietQr)
                .build();
    }

    @Transactional
    public WorkerBankAccountDto updateBankAccount(UUID userId, WorkerBankAccountDto dto) {
        Worker worker = getWorkerOrThrow(userId);

        if (dto.getBankCode() != null) {
            worker.setBankCode(dto.getBankCode().trim().toUpperCase());
        }
        if (dto.getBankAccountNumber() != null) {
            worker.setBankAccountNumber(dto.getBankAccountNumber().trim().replaceAll("\\s+", ""));
        }
        if (dto.getBankAccountName() != null) {
            worker.setBankAccountName(dto.getBankAccountName().trim().toUpperCase());
        }

        workerRepository.save(worker);
        log.info("Worker {} đã cập nhật tài khoản ngân hàng thụ hưởng: Bank={}, AccNum={}",
                userId, worker.getBankCode(), worker.getBankAccountNumberMasked());

        if (consentRecordRepository != null) {
            try {
                consentRecordRepository.save(EkycConsentRecord.builder()
                        .userId(userId)
                        .targetRole("WORKER")
                        .noticeVersion("PAYMENT_PAYOUT_CONSENT_V1")
                        .consentedAt(Instant.now())
                        .build());
            } catch (Exception e) {
                log.warn("Không thể lưu bản ghi đồng thuận thanh toán cho worker {}: {}", userId, e.getMessage());
            }
        }

        return getBankAccount(userId);
    }

    // =========================================================================
    // UC-53: HỦY CA LÀM VIỆC ĐÃ NHẬN & PHẠT ĐIỂM UY TÍN (CANCEL ASSIGNMENT)
    // =========================================================================
    @Transactional
    public void cancelAssignment(UUID userId, UUID assignmentId, String reason) {
        Worker worker = getWorkerOrThrow(userId);
        ShiftAssignment assignment = getWorkerAssignmentOrThrow(assignmentId, worker);

        if (assignment.getStatus() == AssignmentStatus.CANCELLED) {
            throw new BadRequestException("Ca làm việc này đã được hủy trước đó rồi.");
        }

        if (assignment.getStatus() == AssignmentStatus.COMPLETED) {
            throw new BadRequestException("Ca làm việc đã hoàn thành, không thể hủy.");
        }

        if (assignment.getStatus() == AssignmentStatus.CHECKED_IN) {
            throw new BadRequestException("Bạn đã điểm danh vào ca, không thể hủy nữa. Vui lòng liên hệ bộ phận CSKH để hỗ trợ.");
        }

        if (assignment.getStatus() == AssignmentStatus.NO_SHOW) {
            throw new BadRequestException("Ca làm việc này đã bị ghi nhận vắng mặt (No-show), không thể hủy.");
        }

        Shift shift = assignment.getShift();
        Instant now = Instant.now();
        Instant startTime = shift != null ? shift.getStartTime() : null;

        int penaltyPoints = calculateCancellationPenalty(now, startTime);
        int currentScore = worker.getReliabilityScore() != null ? worker.getReliabilityScore() : MAX_RELIABILITY_SCORE;
        int newScore = Math.max(MIN_RELIABILITY_SCORE, currentScore - penaltyPoints);
        worker.setReliabilityScore(newScore);
        worker.setTotalCancellations((worker.getTotalCancellations() != null ? worker.getTotalCancellations() : 0) + 1);
        workerRepository.save(worker);

        // WBS 273.0 (UC-95): Tự động khóa tài khoản khi Reliability Score < 20
        if (newScore < 20) {
            User workerUser = worker.getUser();
            if (workerUser != null && workerUser.getStatus() != com.cani.entity.enums.UserStatus.LOCKED) {
                workerUser.setStatus(com.cani.entity.enums.UserStatus.LOCKED);
                if (userRepository != null) {
                    userRepository.save(workerUser);
                }
                log.warn("Tài khoản Worker {} tự động khóa do Reliability Score < 20 (Hiện tại: {})", worker.getWorkerId(), newScore);
            }
        }

        assignment.setStatus(AssignmentStatus.CANCELLED);
        assignment.setCancellationReason(reason != null && !reason.isBlank() ? reason : "Worker tự hủy ca");
        assignment.setCancelledBy(worker.getUser() != null ? worker.getUser().getUserId() : userId);
        assignment.setCancelledAt(now);
        assignment.setCancellationType("WORKER_CANCELLED");
        shiftAssignmentRepository.save(assignment);

        if (shift != null) {
            shiftRepository.decrementConfirmedWorkersAtomic(shift.getShiftId());
            if (shift.getConfirmedWorkers() != null && shift.getConfirmedWorkers() > 0) {
                shift.setConfirmedWorkers(shift.getConfirmedWorkers() - 1);
            }
            if (shift.getRemainingWorkers() != null) {
                shift.setRemainingWorkers(shift.getRemainingWorkers() + 1);
            }

            ShiftStaffingPolicy policy = getStaffingPolicy();
            ShiftStatus effectiveStatus = policy.resolveEffectiveShiftStatus(shift, now);
            if (shift.getStatus() == ShiftStatus.FULLY_STAFFED) {
                shift.setStatus(effectiveStatus == ShiftStatus.IN_PROGRESS ? ShiftStatus.IN_PROGRESS : ShiftStatus.ACTIVE);
            } else if (effectiveStatus == ShiftStatus.IN_PROGRESS && shift.getStatus() == ShiftStatus.ACTIVE) {
                shift.setStatus(ShiftStatus.IN_PROGRESS);
            }

            Event event = shift.getEvent();
            if (event != null && event.getTotalConfirmedWorkers() != null && event.getTotalConfirmedWorkers() > 0) {
                event.setTotalConfirmedWorkers(event.getTotalConfirmedWorkers() - 1);
                eventRepository.save(event);
            }

            ShiftStaffingPolicy.AutoEmergencyEligibility autoEligibility =
                    policy.evaluateAutoEmergencyEligibility(shift, now);
            String evName = (event != null) ? event.getName() : "Sự kiện";
            String reasonText = (reason != null && !reason.isBlank()) ? reason : "Không nêu lý do";

            if (autoEligibility.eligible()) {
                shift.setEmergency(true);
                shift.setUnderstaffedAlertSent(true);
                shiftRepository.save(shift);

                int dispatched = 0;
                try {
                    dispatched = matchingDispatchService.triggerEmergencyDispatchForSystem(shift.getShiftId(), null);
                } catch (Exception e) {
                    log.warn("Không thể kích hoạt emergency dispatch tìm người thay thế: {}", e.getMessage());
                }

                sendEoShiftNotification(
                        shift,
                        "Thiếu nhân sự · Đã bật điều phối khẩn cấp: " + shift.getJobType(),
                        worker.getFullName() + " vừa hủy nhận ca \"" + shift.getJobType() + "\" (" + evName
                                + "). Hiện chốt " + autoEligibility.confirmedWorkers() + "/" + autoEligibility.requiredWorkers()
                                + " · Còn thiếu " + autoEligibility.missingWorkers()
                                + " người. Hệ thống đã tự động bật điều phối khẩn cấp tìm người thay thế"
                                + (dispatched > 0 ? " (đã gửi " + dispatched + " đề xuất)." : "."),
                        com.cani.entity.enums.NotificationType.REMINDER,
                        assignmentId
                );
            } else {
                if (autoEligibility.missingWorkers() <= 0
                        || effectiveStatus == ShiftStatus.COMPLETED
                        || effectiveStatus == ShiftStatus.CANCELLED) {
                    if (Boolean.TRUE.equals(shift.getEmergency())) {
                        shift.setEmergency(false);
                        shift.setUnderstaffedAlertSent(false);
                        shiftRepository.save(shift);
                    }
                }
                if (event != null && event.getMode() == com.cani.entity.enums.EventMode.INVITE) {
                    log.info("Bỏ qua re-dispatch Matching sau khi hủy ca {} vì sự kiện thuộc chế độ INVITE", shift.getShiftId());
                } else {
                    log.info("Không kích hoạt Emergency Dispatch sau khi hủy ca {}: {}",
                            shift.getShiftId(), autoEligibility.blockReason());
                }
                sendEoShiftNotification(
                        shift,
                        "Cảnh báo: Nhân sự hủy ca",
                        worker.getFullName() + " vừa hủy nhận ca \"" + shift.getJobType() + "\" (" + evName + "). Lý do: " + reasonText,
                        com.cani.entity.enums.NotificationType.REMINDER,
                        assignmentId
                );
            }
        }

        log.info("Worker {} đã hủy ca {} (Bị trừ {} điểm uy tín, điểm mới: {})",
                userId, assignmentId, penaltyPoints, worker.getReliabilityScore());
    }

    // =========================================================================
    // UC-45: CHECKLIST CÔNG VIỆC TRONG CA (SHIFT CHECKLIST)
    // Tách Template (Shift) vs Tiến độ cá nhân (ShiftAssignment.checklistProgress)
    // =========================================================================
    @Transactional(readOnly = true)
    public List<ShiftChecklistItemDto> getShiftChecklist(UUID userId, UUID assignmentId) {
        Worker worker = getWorkerOrThrow(userId);
        ShiftAssignment assignment = getWorkerAssignmentOrThrow(assignmentId, worker);

        Shift shift = assignment.getShift();
        String templateJson = shift != null ? shift.getChecklist() : null;
        List<ShiftChecklistItemDto> templateItems = parseChecklistTemplate(templateJson);
        if (templateItems.isEmpty()) {
            return List.of();
        }

        // Trước khi check-in: vẫn trả về danh sách đầu việc để Worker xem trước (chỉ đọc);
        // chỉ cho phép cập nhật/tích chọn sau khi CHECKED_IN ở updateShiftChecklist().
        boolean canHaveProgress = assignment.getStatus() == AssignmentStatus.CHECKED_IN
                || assignment.getStatus() == AssignmentStatus.COMPLETED;
        Map<String, ChecklistProgressDto> progressMap = canHaveProgress
                ? parseChecklistProgress(assignment.getChecklistProgress())
                : Collections.emptyMap();

        for (ShiftChecklistItemDto item : templateItems) {
            ChecklistProgressDto prog = progressMap.get(item.getId());
            if (prog != null && Boolean.TRUE.equals(prog.getIsCompleted())) {
                item.setIsCompleted(true);
                item.setCompletedAt(prog.getCompletedAt());
                item.setEvidenceImageUrl(prog.getEvidenceImageUrl());
                item.setEvidenceSubmittedAt(prog.getEvidenceSubmittedAt());
            } else {
                item.setIsCompleted(false);
                item.setCompletedAt(null);
                item.setEvidenceImageUrl(null);
                item.setEvidenceSubmittedAt(null);
            }
        }

        return templateItems;
    }

    @Transactional
    public void submitAssignmentProof(UUID userId, UUID assignmentId, String proofImageUrl) {
        Worker worker = getWorkerOrThrow(userId);
        ShiftAssignment assignment = getWorkerAssignmentOrThrow(assignmentId, worker);
        if (assignment.getStatus() != AssignmentStatus.CHECKED_IN
                && assignment.getStatus() != AssignmentStatus.COMPLETED) {
            throw new BadRequestException("Bạn cần check-in vào ca trước khi nộp ảnh bằng chứng nghiệm thu.");
        }
        AttendanceLog attendanceLog = attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)
                .orElseThrow(() -> new BadRequestException("Chưa có dữ liệu điểm danh vào ca cho phân công này."));

        String cleanUrl = proofImageUrl != null ? proofImageUrl.trim() : "";
        if (cleanUrl.isEmpty()) {
            attendanceLog.setProofImageUrl(null);
            attendanceLog.setProofSubmittedAt(null);
        } else {
            attendanceLog.setProofImageUrl(cleanUrl);
            attendanceLog.setProofSubmittedAt(Instant.now());
        }
        attendanceLogRepository.save(attendanceLog);

        if (!cleanUrl.isEmpty()) {
            Shift shift = assignment.getShift();
            String evName = (shift != null && shift.getEvent() != null) ? shift.getEvent().getName() : "Sự kiện";
            sendEoShiftNotification(
                    shift,
                    "Nhân sự đã nộp ảnh nghiệm thu ca làm",
                    worker.getFullName() + " đã gửi ảnh nghiệm thu ca \""
                            + (shift != null ? shift.getJobType() : "") + "\" (" + evName + "). Vui lòng kiểm tra và Duyệt công!",
                    com.cani.entity.enums.NotificationType.ASSIGNMENT,
                    assignmentId
            );
        }
    }

    @Transactional
    public List<ShiftChecklistItemDto> updateShiftChecklist(UUID userId, UUID assignmentId, UpdateChecklistRequestDto request) {
        Worker worker = getWorkerOrThrow(userId);
        ShiftAssignment assignment = getWorkerAssignmentOrThrow(assignmentId, worker);
        if (assignment.getStatus() != AssignmentStatus.CHECKED_IN) {
            throw new BadRequestException("Bạn phải check-in vào ca trước khi tích chọn đầu việc.");
        }

        Shift shift = assignment.getShift();
        String templateJson = shift != null ? shift.getChecklist() : null;
        List<ShiftChecklistItemDto> templateItems = parseChecklistTemplate(templateJson);

        Set<String> validIds = templateItems.stream()
                .map(ShiftChecklistItemDto::getId)
                .collect(Collectors.toSet());

        Map<String, ChecklistProgressDto> progressMap = parseChecklistProgress(assignment.getChecklistProgress());

        Set<String> previouslyCompletedIds = progressMap.entrySet().stream()
                .filter(e -> e.getValue() != null && Boolean.TRUE.equals(e.getValue().getIsCompleted()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (request != null && request.getItems() != null) {
            Instant now = Instant.now();
            for (ShiftChecklistItemDto incoming : request.getItems()) {
                String itemId = incoming.getId();
                // Validate itemId thuộc template (nếu template có danh mục id)
                if (itemId != null && (validIds.isEmpty() || validIds.contains(itemId))) {
                    boolean completed = Boolean.TRUE.equals(incoming.getIsCompleted());
                    Instant completedAt = completed
                            ? (incoming.getCompletedAt() != null ? incoming.getCompletedAt() : now)
                            : null;
                    String evidenceUrl = (incoming.getEvidenceImageUrl() != null && !incoming.getEvidenceImageUrl().isBlank())
                            ? incoming.getEvidenceImageUrl().trim()
                            : null;
                    Instant evidenceSubmittedAt = evidenceUrl != null
                            ? (incoming.getEvidenceSubmittedAt() != null ? incoming.getEvidenceSubmittedAt() : now)
                            : null;

                    progressMap.put(itemId, ChecklistProgressDto.builder()
                            .isCompleted(completed)
                            .completedAt(completedAt)
                            .evidenceImageUrl(evidenceUrl)
                            .evidenceSubmittedAt(evidenceSubmittedAt)
                            .build());
                }
            }
        }

        try {
            assignment.setChecklistProgress(objectMapper.writeValueAsString(progressMap));
            shiftAssignmentRepository.save(assignment);
        } catch (Exception e) {
            log.error("Không thể lưu checklist progress của Assignment {}: {}", assignmentId, e.getMessage());
            throw new BadRequestException("Không thể lưu tiến độ công việc. Vui lòng thử lại.");
        }

        List<String> newlyCompletedLabels = new ArrayList<>();
        int completedCount = 0;

        // Trả về danh sách template đã merge với tiến độ mới nhất của chính worker này
        for (ShiftChecklistItemDto item : templateItems) {
            ChecklistProgressDto prog = progressMap.get(item.getId());
            if (prog != null && Boolean.TRUE.equals(prog.getIsCompleted())) {
                item.setIsCompleted(true);
                item.setCompletedAt(prog.getCompletedAt());
                item.setEvidenceImageUrl(prog.getEvidenceImageUrl());
                item.setEvidenceSubmittedAt(prog.getEvidenceSubmittedAt());
                completedCount++;
                if (!previouslyCompletedIds.contains(item.getId())) {
                    String titleLabel = item.getTitle() != null ? item.getTitle() : item.getId();
                    newlyCompletedLabels.add(titleLabel);
                }
            } else {
                item.setIsCompleted(false);
                item.setCompletedAt(null);
                item.setEvidenceImageUrl(null);
                item.setEvidenceSubmittedAt(null);
            }
        }

        // Kiểm tra xem tất cả các đầu việc và minh chứng bắt buộc đã hoàn tất hay chưa
        boolean allRequiredItemsDone = !templateItems.isEmpty();
        for (ShiftChecklistItemDto item : templateItems) {
            ChecklistProgressDto prog = progressMap.get(item.getId());
            boolean done = prog != null && Boolean.TRUE.equals(prog.getIsCompleted());
            if (!done) {
                allRequiredItemsDone = false;
                break;
            }
            if (Boolean.TRUE.equals(item.getIsRequiredEvidence())) {
                boolean hasProof = prog != null && prog.getEvidenceImageUrl() != null && !prog.getEvidenceImageUrl().isBlank();
                if (!hasProof) {
                    allRequiredItemsDone = false;
                    break;
                }
            }
        }

        boolean isReadyForApproval = assignment.getStatus() == AssignmentStatus.CHECKED_IN && allRequiredItemsDone;
        boolean alreadyNotifiedThisBatch = progressMap.containsKey("__META_READY_NOTIFIED__");

        if (isReadyForApproval) {
            if (!alreadyNotifiedThisBatch && shift != null) {
                progressMap.put("__META_READY_NOTIFIED__", ChecklistProgressDto.builder()
                        .isCompleted(true)
                        .completedAt(Instant.now())
                        .build());
                try {
                    assignment.setChecklistProgress(objectMapper.writeValueAsString(progressMap));
                    shiftAssignmentRepository.save(assignment);
                } catch (Exception ignored) {}

                String evName = shift.getEvent() != null ? shift.getEvent().getName() : "Sự kiện";
                sendEoShiftNotification(
                        shift,
                        "Nhân sự hoàn thành công việc · Chờ nghiệm thu",
                        worker.getFullName() + " đã hoàn tất 100% đầu việc và nộp đủ minh chứng ca \""
                                + (shift.getJobType() != null ? shift.getJobType() : "") + "\" (" + evName + "). Bạn có thể nghiệm thu và duyệt công.",
                        com.cani.entity.enums.NotificationType.ASSIGNMENT,
                        assignmentId,
                        worker.getWorkerId()
                );
            }
        } else {
            if (alreadyNotifiedThisBatch) {
                progressMap.remove("__META_READY_NOTIFIED__");
                try {
                    assignment.setChecklistProgress(objectMapper.writeValueAsString(progressMap));
                    shiftAssignmentRepository.save(assignment);
                } catch (Exception ignored) {}
            }
        }

        return templateItems;
    }

    public List<ShiftChecklistItemDto> parseChecklistTemplate(String checklistJson) {
        if (checklistJson == null || checklistJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JsonNode root = objectMapper.readTree(checklistJson);
            if (!root.isArray()) {
                return new ArrayList<>();
            }
            List<ShiftChecklistItemDto> result = new ArrayList<>();
            int index = 1;
            for (JsonNode node : root) {
                if (node.isTextual()) {
                    result.add(ShiftChecklistItemDto.builder()
                            .id("chk_" + index)
                            .title(node.asText())
                            .instruction(null)
                            .isRequiredEvidence(false)
                            .isCompleted(false)
                            .build());
                    index++;
                } else if (node.isObject()) {
                    String id = node.hasNonNull("id") ? node.get("id").asText() : ("chk_" + index);
                    String title = node.hasNonNull("title") ? node.get("title").asText() : "";
                    String rawInstruction = node.hasNonNull("instruction") ? node.get("instruction").asText().trim() : "";
                    String instruction = rawInstruction.isEmpty() ? null : rawInstruction;
                    boolean isRequired = node.hasNonNull("isRequiredEvidence")
                            ? node.get("isRequiredEvidence").asBoolean()
                            : (node.hasNonNull("requiresEvidence") && node.get("requiresEvidence").asBoolean());
                    result.add(ShiftChecklistItemDto.builder()
                            .id(id)
                            .title(title)
                            .instruction(instruction)
                            .isRequiredEvidence(isRequired)
                            .isCompleted(false)
                            .build());
                    index++;
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Không thể parse checklist template: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public Map<String, ChecklistProgressDto> parseChecklistProgress(String progressJson) {
        if (progressJson == null || progressJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(progressJson, new TypeReference<Map<String, ChecklistProgressDto>>() {});
        } catch (Exception e) {
            log.warn("Không thể parse checklist progress map: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    // =========================================================================
    // UC-69: WORKER ĐÁNH GIÁ BAN TỔ CHỨC (REVIEW EVENT ORGANIZER)
    // =========================================================================
    @Transactional
    public ReviewResponseDto createReview(UUID userId, UUID assignmentId, CreateReviewRequestDto request) {
        Worker worker = getWorkerOrThrow(userId);
        ShiftAssignment assignment = getWorkerAssignmentOrThrow(assignmentId, worker);
        Optional<AttendanceLog> attOpt = attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId);
        boolean hasCheckedOut = (attOpt.isPresent() && attOpt.get().getCheckoutTime() != null)
                || assignment.getStatus() == AssignmentStatus.COMPLETED;
        if (!hasCheckedOut) {
            throw new BadRequestException("Chỉ có thể đánh giá Ban tổ chức sau khi đã hoàn thành và kết ca.");
        }
        if (assignment.getStatus() != AssignmentStatus.COMPLETED) {
            throw new BadRequestException("Chỉ có thể đánh giá Ban tổ chức khi ca làm việc đã hoàn thành (COMPLETED).");
        }

        if (disputeRepository != null && disputeRepository.existsByAssignmentIdAndStatusIn(
                assignmentId, List.of(DisputeStatus.OPEN, DisputeStatus.INVESTIGATING, DisputeStatus.ESCALATED))) {
            throw new BadRequestException("Ca làm việc đang có tranh chấp chưa được giải quyết. Vui lòng hoàn tất tranh chấp trước khi gửi đánh giá.");
        }

        // getWorkerOrThrow tra cứu theo userId nên userId chính là reviewer — tránh NPE worker.getUser()
        UUID reviewerId = userId;
        if (reviewRepository.findByAssignmentIdAndReviewerId(assignmentId, reviewerId).isPresent()) {
            throw new BadRequestException("Bạn đã gửi đánh giá cho ca làm việc này rồi.");
        }

        Shift shift = assignment.getShift();
        Event event = shift != null ? shift.getEvent() : null;
        EventOrganizer eo = event != null ? event.getEventOrganizer() : null;
        if (eo == null || eo.getUser() == null) {
            throw new BadRequestException("Không xác định được Ban tổ chức để đánh giá.");
        }

        UUID revieweeId = eo.getUser().getUserId();

        Review review = Review.builder()
                .assignmentId(assignmentId)
                .reviewerId(reviewerId)
                .revieweeId(revieweeId)
                .rating(request.getRating())
                .criteriaWorkEnvironment(request.getCriteriaWorkEnvironment())
                .criteriaSupport(request.getCriteriaSupport())
                .criteriaPaymentTiming(request.getCriteriaPaymentTiming())
                .comment(request.getComment())
                .tags(request.getTags())
                .build();

        review = reviewRepository.save(review);

        // Cập nhật điểm đánh giá trung bình cho Event Organizer
        if (eventOrganizerRepository != null) {
            int currentCount = eo.getRatingCount() != null ? eo.getRatingCount() : 0;
            BigDecimal currentAvg = eo.getRatingAvg() != null ? eo.getRatingAvg() : BigDecimal.valueOf(5.0);
            BigDecimal totalScore = currentAvg.multiply(BigDecimal.valueOf(currentCount))
                    .add(BigDecimal.valueOf(request.getRating()));
            int newCount = currentCount + 1;
            eo.setRatingCount(newCount);
            eo.setRatingAvg(totalScore.divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP));
            eventOrganizerRepository.save(eo);
        }

        log.info("Worker {} đã đánh giá EO {} cho ca {} (Số sao: {})", userId, revieweeId, assignmentId, request.getRating());

        return mapToReviewResponseDto(review);
    }

    @Transactional(readOnly = true)
    public Optional<ReviewResponseDto> getAssignmentReview(UUID userId, UUID assignmentId) {
        Worker worker = getWorkerOrThrow(userId);
        return reviewRepository.findByAssignmentIdAndReviewerId(assignmentId, userId)
                .map(this::mapToReviewResponseDto);
    }

    // =========================================================================
    // UC-60 / UC-64: THỐNG KÊ THU NHẬP CÁ NHÂN (INCOME SUMMARY)
    // =========================================================================
    @Transactional(readOnly = true)
    public WorkerIncomeSummaryDto getIncomeSummary(UUID userId) {
        Worker worker = getWorkerOrThrow(userId);

        List<Payment> payments = paymentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId());
        BigDecimal totalEarnings = BigDecimal.ZERO;
        BigDecimal pendingEarnings = BigDecimal.ZERO;

        for (Payment p : payments) {
            BigDecimal amount = p.getDeclaredAmount() != null ? p.getDeclaredAmount() : p.getAgreedAmount();
            BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
            if (p.getStatus() == PaymentStatus.CONFIRMED_FULL) {
                totalEarnings = totalEarnings.add(safeAmount);
            } else if (p.getStatus() == PaymentStatus.DECLARED_PENDING_WORKER
                    || p.getStatus() == PaymentStatus.CONFIRMED_PARTIAL) {
                pendingEarnings = pendingEarnings.add(safeAmount);
            }
        }

        // Single query thay vì N+1 query theo từng assignment
        List<AttendanceLog> attendanceLogs = attendanceLogRepository.findByWorker_WorkerId(worker.getWorkerId());
        BigDecimal totalHours = attendanceLogs.stream()
                .map(AttendanceLog::getHoursWorked)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return WorkerIncomeSummaryDto.builder()
                .totalEarnings(totalEarnings)
                .pendingEarnings(pendingEarnings)
                .completedShiftsCount(worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0)
                .totalHoursWorked(totalHours)
                .avgRating(worker.getRatingAvg() != null ? worker.getRatingAvg() : BigDecimal.ZERO)
                .reliabilityScore(worker.getReliabilityScore() != null ? worker.getReliabilityScore() : MAX_RELIABILITY_SCORE)
                .build();
    }

    // =========================================================================
    // PRIVATE HELPER METHODS (CLEAN CODE & DRY)
    // =========================================================================

    private Worker getWorkerOrThrow(UUID userId) {
        return workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Worker cho User ID: " + userId));
    }

    private DispatchProposal getWorkerProposalOrThrow(UUID proposalId, Worker worker) {
        DispatchProposal proposal = dispatchProposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề xuất việc làm ID: " + proposalId));
        if (!proposal.getWorker().getWorkerId().equals(worker.getWorkerId())) {
            throw new BadRequestException("Bạn không có quyền thao tác trên đề xuất việc làm này!");
        }
        return proposal;
    }

    private ShiftAssignment getWorkerAssignmentOrThrow(UUID assignmentId, Worker worker) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc đã nhận ID: " + assignmentId));
        if (!assignment.getWorker().getWorkerId().equals(worker.getWorkerId())) {
            throw new BadRequestException("Bạn không có quyền thao tác trên ca làm việc này!");
        }
        return assignment;
    }

    private int calculateCancellationPenalty(Instant now, Instant startTime) {
        if (startTime != null) {
            long hoursUntilStart = Duration.between(now, startTime).toHours();
            if (hoursUntilStart < 12) return 20;
            if (hoursUntilStart < 24) return 10;
        }
        return 5;
    }

    private BigDecimal calculateSingleWorkerShiftPay(Shift s) {
        if (s == null || s.getHourlyWage() == null || s.getStartTime() == null || s.getEndTime() == null) {
            return BigDecimal.ZERO;
        }
        long minutes = Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
        double hours = Math.max(1.0, minutes / 60.0);
        BigDecimal allowance = s.getAllowance() != null ? s.getAllowance() : BigDecimal.ZERO;
        return s.getHourlyWage().multiply(BigDecimal.valueOf(hours)).add(allowance);
    }

    private String hashString(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(raw.hashCode());
        }
    }

    private ReviewResponseDto mapToReviewResponseDto(Review r) {
        return ReviewResponseDto.builder()
                .reviewId(r.getReviewId())
                .assignmentId(r.getAssignmentId())
                .reviewerId(r.getReviewerId())
                .revieweeId(r.getRevieweeId())
                .rating(r.getRating())
                .criteriaWorkEnvironment(r.getCriteriaWorkEnvironment())
                .criteriaSupport(r.getCriteriaSupport())
                .criteriaPaymentTiming(r.getCriteriaPaymentTiming())
                .comment(r.getComment())
                .tags(r.getTags())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private Map<UUID, String> preloadSkillNames(Set<UUID> skillIds) {
        if (skillIds == null || skillIds.isEmpty() || skillRepository == null) {
            return Collections.emptyMap();
        }
        Map<UUID, String> names = new HashMap<>();
        for (Skill sk : skillRepository.findAllById(skillIds)) {
            if (sk != null && sk.getSkillId() != null && sk.getName() != null) {
                names.put(sk.getSkillId(), sk.getName());
            }
        }
        return names;
    }

    private JobProposalResponseDto mapToProposalDto(
            DispatchProposal p,
            List<ShiftAssignment> activeAssignments,
            WorkerShiftEligibilityService eligibilityService,
            List<WorkerSkill> preloadedWorkerSkills,
            Map<UUID, List<ShiftSkillRequirement>> preloadedReqsByShiftId,
            Map<UUID, String> preloadedSkillNames) {
        Shift s = p.getShift();
        Event e = s != null ? s.getEvent() : null;
        EventOrganizer eo = e != null ? e.getEventOrganizer() : null;
        String eoName = eo != null && eo.getCompanyName() != null && !eo.getCompanyName().isBlank()
                ? eo.getCompanyName()
                : (eo != null && eo.getUser() != null ? eo.getUser().getFullName() : "Ban tổ chức");

        boolean hasPreloadedReqs = preloadedReqsByShiftId != null && s != null && s.getShiftId() != null;
        List<ShiftSkillRequirement> preloadedReqs = hasPreloadedReqs
                ? preloadedReqsByShiftId.getOrDefault(s.getShiftId(), Collections.emptyList())
                : null;

        WorkerShiftEligibilityService.SkillFitResult skillFit;
        List<WorkerShiftEligibilityService.EffectiveRequirement> reqEntries;
        if (s != null) {
            if (hasPreloadedReqs || preloadedWorkerSkills != null || preloadedSkillNames != null) {
                skillFit = eligibilityService.evaluateSkillFit(
                        p.getWorker(), s, preloadedWorkerSkills, preloadedReqs, preloadedSkillNames);
                reqEntries = eligibilityService.resolveEffectiveRequirements(s, preloadedReqs, preloadedSkillNames);
            } else {
                skillFit = eligibilityService.evaluateSkillFit(p.getWorker(), s);
                reqEntries = eligibilityService.resolveEffectiveRequirements(s);
            }
        } else {
            skillFit = WorkerShiftEligibilityService.SkillFitResult.empty();
            reqEntries = Collections.emptyList();
        }
        List<String> requiredNames = !skillFit.requiredSkillNames().isEmpty()
                ? skillFit.requiredSkillNames()
                : (s != null ? (preloadedSkillNames != null
                        ? resolveSkillNamesFromMap(s.getRequiredSkills(), preloadedSkillNames)
                        : resolveSkillNames(s.getRequiredSkills())) : Collections.emptyList());
        String performedSkillName = (s != null && s.getPerformedSkillId() != null)
                ? (preloadedSkillNames != null
                        ? preloadedSkillNames.get(s.getPerformedSkillId())
                        : resolveSingleSkillName(s.getPerformedSkillId()))
                : null;
        WorkerShiftEligibilityService.ScheduleConflictInfo conflictInfo = (s != null && eligibilityService != null)
                ? eligibilityService.findScheduleConflict(s, activeAssignments)
                : WorkerShiftEligibilityService.ScheduleConflictInfo.none();

        return JobProposalResponseDto.builder()
                .proposalId(p.getProposalId())
                .shiftId(s != null ? s.getShiftId() : null)
                .eventId(e != null ? e.getEventId() : null)
                .eventName(e != null ? e.getName() : "Sự kiện")
                .eoId(eo != null ? eo.getEoId() : null)
                .eoName(eoName)
                .eoVerified(eo != null && eo.getVerificationStatus() == com.cani.entity.enums.VerificationStatus.APPROVED)
                .eoRatingAvg(eo != null ? eo.getRatingAvg() : null)
                .venueAddress(s != null && s.getLocationAddress() != null ? s.getLocationAddress() : (e != null ? e.getVenueAddress() : ""))
                .jobType(s != null ? s.getJobType() : "")
                .description(s != null ? s.getDescription() : "")
                .startTime(s != null ? s.getStartTime() : null)
                .endTime(s != null ? s.getEndTime() : null)
                .arrivalTime(s != null ? (s.getArrivalTime() != null ? s.getArrivalTime() : s.getStartTime()) : null)
                .requiredSkillNames(requiredNames)
                .preferredSkillNames(skillFit.preferredSkillNames())
                .matchedSkillNames(skillFit.matchedSkillNames())
                .performedSkillId(s != null ? s.getPerformedSkillId() : null)
                .performedSkillName(performedSkillName)
                .recommendationReasonVi(buildProposalRecommendationReason(skillFit, s, p))
                .skillRequirements(reqEntries.stream().map(WorkerShiftEligibilityService.EffectiveRequirement::toDto).toList())
                .hourlyWage(s != null ? s.getHourlyWage() : BigDecimal.ZERO)
                .allowance(s != null ? s.getAllowance() : BigDecimal.ZERO)
                .totalEstimatedPay(calculateSingleWorkerShiftPay(s))
                .matchScore(p.getMatchScore())
                .scoreBreakdown(p.getScoreBreakdown())
                .status(p.getStatus())
                .expiresAt(p.getExpiresAt())
                .source(p.getSource())
                .createdAt(p.getCreatedAt())
                .imageUrl(s != null ? s.getImageUrl() : null)
                .isConflicting(conflictInfo.conflicting())
                .conflictReason(conflictInfo.shortReasonVi())
                .build();
    }

    public String uploadProofImage(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp ảnh bằng chứng không được để trống");
        }
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }
        if (!ALLOWED_IMAGE_EXTS.contains(ext)) {
            throw new BadRequestException("Chỉ chấp nhận các định dạng ảnh: " + String.join(", ", ALLOWED_IMAGE_EXTS));
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BadRequestException("Dung lượng ảnh bằng chứng không được vượt quá 10MB");
        }
        try {
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(PROOF_UPLOAD_DIR);
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }
            String savedFilename = "proof_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
            java.nio.file.Path targetPath = uploadPath.resolve(savedFilename);
            java.nio.file.Files.copy(file.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return "/api/v1/workers/proof/" + savedFilename;
        } catch (java.io.IOException e) {
            log.error("Lỗi khi lưu tệp ảnh bằng chứng nghiệm thu ca làm: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể lưu tệp ảnh bằng chứng. Vui lòng thử lại.");
        }
    }

    public org.springframework.core.io.Resource loadProofImage(String filename) {
        try {
            // Ngăn chặn Path Traversal
            if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                throw new BadRequestException("Tên tệp không hợp lệ");
            }
            java.nio.file.Path basePath = java.nio.file.Paths.get(PROOF_UPLOAD_DIR).toAbsolutePath().normalize();
            java.nio.file.Path filePath = basePath.resolve(filename).normalize();
            if (!filePath.startsWith(basePath)) {
                throw new BadRequestException("Đường dẫn tệp không được phép");
            }
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Không tìm thấy tệp ảnh bằng chứng: " + filename);
            }
        } catch (java.net.MalformedURLException e) {
            throw new BadRequestException("Đường dẫn tệp ảnh không hợp lệ: " + filename);
        }
    }

    private Shift syncShiftAfterAtomicIncrement(Shift shift) {
        if (shift == null) return null;
        int confirmed = (shift.getConfirmedWorkers() != null ? shift.getConfirmedWorkers() : 0) + 1;
        int required = shift.getRequiredWorkers() != null ? shift.getRequiredWorkers() : 1;
        int remaining = shift.getRemainingWorkers() != null ? Math.max(0, shift.getRemainingWorkers() - 1) : Math.max(0, required - confirmed);
        shift.setConfirmedWorkers(confirmed);
        shift.setRemainingWorkers(remaining);
        if (confirmed >= required) {
            shift.setStatus(ShiftStatus.FULLY_STAFFED);
            shift.setEmergency(false);
            shift.setUnderstaffedAlertSent(false);
        }
        if (entityManager != null) {
            try {
                if (entityManager.contains(shift)) {
                    entityManager.refresh(shift);
                }
            } catch (Exception ignored) {
            }
        }
        return shift;
    }

    private void notifyEoWorkerAcceptedShift(Shift shift, Worker worker, ShiftAssignment assignment) {
        if (shift == null || worker == null || assignment == null) return;
        Event ev = shift.getEvent();
        String evName = ev != null ? ev.getName() : "Sự kiện";
        boolean isEmergency = Boolean.TRUE.equals(shift.getEmergency());
        String title = isEmergency
                ? "🚨 [CỨU TRỢ KHẨN CẤP] Nhân sự nhận ca thành công: " + shift.getJobType()
                : "Nhân sự nhận ca làm việc: " + shift.getJobType();
        String body = isEmergency
                ? String.format("Worker %s đã tiếp nhận ca cứu trợ khẩn cấp \"%s\" (%s).",
                        worker.getFullName(), shift.getJobType(), evName)
                : worker.getFullName() + " đã tham gia ca \"" + shift.getJobType() + "\" trong sự kiện \"" + evName + "\".";
        sendEoShiftNotification(
                shift,
                title,
                body,
                com.cani.entity.enums.NotificationType.ASSIGNMENT,
                assignment.getAssignmentId(),
                worker.getWorkerId(),
                isEmergency ? "EMERGENCY_ASSIGNMENT_ACCEPTED" : "ASSIGNMENT_ACCEPTED"
        );
    }

    private void sendEoShiftNotification(Shift shift, String title, String body, com.cani.entity.enums.NotificationType type, UUID assignmentId) {
        sendEoShiftNotification(shift, title, body, type, assignmentId, null, null);
    }

    private void sendEoShiftNotification(Shift shift, String title, String body, com.cani.entity.enums.NotificationType type, UUID assignmentId, UUID workerId) {
        sendEoShiftNotification(shift, title, body, type, assignmentId, workerId, null);
    }

    private void sendEoShiftNotification(Shift shift, String title, String body, com.cani.entity.enums.NotificationType type, UUID assignmentId, UUID workerId, String alertType) {
        if (notificationService == null || shift == null) return;
        Event ev = shift.getEvent();
        if (ev != null && ev.getEventOrganizer() != null && ev.getEventOrganizer().getUser() != null
                && ev.getEventOrganizer().getUser().getUserId() != null) {
            try {
                UUID eoUserId = ev.getEventOrganizer().getUser().getUserId();
                Map<String, String> metadata = new HashMap<>();
                metadata.put("eventId", ev.getEventId().toString());
                metadata.put("shiftId", shift.getShiftId().toString());
                if (assignmentId != null) {
                    metadata.put("assignmentId", assignmentId.toString());
                }
                if (workerId != null) {
                    metadata.put("workerId", workerId.toString());
                }
                if (alertType != null) {
                    metadata.put("type", alertType);
                }
                notificationService.createAndSend(
                        eoUserId,
                        title,
                        body,
                        type,
                        "SHIFT",
                        shift.getShiftId(),
                        metadata
                );
            } catch (Exception ex) {
                log.warn("Không thể gửi thông báo cho EO về ca {}: {}", shift.getShiftId(), ex.getMessage());
            }
        }
    }

    private ShiftAssignmentResponseDto mapToAssignmentDto(ShiftAssignment a) {
        AttendanceLog att = (attendanceLogRepository != null && a.getAssignmentId() != null)
                ? attendanceLogRepository.findByShiftAssignment_AssignmentId(a.getAssignmentId()).orElse(null)
                : null;
        Payment payment = (paymentRepository != null && a.getAssignmentId() != null)
                ? paymentRepository.findByAssignmentId(a.getAssignmentId()).orElse(null)
                : null;
        List<Review> reviews = (reviewRepository != null && a.getAssignmentId() != null)
                ? reviewRepository.findByAssignmentIdIn(List.of(a.getAssignmentId()))
                : Collections.emptyList();
        UUID workerUserId = (a.getWorker() != null && a.getWorker().getUser() != null)
                ? a.getWorker().getUser().getUserId()
                : null;
        return mapToEnrichedAssignmentDto(a, att, payment, reviews, workerUserId);
    }

    private List<String> resolveSkillNamesFromMap(UUID[] skillIds, Map<UUID, String> skillNamesById) {
        if (skillIds == null || skillIds.length == 0 || skillNamesById == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(skillIds)
                .filter(Objects::nonNull)
                .map(skillNamesById::get)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private ShiftAssignmentResponseDto mapToEnrichedAssignmentDto(
            ShiftAssignment a,
            AttendanceLog att,
            Payment p,
            List<Review> reviews,
            UUID workerUserId
    ) {
        return mapToEnrichedAssignmentDto(a, att, p, reviews, workerUserId, null);
    }

    private ShiftAssignmentResponseDto mapToEnrichedAssignmentDto(
            ShiftAssignment a,
            AttendanceLog att,
            Payment p,
            List<Review> reviews,
            UUID workerUserId,
            Map<UUID, String> preloadedSkillNames
    ) {
        Shift s = a.getShift();
        Event e = s != null ? s.getEvent() : null;
        EventOrganizer eo = e != null ? e.getEventOrganizer() : null;
        String eoName = eo != null && eo.getCompanyName() != null && !eo.getCompanyName().isBlank()
                ? eo.getCompanyName()
                : (eo != null && eo.getUser() != null ? eo.getUser().getFullName() : "Ban tổ chức");

        Double venueLat = s != null && s.getLocationLat() != null ? s.getLocationLat()
                : (e != null && e.getVenueLat() != null ? e.getVenueLat() : DEFAULT_CENTER_LAT);
        Double venueLng = s != null && s.getLocationLng() != null ? s.getLocationLng()
                : (e != null && e.getVenueLng() != null ? e.getVenueLng() : DEFAULT_CENTER_LNG);

        Integer eoRating = null;
        String eoComment = null;
        Instant eoReviewedAt = null;
        boolean hasWorkerReviewed = false;

        if (reviews != null && !reviews.isEmpty() && workerUserId != null) {
            for (Review r : reviews) {
                if (workerUserId.equals(r.getRevieweeId())) {
                    eoRating = r.getRating();
                    eoComment = r.getComment();
                    eoReviewedAt = r.getCreatedAt();
                }
                if (workerUserId.equals(r.getReviewerId())) {
                    hasWorkerReviewed = true;
                }
            }
        }

        BigDecimal totalPay = calculateSingleWorkerShiftPay(s);
        BigDecimal declaredAmt = p != null && p.getDeclaredAmount() != null ? p.getDeclaredAmount() : BigDecimal.ZERO;
        BigDecimal qrAmount = (declaredAmt != null && declaredAmt.compareTo(BigDecimal.ZERO) > 0) ? declaredAmt : totalPay;
        Worker workerEntity = a.getWorker();
        String bCode = workerEntity != null ? workerEntity.getBankCode() : null;
        String bAccNum = workerEntity != null ? workerEntity.getBankAccountNumber() : null;
        String bAccName = workerEntity != null ? workerEntity.getBankAccountName() : null;
        if ((bAccName == null || bAccName.isBlank()) && workerEntity != null && workerEntity.getUser() != null) {
            bAccName = workerEntity.getUser().getFullName();
        }
        String vietQr = VietQrUtil.buildVietQrUrl(bCode, bAccNum, bAccName, qrAmount);

        return ShiftAssignmentResponseDto.builder()
                .assignmentId(a.getAssignmentId())
                .shiftId(s != null ? s.getShiftId() : null)
                .eventId(e != null ? e.getEventId() : null)
                .eventName(e != null ? e.getName() : "Sự kiện")
                .eoId(eo != null ? eo.getEoId() : null)
                .eoName(eoName)
                .eoVerified(eo != null && eo.getVerificationStatus() == com.cani.entity.enums.VerificationStatus.APPROVED)
                .eoRatingAvg(eo != null ? eo.getRatingAvg() : null)
                .venueAddress(s != null && s.getLocationAddress() != null ? s.getLocationAddress() : (e != null ? e.getVenueAddress() : ""))
                .jobType(s != null ? s.getJobType() : "")
                .startTime(s != null ? s.getStartTime() : null)
                .endTime(s != null ? s.getEndTime() : null)
                .arrivalTime(s != null ? (s.getArrivalTime() != null ? s.getArrivalTime() : s.getStartTime()) : null)
                .requiredSkillNames(s != null
                        ? (preloadedSkillNames != null
                                ? resolveSkillNamesFromMap(s.getRequiredSkills(), preloadedSkillNames)
                                : resolveSkillNames(s.getRequiredSkills()))
                        : Collections.emptyList())
                .performedSkillId(s != null ? s.getPerformedSkillId() : null)
                .performedSkillName(s != null && s.getPerformedSkillId() != null
                        ? (preloadedSkillNames != null ? preloadedSkillNames.get(s.getPerformedSkillId()) : resolveSingleSkillName(s.getPerformedSkillId()))
                        : null)
                .confirmedPerformedSkillId(a.getConfirmedPerformedSkillId())
                .confirmedPerformedSkillName(a.getConfirmedPerformedSkillId() != null
                        ? (preloadedSkillNames != null ? preloadedSkillNames.get(a.getConfirmedPerformedSkillId()) : resolveSingleSkillName(a.getConfirmedPerformedSkillId()))
                        : null)
                .spExperienceAwarded(Boolean.TRUE.equals(a.getSpExperienceAwarded()))
                .spExperienceAwardedAt(a.getSpExperienceAwardedAt())
                .spRatingAwarded(Boolean.TRUE.equals(a.getSpRatingAwarded()))
                .inviteOverrideReason(a.getInviteOverrideReason())
                .hourlyWage(s != null ? s.getHourlyWage() : BigDecimal.ZERO)
                .totalPay(totalPay)
                .status(a.getStatus())
                .source(a.getSource())
                .eoConfirmed(a.getEoConfirmed())
                .venueLat(venueLat)
                .venueLng(venueLng)
                .attendanceStatus(att != null && att.getAttendanceStatus() != null ? att.getAttendanceStatus().name() : "NOT_CHECKED_IN")
                .checkinTime(att != null ? att.getCheckinTime() : null)
                .checkoutTime(att != null ? att.getCheckoutTime() : null)
                .hoursWorked(att != null ? att.getHoursWorked() : BigDecimal.ZERO)
                .createdAt(a.getCreatedAt())
                .imageUrl(s != null ? s.getImageUrl() : (e != null ? e.getImageUrl() : null))
                .proofImageUrl(att != null ? att.getProofImageUrl() : null)
                .proofSubmittedAt(att != null ? att.getProofSubmittedAt() : null)
                .paymentStatus(p != null && p.getStatus() != null ? p.getStatus().name() : "NOT_DECLARED")
                .declaredAmount(declaredAmt)
                .paymentMethod(p != null ? p.getPaymentMethod() : null)
                .workerConfirmStatus(p != null ? p.getWorkerConfirmStatus() : null)
                .paymentProofUrl(p != null ? p.getPaymentProofUrl() : null)
                .externalRef(p != null ? p.getExternalRef() : null)
                .declaredAt(p != null ? p.getDeclaredAt() : null)
                .workerComment(p != null ? p.getWorkerComment() : null)
                .bankCode(bCode)
                .bankAccountNumber(bAccNum)
                .bankAccountName(bAccName)
                .vietQrUrl(vietQr)
                .eoRating(eoRating)
                .eoReviewComment(eoComment)
                .eoReviewedAt(eoReviewedAt)
                .hasWorkerReviewed(hasWorkerReviewed)
                .build();
    }

    /**
     * Xác thực và bóc tách shiftId từ mã QR động bảo mật (HMAC-SHA256 Anti-spoofing).
     * Bắt buộc mã QR phải có định dạng: PREFIX:<shiftId>:<epochSeconds>:<signature>.
     */
    private String validateAndExtractShiftId(String qrContent, String expectedPrefix, UUID expectedShiftId) {
        if (qrContent == null || !qrContent.startsWith(expectedPrefix)) {
            throw new BadRequestException("Mã QR không hợp lệ. Vui lòng quét mã QR chính thức từ Nhà tổ chức!");
        }
        String scannedShiftPart = qrContent.substring(expectedPrefix.length()).trim();
        String[] parts = scannedShiftPart.split(":");
        if (parts.length < 3) {
            throw new BadRequestException("Mã QR không đúng định dạng bảo mật động (thiếu chữ ký số HMAC chống gian lận). Vui lòng quét mã QR trực tiếp trên màn hình sự kiện!");
        }

        String scannedShiftId = parts[0].trim();
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Mã QR không hợp lệ (thời gian làm mới không đúng định dạng).");
        }

        long currentEpochSeconds = Instant.now().getEpochSecond();
        if (Math.abs(currentEpochSeconds - epochSeconds) > 300) {
            throw new BadRequestException("Mã QR đã hết hạn hiệu lực do thời gian làm mới. Vui lòng quét mã QR đang hiển thị trực tiếp trên màn hình sự kiện!");
        }

        String signature = parts[2].trim();
        String dataToVerify = expectedPrefix + scannedShiftId + ":" + epochSeconds;
        if (!com.cani.util.QrCryptoUtil.verifySignature(dataToVerify, signature)) {
            throw new BadRequestException("Chữ ký bảo mật mã QR không hợp lệ hoặc đã bị can thiệp. Vui lòng quét mã QR chính thức từ Nhà tổ chức!");
        }

        if (expectedShiftId != null && !scannedShiftId.equalsIgnoreCase(expectedShiftId.toString())) {
            String actionName = expectedPrefix.contains("CHECKIN") ? "Check-in" : "Check-out";
            throw new BadRequestException("Mã QR " + actionName + " này không thuộc ca làm việc hiện tại của bạn!");
        }
        return scannedShiftId;
    }

    /**
     * Chỉ gán group info khi groupId được truyền (assignment source=GROUP);
     * assignment cá nhân truyền null để không làm ô nhiễm thống kê nhóm.
     */
    private void attachGroupInfoIfPresent(ShiftAssignment assignment, Worker worker, UUID groupId) {
        if (groupId == null || workerGroupMembershipRepository == null || worker == null || assignment == null) return;
        try {
            WorkerGroupMembership m = workerGroupMembershipRepository
                    .findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, worker.getWorkerId())
                    .orElse(null);
            assignment.setGroupId(groupId);
            if (m != null) {
                assignment.setIsGroupLeader(m.getRoleInGroup() == com.cani.entity.enums.GroupRole.LEADER);
            }
        } catch (Exception ex) {
            log.warn("Không thể gán group info cho assignment: {}", ex.getMessage());
        }
    }
}
