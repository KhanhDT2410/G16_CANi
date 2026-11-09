package com.cani.controller;

import com.cani.config.SecurityConfig;
import com.cani.dto.admin.noshow.ManualMarkNoShowRequestDto;
import com.cani.dto.admin.noshow.NoShowResponseDto;
import com.cani.dto.admin.noshow.NoShowScanResultDto;
import com.cani.dto.admin.noshow.NoShowStatsDto;
import com.cani.entity.User;
import com.cani.entity.enums.NoShowDetectedBy;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.UserRepository;
import com.cani.security.RestAccessDeniedHandler;
import com.cani.security.RestAuthenticationEntryPoint;
import com.cani.security.UserAuthorityFilter;
import com.cani.service.NoShowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminNoShowController.class)
@Import({
        SecurityConfig.class,
        UserAuthorityFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@DisplayName("AdminNoShowController — Kiểm thử API Quản lý Vi phạm No-show cho Admin & Customer Care (UC-88)")
class AdminNoShowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NoShowService noShowService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    private UsernamePasswordAuthenticationToken createAuthToken(UserRole role) {
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email(role.name().toLowerCase() + "@cani.vn")
                .fullName("Test " + role.name())
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();

        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    @Test
    @DisplayName("Admin xem danh sách ca No-show thành công (200 OK)")
    void admin_getNoShows_returnsOk() throws Exception {
        NoShowResponseDto dto = NoShowResponseDto.builder()
                .id(UUID.randomUUID())
                .workerName("Trần Văn B")
                .shiftRole("Lễ tân")
                .eventName("Sự Kiện Tech Expo")
                .penaltyAmount(25)
                .detectedBy(NoShowDetectedBy.SYSTEM_CRON)
                .build();

        when(noShowService.getNoShows(any())).thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/v1/admin/no-shows")
                        .with(authentication(createAuthToken(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].workerName").value("Trần Văn B"));
    }

    @Test
    @DisplayName("Customer Care xem danh sách ca No-show thành công (200 OK) — không bị 403")
    void customerCare_getNoShows_returnsOk() throws Exception {
        when(noShowService.getNoShows(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/admin/no-shows")
                        .with(authentication(createAuthToken(UserRole.CUSTOMER_CARE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Worker bị chặn 403 Forbidden khi cố truy cập API Admin No-show")
    void worker_accessNoShows_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/no-shows")
                        .with(authentication(createAuthToken(UserRole.WORKER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin đánh dấu No-show thủ công thành công (200 OK)")
    void admin_markNoShow_returnsOk() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        ManualMarkNoShowRequestDto request = ManualMarkNoShowRequestDto.builder()
                .notes("Xác nhận không đến làm việc")
                .build();

        NoShowResponseDto response = NoShowResponseDto.builder()
                .id(UUID.randomUUID())
                .assignmentId(assignmentId)
                .workerName("Lê Văn C")
                .penaltyAmount(25)
                .build();

        when(noShowService.markNoShow(eq(assignmentId), any(), any(), eq("Xác nhận không đến làm việc")))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/no-shows/" + assignmentId + "/mark")
                        .with(authentication(createAuthToken(UserRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.workerName").value("Lê Văn C"));
    }

    @Test
    @DisplayName("Kích hoạt quét No-show toàn hệ thống thành công (200 OK)")
    void admin_triggerScan_returnsOk() throws Exception {
        NoShowScanResultDto result = NoShowScanResultDto.builder()
                .totalCandidatesScanned(5)
                .totalMarkedNoShow(2)
                .totalEmergencyDispatched(2)
                .totalAccountsLocked(1)
                .scannedAt(Instant.now())
                .message("Đã quét 5 ca tiềm năng, phát hiện 2 ca No-show.")
                .build();

        when(noShowService.scanAndProcessNoShows()).thenReturn(result);

        mockMvc.perform(post("/api/v1/admin/no-shows/scan")
                        .with(authentication(createAuthToken(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalMarkedNoShow").value(2));
    }

    @Test
    @DisplayName("Lấy thống kê No-show thành công (200 OK)")
    void admin_getStats_returnsOk() throws Exception {
        NoShowStatsDto stats = NoShowStatsDto.builder()
                .totalNoShows(20)
                .noShowsToday(4)
                .noShowsThisMonth(18)
                .totalRsDeducted(500)
                .totalAccountsLocked(3)
                .build();

        when(noShowService.getNoShowStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/admin/no-shows/stats")
                        .with(authentication(createAuthToken(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalNoShows").value(20));
    }
}

