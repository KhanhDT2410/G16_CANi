package com.cani.service;

import com.cani.dto.admin.noshow.NoShowResponseDto;
import com.cani.dto.eo.EoEventPageResponseDto;
import com.cani.dto.event.*;
import com.cani.dto.worker.ShiftChecklistItemDto;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.DisputeStatus;
import com.cani.entity.enums.EventMode;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.NoShowDetectedBy;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.SkillRequirementType;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.VerificationStatus;
import com.cani.entity.enums.VerificationTier;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import com.cani.service.ekyc.EkycConsentAndAgeHelper;
import com.cani.util.VietQrUtil;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DisputeService disputeService;

    @Autowired(required = false)
    private DisputeRepository disputeRepository;

    private static final String EVENT_UPLOAD_DIR = "uploads/events";
    private static final Set<String> ALLOWED_IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @Autowired(required = false)
    private SubscriptionService subscriptionService;

    @Autowired(required = false)
    private SkillProgressionService skillProgressionService;

    @Autowired(required = false)
    private NoShowService noShowService;

    @Autowired(required = false)
    private NotificationService notificationService;

    @org.springframework.context.annotation.Lazy
    @Autowired(required = false)
    private WorkerJobService workerJobService;

    @Autowired(required = false)
    private SkillRepository skillRepository;

    @Autowired(required = false)
    private ShiftSkillRequirementRepository shiftSkillRequirementRepository;

    @Autowired(required = false)
    private DispatchProposalRepository dispatchProposalRepository;

    @Autowired(required = false)
    private ShiftStaffingPolicy shiftStaffingPolicy;

    @Autowired(required = false)
    private SystemConfigRepository systemConfigRepository;

    @Autowired(required = false)
    private EoEventQueryService eoEventQueryService;

    private ShiftStaffingPolicy getStaffingPolicy() {
        if (shiftStaffingPolicy != null) {
            return shiftStaffingPolicy;
        }
        return ShiftStaffingPolicy.withFallbackDependencies(
                shiftAssignmentRepository,
                dispatchProposalRepository
        );
    }

    private EoEventQueryService getEventQueryService() {
        if (eoEventQueryService != null) {
            return eoEventQueryService;
        }
        return EoEventQueryService.withFallbackDependencies(
                eventOrganizerRepository,
                userRepository,
                eventRepository,
                shiftRepository,
                shiftAssignmentRepository,
                paymentRepository,
                dispatchProposalRepository,
                shiftSkillRequirementRepository,
                skillRepository,
                getStaffingPolicy()
        );
    }

    /**
     * EO tạo Sự kiện mới (Trạng thái DRAFT)
     */
    @Transactional
    public EventResponseDto createEvent(UUID userId, CreateEventRequestDto dto) {
        EventOrganizer eo = getEoOrThrow(userId);

        if (eo.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new BadRequestException("Tài khoản của bạn chưa hoàn tất định danh eKYC. Vui lòng xác thực eKYC trước khi tạo sự kiện và tuyển dụng nhân sự trên CANi.");
        }

        validateEventDetails(dto.getName(), dto.getDescription(), dto.getVenueAddress(), dto.getStartTime(), dto.getEndTime());
        validateFutureStartTime(dto.getStartTime(), "Thời gian bắt đầu sự kiện không được nằm trong quá khứ.");

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

        validateEventDetails(dto.getName(), dto.getDescription(), dto.getVenueAddress(), dto.getStartTime(), dto.getEndTime());
        if (event.getStatus() == EventStatus.DRAFT && dto.getStartTime() != null) {
            validateFutureStartTime(dto.getStartTime(), "Thời gian bắt đầu sự kiện không được nằm trong quá khứ.");
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
        if (event.getStatus() == EventStatus.COMPLETED) {
            throw new BadRequestException("Không thể hủy sự kiện đã hoàn thành.");
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new BadRequestException("Vui lòng nhập lý do hủy sự kiện cụ thể (tối thiểu 10 ký tự).");
        }

        EventStatus prevStatus = event.getStatus();
        boolean isMatching = event.getMode() == EventMode.MATCHING;

        List<Shift> shifts = shiftRepository.findByEvent_EventId(eventId);
        boolean notStartedYet = true;
        if (event.getStartTime() != null) {
            notStartedYet = event.getStartTime().isAfter(Instant.now());
        } else if (!shifts.isEmpty()) {
            notStartedYet = shifts.stream()
                    .allMatch(s -> s.getStartTime() == null || s.getStartTime().isAfter(Instant.now()));
        }

        event.setStatus(EventStatus.CANCELLED);
        event = eventRepository.save(event);

        // Trả lại quota khi hủy mà event chưa bắt đầu (chỉ với event đã tiêu quota: MATCHING & ACTIVE)
        if (isMatching && notStartedYet && prevStatus == EventStatus.ACTIVE && subscriptionService != null) {
            try {
                subscriptionService.refundEventQuota(eo);
                log.info("Hoàn trả 1 quota sự kiện cho EO {} do hủy sự kiện {} trước giờ bắt đầu", eo.getEoId(), eventId);
            } catch (Exception ex) {
                log.warn("Không thể hoàn trả quota khi hủy sự kiện: {}", ex.getMessage());
            }
        }

        UUID cancelledBy = resolveEoUserId(eo, userId);
        String finalReason = reason.trim();

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
        validateFutureStartTime(dto.getStartTime(), "Thời gian bắt đầu ca làm việc không được nằm trong quá khứ.");
        validateShiftWithinEvent(event, dto.getStartTime(), dto.getEndTime());
        validateShiftArrivalTime(dto.getStartTime(), dto.getArrivalTime());
        validateShiftJobType(dto.getJobType());

        int workers = dto.getRequiredWorkers();
        BigDecimal allowance = dto.getAllowance() != null ? dto.getAllowance() : BigDecimal.ZERO;
        if (allowance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Phụ cấp thêm không được là số âm!");
        }
        validateShiftHourlyWage(event, dto.getHourlyWage());
        BigDecimal shiftCost = calculateShiftTotalCost(dto.getStartTime(), dto.getEndTime(), dto.getHourlyWage(), workers, allowance);

        PreparedShiftSkillConfig skillConfig = prepareShiftSkillConfig(
                dto.getPerformedSkillId(),
                dto.getRequiredSkills(),
                dto.getSkillRequirements(),
                dto.getCustomSkillNotes()
        );

        Shift shift = Shift.builder()
                .event(event)
                .jobType(dto.getJobType())
                .description(dto.getDescription())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .arrivalTime(dto.getArrivalTime() != null ? dto.getArrivalTime() : dto.getStartTime())
                .requiredWorkers(workers)
                .confirmedWorkers(0)
                .remainingWorkers(workers)
                .hourlyWage(dto.getHourlyWage())
                .allowance(allowance)
                .totalCost(shiftCost)
                .status(event.getStatus() == EventStatus.ACTIVE ? ShiftStatus.ACTIVE : ShiftStatus.DRAFT)
                .riskScore(0) // ponytail: server luôn trả 0; web tự tính risk client-side (useShifts.js)
                .locationAddress(dto.getLocationAddress() != null ? dto.getLocationAddress() : event.getVenueAddress())
                .locationLat(dto.getLocationLat() != null ? dto.getLocationLat() : event.getVenueLat())
                .locationLng(dto.getLocationLng() != null ? dto.getLocationLng() : event.getVenueLng())
                .requiredSkills(skillConfig.requiredSkillIdsArray())
                .performedSkillId(skillConfig.performedSkillId())
                .customSkillNotes(skillConfig.customSkillNotes())
                .minReliabilityScore(dto.getMinReliabilityScore())
                .maxDistanceKm(dto.getMaxDistanceKm())
                .minExperienceLevel(dto.getMinExperienceLevel())
                .dressCode(dto.getDressCode())
                .imageUrl(dto.getImageUrl())
                .checklist(dto.getChecklist())
                .build();

        shift = shiftRepository.save(shift);
        persistShiftSkillRequirements(shift, skillConfig, false);

        event.setTotalShifts((event.getTotalShifts() != null ? event.getTotalShifts() : 0) + 1);
        event.setTotalRequiredWorkers((event.getTotalRequiredWorkers() != null ? event.getTotalRequiredWorkers() : 0) + workers);
        BigDecimal currentCost = event.getTotalEstimatedCost() != null ? event.getTotalEstimatedCost() : BigDecimal.ZERO;
        event.setTotalEstimatedCost(currentCost.add(shiftCost));
        eventRepository.save(event);

        if (event.getStatus() == EventStatus.ACTIVE && !isInviteMode(event)) {
            try {
                int n = matchingDispatchService.dispatchForShift(shift.getShiftId());
                log.info("Đã thêm ca làm việc '{}' (cần {} người) vào sự kiện {} — dispatch {} đề xuất",
                        shift.getJobType(), workers, event.getName(), n);
            } catch (Exception e) {
                log.warn("Thêm ca '{}' thất bại dispatch tìm ứng viên: {}", shift.getJobType(), e.getMessage());
            }
        } else if (isInviteMode(event)) {
            log.info("Đã thêm ca làm việc '{}' (cần {} người) vào sự kiện INVITE {} — bỏ qua auto-matching",
                    shift.getJobType(), workers, event.getName());
        } else {
            log.info("Đã thêm ca làm việc '{}' (cần {} người) vào sự kiện {}", shift.getJobType(), workers, event.getName());
        }
        return mapToShiftResponseDto(shift);
    }

    /**
     * UC-20 (WBS 74.0): EO tạo hàng loạt ca làm việc theo mẫu khung giờ (Bulk Create Shifts)
     */
    @Transactional
    public List<ShiftResponseDto> addBulkShifts(UUID userId, UUID eventId, CreateBulkShiftsRequestDto dto) {
        EventOrganizer eo = getEoOrThrow(userId);
        Event event = getOwnedEventOrThrow(eventId, eo.getEoId());

        int workers = dto.getRequiredWorkers();
        BigDecimal allowance = dto.getAllowance() != null ? dto.getAllowance() : BigDecimal.ZERO;
        if (allowance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Phụ cấp thêm không được là số âm!");
        }
        validateShiftHourlyWage(event, dto.getHourlyWage());

        List<ShiftResponseDto> results = new ArrayList<>();
        validateShiftJobType(dto.getJobType());
        PreparedShiftSkillConfig skillConfig = prepareShiftSkillConfig(
                dto.getPerformedSkillId(),
                dto.getRequiredSkills(),
                dto.getSkillRequirements(),
                dto.getCustomSkillNotes()
        );
        for (CreateBulkShiftsRequestDto.TimeSlotItem slot : dto.getTimeSlots()) {
            validateFutureStartTime(slot.getStartTime(), "Thời gian bắt đầu ca làm việc không được nằm trong quá khứ.");
            validateShiftWithinEvent(event, slot.getStartTime(), slot.getEndTime());
            BigDecimal shiftCost = calculateShiftTotalCost(slot.getStartTime(), slot.getEndTime(), dto.getHourlyWage(), workers, allowance);

            Shift shift = Shift.builder()
                    .event(event)
                    .jobType(dto.getJobType())
                    .description(dto.getDescription())
                    .startTime(slot.getStartTime())
                    .endTime(slot.getEndTime())
                    .arrivalTime(slot.getStartTime())
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
                    .requiredSkills(skillConfig.requiredSkillIdsArray())
                    .performedSkillId(skillConfig.performedSkillId())
                    .customSkillNotes(skillConfig.customSkillNotes())
                    .minReliabilityScore(dto.getMinReliabilityScore())
                    .maxDistanceKm(dto.getMaxDistanceKm())
                    .minExperienceLevel(dto.getMinExperienceLevel())
                    .checklist(dto.getChecklist())
                    .build();

            shift = shiftRepository.save(shift);
            persistShiftSkillRequirements(shift, skillConfig, false);

            event.setTotalShifts((event.getTotalShifts() != null ? event.getTotalShifts() : 0) + 1);
            event.setTotalRequiredWorkers((event.getTotalRequiredWorkers() != null ? event.getTotalRequiredWorkers() : 0) + workers);
            BigDecimal currentCost = event.getTotalEstimatedCost() != null ? event.getTotalEstimatedCost() : BigDecimal.ZERO;
            event.setTotalEstimatedCost(currentCost.add(shiftCost));

            if (event.getStatus() == EventStatus.ACTIVE && !isInviteMode(event)) {
                try {
                    matchingDispatchService.dispatchForShift(shift.getShiftId());
                } catch (Exception e) {
                    log.warn("Thêm ca bulk '{}' thất bại dispatch tìm ứng viên: {}", shift.getJobType(), e.getMessage());
                }
            }
            results.add(mapToShiftResponseDto(shift));
        }

        eventRepository.save(event);
        log.info("EO {} đã tạo hàng loạt {} ca làm việc cho sự kiện {}", eo.getEoId(), results.size(), event.getName());
        return results;
    }

    /**
     * UC-18: Chỉnh sửa Ca làm việc (Edit Shift)
     */
    @Transactional
    public ShiftResponseDto updateShift(UUID userId, UUID shiftId, UpdateShiftRequestDto dto) {
        EventOrganizer eo = getEoOrThrow(userId);
        Shift shift = getOwnedShiftOrThrow(shiftId, eo.getEoId());
        validateShiftWithinEvent(shift.getEvent(), dto.getStartTime(), dto.getEndTime());
        validateShiftArrivalTime(dto.getStartTime(), dto.getArrivalTime());
        validateShiftJobType(dto.getJobType());

        int workers = dto.getRequiredWorkers();
        BigDecimal allowance = dto.getAllowance() != null ? dto.getAllowance() : BigDecimal.ZERO;
        if (allowance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Phụ cấp thêm không được là số âm!");
        }
        validateShiftHourlyWage(shift.getEvent(), dto.getHourlyWage());
        BigDecimal newShiftCost = calculateShiftTotalCost(dto.getStartTime(), dto.getEndTime(), dto.getHourlyWage(), workers, allowance);

        BigDecimal costDiff = newShiftCost.subtract(shift.getTotalCost() != null ? shift.getTotalCost() : BigDecimal.ZERO);
        int workerDiff = workers - (shift.getRequiredWorkers() != null ? shift.getRequiredWorkers() : 0);

        shift.setJobType(dto.getJobType());
        shift.setDescription(dto.getDescription());
        shift.setStartTime(dto.getStartTime());
        shift.setEndTime(dto.getEndTime());
        shift.setArrivalTime(dto.getArrivalTime() != null ? dto.getArrivalTime() : dto.getStartTime());
        PreparedShiftSkillConfig skillConfig = prepareShiftSkillConfig(
                dto.getPerformedSkillId() != null ? dto.getPerformedSkillId() : shift.getPerformedSkillId(),
                dto.getRequiredSkills(),
                dto.getSkillRequirements(),
                dto.getCustomSkillNotes() != null ? dto.getCustomSkillNotes() : shift.getCustomSkillNotes()
        );
        shift.setRequiredSkills(skillConfig.requiredSkillIdsArray());
        if (dto.getPerformedSkillId() != null) {
            shift.setPerformedSkillId(skillConfig.performedSkillId());
        }
        if (dto.getCustomSkillNotes() != null) {
            shift.setCustomSkillNotes(skillConfig.customSkillNotes());
        }
        shift.setRequiredWorkers(workers);
        int confirmed = getStaffingPolicy().resolveConfirmedWorkersCount(shift);
        shift.setRemainingWorkers(Math.max(0, workers - confirmed));
        if (confirmed >= workers) {
            if (shift.getStatus() == ShiftStatus.ACTIVE) {
                shift.setStatus(ShiftStatus.FULLY_STAFFED);
            }
            shift.setEmergency(false);
            shift.setUnderstaffedAlertSent(false);
        } else if (shift.getStatus() == ShiftStatus.FULLY_STAFFED) {
            shift.setStatus(ShiftStatus.ACTIVE);
        }
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
        boolean shouldSyncRequirements = dto.getSkillRequirements() != null || dto.getRequiredSkills() != null;
        if (shouldSyncRequirements) {
            persistShiftSkillRequirements(shift, skillConfig, true);
        }

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

        long epoch = Instant.now().getEpochSecond();
        String checkinData = QR_CHECKIN_PREFIX + shift.getShiftId() + ":" + epoch;
        String checkinSig = com.cani.util.QrCryptoUtil.generateSignature(checkinData);

        String checkoutData = QR_CHECKOUT_PREFIX + shift.getShiftId() + ":" + epoch;
        String checkoutSig = com.cani.util.QrCryptoUtil.generateSignature(checkoutData);

        return ShiftQrCodeResponseDto.builder()
                .shiftId(shift.getShiftId())
                .eventId(event != null ? event.getEventId() : null)
                .eventName(event != null ? event.getName() : "")
                .jobType(shift.getJobType())
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .venueAddress(shift.getLocationAddress() != null ? shift.getLocationAddress() : (event != null ? event.getVenueAddress() : ""))
                .checkinQrContent(checkinData + ":" + checkinSig)
                .checkoutQrContent(checkoutData + ":" + checkoutSig)
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
        if (assignments == null || assignments.isEmpty()) {
            return Collections.emptyList();
        }
        UUID eoUserId = resolveEoUserId(eo, userId);
        Map<UUID, Shift> shiftsById = new HashMap<>();
        if (shift.getShiftId() != null) {
            shiftsById.put(shift.getShiftId(), shift);
        }
        for (ShiftAssignment sa : assignments) {
            if (sa != null && sa.getShift() == null) {
                sa.setShift(shift);
            }
        }
        return mapAssignmentsToAttendanceDtos(assignments, shiftsById, eoUserId);
    }

    /**
     * UC-49: EO xác nhận hoàn thành ca & duyệt công cho Worker (Confirm Completion)
     */
    @Transactional
    public ShiftAttendanceDto verifyAttendance(UUID userId, UUID assignmentId) {
        return verifyAttendance(userId, assignmentId, null);
    }

    /**
     * UC-49 + Skill V3: EO xác nhận hoàn thành ca, duyệt công và xác nhận kỹ năng thực hiện chính để cấp SP
     */
    @Transactional
    public ShiftAttendanceDto verifyAttendance(UUID userId, UUID assignmentId, VerifyAttendanceRequestDto request) {
        EventOrganizer eo = getEoOrThrow(userId);
        ShiftAssignment sa = getOwnedAssignmentOrThrow(assignmentId, eo.getEoId());

        if (Boolean.TRUE.equals(sa.getEoConfirmed())) {
            throw new BadRequestException("Phân công việc này đã được duyệt hoàn thành trước đó.");
        }

        Optional<AttendanceLog> attOpt = attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId);
        if (attOpt.isEmpty() || attOpt.get().getCheckinTime() == null) {
            throw new BadRequestException("Nhân sự chưa check-in vào ca làm việc nên không thể duyệt công.");
        }

        Shift shift = sa.getShift();
        if (shift != null && shift.getChecklist() != null && !shift.getChecklist().isBlank() && workerJobService != null) {
            List<com.cani.dto.worker.ShiftChecklistItemDto> templateItems = workerJobService.parseChecklistTemplate(shift.getChecklist());
            Map<String, com.cani.dto.worker.ChecklistProgressDto> progressMap = workerJobService.parseChecklistProgress(sa.getChecklistProgress());
            for (com.cani.dto.worker.ShiftChecklistItemDto item : templateItems) {
                com.cani.dto.worker.ChecklistProgressDto prog = progressMap.get(item.getId());
                boolean isDone = prog != null && Boolean.TRUE.equals(prog.getIsCompleted());
                if (!isDone) {
                    throw new BadRequestException("Chưa đủ điều kiện duyệt công: Đầu việc \"" + (item.getTitle() != null ? item.getTitle() : item.getId()) + "\" chưa được hoàn thành.");
                }
                if (Boolean.TRUE.equals(item.getIsRequiredEvidence())) {
                    boolean hasProof = prog != null && prog.getEvidenceImageUrl() != null && !prog.getEvidenceImageUrl().isBlank();
                    if (!hasProof) {
                        throw new BadRequestException("Chưa đủ điều kiện duyệt công: Đầu việc \"" + (item.getTitle() != null ? item.getTitle() : item.getId()) + "\" yêu cầu ảnh minh chứng nhưng chưa được nộp.");
                    }
                }
            }
        }

        UUID confirmedSkillId = null;
        if (request != null && request.getConfirmedPerformedSkillId() != null) {
            Skill confirmedSkill = validateAndResolveActiveSkill(
                    request.getConfirmedPerformedSkillId(),
                    "Kỹ năng thực hiện chính"
            );
            confirmedSkillId = confirmedSkill != null ? confirmedSkill.getSkillId() : request.getConfirmedPerformedSkillId();
        } else if (sa.getConfirmedPerformedSkillId() != null) {
            confirmedSkillId = sa.getConfirmedPerformedSkillId();
        } else if (shift != null && shift.getPerformedSkillId() != null) {
            confirmedSkillId = shift.getPerformedSkillId();
        }

        boolean isInShiftAwaitingCheckout = sa.getStatus() == AssignmentStatus.CHECKED_IN
                && attOpt.get().getCheckoutTime() == null;
        boolean wasAlreadyCompleted = (sa.getStatus() == AssignmentStatus.COMPLETED);

        sa.setEoConfirmed(true);
        sa.setEoConfirmedAt(Instant.now());
        if (confirmedSkillId != null) {
            sa.setConfirmedPerformedSkillId(confirmedSkillId);
        }
        if (!isInShiftAwaitingCheckout) {
            sa.setStatus(AssignmentStatus.COMPLETED);
        }
        sa = shiftAssignmentRepository.save(sa);

        // Chỉ cộng thêm số ca hoàn thành cho Worker nếu không phải đang chờ check-out và chưa COMPLETED trước đó
        Worker worker = sa.getWorker();
        if (worker != null && !isInShiftAwaitingCheckout && !wasAlreadyCompleted) {
            worker.setTotalShiftsCompleted((worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0) + 1);
            workerRepository.save(worker);
        }

        // Cấp SP_experience idempotent khi Assignment đã COMPLETED và đã qua xác nhận của EO
        // (Không làm chặn payment hoặc verifyAttendance nếu dữ liệu kỹ năng ca cũ bị thiếu)
        boolean skillConfirmationAllowed = request == null || !Boolean.FALSE.equals(request.getConfirmPerformedSkill());
        if (worker != null && !isInShiftAwaitingCheckout && skillConfirmationAllowed && skillProgressionService != null) {
            skillProgressionService.recordCompletedShiftForAssignment(sa, confirmedSkillId);
        }

        log.info("EO {} đã duyệt hoàn thành ca cho Assignment {} (inShiftAwaitingCheckout={})", userId, assignmentId, isInShiftAwaitingCheckout);

        BigDecimal agreedWage = calculateSingleWorkerAgreedWage(shift);

        // WBS 246.0: Tính toán tiền công thực tế từ số giờ làm việc thực tế (Actual Hours & Payment)
        BigDecimal actualAmount = agreedWage;
        if (shift != null && shift.getHourlyWage() != null) {
            BigDecimal hourly = shift.getHourlyWage();
            BigDecimal allowance = shift.getAllowance() != null ? shift.getAllowance() : BigDecimal.ZERO;
            if (attOpt.get().getHoursWorked() != null) {
                actualAmount = hourly.multiply(attOpt.get().getHoursWorked()).add(allowance).setScale(2, RoundingMode.HALF_UP);
            } else if (attOpt.get().getCheckinTime() != null) {
                Instant checkin = attOpt.get().getCheckinTime();
                Instant now = Instant.now();
                Instant endTime = shift.getEndTime() != null ? shift.getEndTime() : now;
                Instant effectiveEnd = now.isBefore(endTime) ? now : endTime;
                long minutes = Math.max(30, Duration.between(checkin, effectiveEnd).toMinutes());
                BigDecimal hoursWorked = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                actualAmount = hourly.multiply(hoursWorked).add(allowance).setScale(2, RoundingMode.HALF_UP);
            }
        }

        Optional<Payment> existingPay = paymentRepository.findByAssignmentId(assignmentId);
        Payment payment = existingPay.orElse(Payment.builder()
                .assignmentId(assignmentId)
                .shiftId(shift != null ? shift.getShiftId() : null)
                .eventId((shift != null && shift.getEvent() != null) ? shift.getEvent().getEventId() : null)
                .eventOrganizer(eo)
                .worker(worker)
                .agreedAmount(agreedWage)
                .status(com.cani.entity.enums.PaymentStatus.NOT_DECLARED)
                .build());

        // Chỉ cập nhật agreedAmount tính theo giờ thực tế nếu Payment chưa được EO khai báo thanh toán (NOT_DECLARED)
        if (payment.getStatus() == com.cani.entity.enums.PaymentStatus.NOT_DECLARED) {
            payment.setAgreedAmount(actualAmount);
            paymentRepository.save(payment);
        }

        if (notificationService != null && worker != null && worker.getUser() != null && worker.getUser().getUserId() != null) {
            try {
                String evName = (shift != null && shift.getEvent() != null) ? shift.getEvent().getName() : "Sự kiện";
                String shiftTitle = shift != null ? shift.getJobType() : "ca làm việc";
                notificationService.createAndSend(
                        worker.getUser().getUserId(),
                        "BTC đã duyệt công việc của bạn",
                        "Ban tổ chức đã xác nhận nghiệm thu ca \"" + shiftTitle + "\" (" + evName + "). Đang chờ thanh toán thù lao.",
                        com.cani.entity.enums.NotificationType.ASSIGNMENT,
                        "ASSIGNMENT",
                        assignmentId,
                        Map.of("assignmentId", assignmentId.toString(), "type", "EO_CONFIRMED")
                );
            } catch (Exception ex) {
                log.warn("Không thể gửi thông báo duyệt công tới Worker: {}", ex.getMessage());
            }
        }

        String checklistSummary = computeChecklistSummary(shift != null ? shift.getChecklist() : null, sa.getChecklistProgress());
        return mapToShiftAttendanceDto(sa, resolveEoUserId(eo, userId), actualAmount, checklistSummary);
    }

    /**
     * UC-83: EO báo cáo công việc chưa hoàn thành / chất lượng không đạt (Report Incomplete Work)
     */
    @Transactional
    public com.cani.dto.dispute.DisputeDetailResponseDto reportIncompleteWork(
            UUID userId, UUID assignmentId, ReportIncompleteWorkRequestDto request
    ) {
        EventOrganizer eo = getEoOrThrow(userId);
        ShiftAssignment sa = getOwnedAssignmentOrThrow(assignmentId, eo.getEoId());

        if (disputeService == null) {
            throw new BadRequestException("Hệ thống khiếu nại & tranh chấp đang tạm bảo trì");
        }

        Shift shift = sa.getShift();
        String shiftTitle = shift != null ? shift.getJobType() : "Ca làm việc";
        String eventName = (shift != null && shift.getEvent() != null) ? shift.getEvent().getName() : "Sự kiện";

        Map<String, Object> evidenceMap = new HashMap<>();
        evidenceMap.put("proofUrls", request.getProofUrls() != null ? request.getProofUrls() : List.of());
        evidenceMap.put("incompleteChecklistItems", request.getIncompleteChecklistItems() != null ? request.getIncompleteChecklistItems() : List.of());
        String evidenceJson = null;
        try {
            evidenceJson = objectMapper.writeValueAsString(evidenceMap);
        } catch (Exception ignored) {}

        String description = request.getReason();
        if (description.length() < 50) {
            description = String.format("Báo cáo công việc ca [%s] thuộc sự kiện [%s] không đạt yêu cầu: %s (Chi tiết do Ban tổ chức gửi phản ánh)",
                    shiftTitle, eventName, request.getReason());
        }

        com.cani.dto.dispute.CreateDisputeRequestDto disputeReq = com.cani.dto.dispute.CreateDisputeRequestDto.builder()
                .assignmentId(assignmentId)
                .type(com.cani.entity.enums.DisputeType.WORK_QUALITY)
                .title("Báo cáo chất lượng công việc: " + shiftTitle + " (" + eventName + ")")
                .description(description)
                .evidence(evidenceJson)
                .build();

        sa.setEoConfirmed(false);
        if (workerJobService != null && sa.getChecklistProgress() != null) {
            Map<String, com.cani.dto.worker.ChecklistProgressDto> progressMap = workerJobService.parseChecklistProgress(sa.getChecklistProgress());
            if (progressMap.containsKey("__META_READY_NOTIFIED__")) {
                progressMap.remove("__META_READY_NOTIFIED__");
                try {
                    sa.setChecklistProgress(objectMapper.writeValueAsString(progressMap));
                } catch (Exception ignored) {}
            }
        }
        shiftAssignmentRepository.save(sa);

        com.cani.dto.dispute.DisputeDetailResponseDto dispute = disputeService.createDispute(userId, disputeReq);
        log.info("EO {} đã báo cáo công việc không đạt cho assignment {}: disputeId={}", eo.getEoId(), assignmentId, dispute.getId());
        return dispute;
    }

    /**
     * UC-88: EO báo cáo Worker vắng mặt không báo trước (Report No-Show)
     * Kích hoạt chế tài phạt 25 RS, tự động khóa tài khoản nếu RS < 20 và kích hoạt Emergency Dispatch bù người.
     */
    @Transactional
    public NoShowResponseDto reportNoShow(UUID userId, UUID assignmentId, ReportNoShowRequestDto request) {
        EventOrganizer eo = getEoOrThrow(userId);
        ShiftAssignment sa = getOwnedAssignmentOrThrow(assignmentId, eo.getEoId());

        if (noShowService == null) {
            throw new BadRequestException("Hệ thống xử lý vi phạm No-show đang tạm bảo trì");
        }

        Shift shift = sa.getShift();
        if (shift != null && shift.getStartTime() != null) {
            if (Instant.now().isBefore(shift.getStartTime())) {
                throw new BadRequestException("Ca làm việc chưa đến giờ bắt đầu, chưa thể báo cáo vắng mặt (No-show).");
            }
        }

        String notes = (request != null && request.getNotes() != null && !request.getNotes().trim().isEmpty())
                ? request.getNotes().trim()
                : "Nhà tổ chức sự kiện báo cáo nhân sự không có mặt khi ca làm việc bắt đầu.";

        UUID eoUserId = resolveEoUserId(eo, userId);
        return noShowService.markNoShow(assignmentId, NoShowDetectedBy.EVENT_ORGANIZER, eoUserId, notes);
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

        Shift shift = sa.getShift();
        if (shift != null && shift.getEndTime() != null && Instant.now().isBefore(shift.getEndTime())) {
            throw new BadRequestException("Chỉ có thể đánh giá nhân sự sau khi ca làm việc đã kết thúc theo lịch trình.");
        }

        if (disputeRepository != null && disputeRepository.existsByAssignmentIdAndStatusIn(
                assignmentId, List.of(DisputeStatus.OPEN, DisputeStatus.INVESTIGATING, DisputeStatus.ESCALATED))) {
            throw new BadRequestException("Ca làm việc đang có tranh chấp chưa được giải quyết. Vui lòng hoàn tất xử lý tranh chấp trước khi gửi đánh giá.");
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
        if (skillProgressionService != null) {
            skillProgressionService.recordShiftRatingForAssignment(sa, request.getRating());
        }

        log.info("EO {} đã đánh giá {} sao cho Worker {} (Assignment: {})", eoUserId, request.getRating(), worker.getWorkerId(), assignmentId);
        return review;
    }

    /**
     * Danh sách tất cả các sự kiện của EO (Batch-fetched O(1) queries)
     */
    @Transactional(readOnly = true)
    public List<EventResponseDto> getMyEvents(UUID userId) {
        return getEventQueryService().getMyEvents(userId);
    }

    /**
     * Danh sách sự kiện của EO có phân trang (Batch-fetched O(1) queries)
     */
    @Transactional(readOnly = true)
    public EoEventPageResponseDto getMyEventsPage(UUID userId, int page, int size) {
        return getEventQueryService().getMyEventsPage(userId, page, size);
    }

    /**
     * Xóa mềm Sự kiện khỏi danh sách của EO (Soft Delete - Chỉ áp dụng cho Bản nháp)
     */
    @Transactional
    public void deleteEvent(UUID userId, UUID eventId) {
        EventOrganizer eo = getEoOrThrow(userId);
        Event event = getOwnedEventOrThrow(eventId, eo.getEoId());
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new BadRequestException("Chỉ được phép xóa sự kiện ở trạng thái Bản nháp (DRAFT). Sự kiện đã xuất bản hoặc đã tạo không thể xóa mà phải thực hiện Hủy kèm lý do.");
        }
        event.setDeletedAt(Instant.now());
        eventRepository.save(event);
        log.info("EO {} đã xóa vĩnh viễn bản nháp sự kiện {}", userId, eventId);
    }

    /**
     * Xem chi tiết sự kiện
     */
    @Transactional(readOnly = true)
    public EventResponseDto getEventDetail(UUID userId, UUID eventId) {
        return getEventQueryService().getEventDetail(userId, eventId);
    }

    /**
     * EO xuất bản Sự kiện và tự động kích hoạt Matching Engine cho tất cả các ca (Không cần ví/ký quỹ)
     */
    @Transactional
    public EventResponseDto publishEvent(UUID userId, UUID eventId) {
        EventOrganizer eo = getEoOrThrow(userId);
        Event event = getOwnedEventOrThrow(eventId, eo.getEoId());

        if (event.getEndTime() != null && Instant.now().isAfter(event.getEndTime())) {
            throw new BadRequestException("Thời gian tổ chức sự kiện đã trôi qua. Vui lòng chỉnh sửa lại thời gian sự kiện trước khi đăng tuyển.");
        }

        List<Shift> shifts = shiftRepository.findByEvent_EventId(eventId);
        if (shifts == null || shifts.isEmpty()) {
            throw new BadRequestException("Sự kiện chưa có ca làm việc nào. Vui lòng thêm ít nhất 1 ca trước khi đăng tuyển.");
        }

        // BR-08: Chỉ khấu trừ hạn mức Quota từ Gói cước khi sự kiện kích hoạt Matching Engine (MATCHING)
        // Chế độ Mời người (INVITE) hoàn toàn miễn phí, không tiêu hao Quota gói dịch vụ
        if (!isInviteMode(event)) {
            if (event.getMode() == EventMode.MATCHING && subscriptionService != null) {
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
        } else {
            log.info("Sự kiện {} ở chế độ INVITE (Mời trực tiếp): Miễn phí, không tiêu quota gói dịch vụ và không kích hoạt Matching", event.getName());
        }

        event.setStatus(EventStatus.ACTIVE);
        event = eventRepository.save(event);

        for (Shift s : shifts) {
            s.setStatus(ShiftStatus.ACTIVE);
            shiftRepository.save(s);
            if (!isInviteMode(event)) {
                matchingDispatchService.dispatchForShift(s.getShiftId());
            }
        }

        if (isInviteMode(event)) {
            log.info("EO {} đã xuất bản sự kiện INVITE {} với {} ca làm việc (chỉ mời trực tiếp, không auto-matching)",
                    eo.getEoId(), event.getName(), shifts.size());
        } else {
            log.info("EO {} đã xuất bản sự kiện {} và kích hoạt Matching cho {} ca làm việc",
                    eo.getEoId(), event.getName(), shifts.size());
        }

        return mapToEventResponseDto(event, shifts);
    }

    /**
     * Kích hoạt điều phối tìm ứng viên (Dispatch) cho 1 ca cụ thể
     */
    @Transactional
    public ShiftResponseDto publishShift(UUID userId, UUID shiftId) {
        EventOrganizer eo = getEoOrThrow(userId);
        Shift shift = getOwnedShiftOrThrow(shiftId, eo.getEoId());

        if (shift.getEndTime() != null && Instant.now().isAfter(shift.getEndTime())) {
            throw new BadRequestException("Thời gian ca làm việc đã trôi qua, không thể đăng tuyển.");
        }

        shift.setStatus(ShiftStatus.ACTIVE);
        shift = shiftRepository.save(shift);

        if (!isInviteMode(shift.getEvent())) {
            matchingDispatchService.dispatchForShift(shiftId);
            log.info("EO {} kích hoạt điều phối ứng viên cho ca {}", eo.getEoId(), shiftId);
        } else {
            log.info("EO {} mở ca {} thuộc sự kiện INVITE (không kích hoạt Matching)", eo.getEoId(), shiftId);
        }

        return mapToShiftResponseDto(shift);
    }

    /**
     * UC-59: EO kích hoạt điều phối khẩn cấp (Emergency Dispatch) khi ca làm việc thiếu nhân sự
     */
    @Transactional
    public int triggerEmergencyDispatch(UUID userId, UUID shiftId, Double customRadiusKm) {
        Map<String, Object> details = triggerEmergencyDispatchWithDetails(userId, shiftId, customRadiusKm);
        return ((Number) details.get("dispatchedCount")).intValue();
    }

    /**
     * UC-59: EO kích hoạt điều phối khẩn cấp (Emergency Dispatch) có khóa dòng + trả chi tiết bán kính & ứng viên
     */
    @Transactional
    public Map<String, Object> triggerEmergencyDispatchWithDetails(UUID userId, UUID shiftId, Double customRadiusKm) {
        EventOrganizer eo = getEoOrThrow(userId);
        Shift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseGet(() -> shiftRepository.findById(shiftId)
                        .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc", "id", shiftId)));

        if (shift.getEvent() == null || shift.getEvent().getEventOrganizer() == null
                || !shift.getEvent().getEventOrganizer().getEoId().equals(eo.getEoId())) {
            throw new BadRequestException("Bạn không có quyền thao tác trên ca làm việc này");
        }

        ShiftStaffingPolicy policy = getStaffingPolicy();
        ShiftStaffingPolicy.EmergencyEligibility eligibility = policy.evaluateEmergencyEligibility(shift, Instant.now());
        if (!eligibility.canTriggerEmergency()) {
            throw new BadRequestException(eligibility.blockReason());
        }

        double effectiveRadiusKm = policy.resolveEffectiveEmergencyRadiusKm(customRadiusKm);
        shift.setEmergency(true);
        shiftRepository.save(shift);

        int dispatched = matchingDispatchService.triggerEmergencyDispatch(shiftId, effectiveRadiusKm);
        log.info("EO {} kích hoạt Emergency Dispatch cho ca {} (radius={}km): Đã gửi {} đề xuất",
                eo.getEoId(), shiftId, effectiveRadiusKm, dispatched);

        // Ticket EO-011: Đồng bộ thông báo trạng thái điều phối khẩn cấp vào Notification Bell của EO
        if (notificationService != null && eo.getUser() != null && eo.getUser().getUserId() != null) {
            try {
                String evName = (shift.getEvent() != null && shift.getEvent().getName() != null)
                        ? shift.getEvent().getName() : "Sự kiện";
                String radiusLabel = (effectiveRadiusKm == Math.rint(effectiveRadiusKm))
                        ? String.valueOf((long) effectiveRadiusKm) : String.valueOf(effectiveRadiusKm);
                String notifTitle = "🚨 [ĐIỀU PHỐI KHẨN CẤP] Đã kích hoạt cho ca " + shift.getJobType();
                String notifBody = dispatched > 0
                        ? String.format("Đã mở rộng bán kính tìm kiếm %s km cho ca \"%s\" (%s) và gửi %d đề xuất khẩn cấp đến Worker phù hợp theo Preference.",
                                radiusLabel, shift.getJobType(), evName, dispatched)
                        : String.format("Đã mở rộng bán kính tìm kiếm %s km cho ca \"%s\" (%s). Hệ thống đang tiếp tục quét Worker trực tuyến phù hợp.",
                                radiusLabel, shift.getJobType(), evName);

                notificationService.createAndSend(
                        eo.getUser().getUserId(),
                        notifTitle,
                        notifBody,
                        NotificationType.REMINDER,
                        "SHIFT",
                        shiftId,
                        Map.of(
                                "eventId", shift.getEvent() != null ? shift.getEvent().getEventId().toString() : "",
                                "shiftId", shiftId.toString(),
                                "type", "EMERGENCY_DISPATCH_ACTIVE",
                                "dispatchedCount", String.valueOf(dispatched),
                                "radiusKm", radiusLabel
                        )
                );
            } catch (Exception ex) {
                log.warn("Lỗi gửi thông báo kích hoạt Emergency Dispatch cho EO: {}", ex.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shiftId", shiftId);
        result.put("dispatchedCount", dispatched);
        result.put("emergency", true);
        result.put("effectiveRadiusKm", effectiveRadiusKm);
        result.put("hasCandidates", dispatched > 0);
        result.put("missingWorkers", eligibility.missingWorkers());
        return result;
    }

    private boolean isInviteMode(Event event) {
        return event != null && event.getMode() == EventMode.INVITE;
    }

    /**
     * Tắt chế độ điều phối khẩn cấp (Emergency Dispatch) cho ca làm việc
     */
    @Transactional
    public void disableEmergencyDispatch(UUID userId, UUID shiftId) {
        EventOrganizer eo = getEoOrThrow(userId);
        Shift shift = getOwnedShiftOrThrow(shiftId, eo.getEoId());
        shift.setEmergency(false);
        shiftRepository.save(shift);
        log.info("EO {} đã tắt chế độ Emergency Dispatch cho ca {}", eo.getEoId(), shiftId);

        // Ticket EO-011: Đồng bộ thông báo tắt Emergency Dispatch vào Notification Bell của EO
        if (notificationService != null && eo.getUser() != null && eo.getUser().getUserId() != null) {
            try {
                String evName = (shift.getEvent() != null && shift.getEvent().getName() != null)
                        ? shift.getEvent().getName() : "Sự kiện";
                notificationService.createAndSend(
                        eo.getUser().getUserId(),
                        "ℹ️ [ĐIỀU PHỐI KHẨN CẤP] Đã tắt chế độ khẩn cấp",
                        String.format("Chế độ điều phối khẩn cấp cho ca \"%s\" thuộc sự kiện \"%s\" đã được tắt.", shift.getJobType(), evName),
                        NotificationType.SYSTEM,
                        "SHIFT",
                        shiftId,
                        Map.of(
                                "eventId", shift.getEvent() != null ? shift.getEvent().getEventId().toString() : "",
                                "shiftId", shiftId.toString(),
                                "type", "EMERGENCY_DISPATCH_DISABLED"
                        )
                );
            } catch (Exception ex) {
                log.warn("Lỗi gửi thông báo tắt Emergency Dispatch cho EO: {}", ex.getMessage());
            }
        }
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

    private void validateFutureStartTime(Instant startTime, String errorMessage) {
        if (startTime == null) return;
        Instant minAllowed = Instant.now().minus(Duration.ofMinutes(5));
        if (startTime.isBefore(minAllowed)) {
            throw new BadRequestException(errorMessage);
        }
    }

    private void validateShiftArrivalTime(Instant startTime, Instant arrivalTime) {
        if (startTime == null || arrivalTime == null) return;
        if (arrivalTime.isAfter(startTime)) {
            throw new BadRequestException("Giờ yêu cầu có mặt (Arrival Time) phải trước hoặc bằng giờ bắt đầu ca.");
        }
        if (Duration.between(arrivalTime, startTime).toMinutes() > 120) {
            throw new BadRequestException("Giờ yêu cầu có mặt không được sớm hơn giờ bắt đầu ca quá 120 phút.");
        }
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

    private void validateShiftJobType(String rawJobType) {
        String trimmed = rawJobType != null ? rawJobType.trim() : "";
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Vui lòng nhập tên vị trí / vai trò cần tuyển.");
        }
        if (trimmed.length() > 80) {
            throw new BadRequestException("Tên vị trí công việc không được vượt quá 80 ký tự.");
        }
        if (!trimmed.matches("^[\\p{L}\\p{N}\\s,./()&+-]+$")) {
            throw new BadRequestException("Tên vị trí chỉ được chứa chữ cái, chữ số, khoảng trắng và các dấu , . - / ( ) & +.");
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        Set<String> allowedAcronyms = Set.of("mc", "pg", "pb", "dj", "pr", "it", "av", "led", "vip", "f&b", "fnb", "qc", "qa", "cskh", "ctv", "hr", "pa", "pm");
        String[] tokens = lower.split("[\\s,./()&+-]+");
        boolean hasValidLetterToken = false;
        String vowelRegex = ".*[aàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ].*";

        if (trimmed.length() < 3 && !allowedAcronyms.contains(lower)) {
            throw new BadRequestException("Tên vị trí quá ngắn (tối thiểu 3 ký tự, trừ viết tắt hợp lệ như MC, PG, DJ).");
        }
        if (trimmed.matches(".*(.)\\1{3,}.*")) {
            throw new BadRequestException("Tên vị trí chứa ký tự lặp bất thường.");
        }
        if (lower.matches(".*(asdasd|sdasd|ádasd|adasd|sdfsdf|dfghj|fghjk|ghjkl|qwerty|zxcvb|sdfh|asdf).*")) {
            throw new BadRequestException("Tên vị trí không hợp lệ hoặc chứa chuỗi ký tự vô nghĩa.");
        }
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (token.matches("^\\d+$")) continue;
            if (allowedAcronyms.contains(token)) {
                hasValidLetterToken = true;
                continue;
            }
            if (token.matches(".*\\p{L}.*")) {
                hasValidLetterToken = true;
            }
            String lettersOnly = token.replaceAll("[^\\p{L}]", "");
            if (lettersOnly.length() >= 3 && !lettersOnly.matches(vowelRegex)) {
                throw new BadRequestException("Tên vị trí chứa từ vô nghĩa hoặc sai định dạng (không có nguyên âm).");
            }
        }
        if (!hasValidLetterToken) {
            throw new BadRequestException("Tên vị trí phải chứa từ khóa mô tả công việc hợp lệ (không thể chỉ nhập số).");
        }
    }

    private void validateShiftHourlyWage(Event event, BigDecimal hourlyWage) {
        if (hourlyWage == null || hourlyWage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Thù lao theo giờ phải lớn hơn 0!");
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

    private boolean isChecklistFullyCompleted(String templateJson, String progressJson) {
        if (templateJson == null || templateJson.isBlank()) {
            return true;
        }
        List<ShiftChecklistItemDto> template = parseChecklistTemplate(templateJson);
        if (template.isEmpty()) {
            return true;
        }
        if (progressJson == null || progressJson.isBlank()) {
            return false;
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
            return completed >= template.size();
        } catch (Exception e) {
            return false;
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

    public List<ShiftAttendanceDto> mapAssignmentsToAttendanceDtos(
            List<ShiftAssignment> assignments,
            Map<UUID, Shift> shiftsById
    ) {
        return mapAssignmentsToAttendanceDtos(assignments, shiftsById, null);
    }

    public List<ShiftAttendanceDto> mapAssignmentsToAttendanceDtos(
            List<ShiftAssignment> assignments,
            Map<UUID, Shift> shiftsById,
            UUID eoUserId
    ) {
        if (assignments == null || assignments.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, Shift> safeShiftsById = shiftsById != null ? shiftsById : Collections.emptyMap();

        List<UUID> assignmentIds = assignments.stream()
                .filter(Objects::nonNull)
                .map(ShiftAssignment::getAssignmentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 1. Batch AttendanceLog
        Map<UUID, AttendanceLog> attendanceByAssignmentId = new HashMap<>();
        boolean hasBatchAttendance = false;
        if (!assignmentIds.isEmpty() && attendanceLogRepository != null) {
            List<AttendanceLog> batchLogs = attendanceLogRepository.findByShiftAssignment_AssignmentIdIn(assignmentIds);
            if (batchLogs != null && !batchLogs.isEmpty()) {
                hasBatchAttendance = true;
                for (AttendanceLog att : batchLogs) {
                    if (att != null && att.getShiftAssignment() != null && att.getShiftAssignment().getAssignmentId() != null) {
                        attendanceByAssignmentId.putIfAbsent(att.getShiftAssignment().getAssignmentId(), att);
                    }
                }
            }
        }

        // 2. Batch Payment
        Map<UUID, Payment> paymentByAssignmentId = new HashMap<>();
        boolean hasBatchPayments = false;
        if (!assignmentIds.isEmpty() && paymentRepository != null) {
            List<Payment> batchPayments = paymentRepository.findByAssignmentIdIn(assignmentIds);
            if (batchPayments != null && !batchPayments.isEmpty()) {
                hasBatchPayments = true;
                for (Payment p : batchPayments) {
                    if (p != null && p.getAssignmentId() != null) {
                        paymentByAssignmentId.putIfAbsent(p.getAssignmentId(), p);
                    }
                }
            }
        }

        // 3. Batch Review
        Set<UUID> reviewedAssignmentIds = new LinkedHashSet<>();
        boolean hasBatchReviews = false;
        if (!assignmentIds.isEmpty() && reviewRepository != null) {
            List<Review> batchReviews = reviewRepository.findByAssignmentIdIn(assignmentIds);
            if (batchReviews != null && !batchReviews.isEmpty()) {
                hasBatchReviews = true;
                for (Review r : batchReviews) {
                    if (r != null && r.getAssignmentId() != null
                            && (eoUserId == null || Objects.equals(r.getReviewerId(), eoUserId))) {
                        reviewedAssignmentIds.add(r.getAssignmentId());
                    }
                }
            }
        }

        // 4. Batch WorkerEkycVerification
        List<UUID> workerIds = assignments.stream()
                .filter(Objects::nonNull)
                .map(ShiftAssignment::getWorker)
                .filter(Objects::nonNull)
                .map(Worker::getWorkerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, WorkerEkycVerification> latestApprovedEkycByWorkerId = new HashMap<>();
        Map<UUID, WorkerEkycVerification> latestAnyEkycByWorkerId = new HashMap<>();
        boolean hasBatchEkyc = false;
        if (!workerIds.isEmpty() && workerEkycVerificationRepository != null) {
            List<WorkerEkycVerification> batchEkycs =
                    workerEkycVerificationRepository.findByWorker_WorkerIdInOrderByCreatedAtDesc(workerIds);
            if (batchEkycs != null && !batchEkycs.isEmpty()) {
                hasBatchEkyc = true;
                for (WorkerEkycVerification v : batchEkycs) {
                    if (v != null && v.getWorker() != null && v.getWorker().getWorkerId() != null) {
                        UUID wId = v.getWorker().getWorkerId();
                        latestAnyEkycByWorkerId.putIfAbsent(wId, v);
                        if ("APPROVED".equalsIgnoreCase(v.getStatus())) {
                            latestApprovedEkycByWorkerId.putIfAbsent(wId, v);
                        }
                    }
                }
            }
        }

        // 5. Batch Skill Names & Precomputed Shift wages
        Set<UUID> skillIds = new LinkedHashSet<>();
        Map<UUID, BigDecimal> wageByShiftId = new HashMap<>();
        for (Shift s : safeShiftsById.values()) {
            if (s != null) {
                if (s.getShiftId() != null) {
                    wageByShiftId.put(s.getShiftId(), calculateSingleWorkerAgreedWage(s));
                }
                if (s.getPerformedSkillId() != null) {
                    skillIds.add(s.getPerformedSkillId());
                }
            }
        }
        for (ShiftAssignment sa : assignments) {
            if (sa == null) continue;
            if (sa.getConfirmedPerformedSkillId() != null) {
                skillIds.add(sa.getConfirmedPerformedSkillId());
            }
            Shift s = sa.getShift();
            if (s != null) {
                if (s.getPerformedSkillId() != null) {
                    skillIds.add(s.getPerformedSkillId());
                }
                if (s.getShiftId() != null) {
                    wageByShiftId.computeIfAbsent(s.getShiftId(), k -> calculateSingleWorkerAgreedWage(s));
                }
            }
        }
        Map<UUID, String> skillNamesById = new HashMap<>();
        if (!skillIds.isEmpty() && skillRepository != null) {
            List<Skill> loadedSkills = skillRepository.findAllById(skillIds);
            if (loadedSkills != null) {
                for (Skill sk : loadedSkills) {
                    if (sk != null && sk.getSkillId() != null && sk.getName() != null) {
                        skillNamesById.put(sk.getSkillId(), sk.getName());
                    }
                }
            }
        }

        List<ShiftAttendanceDto> results = new ArrayList<>(assignments.size());
        for (ShiftAssignment sa : assignments) {
            if (sa == null) continue;
            Shift shift = sa.getShift();
            if (shift != null && shift.getShiftId() != null && safeShiftsById.containsKey(shift.getShiftId())) {
                shift = safeShiftsById.get(shift.getShiftId());
            } else if (shift == null && safeShiftsById.size() == 1) {
                shift = safeShiftsById.values().iterator().next();
            }

            BigDecimal agreedWage = (shift != null && shift.getShiftId() != null && wageByShiftId.containsKey(shift.getShiftId()))
                    ? wageByShiftId.get(shift.getShiftId())
                    : calculateSingleWorkerAgreedWage(shift);
            String checklistSummary = computeChecklistSummary(shift != null ? shift.getChecklist() : null, sa.getChecklistProgress());

            Optional<AttendanceLog> attOpt;
            if (hasBatchAttendance) {
                attOpt = Optional.ofNullable(sa.getAssignmentId() != null ? attendanceByAssignmentId.get(sa.getAssignmentId()) : null);
            } else {
                attOpt = (sa.getAssignmentId() != null && attendanceLogRepository != null)
                        ? attendanceLogRepository.findByShiftAssignment_AssignmentId(sa.getAssignmentId())
                        : Optional.empty();
            }

            Optional<Payment> payOpt;
            if (hasBatchPayments) {
                payOpt = Optional.ofNullable(sa.getAssignmentId() != null ? paymentByAssignmentId.get(sa.getAssignmentId()) : null);
            } else {
                payOpt = (sa.getAssignmentId() != null && paymentRepository != null)
                        ? paymentRepository.findByAssignmentId(sa.getAssignmentId())
                        : Optional.empty();
            }

            boolean reviewed;
            if (hasBatchReviews) {
                reviewed = sa.getAssignmentId() != null && reviewedAssignmentIds.contains(sa.getAssignmentId());
            } else {
                reviewed = sa.getAssignmentId() != null && reviewRepository != null
                        && reviewRepository.findByAssignmentIdAndReviewerId(sa.getAssignmentId(), eoUserId).isPresent();
            }

            Optional<WorkerEkycVerification> ekycOpt = Optional.empty();
            Worker worker = sa.getWorker();
            if (worker != null && worker.getWorkerId() != null) {
                if (hasBatchEkyc) {
                    WorkerEkycVerification v = latestApprovedEkycByWorkerId.getOrDefault(
                            worker.getWorkerId(),
                            latestAnyEkycByWorkerId.get(worker.getWorkerId())
                    );
                    ekycOpt = Optional.ofNullable(v);
                } else if (workerEkycVerificationRepository != null) {
                    ekycOpt = workerEkycVerificationRepository
                            .findTopByWorker_WorkerIdAndStatusInOrderByCreatedAtDesc(worker.getWorkerId(), List.of("APPROVED"))
                            .or(() -> workerEkycVerificationRepository.findTopByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId()));
                }
            }

            ShiftAttendanceDto dto = buildShiftAttendanceDto(
                    sa,
                    shift,
                    agreedWage,
                    checklistSummary,
                    attOpt,
                    payOpt,
                    reviewed,
                    ekycOpt,
                    skillNamesById
            );
            if (shift != null) {
                dto.setShiftId(shift.getShiftId());
                dto.setJobType(shift.getJobType());
                dto.setShiftName(shift.getJobType());
            }
            results.add(dto);
        }
        return results;
    }

    private ShiftAttendanceDto mapToShiftAttendanceDto(ShiftAssignment sa, UUID eoUserId, BigDecimal agreedWage, String checklistSummary) {
        Worker worker = sa.getWorker();
        Shift shift = sa.getShift();

        Optional<AttendanceLog> attOpt = (sa.getAssignmentId() != null && attendanceLogRepository != null)
                ? attendanceLogRepository.findByShiftAssignment_AssignmentId(sa.getAssignmentId())
                : Optional.empty();
        Optional<Payment> payOpt = (sa.getAssignmentId() != null && paymentRepository != null)
                ? paymentRepository.findByAssignmentId(sa.getAssignmentId())
                : Optional.empty();
        boolean reviewed = sa.getAssignmentId() != null && reviewRepository != null
                && reviewRepository.findByAssignmentIdAndReviewerId(sa.getAssignmentId(), eoUserId).isPresent();

        Optional<WorkerEkycVerification> ekycOpt = Optional.empty();
        if (worker != null && worker.getWorkerId() != null && workerEkycVerificationRepository != null) {
            ekycOpt = workerEkycVerificationRepository
                    .findTopByWorker_WorkerIdAndStatusInOrderByCreatedAtDesc(worker.getWorkerId(), List.of("APPROVED"))
                    .or(() -> workerEkycVerificationRepository.findTopByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId()));
        }

        return buildShiftAttendanceDto(
                sa,
                shift,
                agreedWage,
                checklistSummary,
                attOpt,
                payOpt,
                reviewed,
                ekycOpt,
                Collections.emptyMap()
        );
    }

    private ShiftAttendanceDto buildShiftAttendanceDto(
            ShiftAssignment sa,
            Shift shift,
            BigDecimal agreedWage,
            String checklistSummary,
            Optional<AttendanceLog> attOpt,
            Optional<Payment> payOpt,
            boolean reviewed,
            Optional<WorkerEkycVerification> ekycOpt,
            Map<UUID, String> preloadedSkillNames
    ) {
        Worker worker = sa.getWorker();
        User u = worker != null ? worker.getUser() : null;
        boolean checklistDone = isChecklistFullyCompleted(shift != null ? shift.getChecklist() : null, sa.getChecklistProgress());

        String defaultName = "Worker #" + (worker != null && worker.getWorkerId() != null ? worker.getWorkerId().toString().substring(0, 8) : "");

        Integer workerAge = null;
        Boolean meetsMinimumAge = null;
        if (ekycOpt != null && ekycOpt.isPresent()) {
            WorkerEkycVerification latestEkyc = ekycOpt.get();
            meetsMinimumAge = latestEkyc.getMeetsMinimumAge() != null
                    ? latestEkyc.getMeetsMinimumAge()
                    : EkycConsentAndAgeHelper.evaluateMeetsMinimumAge(latestEkyc.getDob(), systemConfigRepository);
            workerAge = EkycConsentAndAgeHelper.calculateAgeFromDob(latestEkyc.getDob());
        }

        String bankCode = worker != null ? worker.getBankCode() : null;
        String bankAccountNumber = worker != null ? worker.getBankAccountNumber() : null;
        String bankAccountName = worker != null ? worker.getBankAccountName() : null;
        if ((bankAccountName == null || bankAccountName.isBlank()) && u != null) {
            bankAccountName = u.getFullName();
        }
        String vietQrUrl = VietQrUtil.buildVietQrUrl(bankCode, bankAccountNumber, bankAccountName, agreedWage);

        UUID performedSkillId = shift != null ? shift.getPerformedSkillId() : null;
        UUID confirmedSkillId = sa.getConfirmedPerformedSkillId();
        String performedSkillName = performedSkillId != null
                ? (preloadedSkillNames != null && preloadedSkillNames.containsKey(performedSkillId)
                        ? preloadedSkillNames.get(performedSkillId)
                        : resolveSkillNameById(performedSkillId))
                : null;
        String confirmedSkillName = confirmedSkillId != null
                ? (preloadedSkillNames != null && preloadedSkillNames.containsKey(confirmedSkillId)
                        ? preloadedSkillNames.get(confirmedSkillId)
                        : resolveSkillNameById(confirmedSkillId))
                : null;

        return ShiftAttendanceDto.builder()
                .assignmentId(sa.getAssignmentId())
                .shiftId(shift != null ? shift.getShiftId() : null)
                .jobType(shift != null ? shift.getJobType() : null)
                .shiftName(shift != null ? shift.getJobType() : null)
                .performedSkillId(performedSkillId)
                .performedSkillName(performedSkillName)
                .confirmedPerformedSkillId(confirmedSkillId)
                .confirmedPerformedSkillName(confirmedSkillName)
                .spExperienceAwarded(Boolean.TRUE.equals(sa.getSpExperienceAwarded()))
                .spExperienceAwardedAt(sa.getSpExperienceAwardedAt())
                .spRatingAwarded(Boolean.TRUE.equals(sa.getSpRatingAwarded()))
                .inviteOverrideReason(sa.getInviteOverrideReason())
                .workerId(worker != null ? worker.getWorkerId() : null)
                .userId(u != null ? u.getUserId() : null)
                .workerName(u != null ? u.getFullName() : defaultName)
                .workerPhone(u != null ? u.getPhone() : "")
                .avatarUrl(u != null ? u.getAvatarUrl() : null)
                .workerReliabilityScore(worker != null ? worker.getReliabilityScore() : MAX_RELIABILITY_SCORE)
                .workerRatingAvg(worker != null ? worker.getRatingAvg() : DEFAULT_RATING)
                .workerAge(workerAge)
                .meetsMinimumAge(meetsMinimumAge)
                .workerDob(null)
                .assignmentStatus(sa.getStatus() != null ? sa.getStatus().name() : AssignmentStatus.PENDING.name())
                .checkinTime(attOpt.map(AttendanceLog::getCheckinTime).orElse(null))
                .checkoutTime(attOpt.map(AttendanceLog::getCheckoutTime).orElse(null))
                .isOnTime(attOpt.map(AttendanceLog::getIsOnTime).orElse(null))
                .hoursWorked(attOpt.map(AttendanceLog::getHoursWorked).orElse(null))
                .eoConfirmed(Boolean.TRUE.equals(sa.getEoConfirmed()))
                .eoConfirmedAt(sa.getEoConfirmedAt())
                .hasReviewedWorker(reviewed)
                .checklistProgress(checklistSummary)
                .checklistRawProgress(sa.getChecklistProgress())
                .isChecklistCompleted(checklistDone)
                .proofImageUrl(attOpt.map(AttendanceLog::getProofImageUrl).orElse(null))
                .paymentStatus(payOpt.map(p -> p.getStatus().name()).orElse(PAYMENT_NOT_DECLARED))
                .paymentProofUrl(payOpt.map(Payment::getPaymentProofUrl).orElse(null))
                .externalRef(payOpt.map(Payment::getExternalRef).orElse(null))
                .declaredAt(payOpt.map(Payment::getDeclaredAt).orElse(null))
                .agreedWage(agreedWage)
                .declaredAmount(payOpt.map(Payment::getDeclaredAmount).orElse(null))
                .paymentMethod(payOpt.map(Payment::getPaymentMethod).orElse(null))
                .workerConfirmStatus(payOpt.map(Payment::getWorkerConfirmStatus).orElse(null))
                .workerComment(payOpt.map(Payment::getWorkerComment).orElse(null))
                .workerBio(worker != null ? worker.getBio() : null)
                .yearsExperience(worker != null ? worker.getYearsExperience() : 0)
                .bankCode(bankCode)
                .bankAccountNumber(bankAccountNumber)
                .bankAccountName(bankAccountName)
                .vietQrUrl(vietQrUrl)
                .source(sa.getSource())
                .groupId(sa.getGroupId())
                .build();
    }

    private List<EventResponseDto> mapEventsToResponseDtos(List<Event> events) {
        return getEventQueryService().mapEventsToResponseDtos(events);
    }

    private EventResponseDto mapToEventResponseDto(Event e, List<Shift> shifts) {
        return getEventQueryService().mapToEventResponseDto(e, shifts);
    }

    @Transactional(readOnly = true)
    public List<ShiftAttendanceDto> getEventWorkersList(UUID userId, UUID eventId) {
        EventOrganizer eo = getEoOrThrow(userId);
        getOwnedEventOrThrow(eventId, eo.getEoId());
        List<Shift> shifts = shiftRepository.findByEvent_EventIdAndStatusNot(eventId, ShiftStatus.CANCELLED);
        if (shifts == null || shifts.isEmpty()) {
            return Collections.emptyList();
        }
        UUID eoUserId = resolveEoUserId(eo, userId);

        Map<UUID, Shift> shiftsById = new LinkedHashMap<>();
        List<UUID> shiftIds = new ArrayList<>(shifts.size());
        for (Shift s : shifts) {
            if (s != null && s.getShiftId() != null) {
                shiftsById.put(s.getShiftId(), s);
                shiftIds.add(s.getShiftId());
            }
        }

        List<ShiftAssignment> allAssignments = (!shiftIds.isEmpty() && shiftAssignmentRepository != null)
                ? shiftAssignmentRepository.findByShift_ShiftIdIn(shiftIds)
                : Collections.emptyList();
        if ((allAssignments == null || allAssignments.isEmpty()) && shiftAssignmentRepository != null) {
            allAssignments = new ArrayList<>();
            for (Shift s : shifts) {
                if (s != null && s.getShiftId() != null) {
                    List<ShiftAssignment> perShift = shiftAssignmentRepository.findByShift_ShiftId(s.getShiftId());
                    if (perShift != null) {
                        for (ShiftAssignment sa : perShift) {
                            if (sa != null && sa.getShift() == null) {
                                sa.setShift(s);
                            }
                            allAssignments.add(sa);
                        }
                    }
                }
            }
        }
        return mapAssignmentsToAttendanceDtos(allAssignments, shiftsById, eoUserId);
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
        return getEventQueryService().mapToShiftResponseDto(s);
    }

    private PreparedShiftSkillConfig prepareShiftSkillConfig(
            UUID performedSkillId,
            UUID[] legacyRequiredSkills,
            List<ShiftSkillRequirementDto> structuredReqs,
            String customSkillNotes
    ) {
        if (performedSkillId != null) {
            validateAndResolveActiveSkill(performedSkillId, "Kỹ năng thực hiện chính");
        }

        Map<UUID, PreparedRequirementEntry> entriesBySkillId = new LinkedHashMap<>();
        if (structuredReqs != null && !structuredReqs.isEmpty()) {
            for (ShiftSkillRequirementDto item : structuredReqs) {
                if (item == null || item.getSkillId() == null) {
                    throw new BadRequestException("Mỗi yêu cầu kỹ năng phải gắn với một Skill ID hợp lệ.");
                }
                Skill resolvedSkill = validateAndResolveActiveSkill(item.getSkillId(), "Kỹ năng yêu cầu");
                SkillRequirementType reqType = item.getRequirementType() != null
                        ? item.getRequirementType()
                        : SkillRequirementType.REQUIRED;
                VerificationTier minTier = item.getMinVerificationTier() != null
                        ? item.getMinVerificationTier()
                        : VerificationTier.SELF_DECLARED;
                boolean reqCert = Boolean.TRUE.equals(item.getRequiresValidCertificate());

                PreparedRequirementEntry existing = entriesBySkillId.get(item.getSkillId());
                if (existing == null) {
                    entriesBySkillId.put(item.getSkillId(), new PreparedRequirementEntry(
                            item.getSkillId(), resolvedSkill, reqType, minTier, reqCert));
                } else {
                    // Nếu trùng skillId: REQUIRED ưu tiên hơn PREFERRED, mức xác minh cao hơn được giữ lại
                    SkillRequirementType mergedType = (existing.requirementType() == SkillRequirementType.REQUIRED
                            || reqType == SkillRequirementType.REQUIRED)
                            ? SkillRequirementType.REQUIRED
                            : SkillRequirementType.PREFERRED;
                    VerificationTier mergedTier = tierRank(minTier) > tierRank(existing.minVerificationTier())
                            ? minTier : existing.minVerificationTier();
                    boolean mergedCert = existing.requiresValidCertificate() || reqCert;
                    entriesBySkillId.put(item.getSkillId(), new PreparedRequirementEntry(
                            item.getSkillId(),
                            resolvedSkill != null ? resolvedSkill : existing.skill(),
                            mergedType,
                            mergedTier,
                            mergedCert));
                }
            }
        } else if (legacyRequiredSkills != null && legacyRequiredSkills.length > 0) {
            Set<UUID> dedup = Arrays.stream(legacyRequiredSkills)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (UUID skillId : dedup) {
                Skill resolvedSkill = validateAndResolveActiveSkill(skillId, "Kỹ năng bắt buộc");
                entriesBySkillId.put(skillId, new PreparedRequirementEntry(
                        skillId,
                        resolvedSkill,
                        SkillRequirementType.REQUIRED,
                        VerificationTier.SELF_DECLARED,
                        false));
            }
        }

        UUID[] requiredArray;
        if (!entriesBySkillId.isEmpty()) {
            List<UUID> reqIds = entriesBySkillId.values().stream()
                    .filter(e -> e.requirementType() == SkillRequirementType.REQUIRED)
                    .map(PreparedRequirementEntry::skillId)
                    .toList();
            requiredArray = reqIds.isEmpty() ? null : reqIds.toArray(new UUID[0]);
        } else {
            requiredArray = legacyRequiredSkills;
        }

        String trimmedNotes = (customSkillNotes != null && !customSkillNotes.isBlank())
                ? customSkillNotes.trim()
                : null;

        return new PreparedShiftSkillConfig(performedSkillId, requiredArray, new ArrayList<>(entriesBySkillId.values()), trimmedNotes);
    }

    private void persistShiftSkillRequirements(Shift shift, PreparedShiftSkillConfig config, boolean clearExisting) {
        if (shiftSkillRequirementRepository == null || shift == null || shift.getShiftId() == null) {
            return;
        }
        if (clearExisting) {
            shiftSkillRequirementRepository.deleteByShift_ShiftId(shift.getShiftId());
        }
        if (config.entries().isEmpty()) {
            return;
        }
        List<ShiftSkillRequirement> toSave = new ArrayList<>();
        for (PreparedRequirementEntry entry : config.entries()) {
            Skill sk = entry.skill();
            if (sk == null && skillRepository != null) {
                sk = skillRepository.findById(entry.skillId()).orElse(null);
            }
            if (sk == null) {
                continue;
            }
            toSave.add(ShiftSkillRequirement.builder()
                    .shift(shift)
                    .skill(sk)
                    .requirementType(entry.requirementType())
                    .minVerificationTier(entry.minVerificationTier())
                    .requiresValidCertificate(entry.requiresValidCertificate())
                    .build());
        }
        if (!toSave.isEmpty()) {
            shiftSkillRequirementRepository.saveAll(toSave);
        }
    }

    private Skill validateAndResolveActiveSkill(UUID skillId, String label) {
        if (skillId == null || skillRepository == null) {
            return null;
        }
        Skill sk = skillRepository.findById(skillId)
                .orElseThrow(() -> new BadRequestException(label + " không tồn tại trong hệ thống (ID: " + skillId + ")."));
        if (Boolean.FALSE.equals(sk.getIsActive())) {
            throw new BadRequestException(label + " \"" + sk.getName() + "\" hiện chưa được kích hoạt trong danh mục chuẩn.");
        }
        return sk;
    }

    private String resolveSkillNameById(UUID skillId) {
        if (skillId == null || skillRepository == null) {
            return null;
        }
        return skillRepository.findById(skillId).map(Skill::getName).orElse(null);
    }

    private int tierRank(VerificationTier tier) {
        if (tier == null) return 0;
        return switch (tier) {
            case SELF_DECLARED -> 0;
            case AI_DOCUMENT -> 1;
            case PLATFORM_PROVEN -> 2;
        };
    }

    private record PreparedRequirementEntry(
            UUID skillId,
            Skill skill,
            SkillRequirementType requirementType,
            VerificationTier minVerificationTier,
            boolean requiresValidCertificate
    ) {
    }

    private record PreparedShiftSkillConfig(
            UUID performedSkillId,
            UUID[] requiredSkillIdsArray,
            List<PreparedRequirementEntry> entries,
            String customSkillNotes
    ) {
    }

    public static boolean isGibberishOrSpam(String input) {
        if (input == null) return false;
        String str = input.trim().toLowerCase();
        if (str.length() < 3) return false;

        // 1. Ký tự đơn lặp lại liên tiếp >= 4 lần (ví dụ: aaaaa, 11111)
        java.util.regex.Pattern singleCharRep = java.util.regex.Pattern.compile("(.)\\1{3,}");
        if (singleCharRep.matcher(str).find()) return true;

        // 2. Cặp ký tự lặp lại >= 3 lần (ví dụ: ababab, cdcdcd)
        java.util.regex.Pattern pairCharRep = java.util.regex.Pattern.compile("(.{2})\\1{2,}");
        if (pairCharRep.matcher(str).find()) return true;

        // Tách các từ theo khoảng trắng
        String[] words = str.split("\\s+");

        // 3. Lặp từ liên tiếp >= 3 lần (ví dụ: "ád ád ád", "test test test")
        String consecutiveWord = null;
        int consecutiveCount = 0;
        for (String w : words) {
            if (!w.isBlank() && w.equals(consecutiveWord)) {
                consecutiveCount++;
                if (consecutiveCount >= 3) return true;
            } else {
                consecutiveWord = w;
                consecutiveCount = 1;
            }
        }

        // 4. Các mẫu cào phím (Keyboard mash) phổ biến (Telex / Qwerty home-row)
        java.util.regex.Pattern mashPattern = java.util.regex.Pattern.compile(
                "(asdasd|sdasd|ádasd|sdada|adasd|sdfsdf|dfghj|fghjk|ghjkl|qwerty|zxcvb)",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        if (mashPattern.matcher(str).find()) {
            return true;
        }

        // 5. Kiểm tra tần suất từ chiếm ưu thế bất thường (ví dụ: "ádas ád ád ád ádasd ád")
        if (words.length >= 4) {
            java.util.Map<String, Integer> wordCounts = new java.util.HashMap<>();
            int maxCount = 0;
            for (String w : words) {
                if (!w.isBlank()) {
                    int c = wordCounts.getOrDefault(w, 0) + 1;
                    wordCounts.put(w, c);
                    if (c > maxCount) maxCount = c;
                }
            }
            if ((double) maxCount / words.length >= 0.45) {
                return true;
            }
        }

        // 6. Độ đa dạng ký tự (Character Diversity / Entropy) trên văn bản chữ cái
        String cleanLetters = str.replaceAll("[^a-zàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ]", "");
        if (cleanLetters.length() >= 10) {
            long uniqueChars = cleanLetters.chars().distinct().count();
            if (cleanLetters.length() <= 30) {
                double ratio = (double) uniqueChars / cleanLetters.length();
                if (uniqueChars <= 4 || ratio < 0.30) {
                    return true;
                }
            } else {
                if (uniqueChars <= 5) {
                    return true;
                }
            }
        }

        // 7. Regex nhận diện nguyên âm tiếng Việt và tiếng Anh
        String vowelsPattern = ".*[aeiouyáàảãạăằắẳẵặâầấẩẫậéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơờớởỡợúùúủũụưứừửữựýỳỷỹỵ].*";

        // Nếu một từ chữ cái có độ dài >= 4 ký tự nhưng hoàn toàn KHÔNG chứa nguyên âm (bỏ qua số, dấu phân cách)
        for (String w : words) {
            String lettersOnly = w.replaceAll("[^\\p{L}]", "");
            if (lettersOnly.length() >= 4 && !lettersOnly.matches(vowelsPattern)) {
                return true;
            }
        }

        // 8. Chuỗi phụ âm liên tiếp >= 5 ký tự không ngắt quãng (như sdfdsfsdfsd, asdfgh)
        java.util.regex.Pattern consonantRun = java.util.regex.Pattern.compile(
                "[^aeiouyáàảãạăằắẳẵặâầấẩẫậéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùúủũụưứừửữựýỳýỷỹỵ\\s\\d,\\.\\-_/()]{5,}",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        if (consonantRun.matcher(str).find()) {
            return true;
        }

        // 9. Chuỗi tổng thể >= 6 ký tự chữ cái nhưng không có nguyên âm nào
        String lettersAll = str.replaceAll("[^\\p{L}]", "");
        if (lettersAll.length() >= 6 && !lettersAll.matches(vowelsPattern)) {
            return true;
        }

        return false;
    }

    private void validateEventDetails(String name, String description, String venueAddress, Instant startTime, Instant endTime) {
        if (name != null) {
            String trimmedName = name.trim();
            if (trimmedName.length() < 6) {
                throw new BadRequestException("Tên sự kiện quá ngắn, tối thiểu phải từ 6 ký tự.");
            }
            if (isGibberishOrSpam(trimmedName)) {
                throw new BadRequestException("Tên sự kiện không hợp lệ hoặc chứa chuỗi ký tự vô nghĩa.");
            }
        }
        if (description != null && !description.isBlank()) {
            String trimmedDesc = description.trim();
            if (trimmedDesc.length() < 3) {
                throw new BadRequestException("Mô tả quy mô sự kiện quá ngắn (tối thiểu 3 ký tự nếu nhập).");
            }
            if (isGibberishOrSpam(trimmedDesc)) {
                throw new BadRequestException("Mô tả sự kiện không hợp lệ hoặc chứa chuỗi ký tự vô nghĩa.");
            }
        }
        if (startTime != null && endTime != null) {
            if (!endTime.isAfter(startTime)) {
                throw new BadRequestException("Thời gian kết thúc sự kiện phải diễn ra sau thời gian bắt đầu sự kiện.");
            }
            if (java.time.Duration.between(startTime, endTime).toMinutes() < 30) {
                throw new BadRequestException("Thời lượng sự kiện tối thiểu phải từ 30 phút trở lên.");
            }
        }
    }
}
