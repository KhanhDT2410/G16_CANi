package com.cani.service;

import com.cani.entity.Event;
import com.cani.entity.Shift;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.ShiftStatus;
import com.cani.repository.EventRepository;
import com.cani.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service quản lý vòng đời tự động của Ca làm việc (Shift) và Sự kiện (Event)
 * Định kỳ quét và chuyển đổi trạng thái (State Machine):
 * - Đến giờ bắt đầu (startTime <= now <= endTime) -> chuyển sang IN_PROGRESS / ONGOING.
 * - Hết giờ kết thúc (now > endTime) -> chuyển sang COMPLETED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShiftLifecycleService {

    private final ShiftRepository shiftRepository;
    private final EventRepository eventRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Scheduled(cron = "${cani.shift-lifecycle.cron:0 */1 * * * *}")
    @Transactional
    public void evaluateShiftsAndEventsLifecycle() {
        Instant now = Instant.now();

        // 1. Quét và cập nhật trạng thái các Ca làm việc (Shifts)
        List<Shift> nonCancelledShifts = shiftRepository.findAll().stream()
                .filter(s -> s.getStatus() != null && s.getStatus() != ShiftStatus.CANCELLED && s.getStatus() != ShiftStatus.DRAFT)
                .toList();

        int shiftsCompleted = 0;
        int shiftsInProgress = 0;

        for (Shift s : nonCancelledShifts) {
            // Trường hợp 1: Ca đã qua giờ kết thúc -> COMPLETED
            if (s.getEndTime() != null && now.isAfter(s.getEndTime())) {
                if (s.getStatus() != ShiftStatus.COMPLETED) {
                    s.setStatus(ShiftStatus.COMPLETED);
                    s.setEmergency(false); // Ca kết thúc thì tự động tắt emergency
                    shiftRepository.save(s);
                    shiftsCompleted++;
                    notifyEoShiftLifecycle(
                            s,
                            "Ca làm việc đã kết thúc",
                            "Ca \"" + s.getJobType() + "\" thuộc sự kiện \"" + getEventName(s) + "\" đã hết giờ. Vui lòng kiểm tra duyệt công và quyết toán thù lao.",
                            NotificationType.REMINDER
                    );
                }
            }
            // Trường hợp 2: Ca đang trong khung giờ làm việc -> IN_PROGRESS
            else if (s.getStartTime() != null && !now.isBefore(s.getStartTime())) {
                if (s.getStatus() == ShiftStatus.ACTIVE || s.getStatus() == ShiftStatus.FULLY_STAFFED) {
                    s.setStatus(ShiftStatus.IN_PROGRESS);
                    shiftRepository.save(s);
                    shiftsInProgress++;

                    int confirmed = s.getConfirmedWorkers() != null ? s.getConfirmedWorkers() : 0;
                    int required = s.getRequiredWorkers() != null ? s.getRequiredWorkers() : 1;
                    if (confirmed < required) {
                        notifyEoShiftLifecycle(
                                s,
                                "Cảnh báo thiếu nhân sự khi vào ca",
                                "Ca \"" + s.getJobType() + "\" (" + getEventName(s) + ") đã đến giờ làm nhưng mới có " + confirmed + "/" + required + " nhân sự. Bạn có thể bật điều phối khẩn cấp ngay.",
                                NotificationType.REMINDER
                        );
                    }
                }
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
            if ((e.getEndTime() != null && now.isAfter(e.getEndTime())) || allShiftsCompleted) {
                if (e.getStatus() != EventStatus.COMPLETED) {
                    e.setStatus(EventStatus.COMPLETED);
                    eventRepository.save(e);
                    eventsCompleted++;
                }
            }
            // Sự kiện đang trong khung giờ diễn ra -> ONGOING
            else if (e.getStartTime() != null && !now.isBefore(e.getStartTime())) {
                if (e.getStatus() == EventStatus.ACTIVE) {
                    e.setStatus(EventStatus.ONGOING);
                    eventRepository.save(e);
                    eventsOngoing++;
                }
            }
        }

        if (shiftsCompleted > 0 || shiftsInProgress > 0 || eventsCompleted > 0 || eventsOngoing > 0) {
            log.info("ShiftLifecycleService: Cập nhật trạng thái vòng đời: {} ca hoàn thành, {} ca vào giờ làm, {} sự kiện kết thúc, {} sự kiện đang diễn ra",
                    shiftsCompleted, shiftsInProgress, eventsCompleted, eventsOngoing);
        }
    }

    private String getEventName(Shift s) {
        return (s.getEvent() != null && s.getEvent().getName() != null) ? s.getEvent().getName() : "Sự kiện";
    }

    private void notifyEoShiftLifecycle(Shift s, String title, String body, NotificationType type) {
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
                                "shiftId", s.getShiftId().toString()
                        )
                );
            } catch (Exception ex) {
                log.warn("Lỗi gửi thông báo vòng đời ca {}: {}", s.getShiftId(), ex.getMessage());
            }
        }
    }
}
