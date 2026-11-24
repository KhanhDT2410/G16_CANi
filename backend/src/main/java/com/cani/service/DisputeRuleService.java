package com.cani.service;

import com.cani.dto.admin.disputerule.CreateDisputeRuleRequestDto;
import com.cani.dto.admin.disputerule.DisputeRuleResponseDto;
import com.cani.dto.admin.disputerule.UpdateDisputeRuleRequestDto;
import com.cani.entity.AuditLog;
import com.cani.entity.DisputeRule;
import com.cani.entity.enums.DisputeType;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.DisputeRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisputeRuleService {

    private final DisputeRuleRepository disputeRuleRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * Lấy danh sách quy tắc xử lý khiếu nại (có thể lọc theo loại hoặc trạng thái kích hoạt).
     */
    public List<DisputeRuleResponseDto> getRules(DisputeType ruleType, Boolean enabled) {
        if (ruleType != null && Boolean.TRUE.equals(enabled)) {
            return disputeRuleRepository.findByRuleTypeAndEnabledTrue(ruleType).stream()
                    .map(this::toDto)
                    .toList();
        }
        if (ruleType == null && Boolean.TRUE.equals(enabled)) {
            return disputeRuleRepository.findByEnabledTrueOrderByCodeAscRepeatIndexAsc().stream()
                    .map(this::toDto)
                    .toList();
        }

        Specification<DisputeRule> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (ruleType != null) {
                predicates = cb.and(predicates, cb.equal(root.get("ruleType"), ruleType));
            }
            if (enabled != null) {
                predicates = cb.and(predicates, cb.equal(root.get("enabled"), enabled));
            }
            query.orderBy(cb.asc(root.get("code")), cb.asc(root.get("repeatIndex")));
            return predicates;
        };

        return disputeRuleRepository.findAll(spec).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Lấy chi tiết quy tắc theo ID.
     */
    public DisputeRuleResponseDto getRuleById(UUID id) {
        DisputeRule rule = disputeRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quy tắc xử lý khiếu nại", "id", id));
        return toDto(rule);
    }

    /**
     * Tạo mới quy tắc khiếu nại và ghi nhận AuditLog.
     */
    @Transactional
    public DisputeRuleResponseDto createRule(UUID adminId, CreateDisputeRuleRequestDto request) {
        DisputeRule rule = DisputeRule.builder()
                .code(request.getCode().trim().toUpperCase())
                .behavior(request.getBehavior().trim())
                .violatingParty(request.getViolatingParty().trim())
                .sanctionForm(request.getSanctionForm().trim())
                .specificPenalty(request.getSpecificPenalty().trim())
                .ruleType(request.getRuleType())
                .faultOutcome(request.getFaultOutcome())
                .repeatWindowDays(request.getRepeatWindowDays())
                .repeatIndex(request.getRepeatIndex() != null ? request.getRepeatIndex() : 1)
                .sanctionText(request.getSanctionText().trim())
                .adminActionText(request.getAdminActionText() != null ? request.getAdminActionText().trim() : null)
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();

        DisputeRule saved = disputeRuleRepository.save(rule);
        log.info("Admin {} tạo mới DisputeRule id={}, code={}", adminId, saved.getId(), saved.getCode());

        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(adminId)
                    .action("CREATE_DISPUTE_RULE")
                    .entityType("DISPUTE_RULE")
                    .entityId(saved.getId())
                    .newValues(objectMapper.writeValueAsString(Map.of(
                            "code", saved.getCode(),
                            "ruleType", saved.getRuleType().name(),
                            "outcome", saved.getFaultOutcome().name(),
                            "sanctionText", saved.getSanctionText()
                    )))
                    .build();
            auditLogService.logAction(auditLog);
        } catch (Exception e) {
            log.error("Lỗi khi ghi AuditLog CREATE_DISPUTE_RULE", e);
        }

        return toDto(saved);
    }

    /**
     * Cập nhật quy tắc khiếu nại (tự động tăng version) và ghi nhận AuditLog.
     */
    @Transactional
    public DisputeRuleResponseDto updateRule(UUID adminId, UUID id, UpdateDisputeRuleRequestDto request) {
        DisputeRule rule = disputeRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quy tắc xử lý khiếu nại", "id", id));

        String oldValuesJson = "";
        try {
            oldValuesJson = objectMapper.writeValueAsString(Map.of(
                    "code", rule.getCode(),
                    "sanctionText", rule.getSanctionText(),
                    "enabled", rule.getEnabled(),
                    "version", rule.getVersion()
            ));
        } catch (Exception ignored) {}

        rule.setCode(request.getCode().trim().toUpperCase());
        rule.setBehavior(request.getBehavior().trim());
        rule.setViolatingParty(request.getViolatingParty().trim());
        rule.setSanctionForm(request.getSanctionForm().trim());
        rule.setSpecificPenalty(request.getSpecificPenalty().trim());
        rule.setRuleType(request.getRuleType());
        rule.setFaultOutcome(request.getFaultOutcome());
        rule.setRepeatWindowDays(request.getRepeatWindowDays());
        rule.setRepeatIndex(request.getRepeatIndex());
        rule.setSanctionText(request.getSanctionText().trim());
        rule.setAdminActionText(request.getAdminActionText() != null ? request.getAdminActionText().trim() : null);
        rule.setEnabled(request.getEnabled());

        DisputeRule saved = disputeRuleRepository.save(rule);
        log.info("Admin {} cập nhật DisputeRule id={}, code={}, newVersion={}", adminId, saved.getId(), saved.getCode(), saved.getVersion());

        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(adminId)
                    .action("UPDATE_DISPUTE_RULE")
                    .entityType("DISPUTE_RULE")
                    .entityId(saved.getId())
                    .oldValues(oldValuesJson)
                    .newValues(objectMapper.writeValueAsString(Map.of(
                            "code", saved.getCode(),
                            "sanctionText", saved.getSanctionText(),
                            "enabled", saved.getEnabled(),
                            "version", saved.getVersion()
                    )))
                    .build();
            auditLogService.logAction(auditLog);
        } catch (Exception e) {
            log.error("Lỗi khi ghi AuditLog UPDATE_DISPUTE_RULE", e);
        }

        return toDto(saved);
    }

    /**
     * Soft delete: đánh dấu enabled = false.
     */
    @Transactional
    public void deleteRule(UUID adminId, UUID id) {
        DisputeRule rule = disputeRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quy tắc xử lý khiếu nại", "id", id));

        rule.setEnabled(false);
        disputeRuleRepository.save(rule);
        log.info("Admin {} vô hiệu hóa (soft-delete) DisputeRule id={}", adminId, id);

        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(adminId)
                    .action("DELETE_DISPUTE_RULE")
                    .entityType("DISPUTE_RULE")
                    .entityId(id)
                    .newValues(objectMapper.writeValueAsString(Map.of("enabled", false)))
                    .build();
            auditLogService.logAction(auditLog);
        } catch (Exception e) {
            log.error("Lỗi khi ghi AuditLog DELETE_DISPUTE_RULE", e);
        }
    }

    private DisputeRuleResponseDto toDto(DisputeRule r) {
        return DisputeRuleResponseDto.builder()
                .id(r.getId())
                .code(r.getCode())
                .behavior(r.getBehavior())
                .violatingParty(r.getViolatingParty())
                .sanctionForm(r.getSanctionForm())
                .specificPenalty(r.getSpecificPenalty())
                .ruleType(r.getRuleType())
                .faultOutcome(r.getFaultOutcome())
                .repeatWindowDays(r.getRepeatWindowDays())
                .repeatIndex(r.getRepeatIndex())
                .sanctionText(r.getSanctionText())
                .adminActionText(r.getAdminActionText())
                .enabled(r.getEnabled())
                .version(r.getVersion())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
