package com.cani.controller;

import com.cani.dto.admin.dashboard.*;
import com.cani.repository.UserRepository;
import com.cani.service.AdminDashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminDashboardService adminDashboardService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/admin/dashboard/metrics - Lấy số liệu vận hành thành công")
    @WithMockUser(roles = "ADMIN")
    void testGetDashboardMetrics_Success() throws Exception {
        UserMetricsDto userMetrics = UserMetricsDto.builder()
                .totalUsers(200)
                .workerCount(150)
                .eventOrganizerCount(40)
                .customerCareCount(8)
                .adminCount(2)
                .lockedUsersCount(4)
                .build();

        PendingVerificationMetricsDto verificationMetrics = PendingVerificationMetricsDto.builder()
                .pendingWorkerEkycCount(5)
                .pendingEoEkycCount(2)
                .pendingSkillVerificationsCount(7)
                .totalPendingCount(14)
                .build();

        OperationMetricsDto operationMetrics = OperationMetricsDto.builder()
                .totalEventsCount(30)
                .activeEventsCount(12)
                .totalShiftsCount(80)
                .activeShiftsCount(35)
                .build();

        AdminDashboardMetricsResponseDto responseDto = AdminDashboardMetricsResponseDto.builder()
                .users(userMetrics)
                .verifications(verificationMetrics)
                .operations(operationMetrics)
                .build();

        when(adminDashboardService.getDashboardMetrics()).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/admin/dashboard/metrics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy số liệu vận hành dashboard thành công"))
                .andExpect(jsonPath("$.data.users.totalUsers").value(200))
                .andExpect(jsonPath("$.data.users.workerCount").value(150))
                .andExpect(jsonPath("$.data.users.lockedUsersCount").value(4))
                .andExpect(jsonPath("$.data.verifications.pendingWorkerEkycCount").value(5))
                .andExpect(jsonPath("$.data.verifications.pendingEoEkycCount").value(2))
                .andExpect(jsonPath("$.data.verifications.pendingSkillVerificationsCount").value(7))
                .andExpect(jsonPath("$.data.verifications.totalPendingCount").value(14))
                .andExpect(jsonPath("$.data.operations.activeEventsCount").value(12))
                .andExpect(jsonPath("$.data.operations.totalShiftsCount").value(80));
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard/recent-activities - Lấy 10 hoạt động gần nhất thành công")
    @WithMockUser(roles = "ADMIN")
    void testGetRecentActivities_Success() throws Exception {
        UUID activityId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        AdminRecentActivityResponseDto activity = AdminRecentActivityResponseDto.builder()
                .id(activityId)
                .userId(adminId)
                .adminName("Admin Quản Trị")
                .adminEmail("admin@cani.vn")
                .action("UPDATE_USER_STATUS")
                .actionDescription("Cập nhật trạng thái người dùng")
                .entityType("USER")
                .entityId(UUID.randomUUID())
                .oldValues("{\"status\":\"ACTIVE\"}")
                .newValues("{\"status\":\"LOCKED\"}")
                .createdAt(Instant.now())
                .build();

        when(adminDashboardService.getRecentActivities()).thenReturn(List.of(activity));

        mockMvc.perform(get("/api/v1/admin/dashboard/recent-activities")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy danh sách hoạt động gần nhất thành công"))
                .andExpect(jsonPath("$.data[0].id").value(activityId.toString()))
                .andExpect(jsonPath("$.data[0].action").value("UPDATE_USER_STATUS"))
                .andExpect(jsonPath("$.data[0].actionDescription").value("Cập nhật trạng thái người dùng"))
                .andExpect(jsonPath("$.data[0].adminName").value("Admin Quản Trị"));
    }
}
