package com.cani.service;

import com.cani.entity.Shift;
import com.cani.entity.Skill;
import com.cani.entity.Worker;
import com.cani.entity.WorkerSkill;
import com.cani.entity.WorkerSkillPoint;
import com.cani.entity.enums.ExperienceLevel;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.VerificationTier;
import com.cani.repository.SkillRepository;
import com.cani.repository.WorkerRepository;
import com.cani.repository.WorkerSkillPointRepository;
import com.cani.repository.WorkerSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public static final BigDecimal PLATFORM_PROVEN_MIN_AVG_RATING = new BigDecimal("4.50");
    public static final double PLATFORM_PROVEN_MIN_COMPLETION_RATE = 0.85;
    public static final int PLATFORM_PROVEN_MIN_RELIABILITY_SCORE = 70;
    public static final double PLATFORM_PROVEN_MAX_NOSHOW_RATE = 0.05;
    private static final BigDecimal DEFAULT_RATING = new BigDecimal("5.00");

    private final WorkerSkillPointRepository workerSkillPointRepository;
    private final WorkerSkillRepository workerSkillRepository;
    private final SkillRepository skillRepository;
    private final WorkerRepository workerRepository;

    /**
     * Cộng điểm kinh nghiệm (SP_experience) và số ca hoàn thành khi kết thúc ca thành công.
     */
    @Transactional
    public void recordCompletedShift(Worker worker, Shift shift) {
        if (worker == null || shift == null) {
            return;
        }

        Map<String, Skill> targets = resolveTargetJobTypes(shift);
        for (Map.Entry<String, Skill> entry : targets.entrySet()) {
            String jobType = entry.getKey();
            Skill matchedSkill = entry.getValue();

            WorkerSkillPoint wsp = findOrCreateSkillPoint(worker, jobType);
            int completed = (wsp.getTotalShiftsCompleted() != null ? wsp.getTotalShiftsCompleted() : 0) + 1;
            int spExp = (wsp.getSpExperience() != null ? wsp.getSpExperience() : 0) + SP_EXPERIENCE_PER_SHIFT;

            wsp.setTotalShiftsCompleted(completed);
            wsp.setSpExperience(spExp);
            recalculatePointAndTier(wsp);

            tryPromoteToPlatformProven(worker, matchedSkill, jobType, wsp);
            workerSkillPointRepository.save(wsp);
            syncWorkerOverallTier(worker, wsp.getTier());
        }
    }

    /**
     * Cộng điểm đánh giá (SP_rating), cập nhật rating trung bình theo kỹ năng
     * và kiểm tra thăng hạng Tier 3 (PLATFORM_PROVEN) sau khi EO chấm sao.
     */
    @Transactional
    public void recordShiftRating(Worker worker, Shift shift, int rating) {
        if (worker == null || shift == null) {
            return;
        }

        int ratingBonus = resolveRatingBonus(rating);
        Map<String, Skill> targets = resolveTargetJobTypes(shift);

        for (Map.Entry<String, Skill> entry : targets.entrySet()) {
            String jobType = entry.getKey();
            Skill matchedSkill = entry.getValue();

            WorkerSkillPoint wsp = findOrCreateSkillPoint(worker, jobType);
            updateSkillAvgRating(wsp, rating);

            int currentSpRating = wsp.getSpRating() != null ? wsp.getSpRating() : 0;
            wsp.setSpRating(currentSpRating + ratingBonus);
            recalculatePointAndTier(wsp);

            tryPromoteToPlatformProven(worker, matchedSkill, jobType, wsp);
            workerSkillPointRepository.save(wsp);
            syncWorkerOverallTier(worker, wsp.getTier());
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
        int completedCount = wsp.getTotalShiftsCompleted() != null ? wsp.getTotalShiftsCompleted() : 0;
        int divisor = Math.max(1, completedCount);
        if (divisor == 1) {
            wsp.setAvgRating(BigDecimal.valueOf(newRating).setScale(2, RoundingMode.HALF_UP));
            return;
        }
        BigDecimal currentAvg = wsp.getAvgRating() != null ? wsp.getAvgRating() : DEFAULT_RATING;
        BigDecimal updated = currentAvg.multiply(BigDecimal.valueOf(divisor - 1))
                .add(BigDecimal.valueOf(newRating))
                .divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
        wsp.setAvgRating(updated);
    }

    /**
     * Ghi nhận lượt hủy ca theo kỹ năng để tính chính xác tỷ lệ hoàn thành ca (Practical Skill Validation V2).
     */
    @Transactional
    public void recordCancelledShift(Worker worker, Shift shift) {
        if (worker == null || shift == null) {
            return;
        }
        Map<String, Skill> targets = resolveTargetJobTypes(shift);
        for (String jobType : targets.keySet()) {
            WorkerSkillPoint wsp = findOrCreateSkillPoint(worker, jobType);
            int cancelled = (wsp.getTotalShiftsCancelled() != null ? wsp.getTotalShiftsCancelled() : 0) + 1;
            wsp.setTotalShiftsCancelled(cancelled);
            workerSkillPointRepository.save(wsp);
        }
    }

    private void tryPromoteToPlatformProven(Worker worker, Skill matchedSkill, String jobType, WorkerSkillPoint wsp) {
        int completed = wsp.getTotalShiftsCompleted() != null ? wsp.getTotalShiftsCompleted() : 0;
        int cancelled = wsp.getTotalShiftsCancelled() != null ? wsp.getTotalShiftsCancelled() : 0;
        BigDecimal avgRating = wsp.getAvgRating() != null ? wsp.getAvgRating() : BigDecimal.ZERO;

        if (completed < PLATFORM_PROVEN_MIN_SHIFTS || avgRating.compareTo(PLATFORM_PROVEN_MIN_AVG_RATING) < 0) {
            return;
        }

        // Practical Validation V2: kiểm tra tỷ lệ hoàn thành ca, điểm uy tín và tỷ lệ No-Show
        // Không nâng lên Tier 3 chỉ vì có đánh giá 5 sao nếu Worker thường xuyên hủy ca hoặc bỏ ca.
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
                : skillRepository.findByNameIgnoreCase(jobType).orElse(null);
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
            int provenSp = VerificationTier.PLATFORM_PROVEN.getSpVerify();
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

            log.info("Worker {} đã đạt chuẩn thực chiến PLATFORM_PROVEN cho kỹ năng '{}' ({} ca, rating {})",
                    worker.getWorkerId(), skill.getName(), completed, avgRating);
        }
    }

    private Map<String, Skill> resolveTargetJobTypes(Shift shift) {
        Map<String, Skill> result = new LinkedHashMap<>();

        if (shift.getRequiredSkills() != null && shift.getRequiredSkills().length > 0) {
            List<UUID> ids = Arrays.stream(shift.getRequiredSkills())
                    .filter(Objects::nonNull)
                    .toList();
            if (!ids.isEmpty()) {
                for (Skill s : skillRepository.findAllById(ids)) {
                    if (s.getName() != null && !s.getName().isBlank()) {
                        result.putIfAbsent(s.getName().trim(), s);
                    }
                }
            }
        }

        if (shift.getJobType() != null && !shift.getJobType().isBlank()) {
            String rawJobType = shift.getJobType().trim();
            boolean alreadyIncluded = result.keySet().stream()
                    .anyMatch(k -> k.equalsIgnoreCase(rawJobType));
            if (!alreadyIncluded) {
                Skill byName = skillRepository.findByNameIgnoreCase(rawJobType).orElse(null);
                String canonicalName = (byName != null && byName.getName() != null) ? byName.getName() : rawJobType;
                result.putIfAbsent(canonicalName, byName);
            }
        }

        return result;
    }

    private WorkerSkillPoint findOrCreateSkillPoint(Worker worker, String jobType) {
        return workerSkillPointRepository.findByWorker_WorkerIdAndJobType(worker.getWorkerId(), jobType)
                .orElseGet(() -> WorkerSkillPoint.builder()
                        .worker(worker)
                        .jobType(jobType)
                        .spVerify(0)
                        .spExperience(0)
                        .spRating(0)
                        .totalSp(0)
                        .tier(SkillTier.STARTER)
                        .totalShiftsCompleted(0)
                        .totalShiftsCancelled(0)
                        .avgRating(DEFAULT_RATING)
                        .build());
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
}
