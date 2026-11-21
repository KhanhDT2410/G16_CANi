package com.cani.service;

import com.cani.entity.*;
import com.cani.entity.enums.AssignmentSource;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.MembershipStatus;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.ProposalStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.DispatchProposalRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import com.cani.repository.WorkerGroupMembershipRepository;
import com.cani.repository.WorkerGroupRepository;
import com.cani.repository.WorkerRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingDispatchService {

    private final WorkerRepository workerRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final DispatchProposalRepository dispatchProposalRepository;
    private final WorkerGroupRepository workerGroupRepository;
    private final WorkerGroupMembershipRepository workerGroupMembershipRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private BlockListService blockListService;

    @Autowired(required = false)
    private com.cani.repository.SystemConfigRepository systemConfigRepository;

    @Autowired(required = false)
    private com.cani.repository.WorkerSkillRepository workerSkillRepository;

    @Autowired(required = false)
    private com.cani.repository.WorkerAvailabilityRepository workerAvailabilityRepository;

    private static final List<ProposalStatus> ACTIVE_PROPOSAL_STATUSES = List.of(
            ProposalStatus.PENDING, ProposalStatus.ACCEPTED
    );

    private static final double MAX_DISTANCE_KM = 15.0;
    private static final int MIN_RELIABILITY_SCORE = 60;
    private static final int GROUP_NO_SHOW_COOLDOWN_MINUTES = 20;
    private static final int DEFAULT_TIMEOUT_HOURS = 1;
    private static final double EARTH_RADIUS_KM = 6371.0;

    @Data
    @Builder
    public static class CandidateMatch {
        private Worker worker;
        private double distanceKm;
        private double finalScore;
        private String scoreBreakdownJson;
    }

    private record GroupCandidate(
            WorkerGroup group,
            Worker leader,
            List<Worker> eligible,
            double score,
            double distanceKm) {
    }

    /**
     * Kích hoạt matching và dispatch cho tất cả ca làm việc đang ACTIVE của một Event
     */
    @Transactional
    public int dispatchForEvent(UUID eventId) {
        List<Shift> activeShifts = shiftRepository.findByEvent_EventIdAndStatus(eventId, ShiftStatus.ACTIVE);
        int totalProposalsCreated = 0;
        for (Shift shift : activeShifts) {
            totalProposalsCreated += dispatchForShift(shift.getShiftId());
        }
        return totalProposalsCreated;
    }

    /**
     * Thuật toán Matching & Dispatch cho một ca làm việc cụ thể
     */
    @Transactional
    public int dispatchForShift(UUID shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ca làm việc ID: " + shiftId));

        if (shift.getStatus() != ShiftStatus.ACTIVE) {
            log.info("Bỏ qua dispatch: Ca làm việc {} không ở trạng thái ACTIVE (hiện tại: {})", shiftId, shift.getStatus());
            return 0;
        }

        int needed = shift.getRemainingWorkers() != null ? shift.getRemainingWorkers() : shift.getRequiredWorkers();
        if (needed <= 0) {
            log.info("Ca làm việc {} đã đủ nhân sự.", shiftId);
            return 0;
        }

        // Tọa độ mục tiêu của ca làm việc
        Double targetLat = shift.getLocationLat() != null ? shift.getLocationLat() : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
        Double targetLng = shift.getLocationLng() != null ? shift.getLocationLng() : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);

        // Bán kính tối đa của ca làm việc (hoặc mặc định 15km)
        double maxAllowedDistanceKm = (shift.getMaxDistanceKm() != null && shift.getMaxDistanceKm().doubleValue() > 0)
                ? shift.getMaxDistanceKm().doubleValue()
                : MAX_DISTANCE_KM;

        // Ưu tiên truy vấn danh sách Worker đang Online dưới DB để tối ưu hiệu năng (O(online) thay vì full table scan)
        List<Worker> allWorkers = workerRepository.findByIsOnlineTrue();
        if (allWorkers == null || allWorkers.isEmpty()) {
            allWorkers = workerRepository.findAll();
        }

        List<CandidateMatch> matchedCandidates = new ArrayList<>();

        for (Worker worker : allWorkers) {
            // STEP 1: HARD FILTER
            if (!passHardFilter(worker, shift)) {
                continue;
            }

            // Tính khoảng cách Haversine & kiểm tra bán kính kết hợp (Ca làm việc & Tiêu chí Worker)
            double distanceKm = calculateDistance(targetLat, targetLng, worker.getBaseLat(), worker.getBaseLng());
            if (targetLat == null || targetLng == null || worker.getBaseLat() == null || worker.getBaseLng() == null) {
                // # ponytail: thiếu tọa độ (worker chưa báo GPS hoặc ca chưa có địa chỉ) -> bỏ kiểm tra bán kính,
                // cho worker vào candidate; upgrade: bắt worker cập nhật GPS trước khi dispatch.
                distanceKm = 0.0;
            }
            double workerMaxDist = (worker.getMaxDistanceKm() != null && worker.getMaxDistanceKm() > 0)
                    ? worker.getMaxDistanceKm()
                    : maxAllowedDistanceKm;
            double effectiveAllowedDistanceKm = Math.min(maxAllowedDistanceKm, workerMaxDist);

            if (distanceKm > effectiveAllowedDistanceKm) {
                continue;
            }

            // STEP 2: SOFT RANKING SCORE
            CandidateMatch match = calculateSoftScore(worker, distanceKm, effectiveAllowedDistanceKm, shift);
            matchedCandidates.add(match);
        }

        // Sắp xếp ứng viên theo điểm số giảm dần
        matchedCandidates.sort(Comparator.comparingDouble(CandidateMatch::getFinalScore).reversed());

        // Dispatch ratio: tuyển N người -> dispatch cho min(N * 2 + 3, số ứng viên phù hợp)
        int targetDispatchCount = Math.max(needed * 2 + 3, needed);
        List<CandidateMatch> topCandidates = matchedCandidates.stream()
                .limit(targetDispatchCount)
                .collect(Collectors.toList());

        int createdCount = 0;
        Instant expiresAt = Instant.now().plus(Duration.ofHours(DEFAULT_TIMEOUT_HOURS));

        for (CandidateMatch candidate : topCandidates) {
            Worker worker = candidate.getWorker();
            // Chỉ chặn nếu đã có proposal đang chờ/đã nhận (PENDING, ACCEPTED) hoặc đã có assignment
            if (dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(shiftId, worker.getWorkerId(), ACTIVE_PROPOSAL_STATUSES) ||
                shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(shiftId, worker.getWorkerId())) {
                continue;
            }

            DispatchProposal proposal = DispatchProposal.builder()
                    .shift(shift)
                    .worker(worker)
                    .matchScore(BigDecimal.valueOf(candidate.getFinalScore()).setScale(2, RoundingMode.HALF_UP))
                    .scoreBreakdown(candidate.getScoreBreakdownJson())
                    .status(ProposalStatus.PENDING)
                    .expiresAt(expiresAt)
                    .build();

            DispatchProposal savedProposal = dispatchProposalRepository.save(proposal);
            createdCount++;

            if (notificationService != null && worker.getUser() != null && worker.getUser().getUserId() != null) {
                try {
                    String eventName = shift.getEvent() != null ? shift.getEvent().getName() : "Sự kiện CANi";
                    notificationService.createAndSend(
                            worker.getUser().getUserId(),
                            "Đề xuất ca làm mới: " + shift.getJobType(),
                            "Bạn vừa nhận được lời mời làm việc tại \"" + eventName + "\" với mức thù lao " + shift.getHourlyWage() + " đ/giờ.",
                            NotificationType.PROPOSAL,
                            "PROPOSAL",
                            savedProposal != null && savedProposal.getProposalId() != null ? savedProposal.getProposalId() : shiftId,
                            Map.of("shiftId", shiftId.toString(), "type", "NEW_PROPOSAL")
                    );
                } catch (Exception ex) {
                    log.warn("Không thể gửi thông báo FCM cho đề xuất ca làm: {}", ex.getMessage());
                }
            }
        }

        if (matchedCandidates.isEmpty()) {
            log.warn("Matching ca {}: KHÔNG TÌM THẤY ứng viên nào đạt điều kiện. "
                            + "Kiểm tra: worker đang online? kycStatus APPROVED? có GPS? requiredSkills khớp worker_skills? "
                            + "khung giờ rảnh trùng ca? minWage không vượt lương ca? blocklist? "
                            + "Tổng worker đã xét: {}",
                    shiftId, allWorkers.size());
        }

        createdCount += dispatchGroupsForShift(shiftId, false);

        log.info("Matching hoàn tất cho ca {}: Tìm thấy {} ứng viên, đã gửi {} đề xuất (Proposals).",
                shiftId, matchedCandidates.size(), createdCount);
        return createdCount;
    }

    /**
     * Dọn proposal PENDING đã hết hạn (expiresAt) -> EXPIRED.
     * Chạy định kỳ 5 phút, không re-dispatch (EO publish lại nếu cần).
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void expireStalePendingProposals() {
        Instant now = Instant.now();
        // Time rule: bounded DB query, không quét cả bảng PENDING.
        List<DispatchProposal> stale = new ArrayList<>(
                dispatchProposalRepository.findByStatusAndExpiresAtBefore(ProposalStatus.PENDING, now));
        // Orphan rule: group PENDING trong 24h, leader hiện tại khác worker đã lưu (batch load groups).
        List<DispatchProposal> groupRecent = dispatchProposalRepository
                .findByStatusAndGroupIdIsNotNullAndCreatedAtAfter(
                        ProposalStatus.PENDING, now.minus(24, TimeUnit.HOURS));
        Map<UUID, WorkerGroup> groups = workerGroupRepository
                .findAllById(groupRecent.stream().map(DispatchProposal::getGroupId).distinct().toList())
                .stream().collect(Collectors.toMap(WorkerGroup::getGroupId, g -> g));
        stale.addAll(groupRecent.stream()
                .filter(p -> p.getExpiresAt() == null || !p.getExpiresAt().isBefore(now))
                .filter(p -> p.getWorker() != null)
                .filter(p -> {
                    WorkerGroup group = groups.get(p.getGroupId());
                    return group != null
                            && !p.getWorker().getWorkerId().equals(group.getLeaderWorkerId());
                })
                .toList());
        if (stale.isEmpty()) {
            return;
        }
        stale.forEach(p -> p.setStatus(ProposalStatus.EXPIRED));
        dispatchProposalRepository.saveAll(stale);
        log.info("Đã chuyển {} proposal PENDING hết hạn/đội trưởng đã thay đổi sang EXPIRED.", stale.size());
    }

    /**
     * Hard Filter: Loại bỏ các ứng viên không đạt tiêu chuẩn tối thiểu
     */
    private boolean passHardFilter(Worker worker, Shift shift) {
        // 1. Phải bật trạng thái "Bắt đầu kiếm việc" (isOnline = true)
        if (Boolean.FALSE.equals(worker.getIsOnline())) {
            return false;
        }

        // 1b. Bắt buộc tài khoản đã hoàn tất định danh eKYC (KycStatus == APPROVED)
        if (worker.getKycStatus() != com.cani.entity.enums.KycStatus.APPROVED) {
            return false;
        }

        // 2. Tài khoản User phải ACTIVE
        if (worker.getUser() == null || worker.getUser().getStatus() != UserStatus.ACTIVE) {
            return false;
        }

        // 3. Điểm tin cậy (Reliability Score) >= 60 (hoặc theo yêu cầu của shift)
        int minRs = shift.getMinReliabilityScore() != null && shift.getMinReliabilityScore() > 0 
                ? shift.getMinReliabilityScore() : MIN_RELIABILITY_SCORE;
        if (worker.getReliabilityScore() == null || worker.getReliabilityScore() < minRs) {
            return false;
        }

        // 4. Kiểm tra kinh nghiệm tối thiểu nếu ca làm có yêu cầu (minExperienceLevel)
        if (shift.getMinExperienceLevel() != null) {
            int requiredShifts = switch (shift.getMinExperienceLevel()) {
                case INTERMEDIATE -> 3;
                case ADVANCED -> 5;
                case EXPERT -> 10;
                default -> 0;
            };
            int completed = worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0;
            if (completed < requiredShifts) {
                return false;
            }
        }

        // 4b. Kiểm tra kỹ năng bắt buộc theo tiêu chí ca làm việc (requiredSkills - containsAll)
        if (shift.getRequiredSkills() != null && shift.getRequiredSkills().length > 0 && workerSkillRepository != null) {
            Set<UUID> requiredSkillIds = Arrays.stream(shift.getRequiredSkills())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!requiredSkillIds.isEmpty()) {
                List<WorkerSkill> workerSkills = workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId());
                Set<UUID> haveSkillIds = workerSkills == null ? Collections.emptySet() :
                        workerSkills.stream()
                                .filter(ws -> ws.getSkill() != null && ws.getSkill().getSkillId() != null)
                                .map(ws -> ws.getSkill().getSkillId())
                                .collect(Collectors.toSet());
                if (!haveSkillIds.containsAll(requiredSkillIds)) {
                    return false;
                }
            }
        }

        // 5. Kiểm tra xung đột lịch làm việc (Không được trùng ca đã xác nhận/đang chờ)
        List<AssignmentStatus> activeStatuses = List.of(
                AssignmentStatus.CONFIRMED,
                AssignmentStatus.CHECKED_IN,
                AssignmentStatus.PENDING
        );
        List<ShiftAssignment> conflicts = shiftAssignmentRepository.findConflictingAssignments(
                worker.getWorkerId(),
                shift.getStartTime(),
                shift.getEndTime(),
                activeStatuses
        );
        if (!conflicts.isEmpty()) {
            return false;
        }

        // 6. Kiểm tra Danh sách Chặn (UC-38, UC-39): Không match nếu Worker hoặc EO đã chặn nhau
        if (blockListService != null && shift.getEvent() != null && shift.getEvent().getEventOrganizer() != null) {
            EventOrganizer eo = shift.getEvent().getEventOrganizer();
            if (eo.getUser() != null && worker.getUser() != null) {
                if (blockListService.isEitherBlocked(eo.getUser().getUserId(), worker.getUser().getUserId())) {
                    log.debug("Loại trừ ứng viên {} do có chặn tương tác với EO {}", worker.getWorkerId(), eo.getEoId());
                    return false;
                }
            }
        }

        // 7. Kiểm tra mức lương tối thiểu theo tiêu chí tìm việc của Worker (Worker Preferences: minWage)
        if (worker.getMinWage() != null && worker.getMinWage().compareTo(BigDecimal.ZERO) > 0) {
            if (shift.getHourlyWage() != null && shift.getHourlyWage().compareTo(worker.getMinWage()) < 0) {
                log.debug("Loại trừ ứng viên {}: Lương ca làm ({} đ) thấp hơn lương tối thiểu mong muốn ({} đ)",
                        worker.getWorkerId(), shift.getHourlyWage(), worker.getMinWage());
                return false;
            }
        }

        // 8. Kiểm tra lịch rảnh cố định hàng tuần (Worker Availability: 1=T2 ... 6=T7, 0=CN)
        if (!isShiftCoveredByAvailability(worker, shift)) {
            log.debug("Loại trừ ứng viên {}: Ca làm không nằm trong khung giờ rảnh hàng tuần đã cài đặt", worker.getWorkerId());
            return false;
        }

        return true;
    }

    /**
     * Kiểm tra ca làm có nằm trong lịch rảnh cố định hàng tuần của Worker hay không.
     * Quy tắc Kỹ nghệ:
     * - Nếu Worker chưa cấu hình lịch rảnh (bảng trống) -> xem như rảnh linh hoạt toàn thời gian (return true).
     * - Nếu Worker đã cấu hình lịch rảnh -> ca làm việc bắt buộc phải nằm trọn trong ít nhất một slot rảnh.
     */
    private boolean isShiftCoveredByAvailability(Worker worker, Shift shift) {
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

        // Day of week mapping chuẩn CANi: 0=Chủ Nhật, 1=Thứ Hai, ..., 6=Thứ Bảy
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

            // Trường hợp 1: Slot trong cùng 1 ngày (ví dụ 08:00 -> 17:00 hoặc 18:00 -> 23:00)
            if (!slotEnd.isBefore(slotStart)) {
                if (shiftStart.toLocalDate().equals(shiftEnd.toLocalDate())) {
                    if (!shiftStartTime.isBefore(slotStart) && !shiftEndTime.isAfter(slotEnd)) {
                        return true;
                    }
                }
            } else {
                // Trường hợp 2: Slot làm việc qua đêm (ví dụ 22:00 -> 06:00 hôm sau)
                if (!shiftStartTime.isBefore(slotStart) || !shiftEndTime.isAfter(slotEnd)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Soft Ranking Score: Tính điểm xếp hạng ứng viên dựa trên 4 thành phần + Điểm thưởng
     */
    private CandidateMatch calculateSoftScore(Worker worker, double distanceKm, double maxAllowedDistanceKm, Shift shift) {
        double effectiveMaxDist = maxAllowedDistanceKm > 0 ? maxAllowedDistanceKm : MAX_DISTANCE_KM;
        // 1. Distance Score: 100 - (distance / maxAllowedDistanceKm) * 100 (Trọng số 0.4)
        double distanceScore = Math.max(0.0, 100.0 - (distanceKm / effectiveMaxDist) * 100.0);

        // 2. Rating Score: (avg_rating / 5) * 100 (Trọng số 0.3)
        double rating = worker.getRatingAvg() != null ? worker.getRatingAvg().doubleValue() : 5.0;
        double ratingScore = Math.min(100.0, (rating / 5.0) * 100.0);

        // 3. Experience Score: (completed_shifts / 10) * 100 (Trọng số 0.2)
        int completedShifts = worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0;
        double expScore = Math.min(100.0, (completedShifts / 10.0) * 100.0);

        // 4. Reliability Score: Trọng số 0.1
        double rsScore = worker.getReliabilityScore() != null ? Math.min(100.0, worker.getReliabilityScore()) : 80.0;

        // Bonus: Người mới hoặc nghỉ lâu (UC-60, UC-61: Fairness Boost)
        double newWorkerBoostConfig = getConfigDouble("fairness.new_worker_boost", 10.0);
        double idleWorkerBoostConfig = getConfigDouble("fairness.idle_worker_boost", 10.0);

        double newWorkerBonus = (completedShifts < 3) ? newWorkerBoostConfig : 0.0;
        double idleBonus = (completedShifts >= 3 && Boolean.TRUE.equals(worker.getIsOnline())) ? idleWorkerBoostConfig : 0.0;

        // Bonus kỹ năng đã xác thực (AI_DOCUMENT / ADMIN_REVIEW): +10.0 (tương đương +0.1 trên thang chuẩn hóa)
        double skillBonus = 0.0;
        if (shift != null && shift.getRequiredSkills() != null && shift.getRequiredSkills().length > 0 && workerSkillRepository != null) {
            Set<UUID> reqIds = Arrays.stream(shift.getRequiredSkills()).filter(Objects::nonNull).collect(Collectors.toSet());
            List<WorkerSkill> workerSkills = workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId());
            if (workerSkills != null) {
                boolean hasVerifiedSkill = workerSkills.stream()
                        .anyMatch(ws -> ws.getSkill() != null
                                && reqIds.contains(ws.getSkill().getSkillId())
                                && ws.getVerificationTier() != null
                                && ws.getVerificationTier() != com.cani.entity.enums.VerificationTier.SELF_DECLARED);
                if (hasVerifiedSkill) {
                    skillBonus = 10.0;
                }
            }
        }

        double finalScore = (0.4 * distanceScore) + (0.3 * ratingScore) + (0.2 * expScore) + (0.1 * rsScore) + newWorkerBonus + idleBonus + skillBonus;

        String breakdownJson = String.format(
                Locale.US,
                "{\"distanceKm\":%.2f,\"distScore\":%.1f,\"ratingScore\":%.1f,\"expScore\":%.1f,\"rsScore\":%.1f,\"newWorkerBonus\":%.1f,\"idleBonus\":%.1f,\"skillBonus\":%.1f,\"finalScore\":%.2f}",
                distanceKm, distanceScore, ratingScore, expScore, rsScore, newWorkerBonus, idleBonus, skillBonus, finalScore
        );

        return CandidateMatch.builder()
                .worker(worker)
                .distanceKm(distanceKm)
                .finalScore(finalScore)
                .scoreBreakdownJson(breakdownJson)
                .build();
    }

    /**
     * UC-59: Kích hoạt điều phối khẩn cấp (Emergency Dispatch) khi ca làm việc thiếu người
     */
    @Transactional
    public int triggerEmergencyDispatch(UUID shiftId, Double customRadiusKm) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ca làm việc ID: " + shiftId));

        if (shift.getStatus() != ShiftStatus.ACTIVE) {
            log.warn("Không thể kích hoạt Emergency Dispatch: Ca làm việc {} không ở trạng thái ACTIVE", shiftId);
            return 0;
        }

        if (!Boolean.TRUE.equals(shift.getEmergency())) {
            shift.setEmergency(true);
            shiftRepository.save(shift);
        }

        double rawRadius = (customRadiusKm != null && customRadiusKm > 0) ? customRadiusKm : 25.0;
        double emergencyRadius = Math.min(Math.max(rawRadius, 1.0), 50.0);
        log.info("KÍCH HOẠT EMERGENCY DISPATCH cho ca {}: Bán kính mở rộng {} km (Capped max 50km)", shiftId, emergencyRadius);

        int needed = shift.getRemainingWorkers() != null ? shift.getRemainingWorkers() : shift.getRequiredWorkers();
        if (needed <= 0) {
            return 0;
        }

        Double targetLat = shift.getLocationLat() != null ? shift.getLocationLat() : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
        Double targetLng = shift.getLocationLng() != null ? shift.getLocationLng() : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);

        List<Worker> allWorkers = workerRepository.findByIsOnlineTrue();
        if (allWorkers == null || allWorkers.isEmpty()) {
            allWorkers = workerRepository.findAll();
        }

        List<CandidateMatch> matchedCandidates = new ArrayList<>();
        for (Worker worker : allWorkers) {
            if (!passHardFilter(worker, shift)) {
                continue;
            }
            double distanceKm = calculateDistance(targetLat, targetLng, worker.getBaseLat(), worker.getBaseLng());
            if (targetLat == null || targetLng == null || worker.getBaseLat() == null || worker.getBaseLng() == null) {
                distanceKm = 0.0; // cùng chính sách bỏ kiểm tra bán kính khi thiếu tọa độ
            } else if (distanceKm > emergencyRadius) {
                continue;
            }
            CandidateMatch match = calculateSoftScore(worker, distanceKm, emergencyRadius, shift);
            matchedCandidates.add(match);
        }

        matchedCandidates.sort(Comparator.comparingDouble(CandidateMatch::getFinalScore).reversed());

        int targetDispatchCount = Math.max(needed * 3 + 5, needed);
        List<CandidateMatch> topCandidates = matchedCandidates.stream()
                .limit(targetDispatchCount)
                .toList();

        int createdCount = 0;
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(30)); // Khẩn cấp: Hết hạn sau 30 phút

        for (CandidateMatch candidate : topCandidates) {
            Worker worker = candidate.getWorker();
            if (dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(shiftId, worker.getWorkerId(), ACTIVE_PROPOSAL_STATUSES) ||
                shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(shiftId, worker.getWorkerId())) {
                continue;
            }

            DispatchProposal proposal = DispatchProposal.builder()
                    .shift(shift)
                    .worker(worker)
                    .matchScore(BigDecimal.valueOf(candidate.getFinalScore()).setScale(2, RoundingMode.HALF_UP))
                    .scoreBreakdown(candidate.getScoreBreakdownJson())
                    .status(ProposalStatus.PENDING)
                    .expiresAt(expiresAt)
                    .build();

            DispatchProposal savedProposal = dispatchProposalRepository.save(proposal);
            createdCount++;

            if (notificationService != null && worker.getUser() != null && worker.getUser().getUserId() != null) {
                try {
                    String eventName = shift.getEvent() != null ? shift.getEvent().getName() : "Sự kiện CANi";
                    notificationService.createAndSend(
                            worker.getUser().getUserId(),
                            "🚨 [GẤP] Lời mời nhận ca làm khẩn cấp: " + shift.getJobType(),
                            "Ca làm việc khẩn cấp tại \"" + eventName + "\" đang thiếu nhân sự! Thù lao " + shift.getHourlyWage() + " đ/giờ.",
                            NotificationType.PROPOSAL,
                            "PROPOSAL",
                            savedProposal != null && savedProposal.getProposalId() != null ? savedProposal.getProposalId() : shiftId,
                            Map.of("shiftId", shiftId.toString(), "type", "EMERGENCY_PROPOSAL")
                    );
                } catch (Exception ex) {
                    log.warn("Lỗi gửi thông báo FCM khẩn cấp: {}", ex.getMessage());
                }
            }
        }

        createdCount += dispatchGroupsForShift(shiftId, true);

        log.info("Emergency Dispatch hoàn tất: Đã gửi {} đề xuất khẩn cấp cho ca {}", createdCount, shiftId);
        return createdCount;
    }

    /**
     * Kênh Group Dispatch: gửi 1 lời mời cho tối đa 5 đội (Group) có thành viên đủ điều kiện.
     * Proposal gán worker = leader nhóm; FCM gửi leader để cử 1 đến [claimableSlots] thành viên.
     */
    @Transactional
    public int dispatchGroupsForShift(UUID shiftId, boolean emergency) {
        Shift shift = shiftRepository.findById(shiftId).orElse(null);
        if (shift == null || shift.getStatus() != ShiftStatus.ACTIVE) {
            return 0;
        }
        int remaining = shift.getRemainingWorkers() != null ? shift.getRemainingWorkers() : 0;
        if (remaining <= 0) {
            log.info("Ca làm việc {} đã đủ nhân sự, bỏ qua kênh group dispatch.", shiftId);
            return 0;
        }

        Double targetLat = shift.getLocationLat() != null ? shift.getLocationLat() : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
        Double targetLng = shift.getLocationLng() != null ? shift.getLocationLng() : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);

        // shortcut: cap 50 group, dùng query rank DB khi group > hàng nghìn.
        List<WorkerGroup> candidateGroups = workerGroupRepository.findAll().stream()
                .filter(g -> Boolean.TRUE.equals(g.getIsActive()))
                .limit(50)
                .toList();
        if (candidateGroups.isEmpty()) {
            return 0;
        }

        // Batch tải membership ACTIVE của các group -> map groupId -> members
        List<WorkerGroupMembership> memberships = workerGroupMembershipRepository
                .findByWorkerGroup_GroupIdInAndStatus(candidateGroups.stream().map(WorkerGroup::getGroupId).toList(), MembershipStatus.ACTIVE);
        Map<UUID, List<WorkerGroupMembership>> membershipsByGroup = memberships.stream()
                .filter(m -> m.getWorkerGroup() != null && m.getWorker() != null)
                .collect(Collectors.groupingBy(m -> m.getWorkerGroup().getGroupId()));

        List<GroupCandidate> groupCandidates = new ArrayList<>();
        for (WorkerGroup group : candidateGroups) {
            List<WorkerGroupMembership> groupMemberships = membershipsByGroup.getOrDefault(group.getGroupId(), Collections.emptyList());
            if (groupMemberships.isEmpty()) {
                continue;
            }

            // Leader phải tồn tại & user ACTIVE
            if (group.getLeaderWorkerId() == null) {
                continue;
            }
            Worker leader = workerRepository.findById(group.getLeaderWorkerId()).orElse(null);
            if (leader == null || leader.getUser() == null || leader.getUser().getStatus() != UserStatus.ACTIVE) {
                continue;
            }

            // Chặn leader nếu đã chặn EO (cùng cách gọi passHardFilter)
            if (blockListService != null && shift.getEvent() != null && shift.getEvent().getEventOrganizer() != null) {
                EventOrganizer eo = shift.getEvent().getEventOrganizer();
                if (eo.getUser() != null && leader.getUser() != null
                        && blockListService.isEitherBlocked(eo.getUser().getUserId(), leader.getUser().getUserId())) {
                    continue;
                }
            }

            // No-show cooldown: group có thành viên no-show trong 20 phút qua -> tạm bỏ qua
            if (group.getLastNoShowAt() != null
                    && group.getLastNoShowAt().isAfter(Instant.now().minus(Duration.ofMinutes(GROUP_NO_SHOW_COOLDOWN_MINUTES)))) {
                continue;
            }

            if (!dispatchProposalRepository
                    .findByShift_ShiftIdAndGroupIdAndStatusIn(shiftId, group.getGroupId(), ACTIVE_PROPOSAL_STATUSES).isEmpty()) {
                continue;
            }

            // Member đủ điều kiện (không xét isOnline — leader cử cả thành viên offline)
            List<Worker> eligible = groupMemberships.stream()
                    .map(WorkerGroupMembership::getWorker)
                    .filter(w -> isGroupChannelMemberEligible(w, shift))
                    .toList();
            int claimableSlots = Math.min(eligible.size(), remaining);
            if (eligible.isEmpty() || claimableSlots == 0) {
                continue;
            }

            // Khoảng cách: TB Haversine các member eligible; thiếu tọa độ -> 0.0 (cùng chính sách kênh cá nhân)
            double distanceKm = 0.0;
            int computed = 0;
            for (Worker w : eligible) {
                double d = calculateDistance(targetLat, targetLng, w.getBaseLat(), w.getBaseLng());
                if (d != Double.MAX_VALUE) {
                    distanceKm += d;
                    computed++;
                }
            }
            if (computed > 0) {
                distanceKm /= computed;
            }

            double groupScore = 0.35 * Math.max(0.0, 100.0 - (distanceKm / MAX_DISTANCE_KM) * 100.0)
                    + 0.25 * Math.min(100.0, ((group.getAvgRating() != null ? group.getAvgRating().doubleValue() : 5.0) / 5.0) * 100.0)
                    + 0.2 * Math.min(100.0, eligible.stream()
                        .mapToDouble(w -> (double) (w.getTotalShiftsCompleted() != null ? w.getTotalShiftsCompleted() : 0))
                        .average().orElse(0.0) / 10.0 * 100.0)
                    + 0.1 * Math.min(100.0, group.getReliabilityScore() != null ? group.getReliabilityScore() : 80.0);

            // Bonus +10: ít nhất 1 member có skill verified nằm trong requiredSkills của ca
            if (shift.getRequiredSkills() != null && shift.getRequiredSkills().length > 0 && workerSkillRepository != null) {
                Set<UUID> reqIds = Arrays.stream(shift.getRequiredSkills()).filter(Objects::nonNull).collect(Collectors.toSet());
                boolean verified = eligible.stream().anyMatch(w -> {
                    List<WorkerSkill> skills = workerSkillRepository.findByWorker_WorkerId(w.getWorkerId());
                    return skills != null && skills.stream().anyMatch(ws -> ws.getSkill() != null
                            && reqIds.contains(ws.getSkill().getSkillId())
                            && ws.getVerificationTier() != null
                            && ws.getVerificationTier() != com.cani.entity.enums.VerificationTier.SELF_DECLARED);
                });
                if (verified) {
                    groupScore += 10.0;
                }
            }

            groupCandidates.add(new GroupCandidate(group, leader, eligible, groupScore, distanceKm));
        }

        if (groupCandidates.isEmpty()) {
            return 0;
        }

        groupCandidates.sort(Comparator.comparingDouble(GroupCandidate::score).reversed());
        List<GroupCandidate> topGroups = groupCandidates.stream().limit(5).toList();

        Instant expiresAt = emergency
                ? Instant.now().plus(Duration.ofMinutes(30))
                : Instant.now().plus(Duration.ofHours(DEFAULT_TIMEOUT_HOURS));

        int createdCount = 0;
        for (GroupCandidate candidate : topGroups) {
            List<Worker> eligible = candidate.eligible();
            int claimableSlots = Math.min(eligible.size(), remaining);

            String breakdownJson = String.format(
                    Locale.US,
                    "{\"groupId\":\"%s\",\"groupName\":\"%s\",\"distanceKm\":%.2f,\"eligibleMemberWorkerIds\":[%s],\"eligibleCount\":%d,\"claimableSlots\":%d,\"finalScore\":%.2f}",
                    candidate.group().getGroupId(),
                    candidate.group().getName(),
                    candidate.distanceKm(),
                    eligible.stream().map(w -> "\"" + w.getWorkerId() + "\"").collect(Collectors.joining(",")),
                    eligible.size(),
                    claimableSlots,
                    candidate.score()
            );

            DispatchProposal proposal = DispatchProposal.builder()
                    .shift(shift)
                    .worker(candidate.leader())
                    .groupId(candidate.group().getGroupId())
                    .matchScore(BigDecimal.valueOf(candidate.score()).setScale(2, RoundingMode.HALF_UP))
                    .scoreBreakdown(breakdownJson)
                    .status(ProposalStatus.PENDING)
                    .expiresAt(expiresAt)
                    .source(AssignmentSource.GROUP)
                    .build();

            DispatchProposal savedProposal = dispatchProposalRepository.save(proposal);
            createdCount++;

            if (notificationService != null && candidate.leader().getUser() != null && candidate.leader().getUser().getUserId() != null) {
                try {
                    String eventName = shift.getEvent() != null ? shift.getEvent().getName() : "Sự kiện CANi";
                    notificationService.createAndSend(
                            candidate.leader().getUser().getUserId(),
                            (emergency ? "🚨 GẤP " : "") + "Lời mời ca cho đội " + candidate.group().getName(),
                            "Cử 1 đến " + claimableSlots + " thành viên — ca " + shift.getJobType() + " tại " + eventName + ", thù lao " + shift.getHourlyWage() + " đ/giờ.",
                            NotificationType.PROPOSAL,
                            "PROPOSAL",
                            savedProposal != null && savedProposal.getProposalId() != null ? savedProposal.getProposalId() : shiftId,
                            Map.of("shiftId", shiftId.toString(), "type", emergency ? "EMERGENCY_GROUP_PROPOSAL" : "GROUP_PROPOSAL")
                    );
                } catch (Exception ex) {
                    log.warn("Không thể gửi thông báo FCM cho đội {}: {}", candidate.group().getName(), ex.getMessage());
                }
            }
        }

        log.info("Group Dispatch hoàn tất cho ca {}: Xét {} group, đã gửi {} đề xuất group.",
                shiftId, candidateGroups.size(), createdCount);
        return createdCount;
    }

    /**
     * Tiêu chí thành viên đủ điều kiện nhận proposal kênh group.
     * Giống passHardFilter nhưng BỎ kiểm tra isOnline (leader tự cử thành viên) và blocklist per-member.
     */
    boolean isGroupChannelMemberEligible(Worker worker, Shift shift) {
        return groupMemberEligibilityReason(worker, shift) == null;
    }

    /**
     * Kênh group: trả code lý do thành viên KHÔNG đủ điều kiện; null = đủ điều kiện.
     * Dùng chung: dispatch group (Task 2) và re-check khi leader accept (Task 3).
     */
    public String groupMemberEligibilityReason(Worker worker, Shift shift) {
        if (worker.getKycStatus() != com.cani.entity.enums.KycStatus.APPROVED) {
            return "KYC_UNAPPROVED";
        }
        if (worker.getUser() == null || worker.getUser().getStatus() != UserStatus.ACTIVE) {
            return "USER_INACTIVE";
        }
        int minRs = shift.getMinReliabilityScore() != null && shift.getMinReliabilityScore() > 0
                ? shift.getMinReliabilityScore() : MIN_RELIABILITY_SCORE;
        if (worker.getReliabilityScore() == null || worker.getReliabilityScore() < minRs) {
            return "LOW_RELIABILITY";
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
                return "LOW_EXPERIENCE";
            }
        }
        if (shift.getRequiredSkills() != null && shift.getRequiredSkills().length > 0 && workerSkillRepository != null) {
            Set<UUID> requiredSkillIds = Arrays.stream(shift.getRequiredSkills())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!requiredSkillIds.isEmpty()) {
                List<WorkerSkill> workerSkills = workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId());
                Set<UUID> haveSkillIds = workerSkills == null ? Collections.emptySet() :
                        workerSkills.stream()
                                .filter(ws -> ws.getSkill() != null && ws.getSkill().getSkillId() != null)
                                .map(ws -> ws.getSkill().getSkillId())
                                .collect(Collectors.toSet());
                if (!haveSkillIds.containsAll(requiredSkillIds)) {
                    return "MISSING_SKILLS";
                }
            }
        }
        List<AssignmentStatus> activeStatuses = List.of(
                AssignmentStatus.CONFIRMED,
                AssignmentStatus.CHECKED_IN,
                AssignmentStatus.PENDING
        );
        List<ShiftAssignment> conflicts = shiftAssignmentRepository.findConflictingAssignments(
                worker.getWorkerId(), shift.getStartTime(), shift.getEndTime(), activeStatuses);
        if (!conflicts.isEmpty()) {
            return "SHIFT_CONFLICT";
        }
        if (worker.getMinWage() != null && worker.getMinWage().compareTo(BigDecimal.ZERO) > 0) {
            if (shift.getHourlyWage() != null && shift.getHourlyWage().compareTo(worker.getMinWage()) < 0) {
                return "WAGE_BELOW_MIN";
            }
        }
        if (!isShiftCoveredByAvailability(worker, shift)) {
            return "UNAVAILABLE";
        }
        return null;
    }

    private double getConfigDouble(String key, double defaultValue) {
        if (systemConfigRepository != null) {
            return systemConfigRepository.findByKey(key)
                    .map(cfg -> {
                        try {
                            return Double.parseDouble(cfg.getValue().trim());
                        } catch (Exception ignored) {
                            return defaultValue;
                        }
                    })
                    .orElse(defaultValue);
        }
        return defaultValue;
    }

    /**
     * Tính khoảng cách Haversine giữa 2 tọa độ GPS (km)
     */
    public static double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return Double.MAX_VALUE; // Không xác định được tọa độ -> ngoài tầm phủ sóng để tránh matching nhầm
        }
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(rLat1) * Math.cos(rLat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
