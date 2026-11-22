package com.cani.service;

import com.cani.dto.admin.shift.AdminShiftDetailDto;
import com.cani.dto.admin.shift.AdminShiftSummaryDto;
import com.cani.dto.admin.shift.AdminShiftWorkerDto;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.PaymentStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.AttendanceLogRepository;
import com.cani.repository.PaymentRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminShiftService {

    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * Lấy danh sách ca làm việc cho Admin & CSKH (hỗ trợ lọc từ khóa và trạng thái, có phân trang).
     */
    public Page<AdminShiftSummaryDto> getShifts(String query, ShiftStatus status, Pageable pageable) {
        List<Shift> allShifts = shiftRepository.findAll();

        String q = query != null ? query.trim().toLowerCase() : "";

        List<Shift> filtered = allShifts.stream()
                .filter(s -> {
                    ShiftStatus effectiveStatus = resolveRealtimeStatus(s);
                    if (status != null && effectiveStatus != status) {
                        return false;
                    }
                    if (q.isEmpty()) {
                        return true;
                    }
                    String code = formatShiftCode(s.getShiftId()).toLowerCase();
                    String jobType = s.getJobType() != null ? s.getJobType().toLowerCase() : "";
                    String eventName = s.getEvent() != null && s.getEvent().getName() != null
                            ? s.getEvent().getName().toLowerCase() : "";
                    String eoName = "";
                    String eoPhone = "";
                    if (s.getEvent() != null && s.getEvent().getEventOrganizer() != null
                            && s.getEvent().getEventOrganizer().getUser() != null) {
                        User eoUser = s.getEvent().getEventOrganizer().getUser();
                        eoName = eoUser.getFullName() != null ? eoUser.getFullName().toLowerCase() : "";
                        eoPhone = eoUser.getPhone() != null ? eoUser.getPhone().toLowerCase() : "";
                    }
                    return code.contains(q) || jobType.contains(q) || eventName.contains(q)
                            || eoName.contains(q) || eoPhone.contains(q);
                })
                .sorted((a, b) -> {
                    // Sắp xếp ca mới nhất lên đầu
                    if (a.getStartTime() != null && b.getStartTime() != null) {
                        return b.getStartTime().compareTo(a.getStartTime());
                    }
                    if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    }
                    return 0;
                })
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());
        List<AdminShiftSummaryDto> pageContent = new ArrayList<>();

        if (start <= end && start < filtered.size()) {
            pageContent = filtered.subList(start, end).stream()
                    .map(this::toSummaryDto)
                    .collect(Collectors.toList());
        }

        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    /**
     * Xem chi tiết ca làm việc kèm danh sách Worker tham gia.
     */
    public AdminShiftDetailDto getShiftDetail(UUID shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc", "id", shiftId));

        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByShift_ShiftId(shiftId);

        List<AdminShiftWorkerDto> workers = assignments.stream()
                .map(sa -> {
                    Worker w = sa.getWorker();
                    User u = w != null ? w.getUser() : null;

                    Optional<AttendanceLog> attLog = attendanceLogRepository
                            .findByShiftAssignment_AssignmentId(sa.getAssignmentId());

                    return AdminShiftWorkerDto.builder()
                            .assignmentId(sa.getAssignmentId())
                            .workerId(w != null ? w.getWorkerId() : null)
                            .userId(u != null ? u.getUserId() : null)
                            .workerName(u != null ? u.getFullName() : "Nhân viên CANi")
                            .workerPhone(u != null ? u.getPhone() : "—")
                            .workerEmail(u != null ? u.getEmail() : "—")
                            .avatarUrl(u != null ? u.getAvatarUrl() : null)
                            .status(sa.getStatus())
                            .isGroupLeader(sa.getIsGroupLeader())
                            .checkinTime(attLog.map(AttendanceLog::getCheckinTime).orElse(null))
                            .checkoutTime(attLog.map(AttendanceLog::getCheckoutTime).orElse(null))
                            .attendanceStatus(attLog.map(al -> al.getAttendanceStatus() != null ? al.getAttendanceStatus().name() : "NONE").orElse("NONE"))
                            .checklistProgress(sa.getChecklistProgress())
                            .proofImageUrl(attLog.map(AttendanceLog::getProofImageUrl).orElse(null))
                            .isOnTime(attLog.map(AttendanceLog::getIsOnTime).orElse(null))
                            .eoConfirmed(sa.getEoConfirmed())
                            .eoConfirmedAt(sa.getEoConfirmedAt())
                            .build();
                })
                .collect(Collectors.toList());

        Event event = shift.getEvent();
        EventOrganizer eo = event != null ? event.getEventOrganizer() : null;
        User eoUser = eo != null ? eo.getUser() : null;

        PaymentStatus paymentStatus = null;
        List<Payment> shiftPayments = paymentRepository.findByShiftId(shiftId);
        if (!shiftPayments.isEmpty()) {
            paymentStatus = shiftPayments.get(shiftPayments.size() - 1).getStatus();
        }

        return AdminShiftDetailDto.builder()
                .shiftId(shift.getShiftId())
                .shiftCode(formatShiftCode(shift.getShiftId()))
                .eventId(event != null ? event.getEventId() : null)
                .eventName(event != null ? event.getName() : "Sự kiện")
                .eventDescription(event != null ? event.getDescription() : null)
                .jobType(shift.getJobType())
                .description(shift.getDescription())
                .eventDate(event != null ? event.getEventDate() : null)
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .venueAddress(shift.getLocationAddress() != null ? shift.getLocationAddress()
                        : (event != null ? event.getVenueAddress() : "—"))
                .locationLat(shift.getLocationLat())
                .locationLng(shift.getLocationLng())
                .eoUserId(eoUser != null ? eoUser.getUserId() : null)
                .eoName(eoUser != null ? eoUser.getFullName() : "Nhà tổ chức")
                .eoPhone(eoUser != null ? eoUser.getPhone() : "—")
                .eoEmail(eoUser != null ? eoUser.getEmail() : "—")
                .companyName(eo != null ? eo.getCompanyName() : null)
                .requiredWorkers(shift.getRequiredWorkers())
                .confirmedWorkers(shift.getConfirmedWorkers())
                .remainingWorkers(shift.getRemainingWorkers())
                .hourlyWage(shift.getHourlyWage())
                .allowance(shift.getAllowance())
                .totalCost(shift.getTotalCost())
                .status(resolveRealtimeStatus(shift))
                .minExperienceLevel(shift.getMinExperienceLevel())
                .minReliabilityScore(shift.getMinReliabilityScore())
                .dressCode(shift.getDressCode())
                .checklist(shift.getChecklist())
                .paymentStatus(paymentStatus)
                .adminNote(shift.getAdminNote())
                .cancellationReason(shift.getCancellationReason())
                .cancelledBy(shift.getCancelledBy())
                .suspendedAt(shift.getSuspendedAt())
                .createdAt(shift.getCreatedAt())
                .workers(workers)
                .build();
    }

    /**
     * Hủy ca cưỡng chế (Admin Force Cancel Shift)
     */
    @Transactional
    public AdminShiftDetailDto cancelShift(UUID adminUserId, UUID shiftId, String reason) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc", "id", shiftId));

        if (shift.getStatus() == ShiftStatus.COMPLETED) {
            throw new BadRequestException("Không thể hủy ca làm việc đã hoàn thành.");
        }
        if (shift.getStatus() == ShiftStatus.CANCELLED) {
            throw new BadRequestException("Ca làm việc này đã được hủy trước đó.");
        }

        ShiftStatus oldStatus = shift.getStatus();
        Instant now = Instant.now();

        shift.setStatus(ShiftStatus.CANCELLED);
        shift.setCancelledBy(adminUserId);
        shift.setCancellationReason(reason);
        shift.setEmergency(false);
        shift = shiftRepository.save(shift);

        // Hủy toàn bộ Assignment đang hoạt động của ca
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByShift_ShiftId(shiftId);
        String eventName = shift.getEvent() != null ? shift.getEvent().getName() : "Sự kiện";

        for (ShiftAssignment sa : assignments) {
            if (sa.getStatus() != AssignmentStatus.COMPLETED && sa.getStatus() != AssignmentStatus.CANCELLED) {
                sa.setStatus(AssignmentStatus.CANCELLED);
                sa.setCancellationReason("Ban quản trị hủy ca cưỡng chế: " + reason);
                sa.setCancelledBy(adminUserId);
                sa.setCancelledAt(now);
                sa.setCancellationType("ADMIN_FORCE_CANCEL");
                shiftAssignmentRepository.save(sa);

                // Thông báo tới từng Worker
                if (sa.getWorker() != null && sa.getWorker().getUser() != null) {
                    try {
                        notificationService.createAndSend(
                                sa.getWorker().getUser().getUserId(),
                                "Ca làm việc đã bị hủy",
                                "Ca làm việc \"" + shift.getJobType() + "\" (" + eventName + ") đã bị Ban quản trị hủy. Lịch làm việc của bạn đã được giải phóng.",
                                NotificationType.ASSIGNMENT,
                                "SHIFT",
                                shiftId,
                                Map.of("shiftId", shiftId.toString(), "reason", reason)
                        );
                    } catch (Exception e) {
                        log.warn("Không thể gửi thông báo hủy ca tới Worker {}: {}", sa.getWorker().getWorkerId(), e.getMessage());
                    }
                }
            }
        }

        // Thông báo tới EO của ca
        Event event = shift.getEvent();
        if (event != null && event.getEventOrganizer() != null && event.getEventOrganizer().getUser() != null) {
            UUID eoUserId = event.getEventOrganizer().getUser().getUserId();
            try {
                notificationService.createAndSend(
                        eoUserId,
                        "Ca làm việc bị hủy cưỡng chế bởi Quản trị viên",
                        "Ca \"" + shift.getJobType() + "\" thuộc sự kiện \"" + eventName + "\" đã bị Ban quản trị hủy cưỡng chế. Lý do: " + reason,
                        NotificationType.SYSTEM,
                        "SHIFT",
                        shiftId,
                        Map.of("shiftId", shiftId.toString(), "reason", reason)
                );
            } catch (Exception e) {
                log.warn("Không thể gửi thông báo hủy ca tới EO {}: {}", eoUserId, e.getMessage());
            }
        }

        recordAuditLog(adminUserId, shiftId, "ADMIN_FORCE_CANCEL_SHIFT",
                Map.of("status", oldStatus.name()),
                Map.of("status", ShiftStatus.CANCELLED.name(), "reason", reason));

        log.info("Admin {} đã hủy cưỡng chế ca làm việc {}. Lý do: {}", adminUserId, shiftId, reason);
        return getShiftDetail(shiftId);
    }

    /**
     * Tạm đình chỉ ca làm việc (Admin Suspend Shift)
     */
    @Transactional
    public AdminShiftDetailDto suspendShift(UUID adminUserId, UUID shiftId, String reason) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc", "id", shiftId));

        if (shift.getStatus() == ShiftStatus.COMPLETED || shift.getStatus() == ShiftStatus.CANCELLED) {
            throw new BadRequestException("Chỉ có thể tạm đình chỉ ca đang hoạt động hoặc đang tuyển.");
        }
        if (shift.getStatus() == ShiftStatus.SUSPENDED) {
            throw new BadRequestException("Ca làm việc này đang trong trạng thái tạm đình chỉ.");
        }

        ShiftStatus oldStatus = shift.getStatus();
        shift.setStatus(ShiftStatus.SUSPENDED);
        shift.setAdminNote(reason);
        shift.setSuspendedAt(Instant.now());
        shift.setEmergency(false);
        shift = shiftRepository.save(shift);

        Event event = shift.getEvent();
        String eventName = event != null ? event.getName() : "Sự kiện";

        if (event != null && event.getEventOrganizer() != null && event.getEventOrganizer().getUser() != null) {
            UUID eoUserId = event.getEventOrganizer().getUser().getUserId();
            try {
                notificationService.createAndSend(
                        eoUserId,
                        "Ca làm việc bị tạm đình chỉ",
                        "Ca \"" + shift.getJobType() + "\" thuộc sự kiện \"" + eventName + "\" đã bị Ban quản trị tạm đình chỉ để rà soát. Lý do: " + reason,
                        NotificationType.SYSTEM,
                        "SHIFT",
                        shiftId,
                        Map.of("shiftId", shiftId.toString(), "reason", reason)
                );
            } catch (Exception e) {
                log.warn("Không thể gửi thông báo tạm đình chỉ ca tới EO {}: {}", eoUserId, e.getMessage());
            }
        }

        recordAuditLog(adminUserId, shiftId, "ADMIN_SUSPEND_SHIFT",
                Map.of("status", oldStatus.name()),
                Map.of("status", ShiftStatus.SUSPENDED.name(), "reason", reason));

        log.info("Admin {} đã tạm đình chỉ ca làm việc {}. Lý do: {}", adminUserId, shiftId, reason);
        return getShiftDetail(shiftId);
    }

    /**
     * Gửi cảnh báo/nhắc nhở trực tiếp tới EO của ca (Admin Warn EO)
     */
    @Transactional
    public AdminShiftDetailDto warnEo(UUID adminUserId, UUID shiftId, String reason) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc", "id", shiftId));

        Event event = shift.getEvent();
        if (event == null || event.getEventOrganizer() == null || event.getEventOrganizer().getUser() == null) {
            throw new BadRequestException("Không tìm thấy thông tin Nhà tổ chức sự kiện cho ca làm việc này.");
        }

        shift.setAdminNote(reason);
        shift = shiftRepository.save(shift);

        UUID eoUserId = event.getEventOrganizer().getUser().getUserId();
        String eventName = event.getName();

        try {
            notificationService.createAndSend(
                    eoUserId,
                    "Cảnh báo từ Quản trị viên về ca làm việc",
                    "Ban quản trị gửi cảnh báo về ca \"" + shift.getJobType() + "\" (" + eventName + "): " + reason,
                    NotificationType.SYSTEM,
                    "SHIFT",
                    shiftId,
                    Map.of("shiftId", shiftId.toString(), "warning", reason)
            );
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo cảnh báo tới EO {}: {}", eoUserId, e.getMessage());
        }

        recordAuditLog(adminUserId, shiftId, "ADMIN_WARN_EO",
                Collections.emptyMap(),
                Map.of("warning", reason));

        log.info("Admin {} đã gửi cảnh báo tới EO {} về ca {}. Nội dung: {}", adminUserId, eoUserId, shiftId, reason);
        return getShiftDetail(shiftId);
    }

    private void recordAuditLog(UUID adminUserId, UUID shiftId, String action, Map<String, Object> oldVal, Map<String, Object> newVal) {
        try {
            String oldValJson = oldVal != null && !oldVal.isEmpty() ? objectMapper.writeValueAsString(oldVal) : null;
            String newValJson = newVal != null && !newVal.isEmpty() ? objectMapper.writeValueAsString(newVal) : null;

            AuditLog logEntry = AuditLog.builder()
                    .userId(adminUserId)
                    .action(action)
                    .entityType("SHIFT")
                    .entityId(shiftId)
                    .oldValues(oldValJson)
                    .newValues(newValJson)
                    .build();

            auditLogService.logAction(logEntry);
        } catch (Exception e) {
            log.error("Không thể ghi AuditLog cho ca làm việc: {}", shiftId, e);
        }
    }

    private AdminShiftSummaryDto toSummaryDto(Shift shift) {
        Event event = shift.getEvent();
        EventOrganizer eo = event != null ? event.getEventOrganizer() : null;
        User eoUser = eo != null ? eo.getUser() : null;

        return AdminShiftSummaryDto.builder()
                .shiftId(shift.getShiftId())
                .shiftCode(formatShiftCode(shift.getShiftId()))
                .eventId(event != null ? event.getEventId() : null)
                .eventName(event != null ? event.getName() : "Sự kiện")
                .jobType(shift.getJobType())
                .eventDate(event != null ? event.getEventDate() : null)
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .venueAddress(shift.getLocationAddress() != null ? shift.getLocationAddress()
                        : (event != null ? event.getVenueAddress() : "—"))
                .eoUserId(eoUser != null ? eoUser.getUserId() : null)
                .eoName(eoUser != null ? eoUser.getFullName() : "Nhà tổ chức")
                .eoPhone(eoUser != null ? eoUser.getPhone() : "—")
                .eoEmail(eoUser != null ? eoUser.getEmail() : "—")
                .companyName(eo != null ? eo.getCompanyName() : null)
                .requiredWorkers(shift.getRequiredWorkers())
                .confirmedWorkers(shift.getConfirmedWorkers())
                .remainingWorkers(shift.getRemainingWorkers())
                .hourlyWage(shift.getHourlyWage())
                .status(resolveRealtimeStatus(shift))
                .createdAt(shift.getCreatedAt())
                .build();
    }

    private ShiftStatus resolveRealtimeStatus(Shift s) {
        if (s == null) return ShiftStatus.ACTIVE;
        if (s.getStatus() == ShiftStatus.CANCELLED || s.getStatus() == ShiftStatus.SUSPENDED || s.getStatus() == ShiftStatus.DRAFT) {
            return s.getStatus();
        }
        Instant now = Instant.now();
        if (s.getEndTime() != null && now.isAfter(s.getEndTime())) {
            return ShiftStatus.COMPLETED;
        }
        if (s.getStartTime() != null && !now.isBefore(s.getStartTime())) {
            return ShiftStatus.IN_PROGRESS;
        }
        return s.getStatus() != null ? s.getStatus() : ShiftStatus.ACTIVE;
    }

    private String formatShiftCode(UUID shiftId) {
        if (shiftId == null) return "SH-0000";
        String hex = shiftId.toString().replace("-", "").toUpperCase();
        return "SH-" + hex.substring(0, Math.min(6, hex.length()));
    }
}
