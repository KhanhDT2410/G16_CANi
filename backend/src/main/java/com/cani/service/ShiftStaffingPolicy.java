package com.cani.service;

import com.cani.entity.Event;
import com.cani.entity.Shift;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.EventMode;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.ProposalStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.repository.DispatchProposalRepository;
import com.cani.repository.ShiftAssignmentRepository;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Single Source of Truth cho quy tắc thiếu nhân sự sát giờ & điều kiện kích hoạt Emergency Dispatch.
 * Dùng chung cho:
 * 1. ShiftLifecycleService (job quét thiếu người sát giờ)
 * 2. EoEventService / ShiftResponseDto (trả trạng thái + quyền kích hoạt về Web EO)
 * 3. EoEventService.triggerEmergencyDispatch & MatchingDispatchService (kiểm tra API trước khi điều phối)
 */
@Service
public class ShiftStaffingPolicy {

    public static final int DEFAULT_UNDERSTAFFED_ALERT_HOURS = 4;
    public static final double DEFAULT_EMERGENCY_RADIUS_KM = 25.0;
    public static final long MIN_REMAINING_MINUTES_FOR_AUTO_EMERGENCY = 30L;
    public static final List<AssignmentStatus> VALID_CONFIRMED_STATUSES = List.of(
            AssignmentStatus.CONFIRMED,
            AssignmentStatus.CHECKED_IN,
            AssignmentStatus.COMPLETED
    );

    @Autowired(required = false)
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired(required = false)
    private DispatchProposalRepository dispatchProposalRepository;

    @Getter
    @Setter
    @Value("${cani.shift-lifecycle.understaffed-alert-hours:4}")
    private int understaffedAlertHours = DEFAULT_UNDERSTAFFED_ALERT_HOURS;

    @Getter
    @Setter
    @Value("${cani.shift-lifecycle.emergency-default-radius-km:25}")
    private double emergencyDefaultRadiusKm = DEFAULT_EMERGENCY_RADIUS_KM;

    public ShiftStaffingPolicy() {
    }

    public static ShiftStaffingPolicy withFallbackDependencies(
            ShiftAssignmentRepository shiftAssignmentRepository,
            DispatchProposalRepository dispatchProposalRepository
    ) {
        ShiftStaffingPolicy policy = new ShiftStaffingPolicy();
        policy.shiftAssignmentRepository = shiftAssignmentRepository;
        policy.dispatchProposalRepository = dispatchProposalRepository;
        return policy;
    }

    public record EmergencyEligibility(
            boolean canTriggerEmergency,
            String blockReason,
            ShiftStatus effectiveStatus,
            int requiredWorkers,
            int confirmedWorkers,
            int missingWorkers,
            boolean nearStartUnderstaffed,
            boolean inviteMode,
            int pendingProposalCount,
            double defaultEmergencyRadiusKm
    ) {
    }

    public record AutoEmergencyEligibility(
            boolean eligible,
            String blockReason,
            ShiftStatus effectiveStatus,
            int requiredWorkers,
            int confirmedWorkers,
            int missingWorkers,
            double effectiveRadiusKm
    ) {
    }

    public int resolveRequiredWorkers(Shift shift) {
        if (shift == null || shift.getRequiredWorkers() == null) {
            return 1;
        }
        return Math.max(1, shift.getRequiredWorkers());
    }

    /**
     * Số Worker đã chốt hợp lệ (CONFIRMED / CHECKED_IN / COMPLETED).
     * Đề xuất đang chờ phản hồi (DispatchProposal PENDING) hay assignment PENDING/CANCELLED/NO_SHOW không được tính.
     */
    public int resolveConfirmedWorkersCount(Shift shift) {
        if (shift == null) {
            return 0;
        }
        if (shiftAssignmentRepository != null && shift.getShiftId() != null
                && shiftAssignmentRepository.existsByShift_ShiftId(shift.getShiftId())) {
            long validCount = shiftAssignmentRepository.countByShift_ShiftIdAndStatusIn(
                    shift.getShiftId(),
                    VALID_CONFIRMED_STATUSES
            );
            return (int) Math.max(0L, validCount);
        }
        return Math.max(0, shift.getConfirmedWorkers() != null ? shift.getConfirmedWorkers() : 0);
    }

