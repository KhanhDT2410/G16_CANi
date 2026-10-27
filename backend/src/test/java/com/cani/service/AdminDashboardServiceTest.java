package com.cani.service;

import com.cani.dto.admin.dashboard.AdminDashboardMetricsResponseDto;
import com.cani.dto.admin.dashboard.AdminRecentActivityResponseDto;
import com.cani.entity.AuditLog;
import com.cani.entity.User;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.SkillVerificationStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkerEkycVerificationRepository workerEkycRepository;

    @Mock
    private EoEkycVerificationRepository eoEkycRepository;

    @Mock
    private SkillVerificationRequestRepository skillVerificationRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    @Test
    @DisplayName("getDashboardMetrics() - Tổng hợp số liệu vận hành chính xác qua các câu lệnh count")
    void testGetDashboardMetrics_Success() {
        // Given
        when(userRepository.count()).thenReturn(150L);
        when(userRepository.countByRole(UserRole.WORKER)).thenReturn(100L);
        when(userRepository.countByRole(UserRole.EVENT_ORGANIZER)).thenReturn(40L);
        when(userRepository.countByRole(UserRole.CUSTOMER_CARE)).thenReturn(7L);
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(3L);
        when(userRepository.countByStatus(UserStatus.LOCKED)).thenReturn(5L);

        when(workerEkycRepository.countByStatus("PENDING_REVIEW")).thenReturn(8L);
        when(eoEkycRepository.countByStatus("PENDING_REVIEW")).thenReturn(3L);
        when(skillVerificationRepository.countByStatus(SkillVerificationStatus.PENDING_REVIEW)).thenReturn(10L);

        when(eventRepository.count()).thenReturn(35L);
        when(eventRepository.countByStatus(EventStatus.ACTIVE)).thenReturn(15L);
        when(shiftRepository.count()).thenReturn(90L);
        when(shiftRepository.countByStatus(ShiftStatus.ACTIVE)).thenReturn(40L);

        // When
        AdminDashboardMetricsResponseDto result = adminDashboardService.getDashboardMetrics();

        // Then
        assertNotNull(result);

        // Verify User metrics
        assertNotNull(result.getUsers());
        assertEquals(150L, result.getUsers().getTotalUsers());
        assertEquals(100L, result.getUsers().getWorkerCount());
        assertEquals(40L, result.getUsers().getEventOrganizerCount());
        assertEquals(7L, result.getUsers().getCustomerCareCount());
        assertEquals(3L, result.getUsers().getAdminCount());
        assertEquals(5L, result.getUsers().getLockedUsersCount());

        // Verify Verification metrics
        assertNotNull(result.getVerifications());
        assertEquals(8L, result.getVerifications().getPendingWorkerEkycCount());
        assertEquals(3L, result.getVerifications().getPendingEoEkycCount());
        assertEquals(10L, result.getVerifications().getPendingSkillVerificationsCount());
        assertEquals(21L, result.getVerifications().getTotalPendingCount());

        // Verify Operations metrics
        assertNotNull(result.getOperations());
        assertEquals(35L, result.getOperations().getTotalEventsCount());
        assertEquals(15L, result.getOperations().getActiveEventsCount());
        assertEquals(90L, result.getOperations().getTotalShiftsCount());
        assertEquals(40L, result.getOperations().getActiveShiftsCount());

        // Verify counts were called
        verify(userRepository, times(1)).count();
        verify(userRepository, times(1)).countByRole(UserRole.WORKER);
        verify(workerEkycRepository, times(1)).countByStatus("PENDING_REVIEW");
        verify(eoEkycRepository, times(1)).countByStatus("PENDING_REVIEW");
        verify(skillVerificationRepository, times(1)).countByStatus(SkillVerificationStatus.PENDING_REVIEW);
        verify(eventRepository, times(1)).countByStatus(EventStatus.ACTIVE);
        verify(shiftRepository, times(1)).countByStatus(ShiftStatus.ACTIVE);
    }

    @Test
    @DisplayName("getRecentActivities() - Trả về 10 hoạt động gần nhất kèm thông tin Admin thực hiện")
    void testGetRecentActivities_Success() {
        // Given
        UUID adminId = UUID.randomUUID();
        User mockAdmin = User.builder()
                .userId(adminId)
                .fullName("Quản trị viên Hệ thống")
                .email("admin@cani.vn")
                .avatarUrl("https://storage.cani.vn/avatars/admin.png")
                .build();

        AuditLog log1 = AuditLog.builder()
                .auditId(UUID.randomUUID())
                .userId(adminId)
                .action("UPDATE_USER_STATUS")
                .entityType("USER")
                .entityId(UUID.randomUUID())
                .oldValues("{\"status\":\"ACTIVE\"}")
                .newValues("{\"status\":\"LOCKED\",\"reason\":\"Vi phạm nội quy\"}")
                .ipAddress("127.0.0.1")
                .createdAt(Instant.now())
                .build();

        AuditLog log2 = AuditLog.builder()
                .auditId(UUID.randomUUID())
                .userId(null) // Hành động tự động bởi hệ thống
                .action("APPROVE_SKILL_VERIFICATION")
                .entityType("SKILL_VERIFICATION")
                .entityId(UUID.randomUUID())
                .newValues("{\"status\":\"APPROVED\"}")
                .createdAt(Instant.now().minusSeconds(60))
                .build();

        when(auditLogRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of(log1, log2));
        when(userRepository.findAllById(any())).thenReturn(List.of(mockAdmin));

        // When
        List<AdminRecentActivityResponseDto> activities = adminDashboardService.getRecentActivities();

        // Then
        assertNotNull(activities);
        assertEquals(2, activities.size());

        // Log 1: Performed by admin
        AdminRecentActivityResponseDto dto1 = activities.get(0);
        assertEquals(log1.getAuditId(), dto1.getId());
        assertEquals("UPDATE_USER_STATUS", dto1.getAction());
        assertEquals("Cập nhật trạng thái người dùng", dto1.getActionDescription());
        assertEquals("Quản trị viên Hệ thống", dto1.getAdminName());
        assertEquals("admin@cani.vn", dto1.getAdminEmail());

        // Log 2: System action
        AdminRecentActivityResponseDto dto2 = activities.get(1);
        assertEquals(log2.getAuditId(), dto2.getId());
        assertEquals("APPROVE_SKILL_VERIFICATION", dto2.getAction());
        assertEquals("Thẩm định chứng chỉ kỹ năng", dto2.getActionDescription());
        assertEquals("Hệ thống", dto2.getAdminName());
        assertNull(dto2.getAdminEmail());
    }

    @Test
    @DisplayName("getRecentActivities() - Trả về danh sách rỗng khi chưa có AuditLog")
    void testGetRecentActivities_Empty() {
        when(auditLogRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());

        List<AdminRecentActivityResponseDto> activities = adminDashboardService.getRecentActivities();

        assertNotNull(activities);
        assertTrue(activities.isEmpty());
        verify(userRepository, never()).findAllById(any());
    }
}
