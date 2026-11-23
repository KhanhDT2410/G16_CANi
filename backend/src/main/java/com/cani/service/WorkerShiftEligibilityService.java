package com.cani.service;

import com.cani.dto.event.ShiftSkillRequirementDto;
import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.repository.*;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Nguồn sự thật duy nhất (Single Source of Truth) kiểm tra điều kiện hợp lệ (Eligibility)
 * và chấm điểm phù hợp kỹ năng (SkillFitBonus) trên toàn bộ 6 kênh:
 * Matching Dispatch, Reverse Matching, Emergency Dispatch, Proposal Acceptance,
 * Emergency Direct Apply, và Invite Mode.
 */
@Slf4j
@Service
@NoArgsConstructor
public class WorkerShiftEligibilityService {

    private static final int DEFAULT_MIN_RELIABILITY = 60;
    private static final double WEIGHT_REQUIRED_QUALITY = 6.0;
    private static final double WEIGHT_PREFERRED_FIT = 4.0;
    private static final DateTimeFormatter CONFLICT_FMT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Autowired(required = false)
    private WorkerSkillRepository workerSkillRepository;

    @Autowired(required = false)
    private ShiftSkillRequirementRepository shiftSkillRequirementRepository;

    @Autowired(required = false)
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired(required = false)
    private WorkerAvailabilityRepository workerAvailabilityRepository;

    @Autowired(required = false)
    private BlockListService blockListService;

    @Autowired(required = false)
    private SkillRepository skillRepository;

    public WorkerShiftEligibilityService(
            WorkerSkillRepository workerSkillRepository,
            ShiftSkillRequirementRepository shiftSkillRequirementRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            WorkerAvailabilityRepository workerAvailabilityRepository,
            BlockListService blockListService,
            SkillRepository skillRepository
    ) {
        this.workerSkillRepository = workerSkillRepository;
        this.shiftSkillRequirementRepository = shiftSkillRequirementRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.workerAvailabilityRepository = workerAvailabilityRepository;
        this.blockListService = blockListService;
        this.skillRepository = skillRepository;
    }

    public static WorkerShiftEligibilityService withFallbackDependencies(
            WorkerSkillRepository workerSkillRepository,
            ShiftSkillRequirementRepository shiftSkillRequirementRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            WorkerAvailabilityRepository workerAvailabilityRepository,
            BlockListService blockListService,
            SkillRepository skillRepository
    ) {
        return new WorkerShiftEligibilityService(
                workerSkillRepository,
                shiftSkillRequirementRepository,
                shiftAssignmentRepository,
                workerAvailabilityRepository,
                blockListService,
                skillRepository
        );
    }

    public enum MatchingChannel {
        MATCHING_DISPATCH,
        REVERSE_MATCHING,
        EMERGENCY_DISPATCH,
        PROPOSAL_ACCEPTANCE,
        EMERGENCY_APPLY,
        INVITE_MODE
    }

    public record EligibilityResult(
            boolean eligible,
            String reasonCode,
            String reasonMessageVi,
            boolean overrideApplied
    ) {
        public static EligibilityResult pass() {
            return new EligibilityResult(true, "ELIGIBLE", null, false);
        }

        public static EligibilityResult passWithOverride(String reasonCode) {
            return new EligibilityResult(true, reasonCode, null, true);
        }

        public static EligibilityResult fail(String code, String messageVi) {
            return new EligibilityResult(false, code, messageVi, false);
        }
    }

    public record EffectiveRequirement(
            UUID skillId,
            String skillName,
            SkillRequirementType requirementType,
            VerificationTier minVerificationTier,
            boolean requiresValidCertificate
    ) {
        public ShiftSkillRequirementDto toDto() {
            return ShiftSkillRequirementDto.builder()
                    .skillId(skillId)
                    .skillName(skillName)
                    .requirementType(requirementType)
                    .minVerificationTier(minVerificationTier)
                    .requiresValidCertificate(requiresValidCertificate)
                    .build();
        }
    }