    public int resolvePendingProposalCount(Shift shift) {
        if (shift == null || shift.getShiftId() == null || dispatchProposalRepository == null) {
            return 0;
        }
        long count = dispatchProposalRepository.countByShift_ShiftIdAndStatusIn(
                shift.getShiftId(),
                List.of(ProposalStatus.PENDING)
        );
        return (int) Math.max(0L, count);
    }

    public ShiftStatus resolveEffectiveShiftStatus(Shift shift, Instant now) {
        if (shift == null) {
            return null;
        }
        ShiftStatus raw = shift.getStatus();
        if (raw == ShiftStatus.CANCELLED || raw == ShiftStatus.DRAFT || raw == ShiftStatus.SUSPENDED) {
            return raw;
        }
        Instant effectiveNow = now != null ? now : Instant.now();
        if (shift.getEndTime() != null && effectiveNow.isAfter(shift.getEndTime())) {
            return ShiftStatus.COMPLETED;
        }
        if (shift.getStartTime() != null && !effectiveNow.isBefore(shift.getStartTime())) {
            if (raw == ShiftStatus.ACTIVE || raw == ShiftStatus.FULLY_STAFFED || raw == ShiftStatus.IN_PROGRESS) {
                return ShiftStatus.IN_PROGRESS;
            }
        }
        return raw;
    }

    public double resolveEffectiveEmergencyRadiusKm(Double requestedRadiusKm) {
        double fallback = (emergencyDefaultRadiusKm > 0 && Double.isFinite(emergencyDefaultRadiusKm))
                ? emergencyDefaultRadiusKm
                : DEFAULT_EMERGENCY_RADIUS_KM;
        double raw = (requestedRadiusKm != null && requestedRadiusKm > 0 && Double.isFinite(requestedRadiusKm))
                ? requestedRadiusKm
                : fallback;
        return Math.min(Math.max(raw, 1.0), 50.0);
    }

    public boolean isEventEligibleForStaffingOperations(Event event) {
        if (event == null) {
            return false;
        }
        EventStatus st = event.getStatus();
        return st != EventStatus.DRAFT
                && st != EventStatus.CANCELLED
                && st != EventStatus.COMPLETED
                && st != EventStatus.SUSPENDED;
    }

    /**
     * Điều kiện ca nằm trong khung cảnh báo thiếu nhân sự sát giờ (mặc định <= 4h trước startTime đến trước endTime).
     */
    public boolean isNearStartUnderstaffed(Shift shift, Instant now) {
        if (!isShiftEligibleForNearStartUnderstaffedCheck(shift, now)) {
            return false;
        }
        int confirmed = resolveConfirmedWorkersCount(shift);
        return isNearStartUnderstaffed(shift, now, confirmed);
    }

    public boolean isNearStartUnderstaffed(Shift shift, Instant now, int confirmedWorkers) {
        if (!isShiftEligibleForNearStartUnderstaffedCheck(shift, now)) {
            return false;
        }
        Instant effectiveNow = now != null ? now : Instant.now();
        int required = resolveRequiredWorkers(shift);
        int confirmed = Math.max(0, confirmedWorkers);
        if (confirmed >= required) {
            return false;
        }
        // Nếu hệ thống đã bật chế độ khẩn cấp (vd do Worker hủy ca hoặc No-show) -> hiển thị cảnh báo ngay
        if (Boolean.TRUE.equals(shift.getEmergency())) {
            return true;
        }
        int alertWindowHours = Math.max(1, understaffedAlertHours);
        Instant windowStart = shift.getStartTime().minus(Duration.ofHours(alertWindowHours));
        return !effectiveNow.isBefore(windowStart);
    }

