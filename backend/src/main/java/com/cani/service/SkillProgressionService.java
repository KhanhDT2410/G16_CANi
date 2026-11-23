package com.cani.service;

import com.cani.entity.AuditLog;
import com.cani.entity.Shift;
import com.cani.entity.ShiftAssignment;
import com.cani.entity.Skill;
import com.cani.entity.SystemConfig;
import com.cani.entity.Worker;
import com.cani.entity.WorkerSkill;
import com.cani.entity.WorkerSkillPoint;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.ExperienceLevel;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.VerificationTier;
import com.cani.exception.BadRequestException;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.SkillRepository;
import com.cani.repository.SystemConfigRepository;
import com.cani.repository.WorkerRepository;
import com.cani.repository.WorkerSkillPointRepository;
import com.cani.repository.WorkerSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service quản lý tiến trình tích lũy Điểm Kỹ Năng (WorkerSkillPoint)
 * và thăng hạng bảo chứng thực chiến (Tier 3: PLATFORM_PROVEN) từ ca làm việc thực tế.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillProgressionService {

    public static final int SP_EXPERIENCE_PER_SHIFT = 20;
    public static final int SP_RATING_FIVE_STARS = 15;
    public static final int SP_RATING_FOUR_STARS = 5;
    public static final int PLATFORM_PROVEN_MIN_SHIFTS = 3;
    public static final int PLATFORM_PROVEN_MIN_RATINGS = 1;
    public static final BigDecimal PLATFORM_PROVEN_MIN_AVG_RATING = new BigDecimal("4.50");
    public static final double PLATFORM_PROVEN_MIN_COMPLETION_RATE = 0.85;
    public static final int PLATFORM_PROVEN_MIN_RELIABILITY_SCORE = 70;
    public static final double PLATFORM_PROVEN_MAX_NOSHOW_RATE = 0.05;
    private static final BigDecimal DEFAULT_RATING = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final WorkerSkillPointRepository workerSkillPointRepository;
    private final WorkerSkillRepository workerSkillRepository;
    private final SkillRepository skillRepository;
    private final WorkerRepository workerRepository;

    @Autowired(required = false)
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    @Autowired(required = false)
    private SystemConfigRepository systemConfigRepository;

    private int getPlatformProvenSp() {
        if (systemConfigRepository == null) {
            return VerificationTier.PLATFORM_PROVEN.getSpVerify();
        }
        try {
            return systemConfigRepository.findByKey("skill_verify_sp_platform_proven")
                    .map(SystemConfig::getValue)
                    .map(String::trim)
                    .filter(val -> !val.isEmpty())
                    .map(Integer::parseInt)
                    .filter(val -> val >= 0)
                    .orElse(VerificationTier.PLATFORM_PROVEN.getSpVerify());
        } catch (Exception e) {
            log.warn("Lỗi khi đọc cấu hình skill_verify_sp_platform_proven, fallback về {}: {}",
                    VerificationTier.PLATFORM_PROVEN.getSpVerify(), e.getMessage());
            return VerificationTier.PLATFORM_PROVEN.getSpVerify();
        }
    }

    /**
     * Cộng điểm kinh nghiệm (SP_experience) có kiểm soát idempotency ở mức Assignment + Pessimistic Lock.
     * Chỉ cộng cho 1 kỹ năng thực hiện chính duy nhất, không cộng lan sang toàn bộ requiredSkills.
     *
     * @return true nếu đã cộng điểm, false nếu đã cộng trước đó hoặc đang chờ EO xác nhận kỹ năng cho ca cũ mơ hồ
     */
    @Transactional
    public boolean recordCompletedShiftForAssignment(ShiftAssignment assignment, UUID confirmedPerformedSkillId) {
        if (assignment == null || assignment.getWorker() == null || assignment.getShift() == null) {
            return false;
        }

        ShiftAssignment targetAssignment = assignment;
        if (shiftAssignmentRepository != null && assignment.getAssignmentId() != null) {
            targetAssignment = shiftAssignmentRepository.findByIdForUpdate(assignment.getAssignmentId())
                    .orElse(assignment);
        }

        if (Boolean.TRUE.equals(targetAssignment.getSpExperienceAwarded())) {
            log.debug("Assignment {} đã được cộng SP_experience trước đó, bỏ qua để tránh cộng trùng.",
                    targetAssignment.getAssignmentId());
            return false;
        }

        if (targetAssignment.getStatus() != AssignmentStatus.COMPLETED) {
            return false;
        }

        Shift shift = targetAssignment.getShift();
        UUID effectiveConfirmedSkillId = confirmedPerformedSkillId != null
                ? confirmedPerformedSkillId
                : targetAssignment.getConfirmedPerformedSkillId();

        PrimarySkillTarget target = resolvePrimaryPerformedSkill(shift, effectiveConfirmedSkillId);
        if (target == null) {
            return false;
        }

        if (target.ambiguousLegacyShift()) {
            log.info("Assignment {} (Shift {}) chưa có performed_skill_id và có nhiều kỹ năng yêu cầu — giữ SP ở trạng thái chờ EO xác nhận.",
                    targetAssignment.getAssignmentId(), shift.getShiftId());
            return false;
        }

        applyCompletedShiftPoint(targetAssignment.getWorker(), target.skill(), target.canonicalJobType());

        Instant awardedAt = Instant.now();
        targetAssignment.setSpExperienceAwarded(true);
        targetAssignment.setSpExperienceAwardedAt(awardedAt);
        if (target.skill() != null && target.skill().getSkillId() != null) {
            targetAssignment.setConfirmedPerformedSkillId(target.skill().getSkillId());
        } else if (effectiveConfirmedSkillId != null) {
            targetAssignment.setConfirmedPerformedSkillId(effectiveConfirmedSkillId);
        } else if (shift.getPerformedSkillId() != null) {
            targetAssignment.setConfirmedPerformedSkillId(shift.getPerformedSkillId());
        }

        if (shiftAssignmentRepository != null && targetAssignment.getAssignmentId() != null) {
            shiftAssignmentRepository.save(targetAssignment);
        }
        // Đồng bộ lại instance caller đang nắm giữ trong cùng phiên giao dịch
        if (assignment != targetAssignment) {
            assignment.setSpExperienceAwarded(true);
            assignment.setSpExperienceAwardedAt(awardedAt);
            assignment.setConfirmedPerformedSkillId(targetAssignment.getConfirmedPerformedSkillId());
        }
        return true;
    }

    /**
     * Overload tương thích ngược: Cộng SP_experience cho duy nhất 1 kỹ năng thực hiện chính của Shift.
     * Tuyệt đối không cộng lan sang toàn bộ requiredSkills.
     */
    @Transactional
    public void recordCompletedShift(Worker worker, Shift shift) {
        if (worker == null || shift == null) {
            return;
        }
        PrimarySkillTarget target = resolvePrimaryPerformedSkill(shift, null);
        if (target == null) {
            return;
        }
        applyCompletedShiftPoint(worker, target.skill(), target.canonicalJobType());
    }

    /**
     * Cộng điểm đánh giá (SP_rating) gắn với Assignment cụ thể để chống cộng trùng khi retry.
     */
    @Transactional
    public boolean recordShiftRatingForAssignment(ShiftAssignment assignment, int rating) {
        if (assignment == null || assignment.getWorker() == null || assignment.getShift() == null) {
            return false;
        }
        validateRating(rating);

        ShiftAssignment targetAssignment = assignment;
        if (shiftAssignmentRepository != null && assignment.getAssignmentId() != null) {
            targetAssignment = shiftAssignmentRepository.findByIdForUpdate(assignment.getAssignmentId())
                    .orElse(assignment);
        }

        if (Boolean.TRUE.equals(targetAssignment.getSpRatingAwarded())) {
            log.debug("Assignment {} đã được cộng SP_rating trước đó, bỏ qua để tránh cộng trùng.",
                    targetAssignment.getAssignmentId());
            return false;
        }

        Shift shift = targetAssignment.getShift();
        UUID effectiveSkillId = targetAssignment.getConfirmedPerformedSkillId() != null
                ? targetAssignment.getConfirmedPerformedSkillId()
                : shift.getPerformedSkillId();

        PrimarySkillTarget target = resolvePrimaryPerformedSkill(shift, effectiveSkillId);
        if (target == null) {
            return false;
        }

        applyRatingPoint(targetAssignment.getWorker(), target.skill(), target.canonicalJobType(), rating);

        targetAssignment.setSpRatingAwarded(true);
        if (shiftAssignmentRepository != null && targetAssignment.getAssignmentId() != null) {
            shiftAssignmentRepository.save(targetAssignment);
        }
        if (assignment != targetAssignment) {
            assignment.setSpRatingAwarded(true);
        }
        return true;
    }

    /**
     * Cộng điểm đánh giá (SP_rating), cập nhật rating trung bình thật theo kỹ năng
     * và kiểm tra thăng hạng Tier 3 (PLATFORM_PROVEN) sau khi EO chấm sao.
     */
    @Transactional
    public void recordShiftRating(Worker worker, Shift shift, int rating) {
        if (worker == null || shift == null) {
            return;
        }
        validateRating(rating);

        PrimarySkillTarget target = resolvePrimaryPerformedSkill(shift, null);
        if (target == null) {
            return;
        }
        applyRatingPoint(worker, target.skill(), target.canonicalJobType(), rating);
    }

    /**
     * Ghi nhận lượt hủy ca theo kỹ năng thực hiện chính của Shift.
     */
    @Transactional
    public void recordCancelledShift(Worker worker, Shift shift) {
        if (worker == null || shift == null) {
            return;
        }
        PrimarySkillTarget target = resolvePrimaryPerformedSkill(shift, null);
        if (target == null) {
            return;
        }
        WorkerSkillPoint wsp = findOrCreateSkillPoint(worker, target.skill(), target.canonicalJobType());
        int cancelled = (wsp.getTotalShiftsCancelled() != null ? wsp.getTotalShiftsCancelled() : 0) + 1;
        wsp.setTotalShiftsCancelled(cancelled);
        workerSkillPointRepository.save(wsp);
    }

    /**
     * Tái kiểm tra và thăng hạng Tier 3 (PLATFORM_PROVEN) cho Worker khi có biến động điểm đánh giá/uy tín
     * (ví dụ: sau khi Admin gỡ đánh giá oan).
     */
    @Transactional
    public void recheckTierProgression(Worker worker) {
        if (worker == null || workerSkillPointRepository == null) return;
        List<WorkerSkillPoint> points = workerSkillPointRepository.findByWorker_WorkerId(worker.getWorkerId());
        if (points == null || points.isEmpty()) return;
        for (WorkerSkillPoint wsp : points) {
            tryPromoteToPlatformProven(worker, wsp.getSkill(), wsp.getJobType(), wsp);
            workerSkillPointRepository.save(wsp);
            syncWorkerOverallTier(worker, wsp.getTier());
        }
    }

    private void applyCompletedShiftPoint(Worker worker, Skill matchedSkill, String canonicalJobType) {
        WorkerSkillPoint wsp = findOrCreateSkillPoint(worker, matchedSkill, canonicalJobType);
        int completed = (wsp.getTotalShiftsCompleted() != null ? wsp.getTotalShiftsCompleted() : 0) + 1;
        int spExp = (wsp.getSpExperience() != null ? wsp.getSpExperience() : 0) + SP_EXPERIENCE_PER_SHIFT;

        wsp.setTotalShiftsCompleted(completed);
        wsp.setSpExperience(spExp);
        recalculatePointAndTier(wsp);

        tryPromoteToPlatformProven(worker, matchedSkill, canonicalJobType, wsp);
        workerSkillPointRepository.save(wsp);
        syncWorkerOverallTier(worker, wsp.getTier());
    }

    private void applyRatingPoint(Worker worker, Skill matchedSkill, String canonicalJobType, int rating) {
        int ratingBonus = resolveRatingBonus(rating);
        WorkerSkillPoint wsp = findOrCreateSkillPoint(worker, matchedSkill, canonicalJobType);
        updateSkillAvgRating(wsp, rating);

        int currentSpRating = wsp.getSpRating() != null ? wsp.getSpRating() : 0;
        wsp.setSpRating(currentSpRating + ratingBonus);
        recalculatePointAndTier(wsp);

        tryPromoteToPlatformProven(worker, matchedSkill, canonicalJobType, wsp);
        workerSkillPointRepository.save(wsp);
        syncWorkerOverallTier(worker, wsp.getTier());
    }

    private void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new BadRequestException("Điểm đánh giá kỹ năng phải nằm trong khoảng từ 1 đến 5 sao.");
        }
    }

    private int resolveRatingBonus(int rating) {
        if (rating >= 5) {
            return SP_RATING_FIVE_STARS;
        }
        if (rating == 4) {
            return SP_RATING_FOUR_STARS;
        }
        return 0;
    }

    private void updateSkillAvgRating(WorkerSkillPoint wsp, int newRating) {
        int previousCount = resolveEffectiveRatingCount(wsp);
        int newCount = previousCount + 1;
        wsp.setRatingCount(newCount);

        if (previousCount <= 0 || wsp.getAvgRating() == null || wsp.getAvgRating().compareTo(BigDecimal.ZERO) <= 0) {
            wsp.setAvgRating(BigDecimal.valueOf(newRating).setScale(2, RoundingMode.HALF_UP));
            return;
        }

        BigDecimal updated = wsp.getAvgRating()
                .multiply(BigDecimal.valueOf(previousCount))
                .add(BigDecimal.valueOf(newRating))
                .divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP);
        wsp.setAvgRating(updated);
    }

    private int resolveEffectiveRatingCount(WorkerSkillPoint wsp) {
        if (wsp.getRatingCount() != null && wsp.getRatingCount() > 0) {
            return wsp.getRatingCount();
        }
        // Tương thích bản ghi cũ đã có điểm đánh giá thật trước V3
        if (wsp.getSpRating() != null && wsp.getSpRating() > 0
                && wsp.getAvgRating() != null && wsp.getAvgRating().compareTo(BigDecimal.ZERO) > 0) {
            int completed = wsp.getTotalShiftsCompleted() != null ? wsp.getTotalShiftsCompleted() : 1;
            return Math.max(1, completed - 1);
        }
        return 0;
    }

    private void tryPromoteToPlatformProven(Worker worker, Skill matchedSkill, String jobType, WorkerSkillPoint wsp) {
        int completed = wsp.getTotalShiftsCompleted() != null ? wsp.getTotalShiftsCompleted() : 0;
        int cancelled = wsp.getTotalShiftsCancelled() != null ? wsp.getTotalShiftsCancelled() : 0;
        int realRatingCount = resolveEffectiveRatingCount(wsp);
        BigDecimal avgRating = wsp.getAvgRating() != null ? wsp.getAvgRating() : BigDecimal.ZERO;

        if (completed < PLATFORM_PROVEN_MIN_SHIFTS
                || realRatingCount < PLATFORM_PROVEN_MIN_RATINGS
                || avgRating.compareTo(PLATFORM_PROVEN_MIN_AVG_RATING) < 0) {
            return;
        }

        // Practical Validation V2: kiểm tra tỷ lệ hoàn thành ca, điểm uy tín và tỷ lệ No-Show
        int totalAttempted = completed + Math.max(0, cancelled);
        double completionRate = totalAttempted > 0 ? (double) completed / totalAttempted : 0.0;
        if (completionRate < PLATFORM_PROVEN_MIN_COMPLETION_RATE) {
            log.info("Worker {} chưa đủ tỷ lệ hoàn thành ca ({}) để lên PLATFORM_PROVEN cho '{}'",
                    worker.getWorkerId(), String.format("%.2f", completionRate), jobType);
            return;
        }

        int reliability = worker.getReliabilityScore() != null ? worker.getReliabilityScore() : 100;
        if (reliability < PLATFORM_PROVEN_MIN_RELIABILITY_SCORE) {
            log.info("Worker {} chưa đủ điểm uy tín ({}) để lên PLATFORM_PROVEN cho '{}'",
                    worker.getWorkerId(), reliability, jobType);
            return;
        }

        int totalNoShows = worker.getTotalNoshows() != null ? worker.getTotalNoshows() : 0;
        int overallCompleted = Math.max(completed, worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0);
        double noShowRate = (overallCompleted + totalNoShows) > 0
                ? (double) totalNoShows / (overallCompleted + totalNoShows)
                : 0.0;
        if (noShowRate > PLATFORM_PROVEN_MAX_NOSHOW_RATE) {
            log.info("Worker {} có tỷ lệ No-Show cao ({}) nên chưa đủ điều kiện PLATFORM_PROVEN cho '{}'",
                    worker.getWorkerId(), String.format("%.2f", noShowRate), jobType);
            return;
        }

        Skill skill = matchedSkill != null
                ? matchedSkill
                : lookupSkillByName(jobType);
        if (skill == null) {
            return;
        }

        WorkerSkill ws = workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(worker.getWorkerId(), skill.getSkillId())
                .orElseGet(() -> WorkerSkill.builder()
                        .worker(worker)
                        .skill(skill)
                        .proficiency(ExperienceLevel.INTERMEDIATE)
                        .build());

        if (ws.getVerificationTier() != VerificationTier.PLATFORM_PROVEN) {
            int provenSp = getPlatformProvenSp();
            int existingSp = ws.getSpVerifyAwarded() != null ? ws.getSpVerifyAwarded() : 0;

            ws.setVerificationTier(VerificationTier.PLATFORM_PROVEN);
            ws.setSpVerifyAwarded(Math.max(existingSp, provenSp));
            if (ws.getVerifiedAt() == null) {
                ws.setVerifiedAt(Instant.now());
            }
            workerSkillRepository.save(ws);

            int currentVerify = wsp.getSpVerify() != null ? wsp.getSpVerify() : 0;
            if (currentVerify < provenSp) {
                wsp.setSpVerify(provenSp);
                recalculatePointAndTier(wsp);
            }

            log.info("Worker {} đã đạt chuẩn thực chiến PLATFORM_PROVEN cho kỹ năng '{}' ({} ca, {} đánh giá thật, rating {})",
                    worker.getWorkerId(), skill.getName(), completed, realRatingCount, avgRating);
        }
    }

    /**
     * Xác định DUY NHẤT 1 kỹ năng thực hiện chính cho Shift/Assignment:
     * 1. confirmedPerformedSkillId (EO xác nhận lúc duyệt công)
     * 2. shift.performedSkillId (EO chọn lúc tạo ca)
     * 3. Fallback an toàn cho Shift cũ: theo jobType khớp tên Skill hoặc khi requiredSkills chỉ có đúng 1 Skill.
     * Tuyệt đối KHÔNG trả về danh sách nhiều kỹ năng để cộng điểm lan.
     */
    public PrimarySkillTarget resolvePrimaryPerformedSkill(Shift shift, UUID confirmedPerformedSkillId) {
        if (shift == null) {
            return null;
        }

        if (confirmedPerformedSkillId != null) {
            Skill confirmed = lookupSkillById(confirmedPerformedSkillId);
            if (confirmed != null && confirmed.getName() != null && !confirmed.getName().isBlank()) {
                return new PrimarySkillTarget(confirmed, confirmed.getName().trim(), false);
            }
        }

        if (shift.getPerformedSkillId() != null) {
            Skill configured = lookupSkillById(shift.getPerformedSkillId());
            if (configured != null && configured.getName() != null && !configured.getName().isBlank()) {
                return new PrimarySkillTarget(configured, configured.getName().trim(), false);
            }
        }

        String rawJobType = (shift.getJobType() != null && !shift.getJobType().isBlank())
                ? shift.getJobType().trim()
                : null;
        Skill byJobType = rawJobType != null ? lookupSkillByName(rawJobType) : null;

        UUID[] reqIds = shift.getRequiredSkills();
        int reqCount = reqIds != null ? reqIds.length : 0;

        if (reqCount == 1 && reqIds[0] != null) {
            Skill singleReqSkill = lookupSkillById(reqIds[0]);
            if (byJobType == null && singleReqSkill != null && singleReqSkill.getName() != null) {
                return new PrimarySkillTarget(singleReqSkill, singleReqSkill.getName().trim(), false);
            }
            if (byJobType != null && byJobType.getSkillId().equals(reqIds[0])) {
                return new PrimarySkillTarget(byJobType, byJobType.getName().trim(), false);
            }
        }

        if (byJobType != null) {
            boolean ambiguous = reqCount > 1;
            return new PrimarySkillTarget(byJobType, byJobType.getName().trim(), ambiguous);
        }

        if (rawJobType != null) {
            boolean ambiguous = reqCount > 1;
            return new PrimarySkillTarget(null, rawJobType, ambiguous);
        }

        return null;
    }

    private Skill lookupSkillById(UUID skillId) {
        if (skillId == null) {
            return null;
        }
        Optional<Skill> direct = skillRepository.findById(skillId);
        if (direct.isPresent()) {
            return direct.get();
        }
        return skillRepository.findAllById(List.of(skillId)).stream()
                .filter(s -> skillId.equals(s.getSkillId()))
                .findFirst()
                .orElse(null);
    }

    private Skill lookupSkillByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return skillRepository.findByNameIgnoreCase(name.trim()).orElse(null);
    }

    private WorkerSkillPoint findOrCreateSkillPoint(Worker worker, Skill skill, String jobType) {
        if (skill != null && skill.getSkillId() != null) {
            Optional<WorkerSkillPoint> bySkillId = workerSkillPointRepository
                    .findByWorker_WorkerIdAndSkill_SkillId(worker.getWorkerId(), skill.getSkillId());
            if (bySkillId.isPresent()) {
                return bySkillId.get();
            }
        }

        String lookupJobType = (skill != null && skill.getName() != null && !skill.getName().isBlank())
                ? skill.getName().trim()
                : jobType;

        Optional<WorkerSkillPoint> byJobType = workerSkillPointRepository
                .findByWorker_WorkerIdAndJobType(worker.getWorkerId(), lookupJobType);
        if (byJobType.isEmpty() && lookupJobType != null) {
            byJobType = workerSkillPointRepository.findFirstByWorker_WorkerIdAndJobTypeIgnoreCase(
                    worker.getWorkerId(), lookupJobType);
        }

        if (byJobType.isPresent()) {
            WorkerSkillPoint existing = byJobType.get();
            if (existing.getSkill() == null && skill != null) {
                existing.setSkill(skill);
            }
            return existing;
        }

        return WorkerSkillPoint.builder()
                .worker(worker)
                .skill(skill)
                .jobType(lookupJobType)
                .spVerify(0)
                .spExperience(0)
                .spRating(0)
                .totalSp(0)
                .tier(SkillTier.STARTER)
                .totalShiftsCompleted(0)
                .totalShiftsCancelled(0)
                .avgRating(DEFAULT_RATING)
                .ratingCount(0)
                .build();
    }

    private void recalculatePointAndTier(WorkerSkillPoint wsp) {
        int verify = wsp.getSpVerify() != null ? wsp.getSpVerify() : 0;
        int exp = wsp.getSpExperience() != null ? wsp.getSpExperience() : 0;
        int rating = wsp.getSpRating() != null ? wsp.getSpRating() : 0;
        int total = verify + exp + rating;
        wsp.setTotalSp(total);
        wsp.setTier(SkillTier.fromTotalSp(total));
    }

    private void syncWorkerOverallTier(Worker worker, SkillTier skillTier) {
        if (skillTier == null) {
            return;
        }
        SkillTier current = worker.getSkillTier() != null ? worker.getSkillTier() : SkillTier.STARTER;
        if (skillTier.ordinal() > current.ordinal()) {
            worker.setSkillTier(skillTier);
            workerRepository.save(worker);
        }
    }

    public record PrimarySkillTarget(Skill skill, String canonicalJobType, boolean ambiguousLegacyShift) {
    }
}