    public record SkillFitResult(
            double requiredQuality,
            double preferredFit,
            double skillFitBonus,
            List<UUID> matchedSkillIds,
            List<String> matchedSkillNames,
            List<String> requiredSkillNames,
            List<String> preferredSkillNames
    ) {
        public static SkillFitResult empty() {
            return new SkillFitResult(0.0, 0.0, 0.0, List.of(), List.of(), List.of(), List.of());
        }
    }

    public EligibilityResult evaluate(Worker worker, Shift shift, MatchingChannel channel) {
        return evaluate(worker, shift, channel, null);
    }

    public EligibilityResult evaluate(
            Worker worker,
            Shift shift,
            MatchingChannel channel,
            String inviteOverrideReason
    ) {
        if (worker == null || shift == null || channel == null) {
            return EligibilityResult.fail("INVALID_INPUT", "Thông tin nhân sự hoặc ca làm việc không hợp lệ.");
        }

        // 1. Tài khoản User & eKYC bắt buộc trên MỌI kênh (không bao giờ bypass)
        if (worker.getUser() == null || worker.getUser().getStatus() != UserStatus.ACTIVE) {
            return EligibilityResult.fail("USER_INACTIVE",
                    "Tài khoản của bạn hiện không ở trạng thái hoạt động (ACTIVE), không thể nhận việc.");
        }
        if (worker.getKycStatus() != KycStatus.APPROVED) {
            return EligibilityResult.fail("EKYC_REQUIRED",
                    "Tài khoản chưa hoàn tất định danh eKYC. Vui lòng xác thực CCCD trước khi tham gia ca làm việc.");
        }

        boolean isAutomaticMatching = channel == MatchingChannel.MATCHING_DISPATCH
                || channel == MatchingChannel.REVERSE_MATCHING
                || channel == MatchingChannel.EMERGENCY_DISPATCH;

        // 2. Trạng thái Ca & Sự kiện
        if (channel == MatchingChannel.INVITE_MODE) {
            if (shift.getStatus() == ShiftStatus.CANCELLED || shift.getStatus() == ShiftStatus.COMPLETED
                    || shift.getStatus() == ShiftStatus.DRAFT) {
                return EligibilityResult.fail("SHIFT_NOT_AVAILABLE",
                        "Ca làm việc này hiện không còn mở đăng ký qua mã mời.");
            }
        } else if (channel == MatchingChannel.EMERGENCY_DISPATCH
                || channel == MatchingChannel.EMERGENCY_APPLY
                || channel == MatchingChannel.PROPOSAL_ACCEPTANCE) {
            boolean inProgressEmergency = shift.getStatus() == ShiftStatus.IN_PROGRESS
                    && (channel == MatchingChannel.EMERGENCY_DISPATCH || Boolean.TRUE.equals(shift.getEmergency()));
            if (shift.getStatus() != ShiftStatus.ACTIVE && !inProgressEmergency) {
                return EligibilityResult.fail("SHIFT_NOT_ACTIVE",
                        "Ca làm việc này hiện không còn mở tuyển nhân sự (Trạng thái: " + shift.getStatus() + ").");
            }
        } else {
            if (shift.getStatus() != ShiftStatus.ACTIVE) {
                return EligibilityResult.fail("SHIFT_NOT_ACTIVE",
                        "Ca làm việc này hiện không còn mở tuyển nhân sự (Trạng thái: " + shift.getStatus() + ").");
            }
        }
        if (shift.getEndTime() != null && Instant.now().isAfter(shift.getEndTime())) {
            return EligibilityResult.fail("SHIFT_ENDED",
                    "Ca làm việc này đã kết thúc, không thể tham gia.");
        }
        if (channel == MatchingChannel.EMERGENCY_APPLY && !Boolean.TRUE.equals(shift.getEmergency())) {
            return EligibilityResult.fail("NOT_EMERGENCY_SHIFT",
                    "Ca này chưa ở chế độ khẩn cấp. Ứng tuyển trực tiếp chỉ mở khi Ban tổ chức kích hoạt điều phối khẩn cấp — hãy chờ lời mời việc (matching) hoặc quay lại sau khi ca được mở rộng phạm vi tuyển dụng.");
        }

        Event shiftEvent = shift.getEvent();
        if (shiftEvent != null && shiftEvent.getStatus() != null) {
            if (isAutomaticMatching) {
                if (shiftEvent.getStatus() == EventStatus.CANCELLED
                        || shiftEvent.getStatus() == EventStatus.COMPLETED
                        || shiftEvent.getStatus() == EventStatus.SUSPENDED) {
                    return EligibilityResult.fail("EVENT_NOT_ACTIVE",
                            "Sự kiện của ca làm việc này không còn hoạt động (Trạng thái: " + shiftEvent.getStatus() + ").");
                }
            } else if (shiftEvent.getStatus() != EventStatus.ACTIVE
                    && shiftEvent.getStatus() != EventStatus.ONGOING) {
                return EligibilityResult.fail("EVENT_NOT_ACTIVE",
                        "Sự kiện của ca làm việc này không còn hoạt động (Trạng thái: " + shiftEvent.getStatus() + ").");
            }
        }

        // 3. Danh sách chặn hai chiều (không bao giờ bypass)
        if (blockListService != null && shiftEvent != null && shiftEvent.getEventOrganizer() != null) {
            EventOrganizer eo = shiftEvent.getEventOrganizer();
            if (eo.getUser() != null && eo.getUser().getUserId() != null && worker.getUser().getUserId() != null) {
                if (blockListService.isEitherBlocked(eo.getUser().getUserId(), worker.getUser().getUserId())) {
                    return EligibilityResult.fail("BLOCKED",
                            "Không thể nhận ca do tồn tại hạn chế tương tác với Ban tổ chức sự kiện.");
                }
            }
        }

        // 4. Xung đột lịch làm việc (không bao giờ bypass)
        if (shiftAssignmentRepository != null && worker.getWorkerId() != null) {
            List<AssignmentStatus> activeStatuses = List.of(
                    AssignmentStatus.CONFIRMED,
                    AssignmentStatus.CHECKED_IN,
                    AssignmentStatus.PENDING
            );
            List<ShiftAssignment> conflicts = shiftAssignmentRepository.findConflictingAssignments(
                    worker.getWorkerId(), shift.getStartTime(), shift.getEndTime(), activeStatuses);
            if (conflicts != null) {
                for (ShiftAssignment conflict : conflicts) {
                    if (conflict.getShift() != null && Objects.equals(conflict.getShift().getShiftId(), shift.getShiftId())) {
                        continue;
                    }
                    Shift cShift = conflict.getShift();
                    String cJob = cShift != null ? cShift.getJobType() : "Ca làm việc";
                    String cEvent = (cShift != null && cShift.getEvent() != null) ? cShift.getEvent().getName() : "";
                    String timeRange = (cShift != null && cShift.getStartTime() != null && cShift.getEndTime() != null)
                            ? CONFLICT_FMT.format(cShift.getStartTime()) + " - " + CONFLICT_FMT.format(cShift.getEndTime())
                            : "";
                    return EligibilityResult.fail("SCHEDULE_CONFLICT", String.format(
                            "Không thể nhận ca: Bạn đã có ca làm việc khác trùng khung giờ (\"%s\" - %s, %s). Mỗi thời điểm chỉ được làm 1 ca duy nhất!",
                            cJob, cEvent, timeRange));
                }
            }
        }

        // 5. Điều kiện riêng cho kênh Matching tự động (Online, MinWage, Availability)
        if (isAutomaticMatching) {
            if (!Boolean.TRUE.equals(worker.getIsOnline())) {
                return EligibilityResult.fail("WORKER_OFFLINE", "Nhân sự hiện đang ngoại tuyến.");
            }
            if (worker.getMinWage() != null && worker.getMinWage().compareTo(BigDecimal.ZERO) > 0) {
                if (shift.getHourlyWage() == null || shift.getHourlyWage().compareTo(worker.getMinWage()) < 0) {
                    return EligibilityResult.fail("MIN_WAGE_NOT_MET",
                            "Mức lương của ca thấp hơn mức mong muốn tối thiểu của nhân sự.");
                }
            }
            if (!isShiftCoveredByAvailability(worker, shift)) {
                return EligibilityResult.fail("AVAILABILITY_MISMATCH",
                        "Khung giờ ca làm không nằm trong lịch rảnh hàng tuần của nhân sự.");
            }
        }

        // 6. Điều kiện Kỹ năng Bắt buộc, Mức Xác Minh tối thiểu & Chứng chỉ an toàn còn hiệu lực
        List<EffectiveRequirement> allReqs = resolveEffectiveRequirements(shift);
        List<EffectiveRequirement> requiredReqs = allReqs.stream()
                .filter(r -> r.requirementType() == SkillRequirementType.REQUIRED)
                .toList();

        boolean hasOverrideReason = inviteOverrideReason != null && !inviteOverrideReason.trim().isBlank();
        boolean usedOverride = false;

        if (!requiredReqs.isEmpty()) {
            if (workerSkillRepository == null) {
                log.error("Fail-closed eligibility check: thiếu WorkerSkillRepository khi kiểm tra ca {}", shift.getShiftId());
                return EligibilityResult.fail("SKILL_DATA_UNAVAILABLE",
                        "Hệ thống tạm thời chưa xác minh được hồ sơ kỹ năng của bạn.");
            }
            List<WorkerSkill> workerSkills = workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId());
            if (workerSkills == null) {
                return EligibilityResult.fail("SKILL_DATA_UNAVAILABLE",
                        "Không thể truy xuất dữ liệu kỹ năng của nhân sự.");
            }

            Instant now = Instant.now();
            Map<UUID, WorkerSkill> skillMap = indexWorkerSkillsBySkillId(workerSkills);

            for (EffectiveRequirement req : requiredReqs) {
                WorkerSkill ws = skillMap.get(req.skillId());
                String displaySkillName = resolveDisplaySkillName(req);

                if (req.requiresValidCertificate()) {
                    // Chứng chỉ bắt buộc / an toàn: KHÔNG BAO GIỜ cho phép bypass kể cả Invite Mode
                    if (!hasValidCertificate(ws, now) || !meetsVerificationTier(ws, req.minVerificationTier(), now)) {
                        return EligibilityResult.fail("CERTIFICATE_REQUIRED_OR_EXPIRED",
                                "Ca làm việc yêu cầu chứng chỉ còn hiệu lực cho kỹ năng \"" + displaySkillName + "\".");
                    }
                }

                boolean hasSkill = isWorkerSkillActiveAndUsable(ws, now);
                boolean meetsTier = hasSkill && meetsVerificationTier(ws, req.minVerificationTier(), now);

                if (!hasSkill) {
                    if (channel == MatchingChannel.INVITE_MODE && hasOverrideReason) {
                        usedOverride = true;
                    } else {
                        return EligibilityResult.fail("MISSING_REQUIRED_SKILL",
                                "Hồ sơ của bạn chưa có hoặc đã hết hạn kỹ năng bắt buộc: \"" + displaySkillName + "\".");
                    }
                } else if (!meetsTier) {
                    if (channel == MatchingChannel.INVITE_MODE && hasOverrideReason) {
                        usedOverride = true;
                    } else {
                        return EligibilityResult.fail("INSUFFICIENT_VERIFICATION_TIER",
                                "Kỹ năng \"" + displaySkillName + "\" cần đạt mức xác minh tối thiểu: "
                                        + toTierLabelVi(req.minVerificationTier()) + ".");
                    }
                }
            }
        }

