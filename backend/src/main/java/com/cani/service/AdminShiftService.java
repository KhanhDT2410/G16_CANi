package com.cani.service;

import com.cani.dto.admin.shift.AdminShiftDetailDto;
import com.cani.dto.admin.shift.AdminShiftSummaryDto;
import com.cani.dto.admin.shift.AdminShiftWorkerDto;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.PaymentStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.AttendanceLogRepository;
import com.cani.repository.PaymentRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Lấy danh sách ca làm việc cho Admin & CSKH (hỗ trợ lọc từ khóa và trạng thái, có phân trang).
     */
    public Page<AdminShiftSummaryDto> getShifts(String query, ShiftStatus status, Pageable pageable) {
        List<Shift> allShifts = shiftRepository.findAll();

        String q = query != null ? query.trim().toLowerCase() : "";

        List<Shift> filtered = allShifts.stream()
                .filter(s -> {
                    if (status != null && s.getStatus() != status) {
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
                .status(shift.getStatus())
                .minExperienceLevel(shift.getMinExperienceLevel())
                .minReliabilityScore(shift.getMinReliabilityScore())
                .dressCode(shift.getDressCode())
                .checklist(shift.getChecklist())
                .paymentStatus(paymentStatus)
                .createdAt(shift.getCreatedAt())
                .workers(workers)
                .build();
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
                .status(shift.getStatus())
                .createdAt(shift.getCreatedAt())
                .build();
    }

    private String formatShiftCode(UUID shiftId) {
        if (shiftId == null) return "SH-0000";
        String hex = shiftId.toString().replace("-", "").toUpperCase();
        return "SH-" + hex.substring(0, Math.min(6, hex.length()));
    }
}
