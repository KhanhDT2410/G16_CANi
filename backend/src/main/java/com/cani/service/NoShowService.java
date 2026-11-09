package com.cani.service;

import com.cani.dto.admin.noshow.*;
import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoShowService {

    public static final int NO_SHOW_PENALTY_POINTS = 25;
    public static final int LOCK_THRESHOLD_RS = 20;
    public static final int NO_SHOW_GRACE_PERIOD_MINUTES = 30;

    private final NoShowRecordRepository noShowRecordRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final WorkerRepository workerRepository;
    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final ReliabilityLogRepository reliabilityLogRepository;
    private final RsAppealRepository rsAppealRepository;
    private final MatchingDispatchService matchingDispatchService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    /**
     * Đánh dấu một ca làm việc vi phạm No-show và thực hiện toàn bộ quy trình chế tài & điều phối thay thế.
     */
    @Transactional
    public NoShowResponseDto markNoShow(UUID assignmentId, NoShowDetectedBy detectedBy, UUID actorUserId, String notes) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Phân công ca làm việc", "id", assignmentId));

        if (noShowRecordRepository.existsByShiftAssignment_AssignmentId(assignmentId)) {
            throw new BadRequestException("Ca làm việc này đã được ghi nhận No-show trước đó");
        }

        if (assignment.getStatus() == AssignmentStatus.COMPLETED || assignment.getStatus() == AssignmentStatus.CANCELLED) {
            throw new BadRequestException("Không thể đánh dấu No-show cho ca làm việc đã hoàn thành hoặc đã bị hủy");
        }

        // Kiểm tra log điểm danh
        Optional<AttendanceLog> attOpt = attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId);
        if (attOpt.isPresent() && attOpt.get().getCheckinTime() != null) {
            throw new BadRequestException("Nhân sự đã thực hiện quét mã QR điểm danh, không thể đánh dấu No-show");
        }

        AttendanceLog attendanceLog = attOpt.orElseGet(() -> AttendanceLog.builder()
                .shiftAssignment(assignment)
                .worker(assignment.getWorker())
                .build());
        attendanceLog.setAttendanceStatus(AttendanceStatus.NO_SHOW);
        attendanceLogRepository.save(attendanceLog);

        // Cập nhật trạng thái assignment
        assignment.setStatus(AssignmentStatus.NO_SHOW);
        shiftAssignmentRepository.save(assignment);

        // Mở lại vị trí còn thiếu cho Shift
        Shift shift = assignment.getShift();
        if (shift.getRemainingWorkers() != null) {
            shift.setRemainingWorkers(shift.getRemainingWorkers() + 1);
            shiftRepository.save(shift);
        }

        // Chế tài điểm uy tín (RS) của Worker (-25 điểm)
        Worker worker = assignment.getWorker();
        int scoreBefore = worker.getReliabilityScore() != null ? worker.getReliabilityScore() : 100;
        int scoreAfter = Math.max(0, scoreBefore - NO_SHOW_PENALTY_POINTS);
        worker.setReliabilityScore(scoreAfter);
        workerRepository.save(worker);

        ReliabilityLog penaltyLog = ReliabilityLog.builder()
                .worker(worker)
                .changeAmount(-NO_SHOW_PENALTY_POINTS)
                .scoreBefore(scoreBefore)
                .scoreAfter(scoreAfter)
                .reason("Vắng mặt không báo trước (No-show) ca trực: " + (shift.getJobType() != null ? shift.getJobType() : "Nhân sự sự kiện"))
                .referenceType("NO_SHOW")
                .referenceId(assignmentId)
                .build();
        penaltyLog = reliabilityLogRepository.save(penaltyLog);

        // Kiểm tra tự động khóa tài khoản nếu RS < 20 (UC-95)
        boolean isAccountLocked = false;
        User workerUser = worker.getUser();
        if (scoreAfter < LOCK_THRESHOLD_RS) {
            if (workerUser != null && workerUser.getStatus() != UserStatus.LOCKED) {
                workerUser.setStatus(UserStatus.LOCKED);
                userRepository.save(workerUser);
                isAccountLocked = true;
                log.warn("Tài khoản Worker {} tự động bị KHÓA do điểm RS giảm dưới 20 (hiện tại: {})",
                        workerUser.getEmail(), scoreAfter);
            }
        }

        // Lưu bản ghi No-show
        NoShowRecord record = NoShowRecord.builder()
                .shiftAssignment(assignment)
                .worker(worker)
                .shift(shift)
                .event(shift.getEvent())
                .detectedBy(detectedBy != null ? detectedBy : NoShowDetectedBy.ADMIN)
                .detectedAt(Instant.now())
                .reliabilityLog(penaltyLog)
                .penaltyAmount(NO_SHOW_PENALTY_POINTS)
                .isAccountLocked(isAccountLocked)
                .emergencyDispatched(false)
                .notes(notes)
                .build();

        // Kích hoạt Emergency Dispatch tìm nhân sự thay thế
        boolean emergencyDispatched = false;
        try {
            int dispatched = matchingDispatchService.dispatchForShift(shift.getShiftId());
            emergencyDispatched = (dispatched > 0);
            log.info("Emergency Dispatch kích hoạt cho ca {}: gửi đề xuất tới {} ứng viên thay thế",
                    shift.getShiftId(), dispatched);
        } catch (Exception e) {
            log.error("Lỗi khi kích hoạt Emergency Dispatch cho ca {}: {}", shift.getShiftId(), e.getMessage());
        }
        record.setEmergencyDispatched(emergencyDispatched);
        NoShowRecord savedRecord = noShowRecordRepository.save(record);

        // Gửi thông báo đến Worker và Nhà tổ chức (EO)
        if (workerUser != null) {
            String wTitle = "Cảnh báo vi phạm: Đánh dấu vắng mặt (No-show)";
            String wBody = String.format("Bạn bị đánh dấu vắng mặt (No-show) cho ca '%s' và bị trừ %d điểm uy tín (RS hiện tại: %d). Bạn có 7 ngày để nộp kháng cáo nếu có lý do bất khả kháng.",
                    shift.getJobType() != null ? shift.getJobType() : "Sự kiện", NO_SHOW_PENALTY_POINTS, scoreAfter);
            notificationService.sendNotification(workerUser, wTitle, wBody, NotificationType.ASSIGNMENT, "NO_SHOW", assignmentId);
        }

        if (shift.getEvent() != null && shift.getEvent().getEventOrganizer() != null) {
            User eoUser = shift.getEvent().getEventOrganizer().getUser();
            if (eoUser != null) {
                String eoTitle = "Nhân sự vắng mặt không báo trước (No-show)";
                String eoBody = String.format("Nhân sự %s đã bị đánh dấu vắng mặt (No-show) cho ca '%s'. Hệ thống đã tự động kích hoạt điều phối nhân sự thay thế.",
                        workerUser != null ? workerUser.getFullName() : "Worker",
                        shift.getJobType() != null ? shift.getJobType() : "Sự kiện");
                notificationService.sendNotification(eoUser, eoTitle, eoBody, NotificationType.ASSIGNMENT, "NO_SHOW", assignmentId);
            }
        }

        // Ghi Audit Log
        auditLogService.logAction(AuditLog.builder()
                .userId(actorUserId)
                .action("MARK_NO_SHOW")
                .entityType("SHIFT_ASSIGNMENT")
                .entityId(assignmentId)
                .newValues(String.format("{\"worker\":\"%s\",\"penalty\":%d,\"scoreBefore\":%d,\"scoreAfter\":%d,\"locked\":%s}",
                        workerUser != null ? workerUser.getEmail() : worker.getWorkerId(),
                        NO_SHOW_PENALTY_POINTS, scoreBefore, scoreAfter, isAccountLocked))
                .build());

        return toResponseDto(savedRecord);
    }

    /**
     * Tiến trình quét tự động các ca vi phạm No-show (chạy định kỳ mỗi 10 phút).
     */
    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public NoShowScanResultDto scanAndProcessNoShows() {
        Instant thresholdTime = Instant.now().minus(Duration.ofMinutes(NO_SHOW_GRACE_PERIOD_MINUTES));
        List<ShiftAssignment> candidates = shiftAssignmentRepository.findPotentialNoShows(
                AssignmentStatus.CONFIRMED, thresholdTime
        );

        int scanned = candidates.size();
        int marked = 0;
        int dispatched = 0;
        int locked = 0;

        for (ShiftAssignment sa : candidates) {
            Optional<AttendanceLog> attOpt = attendanceLogRepository.findByShiftAssignment_AssignmentId(sa.getAssignmentId());
            if (attOpt.isPresent() && attOpt.get().getCheckinTime() != null) {
                continue;
            }
            if (noShowRecordRepository.existsByShiftAssignment_AssignmentId(sa.getAssignmentId())) {
                continue;
            }

            try {
                NoShowResponseDto result = markNoShow(
                        sa.getAssignmentId(),
                        NoShowDetectedBy.SYSTEM_CRON,
                        null,
                        "Hệ thống tự động phát hiện vắng mặt sau 30 phút bắt đầu ca"
                );
                marked++;
                if (Boolean.TRUE.equals(result.getEmergencyDispatched())) dispatched++;
                if (Boolean.TRUE.equals(result.getIsAccountLocked())) locked++;
            } catch (Exception e) {
                log.error("Lỗi khi xử lý No-show tự động cho assignment {}: {}", sa.getAssignmentId(), e.getMessage());
            }
        }

        if (marked > 0) {
            auditLogService.logAction(AuditLog.builder()
                    .action("AUTO_NO_SHOW_SCAN")
                    .entityType("SYSTEM")
                    .newValues(String.format("{\"marked\":%d,\"dispatched\":%d,\"locked\":%d}",
                            marked, dispatched, locked))
                    .build());
        }

        return NoShowScanResultDto.builder()
                .totalCandidatesScanned(scanned)
                .totalMarkedNoShow(marked)
                .totalEmergencyDispatched(dispatched)
                .totalAccountsLocked(locked)
                .scannedAt(Instant.now())
                .message(String.format("Đã quét %d ca tiềm năng, phát hiện và xử lý %d ca No-show.", scanned, marked))
                .build();
    }

    /**
     * Lấy danh sách phân trang các ca No-show kèm bộ lọc.
     */
    @Transactional(readOnly = true)
    public Page<NoShowResponseDto> getNoShows(NoShowFilterRequestDto filter) {
        Pageable pageable = PageRequest.of(
                Math.max(0, filter.getPage()),
                Math.max(1, Math.min(filter.getSize(), 100)),
                Sort.by(Sort.Direction.DESC, "detectedAt")
        );

        Specification<NoShowRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getWorkerId() != null) {
                predicates.add(cb.equal(root.get("worker").get("workerId"), filter.getWorkerId()));
            }

            if (filter.getEventId() != null) {
                predicates.add(cb.equal(root.get("event").get("eventId"), filter.getEventId()));
            }

            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("detectedAt"), filter.getFromDate()));
            }

            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("detectedAt"), filter.getToDate()));
            }

            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String kw = "%" + filter.getKeyword().trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("worker").get("user").get("fullName")), kw);
                Predicate emailMatch = cb.like(cb.lower(root.get("worker").get("user").get("email")), kw);
                Predicate roleMatch = cb.like(cb.lower(root.get("shift").get("jobType")), kw);
                Predicate eventMatch = cb.like(cb.lower(root.get("event").get("name")), kw);
                predicates.add(cb.or(nameMatch, emailMatch, roleMatch, eventMatch));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };

        return noShowRecordRepository.findAll(spec, pageable).map(this::toResponseDto);
    }

    /**
     * Lấy chi tiết một bản ghi No-show theo ID.
     */
    @Transactional(readOnly = true)
    public NoShowResponseDto getNoShowDetail(UUID noShowId) {
        NoShowRecord record = noShowRecordRepository.findById(noShowId)
                .orElseThrow(() -> new ResourceNotFoundException("Bản ghi No-show", "id", noShowId));
        return toResponseDto(record);
    }

    /**
     * Lấy số liệu thống kê tổng hợp No-show.
     */
    @Transactional(readOnly = true)
    public NoShowStatsDto getNoShowStats() {
        long total = noShowRecordRepository.count();
        long locked = noShowRecordRepository.countByIsAccountLockedTrue();
        Long totalRs = noShowRecordRepository.sumTotalPenaltyPoints();

        Instant startOfDay = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant endOfDay = startOfDay.plus(Duration.ofDays(1));
        long today = noShowRecordRepository.countByDetectedAtBetween(startOfDay, endOfDay);

        Instant startOfMonth = Instant.now().minus(Duration.ofDays(30));
        long month = noShowRecordRepository.countByDetectedAtBetween(startOfMonth, Instant.now());

        return NoShowStatsDto.builder()
                .totalNoShows(total)
                .noShowsToday(today)
                .noShowsThisMonth(month)
                .totalRsDeducted(totalRs != null ? totalRs : 0L)
                .totalAccountsLocked(locked)
                .build();
    }

    private NoShowResponseDto toResponseDto(NoShowRecord record) {
        Worker worker = record.getWorker();
        User user = worker != null ? worker.getUser() : null;
        Shift shift = record.getShift();
        Event event = record.getEvent();
        ReliabilityLog relLog = record.getReliabilityLog();

        UUID appealId = null;
        String appealStatus = null;
        if (relLog != null) {
            Optional<RsAppeal> appealOpt = rsAppealRepository.findByReliabilityLog_LogId(relLog.getLogId());
            if (appealOpt.isPresent()) {
                appealId = appealOpt.get().getId();
                appealStatus = appealOpt.get().getStatus().name();
            }
        }

        return NoShowResponseDto.builder()
                .id(record.getId())
                .assignmentId(record.getShiftAssignment() != null ? record.getShiftAssignment().getAssignmentId() : null)
                .workerId(worker != null ? worker.getWorkerId() : null)
                .workerUserId(user != null ? user.getUserId() : null)
                .workerName(user != null ? user.getFullName() : "N/A")
                .workerEmail(user != null ? user.getEmail() : "N/A")
                .workerPhone(user != null ? user.getPhone() : null)
                .workerAvatar(user != null ? user.getAvatarUrl() : null)
                .shiftId(shift != null ? shift.getShiftId() : null)
                .shiftRole(shift != null ? shift.getJobType() : "N/A")
                .eventId(event != null ? event.getEventId() : null)
                .eventName(event != null ? event.getName() : "N/A")
                .shiftStartTime(shift != null ? shift.getStartTime() : null)
                .shiftEndTime(shift != null ? shift.getEndTime() : null)
                .detectedBy(record.getDetectedBy())
                .detectedAt(record.getDetectedAt())
                .penaltyAmount(record.getPenaltyAmount())
                .reliabilityScoreBefore(relLog != null ? relLog.getScoreBefore() : null)
                .reliabilityScoreAfter(relLog != null ? relLog.getScoreAfter() : null)
                .isAccountLocked(record.getIsAccountLocked())
                .emergencyDispatched(record.getEmergencyDispatched())
                .notes(record.getNotes())
                .appealId(appealId)
                .appealStatus(appealStatus)
                .createdAt(record.getCreatedAt())
                .build();
    }
}
