package com.cani.service;

import com.cani.dto.worker.*;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentSource;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.AttendanceStatus;
import com.cani.entity.enums.PaymentStatus;
import com.cani.entity.enums.ProposalStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

    private final WorkerRepository workerRepository;
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
    private final ObjectMapper objectMapper;

    /**
     * Bật / tắt trạng thái "Bắt đầu kiếm việc" (Online status)
     */
    @Transactional
    public Worker setOnlineStatus(UUID userId, WorkerOnlineStatusRequestDto dto) {
        Worker worker = getWorkerOrThrow(userId);

        worker.setIsOnline(dto.getIsOnline());
        if (dto.getCurrentLat() != null && dto.getCurrentLng() != null) {
            worker.setBaseLat(dto.getCurrentLat());
            worker.setBaseLng(dto.getCurrentLng());
        }
        worker = workerRepository.save(worker);
        log.info("Worker {} đã cập nhật trạng thái online: {}", userId, worker.getIsOnline());
        return worker;
    }

    /**
     * Lấy danh sách các đề xuất việc làm đang chờ Worker phản hồi (Status = PENDING)
     */
    @Transactional(readOnly = true)
    public List<JobProposalResponseDto> getMyProposals(UUID userId) {
        Worker worker = getWorkerOrThrow(userId);

        List<DispatchProposal> proposals = dispatchProposalRepository.findByWorker_WorkerIdAndStatusOrderByCreatedAtDesc(
                worker.getWorkerId(), ProposalStatus.PENDING);

        Instant now = Instant.now();
        return proposals.stream()
                .filter(p -> p.getExpiresAt() == null || p.getExpiresAt().isAfter(now))
                .map(this::mapToProposalDto)
                .collect(Collectors.toList());
    }

    /**
     * Worker chấp nhận lời mời làm việc (Accept Proposal)
     * Áp dụng Atomic Concurrency Locking để chống overbooking
     */
    @Transactional
    public ShiftAssignmentResponseDto acceptProposal(UUID userId, UUID proposalId) {
        Worker worker = getWorkerOrThrow(userId);
        DispatchProposal proposal = getWorkerProposalOrThrow(proposalId, worker);

        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new BadRequestException("Đề xuất việc làm này đã được xử lý hoặc không còn khả dụng (Trạng thái: " + proposal.getStatus() + ")");
        }

        if (proposal.getExpiresAt() != null && proposal.getExpiresAt().isBefore(Instant.now())) {
            proposal.setStatus(ProposalStatus.EXPIRED);
            dispatchProposalRepository.save(proposal);
            throw new BadRequestException("Rất tiếc! Đề xuất việc làm này đã hết hạn phản hồi.");
        }

        Shift shift = proposal.getShift();

        // 1. Kiểm tra lại trùng ca làm việc (phòng trường hợp vừa nhận ca khác)
        List<AssignmentStatus> activeStatuses = List.of(AssignmentStatus.CONFIRMED, AssignmentStatus.CHECKED_IN, AssignmentStatus.PENDING);
        List<ShiftAssignment> conflicts = shiftAssignmentRepository.findConflictingAssignments(
                worker.getWorkerId(), shift.getStartTime(), shift.getEndTime(), activeStatuses);
        if (!conflicts.isEmpty()) {
            throw new BadRequestException("Bạn đã có một ca làm việc khác trùng khung giờ với ca này!");
        }

        // 2. ATOMIC CONCURRENCY CHECK: Tăng confirmed_workers một cách nguyên tử
        int updatedRows = shiftRepository.incrementConfirmedWorkersAtomic(shift.getShiftId());
        if (updatedRows == 0) {
            proposal.setStatus(ProposalStatus.REJECTED);
            dispatchProposalRepository.save(proposal);
            throw new BadRequestException("Rất tiếc! Ca làm việc này vừa được người khác nhận đủ trước bạn ít giây.");
        }

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
        assignment = shiftAssignmentRepository.save(assignment);

        // Tạo bản ghi theo dõi thanh toán trực tiếp (Non-Escrow)
        directPaymentService.createInitialPayment(assignment);

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

        try {
            matchingDispatchService.dispatchForShift(proposal.getShift().getShiftId());
        } catch (Exception e) {
            log.warn("Lỗi khi dispatch lại sau khi reject: {}", e.getMessage());
        }
    }

    /**
     * Lịch làm việc của Worker (Các ca đã nhận)
     */
    @Transactional(readOnly = true)
    public List<ShiftAssignmentResponseDto> getMyAssignments(UUID userId) {
        Worker worker = getWorkerOrThrow(userId);
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId());
        return assignments.stream().map(this::mapToAssignmentDto).collect(Collectors.toList());
    }

    /**
     * Quét QR Check-in vào ca làm việc
     */
    @Transactional
    public AttendanceCheckResponseDto checkinAssignment(UUID userId, UUID assignmentId, AttendanceCheckRequestDto request) {
        Worker worker = getWorkerOrThrow(userId);
        ShiftAssignment assignment = getWorkerAssignmentOrThrow(assignmentId, worker);

        Shift shift = assignment.getShift();
        Instant now = Instant.now();

        AttendanceLog attendanceLog = attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)
                .orElseGet(() -> AttendanceLog.builder()
                        .shiftAssignment(assignment)
                        .worker(worker)
                        .build());

        if (attendanceLog.getCheckinTime() != null) {
            throw new BadRequestException("Bạn đã thực hiện check-in vào ca làm việc này rồi.");
        }

        boolean isOnTime = shift == null || shift.getStartTime() == null ||
                now.isBefore(shift.getStartTime().plus(Duration.ofMinutes(15)));

        AttendanceStatus status = isOnTime ? AttendanceStatus.ON_TIME : AttendanceStatus.LATE;

        attendanceLog.setCheckinTime(now);
        attendanceLog.setCheckinLat(request.getLat());
        attendanceLog.setCheckinLng(request.getLng());
        attendanceLog.setCheckinQrHash(request.getQrContent() != null ? hashString(request.getQrContent()) : "MANUAL_CHECKIN");
        attendanceLog.setIsOnTime(isOnTime);
        attendanceLog.setAttendanceStatus(status);

        attendanceLog = attendanceLogRepository.save(attendanceLog);

        assignment.setStatus(AssignmentStatus.CHECKED_IN);
        shiftAssignmentRepository.save(assignment);

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
        attendanceLog.setCheckoutQrHash(request.getQrContent() != null ? hashString(request.getQrContent()) : "MANUAL_CHECKOUT");
        attendanceLog.setHoursWorked(hoursWorked);
        attendanceLog.setAttendanceStatus(AttendanceStatus.CHECKED_OUT);

        attendanceLogRepository.save(attendanceLog);

        boolean wasAlreadyCompleted = (assignment.getStatus() == AssignmentStatus.COMPLETED);
        assignment.setStatus(AssignmentStatus.COMPLETED);
        shiftAssignmentRepository.save(assignment);

        if (!wasAlreadyCompleted) {
            worker.setTotalShiftsCompleted((worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0) + 1);
            workerRepository.save(worker);
        }

        return AttendanceCheckResponseDto.builder()
                .assignmentId(assignmentId)
                .attendanceId(attendanceLog.getAttendanceId())
                .attendanceStatus(AttendanceStatus.CHECKED_OUT)
                .assignmentStatus(assignment.getStatus().name())
                .checkinTime(attendanceLog.getCheckinTime())
                .checkoutTime(now)
                .hoursWorked(hoursWorked)
                .isOnTime(attendanceLog.getIsOnTime())
                .message("Điểm danh Check-out thành công! Cảm ơn bạn đã hoàn thành ca làm việc.")
                .build();
    }

    /**
     * Quét và tìm kiếm các ca làm việc xung quanh vị trí của Worker trên bản đồ
     */
    @Transactional(readOnly = true)
    public List<NearbyShiftResponseDto> getNearbyShifts(Double userLat, Double userLng, Double radiusKm) {
        double searchRadius = (radiusKm != null && radiusKm > 0) ? radiusKm : DEFAULT_SEARCH_RADIUS_KM;
        double centerLat = (userLat != null) ? userLat : DEFAULT_CENTER_LAT;
        double centerLng = (userLng != null) ? userLng : DEFAULT_CENTER_LNG;

        List<Shift> activeShifts = shiftRepository.findByStatus(ShiftStatus.ACTIVE);
        List<NearbyShiftResponseDto> nearbyList = new ArrayList<>();

        for (Shift s : activeShifts) {
            Double sLat = s.getLocationLat() != null ? s.getLocationLat() : (s.getEvent() != null ? s.getEvent().getVenueLat() : null);
            Double sLng = s.getLocationLng() != null ? s.getLocationLng() : (s.getEvent() != null ? s.getEvent().getVenueLng() : null);

            double distance = MatchingDispatchService.calculateDistance(centerLat, centerLng, sLat, sLng);
            if (distance <= searchRadius) {
                Event e = s.getEvent();
                nearbyList.add(NearbyShiftResponseDto.builder()
                        .shiftId(s.getShiftId())
                        .eventId(e != null ? e.getEventId() : null)
                        .eventName(e != null ? e.getName() : "Sự kiện")
                        .jobType(s.getJobType())
                        .description(s.getDescription())
                        .locationAddress(s.getLocationAddress() != null ? s.getLocationAddress() : (e != null ? e.getVenueAddress() : ""))
                        .locationLat(sLat != null ? sLat : centerLat)
                        .locationLng(sLng != null ? sLng : centerLng)
                        .hourlyWage(s.getHourlyWage())
                        .allowance(s.getAllowance())
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .distanceKm(Math.round(distance * 10.0) / 10.0)
                        .requiredWorkers(s.getRequiredWorkers())
                        .confirmedWorkers(s.getConfirmedWorkers())
                        .remainingWorkers(s.getRemainingWorkers())
                        .build());
            }
        }

        nearbyList.sort(Comparator.comparingDouble(NearbyShiftResponseDto::getDistanceKm));
        return nearbyList;
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

        workerRepository.save(worker);
        log.info("Worker {} đã cập nhật tiêu chí tìm việc (Bán kính: {} km, Lương tối thiểu: {})",
                userId, worker.getMaxDistanceKm(), worker.getMinWage());

        return getWorkerPreferences(userId);
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

        Shift shift = assignment.getShift();
        Instant now = Instant.now();
        Instant startTime = shift != null ? shift.getStartTime() : null;

        int penaltyPoints = calculateCancellationPenalty(now, startTime);
        int currentScore = worker.getReliabilityScore() != null ? worker.getReliabilityScore() : MAX_RELIABILITY_SCORE;
        worker.setReliabilityScore(Math.max(MIN_RELIABILITY_SCORE, currentScore - penaltyPoints));
        worker.setTotalCancellations((worker.getTotalCancellations() != null ? worker.getTotalCancellations() : 0) + 1);
        workerRepository.save(worker);

        assignment.setStatus(AssignmentStatus.CANCELLED);
        assignment.setCancellationReason(reason != null && !reason.isBlank() ? reason : "Worker tự hủy ca");
        assignment.setCancelledBy(worker.getUser() != null ? worker.getUser().getUserId() : userId);
        assignment.setCancelledAt(now);
        assignment.setCancellationType("WORKER_CANCELLED");
        shiftAssignmentRepository.save(assignment);

        if (shift != null) {
            if (shift.getConfirmedWorkers() != null && shift.getConfirmedWorkers() > 0) {
                shift.setConfirmedWorkers(shift.getConfirmedWorkers() - 1);
            }
            if (shift.getRemainingWorkers() != null) {
                shift.setRemainingWorkers(shift.getRemainingWorkers() + 1);
            }
            if (shift.getStatus() == ShiftStatus.FULLY_STAFFED) {
                shift.setStatus(ShiftStatus.ACTIVE);
            }
            shiftRepository.save(shift);

            Event event = shift.getEvent();
            if (event != null && event.getTotalConfirmedWorkers() != null && event.getTotalConfirmedWorkers() > 0) {
                event.setTotalConfirmedWorkers(event.getTotalConfirmedWorkers() - 1);
                eventRepository.save(event);
            }

            try {
                matchingDispatchService.dispatchForShift(shift.getShiftId());
            } catch (Exception e) {
                log.warn("Không thể kích hoạt dispatch tìm người thay thế: {}", e.getMessage());
            }
        }

        log.info("Worker {} đã hủy ca {} (Bị trừ {} điểm uy tín, điểm mới: {})",
                userId, assignmentId, penaltyPoints, worker.getReliabilityScore());
    }

    // =========================================================================
    // UC-45: CHECKLIST CÔNG VIỆC TRONG CA (SHIFT CHECKLIST)
    // =========================================================================
    @Transactional(readOnly = true)
    public List<ShiftChecklistItemDto> getShiftChecklist(UUID userId, UUID assignmentId) {
        Worker worker = getWorkerOrThrow(userId);
        ShiftAssignment assignment = getWorkerAssignmentOrThrow(assignmentId, worker);

        Shift shift = assignment.getShift();
        if (shift == null || shift.getChecklist() == null || shift.getChecklist().isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(shift.getChecklist(), new TypeReference<List<ShiftChecklistItemDto>>() {});
        } catch (Exception e) {
            log.error("Lỗi khi đọc JSON checklist của Shift {}: {}", shift.getShiftId(), e.getMessage());
            return List.of();
        }
    }

    @Transactional
    public List<ShiftChecklistItemDto> updateShiftChecklist(UUID userId, UUID assignmentId, UpdateChecklistRequestDto request) {
        Worker worker = getWorkerOrThrow(userId);
        ShiftAssignment assignment = getWorkerAssignmentOrThrow(assignmentId, worker);

        Shift shift = assignment.getShift();
        if (shift != null) {
            try {
                shift.setChecklist(objectMapper.writeValueAsString(request.getItems()));
                shiftRepository.save(shift);
            } catch (Exception e) {
                throw new BadRequestException("Không thể lưu checklist: " + e.getMessage());
            }
        }

        return request.getItems();
    }

    // =========================================================================
    // UC-69: WORKER ĐÁNH GIÁ BAN TỔ CHỨC (REVIEW EVENT ORGANIZER)
    // =========================================================================
    @Transactional
    public ReviewResponseDto createReview(UUID userId, UUID assignmentId, CreateReviewRequestDto request) {
        Worker worker = getWorkerOrThrow(userId);
        ShiftAssignment assignment = getWorkerAssignmentOrThrow(assignmentId, worker);

        UUID reviewerId = worker.getUser().getUserId();
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
        log.info("Worker {} đã đánh giá EO {} cho ca {} (Số sao: {})", userId, revieweeId, assignmentId, request.getRating());

        return mapToReviewResponseDto(review);
    }

    @Transactional(readOnly = true)
    public Optional<ReviewResponseDto> getAssignmentReview(UUID userId, UUID assignmentId) {
        Worker worker = getWorkerOrThrow(userId);
        return reviewRepository.findByAssignmentIdAndReviewerId(assignmentId, worker.getUser().getUserId())
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
        if (startTime != null && startTime.isAfter(now)) {
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

    private JobProposalResponseDto mapToProposalDto(DispatchProposal p) {
        Shift s = p.getShift();
        Event e = s != null ? s.getEvent() : null;

        return JobProposalResponseDto.builder()
                .proposalId(p.getProposalId())
                .shiftId(s != null ? s.getShiftId() : null)
                .eventId(e != null ? e.getEventId() : null)
                .eventName(e != null ? e.getName() : "Sự kiện")
                .venueAddress(s != null && s.getLocationAddress() != null ? s.getLocationAddress() : (e != null ? e.getVenueAddress() : ""))
                .jobType(s != null ? s.getJobType() : "")
                .description(s != null ? s.getDescription() : "")
                .startTime(s != null ? s.getStartTime() : null)
                .endTime(s != null ? s.getEndTime() : null)
                .hourlyWage(s != null ? s.getHourlyWage() : BigDecimal.ZERO)
                .allowance(s != null ? s.getAllowance() : BigDecimal.ZERO)
                .totalEstimatedPay(calculateSingleWorkerShiftPay(s))
                .matchScore(p.getMatchScore())
                .scoreBreakdown(p.getScoreBreakdown())
                .status(p.getStatus())
                .expiresAt(p.getExpiresAt())
                .source(p.getSource())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private ShiftAssignmentResponseDto mapToAssignmentDto(ShiftAssignment a) {
        Shift s = a.getShift();
        Event e = s != null ? s.getEvent() : null;

        Double venueLat = s != null && s.getLocationLat() != null ? s.getLocationLat()
                : (e != null && e.getVenueLat() != null ? e.getVenueLat() : DEFAULT_CENTER_LAT);
        Double venueLng = s != null && s.getLocationLng() != null ? s.getLocationLng()
                : (e != null && e.getVenueLng() != null ? e.getVenueLng() : DEFAULT_CENTER_LNG);

        Optional<AttendanceLog> attendanceOpt = attendanceLogRepository.findByShiftAssignment_AssignmentId(a.getAssignmentId());

        return ShiftAssignmentResponseDto.builder()
                .assignmentId(a.getAssignmentId())
                .shiftId(s != null ? s.getShiftId() : null)
                .eventId(e != null ? e.getEventId() : null)
                .eventName(e != null ? e.getName() : "Sự kiện")
                .venueAddress(s != null && s.getLocationAddress() != null ? s.getLocationAddress() : (e != null ? e.getVenueAddress() : ""))
                .jobType(s != null ? s.getJobType() : "")
                .startTime(s != null ? s.getStartTime() : null)
                .endTime(s != null ? s.getEndTime() : null)
                .hourlyWage(s != null ? s.getHourlyWage() : BigDecimal.ZERO)
                .totalPay(calculateSingleWorkerShiftPay(s))
                .status(a.getStatus())
                .source(a.getSource())
                .eoConfirmed(a.getEoConfirmed())
                .venueLat(venueLat)
                .venueLng(venueLng)
                .attendanceStatus(attendanceOpt.map(att -> att.getAttendanceStatus().name()).orElse("NOT_CHECKED_IN"))
                .checkinTime(attendanceOpt.map(AttendanceLog::getCheckinTime).orElse(null))
                .checkoutTime(attendanceOpt.map(AttendanceLog::getCheckoutTime).orElse(null))
                .hoursWorked(attendanceOpt.map(AttendanceLog::getHoursWorked).orElse(BigDecimal.ZERO))
                .createdAt(a.getCreatedAt())
                .build();
    }
}