        // 7. Điểm uy tín (RS) & Kinh nghiệm tối thiểu
        int minRs = (shift.getMinReliabilityScore() != null && shift.getMinReliabilityScore() > 0)
                ? shift.getMinReliabilityScore() : DEFAULT_MIN_RELIABILITY;
        if (worker.getReliabilityScore() == null || worker.getReliabilityScore() < minRs) {
            if (channel == MatchingChannel.INVITE_MODE && hasOverrideReason) {
                usedOverride = true;
            } else {
                return EligibilityResult.fail("INSUFFICIENT_RELIABILITY",
                        "Điểm uy tín hiện tại không đáp ứng yêu cầu tối thiểu (" + minRs + " điểm) của ca làm việc.");
            }
        }

        if (shift.getMinExperienceLevel() != null) {
            int requiredShifts = switch (shift.getMinExperienceLevel()) {
                case INTERMEDIATE -> 3;
                case ADVANCED -> 5;
                case EXPERT -> 10;
                default -> 0;
            };
            int completed = worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0;
            if (completed < requiredShifts) {
                if (channel == MatchingChannel.INVITE_MODE && hasOverrideReason) {
                    usedOverride = true;
                } else {
                    return EligibilityResult.fail("INSUFFICIENT_EXPERIENCE",
                            "Kinh nghiệm hiện tại (" + completed + " ca) chưa đáp ứng yêu cầu tối thiểu (" + requiredShifts + " ca).");
                }
            }
        }

