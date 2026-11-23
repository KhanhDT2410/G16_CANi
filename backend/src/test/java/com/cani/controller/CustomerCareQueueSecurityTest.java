package com.cani.controller;

import com.cani.config.SecurityConfig;
import com.cani.entity.User;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.UserRepository;
import com.cani.security.RestAccessDeniedHandler;
import com.cani.security.RestAuthenticationEntryPoint;
import com.cani.security.UserAuthorityFilter;
import com.cani.service.EventOrganizerEkycService;
import com.cani.service.SkillVerificationService;
import com.cani.service.WorkerEkycService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        WorkerEkycController.class,
        EventOrganizerEkycController.class,
        SkillVerificationController.class
})
@Import({
        SecurityConfig.class,
        UserAuthorityFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class CustomerCareQueueSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private WorkerEkycService workerEkycService;

    @MockitoBean
    private EventOrganizerEkycService eoEkycService;

    @MockitoBean
    private SkillVerificationService skillVerificationService;

    private User adminUser;
    private User ccUser;
    private User workerUser;
    private User eoUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .userId(UUID.randomUUID())
                .email("admin@cani.vn")
                .fullName("Admin Hệ thống")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        ccUser = User.builder()
                .userId(UUID.randomUUID())
                .email("cc@cani.vn")
                .fullName("Nhân viên CSKH")
                .role(UserRole.CUSTOMER_CARE)
                .status(UserStatus.ACTIVE)
                .build();

        workerUser = User.builder()
                .userId(UUID.randomUUID())
                .email("worker@cani.vn")
                .fullName("Lao động Worker")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();

        eoUser = User.builder()
                .userId(UUID.randomUUID())
                .email("eo@cani.vn")
                .fullName("Ban tổ chức EO")
                .role(UserRole.EVENT_ORGANIZER)
                .status(UserStatus.ACTIVE)
                .build();

        when(skillVerificationService.getAllVerificationsForAdmin(any())).thenReturn(Collections.emptyList());
    }

    private UsernamePasswordAuthenticationToken createAuthToken(User user) {
        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    @Test
    @DisplayName("CUSTOMER_CARE đọc được lịch sử chứng chỉ kỹ năng (200 OK); WORKER & EO bị chặn 403")
    void historicalSkillAuditReadAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/workers/skills/admin/all")
                        .with(authentication(createAuthToken(ccUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/workers/skills/admin/all")
                        .with(authentication(createAuthToken(workerUser))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/workers/skills/admin/all")
                        .with(authentication(createAuthToken(eoUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Người dùng chưa đăng nhập bị chặn 401 Unauthorized trên endpoint quản trị")
    void anonymous_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/workers/skills/admin/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Legal Hold chỉ cho phép ADMIN; CUSTOMER_CARE và WORKER bị chặn 403 Forbidden")
    void legalHold_AdminOnly() throws Exception {
        UUID verificationId = UUID.randomUUID();
        when(workerEkycService.updateLegalHold(eq(adminUser.getUserId()), eq(verificationId), anyBoolean(), any()))
                .thenReturn(com.cani.dto.ekyc.EkycVerificationResultDto.builder()
                        .verificationId(verificationId)
                        .legalHold(true)
                        .build());

        mockMvc.perform(patch("/api/v1/admin/ekyc/" + verificationId + "/legal-hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalHold\":true,\"reason\":\"Đối soát tranh chấp\"}")
                        .with(authentication(createAuthToken(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(patch("/api/v1/admin/ekyc/" + verificationId + "/legal-hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalHold\":true}")
                        .with(authentication(createAuthToken(ccUser))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/admin/ekyc/" + verificationId + "/legal-hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalHold\":true}")
                        .with(authentication(createAuthToken(workerUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Các endpoint duyệt thủ công của Admin/CSKH cho eKYC & Skill đã bị gỡ bỏ khỏi API")
    void removedManualReviewEndpointsAreNoLongerMapped() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/admin/ekyc/" + id + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}")
                        .with(authentication(createAuthToken(adminUser))))
                .andExpect(status().is5xxServerError()); // NoResourceFoundException caught by GlobalExceptionHandler when handler does not exist

        mockMvc.perform(post("/api/v1/workers/skills/admin/" + id + "/review")
                        .param("approved", "true")
                        .with(authentication(createAuthToken(adminUser))))
                .andExpect(status().is5xxServerError());
    }
}
