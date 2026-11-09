package com.cani.controller;

import com.cani.config.SecurityConfig;
import com.cani.dto.ekyc.EkycVerificationResultDto;
import com.cani.dto.ekyc.EoEkycVerificationResultDto;
import com.cani.entity.User;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.UserRepository;
import com.cani.security.RestAccessDeniedHandler;
import com.cani.security.RestAuthenticationEntryPoint;
import com.cani.security.UserAuthorityFilter;
import com.cani.service.EventOrganizerEkycService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng vá IDOR (BOLA) cho 2 controller eKYC.
 *
 * Trước khi vá: các endpoint này nhận {@code userId} từ query-param và ưu tiên giá trị đó
 * ({@code resolveUserId(authentication, explicitUserId)}), nên người dùng A có thể truy vấn
 * được trạng thái eKYC của người dùng B chỉ bằng cách thêm {@code ?userId=<uuid của B>}.
 *
 * Sau khi vá: userId luôn lấy từ {@code Authentication.getPrincipal()} (đã map sang entity User
 * bởi {@code UserAuthorityFilter}), tham số {@code userId} từ request bị bỏ qua hoàn toàn.
 */
@WebMvcTest(controllers = {
        WorkerEkycController.class,
        EventOrganizerEkycController.class
})
@Import({
        SecurityConfig.class,
        UserAuthorityFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class EkycUserIdParamIgnoredTest {

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

    private User callerUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        callerUser = User.builder()
                .userId(UUID.randomUUID())
                .email("caller@cani.vn")
                .fullName("Người gọi")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();

        otherUser = User.builder()
                .userId(UUID.randomUUID())
                .email("other@cani.vn")
                .fullName("Người khác")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();

        when(workerEkycService.getMyEkycStatus(callerUser.getUserId()))
                .thenReturn(EkycVerificationResultDto.builder()
                        .verificationId(UUID.randomUUID())
                        .status("APPROVED")
                        .build());

        when(eoEkycService.getMyEkycStatus(callerUser.getUserId()))
                .thenReturn(EoEkycVerificationResultDto.builder()
                        .verificationId(UUID.randomUUID())
                        .status("APPROVED")
                        .build());
    }

    private UsernamePasswordAuthenticationToken createAuthToken(User user) {
        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    @Test
    @DisplayName("Tham số userId trong query bị bỏ qua — trạng thái eKYC Worker trả về của chính người gọi")
    void workerEkycStatus_ignoresUserIdQueryParam() throws Exception {
        mockMvc.perform(get("/api/v1/workers/ekyc/status")
                        .param("userId", otherUser.getUserId().toString())
                        .with(authentication(createAuthToken(callerUser))))
                .andExpect(status().isOk());

        verify(workerEkycService).getMyEkycStatus(callerUser.getUserId());
        verify(workerEkycService, never()).getMyEkycStatus(otherUser.getUserId());
    }

    @Test
    @DisplayName("Tham số userId trong query bị bỏ qua — trạng thái eKYC doanh nghiệp trả về của chính người gọi")
    void eoEkycStatus_ignoresUserIdQueryParam() throws Exception {
        mockMvc.perform(get("/api/v1/eo/ekyc/status")
                        .param("userId", otherUser.getUserId().toString())
                        .with(authentication(createAuthToken(callerUser))))
                .andExpect(status().isOk());

        verify(eoEkycService).getMyEkycStatus(callerUser.getUserId());
        verify(eoEkycService, never()).getMyEkycStatus(otherUser.getUserId());
    }

    @Test
    @DisplayName("Không có token — truy vấn trạng thái eKYC Worker bị từ chối (400 Bad Request)")
    void workerEkycStatus_rejectedWithoutToken() throws Exception {
        // Route /api/v1/workers/ekyc/** là permitAll() nên request tới được controller.
        // SecurityUtils ném BadRequestException vì không xác định được người dùng → 400.
        mockMvc.perform(get("/api/v1/workers/ekyc/status"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Không có token — truy vấn trạng thái eKYC doanh nghiệp bị từ chối (400 Bad Request)")
    void eoEkycStatus_rejectedWithoutToken() throws Exception {
        // Route /api/v1/eo/ekyc/** là permitAll() nên request tới được controller.
        // SecurityUtils ném BadRequestException vì không xác định được người dùng → 400.
        mockMvc.perform(get("/api/v1/eo/ekyc/status"))
                .andExpect(status().isBadRequest());
    }
}
