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
        if (shift.getEndTime() != null && effectiveNow.isAfter(shift.getEndTime())) {
            return false;
        }
        int required = resolveRequiredWorkers(shift);
        int confirmed = resolveConfirmedWorkersCount(shift);
        if (confirmed >= required) {
            return false;
        }
        int alertWindowHours = Math.max(1, understaffedAlertHours);
        Instant windowStart = shift.getStartTime().minus(Duration.ofHours(alertWindowHours));
        return !effectiveNow.isBefore(windowStart);
    }

    /**
     * Đánh giá toàn diện quyền kích hoạt Emergency Dispatch thủ công & dữ liệu hiển thị thống nhất cho Web + API.
     */
    public EmergencyEligibility evaluateEmergencyEligibility(Shift shift, Instant now) {
        Instant effectiveNow = now != null ? now : Instant.now();
        if (shift == null) {
            return new EmergencyEligibility(false, "Không tìm thấy thông tin ca làm việc.", null, 0, 0, 0, false, false, 0, resolveEffectiveEmergencyRadiusKm(null));
        }

        Event event = shift.getEvent();
        boolean inviteMode = event != null && event.getMode() == EventMode.INVITE;
        ShiftStatus effectiveStatus = resolveEffectiveShiftStatus(shift, effectiveNow);
        int required = resolveRequiredWorkers(shift);
        int confirmed = resolveConfirmedWorkersCount(shift);
        int missing = Math.max(0, required - confirmed);
        boolean nearStart = isNearStartUnderstaffed(shift, effectiveNow);
        int pendingProposals = resolvePendingProposalCount(shift);
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
        } else if (effectiveStatus == ShiftStatus.COMPLETED) {
            blockReason = "Ca làm việc đã kết thúc, không thể kích hoạt điều phối khẩn cấp.";
        } else if (missing <= 0 || effectiveStatus == ShiftStatus.FULLY_STAFFED) {
            blockReason = "Ca làm việc đã tuyển đủ nhân sự, không cần kích hoạt điều phối khẩn cấp.";
        } else if (effectiveStatus != ShiftStatus.ACTIVE && effectiveStatus != ShiftStatus.IN_PROGRESS) {
            blockReason = "Chỉ có thể kích hoạt điều phối khẩn cấp cho ca làm việc đang mở tuyển hoặc đang diễn ra.";
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
                pendingProposals,
                defaultRadius
        );
    }
}
