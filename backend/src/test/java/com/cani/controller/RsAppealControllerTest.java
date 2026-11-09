package com.cani.controller;

import com.cani.config.SecurityConfig;
import com.cani.dto.admin.appeal.ReviewRsAppealRequestDto;
import com.cani.dto.admin.appeal.RsAppealResponseDto;
import com.cani.dto.worker.appeal.SubmitRsAppealRequestDto;
import com.cani.entity.User;
import com.cani.entity.enums.RsAppealStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.UserRepository;
import com.cani.security.RestAccessDeniedHandler;
import com.cani.security.RestAuthenticationEntryPoint;
import com.cani.security.UserAuthorityFilter;
import com.cani.service.RsAppealService;
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

@WebMvcTest({WorkerAppealController.class, AdminAppealController.class})
@Import({
        SecurityConfig.class,
        UserAuthorityFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@DisplayName("RsAppealController — Kiểm thử API Kháng cáo điểm uy tín Worker & Admin/CC Review (UC-94)")
class RsAppealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RsAppealService rsAppealService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UsernamePasswordAuthenticationToken mockAuth(UserRole role) {
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email(role.name().toLowerCase() + "@cani.vn")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
        return new UsernamePasswordAuthenticationToken(
                user,
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    @Test
    @DisplayName("POST /api/v1/workers/appeals — Worker nộp kháng cáo thành công trả về 200 OK")
    void shouldSubmitAppealAsWorker() throws Exception {
        SubmitRsAppealRequestDto request = SubmitRsAppealRequestDto.builder()
                .reliabilityLogId(UUID.randomUUID())
                .reasonCode("FORCE_MAJEURE")
                .reasonDetail("Bị ốm đột xuất phải cấp cứu tại bệnh viện")
                .evidence("https://cani.vn/evidence.pdf")
                .build();

        RsAppealResponseDto response = RsAppealResponseDto.builder()
                .id(UUID.randomUUID())
                .status(RsAppealStatus.SUBMITTED)
                .penaltyAmount(25)
                .build();

        when(rsAppealService.submitAppeal(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/workers/appeals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(mockAuth(UserRole.WORKER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/appeals — Customer Care truy cập hàng đợi kháng cáo thành công 200 OK")
    void shouldAllowCustomerCareToAccessQueue() throws Exception {
        RsAppealResponseDto item = RsAppealResponseDto.builder()
                .id(UUID.randomUUID())
                .status(RsAppealStatus.SUBMITTED)
                .penaltyAmount(20)
                .build();

        when(rsAppealService.getAdminAppeals(any(), any()))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/v1/admin/appeals")
                        .with(authentication(mockAuth(UserRole.CUSTOMER_CARE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].penaltyAmount").value(20));
    }

    @Test
    @DisplayName("POST /api/v1/admin/appeals/{id}/review — Customer Care duyệt kháng cáo thành công 200 OK")
    void shouldAllowCustomerCareToReviewAppeal() throws Exception {
        UUID appealId = UUID.randomUUID();
        ReviewRsAppealRequestDto reviewReq = ReviewRsAppealRequestDto.builder()
                .approved(true)
                .decisionNote("Hồ sơ bệnh viện hợp lệ, phê duyệt khôi phục RS")
                .build();

        RsAppealResponseDto res = RsAppealResponseDto.builder()
                .id(appealId)
                .status(RsAppealStatus.APPROVED)
                .decisionNote("Hồ sơ bệnh viện hợp lệ, phê duyệt khôi phục RS")
                .build();

        when(rsAppealService.reviewAppeal(any(), eq(appealId), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/admin/appeals/" + appealId + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq))
                        .with(authentication(mockAuth(UserRole.CUSTOMER_CARE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/appeals — Worker truy cập hàng đợi admin bị chặn 403 Forbidden")
    void shouldReturnForbiddenForWorkerOnAdminQueue() throws Exception {
        mockMvc.perform(get("/api/v1/admin/appeals")
                        .with(authentication(mockAuth(UserRole.WORKER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/appeals — Người dùng chưa đăng nhập bị chặn 401 Unauthorized")
    void shouldReturnUnauthorizedForAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/admin/appeals"))
                .andExpect(status().isUnauthorized());
    }
}

