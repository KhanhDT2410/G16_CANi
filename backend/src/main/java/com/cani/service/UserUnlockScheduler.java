package com.cani.service;

import com.cani.entity.AuditLog;
import com.cani.entity.User;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserUnlockScheduler {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * Tự động mở khóa các tài khoản SUSPENDED/LOCKED có lockUntil đã quá hạn (mỗi 15 phút).
     */
    @Scheduled(cron = "${cani.user-unlock.cron:0 */15 * * * *}")
    @Transactional
    public int autoUnlockExpiredUsers() {
        Instant now = Instant.now();
        List<User> expiredUsers = userRepository.findByStatusInAndLockUntilBefore(
                List.of(UserStatus.SUSPENDED, UserStatus.LOCKED), now);

        if (expiredUsers.isEmpty()) {
            return 0;
        }

        int unlockedCount = 0;
        for (User user : expiredUsers) {
            UserStatus oldStatus = user.getStatus();
            Instant oldLockUntil = user.getLockUntil();

            user.setStatus(UserStatus.ACTIVE);
            user.setLockUntil(null);
            userRepository.save(user);
            unlockedCount++;

            log.info("AUTO_UNLOCK: Tài khoản {} (role {}) đã được tự động mở khóa (hết hạn lockUntil={})",
                    user.getUserId(), user.getRole(), oldLockUntil);

            try {
                Map<String, Object> oldVal = new HashMap<>();
                oldVal.put("status", oldStatus.name());
                if (oldLockUntil != null) {
                    oldVal.put("lockUntil", oldLockUntil.toString());
                }

                Map<String, Object> newVal = new HashMap<>();
                newVal.put("status", UserStatus.ACTIVE.name());
                newVal.put("reason", "Tự động mở khóa do hết thời hạn tạm khóa");

                AuditLog logEntry = AuditLog.builder()
                        .userId(null)
                        .action("AUTO_UNLOCK")
                        .entityType("USER")
                        .entityId(user.getUserId())
                        .oldValues(objectMapper.writeValueAsString(oldVal))
                        .newValues(objectMapper.writeValueAsString(newVal))
                        .build();

                auditLogService.logAction(logEntry);
            } catch (Exception e) {
                log.error("Lỗi khi ghi AuditLog AUTO_UNLOCK cho user {}", user.getUserId(), e);
            }
        }

        return unlockedCount;
    }
}
