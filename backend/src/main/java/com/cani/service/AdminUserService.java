package com.cani.service;

import com.cani.dto.admin.user.*;
import com.cani.dto.response.PageResponse;
import com.cani.entity.AuditLog;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.AuditLogRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import com.cani.repository.specification.UserSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final WorkerRepository workerRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * Lấy danh sách người dùng có phân trang và bộ lọc linh hoạt (search, role, status).
     */
    public PageResponse<UserSummaryResponseDto> getUsers(UserFilterRequest filter, Pageable pageable) {
        Pageable sanitizedPageable = sanitizePageable(pageable);
        Page<User> userPage = userRepository.findAll(UserSpecification.filterUsers(filter), sanitizedPageable);
        return PageResponse.from(userPage.map(this::toSummaryDto));
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
            workerRepository.findByUser_UserId(userId).ifPresent(worker ->
                    dto.setWorkerProfile(toWorkerProfileDto(worker))
            );
        } else if (user.getRole() == UserRole.EVENT_ORGANIZER) {
            eventOrganizerRepository.findByUser_UserId(userId).ifPresent(eo ->
                    dto.setEoProfile(toEoProfileDto(eo))
            );
        }

        return dto;
    }

    /**
     * Cập nhật trạng thái người dùng (LOCKED, ACTIVE) kèm lý do và ghi nhận Audit Log.
     */
    @Transactional
    public UserDetailResponseDto updateUserStatus(UUID targetUserId, UUID adminUserId, UpdateUserStatusRequestDto request) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", targetUserId));

        // Ngăn Admin tự khóa tài khoản của chính mình
        if (adminUserId != null && adminUserId.equals(targetUserId)) {
            throw new BadRequestException("Quản trị viên không thể tự thay đổi trạng thái tài khoản của chính mình");
        }

        UserStatus oldStatus = user.getStatus();
        UserStatus newStatus = request.getStatus();

        user.setStatus(newStatus);
        userRepository.save(user);

        log.info("Admin {} cập nhật trạng thái user {} từ {} sang {}. Lý do: {}",
                adminUserId, targetUserId, oldStatus, newStatus, request.getReason());

        // Ghi nhận Audit Log
        recordStatusChangeAuditLog(adminUserId, targetUserId, oldStatus, newStatus, request.getReason());

        return getUserById(targetUserId);
    }

    private void recordStatusChangeAuditLog(UUID adminUserId, UUID targetUserId, UserStatus oldStatus, UserStatus newStatus, String reason) {
        try {
            String oldValJson = objectMapper.writeValueAsString(Map.of("status", oldStatus.name()));
            String newValJson = objectMapper.writeValueAsString(Map.of("status", newStatus.name(), "reason", reason));

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

    private UserSummaryResponseDto toSummaryDto(User user) {
        return UserSummaryResponseDto.builder()
                .id(user.getUserId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(user.getEmailVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
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
                .language(user.getLanguage())
                .fcmToken(user.getFcmToken())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private WorkerProfileSummaryDto toWorkerProfileDto(Worker worker) {
        return WorkerProfileSummaryDto.builder()
                .workerId(worker.getWorkerId())
                .fullName(worker.getFullName())
                .cccdNumber(worker.getCccdNumber())
                .kycStatus(worker.getKycStatus())
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

    private EoProfileSummaryDto toEoProfileDto(EventOrganizer eo) {
        return EoProfileSummaryDto.builder()
                .eoId(eo.getEoId())
                .companyName(eo.getCompanyName())
                .taxId(eo.getTaxId())
                .address(eo.getAddress())
                .verificationStatus(eo.getVerificationStatus())
                .verifiedAt(eo.getVerifiedAt())
                .ratingAvg(eo.getRatingAvg())
                .ratingCount(eo.getRatingCount())
                .totalEventsCompleted(eo.getTotalEventsCompleted())
                .build();
    }
}