    private boolean isShiftEligibleForNearStartUnderstaffedCheck(Shift shift, Instant now) {
        if (shift == null || shift.getStartTime() == null) {
            return false;
        }
        Event event = shift.getEvent();
        if (!isEventEligibleForStaffingOperations(event)) {
            return false;
        }
        Instant effectiveNow = now != null ? now : Instant.now();
        ShiftStatus effectiveStatus = resolveEffectiveShiftStatus(shift, effectiveNow);
        if (effectiveStatus == ShiftStatus.DRAFT
                || effectiveStatus == ShiftStatus.CANCELLED
                || effectiveStatus == ShiftStatus.COMPLETED
                || effectiveStatus == ShiftStatus.SUSPENDED) {
            return false;
        }
        return shift.getEndTime() == null || !effectiveNow.isAfter(shift.getEndTime());
    }

    /**
     * Đánh giá điều kiện cho đường TỰ ĐỘNG (No-show / Worker tự hủy làm thiếu người):
     * - Cho phép cả trước giờ bắt đầu (ACTIVE) và sau giờ bắt đầu (IN_PROGRESS) nếu còn đủ thời gian làm việc (>= 30 phút).
     * - Cả hai đường đều dừng khi ca đã kết thúc (now >= endTime / COMPLETED), bị hủy, tạm đình chỉ, nháp, INVITE hoặc đã đủ người.
     */
    public AutoEmergencyEligibility evaluateAutoEmergencyEligibility(Shift shift, Instant now) {
        Instant effectiveNow = now != null ? now : Instant.now();
        double defaultRadius = resolveEffectiveEmergencyRadiusKm(null);
        if (shift == null) {
            return new AutoEmergencyEligibility(false, "Không tìm thấy ca làm việc.", null, 0, 0, 0, defaultRadius);
        }

        Event event = shift.getEvent();
        boolean inviteMode = event != null && event.getMode() == EventMode.INVITE;
        ShiftStatus effectiveStatus = resolveEffectiveShiftStatus(shift, effectiveNow);
        int required = resolveRequiredWorkers(shift);
        int confirmed = resolveConfirmedWorkersCount(shift);
        int missing = Math.max(0, required - confirmed);

        String blockReason = null;
        if (inviteMode) {
            blockReason = "Sự kiện đang ở chế độ Mời trực tiếp (INVITE), không kích hoạt điều phối tự động.";
        } else if (event != null && !isEventEligibleForStaffingOperations(event)) {
            blockReason = "Sự kiện không còn ở trạng thái mở tuyển dụng.";
        } else if (effectiveStatus == ShiftStatus.CANCELLED) {
            blockReason = "Ca làm việc đã bị hủy.";
        } else if (effectiveStatus == ShiftStatus.DRAFT) {
            blockReason = "Ca làm việc đang ở trạng thái bản nháp.";
        } else if (effectiveStatus == ShiftStatus.SUSPENDED) {
            blockReason = "Ca làm việc đang bị tạm đình chỉ.";
        } else if (effectiveStatus == ShiftStatus.COMPLETED
                || (shift.getEndTime() != null && !effectiveNow.isBefore(shift.getEndTime()))) {
            blockReason = "Ca làm việc đã kết thúc.";
        } else if (missing <= 0 || effectiveStatus == ShiftStatus.FULLY_STAFFED) {
            blockReason = "Ca làm việc vẫn đủ nhân sự.";
        } else if (effectiveStatus == ShiftStatus.IN_PROGRESS
                || (shift.getStartTime() != null && !effectiveNow.isBefore(shift.getStartTime()))) {
            if (shift.getEndTime() == null
                    || Duration.between(effectiveNow, shift.getEndTime()).toMinutes() < MIN_REMAINING_MINUTES_FOR_AUTO_EMERGENCY) {
                blockReason = "Ca làm việc đã vào giờ và không còn đủ thời gian tối thiểu (30 phút) để điều phối thay thế.";
            }
        } else if (effectiveStatus != ShiftStatus.ACTIVE) {
            blockReason = "Trạng thái ca làm việc không hợp lệ để điều phối.";
        }

        return new AutoEmergencyEligibility(
                blockReason == null,
                blockReason,
                effectiveStatus,
                required,
                confirmed,
                missing,
                defaultRadius
        );
    }

