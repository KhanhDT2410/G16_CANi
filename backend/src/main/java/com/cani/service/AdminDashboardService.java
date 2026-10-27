package com.cani.service;

import com.cani.dto.admin.dashboard.*;
import com.cani.entity.AuditLog;
import com.cani.entity.User;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.SkillVerificationStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service xử lý logic nghiệp vụ cho UC-86: Bảng Điều Khiển & Số Liệu Vận Hành Admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final WorkerEkycVerificationRepository workerEkycRepository;
    private final EoEkycVerificationRepository eoEkycRepository;
    private final SkillVerificationRequestRepository skillVerificationRepository;
    private final EventRepository eventRepository;
    private final ShiftRepository shiftRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * Tổng hợp và trả về các chỉ số vận hành nền tảng:
     * - User: Tổng số người dùng, số lượng theo từng vai trò, số tài khoản bị khóa.
     * - Pending Verifications: eKYC Worker chờ duyệt, eKYC Doanh nghiệp chờ duyệt, Chứng chỉ kỹ năng chờ thẩm định.
     * - Operations: Tổng số sự kiện & sự kiện đang hoạt động, tổng số ca làm việc & ca đang mở tuyển.
     * 
     * Toàn bộ các chỉ số được truy vấn trực tiếp qua count() tại database, không tải Entity lên bộ nhớ.
     */
    public AdminDashboardMetricsResponseDto getDashboardMetrics() {
        // 1. Thống kê User
        long totalUsers = userRepository.count();
        long workerCount = userRepository.countByRole(UserRole.WORKER);
        long eoCount = userRepository.countByRole(UserRole.EVENT_ORGANIZER);
        long customerCareCount = userRepository.countByRole(UserRole.CUSTOMER_CARE);
        long adminCount = userRepository.countByRole(UserRole.ADMIN);
        long lockedUsersCount = userRepository.countByStatus(UserStatus.LOCKED);

        UserMetricsDto users = UserMetricsDto.builder()
                .totalUsers(totalUsers)
                .workerCount(workerCount)
                .eventOrganizerCount(eoCount)
                .customerCareCount(customerCareCount)
                .adminCount(adminCount)
                .lockedUsersCount(lockedUsersCount)
                .build();

        // 2. Thống kê Thẩm định chờ xử lý (Pending Verifications)
        long pendingWorkerEkyc = workerEkycRepository.countByStatus("PENDING_REVIEW");
        long pendingEoEkyc = eoEkycRepository.countByStatus("PENDING_REVIEW");
        long pendingSkill = skillVerificationRepository.countByStatus(SkillVerificationStatus.PENDING_REVIEW);
        long totalPending = pendingWorkerEkyc + pendingEoEkyc + pendingSkill;

        PendingVerificationMetricsDto verifications = PendingVerificationMetricsDto.builder()
                .pendingWorkerEkycCount(pendingWorkerEkyc)
                .pendingEoEkycCount(pendingEoEkyc)
                .pendingSkillVerificationsCount(pendingSkill)
                .totalPendingCount(totalPending)
                .build();

        // 3. Thống kê Vận hành Sự kiện & Ca làm việc
        long totalEvents = eventRepository.count();
        long activeEvents = eventRepository.countByStatus(EventStatus.ACTIVE);
        long totalShifts = shiftRepository.count();
        long activeShifts = shiftRepository.countByStatus(ShiftStatus.ACTIVE);

        OperationMetricsDto operations = OperationMetricsDto.builder()
                .totalEventsCount(totalEvents)
                .activeEventsCount(activeEvents)
                .totalShiftsCount(totalShifts)
                .activeShiftsCount(activeShifts)
                .build();

        return AdminDashboardMetricsResponseDto.builder()
                .users(users)
                .verifications(verifications)
                .operations(operations)
                .build();
    }

    /**
     * Lấy 10 hoạt động quản trị gần nhất từ bảng audit_logs, kết hợp thông tin người thực hiện (Admin).
     */
    public List<AdminRecentActivityResponseDto> getRecentActivities() {
        List<AuditLog> auditLogs = auditLogRepository.findTop10ByOrderByCreatedAtDesc();
        if (auditLogs.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch lookup danh sách người thực hiện để tránh N+1 query
        Set<UUID> userIds = auditLogs.stream()
                .map(AuditLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(userIds).stream()
                    .collect(Collectors.toMap(User::getUserId, Function.identity()));

        return auditLogs.stream()
                .map(logEntry -> toActivityDto(logEntry, userMap.get(logEntry.getUserId())))
                .collect(Collectors.toList());
    }

    private AdminRecentActivityResponseDto toActivityDto(AuditLog logEntry, User adminUser) {
        String adminName = adminUser != null ? adminUser.getFullName() : "Hệ thống";
        String adminEmail = adminUser != null ? adminUser.getEmail() : null;
        String adminAvatarUrl = adminUser != null ? adminUser.getAvatarUrl() : null;

        return AdminRecentActivityResponseDto.builder()
                .id(logEntry.getAuditId())
                .userId(logEntry.getUserId())
                .adminName(adminName)
                .adminEmail(adminEmail)
                .adminAvatarUrl(adminAvatarUrl)
                .action(logEntry.getAction())
                .actionDescription(formatActionDescription(logEntry.getAction(), logEntry.getEntityType()))
                .entityType(logEntry.getEntityType())
                .entityId(logEntry.getEntityId())
                .oldValues(logEntry.getOldValues())
                .newValues(logEntry.getNewValues())
                .ipAddress(logEntry.getIpAddress())
                .createdAt(logEntry.getCreatedAt())
                .build();
    }

    private String formatActionDescription(String action, String entityType) {
        if (action == null) {
            return "Hoạt động hệ thống";
        }
        return switch (action) {
            case "UPDATE_USER_STATUS" -> "Cập nhật trạng thái người dùng";
            case "LOCK_USER" -> "Khóa tài khoản người dùng";
            case "UNLOCK_USER" -> "Mở khóa tài khoản người dùng";
            case "APPROVE_SKILL_VERIFICATION", "REVIEW_SKILL_VERIFICATION" -> "Thẩm định chứng chỉ kỹ năng";
            case "APPROVE_WORKER_EKYC", "REVIEW_WORKER_EKYC" -> "Thẩm định hồ sơ eKYC Worker";
            case "APPROVE_EO_EKYC", "REVIEW_EO_EKYC" -> "Thẩm định hồ sơ eKYC Doanh nghiệp";
            case "REJECT_SKILL_VERIFICATION" -> "Từ chối chứng chỉ kỹ năng";
            case "REJECT_WORKER_EKYC" -> "Từ chối hồ sơ eKYC Worker";
            case "REJECT_EO_EKYC" -> "Từ chối hồ sơ eKYC Doanh nghiệp";
            default -> {
                if (action.contains("USER")) yield "Quản trị người dùng";
                if (action.contains("EKYC")) yield "Xử lý hồ sơ eKYC";
                if (action.contains("SKILL")) yield "Xử lý hồ sơ kỹ năng";
                yield action;
            }
        };
    }
}
