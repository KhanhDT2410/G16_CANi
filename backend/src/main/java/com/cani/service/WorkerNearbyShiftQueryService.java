package com.cani.service;

import com.cani.dto.worker.NearbyShiftResponseDto;
import com.cani.entity.Event;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Shift;
import com.cani.entity.ShiftAssignment;
import com.cani.entity.ShiftSkillRequirement;
import com.cani.entity.Skill;
import com.cani.entity.Worker;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.KycStatus;
import com.cani.entity.enums.SkillRequirementType;
import com.cani.entity.enums.VerificationStatus;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import com.cani.repository.ShiftSkillRequirementRepository;
import com.cani.repository.SkillRepository;
import com.cani.repository.WorkerAvailabilityRepository;
import com.cani.repository.WorkerRepository;
import com.cani.repository.WorkerSkillRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only Query Service cho tính năng Job Radar (Tìm kiếm Ca làm việc gần vị trí Worker).
 * Tích hợp Bounding-Box Pre-Filter để loại nhanh các tọa độ nằm ngoài vùng chữ nhật
 * [centerLat ± deltaLat, centerLng ± deltaLng] trước khi tính công thức lượng giác Haversine.
 */
@Slf4j
@Service
public class WorkerNearbyShiftQueryService {

    private static final double DEFAULT_CENTER_LAT = 10.7769;
    private static final double DEFAULT_CENTER_LNG = 106.7009;
    private static final double DEFAULT_SEARCH_RADIUS_KM = 15.0;
    private static final double KM_PER_DEGREE_LAT = 111.0;

    @Autowired(required = false)
    private WorkerRepository workerRepository;

    @Autowired(required = false)
    private ShiftRepository shiftRepository;

    @Autowired(required = false)
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired(required = false)
    private ShiftSkillRequirementRepository shiftSkillRequirementRepository;

    @Autowired(required = false)
    private SkillRepository skillRepository;

    @Autowired(required = false)
    private WorkerShiftEligibilityService workerShiftEligibilityService;

    @Autowired(required = false)
    private WorkerSkillRepository workerSkillRepository;

    @Autowired(required = false)
    private WorkerAvailabilityRepository workerAvailabilityRepository;

    @Autowired(required = false)
    private BlockListService blockListService;

    public WorkerNearbyShiftQueryService() {
    }

    public static WorkerNearbyShiftQueryService withFallbackDependencies(
            WorkerRepository workerRepository,
            ShiftRepository shiftRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            ShiftSkillRequirementRepository shiftSkillRequirementRepository,
            SkillRepository skillRepository,
            WorkerShiftEligibilityService workerShiftEligibilityService
    ) {
        return withFallbackDependencies(
                workerRepository,
                shiftRepository,
                shiftAssignmentRepository,
                shiftSkillRequirementRepository,
                skillRepository,
                workerShiftEligibilityService,
                null,
                null,
                null
        );
    }