    /**
     * Đánh giá toàn diện quyền kích hoạt Emergency Dispatch THỦ CÔNG (chỉ trước giờ bắt đầu ca) & dữ liệu hiển thị cho Web + API.
     */
    public EmergencyEligibility evaluateEmergencyEligibility(Shift shift, Instant now) {
        Instant effectiveNow = now != null ? now : Instant.now();
        if (shift == null) {
            return new EmergencyEligibility(false, "Không tìm thấy thông tin ca làm việc.", null, 0, 0, 0, false, false, 0, resolveEffectiveEmergencyRadiusKm(null));
        }
        int confirmed = resolveConfirmedWorkersCount(shift);
        int pendingProposals = resolvePendingProposalCount(shift);
        return evaluateEmergencyEligibility(shift, effectiveNow, confirmed, pendingProposals);
    }

    public EmergencyEligibility evaluateEmergencyEligibility(
            Shift shift,
            Instant now,
            int confirmedWorkers,
            int pendingProposals
    ) {
        Instant effectiveNow = now != null ? now : Instant.now();
        if (shift == null) {
            return new EmergencyEligibility(false, "Không tìm thấy thông tin ca làm việc.", null, 0, 0, 0, false, false, 0, resolveEffectiveEmergencyRadiusKm(null));
        }

        Event event = shift.getEvent();
        boolean inviteMode = event != null && event.getMode() == EventMode.INVITE;
        ShiftStatus effectiveStatus = resolveEffectiveShiftStatus(shift, effectiveNow);
        int required = resolveRequiredWorkers(shift);
        int confirmed = Math.max(0, confirmedWorkers);
        int missing = Math.max(0, required - confirmed);
        boolean nearStart = isNearStartUnderstaffed(shift, effectiveNow, confirmed);
        int resolvedPendingProposals = Math.max(0, pendingProposals);
        double defaultRadius = resolveEffectiveEmergencyRadiusKm(null);

        String blockReason = null;
        if (inviteMode) {
            blockReason = "Sự kiện đang ở chế độ Mời trực tiếp (INVITE), không hỗ trợ điều phối tự động khẩn cấp (Matching Engine).";
        } else if (event != null && !isEventEligibleForStaffingOperations(event)) {
            blockReason = "Sự kiện chưa mở tuyển hoặc đã kết thúc/hủy, không thể kích hoạt điều phối khẩn cấp.";
        } else if (effectiveStatus == ShiftStatus.CANCELLED) {
            blockReason = "Ca làm việc đã bị hủy, không thể kích hoạt điều phối khẩn cấp.";
        } else if (effectiveStatus == ShiftStatus.DRAFT) {
            blockReason = "Ca làm việc đang ở trạng thái bản nháp, vui lòng xuất bản trước khi điều phối.";
        } else if (effectiveStatus == ShiftStatus.SUSPENDED) {
            blockReason = "Ca làm việc đang bị tạm đình chỉ.";
        } else if (effectiveStatus == ShiftStatus.COMPLETED
                || (shift.getEndTime() != null && !effectiveNow.isBefore(shift.getEndTime()))) {
            blockReason = "Ca làm việc đã kết thúc, không thể kích hoạt điều phối khẩn cấp.";
        } else if (missing <= 0 || effectiveStatus == ShiftStatus.FULLY_STAFFED) {
            blockReason = "Ca làm việc đã tuyển đủ nhân sự, không cần kích hoạt điều phối khẩn cấp.";
        } else if (effectiveStatus == ShiftStatus.IN_PROGRESS
                || (shift.getStartTime() != null && !effectiveNow.isBefore(shift.getStartTime()))) {
            blockReason = "Ca làm việc đã đến giờ bắt đầu (IN_PROGRESS), không thể bật điều phối khẩn cấp thủ công.";
        } else if (effectiveStatus != ShiftStatus.ACTIVE) {
            blockReason = "Chỉ có thể kích hoạt điều phối khẩn cấp thủ công cho ca làm việc đang mở tuyển trước giờ bắt đầu.";
        } else if (Boolean.TRUE.equals(shift.getEmergency())) {
            blockReason = "Ca làm việc đã được bật điều phối khẩn cấp trước đó.";
        }

        return new EmergencyEligibility(
                blockReason == null,
                blockReason,
                effectiveStatus,
                required,
                confirmed,
                missing,
                nearStart,
                inviteMode,
                resolvedPendingProposals,
                defaultRadius
        );
    }
}
