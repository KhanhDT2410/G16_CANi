package com.cani.service;

import com.cani.dto.admin.blacklist.BlacklistResponseDto;
import com.cani.dto.admin.blacklist.CreateBlacklistRequestDto;
import com.cani.dto.response.PageResponse;
import com.cani.entity.AuditLog;
import com.cani.entity.Blacklist;
import com.cani.entity.User;
import com.cani.entity.enums.BlacklistIdentifierType;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.BlacklistRepository;
import com.cani.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBlacklistService {

    private final BlacklistRepository blacklistRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    // TODO: Tích hợp chặn eKYC (VnptEkycClient) và xác thực số điện thoại (AuthService) tự động từ Blacklist trong giai đoạn tiếp theo.

    /**
     * Danh sách Blacklist có phân trang và bộ lọc theo định danh hoặc loại.
     */
    public PageResponse<BlacklistResponseDto> getBlacklist(String search, BlacklistIdentifierType identifierType, Pageable pageable) {
        Specification<Blacklist> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (search != null && !search.isBlank()) {
                predicates = cb.and(predicates, cb.or(
                        cb.like(cb.lower(root.get("identifier")), "%" + search.trim().toLowerCase() + "%"),
                        cb.like(cb.lower(root.get("reason")), "%" + search.trim().toLowerCase() + "%")
                ));
            }
            if (identifierType != null) {
                predicates = cb.and(predicates, cb.equal(root.get("identifierType"), identifierType));
            }
            return predicates;
        };

        Page<Blacklist> page = blacklistRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::toDto));
    }

    /**
     * Thêm định danh vào Blacklist và ghi nhận AuditLog.
     */
    @Transactional
    public BlacklistResponseDto addToBlacklist(UUID adminId, CreateBlacklistRequestDto request) {
        if (blacklistRepository.existsByIdentifierAndIdentifierType(request.getIdentifier().trim(), request.getIdentifierType())) {
            throw new BadRequestException("Định danh này đã nằm trong danh sách đen");
        }

        User adminUser = adminId != null ? userRepository.findById(adminId).orElse(null) : null;

        Blacklist blacklist = Blacklist.builder()
                .identifier(request.getIdentifier().trim())
                .identifierType(request.getIdentifierType())
                .permanent(Boolean.TRUE.equals(request.getPermanent()))
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .reason(request.getReason().trim())
                .createdBy(adminUser)
                .build();

        Blacklist saved = blacklistRepository.save(blacklist);
        log.info("Admin {} thêm định danh {} (loại {}) vào Blacklist (permanent={})",
                adminId, saved.getIdentifier(), saved.getIdentifierType(), saved.getPermanent());

        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(adminId)
                    .action("CREATE_BLACKLIST")
                    .entityType("BLACKLIST")
                    .entityId(saved.getId())
                    .newValues(objectMapper.writeValueAsString(Map.of(
                            "identifier", saved.getIdentifier(),
                            "type", saved.getIdentifierType().name(),
                            "permanent", saved.getPermanent(),
                            "reason", saved.getReason()
                    )))
                    .build();
            auditLogService.logAction(auditLog);
        } catch (Exception e) {
            log.error("Lỗi khi ghi AuditLog CREATE_BLACKLIST", e);
        }

        return toDto(saved);
    }

    /**
     * Xóa bản ghi khỏi Blacklist (chỉ cho phép nếu permanent = false).
     */
    @Transactional
    public void removeFromBlacklist(UUID adminId, UUID blacklistId) {
        Blacklist blacklist = blacklistRepository.findById(blacklistId)
                .orElseThrow(() -> new ResourceNotFoundException("Danh sách đen", "id", blacklistId));

        if (Boolean.TRUE.equals(blacklist.getPermanent())) {
            throw new BadRequestException("Không thể xóa bản ghi danh sách đen vĩnh viễn (permanent=true)");
        }

        blacklistRepository.delete(blacklist);
        log.info("Admin {} xóa bản ghi Blacklist {} (identifier={})", adminId, blacklistId, blacklist.getIdentifier());

        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(adminId)
                    .action("DELETE_BLACKLIST")
                    .entityType("BLACKLIST")
                    .entityId(blacklistId)
                    .oldValues(objectMapper.writeValueAsString(Map.of(
                            "identifier", blacklist.getIdentifier(),
                            "type", blacklist.getIdentifierType().name()
                    )))
                    .build();
            auditLogService.logAction(auditLog);
        } catch (Exception e) {
            log.error("Lỗi khi ghi AuditLog DELETE_BLACKLIST", e);
        }
    }

    private BlacklistResponseDto toDto(Blacklist b) {
        return BlacklistResponseDto.builder()
                .id(b.getId())
                .identifier(b.getIdentifier())
                .identifierType(b.getIdentifierType())
                .permanent(b.getPermanent())
                .referenceType(b.getReferenceType())
                .referenceId(b.getReferenceId())
                .reason(b.getReason())
                .createdById(b.getCreatedBy() != null ? b.getCreatedBy().getUserId() : null)
                .createdByName(b.getCreatedBy() != null ? b.getCreatedBy().getFullName() : null)
                .createdAt(b.getCreatedAt())
                .build();
    }
}
