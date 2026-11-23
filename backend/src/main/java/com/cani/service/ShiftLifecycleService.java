package com.cani.service;

import com.cani.entity.Event;
import com.cani.entity.Shift;
import com.cani.entity.enums.EventMode;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.ShiftStatus;
import com.cani.repository.DispatchProposalRepository;
import com.cani.repository.EventRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service quản lý vòng đời tự động của Ca làm việc (Shift) và Sự kiện (Event)
 * Định kỳ quét và chuyển đổi trạng thái (State Machine):
 * - Còn <= N giờ (mặc định 4h) trước giờ bắt đầu và thiếu nhân sự -> tạo 1 cảnh báo mỗi đợt cho EO.
 * - Đến giờ bắt đầu (startTime <= now <= endTime) -> chuyển sang IN_PROGRESS / ONGOING.
 * - Hết giờ kết thúc (now > endTime) -> chuyển sang COMPLETED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShiftLifecycleService {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final ShiftRepository shiftRepository;
    private final EventRepository eventRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired(required = false)
    private DispatchProposalRepository dispatchProposalRepository;

    @Autowired(required = false)
    private ShiftStaffingPolicy shiftStaffingPolicy;

    @Value("${cani.shift-lifecycle.understaffed-alert-hours:4}")
    private int understaffedAlertHours = ShiftStaffingPolicy.DEFAULT_UNDERSTAFFED_ALERT_HOURS;

    public void setUnderstaffedAlertHours(int understaffedAlertHours) {
        this.understaffedAlertHours = understaffedAlertHours;
        if (this.shiftStaffingPolicy != null) {
            this.shiftStaffingPolicy.setUnderstaffedAlertHours(understaffedAlertHours);
        }
    }

    private ShiftStaffingPolicy getStaffingPolicy() {
        if (shiftStaffingPolicy != null) {
            return shiftStaffingPolicy;
        }
        ShiftStaffingPolicy fallback = ShiftStaffingPolicy.withFallbackDependencies(
                shiftAssignmentRepository,
                dispatchProposalRepository
        );
        fallback.setUnderstaffedAlertHours(understaffedAlertHours);
        return fallback;
    }

    @Scheduled(cron = "${cani.shift-lifecycle.cron:0 */1 * * * *}")
    @Transactional
    public void evaluateShiftsAndEventsLifecycle() {
        evaluateShiftsAndEventsLifecycleAt(Instant.now());
    }

    @Transactional
    public void evaluateShiftsAndEventsLifecycleAt(Instant now) {
        Instant effectiveNow = now != null ? now : Instant.now();
        ShiftStaffingPolicy policy = getStaffingPolicy();

        // 1. Quét và cập nhật trạng thái các Ca làm việc (Shifts) + phát hiện thiếu người sát giờ
        List<Shift> nonCancelledShifts = shiftRepository.findAll().stream()
                .filter(s -> s.getStatus() != null
                        && s.getStatus() != ShiftStatus.CANCELLED
                        && s.getStatus() != ShiftStatus.DRAFT
                        && s.getStatus() != ShiftStatus.SUSPENDED)
                .toList();

        int shiftsCompleted = 0;
        int shiftsInProgress = 0;
        int understaffedAlertsSent = 0;

        for (Shift s : nonCancelledShifts) {
            // Trường hợp 1: Ca đã qua giờ kết thúc -> COMPLETED
            if (s.getEndTime() != null && effectiveNow.isAfter(s.getEndTime())) {
                if (s.getStatus() != ShiftStatus.COMPLETED) {
                    s.setStatus(ShiftStatus.COMPLETED);
                    s.setEmergency(false); // Ca kết thúc thì tự động tắt emergency
                    s.setUnderstaffedAlertSent(false);
                    shiftRepository.save(s);
                    shiftsCompleted++;
                    notifyEoShiftLifecycle(
                            s,
                            "Ca làm việc đã kết thúc",
                            "Ca \"" + s.getJobType() + "\" thuộc sự kiện \"" + getEventName(s) + "\" đã hết giờ. Vui lòng kiểm tra duyệt công và quyết toán thù lao.",
                            NotificationType.REMINDER,
                            "SHIFT_COMPLETED"
                    );
                }
                continue;
            }

            // Trường hợp 2: Ca đang trong khung giờ làm việc -> IN_PROGRESS
            if (s.getStartTime() != null && !effectiveNow.isBefore(s.getStartTime())) {
                if (s.getStatus() == ShiftStatus.ACTIVE || s.getStatus() == ShiftStatus.FULLY_STAFFED) {
                    s.setStatus(ShiftStatus.IN_PROGRESS);
                    shiftRepository.save(s);
                    shiftsInProgress++;
                }
            }

            // Trường hợp 3: Đánh giá thiếu nhân sự sát giờ (<= N giờ trước giờ bắt đầu hoặc đang diễn ra nhưng chưa đủ người)
            if (!policy.isEventEligibleForStaffingOperations(s.getEvent())) {
                continue;
            }
            if (policy.resolveEffectiveShiftStatus(s, effectiveNow) == ShiftStatus.COMPLETED) {
                continue;
            }

            int required = policy.resolveRequiredWorkers(s);
            int confirmed = policy.resolveConfirmedWorkersCount(s);

            if (confirmed >= required) {
                if (Boolean.TRUE.equals(s.getUnderstaffedAlertSent()) || Boolean.TRUE.equals(s.getEmergency())) {
                    s.setUnderstaffedAlertSent(false);
                    s.setEmergency(false);
                    shiftRepository.save(s);
                }
                continue;
            }

            if (policy.isNearStartUnderstaffed(s, effectiveNow)
                    && !Boolean.TRUE.equals(s.getUnderstaffedAlertSent())
                    && !Boolean.TRUE.equals(s.getEmergency())) {
                sendNearStartUnderstaffedNotification(s, policy.resolveEffectiveShiftStatus(s, effectiveNow), confirmed, required);
                s.setUnderstaffedAlertSent(true);
                shiftRepository.save(s);
                understaffedAlertsSent++;
            }
        }

        // 2. Quét và cập nhật trạng thái Sự kiện (Events)
        List<Event> nonCancelledEvents = eventRepository.findAll().stream()
                .filter(e -> e.getStatus() != null && e.getStatus() != EventStatus.CANCELLED && e.getStatus() != EventStatus.DRAFT)
                .toList();

        int eventsCompleted = 0;
        int eventsOngoing = 0;

        for (Event e : nonCancelledEvents) {
            List<Shift> eventShifts = shiftRepository.findByEvent_EventId(e.getEventId());
            boolean allShiftsCompleted = !eventShifts.isEmpty() && eventShifts.stream()
                    .allMatch(s -> s.getStatus() == ShiftStatus.COMPLETED || s.getStatus() == ShiftStatus.CANCELLED);

            // Sự kiện đã qua giờ kết thúc hoặc toàn bộ ca đã xong -> COMPLETED
            if ((e.getEndTime() != null && effectiveNow.isAfter(e.getEndTime())) || allShiftsCompleted) {
                if (e.getStatus() != EventStatus.COMPLETED) {
                    e.setStatus(EventStatus.COMPLETED);
                    eventRepository.save(e);
                    eventsCompleted++;
                }
            }
            // Sự kiện đang trong khung giờ diễn ra -> ONGOING
            else if (e.getStartTime() != null && !effectiveNow.isBefore(e.getStartTime())) {
                if (e.getStatus() == EventStatus.ACTIVE) {
                    e.setStatus(EventStatus.ONGOING);
                    eventRepository.save(e);
                    eventsOngoing++;
                }
            }
        }

        if (shiftsCompleted > 0 || shiftsInProgress > 0 || eventsCompleted > 0 || eventsOngoing > 0 || understaffedAlertsSent > 0) {
            log.info("ShiftLifecycleService: Cập nhật trạng thái vòng đời: {} ca hoàn thành, {} ca vào giờ làm, {} cảnh báo thiếu người sát giờ, {} sự kiện kết thúc, {} sự kiện đang diễn ra",
                    shiftsCompleted, shiftsInProgress, understaffedAlertsSent, eventsCompleted, eventsOngoing);
        }
    }

    private void sendNearStartUnderstaffedNotification(Shift s, ShiftStatus effectiveStatus, int confirmed, int required) {
        int missing = Math.max(1, required - confirmed);
        boolean inviteMode = s.getEvent() != null && s.getEvent().getMode() == EventMode.INVITE;
        String startText = s.getStartTime() != null ? TIME_FMT.format(s.getStartTime()) : "--:--";

        String title;
        String actionHint;
        if (effectiveStatus == ShiftStatus.IN_PROGRESS) {
            title = "Cảnh báo thiếu nhân sự khi vào ca: " + s.getJobType();
            actionHint = inviteMode
                    ? "Ca đã bắt đầu, bạn có thể mời thêm Worker hoặc kiểm tra điểm danh."
                    : "Ca đã bắt đầu, bạn có thể bật điều phối khẩn cấp để bù người hoặc kiểm tra điểm danh/báo cáo vắng mặt.";
        } else {
            title = "Thiếu nhân sự sát giờ: " + s.getJobType();
            actionHint = inviteMode
                    ? "Vui lòng mở sự kiện để mời thêm Worker."
                    : "Mở sự kiện để xem chi tiết hoặc bật điều phối khẩn cấp.";
        }

        String body = String.format(
                "Ca %s (%s) bắt đầu lúc %s · Đã chốt %d/%d · Còn thiếu %d người. %s",
                s.getJobType(),
                getEventName(s),
                startText,
                confirmed,
                required,
                missing,
                actionHint
        );

        notifyEoShiftLifecycle(s, title, body, NotificationType.REMINDER, "UNDERSTAFFED_NEAR_START");
    }

    private String getEventName(Shift s) {
        return (s.getEvent() != null && s.getEvent().getName() != null) ? s.getEvent().getName() : "Sự kiện";
    }

    private void notifyEoShiftLifecycle(Shift s, String title, String body, NotificationType type, String alertType) {
        if (notificationService == null || s == null || s.getEvent() == null) return;
        Event ev = s.getEvent();
        if (ev.getEventOrganizer() != null && ev.getEventOrganizer().getUser() != null
                && ev.getEventOrganizer().getUser().getUserId() != null) {
            try {
                notificationService.createAndSend(
                        ev.getEventOrganizer().getUser().getUserId(),
                        title,
                        body,
                        type,
                        "SHIFT",
                        s.getShiftId(),
                        Map.of(
                                "eventId", ev.getEventId().toString(),
                                "shiftId", s.getShiftId().toString(),
                                "type", alertType
                        )
                );
            } catch (Exception ex) {
                log.warn("Lỗi gửi thông báo vòng đời ca {}: {}", s.getShiftId(), ex.getMessage());
            }
        }
    }
}
