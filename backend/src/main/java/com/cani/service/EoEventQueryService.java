package com.cani.service;

import com.cani.dto.eo.EoEventPageResponseDto;
import com.cani.dto.event.EventResponseDto;
import com.cani.dto.event.ShiftResponseDto;
import com.cani.dto.event.ShiftSkillRequirementDto;
import com.cani.entity.Event;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Payment;
import com.cani.entity.Shift;
import com.cani.entity.ShiftSkillRequirement;
import com.cani.entity.Skill;
import com.cani.entity.User;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.PaymentStatus;
import com.cani.entity.enums.ProposalStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.SkillRequirementType;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.VerificationTier;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.DispatchProposalRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.EventRepository;
import com.cani.repository.PaymentRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import com.cani.repository.ShiftSkillRequirementRepository;
import com.cani.repository.SkillRepository;
import com.cani.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only Query Service cho danh sách Sự kiện, Phân trang Sự kiện và Chi tiết Sự kiện của EO.
 * Sử dụng kỹ thuật Batch-fetching O(1) queries để triệt tiêu N+1 queries trên Shifts, Assignments,
 * Proposals, Payments và Skill Requirements.
 */
@Slf4j
@Service
public class EoEventQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired(required = false)
    private EventOrganizerRepository eventOrganizerRepository;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private EventRepository eventRepository;

    @Autowired(required = false)
    private ShiftRepository shiftRepository;

    @Autowired(required = false)
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired(required = false)
    private PaymentRepository paymentRepository;

    @Autowired(required = false)
    private DispatchProposalRepository dispatchProposalRepository;

    @Autowired(required = false)
    private ShiftSkillRequirementRepository shiftSkillRequirementRepository;

    @Autowired(required = false)
    private SkillRepository skillRepository;

    @Autowired(required = false)
    private ShiftStaffingPolicy shiftStaffingPolicy;

    public EoEventQueryService() {
    }

    public static EoEventQueryService withFallbackDependencies(
            EventOrganizerRepository eventOrganizerRepository,
            UserRepository userRepository,
            EventRepository eventRepository,
            ShiftRepository shiftRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            PaymentRepository paymentRepository,
            DispatchProposalRepository dispatchProposalRepository,
            ShiftSkillRequirementRepository shiftSkillRequirementRepository,
            SkillRepository skillRepository,
            ShiftStaffingPolicy shiftStaffingPolicy
    ) {
        EoEventQueryService service = new EoEventQueryService();
        service.eventOrganizerRepository = eventOrganizerRepository;
        service.userRepository = userRepository;
        service.eventRepository = eventRepository;
        service.shiftRepository = shiftRepository;
        service.shiftAssignmentRepository = shiftAssignmentRepository;
        service.paymentRepository = paymentRepository;
        service.dispatchProposalRepository = dispatchProposalRepository;
        service.shiftSkillRequirementRepository = shiftSkillRequirementRepository;
        service.skillRepository = skillRepository;
        service.shiftStaffingPolicy = shiftStaffingPolicy;
        return service;
    }

    private ShiftStaffingPolicy getStaffingPolicy() {
        if (shiftStaffingPolicy != null) {
            return shiftStaffingPolicy;
        }
        return ShiftStaffingPolicy.withFallbackDependencies(
                shiftAssignmentRepository,
                dispatchProposalRepository
        );
    }

    /**
     * Danh sách tất cả các sự kiện của EO (Batch-fetched O(1) queries)
     */
    @Transactional(readOnly = true)
    public List<EventResponseDto> getMyEvents(UUID userId) {
        EventOrganizer eo = getEoOrThrow(userId);
        List<Event> events = eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eo.getEoId());
        return mapEventsToResponseDtos(events);
    }

    /**
     * Danh sách sự kiện của EO có phân trang (Batch-fetched O(1) queries)
     */
    @Transactional(readOnly = true)
    public EoEventPageResponseDto getMyEventsPage(UUID userId, int page, int size) {
        EventOrganizer eo = getEoOrThrow(userId);
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Event> eventPage = eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eo.getEoId(),
                pageable
        );
        if (eventPage == null) {
            return EoEventPageResponseDto.builder()
                    .items(Collections.emptyList())
                    .page(safePage)
                    .size(safeSize)
                    .totalElements(0L)
                    .totalPages(0)
                    .hasNext(false)
                    .hasPrevious(safePage > 0)
                    .build();
        }

        List<EventResponseDto> items = mapEventsToResponseDtos(eventPage.getContent());
        return EoEventPageResponseDto.builder()
                .items(items)
                .page(eventPage.getNumber())
                .size(eventPage.getSize())
                .totalElements(eventPage.getTotalElements())
                .totalPages(eventPage.getTotalPages())
                .hasNext(eventPage.hasNext())
                .hasPrevious(eventPage.hasPrevious())
                .build();
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

    public record ShiftBatchLookupContext(
            Map<UUID, ShiftAssignmentRepository.ShiftAssignmentCountProjection> assignmentCountsByShiftId,
            Map<UUID, Integer> pendingProposalsByShiftId,
            Map<UUID, List<ShiftSkillRequirement>> skillRequirementsByShiftId,
            Map<UUID, String> skillNamesById
    ) {
        public static ShiftBatchLookupContext empty() {
            return new ShiftBatchLookupContext(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap()
            );
        }
    }

    public List<EventResponseDto> mapEventsToResponseDtos(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> eventIds = events.stream()
                .map(Event::getEventId)
                .filter(Objects::nonNull)
                .toList();

        List<Shift> allShifts = (!eventIds.isEmpty() && shiftRepository != null)
                ? shiftRepository.findByEvent_EventIdIn(eventIds)
                : Collections.emptyList();
        if (allShifts == null) {
            allShifts = Collections.emptyList();
        }

        Map<UUID, List<Shift>> shiftsByEventId = allShifts.stream()
                .filter(s -> s.getEvent() != null && s.getEvent().getEventId() != null)
                .collect(Collectors.groupingBy(s -> s.getEvent().getEventId()));

        List<UUID> zeroPaidEventIds = events.stream()
                .filter(e -> e.getEventId() != null
                        && (e.getTotalPaid() == null || e.getTotalPaid().compareTo(BigDecimal.ZERO) == 0))
                .map(Event::getEventId)
                .toList();
        Map<UUID, List<Payment>> paymentsByEventId = new HashMap<>();
        if (!zeroPaidEventIds.isEmpty() && paymentRepository != null) {
            try {
                List<Payment> batchPayments = paymentRepository.findByEventIdIn(zeroPaidEventIds);
                if (batchPayments != null) {
                    paymentsByEventId = batchPayments.stream()
                            .filter(p -> p.getEventId() != null)
                            .collect(Collectors.groupingBy(Payment::getEventId));
                }
            } catch (Exception ignored) {
            }
        }

        ShiftBatchLookupContext shiftContext = buildShiftBatchLookupContext(allShifts);
        Instant now = Instant.now();

        List<EventResponseDto> dtos = new ArrayList<>(events.size());
        for (Event e : events) {
            List<Shift> eventShifts = e.getEventId() != null
                    ? shiftsByEventId.getOrDefault(e.getEventId(), Collections.emptyList())
                    : Collections.emptyList();
            List<Payment> preloadedPayments = e.getEventId() != null
                    ? paymentsByEventId.get(e.getEventId())
                    : null;
            dtos.add(mapToEventResponseDto(e, eventShifts, preloadedPayments, true, shiftContext, now));
        }
        return dtos;
    }

    public ShiftBatchLookupContext buildShiftBatchLookupContext(List<Shift> shifts) {
        if (shifts == null || shifts.isEmpty()) {
            return ShiftBatchLookupContext.empty();
        }
        List<UUID> shiftIds = shifts.stream()
                .map(Shift::getShiftId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (shiftIds.isEmpty()) {
            return ShiftBatchLookupContext.empty();
        }

        Map<UUID, ShiftAssignmentRepository.ShiftAssignmentCountProjection> assignmentCounts = new HashMap<>();
        if (shiftAssignmentRepository != null) {
            List<ShiftAssignmentRepository.ShiftAssignmentCountProjection> rows =
                    shiftAssignmentRepository.countAssignmentsByShiftIds(shiftIds, ShiftStaffingPolicy.VALID_CONFIRMED_STATUSES);
            if (rows != null) {
                for (ShiftAssignmentRepository.ShiftAssignmentCountProjection row : rows) {
                    if (row != null && row.getShiftId() != null) {
                        assignmentCounts.put(row.getShiftId(), row);
                    }
                }
            }
        }

        Map<UUID, Integer> pendingProposals = new HashMap<>();
        if (dispatchProposalRepository != null) {
            List<DispatchProposalRepository.ShiftProposalCountProjection> rows =
                    dispatchProposalRepository.countProposalsByShiftIdsAndStatusIn(
                            shiftIds,
                            List.of(ProposalStatus.PENDING)
                    );
            if (rows != null) {
                for (DispatchProposalRepository.ShiftProposalCountProjection row : rows) {
                    if (row != null && row.getShiftId() != null) {
                        pendingProposals.put(row.getShiftId(), (int) Math.max(0L, row.getProposalCount()));
                    }
                }
            }
        }

        Map<UUID, List<ShiftSkillRequirement>> skillReqsByShiftId = new HashMap<>();
        if (shiftSkillRequirementRepository != null) {
            List<ShiftSkillRequirement> reqRows = shiftSkillRequirementRepository.findByShiftIdsWithSkill(shiftIds);
            if (reqRows != null) {
                skillReqsByShiftId = reqRows.stream()
                        .filter(r -> r.getShift() != null && r.getShift().getShiftId() != null)
                        .collect(Collectors.groupingBy(r -> r.getShift().getShiftId()));
            }
        }

        Set<UUID> neededSkillIds = new LinkedHashSet<>();
        for (Shift s : shifts) {
            if (s.getPerformedSkillId() != null) {
                neededSkillIds.add(s.getPerformedSkillId());
            }
            List<ShiftSkillRequirement> preloadedReqs = s.getShiftId() != null
                    ? skillReqsByShiftId.get(s.getShiftId())
                    : null;
            if ((preloadedReqs == null || preloadedReqs.isEmpty()) && s.getRequiredSkills() != null) {
                for (UUID skId : s.getRequiredSkills()) {
                    if (skId != null) {
                        neededSkillIds.add(skId);
                    }
                }
            }
        }

        Map<UUID, String> skillNamesById = new HashMap<>();
        if (skillRepository != null && !neededSkillIds.isEmpty()) {
            List<Skill> loadedSkills = skillRepository.findAllById(neededSkillIds);
            if (loadedSkills != null) {
                for (Skill sk : loadedSkills) {
                    if (sk != null && sk.getSkillId() != null && sk.getName() != null) {
                        skillNamesById.put(sk.getSkillId(), sk.getName());
                    }
                }
            }
        }

        return new ShiftBatchLookupContext(
                assignmentCounts,
                pendingProposals,
                skillReqsByShiftId,
                skillNamesById
        );
    }

    public EventResponseDto mapToEventResponseDto(Event e, List<Shift> shifts) {
        List<Shift> safeShifts = shifts != null ? shifts : Collections.emptyList();
        ShiftBatchLookupContext shiftContext = buildShiftBatchLookupContext(safeShifts);
        return mapToEventResponseDto(e, safeShifts, null, false, shiftContext, Instant.now());
    }

    public EventResponseDto mapToEventResponseDto(
            Event e,
            List<Shift> shifts,
            List<Payment> preloadedPayments,
            boolean paymentsPreloaded,
            ShiftBatchLookupContext shiftContext,
            Instant now
    ) {
        List<Shift> safeShifts = shifts != null ? shifts : Collections.emptyList();
        BigDecimal effectiveTotalPaid = e.getTotalPaid();
        if ((effectiveTotalPaid == null || effectiveTotalPaid.compareTo(BigDecimal.ZERO) == 0)
                && e.getEventId() != null && paymentRepository != null) {
            try {
                List<Payment> eventPayments = paymentsPreloaded
                        ? preloadedPayments
                        : paymentRepository.findByEventId(e.getEventId());
                if (eventPayments != null && !eventPayments.isEmpty()) {
                    effectiveTotalPaid = eventPayments.stream()
                            .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED_FULL
                                    || p.getStatus() == PaymentStatus.CONFIRMED_PARTIAL
                                    || p.getStatus() == PaymentStatus.DECLARED_PENDING_WORKER)
                            .map(p -> p.getDeclaredAmount() != null ? p.getDeclaredAmount() : p.getAgreedAmount())
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                }
            } catch (Exception ignored) {
            }
        }

        EventStatus resolvedEventStatus = e.getStatus();
        if (resolvedEventStatus != EventStatus.CANCELLED && resolvedEventStatus != EventStatus.DRAFT) {
            boolean allShiftsCompleted = !safeShifts.isEmpty() && safeShifts.stream()
                    .allMatch(s -> s.getStatus() == ShiftStatus.COMPLETED || s.getStatus() == ShiftStatus.CANCELLED);
            if ((e.getEndTime() != null && now.isAfter(e.getEndTime())) || allShiftsCompleted) {
                resolvedEventStatus = EventStatus.COMPLETED;
            } else if (e.getStartTime() != null && !now.isBefore(e.getStartTime())) {
                resolvedEventStatus = EventStatus.ONGOING;
            }
        }

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
                .status(resolvedEventStatus)
                .readinessScore(e.getReadinessScore())
                .totalShifts(e.getTotalShifts())
                .totalRequiredWorkers(e.getTotalRequiredWorkers())
                .totalConfirmedWorkers(e.getTotalConfirmedWorkers())
                .totalEstimatedCost(e.getTotalEstimatedCost())
                .totalPaid(effectiveTotalPaid != null ? effectiveTotalPaid : BigDecimal.ZERO)
                .createdAt(e.getCreatedAt())
                .shifts(safeShifts.stream()
                        .map(s -> mapToShiftResponseDto(s, shiftContext, now))
                        .collect(Collectors.toList()))
                .build();
    }

    public ShiftResponseDto mapToShiftResponseDto(Shift s) {
        return mapToShiftResponseDto(s, null, Instant.now());
    }

    public ShiftResponseDto mapToShiftResponseDto(
            Shift s,
            ShiftBatchLookupContext batchContext,
            Instant now
    ) {
        ShiftStaffingPolicy policy = getStaffingPolicy();
        ShiftStaffingPolicy.EmergencyEligibility eligibility;
        if (batchContext != null && s.getShiftId() != null) {
            ShiftAssignmentRepository.ShiftAssignmentCountProjection countProj =
                    batchContext.assignmentCountsByShiftId().get(s.getShiftId());
            int confirmedWorkers = (countProj != null && countProj.getTotalCount() > 0)
                    ? (int) Math.max(0L, countProj.getConfirmedCount())
                    : Math.max(0, s.getConfirmedWorkers() != null ? s.getConfirmedWorkers() : 0);
            int pendingProposals = batchContext.pendingProposalsByShiftId().getOrDefault(s.getShiftId(), 0);
            eligibility = policy.evaluateEmergencyEligibility(s, now, confirmedWorkers, pendingProposals);
        } else {
            eligibility = policy.evaluateEmergencyEligibility(s, now);
        }
        ShiftStatus resolvedStatus = eligibility.effectiveStatus();

        List<ShiftSkillRequirementDto> reqDtos = batchContext != null
                ? resolveShiftSkillRequirementDtos(s, batchContext)
                : resolveShiftSkillRequirementDtos(s);
        String performedSkillName = batchContext != null
                ? (s.getPerformedSkillId() != null ? batchContext.skillNamesById().get(s.getPerformedSkillId()) : null)
                : resolveSkillNameById(s.getPerformedSkillId());

        return ShiftResponseDto.builder()
                .shiftId(s.getShiftId())
                .eventId(s.getEvent() != null ? s.getEvent().getEventId() : null)
                .jobType(s.getJobType())
                .description(s.getDescription())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .arrivalTime(s.getArrivalTime() != null ? s.getArrivalTime() : s.getStartTime())
                .requiredWorkers(eligibility.requiredWorkers())
                .confirmedWorkers(eligibility.confirmedWorkers())
                .remainingWorkers(eligibility.missingWorkers())
                .hourlyWage(s.getHourlyWage())
                .allowance(s.getAllowance())
                .totalCost(s.getTotalCost())
                .status(resolvedStatus)
                .riskScore(s.getRiskScore())
                .locationAddress(s.getLocationAddress())
                .locationLat(s.getLocationLat())
                .locationLng(s.getLocationLng())
                .minReliabilityScore(s.getMinReliabilityScore())
                .minExperienceLevel(s.getMinExperienceLevel())
                .dressCode(s.getDressCode())
                .imageUrl(s.getImageUrl())
                .checklist(s.getChecklist())
                .requiredSkills(s.getRequiredSkills())
                .performedSkillId(s.getPerformedSkillId())
                .performedSkillName(performedSkillName)
                .skillRequirements(reqDtos)
                .customSkillNotes(s.getCustomSkillNotes())
                .emergency(Boolean.TRUE.equals(s.getEmergency()) && eligibility.missingWorkers() > 0)
                .missingWorkers(eligibility.missingWorkers())
                .nearStartUnderstaffed(eligibility.nearStartUnderstaffed())
                .canTriggerEmergency(eligibility.canTriggerEmergency())
                .emergencyBlockReason(eligibility.blockReason())
                .pendingProposalCount(eligibility.pendingProposalCount())
                .defaultEmergencyRadiusKm(eligibility.defaultEmergencyRadiusKm())
                .build();
    }

    private List<ShiftSkillRequirementDto> resolveShiftSkillRequirementDtos(Shift s, ShiftBatchLookupContext batchContext) {
        if (s == null) {
            return Collections.emptyList();
        }
        List<ShiftSkillRequirement> rows = (batchContext != null && s.getShiftId() != null)
                ? batchContext.skillRequirementsByShiftId().get(s.getShiftId())
                : null;
        if (rows != null && !rows.isEmpty()) {
            return rows.stream()
                    .map(r -> ShiftSkillRequirementDto.builder()
                            .skillId(r.getSkill() != null ? r.getSkill().getSkillId() : null)
                            .skillName(r.getSkill() != null ? r.getSkill().getName() : null)
                            .requirementType(r.getRequirementType())
                            .minVerificationTier(r.getMinVerificationTier())
                            .requiresValidCertificate(Boolean.TRUE.equals(r.getRequiresValidCertificate()))
                            .build())
                    .toList();
        }
        if (s.getRequiredSkills() != null && s.getRequiredSkills().length > 0) {
            Map<UUID, String> names = batchContext != null ? batchContext.skillNamesById() : Collections.emptyMap();
            return Arrays.stream(s.getRequiredSkills())
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(id -> ShiftSkillRequirementDto.builder()
                            .skillId(id)
                            .skillName(names.get(id))
                            .requirementType(SkillRequirementType.REQUIRED)
                            .minVerificationTier(VerificationTier.SELF_DECLARED)
                            .requiresValidCertificate(false)
                            .build())
                    .toList();
        }
        return Collections.emptyList();
    }

    private List<ShiftSkillRequirementDto> resolveShiftSkillRequirementDtos(Shift s) {
        if (s == null) {
            return Collections.emptyList();
        }
        if (shiftSkillRequirementRepository != null && s.getShiftId() != null) {
            List<ShiftSkillRequirement> rows = shiftSkillRequirementRepository.findByShiftIdWithSkill(s.getShiftId());
            if (rows != null && !rows.isEmpty()) {
                return rows.stream()
                        .map(r -> ShiftSkillRequirementDto.builder()
                                .skillId(r.getSkill() != null ? r.getSkill().getSkillId() : null)
                                .skillName(r.getSkill() != null ? r.getSkill().getName() : null)
                                .requirementType(r.getRequirementType())
                                .minVerificationTier(r.getMinVerificationTier())
                                .requiresValidCertificate(Boolean.TRUE.equals(r.getRequiresValidCertificate()))
                                .build())
                        .toList();
            }
        }
        if (s.getRequiredSkills() != null && s.getRequiredSkills().length > 0) {
            List<UUID> ids = Arrays.stream(s.getRequiredSkills()).filter(Objects::nonNull).toList();
            Map<UUID, String> names = new HashMap<>();
            if (skillRepository != null && !ids.isEmpty()) {
                for (Skill sk : skillRepository.findAllById(ids)) {
                    names.put(sk.getSkillId(), sk.getName());
                }
            }
            return ids.stream()
                    .distinct()
                    .map(id -> ShiftSkillRequirementDto.builder()
                            .skillId(id)
                            .skillName(names.get(id))
                            .requirementType(SkillRequirementType.REQUIRED)
                            .minVerificationTier(VerificationTier.SELF_DECLARED)
                            .requiresValidCertificate(false)
                            .build())
                    .toList();
        }
        return Collections.emptyList();
    }

    private String resolveSkillNameById(UUID skillId) {
        if (skillId == null || skillRepository == null) {
            return null;
        }
        return skillRepository.findById(skillId).map(Skill::getName).orElse(null);
    }

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
}
