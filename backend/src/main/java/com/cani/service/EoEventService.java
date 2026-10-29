package com.cani.service;

import com.cani.dto.event.*;
import com.cani.dto.worker.ShiftChecklistItemDto;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.EventStatus;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EoEventService {

    private static final String QR_CHECKIN_PREFIX = "CANI_CHECKIN:";
    private static final String QR_CHECKOUT_PREFIX = "CANI_CHECKOUT:";
    private static final String CANCEL_TYPE_EVENT = "EO_CANCEL_EVENT";
    private static final String CANCEL_TYPE_SHIFT = "EO_CANCEL_SHIFT";
    private static final String PAYMENT_NOT_DECLARED = "NOT_DECLARED";
    private static final int MAX_RELIABILITY_SCORE = 100;
    private static final int MIN_RELIABILITY_SCORE = 0;
    private static final double MIN_BILLABLE_HOURS = 0.5;
    private static final long DEFAULT_SHIFT_MINUTES = 240L;
    private static final BigDecimal DEFAULT_HOURLY_WAGE = BigDecimal.valueOf(50000);
    private static final BigDecimal DEFAULT_RATING = BigDecimal.valueOf(5.0);

    private final EventOrganizerRepository eventOrganizerRepository;
    private final EventRepository eventRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final ReviewRepository reviewRepository;
    private final PaymentRepository paymentRepository;
    private final WorkerRepository workerRepository;
    private final MatchingDispatchService matchingDispatchService;
    private final ObjectMapper objectMapper;

    /**
     * EO tạo Sự kiện mới (Trạng thái DRAFT)
     */
    @Transactional
    public EventResponseDto createEvent(UUID userId, CreateEventRequestDto dto) {
        EventOrganizer eo = getEoOrThrow(userId);

        Event event = Event.builder()
                .eventOrganizer(eo)
                .name(dto.getName())
                .description(dto.getDescription())
                .venueAddress(dto.getVenueAddress())
                .venueLat(dto.getVenueLat())
                .venueLng(dto.getVenueLng())
                .eventDate(dto.getEventDate())
                .mode(dto.getMode())
                .status(EventStatus.DRAFT)
                .readinessScore(0)
                .totalShifts(0)
                .totalRequiredWorkers(0)
                .totalConfirmedWorkers(0)
                .totalEstimatedCost(BigDecimal.ZERO)
                .totalPaid(BigDecimal.ZERO)
                .build();

        event = eventRepository.save(event);
        log.info("EO {} đã tạo sự kiện: {} (ID: {})", userId, event.getName(), event.getEventId());

        return mapToEventResponseDto(event, List.of());
    }

    /**
     * UC-11: Chỉnh sửa thông tin Sự kiện (Edit Event)
     */
    @Transactional
    public EventResponseDto updateEvent(UUID userId, UUID eventId, UpdateEventRequestDto dto) {
        EventOrganizer eo = getEoOrThrow(userId);
        Event event = getOwnedEventOrThrow(eventId, eo.getEoId());

        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new BadRequestException("Không thể chỉnh sửa sự kiện đã hoàn thành hoặc đã bị hủy.");
        }

        event.setName(dto.getName());
        event.setDescription(dto.getDescription());
        event.setVenueAddress(dto.getVenueAddress());
        if (dto.getVenueLat() != null) event.setVenueLat(dto.getVenueLat());
        if (dto.getVenueLng() != null) event.setVenueLng(dto.getVenueLng());
        if (dto.getEventDate() != null) event.setEventDate(dto.getEventDate());
        if (dto.getMode() != null) event.setMode(dto.getMode());

        event = eventRepository.save(event);
        log.info("EO {} đã cập nhật sự kiện: {}", userId, event.getEventId());

        List<Shift> shifts = shiftRepository.findByEvent_EventId(eventId);
        return mapToEventResponseDto(event, shifts);
    }

    /**
     * UC-12: Hủy sự kiện (Cancel Event)
     */
    @Transactional
    public EventResponseDto cancelEvent(UUID userId, UUID eventId, String reason) {
        EventOrganizer eo = getEoOrThrow(userId);
        Event event = getOwnedEventOrThrow(eventId, eo.getEoId());

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BadRequestException("Sự kiện này đã bị hủy trước đó.");
        }

        event.setStatus(EventStatus.CANCELLED);
        event = eventRepository.save(event);

        List<Shift> shifts = shiftRepository.findByEvent_EventId(eventId);
        UUID cancelledBy = resolveEoUserId(eo, userId);
        String finalReason = (reason != null && !reason.isBlank()) ? reason : "Ban tổ chức hủy sự kiện";

        for (Shift s : shifts) {
            s.setStatus(ShiftStatus.CANCELLED);
            shiftRepository.save(s);
            cancelActiveAssignmentsForShift(s.getShiftId(), cancelledBy, finalReason, CANCEL_TYPE_EVENT);
        }

        log.info("EO {} đã hủy sự kiện {} kèm {} ca làm việc. Lý do: {}", userId, eventId, shifts.size(), finalReason);
        return mapToEventResponseDto(event, shifts);
    }

    /**
     * Thêm Ca làm việc (Shift) vào Sự kiện
     */
    @Transactional
    public ShiftResponseDto addShiftToEvent(UUID userId, UUID eventId, CreateShiftRequestDto dto) {
        EventOrganizer eo = getEoOrThrow(userId);
        Event event = getOwnedEventOrThrow(eventId, eo.getEoId());
        validateShiftTimeRange(dto.getStartTime(), dto.getEndTime());

        int workers = dto.getRequiredWorkers();
        BigDecimal allowance = dto.getAllowance() != null ? dto.getAllowance() : BigDecimal.ZERO;
        BigDecimal shiftCost = calculateShiftTotalCost(dto.getStartTime(), dto.getEndTime(), dto.getHourlyWage(), workers, allowance);

        Shift shift = Shift.builder()
                .event(event)
                .jobType(dto.getJobType())
                .description(dto.getDescription())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .requiredWorkers(workers)
                .confirmedWorkers(0)
                .remainingWorkers(workers)
                .hourlyWage(dto.getHourlyWage())
                .allowance(allowance)
                .totalCost(shiftCost)
                .status(event.getStatus() == EventStatus.ACTIVE ? ShiftStatus.ACTIVE : ShiftStatus.DRAFT)
                .riskScore(0)
                .locationAddress(dto.getLocationAddress() != null ? dto.getLocationAddress() : event.getVenueAddress())
                .locationLat(dto.getLocationLat() != null ? dto.getLocationLat() : event.getVenueLat())
                .locationLng(dto.getLocationLng() != null ? dto.getLocationLng() : event.getVenueLng())
                .requiredSkills(dto.getRequiredSkills())
                .minReliabilityScore(dto.getMinReliabilityScore())
                .maxDistanceKm(dto.getMaxDistanceKm())
                .minExperienceLevel(dto.getMinExperienceLevel())
                .dressCode(dto.getDressCode())
                .build();

        shift = shiftRepository.save(shift);

        event.setTotalShifts((event.getTotalShifts() != null ? event.getTotalShifts() : 0) + 1);
        event.setTotalRequiredWorkers((event.getTotalRequiredWorkers() != null ? event.getTotalRequiredWorkers() : 0) + workers);
        BigDecimal currentCost = event.getTotalEstimatedCost() != null ? event.getTotalEstimatedCost() : BigDecimal.ZERO;
        event.setTotalEstimatedCost(currentCost.add(shiftCost));
        eventRepository.save(event);

        log.info("Đã thêm ca làm việc '{}' (cần {} người) vào sự kiện {}", shift.getJobType(), workers, event.getName());
        return mapToShiftResponseDto(shift);
    }

    /**
     * UC-18: Chỉnh sửa Ca làm việc (Edit Shift)
     */
    @Transactional
    public ShiftResponseDto updateShift(UUID userId, UUID shiftId, UpdateShiftRequestDto dto) {
        EventOrganizer eo = getEoOrThrow(userId);
        Shift shift = getOwnedShiftOrThrow(shiftId, eo.getEoId());
        validateShiftTimeRange(dto.getStartTime(), dto.getEndTime());

        int workers = dto.getRequiredWorkers();
        BigDecimal allowance = dto.getAllowance() != null ? dto.getAllowance() : BigDecimal.ZERO;
        BigDecimal newShiftCost = calculateShiftTotalCost(dto.getStartTime(), dto.getEndTime(), dto.getHourlyWage(), workers, allowance);

        BigDecimal costDiff = newShiftCost.subtract(shift.getTotalCost() != null ? shift.getTotalCost() : BigDecimal.ZERO);
        int workerDiff = workers - (shift.getRequiredWorkers() != null ? shift.getRequiredWorkers() : 0);

        shift.setJobType(dto.getJobType());
        shift.setDescription(dto.getDescription());
        shift.setStartTime(dto.getStartTime());
        shift.setEndTime(dto.getEndTime());
        shift.setRequiredWorkers(workers);
        int confirmed = shift.getConfirmedWorkers() != null ? shift.getConfirmedWorkers() : 0;
        shift.setRemainingWorkers(Math.max(0, workers - confirmed));
        shift.setHourlyWage(dto.getHourlyWage());
        shift.setAllowance(allowance);
        shift.setTotalCost(newShiftCost);
        if (dto.getMinReliabilityScore() != null) shift.setMinReliabilityScore(dto.getMinReliabilityScore());
        if (dto.getMaxDistanceKm() != null) shift.setMaxDistanceKm(dto.getMaxDistanceKm());
        if (dto.getMinExperienceLevel() != null) shift.setMinExperienceLevel(dto.getMinExperienceLevel());
        if (dto.getDressCode() != null) shift.setDressCode(dto.getDressCode());
        if (dto.getLocationAddress() != null) shift.setLocationAddress(dto.getLocationAddress());
        if (dto.getLocationLat() != null) shift.setLocationLat(dto.getLocationLat());
        if (dto.getLocationLng() != null) shift.setLocationLng(dto.getLocationLng());

        shift = shiftRepository.save(shift);

        Event event = shift.getEvent();
        if (event != null) {
            event.setTotalRequiredWorkers((event.getTotalRequiredWorkers() != null ? event.getTotalRequiredWorkers() : 0) + workerDiff);
            BigDecimal currentCost = event.getTotalEstimatedCost() != null ? event.getTotalEstimatedCost() : BigDecimal.ZERO;
            event.setTotalEstimatedCost(currentCost.add(costDiff));
            eventRepository.save(event);
        }

        log.info("EO {} đã cập nhật ca làm việc {}", userId, shiftId);
        return mapToShiftResponseDto(shift);
    }

    /**
     * UC-19: Hủy Ca làm việc (Cancel Shift)
     */
    @Transactional
    public ShiftResponseDto cancelShift(UUID userId, UUID shiftId, String reason) {
        EventOrganizer eo = getEoOrThrow(userId);
        Shift shift = getOwnedShiftOrThrow(shiftId, eo.getEoId());

        shift.setStatus(ShiftStatus.CANCELLED);
        shift = shiftRepository.save(shift);

        String finalReason = (reason != null && !reason.isBlank()) ? reason : "Ban tổ chức hủy ca";
        cancelActiveAssignmentsForShift(shiftId, resolveEoUserId(eo, userId), finalReason, CANCEL_TYPE_SHIFT);

        log.info("EO {} đã hủy ca làm việc {}. Lý do: {}", userId, shiftId, finalReason);
        return mapToShiftResponseDto(shift);
    }

    /**
     * UC-52: Sinh mã QR Điểm danh của Ca làm việc (Attendance QR Code Generator)
     */
    @Transactional(readOnly = true)
    public ShiftQrCodeResponseDto generateShiftQr(UUID userId, UUID shiftId) {
        EventOrganizer eo = getEoOrThrow(userId);
        Shift shift = getOwnedShiftOrThrow(shiftId, eo.getEoId());
        Event event = shift.getEvent();

        return ShiftQrCodeResponseDto.builder()
                .shiftId(shift.getShiftId())
                .eventId(event != null ? event.getEventId() : null)
                .eventName(event != null ? event.getName() : "")
                .jobType(shift.getJobType())
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .venueAddress(shift.getLocationAddress() != null ? shift.getLocationAddress() : (event != null ? event.getVenueAddress() : ""))
                .checkinQrContent(QR_CHECKIN_PREFIX + shift.getShiftId())
                .checkoutQrContent(QR_CHECKOUT_PREFIX + shift.getShiftId())
                .build();
    }

    /**
     * UC-48: Bảng theo dõi điểm danh trực tiếp của Ca làm việc (Live Attendance Dashboard)
     */
    @Transactional(readOnly = true)
    public List<ShiftAttendanceDto> getShiftAttendanceList(UUID userId, UUID shiftId) {
        EventOrganizer eo = getEoOrThrow(userId);
        Shift shift = getOwnedShiftOrThrow(shiftId, eo.getEoId());

        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByShift_ShiftId(shiftId);
        BigDecimal agreedWage = calculateSingleWorkerAgreedWage(shift);
        String checklistSummary = computeChecklistSummary(shift.getChecklist());
        UUID eoUserId = resolveEoUserId(eo, userId);

        return assignments.stream()
                .map(sa -> mapToShiftAttendanceDto(sa, eoUserId, agreedWage, checklistSummary))
                .collect(Collectors.toList());
    }

    /**
     * UC-49: EO xác nhận hoàn thành ca & duyệt công cho Worker (Confirm Completion)
     */
    @Transactional
    public ShiftAttendanceDto verifyAttendance(UUID userId, UUID assignmentId) {
        EventOrganizer eo = getEoOrThrow(userId);
        ShiftAssignment sa = getOwnedAssignmentOrThrow(assignmentId, eo.getEoId());

        if (Boolean.TRUE.equals(sa.getEoConfirmed())) {
            throw new BadRequestException("Phân công việc này đã được duyệt hoàn thành trước đó.");
        }

        boolean wasAlreadyCompleted = (sa.getStatus() == AssignmentStatus.COMPLETED);
        sa.setEoConfirmed(true);
        sa.setEoConfirmedAt(Instant.now());
        sa.setStatus(AssignmentStatus.COMPLETED);
        sa = shiftAssignmentRepository.save(sa);

        // Chỉ cộng thêm số ca hoàn thành nếu trước đó Worker chưa check-out tự động
        Worker worker = sa.getWorker();
        if (worker != null && !wasAlreadyCompleted) {
            worker.setTotalShiftsCompleted((worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0) + 1);
            workerRepository.save(worker);
        }

        log.info("EO {} đã duyệt hoàn thành ca cho Assignment {}", userId, assignmentId);

        Shift shift = sa.getShift();
        BigDecimal agreedWage = calculateSingleWorkerAgreedWage(shift);
        String checklistSummary = computeChecklistSummary(shift != null ? shift.getChecklist() : null);
        return mapToShiftAttendanceDto(sa, resolveEoUserId(eo, userId), agreedWage, checklistSummary);
    }

    /**
     * UC-69: EO đánh giá Worker sau ca làm việc (Rate Worker 1-5★)
     */
    @Transactional
    public Review rateWorker(UUID userId, UUID assignmentId, CreateEoReviewRequestDto request) {
        EventOrganizer eo = getEoOrThrow(userId);
        ShiftAssignment sa = getOwnedAssignmentOrThrow(assignmentId, eo.getEoId());
        UUID eoUserId = resolveEoUserId(eo, userId);

        if (reviewRepository.findByAssignmentIdAndReviewerId(assignmentId, eoUserId).isPresent()) {
            throw new BadRequestException("Bạn đã gửi đánh giá cho Worker này trong ca làm việc rồi.");
        }

        Worker worker = sa.getWorker();
        if (worker == null || worker.getUser() == null) {
            throw new BadRequestException("Không tìm thấy thông tin Worker để đánh giá.");
        }

        Review review = Review.builder()
                .assignmentId(assignmentId)
                .reviewerId(eoUserId)
                .revieweeId(worker.getUser().getUserId())
                .rating(request.getRating())
                .criteriaWorkCompletion(request.getCriteriaWorkCompletion())
                .criteriaPunctuality(request.getCriteriaPunctuality())
                .criteriaProfessionalism(request.getCriteriaProfessionalism())
                .comment(request.getComment())
                .tags(request.getTags())
                .build();

        review = reviewRepository.save(review);
        updateWorkerRatingAndReliability(worker, request.getRating());

        log.info("EO {} đã đánh giá {} sao cho Worker {} (Assignment: {})", eoUserId, request.getRating(), worker.getWorkerId(), assignmentId);
        return review;
    }

    /**
     * Danh sách tất cả các sự kiện của EO
     */
    @Transactional(readOnly = true)
    public List<EventResponseDto> getMyEvents(UUID userId) {
        EventOrganizer eo = getEoOrThrow(userId);
        List<Event> events = eventRepository.findByEventOrganizer_EoIdOrderByCreatedAtDesc(eo.getEoId());
        return events.stream().map(e -> {
            List<Shift> shifts = shiftRepository.findByEvent_EventId(e.getEventId());
            return mapToEventResponseDto(e, shifts);
        }).collect(Collectors.toList());
    }

    /**
     * Xem chi tiết sự kiện
     */
    @Transactional(readOnly = true)
    public EventResponseDto getEventDetail(UUID userId, UUID eventId) {
        EventOrganizer eo = getEoOrThrow(userId);
        Event event = getOwnedEventOrThrow(eventId, eo.getEoId());
        List<Shift> shifts = shiftRepository.findByEvent_EventId(eventId);
        return mapToEventResponseDto(event, shifts);
    }

    /**
     * EO xuất bản Sự kiện và tự động kích hoạt Matching Engine cho tất cả các ca (Không cần ví/ký quỹ)
     */
    @Transactional
    public EventResponseDto publishEvent(UUID userId, UUID eventId) {
        EventOrganizer eo = getEoOrThrow(userId);
        Event event = getOwnedEventOrThrow(eventId, eo.getEoId());

        event.setStatus(EventStatus.ACTIVE);
        event = eventRepository.save(event);

        List<Shift> shifts = shiftRepository.findByEvent_EventId(eventId);
        for (Shift s : shifts) {
            s.setStatus(ShiftStatus.ACTIVE);
            shiftRepository.save(s);
            matchingDispatchService.dispatchForShift(s.getShiftId());
        }

        log.info("EO {} đã xuất bản sự kiện {} và kích hoạt Matching cho {} ca làm việc",
                eo.getEoId(), event.getName(), shifts.size());

        return mapToEventResponseDto(event, shifts);
    }

    /**
     * Kích hoạt điều phối tìm ứng viên (Dispatch) cho 1 ca cụ thể
     */
    @Transactional
    public ShiftResponseDto publishShift(UUID userId, UUID shiftId) {
        EventOrganizer eo = getEoOrThrow(userId);
        Shift shift = getOwnedShiftOrThrow(shiftId, eo.getEoId());

        shift.setStatus(ShiftStatus.ACTIVE);
        shift = shiftRepository.save(shift);

        matchingDispatchService.dispatchForShift(shiftId);
        log.info("EO {} kích hoạt điều phối ứng viên cho ca {}", eo.getEoId(), shiftId);

        return mapToShiftResponseDto(shift);
    }

    // =========================================================================
    // PRIVATE HELPER METHODS (CLEAN CODE & DRY)
    // =========================================================================

    private EventOrganizer getEoOrThrow(UUID userId) {
        return eventOrganizerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin nhà tổ chức sự kiện cho User ID: " + userId));
    }

    private Event getOwnedEventOrThrow(UUID eventId, UUID eoId) {
        return eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện ID: " + eventId + " thuộc nhà tổ chức này"));
    }

    private Shift getOwnedShiftOrThrow(UUID shiftId, UUID eoId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc ID: " + shiftId));
        if (shift.getEvent() == null || !shift.getEvent().getEventOrganizer().getEoId().equals(eoId)) {
            throw new BadRequestException("Bạn không sở hữu ca làm việc này.");
        }
        return shift;
    }

    private ShiftAssignment getOwnedAssignmentOrThrow(UUID assignmentId, UUID eoId) {
        ShiftAssignment sa = shiftAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân công việc ID: " + assignmentId));
        if (sa.getShift() == null || sa.getShift().getEvent() == null
                || !sa.getShift().getEvent().getEventOrganizer().getEoId().equals(eoId)) {
            throw new BadRequestException("Bạn không sở hữu sự kiện của ca làm việc này.");
        }
        return sa;
    }

    private UUID resolveEoUserId(EventOrganizer eo, UUID fallbackUserId) {
        return eo.getUser() != null ? eo.getUser().getUserId() : fallbackUserId;
    }

    private void validateShiftTimeRange(Instant startTime, Instant endTime) {
        if (endTime.isBefore(startTime)) {
            throw new BadRequestException("Thời gian kết thúc ca phải sau thời gian bắt đầu");
        }
    }

    private BigDecimal calculateShiftTotalCost(Instant startTime, Instant endTime, BigDecimal hourlyWage, int requiredWorkers, BigDecimal allowance) {
        long minutes = Duration.between(startTime, endTime).toMinutes();
        double hours = Math.max(MIN_BILLABLE_HOURS, minutes / 60.0);
        return hourlyWage.multiply(BigDecimal.valueOf(hours))
                .multiply(BigDecimal.valueOf(requiredWorkers))
                .add(allowance != null ? allowance : BigDecimal.ZERO);
    }

    private BigDecimal calculateSingleWorkerAgreedWage(Shift shift) {
        if (shift == null) return BigDecimal.ZERO;
        long minutes = (shift.getStartTime() != null && shift.getEndTime() != null)
                ? Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes() : DEFAULT_SHIFT_MINUTES;
        double hours = Math.max(MIN_BILLABLE_HOURS, minutes / 60.0);
        BigDecimal hourly = shift.getHourlyWage() != null ? shift.getHourlyWage() : DEFAULT_HOURLY_WAGE;
        BigDecimal allowance = shift.getAllowance() != null ? shift.getAllowance() : BigDecimal.ZERO;
        return hourly.multiply(BigDecimal.valueOf(hours)).add(allowance);
    }

    private String computeChecklistSummary(String checklistJson) {
        if (checklistJson == null || checklistJson.isBlank()) {
            return "";
        }
        try {
            List<ShiftChecklistItemDto> items = objectMapper.readValue(checklistJson, new TypeReference<List<ShiftChecklistItemDto>>() {});
            long completed = items.stream().filter(i -> Boolean.TRUE.equals(i.getIsCompleted())).count();
            return completed + "/" + items.size() + " việc";
        } catch (Exception ignored) {
            return "";
        }
    }

    private void cancelActiveAssignmentsForShift(UUID shiftId, UUID cancelledBy, String reason, String cancellationType) {
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByShift_ShiftId(shiftId);
        Instant now = Instant.now();
        for (ShiftAssignment sa : assignments) {
            if (sa.getStatus() != AssignmentStatus.COMPLETED && sa.getStatus() != AssignmentStatus.CANCELLED) {
                sa.setStatus(AssignmentStatus.CANCELLED);
                sa.setCancellationReason(reason);
                sa.setCancelledBy(cancelledBy);
                sa.setCancelledAt(now);
                sa.setCancellationType(cancellationType);
                shiftAssignmentRepository.save(sa);
            }
        }
    }

    private void updateWorkerRatingAndReliability(Worker worker, int newRating) {
        int count = (worker.getRatingCount() != null ? worker.getRatingCount() : 0) + 1;
        BigDecimal currentAvg = worker.getRatingAvg() != null ? worker.getRatingAvg() : BigDecimal.ZERO;
        BigDecimal newAvg = currentAvg.multiply(BigDecimal.valueOf(count - 1))
                .add(BigDecimal.valueOf(newRating))
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        worker.setRatingAvg(newAvg);
        worker.setRatingCount(count);

        int currentReliability = worker.getReliabilityScore() != null ? worker.getReliabilityScore() : MAX_RELIABILITY_SCORE;
        if (newRating >= 4 && currentReliability < MAX_RELIABILITY_SCORE) {
            worker.setReliabilityScore(Math.min(MAX_RELIABILITY_SCORE, currentReliability + 1));
        } else if (newRating <= 2 && currentReliability > MIN_RELIABILITY_SCORE) {
            worker.setReliabilityScore(Math.max(MIN_RELIABILITY_SCORE, currentReliability - 2));
        }
        workerRepository.save(worker);
    }

    private ShiftAttendanceDto mapToShiftAttendanceDto(ShiftAssignment sa, UUID eoUserId, BigDecimal agreedWage, String checklistSummary) {
        Worker worker = sa.getWorker();
        User u = worker != null ? worker.getUser() : null;

        Optional<AttendanceLog> attOpt = attendanceLogRepository.findByShiftAssignment_AssignmentId(sa.getAssignmentId());
        Optional<Payment> payOpt = paymentRepository.findByAssignmentId(sa.getAssignmentId());
        boolean reviewed = reviewRepository.findByAssignmentIdAndReviewerId(sa.getAssignmentId(), eoUserId).isPresent();

        String defaultName = "Worker #" + (worker != null ? worker.getWorkerId().toString().substring(0, 8) : "");

        return ShiftAttendanceDto.builder()
                .assignmentId(sa.getAssignmentId())
                .workerId(worker != null ? worker.getWorkerId() : null)
                .userId(u != null ? u.getUserId() : null)
                .workerName(u != null ? u.getFullName() : defaultName)
                .workerPhone(u != null ? u.getPhone() : "")
                .avatarUrl(u != null ? u.getAvatarUrl() : null)
                .workerReliabilityScore(worker != null ? worker.getReliabilityScore() : MAX_RELIABILITY_SCORE)
                .workerRatingAvg(worker != null ? worker.getRatingAvg() : DEFAULT_RATING)
                .assignmentStatus(sa.getStatus() != null ? sa.getStatus().name() : AssignmentStatus.PENDING.name())
                .checkinTime(attOpt.map(AttendanceLog::getCheckinTime).orElse(null))
                .checkoutTime(attOpt.map(AttendanceLog::getCheckoutTime).orElse(null))
                .isOnTime(attOpt.map(AttendanceLog::getIsOnTime).orElse(null))
                .hoursWorked(attOpt.map(AttendanceLog::getHoursWorked).orElse(null))
                .eoConfirmed(Boolean.TRUE.equals(sa.getEoConfirmed()))
                .eoConfirmedAt(sa.getEoConfirmedAt())
                .hasReviewedWorker(reviewed)
                .checklistProgress(checklistSummary)
                .paymentStatus(payOpt.map(p -> p.getStatus().name()).orElse(PAYMENT_NOT_DECLARED))
                .agreedWage(agreedWage)
                .build();
    }

    private EventResponseDto mapToEventResponseDto(Event e, List<Shift> shifts) {
        return EventResponseDto.builder()
                .eventId(e.getEventId())
                .name(e.getName())
                .description(e.getDescription())
                .venueAddress(e.getVenueAddress())
                .venueLat(e.getVenueLat())
                .venueLng(e.getVenueLng())
                .eventDate(e.getEventDate())
                .mode(e.getMode())
                .status(e.getStatus())
                .readinessScore(e.getReadinessScore())
                .totalShifts(e.getTotalShifts())
                .totalRequiredWorkers(e.getTotalRequiredWorkers())
                .totalConfirmedWorkers(e.getTotalConfirmedWorkers())
                .totalEstimatedCost(e.getTotalEstimatedCost())
                .totalPaid(e.getTotalPaid())
                .createdAt(e.getCreatedAt())
                .shifts(shifts.stream().map(this::mapToShiftResponseDto).collect(Collectors.toList()))
                .build();
    }

    private ShiftResponseDto mapToShiftResponseDto(Shift s) {
        return ShiftResponseDto.builder()
                .shiftId(s.getShiftId())
                .eventId(s.getEvent() != null ? s.getEvent().getEventId() : null)
                .jobType(s.getJobType())
                .description(s.getDescription())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .requiredWorkers(s.getRequiredWorkers())
                .confirmedWorkers(s.getConfirmedWorkers())
                .remainingWorkers(s.getRemainingWorkers())
                .hourlyWage(s.getHourlyWage())
                .allowance(s.getAllowance())
                .totalCost(s.getTotalCost())
                .status(s.getStatus())
                .riskScore(s.getRiskScore())
                .locationAddress(s.getLocationAddress())
                .locationLat(s.getLocationLat())
                .locationLng(s.getLocationLng())
                .minReliabilityScore(s.getMinReliabilityScore())
                .minExperienceLevel(s.getMinExperienceLevel())
                .dressCode(s.getDressCode())
                .build();
    }
}
