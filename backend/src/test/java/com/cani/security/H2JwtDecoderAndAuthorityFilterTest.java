package com.cani.security;

import com.cani.config.AuthProperties;
import com.cani.config.JwtConfig;
import com.cani.entity.User;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class H2JwtDecoderAndAuthorityFilterTest {

    @Mock
    private AuthProperties authProperties;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectProvider<UserRepository> userRepositoryProvider;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private ObjectMapper objectMapper;
    private JwtConfig jwtConfig;
    private UserAuthorityFilter userAuthorityFilter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jwtConfig = new JwtConfig(authProperties, objectMapper, userRepositoryProvider);
        userAuthorityFilter = new UserAuthorityFilter(userRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("H2JwtDecoder: Giải mã chính xác OAuth JWT chứa Base64URL payload với email và metadata")
    void h2JwtDecoder_decodesRealOAuthJwtPayload() {
        // Arrange
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"ES256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadJson = "{\"sub\":\"12345678-1234-1234-1234-123456789abc\",\"email\":\"eo_real@example.com\",\"user_metadata\":{\"full_name\":\"Trần Văn Ban Tổ Chức\"}}";
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature = "mock-signature";
        String rawJwt = header + "." + payload + "." + signature;

        JwtDecoder decoder = jwtConfig.h2JwtDecoder();

        // Act
        Jwt decoded = decoder.decode(rawJwt);

        // Assert
        assertThat(decoded).isNotNull();
        assertThat(decoded.getSubject()).isEqualTo("12345678-1234-1234-1234-123456789abc");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("eo_real@example.com");
        Map<String, Object> metadata = decoded.getClaim("user_metadata");
        assertThat(metadata).isNotNull();
        assertThat(metadata.get("full_name")).isEqualTo("Trần Văn Ban Tổ Chức");
    }

    @Test
    @DisplayName("H2JwtDecoder: Trích xuất email từ user_metadata khi payload root không có trường email")
    void h2JwtDecoder_extractsEmailFromUserMetadata() {
        // Arrange
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String payloadJson = "{\"sub\":\"12345678-1234-1234-1234-123456789abc\",\"user_metadata\":{\"email\":\"from_meta@example.com\",\"full_name\":\"Meta User\"}}";
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String rawJwt = header + "." + payload + ".sig";

        JwtDecoder decoder = jwtConfig.h2JwtDecoder();

        // Act
        Jwt decoded = decoder.decode(rawJwt);

        // Assert
        assertThat(decoded.getClaimAsString("email")).isEqualTo("from_meta@example.com");
    }

    @Test
    @DisplayName("H2JwtDecoder: dev-token-<userId> tra cứu email tương ứng từ UserRepository")
    void h2JwtDecoder_devTokenWithUserId_looksUpEmail() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String token = "dev-token-" + userId;
        User user = User.builder()
                .userId(userId)
                .email("eo@cani.vn")
                .fullName("Ban Tổ Chức Sài Gòn")
                .role(UserRole.EVENT_ORGANIZER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepositoryProvider.getIfAvailable()).thenReturn(userRepository);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        JwtDecoder decoder = jwtConfig.h2JwtDecoder();

        // Act
        Jwt decoded = decoder.decode(token);

        // Assert
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("email")).isEqualTo("eo@cani.vn");
    }

    @Test
    @DisplayName("UserAuthorityFilter: Fallback tìm user theo email khi subject UUID của OAuth khác với DB UUID")
    void userAuthorityFilter_fallbacksToEmail_whenSubjectUuidNotFound() throws Exception {
        // Arrange: Supabase OAuth tạo ra UUID ngẫu nhiên khác với seeded UUID trong database
        UUID oauthSubUuid = UUID.randomUUID();
        UUID dbSeededUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");

        User dbUser = User.builder()
                .userId(dbSeededUuid)
                .email("eo@cani.vn")
                .fullName("Bà Trần Thị Ngọc (EO Sài Gòn Star)")
                .role(UserRole.EVENT_ORGANIZER)
                .status(UserStatus.ACTIVE)
                .build();

        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getSubject()).thenReturn(oauthSubUuid.toString());
        when(mockJwt.getClaimAsString("email")).thenReturn("eo@cani.vn");

        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(mockJwt);
        SecurityContextHolder.getContext().setAuthentication(jwtAuth);

        // findById với OAuth UUID trả về rỗng, nhưng findByEmail tìm thấy user trong DB
        when(userRepository.findById(oauthSubUuid)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("eo@cani.vn")).thenReturn(Optional.of(dbUser));

        // Act
        userAuthorityFilter.doFilter(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        var currentAuth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(currentAuth).isNotNull();
        assertThat(currentAuth.getPrincipal()).isEqualTo(dbUser);
        assertThat(currentAuth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_EVENT_ORGANIZER");
    }
}

