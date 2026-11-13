package com.cani.service.auth;

import com.cani.service.*;

import com.cani.client.SupabaseAuthClient;
import com.cani.client.dto.SupabaseSession;
import com.cani.client.dto.SupabaseUser;
import com.cani.dto.auth.*;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exhaustive Unit Test Suite for AuthService conforming to IEEE 829 / ISO 29119 and Report-5.1_Unit Test.xlsx standard.
 * Covers 29 Test Cases (10 Normal, 13 Abnormal, 6 Boundary) across all 8 service methods using AAA pattern.
 *
 * Traceability:
 * - Module AuthService (Login/OAuth2/Session): 17 TCs (hungpd) - UTC008-022, UTC028-029
 * - Module AuthService (Register/Password): 12 TCs (minhtb) - UTC001-007, UTC023-027
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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

    // =========================================================================
    // SECTION 1: register(RegisterRequest request) - 7 Test Cases (minhtb)
    // =========================================================================
    @Nested
    @DisplayName("1. register(RegisterRequest request)")
    class RegisterTests {

        @Test
        @DisplayName("UTC001: register - Đăng ký tài khoản Worker thành công (minhtb)")
        void UTC001_register_WorkerSuccess_ReturnsUserDtoAndProvisionsWorker() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .email("worker1@cani.vn")
                    .phone("0912345678")
                    .fullName("Nguyen Van A")
                    .password("SecretPassword123")
                    .accountType(AccountType.WORKER)
                    .otpCode("123456")
                    .build();

            doNothing().when(emailOtpService).verifyOtp(request.getEmail(), request.getOtpCode(), "REGISTER");
            when(userRepository.existsByEmail("worker1@cani.vn")).thenReturn(false);
            when(userRepository.existsByPhone("0912345678")).thenReturn(false);

            UUID userId = UUID.randomUUID();
            SupabaseUser supabaseUser = SupabaseUser.builder().id(userId).email("worker1@cani.vn").build();
            when(supabaseAuthClient.createUser(eq("worker1@cani.vn"), any(), eq("Nguyen Van A"), eq("0912345678"), eq("WORKER"), any()))
                    .thenReturn(supabaseUser);

            when(userRepository.findById(userId)).thenReturn(Optional.empty());
            when(userRepository.findByEmail("worker1@cani.vn")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());

            // Act
            UserDto result = authService.register(request);

            // Assert
            assertNotNull(result);
            assertEquals("worker1@cani.vn", result.getEmail());
            assertEquals(UserRole.WORKER, result.getRole());
            assertEquals(UserStatus.ACTIVE, result.getStatus());
            assertTrue(result.getEmailVerified());
            verify(workerRepository).save(any(Worker.class));
            verify(eventOrganizerRepository, never()).save(any());
        }

        @Test
        @DisplayName("UTC002: register - Đăng ký tài khoản Event Organizer thành công (minhtb)")
        void UTC002_register_EventOrganizerSuccess_ReturnsUserDtoAndProvisionsEo() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .email("eo1@agency.vn")
                    .fullName("Event Vietnam Co.")
                    .accountType(AccountType.EVENT_ORGANIZER)
                    .companyName("Event Pro Ltd")
                    .otpCode("123456")
                    .password("SecretPassword123")
                    .build();

            doNothing().when(emailOtpService).verifyOtp(request.getEmail(), request.getOtpCode(), "REGISTER");
            when(userRepository.existsByEmail("eo1@agency.vn")).thenReturn(false);

            UUID userId = UUID.randomUUID();
            SupabaseUser supabaseUser = SupabaseUser.builder().id(userId).email("eo1@agency.vn").build();
            when(supabaseAuthClient.createUser(eq("eo1@agency.vn"), any(), eq("Event Vietnam Co."), isNull(), eq("EVENT_ORGANIZER"), eq("Event Pro Ltd")))
                    .thenReturn(supabaseUser);

            when(userRepository.findById(userId)).thenReturn(Optional.empty());
            when(userRepository.findByEmail("eo1@agency.vn")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());

            // Act
            UserDto result = authService.register(request);

            // Assert
            assertNotNull(result);
            assertEquals("eo1@agency.vn", result.getEmail());
            assertEquals(UserRole.EVENT_ORGANIZER, result.getRole());
            assertEquals(UserStatus.ACTIVE, result.getStatus());
            verify(eventOrganizerRepository).save(argThat(eo -> "Event Pro Ltd".equals(eo.getCompanyName())));
            verify(workerRepository, never()).save(any());
        }

        @Test
        @DisplayName("UTC003: register - Ném BadRequestException khi mã OTP không hợp lệ hoặc hết hạn (minhtb)")
        void UTC003_register_InvalidOrExpiredOtp_ThrowsBadRequestException() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .email("user@test.vn")
                    .otpCode("000000")
                    .build();

            doThrow(new BadRequestException("Mã OTP không hợp lệ hoặc đã hết hạn"))
                    .when(emailOtpService).verifyOtp(request.getEmail(), request.getOtpCode(), "REGISTER");

            // Act & Assert
            BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(request));
            assertEquals("Mã OTP không hợp lệ hoặc đã hết hạn", ex.getMessage());
            verify(userRepository, never()).save(any());
            verify(supabaseAuthClient, never()).createUser(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("UTC004: register - Ném BadRequestException khi email đã được sử dụng (minhtb)")
        void UTC004_register_EmailAlreadyExists_ThrowsBadRequestException() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .email("exist@test.vn")
                    .otpCode("123456")
                    .build();

            doNothing().when(emailOtpService).verifyOtp(request.getEmail(), request.getOtpCode(), "REGISTER");
            when(userRepository.existsByEmail("exist@test.vn")).thenReturn(true);

            // Act & Assert
            BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(request));
            assertEquals("Email này đã được sử dụng", ex.getMessage());
            verify(supabaseAuthClient, never()).createUser(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("UTC005: register - Ném BadRequestException khi số điện thoại đã tồn tại (minhtb)")
        void UTC005_register_PhoneAlreadyExists_ThrowsBadRequestException() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .email("new@test.vn")
                    .phone("0909999999")
                    .otpCode("123456")
                    .build();

            doNothing().when(emailOtpService).verifyOtp(request.getEmail(), request.getOtpCode(), "REGISTER");
            when(userRepository.existsByEmail("new@test.vn")).thenReturn(false);
            when(userRepository.existsByPhone("0909999999")).thenReturn(true);

            // Act & Assert
            BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(request));
            assertEquals("Số điện thoại này đã được sử dụng", ex.getMessage());
            verify(supabaseAuthClient, never()).createUser(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("UTC006: register - Số điện thoại null vẫn đăng ký Worker bình thường (minhtb)")
        void UTC006_register_NullPhone_SuccessWithoutValidationConstraint() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .email("nophone@cani.vn")
                    .phone(null)
                    .fullName("Tran Tho Moi")
                    .accountType(AccountType.WORKER)
                    .otpCode("123456")
                    .build();

            doNothing().when(emailOtpService).verifyOtp(request.getEmail(), request.getOtpCode(), "REGISTER");
            when(userRepository.existsByEmail("nophone@cani.vn")).thenReturn(false);

            UUID userId = UUID.randomUUID();
            when(supabaseAuthClient.createUser(eq("nophone@cani.vn"), any(), eq("Tran Tho Moi"), isNull(), eq("WORKER"), any()))
                    .thenReturn(SupabaseUser.builder().id(userId).email("nophone@cani.vn").build());

            when(userRepository.findById(userId)).thenReturn(Optional.empty());
            when(userRepository.findByEmail("nophone@cani.vn")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());

            // Act
            UserDto result = authService.register(request);

            // Assert
            assertNotNull(result);
            assertNull(result.getPhone());
            assertEquals(UserStatus.ACTIVE, result.getStatus());
            verify(userRepository, never()).existsByPhone(any());
        }

        @Test
        @DisplayName("UTC007: register - Supabase GoTrue timeout kích hoạt local fallback tạo UUID từ email bytes (BUG-AUTH-001) (minhtb)")
        void UTC007_register_SupabaseTimeout_FallbackGeneratesDeterministicUuid() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .email("fallback@cani.vn")
                    .password("Pass123")
                    .fullName("Le Cuc Bo")
                    .accountType(AccountType.WORKER)
                    .otpCode("123456")
                    .build();

            doNothing().when(emailOtpService).verifyOtp(request.getEmail(), request.getOtpCode(), "REGISTER");
            when(userRepository.existsByEmail("fallback@cani.vn")).thenReturn(false);
            when(supabaseAuthClient.createUser(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Supabase network timeout"));

            UUID expectedUuid = UUID.nameUUIDFromBytes("fallback@cani.vn".toLowerCase().getBytes());
            when(userRepository.findById(expectedUuid)).thenReturn(Optional.empty());
            when(userRepository.findByEmail("fallback@cani.vn")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(workerRepository.findByUser_UserId(expectedUuid)).thenReturn(Optional.empty());

            // Act
            UserDto result = authService.register(request);

            // Assert
            assertNotNull(result);
            assertEquals(expectedUuid, result.getId());
            assertEquals("fallback@cani.vn", result.getEmail());
            verify(workerRepository).save(any(Worker.class));
        }
    }

    // =========================================================================
    // SECTION 2: login(LoginRequest request) - 6 Test Cases (hungpd)
    // =========================================================================
    @Nested
    @DisplayName("2. login(LoginRequest request)")
    class LoginTests {

        @Test
        @DisplayName("UTC008: login - Đăng nhập tài khoản hợp lệ thành công và cập nhật lastLoginAt (hungpd)")
        void UTC008_login_ValidCredentials_ReturnsLoginResponseAndUpdatesLastLogin() {
            // Arrange
            LoginRequest request = LoginRequest.builder()
                    .email("active@cani.vn")
                    .password("SecretPassword123")
                    .build();

            UUID userId = UUID.randomUUID();
            SupabaseSession session = SupabaseSession.builder()
                    .accessToken("access-token-123")
                    .refreshToken("refresh-token-123")
                    .expiresIn(3600L)
                    .user(SupabaseUser.builder().id(userId).email("active@cani.vn").build())
                    .build();

            when(supabaseAuthClient.signInWithPassword("active@cani.vn", "SecretPassword123")).thenReturn(session);

            User user = User.builder()
                    .userId(userId)
                    .email("active@cani.vn")
                    .role(UserRole.WORKER)
                    .status(UserStatus.ACTIVE)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            LoginResponse response = authService.login(request);

            // Assert
            assertNotNull(response);
            assertEquals("access-token-123", response.getToken());
            assertEquals("refresh-token-123", response.getRefreshToken());
            assertEquals(3600L, response.getExpiresIn());
            assertEquals("active@cani.vn", response.getUser().getEmail());
            assertNotNull(user.getLastLoginAt());
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("UTC009: login - Supabase lỗi ở môi trường non-H2 ném AppException 503 SERVICE_UNAVAILABLE (BUG-AUTH-002) (hungpd)")
        void UTC009_login_RemoteAuthOutageNonH2_ThrowsServiceUnavailableAppException() {
            // Arrange
            LoginRequest request = LoginRequest.builder()
                    .email("active@cani.vn")
                    .password("WrongPassword")
                    .build();

            when(supabaseAuthClient.signInWithPassword(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Supabase connection timeout"));
            when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

            // Act & Assert
            AppException ex = assertThrows(AppException.class, () -> authService.login(request));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
            assertTrue(ex.getMessage().contains("Dịch vụ xác thực tạm thời không khả dụng"));
        }

        @Test
        @DisplayName("UTC010: login - Tài khoản có trạng thái LOCKED bị từ chối đăng nhập (hungpd)")
        void UTC010_login_UserLocked_ThrowsBadRequestException() {
            // Arrange
            LoginRequest request = LoginRequest.builder()
                    .email("locked@cani.vn")
                    .password("CorrectPassword")
                    .build();

            UUID userId = UUID.randomUUID();
            SupabaseSession session = SupabaseSession.builder()
                    .accessToken("token")
                    .refreshToken("refresh")
                    .expiresIn(3600L)
                    .user(SupabaseUser.builder().id(userId).build())
                    .build();

            when(supabaseAuthClient.signInWithPassword("locked@cani.vn", "CorrectPassword")).thenReturn(session);

            User lockedUser = User.builder()
                    .userId(userId)
                    .email("locked@cani.vn")
                    .status(UserStatus.LOCKED)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(lockedUser));

            // Act & Assert
            BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.login(request));
            assertEquals("Tài khoản của bạn đã bị khóa hoặc tạm ngưng hoạt động", ex.getMessage());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("UTC011: login - Tài khoản có trạng thái SUSPENDED bị từ chối đăng nhập (hungpd)")
        void UTC011_login_UserSuspended_ThrowsBadRequestException() {
            // Arrange
            LoginRequest request = LoginRequest.builder()
                    .email("suspended@cani.vn")
                    .password("CorrectPassword")
                    .build();

            UUID userId = UUID.randomUUID();
            SupabaseSession session = SupabaseSession.builder()
                    .accessToken("token")
                    .refreshToken("refresh")
                    .expiresIn(3600L)
                    .user(SupabaseUser.builder().id(userId).build())
                    .build();

            when(supabaseAuthClient.signInWithPassword("suspended@cani.vn", "CorrectPassword")).thenReturn(session);

            User suspendedUser = User.builder()
                    .userId(userId)
                    .email("suspended@cani.vn")
                    .status(UserStatus.SUSPENDED)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));

            // Act & Assert
            BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.login(request));
            assertEquals("Tài khoản của bạn đã bị khóa hoặc tạm ngưng hoạt động", ex.getMessage());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("UTC012: login - Chuỗi email có khoảng trắng và chữ hoa được chuẩn hóa thành lowercase trimmed (hungpd)")
        void UTC012_login_InputEmailWithWhitespacesAndUppercase_NormalizesAndSucceeds() {
            // Arrange
            LoginRequest request = LoginRequest.builder()
                    .email("  Test@CANI.vn  ")
                    .password("SecretPassword123")
                    .build();

            UUID userId = UUID.randomUUID();
            SupabaseSession session = SupabaseSession.builder()
                    .accessToken("tok-123")
                    .refreshToken("ref-123")
                    .expiresIn(3600L)
                    .user(SupabaseUser.builder().id(userId).build())
                    .build();

            when(supabaseAuthClient.signInWithPassword("test@cani.vn", "SecretPassword123")).thenReturn(session);

            User user = User.builder()
                    .userId(userId)
                    .email("test@cani.vn")
                    .role(UserRole.WORKER)
                    .status(UserStatus.ACTIVE)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            LoginResponse response = authService.login(request);

            // Assert
            assertNotNull(response);
            assertEquals("test@cani.vn", response.getUser().getEmail());
            verify(supabaseAuthClient).signInWithPassword("test@cani.vn", "SecretPassword123");
        }

        @Test
        @DisplayName("UTC013: login - Supabase offline ở profile H2 kích hoạt dev-token fallback khi đúng mật khẩu seed (hungpd)")
        void UTC013_login_SupabaseOfflineH2Profile_ReturnsDevTokenFallback() {
            // Arrange
            LoginRequest request = LoginRequest.builder()
                    .email("local@cani.vn")
                    .password("Password123!")
                    .build();

            when(supabaseAuthClient.signInWithPassword(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Supabase unavailable"));
            when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

            UUID userId = UUID.randomUUID();
            User localUser = User.builder()
                    .userId(userId)
                    .email("local@cani.vn")
                    .role(UserRole.WORKER)
                    .status(UserStatus.ACTIVE)
                    .build();

            when(userRepository.findByEmail("local@cani.vn")).thenReturn(Optional.of(localUser));
            when(userRepository.findById(userId)).thenReturn(Optional.of(localUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            LoginResponse response = authService.login(request);

            // Assert
            assertNotNull(response);
            assertEquals("dev-token-" + userId, response.getToken());
            assertEquals("dev-refresh-" + userId, response.getRefreshToken());
            assertEquals(86400L, response.getExpiresIn());
            assertEquals("local@cani.vn", response.getUser().getEmail());
        }

        @Test
        @DisplayName("UTC013B: login - Supabase offline ở profile H2 từ chối khi sai mật khẩu (hungpd)")
        void UTC013B_login_SupabaseOfflineH2Profile_WrongPassword_ThrowsBadRequestException() {
            // Arrange
            LoginRequest request = LoginRequest.builder()
                    .email("local@cani.vn")
                    .password("wrong-pass")
                    .build();

            when(supabaseAuthClient.signInWithPassword(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Supabase unavailable"));
            when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

            // Act & Assert
            assertThrows(BadRequestException.class, () -> authService.login(request));
        }
    }

    // =========================================================================
    // SECTION 3: googleLogin(GoogleLoginRequest request) - 5 Test Cases (hungpd)
    // =========================================================================
    @Nested
    @DisplayName("3. googleLogin(GoogleLoginRequest request)")
    class GoogleLoginTests {

        @Test
        @DisplayName("UTC014: googleLogin - Người dùng đã tồn tại đăng nhập Google thành công và cập nhật lastLoginAt (hungpd)")
        void UTC014_googleLogin_ExistingUser_ReturnsLoginResponseAndUpdatesLastLogin() {
            // Arrange
            GoogleLoginRequest request = GoogleLoginRequest.builder()
                    .supabaseAccessToken("valid.google.jwt.token")
                    .build();

            UUID userId = UUID.randomUUID();
            Jwt jwt = mock(Jwt.class);
            when(jwtDecoder.decode("valid.google.jwt.token")).thenReturn(jwt);
            when(jwt.getClaimAsString("email")).thenReturn("googleuser@gmail.com");
            when(jwt.getSubject()).thenReturn(userId.toString());

            User user = User.builder()
                    .userId(userId)
                    .email("googleuser@gmail.com")
                    .role(UserRole.WORKER)
                    .status(UserStatus.ACTIVE)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            LoginResponse response = authService.googleLogin(request);

            // Assert
            assertNotNull(response);
            assertEquals("valid.google.jwt.token", response.getToken());
            assertEquals("googleuser@gmail.com", response.getUser().getEmail());
            assertNotNull(user.getLastLoginAt());
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("UTC015: googleLogin - Đăng ký người dùng mới qua Google với targetRole=EVENT_ORGANIZER (hungpd)")
        void UTC015_googleLogin_NewUserTargetRoleEo_ProvisionsUserAndEoProfile() {
            // Arrange
            GoogleLoginRequest request = GoogleLoginRequest.builder()
                    .supabaseAccessToken("valid.google.jwt")
                    .targetRole("EVENT_ORGANIZER")
                    .build();

            UUID userId = UUID.randomUUID();
            Jwt jwt = mock(Jwt.class);
            when(jwtDecoder.decode("valid.google.jwt")).thenReturn(jwt);
            when(jwt.getClaimAsString("email")).thenReturn("neweo@gmail.com");
            when(jwt.getSubject()).thenReturn(userId.toString());
            when(jwt.getClaim("user_metadata")).thenReturn(Map.of("full_name", "Event Organizer Corp"));

            when(userRepository.findById(userId)).thenReturn(Optional.empty());
            when(userRepository.findByEmail("neweo@gmail.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());
            when(eventOrganizerRepository.save(any(EventOrganizer.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            LoginResponse response = authService.googleLogin(request);

            // Assert
            assertNotNull(response);
            assertEquals("neweo@gmail.com", response.getUser().getEmail());
            assertEquals(UserRole.EVENT_ORGANIZER, response.getUser().getRole());
            verify(eventOrganizerRepository, atLeastOnce()).save(any(EventOrganizer.class));
            verify(workerRepository, never()).save(any());
        }

        @Test
        @DisplayName("UTC015B: googleLogin - Tài khoản mới bị trigger gán WORKER nhưng đăng nhập từ Web Portal (targetRole=EO) -> chuyển đổi an toàn sang EVENT_ORGANIZER")
        void UTC015B_googleLogin_TriggerAssignedWorker_SafelyTransitionsToEo() {
            // Arrange
            GoogleLoginRequest request = GoogleLoginRequest.builder()
                    .supabaseAccessToken("valid.google.jwt")
                    .targetRole("EVENT_ORGANIZER")
                    .build();

            UUID userId = UUID.randomUUID();
            Jwt jwt = mock(Jwt.class);
            when(jwtDecoder.decode("valid.google.jwt")).thenReturn(jwt);
            when(jwt.getClaimAsString("email")).thenReturn("neweo2@gmail.com");
            when(jwt.getSubject()).thenReturn(userId.toString());

            User triggerCreatedUser = User.builder()
                    .userId(userId)
                    .email("neweo2@gmail.com")
                    .fullName("New EO User")
                    .role(UserRole.WORKER)
                    .status(UserStatus.ACTIVE)
                    .build();

            Worker dummyWorker = Worker.builder()
                    .workerId(UUID.randomUUID())
                    .user(triggerCreatedUser)
                    .fullName("New EO User")
                    .cccdNumber(null)
                    .totalShiftsCompleted(0)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(triggerCreatedUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(dummyWorker));
            when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());
            when(eventOrganizerRepository.save(any(EventOrganizer.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            LoginResponse response = authService.googleLogin(request);

            // Assert
            assertNotNull(response);
            assertEquals("neweo2@gmail.com", response.getUser().getEmail());
            assertEquals(UserRole.EVENT_ORGANIZER, response.getUser().getRole());
            verify(workerRepository).delete(dummyWorker);
            verify(eventOrganizerRepository, atLeastOnce()).save(any(EventOrganizer.class));
        }

        @Test
        @DisplayName("UTC015C: googleLogin - Worker thực thụ (đã có CCCD/ca làm việc) đăng nhập từ Web Portal -> giữ nguyên vai trò WORKER, không bị leo quyền")
        void UTC015C_googleLogin_EstablishedWorker_RoleProtectedAgainstEscalation() {
            // Arrange
            GoogleLoginRequest request = GoogleLoginRequest.builder()
                    .supabaseAccessToken("valid.google.jwt")
                    .targetRole("EVENT_ORGANIZER")
                    .build();

            UUID userId = UUID.randomUUID();
            Jwt jwt = mock(Jwt.class);
            when(jwtDecoder.decode("valid.google.jwt")).thenReturn(jwt);
            when(jwt.getClaimAsString("email")).thenReturn("realworker@gmail.com");
            when(jwt.getSubject()).thenReturn(userId.toString());

            User realWorkerUser = User.builder()
                    .userId(userId)
                    .email("realworker@gmail.com")
                    .fullName("Real Worker")
                    .role(UserRole.WORKER)
                    .status(UserStatus.ACTIVE)
                    .build();

            Worker realWorker = Worker.builder()
                    .workerId(UUID.randomUUID())
                    .user(realWorkerUser)
                    .fullName("Real Worker")
                    .cccdNumber("012345678901")
                    .totalShiftsCompleted(5)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(realWorkerUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(realWorker));

            // Act
            LoginResponse response = authService.googleLogin(request);

            // Assert
            assertNotNull(response);
            assertEquals("realworker@gmail.com", response.getUser().getEmail());
            assertEquals(UserRole.WORKER, response.getUser().getRole());
            verify(workerRepository, never()).delete(any());
            verify(eventOrganizerRepository, never()).save(any());
        }

        @Test
        @DisplayName("UTC016: googleLogin - targetRole null an toàn fallback về vai trò mặc định WORKER (BUG-AUTH-003) (hungpd)")
        void UTC016_googleLogin_NewUserNullTargetRole_DefaultsToWorkerSafely() {
            // Arrange
            GoogleLoginRequest request = GoogleLoginRequest.builder()
                    .supabaseAccessToken("valid.jwt")
                    .targetRole(null)
                    .build();

            UUID userId = UUID.randomUUID();
            Jwt jwt = mock(Jwt.class);
            when(jwtDecoder.decode("valid.jwt")).thenReturn(jwt);
            when(jwt.getClaimAsString("email")).thenReturn("worker@gmail.com");
            when(jwt.getSubject()).thenReturn(userId.toString());
            when(jwt.getClaim("user_metadata")).thenReturn(null);

            when(userRepository.findById(userId)).thenReturn(Optional.empty());
            when(userRepository.findByEmail("worker@gmail.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());
            when(workerRepository.save(any(Worker.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            LoginResponse response = authService.googleLogin(request);

            // Assert
            assertNotNull(response);
            assertEquals("worker@gmail.com", response.getUser().getEmail());
            assertEquals(UserRole.WORKER, response.getUser().getRole());
            verify(workerRepository).save(any(Worker.class));
            verify(eventOrganizerRepository, never()).save(any());
        }

        @Test
        @DisplayName("UTC017: googleLogin - Chữ ký JWT không hợp lệ ném BadRequestException (hungpd)")
        void UTC017_googleLogin_JwtException_ThrowsBadRequestException() {
            // Arrange
            GoogleLoginRequest request = GoogleLoginRequest.builder()
                    .supabaseAccessToken("invalid.tampered.token")
                    .build();

            when(jwtDecoder.decode("invalid.tampered.token"))
                    .thenThrow(new BadJwtException("Signature verification failed"));

            // Act & Assert
            BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.googleLogin(request));
            assertEquals("Token xác thực Google không hợp lệ hoặc đã hết hạn", ex.getMessage());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("UTC018: googleLogin - Tài khoản Google bị khóa (LOCKED) bị từ chối đăng nhập (hungpd)")
        void UTC018_googleLogin_UserLocked_ThrowsBadRequestException() {
            // Arrange
            GoogleLoginRequest request = GoogleLoginRequest.builder()
                    .supabaseAccessToken("valid.jwt")
                    .build();

            UUID userId = UUID.randomUUID();
            Jwt jwt = mock(Jwt.class);
            when(jwtDecoder.decode("valid.jwt")).thenReturn(jwt);
            when(jwt.getClaimAsString("email")).thenReturn("locked@gmail.com");
            when(jwt.getSubject()).thenReturn(userId.toString());

            User lockedUser = User.builder()
                    .userId(userId)
                    .email("locked@gmail.com")
                    .role(UserRole.WORKER)
                    .status(UserStatus.LOCKED)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(lockedUser));

            // Act & Assert
            BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.googleLogin(request));
            assertEquals("Tài khoản của bạn đã bị khóa hoặc tạm ngưng hoạt động", ex.getMessage());
        }
    }

    // =========================================================================
    // SECTION 4: me(UUID userId) - 2 Test Cases (hungpd)
    // =========================================================================
    @Nested
    @DisplayName("4. me(UUID userId)")
    class MeTests {

        @Test
        @DisplayName("UTC019: me - Lấy thông tin người dùng hiện tại thành công (hungpd)")
        void UTC019_me_ExistingUser_ReturnsUserDto() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .userId(userId)
                    .email("me@cani.vn")
                    .fullName("Phạm Duy Hưng")
                    .phone("0987654321")
                    .role(UserRole.WORKER)
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // Act
            UserDto result = authService.me(userId);

            // Assert
            assertNotNull(result);
            assertEquals(userId, result.getId());
            assertEquals("me@cani.vn", result.getEmail());
            assertEquals("Phạm Duy Hưng", result.getFullName());
            assertEquals(UserRole.WORKER, result.getRole());
            assertEquals(UserStatus.ACTIVE, result.getStatus());
        }

        @Test
        @DisplayName("UTC020: me - Người dùng không tồn tại ném ResourceNotFoundException (hungpd)")
        void UTC020_me_NonExistentUser_ThrowsResourceNotFoundException() {
            // Arrange
            UUID nonExistentId = UUID.fromString("00000000-0000-0000-0000-000000000000");
            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> authService.me(nonExistentId));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertTrue(ex.getMessage().contains("Người dùng không tồn tại với id: '00000000-0000-0000-0000-000000000000'"));
        }
    }

    // =========================================================================
    // SECTION 5: verifyOtp(VerifyOtpRequest request) - 2 Test Cases (hungpd)
    // =========================================================================
    @Nested
    @DisplayName("5. verifyOtp(VerifyOtpRequest request)")
    class VerifyOtpTests {

        @Test
        @DisplayName("UTC021: verifyOtp - Xác thực OTP thành công cập nhật emailVerified thành true (hungpd)")
        void UTC021_verifyOtp_Success_SetsEmailVerifiedTrue() {
            // Arrange
            VerifyOtpRequest request = VerifyOtpRequest.builder()
                    .email("user@cani.vn")
                    .token("123456")
                    .build();

            doNothing().when(emailOtpService).verifyOtp("user@cani.vn", "123456", "REGISTER");

            User user = User.builder()
                    .email("user@cani.vn")
                    .emailVerified(false)
                    .build();

            when(userRepository.findByEmail("user@cani.vn")).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            authService.verifyOtp(request);

            // Assert
            assertTrue(user.getEmailVerified());
            verify(emailOtpService).verifyOtp("user@cani.vn", "123456", "REGISTER");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("UTC022: verifyOtp - OTP không hợp lệ ném BadRequestException và không thay đổi emailVerified (hungpd)")
        void UTC022_verifyOtp_InvalidOtp_ThrowsBadRequestExceptionAndDoesNotVerify() {
            // Arrange
            VerifyOtpRequest request = VerifyOtpRequest.builder()
                    .email("user@cani.vn")
                    .token("999999")
                    .build();

            doThrow(new BadRequestException("Mã OTP không hợp lệ"))
                    .when(emailOtpService).verifyOtp("user@cani.vn", "999999", "REGISTER");

            // Act & Assert
            BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.verifyOtp(request));
            assertEquals("Mã OTP không hợp lệ", ex.getMessage());
            verify(userRepository, never()).save(any());
        }
    }

    // =========================================================================
    // SECTION 6: resetPassword(ResetPasswordRequest request) - 2 Test Cases (minhtb)
    // =========================================================================
    @Nested
    @DisplayName("6. resetPassword(ResetPasswordRequest request)")
    class ResetPasswordTests {

        @Test
        @DisplayName("UTC023: resetPassword - Gửi OTP đặt lại mật khẩu thành công (minhtb)")
        void UTC023_resetPassword_ValidEmail_DispatchesOtp() {
            // Arrange
            ResetPasswordRequest request = ResetPasswordRequest.builder()
                    .email("forgot@cani.vn")
                    .build();

            // Act
            authService.resetPassword(request);

            // Assert
            verify(emailOtpService, times(1)).sendOtp("forgot@cani.vn", "RESET_PASSWORD");
        }

        @Test
        @DisplayName("UTC024: resetPassword - Chuỗi email có khoảng trắng và chữ hoa được chuẩn hóa trimmed lowercase (BUG-AUTH-004) (minhtb)")
        void UTC024_resetPassword_EmailWithWhitespaceAndUppercase_NormalizesAndDispatches() {
            // Arrange
            ResetPasswordRequest request = ResetPasswordRequest.builder()
                    .email("  Forgot@CANI.vn  ")
                    .build();

            // Act
            authService.resetPassword(request);

            // Assert
            verify(emailOtpService).sendOtp("forgot@cani.vn", "RESET_PASSWORD");
        }
    }

    // =========================================================================
    // SECTION 7: confirmResetPassword(ConfirmResetPasswordRequest request) - 3 Test Cases (minhtb)
    // =========================================================================
    @Nested
    @DisplayName("7. confirmResetPassword(ConfirmResetPasswordRequest request)")
    class ConfirmResetPasswordTests {

        @Test
        @DisplayName("UTC025: confirmResetPassword - Khôi phục mật khẩu thành công cập nhật mật khẩu mới lên Supabase (minhtb)")
        void UTC025_confirmResetPassword_Success_UpdatesPasswordInSupabase() {
            // Arrange
            ConfirmResetPasswordRequest request = ConfirmResetPasswordRequest.builder()
                    .email("forgot@cani.vn")
                    .otp("654321")
                    .newPassword("NewStrongPassword@2026")
                    .build();

            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .userId(userId)
                    .email("forgot@cani.vn")
                    .build();

            doNothing().when(emailOtpService).verifyOtp("forgot@cani.vn", "654321", "RESET_PASSWORD");
            when(userRepository.findByEmail("forgot@cani.vn")).thenReturn(Optional.of(user));

            // Act
            authService.confirmResetPassword(request);

            // Assert
            verify(emailOtpService).verifyOtp("forgot@cani.vn", "654321", "RESET_PASSWORD");
            verify(supabaseAuthClient).updateUserPassword(userId, "NewStrongPassword@2026");
        }

        @Test
        @DisplayName("UTC026: confirmResetPassword - OTP không hợp lệ ném BadRequestException và không gọi đổi mật khẩu (minhtb)")
        void UTC026_confirmResetPassword_InvalidOtp_ThrowsBadRequestExceptionAndDoesNotUpdate() {
            // Arrange
            ConfirmResetPasswordRequest request = ConfirmResetPasswordRequest.builder()
                    .email("forgot@cani.vn")
                    .otp("000000")
                    .newPassword("NewPassword123")
                    .build();

            doThrow(new BadRequestException("Mã OTP không hợp lệ hoặc đã hết hạn"))
                    .when(emailOtpService).verifyOtp(eq("forgot@cani.vn"), eq("000000"), eq("RESET_PASSWORD"));

            // Act & Assert
            BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.confirmResetPassword(request));
            assertEquals("Mã OTP không hợp lệ hoặc đã hết hạn", ex.getMessage());
            verify(supabaseAuthClient, never()).updateUserPassword(any(), any());
        }

        @Test
        @DisplayName("UTC027: confirmResetPassword - Email không tồn tại ném BadRequestException (minhtb)")
        void UTC027_confirmResetPassword_UserNotFound_ThrowsBadRequestException() {
            // Arrange
            ConfirmResetPasswordRequest request = ConfirmResetPasswordRequest.builder()
                    .email("ghost@cani.vn")
                    .otp("654321")
                    .newPassword("NewPassword123")
                    .build();

            doNothing().when(emailOtpService).verifyOtp("ghost@cani.vn", "654321", "RESET_PASSWORD");
            when(userRepository.findByEmail("ghost@cani.vn")).thenReturn(Optional.empty());

            // Act & Assert
            BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.confirmResetPassword(request));
            assertTrue(ex.getMessage().contains("Không tìm thấy tài khoản với email: ghost@cani.vn"));
            verify(supabaseAuthClient, never()).updateUserPassword(any(), any());
        }
    }

    // =========================================================================
    // SECTION 8: refreshToken(RefreshTokenRequest request) - 2 Test Cases (hungpd)
    // =========================================================================
    @Nested
    @DisplayName("8. refreshToken(RefreshTokenRequest request)")
    class RefreshTokenTests {

        @Test
        @DisplayName("UTC028: refreshToken - Làm mới access token thành công khi refresh token hợp lệ (hungpd)")
        void UTC028_refreshToken_ValidToken_ReturnsRefreshedLoginResponse() {
            // Arrange
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("valid.refresh.token")
                    .build();

            UUID userId = UUID.randomUUID();
            SupabaseSession session = SupabaseSession.builder()
                    .accessToken("new-access-token")
                    .refreshToken("new-refresh-token")
                    .expiresIn(3600L)
                    .user(SupabaseUser.builder().id(userId).build())
                    .build();

            when(supabaseAuthClient.refreshToken("valid.refresh.token")).thenReturn(session);

            User user = User.builder()
                    .userId(userId)
                    .email("user@cani.vn")
                    .role(UserRole.WORKER)
                    .status(UserStatus.ACTIVE)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // Act
            LoginResponse response = authService.refreshToken(request);

            // Assert
            assertNotNull(response);
            assertEquals("new-access-token", response.getToken());
            assertEquals("new-refresh-token", response.getRefreshToken());
            assertEquals(3600L, response.getExpiresIn());
            assertEquals("user@cani.vn", response.getUser().getEmail());
            verify(supabaseAuthClient).refreshToken("valid.refresh.token");
        }

        @Test
        @DisplayName("UTC029: refreshToken - Refresh token hết hạn hoặc không hợp lệ ném AppException 401 UNAUTHORIZED (hungpd)")
        void UTC029_refreshToken_InvalidOrExpiredToken_ThrowsAppException() {
            // Arrange
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("expired.or.invalid.token")
                    .build();

            when(supabaseAuthClient.refreshToken("expired.or.invalid.token"))
                    .thenThrow(new AppException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

            // Act & Assert
            AppException ex = assertThrows(AppException.class, () -> authService.refreshToken(request));
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
            assertEquals("Invalid refresh token", ex.getMessage());
            verify(userRepository, never()).findById(any());
        }
    }
}
