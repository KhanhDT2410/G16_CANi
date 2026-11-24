package com.cani.service;

import com.cani.entity.AuditLog;
import com.cani.entity.User;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserUnlockSchedulerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private UserUnlockScheduler userUnlockScheduler;

    private User suspendedUser;

    @BeforeEach
    void setUp() {
        suspendedUser = User.builder()
                .userId(UUID.randomUUID())
                .email("test.worker@example.com")
                .role(UserRole.WORKER)
                .status(UserStatus.SUSPENDED)
                .lockUntil(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
    }

    @Test
    @DisplayName("Tự động mở khóa thành công khi tài khoản có lockUntil đã quá hạn")
    void autoUnlockExpiredUsers_Success() {
        when(userRepository.findByStatusInAndLockUntilBefore(any(), any()))
                .thenReturn(List.of(suspendedUser));

        int count = userUnlockScheduler.autoUnlockExpiredUsers();

        assertThat(count).isEqualTo(1);
        assertThat(suspendedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(suspendedUser.getLockUntil()).isNull();
        verify(userRepository, times(1)).save(suspendedUser);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService, times(1)).logAction(captor.capture());
        AuditLog logged = captor.getValue();
        assertThat(logged.getAction()).isEqualTo("AUTO_UNLOCK");
        assertThat(logged.getEntityId()).isEqualTo(suspendedUser.getUserId());
        assertThat(logged.getNewValues()).contains("ACTIVE");
    }

    @Test
    @DisplayName("Không làm gì khi không có tài khoản nào hết hạn khóa")
    void autoUnlockExpiredUsers_NoExpiredUsers() {
        when(userRepository.findByStatusInAndLockUntilBefore(any(), any()))
                .thenReturn(List.of());

        int count = userUnlockScheduler.autoUnlockExpiredUsers();

        assertThat(count).isEqualTo(0);
        verify(userRepository, never()).save(any());
        verify(auditLogService, never()).logAction(any());
    }
}
