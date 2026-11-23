package com.cani.service;

import com.cani.entity.AuditLog;
import com.cani.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void logEkycAction(UUID actor, String action, String entityType,
            UUID entityId, Map<String, Object> metadata) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .userId(actor)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .newValues(objectMapper.writeValueAsString(metadata))
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize eKYC audit metadata", e);
        }
    }

    /**
     * Ghi nhận AuditLog trong một transaction độc lập (REQUIRES_NEW).
     * Đảm bảo nếu việc ghi log gặp sự cố thì không làm rollback luồng nghiệp vụ chính của người dùng.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(AuditLog logEntry) {
        try {
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Không thể lưu AuditLog [action={}], bỏ qua để bảo vệ nghiệp vụ chính: {}",
                    logEntry.getAction(), e.getMessage(), e);
        }
    }
}
