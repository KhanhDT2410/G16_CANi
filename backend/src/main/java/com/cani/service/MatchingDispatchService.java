package com.cani.service;

import com.cani.entity.*;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.ProposalStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.DispatchProposalRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import com.cani.repository.WorkerRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingDispatchService {

    private final WorkerRepository workerRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final DispatchProposalRepository dispatchProposalRepository;

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
     * Kích hoạt matching và dispatch cho tất cả ca làm việc đang ACTIVE của một Event
     */
    @Transactional
    public int dispatchForEvent(UUID eventId) {
        List<Shift> activeShifts = shiftRepository.findByEvent_EventIdAndStatus(eventId, ShiftStatus.ACTIVE);
        if (activeShifts == null || activeShifts.isEmpty()) {
            return 0;
        }
        int totalProposalsCreated = 0;
        for (Shift shift : activeShifts) {
            if (!isDispatchAllowedForEvent(shift)) {
                log.info("Bỏ qua dispatchForEvent cho sự kiện {}: chế độ INVITE hoặc trạng thái sự kiện không hợp lệ.", eventId);
                continue;
            }
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

        if (!isDispatchAllowedForEvent(shift)) {
            log.info("Bỏ qua dispatch: Sự kiện của ca {} đang ở chế độ INVITE hoặc trạng thái không hợp lệ.", shiftId);
            return 0;
        }

        if (shift.getStatus() != ShiftStatus.ACTIVE) {
            log.info("Bỏ qua dispatch: Ca làm việc {} không ở trạng thái ACTIVE (hiện tại: {})", shiftId, shift.getStatus());
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

        // Tọa độ mục tiêu của ca làm việc (Bắt buộc hợp lệ, tuyệt đối không fallback 0 km)
        Double targetLat = shift.getLocationLat() != null ? shift.getLocationLat() : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
        Double targetLng = shift.getLocationLng() != null ? shift.getLocationLng() : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);
        if (!isValidCoordinate(targetLat, targetLng)) {
            log.warn("Bỏ qua dispatch ca {}: thiếu tọa độ GPS hợp lệ (lat={}, lng={}).", shiftId, targetLat, targetLng);
            return 0;
        }

        // Bán kính tối đa của ca làm việc (hoặc mặc định 15km)
        double maxAllowedDistanceKm = (shift.getMaxDistanceKm() != null && shift.getMaxDistanceKm().doubleValue() > 0)
                ? shift.getMaxDistanceKm().doubleValue()
                : MAX_DISTANCE_KM;

        // 1. Chỉ lấy danh sách Worker đang Online (tuyệt đối không fallback full-table scan Worker offline)
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

        // 6-7. Loại Worker đã có Proposal PENDING/ACCEPTED hoặc đã có Assignment trước khi cắt Top N
        List<CandidateMatch> eligibleCandidates = matchedCandidates.stream()
                .filter(candidate -> !hasExistingProposalOrAssignment(shiftId, candidate.getWorker().getWorkerId()))
                .toList();

        // 8. Dispatch ratio: tuyển N người -> chọn Top min(N * 2 + 3, số ứng viên hợp lệ)
        int targetDispatchCount = Math.max(needed * 2 + 3, needed);
        List<CandidateMatch> topCandidates = eligibleCandidates.stream()
                .limit(targetDispatchCount)
                .collect(Collectors.toList());

        int createdCount = 0;
        Instant expiresAt = Instant.now().plus(Duration.ofHours(DEFAULT_TIMEOUT_HOURS));

        for (CandidateMatch candidate : topCandidates) {
            if (!isShiftReadyForProposal(shift)) {
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
                            + "Kiểm tra: worker đang online? kycStatus APPROVED? có GPS hợp lệ? requiredSkills khớp worker_skills? "
                            + "khung giờ rảnh trùng ca? minWage không vượt lương ca? blocklist? "
                            + "Tổng worker online đã xét: {}",
                    shiftId, onlineWorkers.size());
        }

        log.info("Matching hoàn tất cho ca {}: Tìm thấy {} ứng viên phù hợp ({} chưa trùng), đã gửi {} đề xuất (Proposals).",
                shiftId, matchedCandidates.size(), eligibleCandidates.size(), createdCount);
        return createdCount;
    }

    /**
     * Reverse-Matching: Khi Worker bật trạng thái Online (isOnline = true),
     * tự động quét các ca ACTIVE còn thiếu người và chưa hết hạn để gửi DispatchProposal phù hợp.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dispatchActiveShiftsForWorker(Worker worker) {
        if (worker == null) {
            return 0;
        }
        // Tải lại thực thể Worker trong transaction mới để tránh LazyInitializationException hoặc rollback ngược transaction cha
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
            if (!passHardFilter(worker, shift)) {
                continue;
            }
            Double targetLat = shift.getLocationLat() != null ? shift.getLocationLat() : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
            Double targetLng = shift.getLocationLng() != null ? shift.getLocationLng() : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);
            if (!isValidCoordinate(targetLat, targetLng)) {
                continue;
            }
            double maxAllowedDistanceKm = (shift.getMaxDistanceKm() != null && shift.getMaxDistanceKm().doubleValue() > 0)
                    ? shift.getMaxDistanceKm().doubleValue() : MAX_DISTANCE_KM;
            double distanceKm = calculateDistance(targetLat, targetLng, worker.getBaseLat(), worker.getBaseLng());
            if (!Double.isFinite(distanceKm)) {
                continue;
            }
            double workerMaxDist = (worker.getMaxDistanceKm() != null && worker.getMaxDistanceKm() > 0)
                    ? worker.getMaxDistanceKm() : maxAllowedDistanceKm;
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
                            "Có ca làm việc phù hợp gần bạn tại \"" + eventName + "\" với mức thù lao " + shift.getHourlyWage() + " đ/giờ.",
                            NotificationType.PROPOSAL,
                            "PROPOSAL",
                            savedProposal != null && savedProposal.getProposalId() != null ? savedProposal.getProposalId() : shift.getShiftId(),
                            Map.of("shiftId", shift.getShiftId().toString(), "type", "NEW_PROPOSAL")
                    );
                } catch (Exception ex) {
                    log.warn("Không thể gửi thông báo FCM reverse-matching: {}", ex.getMessage());
                }
            }
        }
        if (createdCount > 0) {
            log.info("Reverse-matching khi Worker {} online: đã tạo {} DispatchProposal mới.", worker.getWorkerId(), createdCount);
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
        List<DispatchProposal> stale = dispatchProposalRepository
                .findByStatusAndExpiresAtBefore(ProposalStatus.PENDING, Instant.now());
        if (stale.isEmpty()) {
            return;
        }
        stale.forEach(p -> p.setStatus(ProposalStatus.EXPIRED));
        dispatchProposalRepository.saveAll(stale);
        log.info("Đã chuyển {} proposal PENDING hết hạn sang EXPIRED.", stale.size());
    }

    /**
     * Hard Filter: Loại bỏ các ứng viên không đạt tiêu chuẩn tối thiểu
     */
    private boolean passHardFilter(Worker worker, Shift shift) {
        if (worker == null || shift == null) {
            return false;
        }

        // 1. Phải bật trạng thái "Bắt đầu kiếm việc" (isOnline == true, không chấp nhận null)
        if (!Boolean.TRUE.equals(worker.getIsOnline())) {
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

        // 4b. Kiểm tra kỹ năng bắt buộc theo tiêu chí ca làm việc (fail-closed nếu không truy vấn được dữ liệu kỹ năng)
        if (shift.getRequiredSkills() != null && shift.getRequiredSkills().length > 0) {
            Set<UUID> requiredSkillIds = Arrays.stream(shift.getRequiredSkills())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!requiredSkillIds.isEmpty()) {
                if (workerSkillRepository == null) {
                    log.error("Từ chối worker {} cho ca {}: hệ thống không thể truy vấn WorkerSkillRepository.",
                            worker.getWorkerId(), shift.getShiftId());
                    return false;
                }
                List<WorkerSkill> workerSkills = workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId());
                if (workerSkills == null) {
                    return false;
                }
                Instant now = Instant.now();
                Set<UUID> haveSkillIds = workerSkills.stream()
                        .filter(ws -> ws.getSkill() != null && ws.getSkill().getSkillId() != null)
                        .filter(ws -> ws.getExpiresAt() == null || ws.getExpiresAt().isAfter(now))
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
        if (conflicts != null && !conflicts.isEmpty()) {
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
            if (shift.getHourlyWage() == null || shift.getHourlyWage().compareTo(worker.getMinWage()) < 0) {
                log.debug("Loại trừ ứng viên {}: Lương ca làm ({} đ) không đáp ứng lương tối thiểu mong muốn ({} đ)",
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
        double effectiveMaxDist = (Double.isFinite(maxAllowedDistanceKm) && maxAllowedDistanceKm > 0)
                ? maxAllowedDistanceKm : MAX_DISTANCE_KM;
        double safeDistanceKm = Double.isFinite(distanceKm) ? Math.max(0.0, distanceKm) : effectiveMaxDist;

        // 1. Distance Score: 100 - (distance / maxAllowedDistanceKm) * 100 (Trọng số 0.4)
        double distanceScore = clamp(100.0 - (safeDistanceKm / effectiveMaxDist) * 100.0, 0.0, 100.0);

        // 2. Rating Score: Nếu Worker chưa có rating history (ratingCount <= 0), dùng mức trung tính (3.5/5.0 = 70 điểm)
        double neutralRating = clamp(getConfigDouble("matching.neutral_unrated_rating", DEFAULT_NEUTRAL_RATING), 0.0, 5.0);
        boolean hasRatingHistory = worker.getRatingCount() != null && worker.getRatingCount() > 0 && worker.getRatingAvg() != null;
        double rating = hasRatingHistory ? clamp(worker.getRatingAvg().doubleValue(), 0.0, 5.0) : neutralRating;
        double ratingScore = clamp((rating / 5.0) * 100.0, 0.0, 100.0);

        // 3. Experience Score: (completed_shifts / 10) * 100 (Trọng số 0.2)
        int completedShifts = worker.getTotalShiftsCompleted() != null ? Math.max(0, worker.getTotalShiftsCompleted()) : 0;
        double expScore = clamp((completedShifts / 10.0) * 100.0, 0.0, 100.0);

        // 4. Reliability Score: Trọng số 0.1
        double rsScore = worker.getReliabilityScore() != null ? clamp(worker.getReliabilityScore(), 0.0, 100.0) : 60.0;

        // Bonus: Người mới hoặc nghỉ lâu thực tế (UC-60, UC-61: Fairness Boost)
        double newWorkerBoostConfig = Math.max(0.0, getConfigDouble("fairness.new_worker_boost", 10.0));
        double idleWorkerBoostConfig = Math.max(0.0, getConfigDouble("fairness.idle_worker_boost", 10.0));

        double newWorkerBonus = (completedShifts < 3) ? newWorkerBoostConfig : 0.0;
        double idleBonus = isEligibleForIdleBoost(worker, completedShifts) ? idleWorkerBoostConfig : 0.0;

        // Bonus kỹ năng đã xác thực còn hiệu lực & khớp kỹ năng yêu cầu của ca: +10.0
        double skillBonus = 0.0;
        if (shift != null && shift.getRequiredSkills() != null && shift.getRequiredSkills().length > 0 && workerSkillRepository != null) {
            Set<UUID> reqIds = Arrays.stream(shift.getRequiredSkills()).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!reqIds.isEmpty()) {
                List<WorkerSkill> workerSkills = workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId());
                if (workerSkills != null) {
                    Instant now = Instant.now();
                    boolean hasVerifiedSkill = workerSkills.stream()
                            .anyMatch(ws -> ws.getSkill() != null
                                    && reqIds.contains(ws.getSkill().getSkillId())
                                    && ws.getVerificationTier() != null
                                    && ws.getVerificationTier() != com.cani.entity.enums.VerificationTier.SELF_DECLARED
                                    && (ws.getExpiresAt() == null || ws.getExpiresAt().isAfter(now)));
                    if (hasVerifiedSkill) {
                        skillBonus = 10.0;
                    }
                }
            }
        }

        double rawFinalScore = (0.4 * distanceScore) + (0.3 * ratingScore) + (0.2 * expScore) + (0.1 * rsScore)
                + newWorkerBonus + idleBonus + skillBonus;
        double finalScore = Double.isFinite(rawFinalScore) ? rawFinalScore : 0.0;

        String breakdownJson = String.format(
                Locale.US,
                "{\"distanceKm\":%.2f,\"distScore\":%.1f,\"ratingScore\":%.1f,\"expScore\":%.1f,\"rsScore\":%.1f,\"newWorkerBonus\":%.1f,\"idleBonus\":%.1f,\"skillBonus\":%.1f,\"finalScore\":%.2f}",
                safeDistanceKm, distanceScore, ratingScore, expScore, rsScore, newWorkerBonus, idleBonus, skillBonus, finalScore
        );

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
        int idleDays = (int) Math.max(1.0, getConfigDouble("fairness.idle_days_threshold", DEFAULT_IDLE_DAYS_THRESHOLD));
        Instant idleCutoff = Instant.now().minus(Duration.ofDays(idleDays));
        List<ShiftAssignment> history = shiftAssignmentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId());
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
     * UC-59: Kích hoạt điều phối khẩn cấp (Emergency Dispatch) khi ca làm việc thiếu người
     */
    @Transactional
    public int triggerEmergencyDispatch(UUID shiftId, Double customRadiusKm) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ca làm việc ID: " + shiftId));

        if (!isDispatchAllowedForEvent(shift)) {
            log.warn("Từ chối Emergency Dispatch: Sự kiện của ca {} đang ở chế độ INVITE hoặc không hợp lệ.", shiftId);
            return 0;
        }

        if (shift.getStatus() != ShiftStatus.ACTIVE) {
            log.warn("Không thể kích hoạt Emergency Dispatch: Ca làm việc {} không ở trạng thái ACTIVE", shiftId);
            return 0;
        }

        if (shift.getEndTime() != null && Instant.now().isAfter(shift.getEndTime())) {
            log.warn("Không thể kích hoạt Emergency Dispatch: Ca làm việc {} đã qua giờ kết thúc.", shiftId);
            return 0;
        }

        int needed = shift.getRemainingWorkers() != null ? shift.getRemainingWorkers() : shift.getRequiredWorkers();
        if (needed <= 0) {
            return 0;
        }

        Double targetLat = shift.getLocationLat() != null ? shift.getLocationLat() : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
        Double targetLng = shift.getLocationLng() != null ? shift.getLocationLng() : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);
        if (!isValidCoordinate(targetLat, targetLng)) {
            log.warn("Bỏ qua Emergency Dispatch ca {}: thiếu tọa độ GPS hợp lệ (lat={}, lng={}).", shiftId, targetLat, targetLng);
            return 0;
        }

        if (!Boolean.TRUE.equals(shift.getEmergency())) {
            shift.setEmergency(true);
            shiftRepository.save(shift);
        }

        double rawRadius = (customRadiusKm != null && customRadiusKm > 0) ? customRadiusKm : 25.0;
        double emergencyRadius = Math.min(Math.max(rawRadius, 1.0), 50.0);
        log.info("KÍCH HOẠT EMERGENCY DISPATCH cho ca {}: Bán kính mở rộng {} km (Capped max 50km)", shiftId, emergencyRadius);

        List<Worker> onlineWorkers = workerRepository.findByIsOnlineTrue();
        if (onlineWorkers == null || onlineWorkers.isEmpty()) {
            log.info("Bỏ qua Emergency Dispatch ca {}: không có Worker nào đang Online.", shiftId);
            return 0;
        }

        List<CandidateMatch> matchedCandidates = new ArrayList<>();
        for (Worker worker : onlineWorkers) {
            if (!passHardFilter(worker, shift)) {
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

        // Loại ứng viên đã có Proposal PENDING/ACCEPTED hoặc Assignment trước khi lấy Top N
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
            if (!isShiftReadyForProposal(shift)) {
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

        log.info("Emergency Dispatch hoàn tất: Đã gửi {} đề xuất khẩn cấp cho ca {}", createdCount, shiftId);
        return createdCount;
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

    private boolean isShiftReadyForProposal(Shift shift) {
        if (shift == null || shift.getStatus() != ShiftStatus.ACTIVE) {
            return false;
        }
        if (shift.getEndTime() != null && Instant.now().isAfter(shift.getEndTime())) {
            return false;
        }
        int needed = shift.getRemainingWorkers() != null ? shift.getRemainingWorkers() : shift.getRequiredWorkers();
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
}
