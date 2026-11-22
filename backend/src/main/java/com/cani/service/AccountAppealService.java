package com.cani.service;

import com.cani.dto.appeal.AccountAppealResponseDto;
import com.cani.dto.appeal.ReviewAccountAppealRequestDto;
import com.cani.dto.appeal.SubmitAccountAppealRequestDto;
import com.cani.entity.AccountAppeal;
import com.cani.entity.AuditLog;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.enums.AccountAppealStatus;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.AccountAppealRepository;
import com.cani.repository.AuditLogRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountAppealService {

    private final AccountAppealRepository accountAppealRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final ObjectMapper objectMapper;

    /**
     * Nộp đơn giải trình / khiếu nại mở khóa tài khoản (Public API cho người dùng bị khóa)
     */
    @Transactional
    public AccountAppealResponseDto submitAppeal(SubmitAccountAppealRequestDto request) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản có email: " + email));

        if (user.getStatus() != UserStatus.LOCKED && user.getStatus() != UserStatus.SUSPENDED) {
            throw new BadRequestException("Tài khoản của bạn hiện đang ở trạng thái " + user.getStatus() + ", không cần nộp đơn giải trình mở khóa.");
        }

        // Chống spam: Không cho phép nộp đơn mới nếu đang có đơn chờ xử lý
        boolean hasPending = accountAppealRepository.existsByUser_UserIdAndStatusIn(
                user.getUserId(),
                List.of(AccountAppealStatus.SUBMITTED, AccountAppealStatus.UNDER_REVIEW)
        );
        if (hasPending) {
            throw new BadRequestException("Tài khoản của bạn đã có đơn giải trình đang chờ Admin xét duyệt. Vui lòng kiên nhẫn chờ phản hồi từ Ban Quản trị.");
        }

        String lockReason = findLatestLockReason(user.getUserId());

        AccountAppeal appeal = AccountAppeal.builder()
                .user(user)
                .email(user.getEmail())
                .userRole(user.getRole() != null ? user.getRole().name() : "EVENT_ORGANIZER")
                .reasonCategory(request.getReasonCategory().trim())
                .appealContent(request.getAppealContent().trim())
                .attachmentUrls(request.getAttachmentUrls() != null ? request.getAttachmentUrls().trim() : null)
                .lockReasonSnapshot(lockReason)
                .status(AccountAppealStatus.SUBMITTED)
                .build();

        AccountAppeal saved = accountAppealRepository.save(appeal);

        // Ghi AuditLog với JSON hợp lệ cho cột new_values (jsonb)
        try {
            Map<String, Object> logValues = new HashMap<>();
            logValues.put("reasonCategory", request.getReasonCategory());
            logValues.put("email", email);
            logValues.put("appealId", saved.getId());
            auditLogService.logAction(AuditLog.builder()
                    .userId(user.getUserId())
                    .entityType("ACCOUNT_APPEAL")
                    .entityId(saved.getId())
                    .action("SUBMIT_ACCOUNT_APPEAL")
                    .newValues(objectMapper.writeValueAsString(logValues))
                    .ipAddress("PUBLIC")
                    .build());
        } catch (Exception e) {
            log.warn("Không thể ghi AuditLog khi nộp đơn giải trình: {}", e.getMessage());
        }

        log.info("Người dùng {} ({}) đã nộp đơn giải trình mở khóa tài khoản (id: {})",
                user.getUserId(), user.getEmail(), saved.getId());

        return mapToDto(saved);
    }

    /**
     * Tra cứu tiến độ đơn giải trình mới nhất của tài khoản theo Email
     */
    public AccountAppealResponseDto getLatestAppealByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Vui lòng cung cấp địa chỉ email để tra cứu");
        }
        String cleanEmail = email.trim().toLowerCase();
        return accountAppealRepository.findTopByEmailIgnoreCaseOrderByCreatedAtDesc(cleanEmail)
                .map(this::mapToDto)
                .orElse(null);
    }

    /**
     * Đếm số lượng đơn giải trình tài khoản đang chờ thẩm định (SUBMITTED, UNDER_REVIEW)
     */
    public long countPendingAppeals() {
        return accountAppealRepository.countByStatusIn(
                List.of(AccountAppealStatus.SUBMITTED, AccountAppealStatus.UNDER_REVIEW)
        );
    }

    /**
     * Admin xem danh sách hàng đợi các đơn giải trình tài khoản
     */
    public Page<AccountAppealResponseDto> getAdminAppeals(AccountAppealStatus status, Pageable pageable) {
        Page<AccountAppeal> page = (status != null)
                ? accountAppealRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : accountAppealRepository.findAllByOrderByCreatedAtDesc(pageable);

        return page.map(this::mapToDto);
    }

    /**
     * Admin xem chi tiết một đơn giải trình
     */
    public AccountAppealResponseDto getAppealDetail(UUID appealId) {
        AccountAppeal appeal = accountAppealRepository.findById(appealId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn giải trình có ID: " + appealId));
        return mapToDto(appeal);
    }

    /**
     * Admin phê duyệt (APPROVED) mở khóa hoặc từ chối (REJECTED) đơn giải trình
     */
    @Transactional
    public AccountAppealResponseDto reviewAppeal(UUID adminUserId, UUID appealId, ReviewAccountAppealRequestDto request) {
        AccountAppeal appeal = accountAppealRepository.findById(appealId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn giải trình có ID: " + appealId));

        if (appeal.getStatus() == AccountAppealStatus.APPROVED || appeal.getStatus() == AccountAppealStatus.REJECTED) {
            throw new BadRequestException("Đơn giải trình này đã được xử lý trước đó với kết quả: " + appeal.getStatus());
        }

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Quản trị viên: " + adminUserId));

        String decision = request.getDecision().trim().toUpperCase();
        User targetUser = appeal.getUser();

        if ("APPROVED".equals(decision)) {
            appeal.setStatus(AccountAppealStatus.APPROVED);
            appeal.setResolvedAt(Instant.now());
            appeal.setReviewer(adminUser);
            appeal.setAdminNotes(request.getAdminNotes());

            // Tự động khôi phục trạng thái tài khoản về ACTIVE
            targetUser.setStatus(UserStatus.ACTIVE);
            userRepository.save(targetUser);

            try {
                Map<String, Object> logValues = new HashMap<>();
                logValues.put("appealId", appeal.getId().toString());
                logValues.put("decision", "APPROVED");
                logValues.put("note", request.getAdminNotes());
                auditLogService.logAction(AuditLog.builder()
                        .userId(adminUserId)
                        .entityType("USER")
                        .entityId(targetUser.getUserId())
                        .action("UNLOCK_USER_VIA_APPEAL")
                        .newValues(objectMapper.writeValueAsString(logValues))
                        .ipAddress("INTERNAL")
                        .build());
            } catch (Exception e) {
                log.warn("Không thể ghi AuditLog khi mở khóa user: {}", e.getMessage());
            }

            log.info("Admin {} đã CHẤP THUẬN đơn giải trình {}, mở khóa tài khoản {}",
                    adminUserId, appealId, targetUser.getUserId());

        } else if ("REJECTED".equals(decision)) {
            appeal.setStatus(AccountAppealStatus.REJECTED);
            appeal.setResolvedAt(Instant.now());
            appeal.setReviewer(adminUser);
            appeal.setAdminNotes(request.getAdminNotes());

            try {
                Map<String, Object> logValues = new HashMap<>();
                logValues.put("appealId", appeal.getId().toString());
                logValues.put("decision", "REJECTED");
                logValues.put("note", request.getAdminNotes());
                auditLogService.logAction(AuditLog.builder()
                        .userId(adminUserId)
                        .entityType("ACCOUNT_APPEAL")
                        .entityId(appeal.getId())
                        .action("REJECT_ACCOUNT_APPEAL")
                        .newValues(objectMapper.writeValueAsString(logValues))
                        .ipAddress("INTERNAL")
                        .build());
            } catch (Exception e) {
                log.warn("Không thể ghi AuditLog khi từ chối đơn: {}", e.getMessage());
            }

            log.info("Admin {} đã TỪ CHỐI đơn giải trình {} của tài khoản {}",
                    adminUserId, appealId, targetUser.getUserId());
        } else {
            throw new BadRequestException("Quyết định chỉ chấp nhận APPROVED hoặc REJECTED");
        }

        AccountAppeal updated = accountAppealRepository.save(appeal);
        return mapToDto(updated);
    }

    /**
     * Upload tài liệu / hình ảnh minh chứng phục vụ giải trình
     */
    public String uploadEvidence(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File tài liệu không được để trống");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BadRequestException("Dung lượng file tối đa là 10MB");
        }
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "evidence.jpg";
        String cleanName = UUID.randomUUID() + "_" + fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
        return "https://storage.cani.vn/appeals/" + cleanName;
    }

    /**
     * Tìm lý do khóa tài khoản gần nhất từ Audit Log
     */
    public String findLatestLockReason(UUID userId) {
        try {
            Optional<AuditLog> opt = auditLogRepository
                    .findTopByEntityTypeAndEntityIdAndActionOrderByCreatedAtDesc("USER", userId, "UPDATE_USER_STATUS");

            if (opt.isPresent()) {
                String newValues = opt.get().getNewValues();
                if (newValues != null && !newValues.isBlank()) {
                    JsonNode node = objectMapper.readTree(newValues);
                    if (node.has("reason") && !node.get("reason").isNull()) {
                        String r = node.get("reason").asText();
                        if (!r.isBlank()) return r;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Không thể bóc tách lock reason từ AuditLog cho user {}: {}", userId, e.getMessage());
        }
        return "Tài khoản bị tạm khóa do vi phạm chính sách hoặc điều khoản hoạt động của hệ thống CANi.";
    }

    private AccountAppealResponseDto mapToDto(AccountAppeal appeal) {
        String companyName = null;
        if (appeal.getUser() != null) {
            companyName = eventOrganizerRepository.findByUser_UserId(appeal.getUser().getUserId())
                    .map(EventOrganizer::getCompanyName)
                    .orElse(null);
        }

        return AccountAppealResponseDto.builder()
                .id(appeal.getId())
                .userId(appeal.getUser() != null ? appeal.getUser().getUserId() : null)
                .email(appeal.getEmail())
                .userRole(appeal.getUserRole())
                .fullName(appeal.getUser() != null ? appeal.getUser().getFullName() : null)
                .companyName(companyName)
                .reasonCategory(appeal.getReasonCategory())
                .appealContent(appeal.getAppealContent())
                .attachmentUrls(appeal.getAttachmentUrls())
                .lockReasonSnapshot(appeal.getLockReasonSnapshot())
                .status(appeal.getStatus())
                .reviewerId(appeal.getReviewer() != null ? appeal.getReviewer().getUserId() : null)
                .reviewerName(appeal.getReviewer() != null ? appeal.getReviewer().getFullName() : null)
                .adminNotes(appeal.getAdminNotes())
                .resolvedAt(appeal.getResolvedAt())
                .createdAt(appeal.getCreatedAt())
                .updatedAt(appeal.getUpdatedAt())
                .build();
    }
}
