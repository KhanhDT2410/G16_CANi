package com.cani.service;

import com.cani.dto.admin.appeal.ReviewRsAppealRequestDto;
import com.cani.dto.admin.appeal.RsAppealResponseDto;
import com.cani.dto.worker.appeal.SubmitRsAppealRequestDto;
import com.cani.entity.*;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.RsAppealStatus;
import com.cani.entity.enums.UserRole;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.ReliabilityLogRepository;
import com.cani.repository.RsAppealRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service xử lý khiếu nại / kháng cáo điểm phạt uy tín RS của Worker (SRS FE-10, UC-94).
 * Hỗ trợ nộp bằng chứng bất khả kháng (force majeure) trong 7 ngày, thẩm định duyệt khôi phục RS
 * và tự động ghi nhật ký kiểm toán (AuditLog) + nhật ký điểm (ReliabilityLog).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RsAppealService {

    private static final int RS_CEILING = 100;
    private static final long APPEAL_WINDOW_DAYS = 7L;

    private final RsAppealRepository rsAppealRepository;
    private final ReliabilityLogRepository reliabilityLogRepository;
    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    /**
     * Worker nộp đơn kháng cáo cho một lần bị trừ điểm uy tín.
     */
    @Transactional
    public RsAppealResponseDto submitAppeal(UUID userId, SubmitRsAppealRequestDto request) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Worker của tài khoản"));

        ReliabilityLog logEntry = reliabilityLogRepository.findById(request.getReliabilityLogId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản ghi trừ điểm uy tín"));

        // 1. Kiểm tra quyền sở hữu
        if (!logEntry.getWorker().getWorkerId().equals(worker.getWorkerId())) {
            throw new AppException("Bạn không có quyền kháng cáo bản ghi điểm uy tín này", HttpStatus.FORBIDDEN);
        }

        // 2. Kiểm tra bản ghi có phải là điểm phạt hay không
        if (logEntry.getChangeAmount() >= 0) {
            throw new BadRequestException("Chỉ có thể nộp kháng cáo cho các bản ghi bị trừ điểm uy tín");
        }

        // 3. Kiểm tra thời hạn 7 ngày
        Instant deadline = logEntry.getCreatedAt().plus(Duration.ofDays(APPEAL_WINDOW_DAYS));
        if (Instant.now().isAfter(deadline)) {
            throw new BadRequestException("Đã quá thời hạn 7 ngày để nộp kháng cáo cho lần trừ điểm này");
        }

        // 4. Kiểm tra chống lạm dụng: Mỗi bản ghi phạt chỉ được kháng cáo duy nhất 1 lần
        if (rsAppealRepository.existsByReliabilityLog_LogId(logEntry.getLogId())) {
            throw new BadRequestException("Bản ghi trừ điểm này đã được nộp kháng cáo trước đó");
        }

        int penaltyAmount = Math.abs(logEntry.getChangeAmount());

        RsAppeal appeal = RsAppeal.builder()
                .worker(worker)
                .reliabilityLog(logEntry)
                .penaltyAmount(penaltyAmount)
                .reasonCode(request.getReasonCode())
                .reasonDetail(request.getReasonDetail())
                .evidence(request.getEvidence())
                .status(RsAppealStatus.SUBMITTED)
                .build();

        RsAppeal saved = rsAppealRepository.save(appeal);

        // Ghi AuditLog
        auditLogService.logAction(AuditLog.builder()
                .userId(userId)
                .action("SUBMIT_RS_APPEAL")
                .entityType("RS_APPEAL")
                .entityId(saved.getId())
                .newValues(String.format("{\"reasonCode\":\"%s\",\"penaltyAmount\":%d}", request.getReasonCode(), penaltyAmount))
                .build());

        // Thông báo cho Worker
        notificationService.sendNotification(
                worker.getUser(),
                "Nộp kháng cáo điểm uy tín thành công",
                String.format("Đơn kháng cáo cho lần trừ -%d điểm RS của bạn đã được ghi nhận và đang chờ xử lý.", penaltyAmount),
                NotificationType.RS_APPEAL,
                "RS_APPEAL",
                saved.getId()
        );

        // Thông báo cho ban quản trị Customer Care
        notificationService.notifyRole(
                UserRole.CUSTOMER_CARE,
                "Có đơn kháng cáo điểm uy tín mới",
                String.format("Worker %s vừa nộp kháng cáo cho lần trừ %d điểm RS.", worker.getUser().getFullName(), penaltyAmount),
                NotificationType.RS_APPEAL,
                "RS_APPEAL",
                saved.getId()
        );

        log.info("Worker {} (userId={}) nộp kháng cáo RS appealId={} cho logId={}",
                worker.getWorkerId(), userId, saved.getId(), logEntry.getLogId());

        return mapToDto(saved);
    }

    /**
     * Worker lấy danh sách các đơn kháng cáo của chính mình.
     */
    public List<RsAppealResponseDto> getWorkerAppeals(UUID userId) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Worker của tài khoản"));

        return rsAppealRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Admin / Customer Care lấy danh sách hàng đợi các đơn kháng cáo (kèm bộ lọc trạng thái và phân trang).
     */
    public Page<RsAppealResponseDto> getAdminAppeals(RsAppealStatus status, Pageable pageable) {
        Page<RsAppeal> page = (status != null)
                ? rsAppealRepository.findByStatus(status, pageable)
                : rsAppealRepository.findAllByOrderByCreatedAtDesc(pageable);

        return page.map(this::mapToDto);
    }

    /**
     * Lấy chi tiết một đơn kháng cáo theo ID.
     */
    public RsAppealResponseDto getAppealById(UUID id) {
        RsAppeal appeal = rsAppealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn kháng cáo điểm uy tín"));
        return mapToDto(appeal);
    }

    /**
     * Admin / Customer Care ra quyết định duyệt hoặc từ chối kháng cáo.
     * Quyết định là cuối cùng (final) và không thể đảo ngược lại trực tiếp.
     */
    @Transactional
    public RsAppealResponseDto reviewAppeal(UUID reviewerUserId, UUID appealId, ReviewRsAppealRequestDto request) {
        RsAppeal appeal = rsAppealRepository.findById(appealId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn kháng cáo điểm uy tín"));

        if (appeal.getStatus() == RsAppealStatus.APPROVED || appeal.getStatus() == RsAppealStatus.REJECTED) {
            throw new BadRequestException("Đơn kháng cáo này đã có quyết định cuối cùng, không thể sửa đổi");
        }

        Instant now = Instant.now();
        appeal.setReviewerId(reviewerUserId);
        appeal.setDecisionNote(request.getDecisionNote());
        appeal.setDecidedAt(now);

        Worker worker = appeal.getWorker();

        if (Boolean.TRUE.equals(request.getApproved())) {
            appeal.setStatus(RsAppealStatus.APPROVED);

            // Khôi phục điểm RS cho Worker
            int before = (worker.getReliabilityScore() != null) ? worker.getReliabilityScore() : RS_CEILING;
            int restoreAmount = appeal.getPenaltyAmount();
            int after = Math.min(RS_CEILING, before + restoreAmount);

            worker.setReliabilityScore(after);
            workerRepository.save(worker);

            // Ghi nhật ký điểm mới (ReliabilityLog)
            ReliabilityLog restoreLog = ReliabilityLog.builder()
                    .worker(worker)
                    .changeAmount(restoreAmount)
                    .reason("Khôi phục điểm sau kháng cáo thành công: " + request.getDecisionNote())
                    .referenceType("RS_APPEAL")
                    .referenceId(appeal.getId())
                    .scoreBefore(before)
                    .scoreAfter(after)
                    .build();
            reliabilityLogRepository.save(restoreLog);

            // Ghi AuditLog
            auditLogService.logAction(AuditLog.builder()
                    .userId(reviewerUserId)
                    .action("APPROVE_RS_APPEAL")
                    .entityType("RS_APPEAL")
                    .entityId(appeal.getId())
                    .oldValues(String.format("{\"score\":%d,\"status\":\"%s\"}", before, RsAppealStatus.SUBMITTED))
                    .newValues(String.format("{\"score\":%d,\"status\":\"%s\",\"restored\":%d}", after, RsAppealStatus.APPROVED, restoreAmount))
                    .build());

            // Thông báo kết quả cho Worker
            notificationService.sendNotification(
                    worker.getUser(),
                    "Kháng cáo điểm uy tín đã được chấp thuận",
                    String.format("Kháng cáo của bạn đã được phê duyệt. Bạn đã được khôi phục %d điểm RS. Điểm hiện tại: %d.", restoreAmount, after),
                    NotificationType.RS_APPEAL,
                    "RS_APPEAL",
                    appeal.getId()
            );

            log.info("Admin/CC {} đã DUYỆT kháng cáo appealId={}, khôi phục {} RS cho workerId={}",
                    reviewerUserId, appealId, restoreAmount, worker.getWorkerId());

        } else {
            appeal.setStatus(RsAppealStatus.REJECTED);

            // Ghi AuditLog
            auditLogService.logAction(AuditLog.builder()
                    .userId(reviewerUserId)
                    .action("REJECT_RS_APPEAL")
                    .entityType("RS_APPEAL")
                    .entityId(appeal.getId())
                    .newValues(String.format("{\"status\":\"%s\",\"note\":\"%s\"}", RsAppealStatus.REJECTED, request.getDecisionNote()))
                    .build());

            // Thông báo kết quả cho Worker
            notificationService.sendNotification(
                    worker.getUser(),
                    "Kháng cáo điểm uy tín bị từ chối",
                    String.format("Đơn kháng cáo cho lần trừ %d điểm RS đã bị từ chối. Lý do: %s.", appeal.getPenaltyAmount(), request.getDecisionNote()),
                    NotificationType.RS_APPEAL,
                    "RS_APPEAL",
                    appeal.getId()
            );

            log.info("Admin/CC {} đã TỪ CHỐI kháng cáo appealId={} của workerId={}",
                    reviewerUserId, appealId, worker.getWorkerId());
        }

        RsAppeal saved = rsAppealRepository.save(appeal);
        return mapToDto(saved);
    }

    // =========================================================================
    // MAPPER
    // =========================================================================

    private RsAppealResponseDto mapToDto(RsAppeal appeal) {
        Worker worker = appeal.getWorker();
        ReliabilityLog logEntry = appeal.getReliabilityLog();

        String workerName = (worker != null && worker.getUser() != null) ? worker.getUser().getFullName() : null;
        String workerEmail = (worker != null && worker.getUser() != null) ? worker.getUser().getEmail() : null;
        Integer currentRs = (worker != null) ? worker.getReliabilityScore() : null;

        String reviewerName = null;
        if (appeal.getReviewerId() != null) {
            reviewerName = userRepository.findById(appeal.getReviewerId())
                    .map(User::getFullName)
                    .orElse(null);
        }

        return RsAppealResponseDto.builder()
                .id(appeal.getId())
                .workerId(worker != null ? worker.getWorkerId() : null)
                .workerName(workerName)
                .workerEmail(workerEmail)
                .currentReliabilityScore(currentRs)
                .reliabilityLogId(logEntry != null ? logEntry.getLogId() : null)
                .penaltyAmount(appeal.getPenaltyAmount())
                .deductedAt(logEntry != null ? logEntry.getCreatedAt() : null)
                .deductionReason(logEntry != null ? logEntry.getReason() : null)
                .reasonCode(appeal.getReasonCode())
                .reasonDetail(appeal.getReasonDetail())
                .evidence(appeal.getEvidence())
                .status(appeal.getStatus())
                .reviewerId(appeal.getReviewerId())
                .reviewerName(reviewerName)
                .decisionNote(appeal.getDecisionNote())
                .decidedAt(appeal.getDecidedAt())
                .createdAt(appeal.getCreatedAt())
                .updatedAt(appeal.getUpdatedAt())
                .build();
    }
}
