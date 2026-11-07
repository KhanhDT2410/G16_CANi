package com.cani.service;

import com.cani.client.SupabaseAuthClient;
import com.cani.dto.auth.LoginRequest;
import com.cani.entity.User;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.AppException;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm chứng cơ chế cấp token khi Supabase GoTrue không khả dụng.
 *
 * Rủi ro bị vá: fallback trước đây cấp {@code dev-token-<uuid>} (bypass xác thực JWT) cho
 * MỌI profile, kể cả {@code local} (Supabase thật). Sau khi vá, fallback chỉ được phép ở
 * profile {@code h2}; profile khác phải trả lỗi 503 thay vì cấp token giả.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceDevTokenTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private SupabaseAuthClient supabaseAuthClient;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private EmailOtpService emailOtpService;

    @Mock
    private Environment environment;

    @InjectMocks
    private AuthService authService;

    private User existingUser() {
        return User.builder()
                .userId(UUID.randomUUID())
                .email("cc@cani.vn")
                .fullName("Nhân viên CSKH")
                .role(UserRole.CUSTOMER_CARE)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Profile h2 — Supabase lỗi thì vẫn cấp được dev-token để chạy offline")
    void login_h2Profile_grantsDevTokenWhenSupabaseUnavailable() throws Exception {
        User user = existingUser();
        when(supabaseAuthClient.signInWithPassword(anyString(), anyString()))
                .thenThrow(new RuntimeException("Supabase unreachable"));
        when(environment.acceptsProfiles("h2")).thenReturn(true);
        when(userRepository.findByEmail("cc@cani.vn")).thenReturn(Optional.of(user));
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));

        var response = authService.login(new LoginRequest("cc@cani.vn", "bat-ky-password"));

        assertEquals("dev-token-" + user.getUserId(), response.getToken());
        assertTrue(response.getRefreshToken().startsWith("dev-refresh-"));
    }

    @Test
    @DisplayName("Profile không phải h2 — Supabase lỗi thì trả 503, KHÔNG cấp dev-token")
    void login_nonH2Profile_throws503InsteadOfDevToken() throws Exception {
        when(supabaseAuthClient.signInWithPassword(anyString(), anyString()))
                .thenThrow(new RuntimeException("Supabase unreachable"));
        when(environment.acceptsProfiles("h2")).thenReturn(false);

        AppException exception = assertThrows(AppException.class,
                () -> authService.login(new LoginRequest("cc@cani.vn", "bat-ky-password")));

        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Profile không phải h2 — email không tồn tại vẫn trả 503 chứ không lộ thông tin tài khoản")
    void login_nonH2Profile_doesNotRevealAccountExistence() throws Exception {
        when(supabaseAuthClient.signInWithPassword(anyString(), anyString()))
                .thenThrow(new RuntimeException("Supabase unreachable"));
        when(environment.acceptsProfiles("h2")).thenReturn(false);

        AppException exception = assertThrows(AppException.class,
                () -> authService.login(new LoginRequest("khong-ton-tai@cani.vn", "mat-khau")));

        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(userRepository, never()).findByEmail(anyString());
    }
}
