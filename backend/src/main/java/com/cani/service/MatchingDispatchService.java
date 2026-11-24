package com.cani.service;

import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service Điều phối & Gợi ý việc làm thông minh (Matching Engine)
 * Áp dụng thuật toán Weighted Scoring Model (WSM) dựa trên:
 * - Khoảng cách địa lý (Haversine Formula) - Trọng số 0.4
 * - Điểm đánh giá sao trung bình (Rating) - Trọng số 0.3
 * - Kinh nghiệm làm việc (Experience) - Trọng số 0.2
 * - Điểm tin cậy (Reliability Score) - Trọng số 0.1
 * + Điểm thưởng độ khớp kỹ năng (SkillFitBonus V3: tối đa +10.0)
 * + Điểm thưởng người mới / quay lại (Fairness Boost: +10.0)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingDispatchService {

    @Autowired(required = false)
    private WorkerRepository workerRepository;

    @Autowired(required = false)
    private ShiftRepository shiftRepository;

    @Autowired(required = false)
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired(required = false)
    private DispatchProposalRepository dispatchProposalRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private BlockListService blockListService;

    @Autowired(required = false)
    private SystemConfigRepository systemConfigRepository;

    @Autowired(required = false)
    private com.cani.repository.WorkerSkillRepository workerSkillRepository;

    @Autowired(required = false)
    private com.cani.repository.WorkerAvailabilityRepository workerAvailabilityRepository;

    @Autowired(required = false)
    private WorkerGroupRepository workerGroupRepository;

    @Autowired(required = false)
    private WorkerGroupMembershipRepository workerGroupMembershipRepository;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private com.cani.repository.ShiftSkillRequirementRepository shiftSkillRequirementRepository;

    @Autowired(required = false)
    private com.cani.repository.SkillRepository skillRepository;

    @Autowired(required = false)
    private WorkerShiftEligibilityService workerShiftEligibilityService;

    @Autowired(required = false)
    private ShiftStaffingPolicy shiftStaffingPolicy;

    private ShiftStaffingPolicy getStaffingPolicy() {
        if (shiftStaffingPolicy != null) {
            return shiftStaffingPolicy;
        }
        return ShiftStaffingPolicy.withFallbackDependencies(
                shiftAssignmentRepository,
                dispatchProposalRepository);
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
                skillRepository);
    }

    private static final List<ProposalStatus> ACTIVE_PROPOSAL_STATUSES = List.of(
            ProposalStatus.PENDING, ProposalStatus.ACCEPTED);

    private static final double MAX_DISTANCE_KM = 15.0;
    private static final int MIN_RELIABILITY_SCORE = 60;
    private static final int GROUP_NO_SHOW_COOLDOWN_MINUTES = 20;
    private static final int DEFAULT_TIMEOUT_HOURS = 1;
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double DEFAULT_NEUTRAL_RATING = 3.5;
    private static final int DEFAULT_IDLE_DAYS_THRESHOLD = 14;

    @Data
    @Builder
    public static class CandidateMatch {
        private Worker worker;
        private double distanceKm;
        private double finalScore;
        private String scoreBreakdownJson;
    }

    /**
     * Kích hoạt matching hàng loạt cho toàn bộ ca làm việc ACTIVE của một sự kiện.
     * Thường được gọi sau khi Event Organizer nạp cọc thành công và Event chuyển
     * sang PUBLISHED/ACTIVE.
     */
    @Transactional
    public int dispatchForEvent(UUID eventId) {
        if (eventId == null) {
            return 0;
        }
        List<Shift> activeShifts = shiftRepository.findByEvent_EventIdAndStatus(eventId, ShiftStatus.ACTIVE);
        if (activeShifts == null || activeShifts.isEmpty()) {
            log.info("Không có ca làm việc ACTIVE nào cho sự kiện {}", eventId);
            return 0;
        }
        int totalDispatched = 0;
        for (Shift shift : activeShifts) {
            if (shift == null || !isDispatchAllowedForEvent(shift)) {
                continue;
            }
            try {
                totalDispatched += dispatchForShift(shift.getShiftId());
            } catch (Exception ex) {
                log.error("Lỗi khi dispatch ca làm việc {}: {}", shift.getShiftId(), ex.getMessage(), ex);
            }
        }
        log.info("Dispatch hoàn tất cho sự kiện {}: Đã kích hoạt cho {} ca làm việc.", eventId, activeShifts.size());
        return totalDispatched;
    }

    /**
     * UC-55, UC-56, UC-57: Kích hoạt Matching cho một ca làm việc cụ thể
     */
    @Transactional
    public int dispatchForShift(UUID shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ca làm việc ID: " + shiftId));

        if (!isDispatchAllowedForEvent(shift)) {
            log.info("Bỏ qua dispatch: Sự kiện của ca {} đang ở chế độ INVITE hoặc trạng thái không hợp lệ.", shiftId);
            return 0;
        }

        ShiftStaffingPolicy policy = getStaffingPolicy();
        ShiftStatus effectiveStatus = policy.resolveEffectiveShiftStatus(shift, Instant.now());
        if (effectiveStatus != ShiftStatus.ACTIVE) {
            log.info(
                    "Bỏ qua dispatch: Ca làm việc {} không ở trạng thái ACTIVE trước giờ bắt đầu (status={}, effective={})",
                    shiftId, shift.getStatus(), effectiveStatus);
            return 0;
        }

        if (shift.getEndTime() != null && Instant.now().isAfter(shift.getEndTime())) {
            log.info("Bỏ qua dispatch: Ca làm việc {} đã qua giờ kết thúc ({}).", shiftId, shift.getEndTime());
            return 0;
        }

        int needed = shift.getRemainingWorkers() != null ? shift.getRemainingWorkers() : shift.getRequiredWorkers();
        if (needed <= 0) {
            log.info("Ca làm việc {} đã đủ nhân sự.", shiftId);
            return 0;
        }

        // Tọa độ mục tiêu của ca làm việc (Bắt buộc hợp lệ, tuyệt đối không fallback 0
        // km)
        Double targetLat = shift.getLocationLat() != null ? shift.getLocationLat()
                : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
        Double targetLng = shift.getLocationLng() != null ? shift.getLocationLng()
                : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);
        if (!isValidCoordinate(targetLat, targetLng)) {
            log.warn("Bỏ qua dispatch ca {}: thiếu tọa độ GPS hợp lệ (lat={}, lng={}).", shiftId, targetLat, targetLng);
            return 0;
        }

        // Bán kính tối đa của ca làm việc (hoặc mặc định 15km)
        double maxAllowedDistanceKm = (shift.getMaxDistanceKm() != null && shift.getMaxDistanceKm().doubleValue() > 0)
                ? shift.getMaxDistanceKm().doubleValue()
                : MAX_DISTANCE_KM;

        // 1. Chỉ lấy danh sách Worker đang Online (tuyệt đối không fallback full-table
        // scan Worker offline)
        List<Worker> onlineWorkers = workerRepository.findByIsOnlineTrue();
        if (onlineWorkers == null || onlineWorkers.isEmpty()) {
            log.info("Bỏ qua dispatch ca {}: hiện không có Worker nào đang Online.", shiftId);
            return 0;
        }

        List<CandidateMatch> matchedCandidates = new ArrayList<>();

        for (Worker worker : onlineWorkers) {
            // 2. Hard Filter
            if (!passHardFilter(worker, shift)) {
                continue;
            }

            // 3. Kiểm tra tọa độ Worker & tính khoảng cách Haversine thực tế
            if (!isValidCoordinate(worker.getBaseLat(), worker.getBaseLng())) {
                log.debug("Loại trừ worker {}: thiếu tọa độ GPS hợp lệ (lat={}, lng={}).",
                        worker.getWorkerId(), worker.getBaseLat(), worker.getBaseLng());
                continue;
            }
            double distanceKm = calculateDistance(targetLat, targetLng, worker.getBaseLat(), worker.getBaseLng());
            if (!Double.isFinite(distanceKm)) {
                continue;
            }

            double workerMaxDist = (worker.getMaxDistanceKm() != null && worker.getMaxDistanceKm() > 0)
                    ? worker.getMaxDistanceKm()
                    : maxAllowedDistanceKm;
            double effectiveAllowedDistanceKm = Math.min(maxAllowedDistanceKm, workerMaxDist);

            if (distanceKm > effectiveAllowedDistanceKm) {
                continue;
            }

            // 4. Soft Ranking Score
            CandidateMatch match = calculateSoftScore(worker, distanceKm, effectiveAllowedDistanceKm, shift);
            matchedCandidates.add(match);
        }

        // 5. Sắp xếp ứng viên theo điểm số giảm dần (nếu bằng điểm thì ưu tiên gần hơn)
        matchedCandidates.sort(Comparator.comparingDouble(CandidateMatch::getFinalScore).reversed()
                .thenComparingDouble(CandidateMatch::getDistanceKm));

        // 6-7. Loại Worker đã có Proposal PENDING/ACCEPTED hoặc đã có Assignment trước
        // khi cắt Top N
        List<CandidateMatch> eligibleCandidates = matchedCandidates.stream()
                .filter(candidate -> !hasExistingProposalOrAssignment(shiftId, candidate.getWorker().getWorkerId()))
                .toList();

        // 8. Dispatch ratio: tuyển N người -> chọn Top min(N * 2 + 3, số ứng viên hợp
        // lệ)
        int targetDispatchCount = Math.max(needed * 2 + 3, needed);
        List<CandidateMatch> topCandidates = eligibleCandidates.stream()
                .limit(targetDispatchCount)
                .collect(Collectors.toList());

        int createdCount = 0;
        Instant expiresAt = Instant.now().plus(Duration.ofHours(DEFAULT_TIMEOUT_HOURS));

        for (CandidateMatch candidate : topCandidates) {
            if (!isShiftReadyForProposal(shift, false)) {
                log.info("Dừng dispatch sớm cho ca {}: ca không còn ACTIVE hoặc đã đủ nhân sự.", shiftId);
                break;
            }

            Worker worker = candidate.getWorker();
            // Kiểm tra lại trước khi ghi DB để hạn chế race condition
            if (hasExistingProposalOrAssignment(shiftId, worker.getWorkerId())) {
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
                            "Bạn vừa nhận được lời mời làm việc tại \"" + eventName + "\" với mức thù lao "
                                    + shift.getHourlyWage() + " đ/giờ.",
                            NotificationType.PROPOSAL,
                            "PROPOSAL",
                            savedProposal != null && savedProposal.getProposalId() != null
                                    ? savedProposal.getProposalId()
                                    : shiftId,
                            Map.of("shiftId", shiftId.toString(), "type", "NEW_PROPOSAL"));
                } catch (Exception ex) {
                    log.warn("Không thể gửi thông báo FCM cho đề xuất ca làm: {}", ex.getMessage());
                }
            }
        }

        if (matchedCandidates.isEmpty()) {
            log.warn("Matching ca {}: KHÔNG TÌM THẤY ứng viên nào đạt điều kiện. "
                    + "Kiểm tra: worker đang online? kycStatus APPROVED? có GPS hợp lệ? requiredSkills khớp worker_skills? "
                    + "khung giờ rảnh trùng ca? minWage không vượt lương ca? blocklist? "
                    + "Tổng worker online đã xét: {}",
                    shiftId, onlineWorkers.size());
        }

        createdCount += dispatchGroupsForShift(shiftId, false);

        log.info(
                "Matching hoàn tất cho ca {}: Tìm thấy {} ứng viên phù hợp ({} chưa trùng), đã gửi {} đề xuất (Proposals).",
                shiftId, matchedCandidates.size(), eligibleCandidates.size(), createdCount);
        return createdCount;
    }

    /**
     * Reverse-Matching: Khi Worker bật trạng thái Online (isOnline = true),
     * tự động quét các ca ACTIVE còn thiếu người và chưa hết hạn để gửi
     * DispatchProposal phù hợp.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dispatchActiveShiftsForWorker(Worker worker) {
        if (worker == null) {
            return 0;
        }
        // Tải lại thực thể Worker trong transaction mới để tránh
        // LazyInitializationException hoặc rollback ngược transaction cha
        if (worker.getWorkerId() != null) {
            worker = workerRepository.findById(worker.getWorkerId()).orElse(worker);
        }
        if (!Boolean.TRUE.equals(worker.getIsOnline())
                || worker.getKycStatus() != com.cani.entity.enums.KycStatus.APPROVED
                || worker.getUser() == null || worker.getUser().getStatus() != UserStatus.ACTIVE) {
            return 0;
        }
        if (!isValidCoordinate(worker.getBaseLat(), worker.getBaseLng())) {
            log.info("Bỏ qua reverse-matching cho worker {}: thiếu tọa độ GPS hợp lệ.", worker.getWorkerId());
            return 0;
        }
        List<Shift> activeShifts = shiftRepository.findByStatus(ShiftStatus.ACTIVE);
        if (activeShifts == null || activeShifts.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(DEFAULT_TIMEOUT_HOURS));
        int createdCount = 0;

        for (Shift shift : activeShifts) {
            if (!isDispatchAllowedForEvent(shift)) {
                continue;
            }
            if (shift.getEndTime() != null && now.isAfter(shift.getEndTime())) {
                continue;
            }
            int needed = shift.getRemainingWorkers() != null ? shift.getRemainingWorkers() : shift.getRequiredWorkers();
            if (needed <= 0) {
                continue;
            }
            if (!passHardFilter(worker, shift, WorkerShiftEligibilityService.MatchingChannel.REVERSE_MATCHING)) {
                continue;
            }
            Double targetLat = shift.getLocationLat() != null ? shift.getLocationLat()
                    : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
            Double targetLng = shift.getLocationLng() != null ? shift.getLocationLng()
                    : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);
            if (!isValidCoordinate(targetLat, targetLng)) {
                continue;
            }
            double maxAllowedDistanceKm = (shift.getMaxDistanceKm() != null
                    && shift.getMaxDistanceKm().doubleValue() > 0)
                            ? shift.getMaxDistanceKm().doubleValue()
                            : MAX_DISTANCE_KM;
            double distanceKm = calculateDistance(targetLat, targetLng, worker.getBaseLat(), worker.getBaseLng());
            if (!Double.isFinite(distanceKm)) {
                continue;
            }
            double workerMaxDist = (worker.getMaxDistanceKm() != null && worker.getMaxDistanceKm() > 0)
                    ? worker.getMaxDistanceKm()
                    : maxAllowedDistanceKm;
            double effectiveAllowedDistanceKm = Math.min(maxAllowedDistanceKm, workerMaxDist);
            if (distanceKm > effectiveAllowedDistanceKm) {
                continue;
            }
            if (hasExistingProposalOrAssignment(shift.getShiftId(), worker.getWorkerId())) {
                continue;
            }

            CandidateMatch match = calculateSoftScore(worker, distanceKm, effectiveAllowedDistanceKm, shift);
            DispatchProposal proposal = DispatchProposal.builder()
                    .shift(shift)
                    .worker(worker)
                    .matchScore(BigDecimal.valueOf(match.getFinalScore()).setScale(2, RoundingMode.HALF_UP))
                    .scoreBreakdown(match.getScoreBreakdownJson())
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
                            "Bạn vừa nhận được lời mời làm việc tại \"" + eventName + "\" với mức thù lao "
                                    + shift.getHourlyWage() + " đ/giờ.",
                            NotificationType.PROPOSAL,
                            "PROPOSAL",
                            savedProposal != null && savedProposal.getProposalId() != null
                                    ? savedProposal.getProposalId()
                                    : shift.getShiftId(),
                            Map.of("shiftId", shift.getShiftId().toString(), "type", "NEW_PROPOSAL"));
                } catch (Exception ex) {
                    log.warn("Không thể gửi thông báo FCM reverse-matching: {}", ex.getMessage());
                }
            }
        }
        if (createdCount > 0) {
            log.info("Reverse-matching khi Worker {} online: đã tạo {} DispatchProposal mới.", worker.getWorkerId(),
                    createdCount);
        }
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
        // Orphan rule: group PENDING trong 24h, leader hiện tại khác worker đã lưu
        // (batch load groups).
        List<DispatchProposal> groupRecent = dispatchProposalRepository
                .findByStatusAndGroupIdIsNotNullAndCreatedAtAfter(
                        ProposalStatus.PENDING, now.minus(Duration.ofHours(24)));
        Map<UUID, WorkerGroup> groups = workerGroupRepository
                .findAllById(groupRecent.stream().map(DispatchProposal::getGroupId).distinct().toList())
                .stream().collect(Collectors.toMap(WorkerGroup::getGroupId, g -> g));
        stale.addAll(groupRecent.stream()
                .filter(p -> p.getExpiresAt() == null || !p.getExpiresAt().isBefore(now))
                .filter(p -> p.getWorker() != null)
                .filter(p -> {
                    WorkerGroup group = groups.get(p.getGroupId());
                    return group != null
                            && (!p.getWorker().getWorkerId().equals(group.getLeaderWorkerId())
                                    || !Boolean.TRUE.equals(group.getIsActive()));
                })
                .toList());
        if (stale.isEmpty()) {
            return;
        }
        stale.forEach(p -> p.setStatus(ProposalStatus.EXPIRED));
        dispatchProposalRepository.saveAll(stale);
        log.info("Đã chuyển {} proposal PENDING hết hạn/đội trưởng đã thay đổi/nhóm ngừng hoạt động sang EXPIRED.",
                stale.size());
    }

    /**
     * Hard Filter: Loại bỏ các ứng viên không đạt tiêu chuẩn tối thiểu
     */
    private boolean passHardFilter(Worker worker, Shift shift) {
        return passHardFilter(worker, shift, WorkerShiftEligibilityService.MatchingChannel.MATCHING_DISPATCH);
    }

    private boolean passHardFilter(Worker worker, Shift shift, WorkerShiftEligibilityService.MatchingChannel channel) {
        return getEligibilityService().evaluate(worker, shift, channel).eligible();
    }

    /**
     * Kiểm tra ca làm có nằm trong lịch rảnh cố định hàng tuần của Worker hay
     * không.
     * Dùng cho kênh Group và kiểm tra tính tương thích ngược.
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

            // Trường hợp 1: Slot trong cùng 1 ngày (ví dụ 08:00 -> 17:00 hoặc 18:00 ->
            // 23:00)
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
     * Soft Ranking Score: Tính điểm xếp hạng ứng viên dựa trên 4 thành phần + Điểm
     * thưởng
     */
    private CandidateMatch calculateSoftScore(Worker worker, double distanceKm, double maxAllowedDistanceKm,
            Shift shift) {
        double effectiveMaxDist = (Double.isFinite(maxAllowedDistanceKm) && maxAllowedDistanceKm > 0)
                ? maxAllowedDistanceKm
                : MAX_DISTANCE_KM;
        double safeDistanceKm = Double.isFinite(distanceKm) ? Math.max(0.0, distanceKm) : effectiveMaxDist;

        // 1. Distance Score: 100 - (distance / maxAllowedDistanceKm) * 100 (Trọng số
        // 0.4)
        double distanceScore = clamp(100.0 - (safeDistanceKm / effectiveMaxDist) * 100.0, 0.0, 100.0);

        // 2. Rating Score: Nếu Worker chưa có rating history (ratingCount <= 0), dùng
        // mức trung tính (3.5/5.0 = 70 điểm)
        double neutralRating = clamp(getConfigDouble("matching.neutral_unrated_rating", DEFAULT_NEUTRAL_RATING), 0.0,
                5.0);
        boolean hasRatingHistory = worker.getRatingCount() != null && worker.getRatingCount() > 0
                && worker.getRatingAvg() != null;
        double rating = hasRatingHistory ? clamp(worker.getRatingAvg().doubleValue(), 0.0, 5.0) : neutralRating;
        double ratingScore = clamp((rating / 5.0) * 100.0, 0.0, 100.0);

        // 3. Experience Score: (completed_shifts / 10) * 100 (Trọng số 0.2)
        int completedShifts = worker.getTotalShiftsCompleted() != null ? Math.max(0, worker.getTotalShiftsCompleted())
                : 0;
        double expScore = clamp((completedShifts / 10.0) * 100.0, 0.0, 100.0);

        // 4. Reliability Score: Trọng số 0.1
        double rsScore = worker.getReliabilityScore() != null ? clamp(worker.getReliabilityScore(), 0.0, 100.0) : 60.0;

        // Bonus: Người mới hoặc nghỉ lâu thực tế (UC-60, UC-61: Fairness Boost)
        double newWorkerBoostConfig = Math.max(0.0, getConfigDouble("fairness.new_worker_boost", 10.0));
        double idleWorkerBoostConfig = Math.max(0.0, getConfigDouble("fairness.idle_worker_boost", 10.0));

        double newWorkerBonus = (completedShifts < 3) ? newWorkerBoostConfig : 0.0;
        double idleBonus = isEligibleForIdleBoost(worker, completedShifts) ? idleWorkerBoostConfig : 0.0;

        // SkillFitBonus V3 = 6 * RequiredQuality + 4 * PreferredFit (tổng tối đa 10.0)
        WorkerShiftEligibilityService.SkillFitResult skillFit = getEligibilityService().evaluateSkillFit(worker, shift);
        double requiredSkillQuality = skillFit.requiredQuality();
        double preferredSkillFit = skillFit.preferredFit();
        double skillFitBonus = skillFit.skillFitBonus();
        String matchedSkillIdsJson = skillFit.matchedSkillIds().stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(",", "[", "]"));

        double baseScore = (0.4 * distanceScore) + (0.3 * ratingScore) + (0.2 * expScore) + (0.1 * rsScore);
        double rawFinalScore = baseScore + newWorkerBonus + idleBonus + skillFitBonus;
        double finalScore = Double.isFinite(rawFinalScore) ? rawFinalScore : 0.0;

        String breakdownJson = String.format(
                Locale.US,
                "{\"algorithmVersion\":\"v3.0\",\"distanceKm\":%.2f,\"baseScore\":%.2f,\"distScore\":%.1f,\"ratingScore\":%.1f,\"expScore\":%.1f,\"rsScore\":%.1f,\"newWorkerBonus\":%.1f,\"idleBonus\":%.1f,\"requiredSkillQuality\":%.2f,\"preferredSkillFit\":%.2f,\"skillFitBonus\":%.2f,\"skillBonus\":%.2f,\"finalScore\":%.2f,\"matchedSkillIds\":%s}",
                safeDistanceKm, baseScore, distanceScore, ratingScore, expScore, rsScore,
                newWorkerBonus, idleBonus, requiredSkillQuality, preferredSkillFit, skillFitBonus, skillFitBonus,
                finalScore, matchedSkillIdsJson);

        return CandidateMatch.builder()
                .worker(worker)
                .distanceKm(safeDistanceKm)
                .finalScore(finalScore)
                .scoreBreakdownJson(breakdownJson)
                .build();
    }

    private boolean isEligibleForIdleBoost(Worker worker, int completedShifts) {
        if (completedShifts < 3 || !Boolean.TRUE.equals(worker.getIsOnline()) || worker.getWorkerId() == null) {
            return false;
        }
        int idleDays = (int) Math.max(1.0,
                getConfigDouble("fairness.idle_days_threshold", DEFAULT_IDLE_DAYS_THRESHOLD));
        Instant idleCutoff = Instant.now().minus(Duration.ofDays(idleDays));
        List<ShiftAssignment> history = shiftAssignmentRepository
                .findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId());
        if (history != null && !history.isEmpty()) {
            ShiftAssignment latest = history.get(0);
            Instant lastActivity = (latest.getShift() != null && latest.getShift().getEndTime() != null)
                    ? latest.getShift().getEndTime()
                    : latest.getCreatedAt();
            return lastActivity != null && lastActivity.isBefore(idleCutoff);
        }
        return worker.getUpdatedAt() != null && worker.getUpdatedAt().isBefore(idleCutoff);
    }

    /**
     * UC-59: Kích hoạt điều phối khẩn cấp thủ công (EO bấm trước giờ bắt đầu ca)
     */
    @Transactional
    public int triggerEmergencyDispatch(UUID shiftId, Double customRadiusKm) {
        Shift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseGet(() -> shiftRepository.findById(shiftId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ca làm việc ID: " + shiftId)));

        ShiftStaffingPolicy policy = getStaffingPolicy();
        Instant now = Instant.now();

        if (!isDispatchAllowedForEvent(shift)) {
            log.warn("Từ chối Emergency Dispatch: Sự kiện của ca {} đang ở chế độ INVITE hoặc không hợp lệ.", shiftId);
            return 0;
        }

        ShiftStatus effectiveStatus = policy.resolveEffectiveShiftStatus(shift, now);
        if (effectiveStatus != ShiftStatus.ACTIVE
                || (shift.getStartTime() != null && !now.isBefore(shift.getStartTime()))) {
            log.warn(
                    "Không thể kích hoạt Emergency Dispatch thủ công: Ca làm việc {} không ở trạng thái ACTIVE trước giờ bắt đầu (status={}, effective={}, startTime={})",
                    shiftId, shift.getStatus(), effectiveStatus, shift.getStartTime());
            return 0;
        }

        int needed = Math.max(0, policy.resolveRequiredWorkers(shift) - policy.resolveConfirmedWorkersCount(shift));
        if (needed <= 0) {
            return 0;
        }

        return executeEmergencyDispatch(shift, policy, customRadiusKm, needed);
    }

    /**
     * UC-59 / UC-88: Kích hoạt điều phối khẩn cấp tự động từ hệ thống (No-show hoặc
     * Worker tự hủy làm thiếu người).
     * Cho phép sau giờ bắt đầu ca nếu vẫn còn đủ thời gian làm việc (>= 30 phút),
     * nhưng dừng khi ca kết thúc hoặc bị hủy.
     */
    @Transactional
    public int triggerEmergencyDispatchForSystem(UUID shiftId, Double customRadiusKm) {
        Shift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseGet(() -> shiftRepository.findById(shiftId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ca làm việc ID: " + shiftId)));

        ShiftStaffingPolicy policy = getStaffingPolicy();
        Instant now = Instant.now();

        ShiftStaffingPolicy.AutoEmergencyEligibility autoEligibility = policy.evaluateAutoEmergencyEligibility(shift,
                now);
        if (!autoEligibility.eligible()) {
            log.info("Bỏ qua Auto Emergency Dispatch cho ca {}: {}", shiftId, autoEligibility.blockReason());
            return 0;
        }

        if (autoEligibility.effectiveStatus() == ShiftStatus.IN_PROGRESS
                && (shift.getStatus() == ShiftStatus.ACTIVE || shift.getStatus() == ShiftStatus.FULLY_STAFFED)) {
            shift.setStatus(ShiftStatus.IN_PROGRESS);
        }

        return executeEmergencyDispatch(shift, policy, customRadiusKm, autoEligibility.missingWorkers());
    }

    private int executeEmergencyDispatch(Shift shift, ShiftStaffingPolicy policy, Double customRadiusKm, int needed) {
        UUID shiftId = shift.getShiftId();

        Double targetLat = shift.getLocationLat() != null ? shift.getLocationLat()
                : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
        Double targetLng = shift.getLocationLng() != null ? shift.getLocationLng()
                : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);
        if (!isValidCoordinate(targetLat, targetLng)) {
            log.warn("Bỏ qua Emergency Dispatch ca {}: thiếu tọa độ GPS hợp lệ (lat={}, lng={}).", shiftId, targetLat,
                    targetLng);
            return 0;
        }

        boolean shiftDirty = false;
        if (!Boolean.TRUE.equals(shift.getEmergency())) {
            shift.setEmergency(true);
            shiftDirty = true;
        }
        if (shift.getStatus() == ShiftStatus.IN_PROGRESS || shiftDirty) {
            shiftRepository.save(shift);
        }

        double emergencyRadius = policy.resolveEffectiveEmergencyRadiusKm(customRadiusKm);
        log.info("KÍCH HOẠT EMERGENCY DISPATCH cho ca {}: Bán kính mở rộng {} km (Capped max 50km)", shiftId,
                emergencyRadius);

        List<Worker> onlineWorkers = workerRepository.findByIsOnlineTrue();
        if (onlineWorkers == null || onlineWorkers.isEmpty()) {
            log.info("Bỏ qua Emergency Dispatch ca {}: không có Worker nào đang Online.", shiftId);
            return dispatchGroupsForShift(shiftId, true, emergencyRadius);
        }

        List<CandidateMatch> matchedCandidates = new ArrayList<>();
        for (Worker worker : onlineWorkers) {
            if (!passHardFilter(worker, shift, WorkerShiftEligibilityService.MatchingChannel.EMERGENCY_DISPATCH)) {
                continue;
            }
            if (!isValidCoordinate(worker.getBaseLat(), worker.getBaseLng())) {
                log.debug("Emergency Dispatch loại worker {}: thiếu tọa độ GPS hợp lệ.", worker.getWorkerId());
                continue;
            }
            double distanceKm = calculateDistance(targetLat, targetLng, worker.getBaseLat(), worker.getBaseLng());
            if (!Double.isFinite(distanceKm) || distanceKm > emergencyRadius) {
                continue;
            }
            CandidateMatch match = calculateSoftScore(worker, distanceKm, emergencyRadius, shift);
            matchedCandidates.add(match);
        }

        matchedCandidates.sort(Comparator.comparingDouble(CandidateMatch::getFinalScore).reversed()
                .thenComparingDouble(CandidateMatch::getDistanceKm));

        // Loại ứng viên đã có Proposal PENDING/ACCEPTED hoặc Assignment trước khi lấy
        // Top N
        List<CandidateMatch> eligibleCandidates = matchedCandidates.stream()
                .filter(candidate -> !hasExistingProposalOrAssignment(shiftId, candidate.getWorker().getWorkerId()))
                .toList();

        int targetDispatchCount = Math.max(needed * 3 + 5, needed);
        List<CandidateMatch> topCandidates = eligibleCandidates.stream()
                .limit(targetDispatchCount)
                .toList();

        int createdCount = 0;
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(30)); // Khẩn cấp: Hết hạn sau 30 phút

        for (CandidateMatch candidate : topCandidates) {
            if (!isShiftReadyForProposal(shift, true)) {
                break;
            }
            Worker worker = candidate.getWorker();
            if (hasExistingProposalOrAssignment(shiftId, worker.getWorkerId())) {
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
                            "Ca làm việc khẩn cấp tại \"" + eventName + "\" đang thiếu nhân sự! Thù lao "
                                    + shift.getHourlyWage() + " đ/giờ.",
                            NotificationType.PROPOSAL,
                            "PROPOSAL",
                            savedProposal != null && savedProposal.getProposalId() != null
                                    ? savedProposal.getProposalId()
                                    : shiftId,
                            Map.of("shiftId", shiftId.toString(), "type", "EMERGENCY_PROPOSAL"));
                } catch (Exception ex) {
                    log.warn("Lỗi gửi thông báo FCM khẩn cấp: {}", ex.getMessage());
                }
            }
        }

        createdCount += dispatchGroupsForShift(shiftId, true, emergencyRadius);

        log.info("Emergency Dispatch hoàn tất: Đã gửi {} đề xuất khẩn cấp cho ca {}", createdCount, shiftId);
        return createdCount;
    }

    @Transactional
    public int dispatchGroupsForShift(UUID shiftId, boolean emergency) {
        return dispatchGroupsForShift(shiftId, emergency, null);
    }

    /**
     * Kênh Group Dispatch: gửi 1 lời mời cho tối đa 5 đội (Group) có thành viên đủ
     * điều kiện.
     * Proposal gán worker = leader nhóm; FCM gửi leader để cử 1 đến
     * [claimableSlots] thành viên.
     */
    @Transactional
    public int dispatchGroupsForShift(UUID shiftId, boolean emergency, Double customRadiusKm) {
        Shift shift = shiftRepository.findById(shiftId).orElse(null);
        if (shift == null) {
            return 0;
        }
        ShiftStaffingPolicy policy = getStaffingPolicy();
        ShiftStatus effectiveStatus = policy.resolveEffectiveShiftStatus(shift, Instant.now());
        boolean statusAllowed = emergency
                ? (effectiveStatus == ShiftStatus.ACTIVE || effectiveStatus == ShiftStatus.IN_PROGRESS)
                : (effectiveStatus == ShiftStatus.ACTIVE);
        if (!statusAllowed) {
            return 0;
        }
        if (!isDispatchAllowedForEvent(shift)) {
            log.info("Bỏ qua group dispatch: Sự kiện của ca {} đang ở chế độ INVITE hoặc trạng thái không hợp lệ.",
                    shiftId);
            return 0;
        }
        int remaining = shift.getRemainingWorkers() != null
                ? Math.max(0, shift.getRemainingWorkers())
                : Math.max(0, policy.resolveRequiredWorkers(shift) - policy.resolveConfirmedWorkersCount(shift));
        if (remaining <= 0) {
            log.info("Ca làm việc {} đã đủ nhân sự, bỏ qua kênh group dispatch.", shiftId);
            return 0;
        }

        if (workerGroupRepository == null || workerGroupMembershipRepository == null) {
            return 0;
        }

        Double targetLat = shift.getLocationLat() != null ? shift.getLocationLat()
                : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
        Double targetLng = shift.getLocationLng() != null ? shift.getLocationLng()
                : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);
        if (!isValidCoordinate(targetLat, targetLng)) {
            log.warn("Bỏ qua group dispatch ca {}: thiếu tọa độ GPS hợp lệ (lat={}, lng={}).", shiftId, targetLat,
                    targetLng);
            return 0;
        }

        // shortcut: xét toàn bộ group active in-memory; mô hình party group có
        // tuổi đời ngắn nên số active luôn nhỏ. Nếu someday số group active vượt
        // ~2000 thì thêm pre-filter SQL theo vùng địa lý (bounding box quanh venue).
        List<WorkerGroup> candidateGroups = workerGroupRepository.findAllByIsActiveTrue();
        if (candidateGroups.isEmpty()) {
            return 0;
        }

        // Batch tải membership ACTIVE của các group -> map groupId -> members
        List<WorkerGroupMembership> memberships = workerGroupMembershipRepository
                .findByWorkerGroup_GroupIdInAndStatus(candidateGroups.stream().map(WorkerGroup::getGroupId).toList(),
                        MembershipStatus.ACTIVE);
        Map<UUID, List<WorkerGroupMembership>> membershipsByGroup = memberships.stream()
                .filter(m -> m.getWorkerGroup() != null)
                .collect(Collectors.groupingBy(m -> m.getWorkerGroup().getGroupId()));

        Instant noShowCutoff = Instant.now().minus(Duration.ofMinutes(GROUP_NO_SHOW_COOLDOWN_MINUTES));
        Set<UUID> recentNoShowWorkerIds = new HashSet<>(
                shiftAssignmentRepository.findWorkerIdsByStatusAndUpdatedAtAfter(AssignmentStatus.NO_SHOW,
                        noShowCutoff));

        List<GroupCandidate> groupCandidates = new ArrayList<>();
        for (WorkerGroup group : candidateGroups) {
            List<WorkerGroupMembership> groupMemberships = membershipsByGroup.getOrDefault(group.getGroupId(),
                    Collections.emptyList());
            boolean hasGhostMember = groupMemberships.stream().anyMatch(m -> m.getWorker() == null);
            if (hasGhostMember) {
                log.warn("Nhóm {} có membership ACTIVE không có worker, loại cả nhóm khỏi dispatch",
                        group.getGroupId());
                continue;
            }
            int packSize = groupMemberships.size();
            if (packSize == 0) {
                continue;
            }

            // Ca không đủ chỗ cho toàn bộ tổ đội -> bỏ qua, không partial-fill
            if (remaining < packSize) {
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
                    && group.getLastNoShowAt().isAfter(noShowCutoff)) {
                continue;
            }

            // F3: Member-level no-show check trong 20 phút qua (chống giải tán lập lại nhóm
            // mới để né cooldown)
            boolean hasNoShowMember = groupMemberships.stream()
                    .filter(m -> m.getWorker() != null)
                    .anyMatch(m -> recentNoShowWorkerIds.contains(m.getWorker().getWorkerId()));
            if (hasNoShowMember) {
                log.debug("Nhóm {} có thành viên vi phạm no-show trong 20 phút qua, loại cả nhóm khỏi dispatch",
                        group.getGroupId());
                continue;
            }

            if (!dispatchProposalRepository
                    .findByShift_ShiftIdAndGroupIdAndStatusIn(shiftId, group.getGroupId(), ACTIVE_PROPOSAL_STATUSES)
                    .isEmpty()) {
                continue;
            }

            // Eligibility nguyên tử: Toàn bộ thành viên pack phải đủ điều kiện
            List<Worker> eligible = groupMemberships.stream()
                    .map(WorkerGroupMembership::getWorker)
                    .filter(Objects::nonNull)
                    .filter(w -> isGroupChannelMemberEligible(w, shift))
                    .toList();
            if (eligible.size() != packSize) {
                // 1 thành viên fail -> loại toàn bộ pack, không fallback subset
                continue;
            }

            // Distance nguyên tử: Toàn bộ thành viên phải có tọa độ hợp lệ và không vượt
            // quá bán kính cho phép (khẩn cấp mở rộng theo Emergency radius)
            double effectiveMaxDist = emergency
                    ? policy.resolveEffectiveEmergencyRadiusKm(customRadiusKm)
                    : ((shift.getMaxDistanceKm() != null && shift.getMaxDistanceKm().doubleValue() > 0)
                            ? shift.getMaxDistanceKm().doubleValue()
                            : MAX_DISTANCE_KM);

            if (targetLat == null || targetLng == null) {
                continue;
            }

            List<Double> distances = new ArrayList<>();
            boolean locationDisqualified = false;
            for (Worker w : eligible) {
                if (w.getBaseLat() == null || w.getBaseLng() == null) {
                    locationDisqualified = true;
                    break;
                }
                double d = calculateDistance(targetLat, targetLng, w.getBaseLat(), w.getBaseLng());
                if (d == Double.MAX_VALUE || d > effectiveMaxDist) {
                    locationDisqualified = true;
                    break;
                }
                distances.add(d);
            }

            if (locationDisqualified || distances.size() != packSize) {
                continue;
            }

            double medianDist = calculateMedian(distances);
            double p90Dist = calculatePercentile(distances, 90.0);
            double maxDist = Collections.max(distances);

            // Scoring distance pack = 0.6 * normalize(median) + 0.4 * normalize(p90)
            double normMedian = Math.max(0.0, 100.0 - (medianDist / effectiveMaxDist) * 100.0);
            double normP90 = Math.max(0.0, 100.0 - (p90Dist / effectiveMaxDist) * 100.0);
            double distanceScore = 0.6 * normMedian + 0.4 * normP90;

            double avgRatingVal = (group.getAvgRating() == null || group.getAvgRating().signum() == 0)
                    ? 5.0
                    : group.getAvgRating().doubleValue();

            double groupScore = 0.35 * distanceScore
                    + 0.25 * Math.min(100.0, (avgRatingVal / 5.0) * 100.0)
                    + 0.2 * Math.min(100.0, eligible.stream()
                            .mapToDouble(
                                    w -> (double) (w.getTotalShiftsCompleted() != null ? w.getTotalShiftsCompleted()
                                            : 0))
                            .average().orElse(0.0) / 10.0 * 100.0)
                    + 0.1 * Math.min(100.0, group.getReliabilityScore() != null ? group.getReliabilityScore() : 80.0);

            // Bonus +10: ít nhất 1 member có skill verified nằm trong requiredSkills của ca
            if (shift.getRequiredSkills() != null && shift.getRequiredSkills().length > 0
                    && workerSkillRepository != null) {
                Set<UUID> reqIds = Arrays.stream(shift.getRequiredSkills()).filter(Objects::nonNull)
                        .collect(Collectors.toSet());
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

            // Bonus +20: Tổ đội vừa khít số lượng slot ca đang cần (Perfect-Fit Squad
            // Bonus)
            if (packSize == remaining) {
                groupScore += 20.0;
            }

            groupCandidates.add(
                    new GroupCandidate(group, leader, eligible, groupScore, medianDist, p90Dist, maxDist, packSize));
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
            int packSize = candidate.packSize();
            int claimableSlots = packSize;

            String breakdownJson = String.format(
                    Locale.US,
                    "{\"groupId\":\"%s\",\"groupName\":\"%s\",\"distanceMedianKm\":%.2f,\"distanceP90Km\":%.2f,\"distanceMaxKm\":%.2f,\"packWorkerIds\":[%s],\"packSize\":%d,\"claimableSlots\":%d,\"eligibleMemberWorkerIds\":[%s],\"eligibleCount\":%d,\"finalScore\":%.2f}",
                    candidate.group().getGroupId(),
                    candidate.group().getName(),
                    candidate.medianDist(),
                    candidate.p90Dist(),
                    candidate.maxDist(),
                    eligible.stream().map(w -> "\"" + w.getWorkerId() + "\"").collect(Collectors.joining(",")),
                    packSize,
                    claimableSlots,
                    eligible.stream().map(w -> "\"" + w.getWorkerId() + "\"").collect(Collectors.joining(",")),
                    eligible.size(),
                    candidate.score());

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

            if (notificationService != null && candidate.leader().getUser() != null
                    && candidate.leader().getUser().getUserId() != null) {
                UUID leaderUserId = candidate.leader().getUser().getUserId();
                String groupName = candidate.group().getName();
                String eventName = shift.getEvent() != null ? shift.getEvent().getName() : "Sự kiện CANi";
                String title = (emergency ? "🚨 GẤP " : "") + "Lời mời ca cho đội " + groupName;
                String body = "Đội " + groupName + " (" + packSize + " người) — ca " + shift.getJobType() + " tại "
                        + eventName + ", thù lao " + shift.getHourlyWage() + " đ/giờ — xác nhận trọn đội.";
                UUID refId = savedProposal != null && savedProposal.getProposalId() != null
                        ? savedProposal.getProposalId()
                        : shiftId;
                Map<String, String> data = Map.of("shiftId", shiftId.toString(), "type",
                        emergency ? "EMERGENCY_GROUP_PROPOSAL" : "GROUP_PROPOSAL");

                Runnable notifyTask = () -> {
                    try {
                        notificationService.createAndSend(
                                leaderUserId,
                                title,
                                body,
                                NotificationType.PROPOSAL,
                                "PROPOSAL",
                                refId,
                                data);
                    } catch (Exception ex) {
                        log.warn("Không thể gửi thông báo FCM cho đội {}: {}", groupName, ex.getMessage());
                    }
                };

                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            notifyTask.run();
                        }
                    });
                } else {
                    notifyTask.run();
                }
            }
        }

        log.info("Group Dispatch hoàn tất cho ca {}: Xét {} group, đã gửi {} đề xuất group.",
                shiftId, candidateGroups.size(), createdCount);
        return createdCount;
    }

    /**
     * Tiêu chí thành viên đủ điều kiện nhận proposal kênh group.
     * Giống passHardFilter nhưng BỎ kiểm tra isOnline (leader tự cử thành viên) và
     * blocklist per-member.
     */

    boolean isGroupChannelMemberEligible(Worker worker, Shift shift) {
        return groupMemberEligibilityReason(worker, shift) == null;
    }

    /**
     * Kênh group: trả code lý do thành viên KHÔNG đủ điều kiện; null = đủ điều
     * kiện.
     * Dùng chung: dispatch group và re-check khi leader accept.
     */
    public String groupMemberEligibilityReason(Worker worker, Shift shift) {
        if (worker.getKycStatus() != com.cani.entity.enums.KycStatus.APPROVED) {
            return "KYC_UNAPPROVED";
        }
        if (worker.getUser() == null || worker.getUser().getStatus() != UserStatus.ACTIVE) {
            return "USER_INACTIVE";
        }
        int minRs = shift.getMinReliabilityScore() != null && shift.getMinReliabilityScore() > 0
                ? shift.getMinReliabilityScore()
                : MIN_RELIABILITY_SCORE;
        if (worker.getReliabilityScore() == null || worker.getReliabilityScore() < minRs) {
            return "LOW_RELIABILITY";
        }
        if (shift.getRequiredSkills() != null && shift.getRequiredSkills().length > 0
                && workerSkillRepository != null) {
            Set<UUID> requiredSkillIds = Arrays.stream(shift.getRequiredSkills())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!requiredSkillIds.isEmpty()) {
                List<WorkerSkill> workerSkills = workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId());
                Set<UUID> haveSkillIds = workerSkills == null ? Collections.emptySet()
                        : workerSkills.stream()
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
                AssignmentStatus.PENDING);
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

    private boolean isDispatchAllowedForEvent(Shift shift) {
        if (shift == null) {
            return false;
        }
        Event event = shift.getEvent();
        if (event == null) {
            return true;
        }
        if (event.getMode() == com.cani.entity.enums.EventMode.INVITE) {
            return false;
        }
        com.cani.entity.enums.EventStatus status = event.getStatus();
        return status != com.cani.entity.enums.EventStatus.CANCELLED
                && status != com.cani.entity.enums.EventStatus.COMPLETED
                && status != com.cani.entity.enums.EventStatus.SUSPENDED;
    }

    private boolean hasExistingProposalOrAssignment(UUID shiftId, UUID workerId) {
        return dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(
                shiftId, workerId, ACTIVE_PROPOSAL_STATUSES)
                || shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(shiftId, workerId);
    }

    private boolean isShiftReadyForProposal(Shift shift, boolean emergency) {
        if (shift == null) {
            return false;
        }
        Instant now = Instant.now();
        if (shift.getEndTime() != null && !now.isBefore(shift.getEndTime())) {
            return false;
        }
        ShiftStaffingPolicy policy = getStaffingPolicy();
        ShiftStatus effectiveStatus = policy.resolveEffectiveShiftStatus(shift, now);
        boolean statusReady = emergency
                ? (effectiveStatus == ShiftStatus.ACTIVE || effectiveStatus == ShiftStatus.IN_PROGRESS)
                : (effectiveStatus == ShiftStatus.ACTIVE);
        if (!statusReady) {
            return false;
        }
        int needed = Math.max(0, policy.resolveRequiredWorkers(shift) - policy.resolveConfirmedWorkersCount(shift));
        return needed > 0 && isDispatchAllowedForEvent(shift);
    }

    private double getConfigDouble(String key, double defaultValue) {
        if (systemConfigRepository != null) {
            return systemConfigRepository.findByKey(key)
                    .map(cfg -> {
                        try {
                            double val = Double.parseDouble(cfg.getValue().trim());
                            return Double.isFinite(val) ? val : defaultValue;
                        } catch (Exception ignored) {
                            return defaultValue;
                        }
                    })
                    .orElse(defaultValue);
        }
        return defaultValue;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.min(max, Math.max(min, value));
    }

    public static boolean isValidCoordinate(Double lat, Double lng) {
        if (lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng)) {
            return false;
        }
        return lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0;
    }

    /**
     * Tính khoảng cách Haversine giữa 2 tọa độ GPS (km)
     */
    public static double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (!isValidCoordinate(lat1, lon1) || !isValidCoordinate(lat2, lon2)) {
            return Double.MAX_VALUE; // Tọa độ thiếu hoặc ngoài miền hợp lệ -> loại an toàn
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

    // shortcut: P90 tính tuyến tính đơn giản; nâng cấp khi cần chính xác hơn.
    public static double calculateMedian(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        } else {
            return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        }
    }

    // shortcut: P90 tính tuyến tính đơn giản; nâng cấp khi cần chính xác hơn.
    public static double calculatePercentile(List<Double> values, double percentile) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n == 1) {
            return sorted.get(0);
        }
        double rank = (percentile / 100.0) * (n - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double weight = rank - lower;
        return sorted.get(lower) * (1.0 - weight) + sorted.get(upper) * weight;
    }
}