        return usedOverride
                ? EligibilityResult.passWithOverride("INVITE_OVERRIDE")
                : EligibilityResult.pass();
    }

    /**
     * Tính SkillFitBonus = 6.0 * RequiredQuality + 4.0 * PreferredFit
     * Trọng số chất lượng VerificationTier:
     * - SELF_DECLARED -> 0.0
     * - AI_DOCUMENT (chưa hết hạn) -> 0.5
     * - PLATFORM_PROVEN -> 1.0
     * Không tính bonus nếu ca không có yêu cầu kỹ năng; không tính trùng 1 skill hai lần.
     */
    public SkillFitResult evaluateSkillFit(Worker worker, Shift shift) {
        if (shift == null) {
            return SkillFitResult.empty();
        }
        List<EffectiveRequirement> allReqs = resolveEffectiveRequirements(shift);
        if (allReqs.isEmpty()) {
            return SkillFitResult.empty();
        }

        List<EffectiveRequirement> requiredReqs = allReqs.stream()
                .filter(r -> r.requirementType() == SkillRequirementType.REQUIRED)
                .toList();
        List<EffectiveRequirement> preferredReqs = allReqs.stream()
                .filter(r -> r.requirementType() == SkillRequirementType.PREFERRED)
                .toList();

        List<String> requiredNames = requiredReqs.stream().map(this::resolveDisplaySkillName).toList();
        List<String> preferredNames = preferredReqs.stream().map(this::resolveDisplaySkillName).toList();

        if (worker == null || workerSkillRepository == null) {
            return new SkillFitResult(0.0, 0.0, 0.0, List.of(), List.of(), requiredNames, preferredNames);
        }

        List<WorkerSkill> workerSkills = workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId());
        if (workerSkills == null || workerSkills.isEmpty()) {
            return new SkillFitResult(0.0, 0.0, 0.0, List.of(), List.of(), requiredNames, preferredNames);
        }

        Instant now = Instant.now();
        Map<UUID, WorkerSkill> skillMap = indexWorkerSkillsBySkillId(workerSkills);
        List<UUID> matchedIds = new ArrayList<>();
        List<String> matchedNames = new ArrayList<>();
        Set<UUID> seenMatchedIds = new LinkedHashSet<>();

        double requiredSum = 0.0;
        for (EffectiveRequirement req : requiredReqs) {
            WorkerSkill ws = skillMap.get(req.skillId());
            if (isWorkerSkillActiveAndUsable(ws, now) && meetsVerificationTier(ws, req.minVerificationTier(), now)
                    && (!req.requiresValidCertificate() || hasValidCertificate(ws, now))) {
                requiredSum += resolveQualityWeight(ws, now);
                if (seenMatchedIds.add(req.skillId())) {
                    matchedIds.add(req.skillId());
                    matchedNames.add(resolveDisplaySkillName(req));
                }
            }
        }
        double requiredQuality = requiredReqs.isEmpty() ? 0.0 : clampUnit(requiredSum / requiredReqs.size());

        double preferredSum = 0.0;
        for (EffectiveRequirement pref : preferredReqs) {
            // Chống cộng trùng nếu skillId đã nằm trong REQUIRED
            if (requiredReqs.stream().anyMatch(r -> r.skillId().equals(pref.skillId()))) {
                continue;
            }
            WorkerSkill ws = skillMap.get(pref.skillId());
            if (isWorkerSkillActiveAndUsable(ws, now) && meetsVerificationTier(ws, pref.minVerificationTier(), now)
                    && (!pref.requiresValidCertificate() || hasValidCertificate(ws, now))) {
                preferredSum += resolveQualityWeight(ws, now);
                if (seenMatchedIds.add(pref.skillId())) {
                    matchedIds.add(pref.skillId());
                    matchedNames.add(resolveDisplaySkillName(pref));
                }
            }
        }
        double preferredFit = preferredReqs.isEmpty() ? 0.0 : clampUnit(preferredSum / preferredReqs.size());

        double bonus = (WEIGHT_REQUIRED_QUALITY * requiredQuality) + (WEIGHT_PREFERRED_FIT * preferredFit);
        return new SkillFitResult(
                requiredQuality,
                preferredFit,
                Math.round(bonus * 100.0) / 100.0,
                matchedIds,
                matchedNames,
                requiredNames,
                preferredNames
        );
    }

    public List<EffectiveRequirement> resolveEffectiveRequirements(Shift shift) {
        if (shift == null) {
            return List.of();
        }
        Map<UUID, EffectiveRequirement> ordered = new LinkedHashMap<>();

        if (shiftSkillRequirementRepository != null && shift.getShiftId() != null) {
            List<ShiftSkillRequirement> rows = shiftSkillRequirementRepository.findByShiftIdWithSkill(shift.getShiftId());
            if (rows != null && !rows.isEmpty()) {
                for (ShiftSkillRequirement row : rows) {
                    if (row.getSkill() == null || row.getSkill().getSkillId() == null) {
                        continue;
                    }
                    UUID skId = row.getSkill().getSkillId();
                    ordered.putIfAbsent(skId, new EffectiveRequirement(
                            skId,
                            row.getSkill().getName(),
                            row.getRequirementType() != null ? row.getRequirementType() : SkillRequirementType.REQUIRED,
                            row.getMinVerificationTier() != null ? row.getMinVerificationTier() : VerificationTier.SELF_DECLARED,
                            Boolean.TRUE.equals(row.getRequiresValidCertificate())
                    ));
                }
                return new ArrayList<>(ordered.values());
            }
        }

        // Fallback tương thích ngược cho ca làm chỉ có shifts.required_skills
        if (shift.getRequiredSkills() != null && shift.getRequiredSkills().length > 0) {
            Map<UUID, String> skillNameLookup = new HashMap<>();
            List<UUID> nonNullIds = Arrays.stream(shift.getRequiredSkills()).filter(Objects::nonNull).toList();
            if (skillRepository != null && !nonNullIds.isEmpty()) {
                for (Skill sk : skillRepository.findAllById(nonNullIds)) {
                    if (sk != null && sk.getSkillId() != null && sk.getName() != null) {
                        skillNameLookup.put(sk.getSkillId(), sk.getName());
                    }
                }
            }
            for (UUID skId : nonNullIds) {
                ordered.putIfAbsent(skId, new EffectiveRequirement(
                        skId,
                        skillNameLookup.get(skId),
                        SkillRequirementType.REQUIRED,
                        VerificationTier.SELF_DECLARED,
                        false
                ));
            }
        }

        return new ArrayList<>(ordered.values());
    }

    public boolean isShiftCoveredByAvailability(Worker worker, Shift shift) {
        if (workerAvailabilityRepository == null || shift.getStartTime() == null || shift.getEndTime() == null) {
            return true;
        }
        List<WorkerAvailability> slots = workerAvailabilityRepository
                .findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(worker.getWorkerId());
        if (slots == null || slots.isEmpty()) {
            return true;
        }

        List<WorkerAvailability> availableSlots = slots.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsAvailable()))
                .toList();
        if (availableSlots.isEmpty()) {
            return false;
        }

        ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");
        ZonedDateTime shiftStart = shift.getStartTime().atZone(vnZone);
        ZonedDateTime shiftEnd = shift.getEndTime().atZone(vnZone);

        int shiftDayOfWeek = shiftStart.getDayOfWeek().getValue() % 7;
        LocalTime shiftStartTime = shiftStart.toLocalTime();
        LocalTime shiftEndTime = shiftEnd.toLocalTime();

        for (WorkerAvailability slot : availableSlots) {
            if (slot.getDayOfWeek() == null || slot.getDayOfWeek() != shiftDayOfWeek) {
                continue;
            }
            LocalTime slotStart = slot.getStartTime();
            LocalTime slotEnd = slot.getEndTime();
            if (slotStart == null || slotEnd == null) {
                continue;
            }
            if (!slotEnd.isBefore(slotStart)) {
                if (shiftStart.toLocalDate().equals(shiftEnd.toLocalDate())
                        && !shiftStartTime.isBefore(slotStart)
                        && !shiftEndTime.isAfter(slotEnd)) {
                    return true;
                }
            } else {
                if (!shiftStartTime.isBefore(slotStart) || !shiftEndTime.isAfter(slotEnd)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<UUID, WorkerSkill> indexWorkerSkillsBySkillId(List<WorkerSkill> workerSkills) {
        Map<UUID, WorkerSkill> map = new HashMap<>();
        for (WorkerSkill ws : workerSkills) {
            if (ws.getSkill() != null && ws.getSkill().getSkillId() != null) {
                map.putIfAbsent(ws.getSkill().getSkillId(), ws);
            }
        }
        return map;
    }

    private boolean isWorkerSkillActiveAndUsable(WorkerSkill ws, Instant now) {
        if (ws == null) {
            return false;
        }
        if (ws.getVerificationTier() == VerificationTier.PLATFORM_PROVEN) {
            return true;
        }
        return ws.getExpiresAt() == null || ws.getExpiresAt().isAfter(now);
    }

    private boolean hasValidCertificate(WorkerSkill ws, Instant now) {
        if (ws == null) {
            return false;
        }
        boolean hasCertBacking = ws.getEvidenceRequestId() != null
                || ws.getVerificationTier() == VerificationTier.AI_DOCUMENT;
        return hasCertBacking && (ws.getExpiresAt() == null || ws.getExpiresAt().isAfter(now));
    }

    private boolean meetsVerificationTier(WorkerSkill ws, VerificationTier requiredTier, Instant now) {
        if (ws == null) {
            return false;
        }
        VerificationTier required = requiredTier != null ? requiredTier : VerificationTier.SELF_DECLARED;
        VerificationTier effective = resolveEffectiveTier(ws, now);
        return tierRank(effective) >= tierRank(required);
    }

    private VerificationTier resolveEffectiveTier(WorkerSkill ws, Instant now) {
        if (ws.getVerificationTier() == null) {
            return VerificationTier.SELF_DECLARED;
        }
        if (ws.getVerificationTier() == VerificationTier.PLATFORM_PROVEN) {
            return VerificationTier.PLATFORM_PROVEN;
        }
        if (ws.getVerificationTier() == VerificationTier.AI_DOCUMENT) {
            if (ws.getExpiresAt() != null && !ws.getExpiresAt().isAfter(now)) {
                return VerificationTier.SELF_DECLARED;
            }
            return VerificationTier.AI_DOCUMENT;
        }
        return VerificationTier.SELF_DECLARED;
    }

    private double resolveQualityWeight(WorkerSkill ws, Instant now) {
        if (ws == null) {
            return 0.0;
        }
        return switch (resolveEffectiveTier(ws, now)) {
            case PLATFORM_PROVEN -> 1.0;
            case AI_DOCUMENT -> 0.5;
            case SELF_DECLARED -> 0.0;
        };
    }

    private int tierRank(VerificationTier tier) {
        return switch (tier) {
            case SELF_DECLARED -> 0;
            case AI_DOCUMENT -> 1;
            case PLATFORM_PROVEN -> 2;
        };
    }

    private String resolveDisplaySkillName(EffectiveRequirement req) {
        if (req.skillName() != null && !req.skillName().isBlank()) {
            return req.skillName();
        }
        if (skillRepository != null && req.skillId() != null) {
            return skillRepository.findById(req.skillId()).map(Skill::getName).orElse("Kỹ năng chuyên môn");
        }
        return "Kỹ năng chuyên môn";
    }

    private String toTierLabelVi(VerificationTier tier) {
        if (tier == null) {
            return "Tự khai báo";
        }
        return switch (tier) {
            case SELF_DECLARED -> "Tự khai báo";
            case AI_DOCUMENT -> "Đã xác thực tài liệu";
            case PLATFORM_PROVEN -> "Đã chứng minh qua ca làm";
        };
    }

    private static double clampUnit(double v) {
        if (!Double.isFinite(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }
}
