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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private User ccUser;
    private User workerUser;
    private User eoUser;

    @BeforeEach
    void setUp() {
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

        when(workerEkycService.getPendingEkycQueue()).thenReturn(Collections.emptyList());
        when(eoEkycService.getPendingEkycQueue()).thenReturn(Collections.emptyList());
        when(skillVerificationService.getPendingVerificationQueue()).thenReturn(Collections.emptyList());
    }

    private UsernamePasswordAuthenticationToken createAuthToken(User user) {
        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    @Test
    @DisplayName("CUSTOMER_CARE truy cập hàng đợi eKYC Worker thành công (200 OK)")
    void customerCare_canAccessWorkerEkycQueue() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ekyc/queue")
                        .with(authentication(createAuthToken(ccUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("CUSTOMER_CARE truy cập hàng đợi eKYC Doanh nghiệp thành công (200 OK)")
    void customerCare_canAccessEoEkycQueue() throws Exception {
        mockMvc.perform(get("/api/v1/admin/eo-ekyc/queue")
                        .with(authentication(createAuthToken(ccUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("CUSTOMER_CARE truy cập hàng đợi Thẩm định kỹ năng thành công (200 OK)")
    void customerCare_canAccessSkillQueue() throws Exception {
        mockMvc.perform(get("/api/v1/workers/skills/admin/queue")
                        .with(authentication(createAuthToken(ccUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("WORKER bị chặn 403 Forbidden khi truy cập hàng đợi eKYC Worker")
    void worker_forbiddenFromWorkerEkycQueue() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ekyc/queue")
                        .with(authentication(createAuthToken(workerUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WORKER bị chặn 403 Forbidden khi truy cập hàng đợi eKYC Doanh nghiệp")
    void worker_forbiddenFromEoEkycQueue() throws Exception {
        mockMvc.perform(get("/api/v1/admin/eo-ekyc/queue")
                        .with(authentication(createAuthToken(workerUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WORKER bị chặn 403 Forbidden khi truy cập hàng đợi Thẩm định kỹ năng")
    void worker_forbiddenFromSkillQueue() throws Exception {
        mockMvc.perform(get("/api/v1/workers/skills/admin/queue")
                        .with(authentication(createAuthToken(workerUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("EVENT_ORGANIZER bị chặn 403 Forbidden khi truy cập hàng đợi eKYC Worker")
    void eo_forbiddenFromWorkerEkycQueue() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ekyc/queue")
                        .with(authentication(createAuthToken(eoUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("EVENT_ORGANIZER bị chặn 403 Forbidden khi truy cập hàng đợi Thẩm định kỹ năng")
    void eo_forbiddenFromSkillQueue() throws Exception {
        mockMvc.perform(get("/api/v1/workers/skills/admin/queue")
                        .with(authentication(createAuthToken(eoUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Người dùng chưa đăng nhập bị chặn 401 Unauthorized")
    void anonymous_unauthorizedFromQueue() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ekyc/queue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CUSTOMER_CARE duyệt hồ sơ eKYC Worker thành công (200 OK)")
    void customerCare_canReviewWorkerEkyc() throws Exception {
        UUID verificationId = UUID.randomUUID();
        when(workerEkycService.adminReviewEkyc(eq(ccUser.getUserId()), eq(verificationId), any()))
                .thenReturn(com.cani.dto.ekyc.EkycVerificationResultDto.builder()
                        .verificationId(verificationId)
                        .status("APPROVED")
                        .build());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/ekyc/" + verificationId + "/review")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"reviewNotes\":\"Duyệt hợp lệ\"}")
                        .with(authentication(createAuthToken(ccUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("CUSTOMER_CARE duyệt chứng chỉ kỹ năng thành công (200 OK)")
    void customerCare_canReviewSkill() throws Exception {
        UUID skillRequestId = UUID.randomUUID();
        when(skillVerificationService.adminReview(eq(skillRequestId), eq(ccUser.getUserId()), eq(true), any()))
                .thenReturn(com.cani.dto.skill.SkillVerificationResponseDto.builder()
                        .id(skillRequestId)
                        .status(com.cani.entity.enums.SkillVerificationStatus.APPROVED)
                        .spAwarded(25)
                        .build());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/workers/skills/admin/" + skillRequestId + "/review")
                        .param("approved", "true")
                        .param("reviewNotes", "Chứng chỉ hợp lệ")
                        .with(authentication(createAuthToken(ccUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("WORKER bị chặn 403 Forbidden khi duyệt hồ sơ eKYC Worker")
    void worker_forbiddenFromReviewingWorkerEkyc() throws Exception {
        UUID verificationId = UUID.randomUUID();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/ekyc/" + verificationId + "/review")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"reviewNotes\":\"Duyệt hợp lệ\"}")
                        .with(authentication(createAuthToken(workerUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WORKER bị chặn 403 Forbidden khi duyệt chứng chỉ kỹ năng")
    void worker_forbiddenFromReviewingSkill() throws Exception {
        UUID skillRequestId = UUID.randomUUID();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/workers/skills/admin/" + skillRequestId + "/review")
                        .param("approved", "true")
                        .with(authentication(createAuthToken(workerUser))))
                .andExpect(status().isForbidden());
    }
}
