package com.cani.service;

import com.cani.dto.event.*;
import com.cani.dto.worker.ShiftChecklistItemDto;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.VerificationStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final ReviewRepository reviewRepository;
    private final PaymentRepository paymentRepository;
    private final WorkerRepository workerRepository;
    private final WorkerEkycVerificationRepository workerEkycVerificationRepository;
    private final MatchingDispatchService matchingDispatchService;
    private final ObjectMapper objectMapper;

    private static final String EVENT_UPLOAD_DIR = "uploads/events";
    private static final Set<String> ALLOWED_IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @Autowired(required = false)
    private SubscriptionService subscriptionService;

    /**
     * EO tạo Sự kiện mới (Trạng thái DRAFT)
     */
    @Transactional
    public EventResponseDto createEvent(UUID userId, CreateEventRequestDto dto) {
        EventOrganizer eo = getEoOrThrow(userId);

        if (eo.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new BadRequestException("Tài khoản của bạn chưa hoàn tất định danh eKYC. Vui lòng xác thực eKYC trước khi tạo sự kiện và tuyển dụng nhân sự trên CANi.");
        }

        Event event = Event.builder()
                .eventOrganizer(eo)
                .name(dto.getName())
                .description(dto.getDescription())
                .venueAddress(dto.getVenueAddress())
                .venueLat(dto.getVenueLat())
                .venueLng(dto.getVenueLng())
                .eventDate(dto.getEventDate())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .imageUrl(dto.getImageUrl() != null && !dto.getImageUrl().isBlank() ? dto.getImageUrl().trim() : null)
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
        if (dto.getStartTime() != null) event.setStartTime(dto.getStartTime());
        if (dto.getEndTime() != null) event.setEndTime(dto.getEndTime());
        if (dto.getImageUrl() != null) event.setImageUrl(dto.getImageUrl().trim());
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
        validateShiftWithinEvent(event, dto.getStartTime(), dto.getEndTime());

        int workers = dto.getRequiredWorkers();
        BigDecimal allowance = dto.getAllowance() != null ? dto.getAllowance() : BigDecimal.ZERO;
        if (allowance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Phụ cấp thêm không được là số âm!");
        }
        if (dto.getHourlyWage() == null || dto.getHourlyWage().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Thù lao theo giờ phải lớn hơn 0!");
        }
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
                .imageUrl(dto.getImageUrl())
                .checklist(dto.getChecklist())
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
        validateShiftWithinEvent(shift.getEvent(), dto.getStartTime(), dto.getEndTime());

        int workers = dto.getRequiredWorkers();
        BigDecimal allowance = dto.getAllowance() != null ? dto.getAllowance() : BigDecimal.ZERO;
        if (allowance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Phụ cấp thêm không được là số âm!");
        }
        if (dto.getHourlyWage() == null || dto.getHourlyWage().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Thù lao theo giờ phải lớn hơn 0!");
        }
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
        if (dto.getChecklist() != null) shift.setChecklist(dto.getChecklist());

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
        UUID eoUserId = resolveEoUserId(eo, userId);

        return assignments.stream()
                .map(sa -> {
                    String checklistSummary = computeChecklistSummary(shift.getChecklist(), sa.getChecklistProgress());
                    return mapToShiftAttendanceDto(sa, eoUserId, agreedWage, checklistSummary);
                })
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
        String checklistSummary = computeChecklistSummary(shift != null ? shift.getChecklist() : null, sa.getChecklistProgress());
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

        Optional<AttendanceLog> attOpt = attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId);
        boolean hasCheckedOut = (attOpt.isPresent() && attOpt.get().getCheckoutTime() != null)
                || sa.getStatus() == AssignmentStatus.COMPLETED;
        if (!hasCheckedOut) {
            throw new BadRequestException("Chỉ có thể đánh giá nhân sự sau khi nhân sự đã hoàn thành Check-out ca làm việc.");
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
        List<Event> events = eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eo.getEoId());
        return events.stream().map(e -> {
            List<Shift> shifts = shiftRepository.findByEvent_EventId(e.getEventId());
            return mapToEventResponseDto(e, shifts);
        }).collect(Collectors.toList());
    }

    /**
     * Xóa mềm Sự kiện khỏi danh sách của EO (Soft Delete)
     */
    @Transactional
    public void deleteEvent(UUID userId, UUID eventId) {
        EventOrganizer eo = getEoOrThrow(userId);
        Event event = getOwnedEventOrThrow(eventId, eo.getEoId());
        event.setDeletedAt(Instant.now());
        eventRepository.save(event);
        log.info("EO {} đã xóa sự kiện {}", userId, eventId);
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

        // BR-08: Khấu trừ hạn mức Quota từ Gói cước dịch vụ (nếu áp dụng gói)
        if (subscriptionService != null) {
            try {
                subscriptionService.validateAndDeductEventQuota(eo);
            } catch (BadRequestException e) {
                // Quota exceeded hoặc chưa mua gói: Chặn không cho bypass xuất bản sự kiện
                throw e;
            } catch (Exception e) {
                // Chế độ chịu lỗi (Fault-tolerant): Nếu service gói cước gặp sự cố hệ thống, cho phép tiếp tục
                log.warn("Cảnh báo sự cố SubscriptionService khi trừ quota sự kiện: {}", e.getMessage());
            }
        }

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
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin User: " + userId));
                    if (user.getRole() != UserRole.EVENT_ORGANIZER) {
                        throw new BadRequestException("Tài khoản không phải là Nhà tổ chức sự kiện (Chỉ tài khoản Ban tổ chức mới có quyền tạo và quản lý sự kiện)");
                    }
                    return eventOrganizerRepository.save(EventOrganizer.builder().user(user).build());
                });
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
        validateShiftWithinEvent(null, startTime, endTime);
    }

    private void validateShiftWithinEvent(Event event, Instant startTime, Instant endTime) {
        if (endTime.isBefore(startTime)) {
            throw new BadRequestException("Thời gian kết thúc ca làm việc phải sau thời gian bắt đầu ca.");
        }
        if (event != null) {
            if (event.getStartTime() != null && startTime.isBefore(event.getStartTime())) {
                throw new BadRequestException("Thời gian bắt đầu ca làm việc không được sớm hơn thời gian bắt đầu của sự kiện.");
            }
            if (event.getEndTime() != null && endTime.isAfter(event.getEndTime())) {
                throw new BadRequestException("Thời gian kết thúc ca làm việc không được muộn hơn thời gian kết thúc của sự kiện.");
            }
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

    private String computeChecklistSummary(String templateJson, String progressJson) {
        if (templateJson == null || templateJson.isBlank()) {
            return "";
        }
        List<ShiftChecklistItemDto> template = parseChecklistTemplate(templateJson);
        if (template.isEmpty()) {
            return "";
        }

        int total = template.size();
        if (progressJson == null || progressJson.isBlank()) {
            return "0/" + total + " việc";
        }

        try {
            JsonNode root = objectMapper.readTree(progressJson);
            long completed = 0;
            if (root.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    JsonNode val = field.getValue();
                    if (val.isObject() && val.has("isCompleted") && val.get("isCompleted").asBoolean()) {
                        completed++;
                    }
                }
            } else if (root.isArray()) {
                for (JsonNode n : root) {
                    if (n.has("isCompleted") && n.get("isCompleted").asBoolean()) {
                        completed++;
                    }
                }
            }
            return completed + "/" + total + " việc";
        } catch (Exception e) {
            return "0/" + total + " việc";
        }
    }

    private List<ShiftChecklistItemDto> parseChecklistTemplate(String checklistJson) {
        if (checklistJson == null || checklistJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(checklistJson);
            if (!root.isArray()) {
                return Collections.emptyList();
            }
            List<ShiftChecklistItemDto> result = new ArrayList<>();
            int index = 1;
            for (JsonNode node : root) {
                if (node.isTextual()) {
                    result.add(ShiftChecklistItemDto.builder()
                            .id("chk_" + index)
                            .title(node.asText())
                            .isCompleted(false)
                            .build());
                    index++;
                } else if (node.isObject()) {
                    String id = node.hasNonNull("id") ? node.get("id").asText() : ("chk_" + index);
                    String title = node.hasNonNull("title") ? node.get("title").asText() : "";
                    result.add(ShiftChecklistItemDto.builder()
                            .id(id)
                            .title(title)
                            .isCompleted(false)
                            .build());
                    index++;
                }
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
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

        Integer workerAge = null;
        String workerDob = null;
        if (worker != null) {
            Optional<WorkerEkycVerification> ekycOpt = workerEkycVerificationRepository.findTopByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId());
            if (ekycOpt.isPresent() && ekycOpt.get().getDob() != null && !ekycOpt.get().getDob().isBlank()) {
                workerDob = ekycOpt.get().getDob().trim();
                try {
                    LocalDate birthDate = null;
                    if (workerDob.contains("/")) {
                        String[] parts = workerDob.split("/");
                        if (parts.length == 3) {
                            birthDate = LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                        }
                    } else if (workerDob.contains("-")) {
                        birthDate = LocalDate.parse(workerDob);
                    }
                    if (birthDate != null) {
                        workerAge = Period.between(birthDate, LocalDate.now()).getYears();
                    }
                } catch (Exception ignored) {}
            }
            if (workerAge == null && worker.getYearsExperience() != null && worker.getYearsExperience() > 0) {
                workerAge = 18 + worker.getYearsExperience();
            } else if (workerAge == null) {
                workerAge = 22;
            }
        }

        return ShiftAttendanceDto.builder()
                .assignmentId(sa.getAssignmentId())
                .workerId(worker != null ? worker.getWorkerId() : null)
                .userId(u != null ? u.getUserId() : null)
                .workerName(u != null ? u.getFullName() : defaultName)
                .workerPhone(u != null ? u.getPhone() : "")
                .avatarUrl(u != null ? u.getAvatarUrl() : null)
                .workerReliabilityScore(worker != null ? worker.getReliabilityScore() : MAX_RELIABILITY_SCORE)
                .workerRatingAvg(worker != null ? worker.getRatingAvg() : DEFAULT_RATING)
                .workerAge(workerAge)
                .workerDob(workerDob)
                .assignmentStatus(sa.getStatus() != null ? sa.getStatus().name() : AssignmentStatus.PENDING.name())
                .checkinTime(attOpt.map(AttendanceLog::getCheckinTime).orElse(null))
                .checkoutTime(attOpt.map(AttendanceLog::getCheckoutTime).orElse(null))
                .isOnTime(attOpt.map(AttendanceLog::getIsOnTime).orElse(null))
                .hoursWorked(attOpt.map(AttendanceLog::getHoursWorked).orElse(null))
                .eoConfirmed(Boolean.TRUE.equals(sa.getEoConfirmed()))
                .eoConfirmedAt(sa.getEoConfirmedAt())
                .hasReviewedWorker(reviewed)
                .checklistProgress(checklistSummary)
                .proofImageUrl(attOpt.map(AttendanceLog::getProofImageUrl).orElse(null))
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
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .imageUrl(e.getImageUrl())
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

    @Transactional(readOnly = true)
    public List<ShiftAttendanceDto> getEventWorkersList(UUID userId, UUID eventId) {
        EventOrganizer eo = getEoOrThrow(userId);
        Event event = getOwnedEventOrThrow(eventId, eo.getEoId());
        List<Shift> shifts = shiftRepository.findByEvent_EventIdAndStatusNot(eventId, ShiftStatus.CANCELLED);
        List<ShiftAttendanceDto> allWorkers = new ArrayList<>();
        UUID eoUserId = resolveEoUserId(eo, userId);

        for (Shift s : shifts) {
            List<ShiftAssignment> assignments = shiftAssignmentRepository.findByShift_ShiftId(s.getShiftId());
            BigDecimal agreedWage = calculateSingleWorkerAgreedWage(s);
            for (ShiftAssignment sa : assignments) {
                String checklistSummary = computeChecklistSummary(s.getChecklist(), sa.getChecklistProgress());
                ShiftAttendanceDto dto = mapToShiftAttendanceDto(sa, eoUserId, agreedWage, checklistSummary);
                dto.setShiftId(s.getShiftId());
                dto.setJobType(s.getJobType());
                dto.setShiftName(s.getJobType());
                allWorkers.add(dto);
            }
        }
        return allWorkers;
    }

    public String uploadEventImage(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp ảnh tải lên không được để trống");
        }
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }
        if (!ALLOWED_IMAGE_EXTS.contains(ext)) {
            throw new BadRequestException("Chỉ chấp nhận các định dạng ảnh: " + String.join(", ", ALLOWED_IMAGE_EXTS));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BadRequestException("Dung lượng tệp ảnh không được vượt quá 5MB");
        }
        try {
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(EVENT_UPLOAD_DIR);
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }
            String savedFilename = "event_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
            java.nio.file.Path targetPath = uploadPath.resolve(savedFilename);
            java.nio.file.Files.copy(file.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return "/api/v1/eo/events/images/" + savedFilename;
        } catch (java.io.IOException e) {
            log.error("Lỗi khi lưu tệp ảnh sự kiện: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể lưu tệp ảnh: " + e.getMessage());
        }
    }

    public org.springframework.core.io.Resource loadEventImage(String filename) {
        try {
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                throw new BadRequestException("Tên tệp không hợp lệ");
            }
            java.nio.file.Path filePath = java.nio.file.Paths.get(EVENT_UPLOAD_DIR).resolve(filename).normalize();
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Không tìm thấy tệp ảnh: " + filename);
            }
        } catch (java.net.MalformedURLException e) {
            throw new ResourceNotFoundException("Đường dẫn tệp không hợp lệ: " + filename);
        }
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
                .imageUrl(s.getImageUrl())
                .checklist(s.getChecklist())
                .build();
    }
}