    public static WorkerNearbyShiftQueryService withFallbackDependencies(
            WorkerRepository workerRepository,
            ShiftRepository shiftRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            ShiftSkillRequirementRepository shiftSkillRequirementRepository,
            SkillRepository skillRepository,
            WorkerShiftEligibilityService workerShiftEligibilityService,
            WorkerSkillRepository workerSkillRepository,
            WorkerAvailabilityRepository workerAvailabilityRepository,
            BlockListService blockListService
    ) {
        WorkerNearbyShiftQueryService service = new WorkerNearbyShiftQueryService();
        service.workerRepository = workerRepository;
        service.shiftRepository = shiftRepository;
        service.shiftAssignmentRepository = shiftAssignmentRepository;
        service.shiftSkillRequirementRepository = shiftSkillRequirementRepository;
        service.skillRepository = skillRepository;
        service.workerShiftEligibilityService = workerShiftEligibilityService;
        service.workerSkillRepository = workerSkillRepository;
        service.workerAvailabilityRepository = workerAvailabilityRepository;
        service.blockListService = blockListService;
        return service;
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

    /**
     * Kiểm tra nhanh tọa độ mục tiêu có nằm trong khung chữ nhật bao quanh tâm tìm kiếm hay không
     * trước khi chạy phép tính Haversine (sin/cos/atan2).
     */
    public static boolean isWithinBoundingBox(
            double centerLat,
            double centerLng,
            double radiusKm,
            double targetLat,
            double targetLng
    ) {
        if (!MatchingDispatchService.isValidCoordinate(centerLat, centerLng)
                || !MatchingDispatchService.isValidCoordinate(targetLat, targetLng)
                || !Double.isFinite(radiusKm) || radiusKm < 0) {
            return false;
        }
        // Biên an toàn nhẹ (1.05x + 0.1km) để không loại nhầm điểm nằm sát rìa đường tròn Haversine
        double paddedRadiusKm = radiusKm * 1.05 + 0.1;
        double deltaLat = paddedRadiusKm / KM_PER_DEGREE_LAT;
        if (Math.abs(targetLat - centerLat) > deltaLat) {
            return false;
        }
        double cosLat = Math.max(0.01, Math.cos(Math.toRadians(centerLat)));
        double deltaLng = paddedRadiusKm / (KM_PER_DEGREE_LAT * cosLat);
        return Math.abs(targetLng - centerLng) <= deltaLng;
    }

    @Transactional(readOnly = true)
    public List<NearbyShiftResponseDto> getNearbyShifts(Double userLat, Double userLng, Double radiusKm) {
        return getNearbyShifts(null, userLat, userLng, radiusKm);
    }

    @Transactional(readOnly = true)
    public List<NearbyShiftResponseDto> getNearbyShifts(UUID userId, Double userLat, Double userLng, Double radiusKm) {
        double searchRadius = (radiusKm != null && radiusKm > 0) ? radiusKm : DEFAULT_SEARCH_RADIUS_KM;
        double centerLat = (userLat != null) ? userLat : DEFAULT_CENTER_LAT;
        double centerLng = (userLng != null) ? userLng : DEFAULT_CENTER_LNG;

        Worker currentWorker = null;
        List<ShiftAssignment> workerActiveAssignments = Collections.emptyList();
        Set<UUID> workerAssignedShiftIds = new HashSet<>();
        if (userId != null && workerRepository != null) {
            currentWorker = workerRepository.findByUser_UserId(userId).orElse(null);
            if (currentWorker != null) {
                if ((radiusKm == null || radiusKm <= 0) && currentWorker.getMaxDistanceKm() != null && currentWorker.getMaxDistanceKm() > 0) {
                    searchRadius = currentWorker.getMaxDistanceKm();
                }
                if (userLat == null && currentWorker.getBaseLat() != null) {
                    centerLat = currentWorker.getBaseLat();
                }
                if (userLng == null && currentWorker.getBaseLng() != null) {
                    centerLng = currentWorker.getBaseLng();
                }

                if (currentWorker.getKycStatus() != KycStatus.APPROVED) {
                    log.info("Worker {} chưa hoàn tất eKYC (KycStatus={}), ẩn danh sách công việc gần đó", userId, currentWorker.getKycStatus());
                    return Collections.emptyList();
                }
                if (!Boolean.TRUE.equals(currentWorker.getIsOnline())) {
                    log.info("Worker {} đang tạm tắt Nhận việc (isOnline=false), ẩn danh sách ca làm việc trên Radar", userId);
                    return Collections.emptyList();
                }
                if (shiftAssignmentRepository != null) {
                    List<ShiftAssignment> loadedActive = shiftAssignmentRepository.findByWorker_WorkerIdAndStatusIn(
                            currentWorker.getWorkerId(),
                            List.of(AssignmentStatus.CONFIRMED, AssignmentStatus.CHECKED_IN, AssignmentStatus.PENDING)
                    );
                    if (loadedActive != null) {
                        workerActiveAssignments = loadedActive;
                        for (ShiftAssignment sa : loadedActive) {
                            if (sa != null && sa.getShift() != null && sa.getShift().getShiftId() != null) {
                                workerAssignedShiftIds.add(sa.getShift().getShiftId());
                            }
                        }
                    }
                    List<UUID> allAssignedShiftIds = shiftAssignmentRepository.findShiftIdsByWorkerId(currentWorker.getWorkerId());
                    if (allAssignedShiftIds != null) {
                        for (UUID assignedShiftId : allAssignedShiftIds) {
                            if (assignedShiftId != null) {
                                workerAssignedShiftIds.add(assignedShiftId);
                            }
                        }
                    }
                }
            }
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        Instant now = Instant.now();
        List<Shift> rawCandidates = shiftRepository != null ? shiftRepository.findNearbyCandidateShifts(now) : Collections.emptyList();
        List<Shift> candidateShifts = rawCandidates != null ? rawCandidates : Collections.emptyList();

        record NearbyShiftCandidate(Shift shift, double distance, Double sLat, Double sLng) {}
        List<NearbyShiftCandidate> matchedCandidates = new ArrayList<>();

        for (Shift s : candidateShifts) {
            if (s.getShiftId() != null && workerAssignedShiftIds.contains(s.getShiftId())) {
                continue;
            }
            if (s.getRemainingWorkers() == null || s.getRemainingWorkers() <= 0) {
                continue;
            }
            if (s.getEndTime() != null && now.isAfter(s.getEndTime())) {
                continue;
            }

            Double sLat = s.getLocationLat() != null ? s.getLocationLat() : (s.getEvent() != null ? s.getEvent().getVenueLat() : null);
            Double sLng = s.getLocationLng() != null ? s.getLocationLng() : (s.getEvent() != null ? s.getEvent().getVenueLng() : null);

            if (sLat == null || sLng == null || !isWithinBoundingBox(centerLat, centerLng, searchRadius, sLat, sLng)) {
                continue;
            }

            double distance = MatchingDispatchService.calculateDistance(centerLat, centerLng, sLat, sLng);
            if (distance <= searchRadius) {
                matchedCandidates.add(new NearbyShiftCandidate(s, distance, sLat, sLng));
            }
        }

        if (matchedCandidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> nearbyShiftIds = matchedCandidates.stream()
                .map(c -> c.shift().getShiftId())
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, List<ShiftSkillRequirement>> skillReqsByShiftId = new HashMap<>();
        if (shiftSkillRequirementRepository != null && !nearbyShiftIds.isEmpty()) {
            List<ShiftSkillRequirement> allReqs = shiftSkillRequirementRepository.findByShiftIdsWithSkill(nearbyShiftIds);
            if (allReqs != null) {
                skillReqsByShiftId = allReqs.stream()
                        .filter(r -> r.getShift() != null && r.getShift().getShiftId() != null)
                        .collect(Collectors.groupingBy(r -> r.getShift().getShiftId()));
            }
        }

        Set<UUID> neededSkillIds = new HashSet<>();
        for (NearbyShiftCandidate c : matchedCandidates) {
            Shift s = c.shift();
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

        List<NearbyShiftResponseDto> nearbyList = new ArrayList<>(matchedCandidates.size());
        WorkerShiftEligibilityService eligibilityService = getEligibilityService();

        WorkerShiftEligibilityService.WorkerEligibilityContext eligibilityContext = null;
        if (currentWorker != null) {
            List<com.cani.entity.WorkerAvailability> preloadedAvailability =
                    workerAvailabilityRepository != null && currentWorker.getWorkerId() != null
                            ? workerAvailabilityRepository.findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(currentWorker.getWorkerId())
                            : null;
            List<com.cani.entity.WorkerSkill> preloadedSkills =
                    workerSkillRepository != null && currentWorker.getWorkerId() != null
                            ? workerSkillRepository.findByWorker_WorkerId(currentWorker.getWorkerId())
                            : null;
            Set<UUID> blockedUserIds =
                    blockListService != null && currentWorker.getUser() != null && currentWorker.getUser().getUserId() != null
                            ? blockListService.getMutuallyBlockedUserIds(currentWorker.getUser().getUserId())
                            : null;
            eligibilityContext = new WorkerShiftEligibilityService.WorkerEligibilityContext(
                    preloadedAvailability,
                    preloadedSkills,
                    blockedUserIds,
                    shiftSkillRequirementRepository != null ? skillReqsByShiftId : null,
                    skillNamesById
            );
        }

        for (NearbyShiftCandidate candidate : matchedCandidates) {
            Shift s = candidate.shift();
            if (currentWorker != null) {
                WorkerShiftEligibilityService.MatchingChannel channel = Boolean.TRUE.equals(s.getEmergency())
                        ? WorkerShiftEligibilityService.MatchingChannel.EMERGENCY_DISPATCH
                        : WorkerShiftEligibilityService.MatchingChannel.REVERSE_MATCHING;
                WorkerShiftEligibilityService.EligibilityResult eligibility = eligibilityService.evaluate(
                        currentWorker, s, channel, null, true, eligibilityContext);
                if (!eligibility.eligible()) {
                    continue;
                }
            }
            double distance = candidate.distance();
            Double sLat = candidate.sLat();
            Double sLng = candidate.sLng();
            Event e = s.getEvent();

            WorkerShiftEligibilityService.ScheduleConflictInfo conflictInfo =
                    eligibilityService.findScheduleConflict(s, workerActiveAssignments);
            boolean isConflicting = conflictInfo.conflicting();
            String conflictReason = conflictInfo.shortReasonVi();

            EventOrganizer eo = e != null ? e.getEventOrganizer() : null;
            String eoName = eo != null && eo.getCompanyName() != null && !eo.getCompanyName().isBlank()
                    ? eo.getCompanyName()
                    : (eo != null && eo.getUser() != null ? eo.getUser().getFullName() : "Ban tổ chức");
            List<ShiftSkillRequirement> preloadedReqs = s.getShiftId() != null
                    ? skillReqsByShiftId.get(s.getShiftId())
                    : null;
            List<WorkerShiftEligibilityService.EffectiveRequirement> effectiveReqs =
                    eligibilityService.resolveEffectiveRequirements(s, preloadedReqs, skillNamesById);
            List<String> reqSkillNames = effectiveReqs.stream()
                    .filter(r -> r.requirementType() == SkillRequirementType.REQUIRED)
                    .map(WorkerShiftEligibilityService.EffectiveRequirement::skillName)
                    .filter(Objects::nonNull)
                    .toList();
            if (reqSkillNames.isEmpty() && s.getRequiredSkills() != null && s.getRequiredSkills().length > 0) {
                reqSkillNames = Arrays.stream(s.getRequiredSkills())
                        .filter(Objects::nonNull)
                        .map(skillNamesById::get)
                        .filter(Objects::nonNull)
                        .toList();
            }
            List<String> prefSkillNames = effectiveReqs.stream()
                    .filter(r -> r.requirementType() == SkillRequirementType.PREFERRED)
                    .map(WorkerShiftEligibilityService.EffectiveRequirement::skillName)
                    .filter(Objects::nonNull)
                    .toList();

            nearbyList.add(NearbyShiftResponseDto.builder()
                    .shiftId(s.getShiftId())
                    .eventId(e != null ? e.getEventId() : null)
                    .eventName(e != null ? e.getName() : "Sự kiện")
                    .eoId(eo != null ? eo.getEoId() : null)
                    .eoName(eoName)
                    .eoVerified(eo != null && eo.getVerificationStatus() == VerificationStatus.APPROVED)
                    .eoRatingAvg(eo != null ? eo.getRatingAvg() : null)
                    .jobType(s.getJobType())
                    .description(s.getDescription())
                    .locationAddress(s.getLocationAddress() != null ? s.getLocationAddress() : (e != null ? e.getVenueAddress() : ""))
                    .locationLat(sLat)
                    .locationLng(sLng)
                    .hourlyWage(s.getHourlyWage())
                    .allowance(s.getAllowance())
                    .startTime(s.getStartTime())
                    .endTime(s.getEndTime())
                    .arrivalTime(s.getArrivalTime() != null ? s.getArrivalTime() : s.getStartTime())
                    .requiredSkills(s.getRequiredSkills())
                    .requiredSkillNames(reqSkillNames)
                    .preferredSkillNames(prefSkillNames)
                    .performedSkillId(s.getPerformedSkillId())
                    .performedSkillName(s.getPerformedSkillId() != null ? skillNamesById.get(s.getPerformedSkillId()) : null)
                    .skillRequirements(effectiveReqs.stream().map(WorkerShiftEligibilityService.EffectiveRequirement::toDto).toList())
                    .customSkillNotes(s.getCustomSkillNotes())
                    .distanceKm(Math.round(distance * 10.0) / 10.0)
                    .requiredWorkers(s.getRequiredWorkers())
                    .confirmedWorkers(s.getConfirmedWorkers())
                    .remainingWorkers(s.getRemainingWorkers())
                    .imageUrl(s.getImageUrl())
                    .isConflicting(isConflicting)
                    .conflictReason(conflictReason)
                    .emergency(Boolean.TRUE.equals(s.getEmergency()))
                    .build());
        }

        nearbyList.sort(Comparator.comparingDouble(NearbyShiftResponseDto::getDistanceKm));
        return nearbyList;
    }
}
