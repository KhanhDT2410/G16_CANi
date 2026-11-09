package com.cani.controller;

import com.cani.config.SecurityConfig;
import com.cani.dto.admin.revenue.PeriodMetricsDto;
import com.cani.dto.admin.revenue.RevenueAnalyticsResponseDto;
import com.cani.dto.admin.revenue.RevenueSummaryResponseDto;
import com.cani.dto.admin.revenue.RevenueTransactionDto;
import com.cani.entity.User;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.UserRepository;
import com.cani.security.RestAccessDeniedHandler;
import com.cani.security.RestAuthenticationEntryPoint;
import com.cani.security.UserAuthorityFilter;
import com.cani.service.RevenueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRevenueController.class)
@Import({
        SecurityConfig.class,
        UserAuthorityFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@DisplayName("AdminRevenueController — Kiểm thử API Bảng điều khiển doanh thu (UC-108)")
class AdminRevenueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RevenueService revenueService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UsernamePasswordAuthenticationToken mockAdminAuth() {
        User adminUser = User.builder()
                .userId(UUID.randomUUID())
                .email("admin@cani.vn")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        return new UsernamePasswordAuthenticationToken(
                adminUser,
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    private UsernamePasswordAuthenticationToken mockCustomerCareAuth() {
        User ccUser = User.builder()
                .userId(UUID.randomUUID())
                .email("cc@cani.vn")
                .role(UserRole.CUSTOMER_CARE)
                .status(UserStatus.ACTIVE)
                .build();
        return new UsernamePasswordAuthenticationToken(
                ccUser,
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER_CARE"))
        );
    }

    @Test
    @DisplayName("GET /api/v1/admin/revenue/summary — Admin truy cập thành công trả về 200 OK")
    void shouldReturnSummaryForAdmin() throws Exception {
        RevenueSummaryResponseDto mockSummary = RevenueSummaryResponseDto.builder()
                .currentPeriod(PeriodMetricsDto.builder()
                        .totalPackageRevenue(BigDecimal.valueOf(5000000))
                        .totalPackageSales(5)
                        .totalPaymentVolume(BigDecimal.valueOf(1000000))
                        .totalPaymentTransactions(3)
                        .combinedVolume(BigDecimal.valueOf(6000000))
                        .build())
                .build();

        when(revenueService.getRevenueSummary(any(), any())).thenReturn(mockSummary);

        mockMvc.perform(get("/api/v1/admin/revenue/summary")
                        .with(authentication(mockAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy dữ liệu tổng quan doanh thu thành công"))
                .andExpect(jsonPath("$.data.currentPeriod.totalPackageRevenue").value(5000000));
    }

    @Test
    @DisplayName("GET /api/v1/admin/revenue/transactions — Admin lấy danh sách giao dịch thành công")
    void shouldReturnTransactionsForAdmin() throws Exception {
        RevenueTransactionDto item = RevenueTransactionDto.builder()
                .id(UUID.randomUUID())
                .transactionType("PACKAGE_SALE")
                .amount(BigDecimal.valueOf(1000000))
                .status("ACTIVE")
                .build();

        when(revenueService.getRevenueTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/v1/admin/revenue/transactions")
                        .with(authentication(mockAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].transactionType").value("PACKAGE_SALE"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/revenue/analytics — Admin lấy xu hướng doanh thu thành công")
    void shouldReturnAnalyticsForAdmin() throws Exception {
        RevenueAnalyticsResponseDto analytics = RevenueAnalyticsResponseDto.builder()
                .period("day")
                .totalPackageRevenue(BigDecimal.valueOf(2000000))
                .dataPoints(Collections.emptyList())
                .build();

        when(revenueService.getRevenueAnalytics(any(), any(), any())).thenReturn(analytics);

        mockMvc.perform(get("/api/v1/admin/revenue/analytics")
                        .param("period", "day")
                        .with(authentication(mockAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.period").value("day"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/revenue/summary — Customer Care truy cập bị chặn 403 Forbidden")
    void shouldReturnForbiddenForCustomerCare() throws Exception {
        mockMvc.perform(get("/api/v1/admin/revenue/summary")
                        .with(authentication(mockCustomerCareAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/revenue/summary — Người dùng chưa xác thực bị chặn 401 Unauthorized")
    void shouldReturnUnauthorizedForAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/admin/revenue/summary"))
                .andExpect(status().isUnauthorized());
    }
}

