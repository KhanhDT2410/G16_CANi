package com.cani.service;

import com.cani.dto.admin.user.*;
import com.cani.dto.response.PageResponse;
import com.cani.entity.AuditLog;
import com.cani.entity.EventOrganizer;
import com.cani.entity.ReliabilityLog;
import com.cani.entity.SkillVerificationRequest;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.AuditLogRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.ReliabilityLogRepository;
import com.cani.repository.SkillVerificationRequestRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import com.cani.repository.WorkerSkillRepository;
import com.cani.repository.specification.UserSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

        private final UserRepository userRepository;
        private final WorkerRepository workerRepository;
        private final EventOrganizerRepository eventOrganizerRepository;
        private final SkillVerificationRequestRepository skillVerificationRequestRepository;
        private final ReliabilityLogRepository reliabilityLogRepository;
        private final AuditLogRepository auditLogRepository;
        private final AuditLogService auditLogService;
        private final ObjectMapper objectMapper;

        @Autowired(required = false)
        private WorkerSkillRepository workerSkillRepository;

        /**
         * Lấy danh sách người dùng có phân trang và bộ lọc linh hoạt (search, role,
         * status).
         */
        public PageResponse<UserSummaryResponseDto> getUsers(UserFilterRequest filter, Pageable pageable) {
                Pageable sanitizedPageable = sanitizePageable(pageable);
                Page<User> userPage = userRepository.findAll(UserSpecification.filterUsers(filter), sanitizedPageable);
                List<User> users = userPage.getContent();

                if (users.isEmpty()) {
                        return PageResponse.from(userPage.map(this::toSummaryDto));
                }

                List<UUID> workerUserIds = users.stream()
                                .filter(u -> u.getRole() == UserRole.WORKER)
                                .map(User::getUserId)
                                .toList();

                List<UUID> eoUserIds = users.stream()
                                .filter(u -> u.getRole() == UserRole.EVENT_ORGANIZER)
                                .map(User::getUserId)
                                .toList();

                Map<UUID, Worker> workerByUserMap = workerUserIds.isEmpty() ? Map.of()
                                : workerRepository.findByUser_UserIdIn(workerUserIds).stream()
                                                .collect(Collectors.toMap(w -> w.getUser().getUserId(), w -> w,
                                                                (existing, replacement) -> existing));

                List<UUID> workerIds = workerByUserMap.values().stream()
                                .map(Worker::getWorkerId)
                                .toList();

                Map<UUID, String> latestSkillByWorkerMap;
                if (workerIds.isEmpty()) {
                        latestSkillByWorkerMap = Map.of();
                } else {
                        List<SkillVerificationRequest> skillRequests = skillVerificationRequestRepository
                                        .findByWorker_WorkerIdIn(workerIds);
                        latestSkillByWorkerMap = skillRequests.stream()
                                        .collect(Collectors.groupingBy(
                                                        req -> req.getWorker().getWorkerId(),
                                                        Collectors.collectingAndThen(
                                                                        Collectors.maxBy(Comparator.comparing(
                                                                                        SkillVerificationRequest::getCreatedAt,
                                                                                        Comparator.nullsLast(Comparator
                                                                                                        .naturalOrder()))),
                                                                        optReq -> optReq
                                                                                        .map(req -> req.getStatus() != null
                                                                                                        ? req.getStatus()
                                                                                                                        .name()
                                                                                                        : "NOT_FOUND")
                                                                                        .orElse("NOT_FOUND"))));
                }

                Map<UUID, EventOrganizer> eoByUserMap = eoUserIds.isEmpty() ? Map.of()
                                : eventOrganizerRepository.findByUser_UserIdIn(eoUserIds).stream()
                                                .collect(Collectors.toMap(eo -> eo.getUser().getUserId(), eo -> eo,
                                                                (existing, replacement) -> existing));

                return PageResponse
                                .from(userPage.map(user -> toSummaryDto(user, workerByUserMap, eoByUserMap,
                                                latestSkillByWorkerMap)));
        }

        /**
         * Xem chi tiết thông tin tài khoản người dùng kèm hồ sơ Worker/EO tương ứng.
         */
        public UserDetailResponseDto getUserById(UUID userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));

                UserDetailResponseDto dto = toDetailDto(user);

                // Nạp thông tin hồ sơ chuyên biệt tùy theo role
                if (user.getRole() == UserRole.WORKER) {
                        workerRepository.findByUser_UserId(userId)
                                        .ifPresent(worker -> dto.setWorkerProfile(toWorkerProfileDto(worker)));
                } else if (user.getRole() == UserRole.EVENT_ORGANIZER) {
                        eventOrganizerRepository.findByUser_UserId(userId)
                                        .ifPresent(eo -> dto.setEoProfile(toEoProfileDto(eo)));
                }

                return dto;
        }

        /**
         * Cập nhật trạng thái người dùng (LOCKED, ACTIVE) kèm lý do và ghi nhận Audit
         * Log.
         */
        @Transactional
        public UserDetailResponseDto updateUserStatus(UUID targetUserId, UUID adminUserId,
                        UpdateUserStatusRequestDto request) {
                User user = userRepository.findById(targetUserId)
                                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", targetUserId));

                // Ngăn Admin tự khóa tài khoản của chính mình
                if (adminUserId != null && adminUserId.equals(targetUserId)) {
                        throw new BadRequestException(
                                        "Quản trị viên không thể tự thay đổi trạng thái tài khoản của chính mình");
                }

                UserStatus oldStatus = user.getStatus();
                UserStatus newStatus = request.getStatus();

                user.setStatus(newStatus);
                if ((newStatus == UserStatus.SUSPENDED || newStatus == UserStatus.LOCKED)
                                && request.getLockUntil() != null) {
                        user.setLockUntil(request.getLockUntil());
                } else if (newStatus == UserStatus.ACTIVE) {
                        user.setLockUntil(null);
                }
                userRepository.save(user);

                log.info("Admin {} cập nhật trạng thái user {} từ {} sang {}. LockUntil: {}. Lý do: {}",
                                adminUserId, targetUserId, oldStatus, newStatus, user.getLockUntil(),
                                request.getReason());

                // Ghi nhận Audit Log
                recordStatusChangeAuditLog(adminUserId, targetUserId, oldStatus, newStatus, user.getLockUntil(),
                                request.getReason());

                return getUserById(targetUserId);
        }

        /**
         * Admin điều chỉnh điểm uy tín RS của Worker theo mức tùy ý (delta in [-50,
         * 50]),
         * chận trong [0, 100], lưu ReliabilityLog (refType ADMIN_ADJUST) và ghi
         * AuditLog.
         */
        @Transactional
        public AdminAdjustReliabilityResponseDto adjustReliabilityScore(
                        UUID targetUserId, UUID adminUserId, AdminAdjustReliabilityRequestDto request) {
                Worker worker = workerRepository.findByUser_UserId(targetUserId)
                                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ nhân sự (Worker)", "userId",
                                                targetUserId));

                int before = worker.getReliabilityScore() != null ? worker.getReliabilityScore() : 100;
                int after = Math.clamp(before + request.getDelta(), 0, 100);

                worker.setReliabilityScore(after);
                workerRepository.save(worker);

                String refType = (request.getReferenceType() != null && !request.getReferenceType().isBlank())
                                ? request.getReferenceType()
                                : "ADMIN_ADJUST";

                ReliabilityLog logEntry = ReliabilityLog.builder()
                                .worker(worker)
                                .changeAmount(request.getDelta())
                                .reason(request.getReason())
                                .referenceType(refType)
                                .referenceId(request.getReferenceId())
                                .scoreBefore(before)
                                .scoreAfter(after)
                                .build();
                ReliabilityLog savedLog = reliabilityLogRepository.save(logEntry);

                log.info("Admin {} điều chỉnh RS cho worker {} (userId {}): before={}, delta={}, after={}. Lý do: {}",
                                adminUserId, worker.getWorkerId(), targetUserId, before, request.getDelta(), after,
                                request.getReason());

                try {
                        Map<String, Object> oldMap = Map.of("score", before);
                        Map<String, Object> newMap = new java.util.HashMap<>();
                        newMap.put("score", after);
                        newMap.put("delta", request.getDelta());
                        newMap.put("reason", request.getReason());
                        newMap.put("referenceType", refType);
                        if (request.getReferenceId() != null) {
                                newMap.put("referenceId", request.getReferenceId().toString());
                        }

                        AuditLog auditEntry = AuditLog.builder()
                                        .userId(adminUserId)
                                        .action("ADMIN_ADJUST_RS")
                                        .entityType("WORKER")
                                        .entityId(worker.getWorkerId())
                                        .oldValues(objectMapper.writeValueAsString(oldMap))
                                        .newValues(objectMapper.writeValueAsString(newMap))
                                        .build();

                        auditLogService.logAction(auditEntry);
                } catch (Exception e) {
                        log.error("Không thể ghi AuditLog khi điều chỉnh RS cho worker {}", worker.getWorkerId(), e);
                }

                return AdminAdjustReliabilityResponseDto.builder()
                                .logId(savedLog.getLogId())
                                .workerId(worker.getWorkerId())
                                .userId(targetUserId)
                                .scoreBefore(before)
                                .scoreAfter(after)
                                .delta(request.getDelta())
                                .reason(request.getReason())
                                .referenceType(refType)
                                .referenceId(request.getReferenceId())
                                .createdAt(savedLog.getCreatedAt())
                                .build();
        }

        /**
         * Lấy lịch sử vi phạm của người dùng trong N ngày (ReliabilityLog + AuditLog
         * liên quan tranh chấp/khiếu nại).
         */
        public UserViolationHistoryResponseDto getViolationHistory(UUID targetUserId, int days) {
                if (!userRepository.existsById(targetUserId)) {
                        throw new ResourceNotFoundException("Người dùng", "id", targetUserId);
                }

                int boundedDays = Math.max(1, Math.min(days, 365));
                java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofDays(boundedDays));

                // 1. Lấy ReliabilityLog nếu là Worker
                List<ReliabilityLog> reliabilityLogs = workerRepository.findByUser_UserId(targetUserId)
                                .map(worker -> reliabilityLogRepository.findViolationsByWorkerId(
                                                worker.getWorkerId(), List.of("NO_SHOW", "DISPUTE", "ADMIN_ADJUST"),
                                                since))
                                .orElse(List.of());

                // 2. Lấy AuditLog liên quan đến DISPUTE
                List<AuditLog> auditLogs = auditLogRepository.findViolationsByUserId(targetUserId, "DISPUTE", since);

                long noShowCount = reliabilityLogs.stream()
                                .filter(l -> "NO_SHOW".equalsIgnoreCase(l.getReferenceType()))
                                .count();

                long adminAdjustCount = reliabilityLogs.stream()
                                .filter(l -> "ADMIN_ADJUST".equalsIgnoreCase(l.getReferenceType()))
                                .count();

                long disputeCountFromRel = reliabilityLogs.stream()
                                .filter(l -> "DISPUTE".equalsIgnoreCase(l.getReferenceType()))
                                .count();

                java.util.Set<UUID> loggedDisputeIds = reliabilityLogs.stream()
                                .filter(l -> "DISPUTE".equalsIgnoreCase(l.getReferenceType())
                                                && l.getReferenceId() != null)
                                .map(ReliabilityLog::getReferenceId)
                                .collect(Collectors.toSet());

                long disputeCountFromAudit = auditLogs.stream()
                                .filter(a -> a.getEntityId() == null || !loggedDisputeIds.contains(a.getEntityId()))
                                .count();

                long totalDisputes = disputeCountFromRel + disputeCountFromAudit;
                long totalViolations = noShowCount + adminAdjustCount + totalDisputes;

                List<UserViolationHistoryResponseDto.ReliabilityViolationItemDto> relItems = reliabilityLogs.stream()
                                .map(l -> UserViolationHistoryResponseDto.ReliabilityViolationItemDto.builder()
                                                .logId(l.getLogId())
                                                .changeAmount(l.getChangeAmount())
                                                .reason(l.getReason())
                                                .referenceType(l.getReferenceType())
                                                .referenceId(l.getReferenceId())
                                                .scoreBefore(l.getScoreBefore())
                                                .scoreAfter(l.getScoreAfter())
                                                .createdAt(l.getCreatedAt())
                                                .build())
                                .toList();

                List<UserViolationHistoryResponseDto.AuditViolationItemDto> auditItems = auditLogs.stream()
                                .map(a -> UserViolationHistoryResponseDto.AuditViolationItemDto.builder()
                                                .auditId(a.getAuditId())
                                                .action(a.getAction())
                                                .entityType(a.getEntityType())
                                                .entityId(a.getEntityId())
                                                .details(a.getNewValues())
                                                .createdAt(a.getCreatedAt())
                                                .build())
                                .toList();

                return UserViolationHistoryResponseDto.builder()
                                .userId(targetUserId)
                                .days(boundedDays)
                                .totalViolations(totalViolations)
                                .noShowCount(noShowCount)
                                .disputeCount(totalDisputes)
                                .adminAdjustCount(adminAdjustCount)
                                .reliabilityLogs(relItems)
                                .auditLogs(auditItems)
                                .build();
        }

        private void recordStatusChangeAuditLog(UUID adminUserId, UUID targetUserId, UserStatus oldStatus,
                        UserStatus newStatus, java.time.Instant lockUntil, String reason) {
                try {
                        java.util.Map<String, Object> oldVal = new java.util.HashMap<>();
                        oldVal.put("status", oldStatus.name());

                        java.util.Map<String, Object> newVal = new java.util.HashMap<>();
                        newVal.put("status", newStatus.name());
                        newVal.put("reason", reason);
                        if (lockUntil != null) {
                                newVal.put("lockUntil", lockUntil.toString());
                        }

                        String oldValJson = objectMapper.writeValueAsString(oldVal);
                        String newValJson = objectMapper.writeValueAsString(newVal);

                        AuditLog logEntry = AuditLog.builder()
                                        .userId(adminUserId)
                                        .action("UPDATE_USER_STATUS")
                                        .entityType("USER")
                                        .entityId(targetUserId)
                                        .oldValues(oldValJson)
                                        .newValues(newValJson)
                                        .build();

                        auditLogService.logAction(logEntry);
                } catch (Exception e) {
                        log.error("Không thể chuẩn bị AuditLog khi cập nhật trạng thái user: {}", targetUserId, e);
                }
        }

        private Pageable sanitizePageable(Pageable pageable) {
                if (pageable.getSort().isSorted()) {
                        List<Sort.Order> orders = pageable.getSort().stream()
                                        .map(order -> "id".equalsIgnoreCase(order.getProperty())
                                                        ? new Sort.Order(order.getDirection(), "userId")
                                                        : order)
                                        .toList();
                        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
                }
                return pageable;
        }

        private UserSummaryResponseDto toSummaryDto(
                        User user,
                        Map<UUID, Worker> workerByUserMap,
                        Map<UUID, EventOrganizer> eoByUserMap,
                        Map<UUID, String> latestSkillByWorkerMap) {
                String kycStatus = null;
                String skillStatus = null;

                if (user.getRole() == UserRole.WORKER) {
                        Worker worker = workerByUserMap.get(user.getUserId());
                        kycStatus = (worker != null && worker.getKycStatus() != null) ? worker.getKycStatus().name()
                                        : null;
                        skillStatus = (worker != null)
                                        ? latestSkillByWorkerMap.getOrDefault(worker.getWorkerId(), "NOT_FOUND")
                                        : "NOT_FOUND";
                } else if (user.getRole() == UserRole.EVENT_ORGANIZER) {
                        EventOrganizer eo = eoByUserMap.get(user.getUserId());
                        kycStatus = (eo != null && eo.getVerificationStatus() != null)
                                        ? eo.getVerificationStatus().name()
                                        : null;
                        skillStatus = null;
                }

                return UserSummaryResponseDto.builder()
                                .id(user.getUserId())
                                .email(user.getEmail())
                                .phone(user.getPhone())
                                .fullName(user.getFullName())
                                .role(user.getRole())
                                .status(user.getStatus())
                                .avatarUrl(user.getAvatarUrl())
                                .emailVerified(user.getEmailVerified())
                                .kycStatus(kycStatus)
                                .skillStatus(skillStatus)
                                .lastLoginAt(user.getLastLoginAt())
                                .lockUntil(user.getLockUntil())
                                .createdAt(user.getCreatedAt())
                                .updatedAt(user.getUpdatedAt())
                                .build();
        }

        private UserSummaryResponseDto toSummaryDto(User user) {
                return toSummaryDto(user, Map.of(), Map.of(), Map.of());
        }

        private UserDetailResponseDto toDetailDto(User user) {
                return UserDetailResponseDto.builder()
                                .id(user.getUserId())
                                .email(user.getEmail())
                                .phone(user.getPhone())
                                .fullName(user.getFullName())
                                .role(user.getRole())
                                .status(user.getStatus())
                                .avatarUrl(user.getAvatarUrl())
                                .emailVerified(user.getEmailVerified())
                                .fcmToken(null)
                                .lastLoginAt(user.getLastLoginAt())
                                .lockUntil(user.getLockUntil())
                                .createdAt(user.getCreatedAt())
                                .updatedAt(user.getUpdatedAt())
                                .build();
        }

        private WorkerProfileSummaryDto toWorkerProfileDto(Worker worker) {
                String skillStatus = resolveWorkerSkillStatus(worker.getWorkerId());

                return WorkerProfileSummaryDto.builder()
                                .workerId(worker.getWorkerId())
                                .fullName(worker.getFullName())
                                .cccdNumber(maskCccdDisplay(worker.getCccdNumber()))
                                .kycStatus(worker.getKycStatus())
                                .skillStatus(skillStatus)
                                .reliabilityScore(worker.getReliabilityScore())
                                .ratingAvg(worker.getRatingAvg())
                                .ratingCount(worker.getRatingCount())
                                .totalShiftsCompleted(worker.getTotalShiftsCompleted())
                                .totalCancellations(worker.getTotalCancellations())
                                .totalNoshows(worker.getTotalNoshows())
                                .isOnline(worker.getIsOnline())
                                .yearsExperience(worker.getYearsExperience())
                                .build();
        }

        private String resolveWorkerSkillStatus(UUID workerId) {
                if (workerId == null) {
                        return "NOT_FOUND";
                }
                List<SkillVerificationRequest> list = skillVerificationRequestRepository
                                .findByWorker_WorkerIdOrderByCreatedAtDesc(workerId);
                if (list != null && !list.isEmpty() && list.get(0).getStatus() != null) {
                        return list.get(0).getStatus().name();
                }
                if (workerSkillRepository != null && !workerSkillRepository.findByWorker_WorkerId(workerId).isEmpty()) {
                        return "SELF_DECLARED";
                }
                return "NOT_FOUND";
        }

        private EoProfileSummaryDto toEoProfileDto(EventOrganizer eo) {
                return EoProfileSummaryDto.builder()
                                .eoId(eo.getEoId())
                                .companyName(eo.getCompanyName())
                                .address(eo.getAddress())
                                .verificationStatus(eo.getVerificationStatus())
                                .verifiedAt(eo.getVerifiedAt())
                                .ratingAvg(eo.getRatingAvg())
                                .ratingCount(eo.getRatingCount())
                                .totalEventsCompleted(eo.getTotalEventsCompleted())
                                .build();
        }

        private String maskCccdDisplay(String rawCccd) {
                if (rawCccd == null || rawCccd.isBlank()) {
                        return null;
                }
                String trimmed = rawCccd.trim();
                if (trimmed.startsWith("****")) {
                        return trimmed;
                }
                String digits = trimmed.replaceAll("\\D", "");
                if (digits.length() < 6) {
                        return "******";
                }
                return "********" + digits.substring(digits.length() - 4);
        }
}
