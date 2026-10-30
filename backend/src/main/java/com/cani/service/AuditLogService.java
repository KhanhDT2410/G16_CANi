package com.cani.service;

import com.cani.entity.AuditLog;
import com.cani.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

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
