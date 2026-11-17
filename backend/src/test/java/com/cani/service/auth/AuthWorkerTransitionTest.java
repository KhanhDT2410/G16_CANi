package com.cani.service.auth;

import com.cani.client.SupabaseAuthClient;
import com.cani.dto.auth.GoogleLoginRequest;
import com.cani.dto.auth.LoginResponse;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.PaymentRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import com.cani.service.AuthService;
import com.cani.service.EmailOtpService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Adversarial Security Test Suite for Auth Worker-to-EO Transition (BUG-B Verification).
 * Verifies that workers with existing financial records (Payment) do not get deleted on Google login transition.
 */
@ExtendWith(MockitoExtension.class)
class AuthWorkerTransitionTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SupabaseAuthClient supabaseAuthClient;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private EmailOtpService emailOtpService;

    @Mock
    private Environment environment;

    @Mock
    private com.cani.service.NotificationService notificationService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("UTC-SEC-007: googleLogin - Worker đã có Payment dù chưa có CCCD khi login portal TargetEO -> Đổi role sang EO thành công nhưng GIỮ LẠI worker row (Chặn BUG-B)")
    void googleLogin_WorkerHasPaymentNoCccd_TargetEO_SucceedsAndKeepsWorkerRow() {
        UUID userId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        String email = "worker_with_pay@cani.vn";

        User user = User.builder()
                .userId(userId)
                .email(email)
                .fullName("Worker Has Pay")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();

        Worker worker = Worker.builder()
                .workerId(workerId)
                .user(user)
                .cccdNumber(null)
                .totalShiftsCompleted(0)
                .build();

        GoogleLoginRequest req = GoogleLoginRequest.builder()
                .supabaseAccessToken("valid.token")
                .targetRole("EVENT_ORGANIZER")
                .build();

        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getSubject()).thenReturn(userId.toString());
        when(mockJwt.getClaimAsString("email")).thenReturn(email);
        lenient().when(mockJwt.getClaimAsString("name")).thenReturn("Worker Has Pay");
        when(jwtDecoder.decode("valid.token")).thenReturn(mockJwt);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        lenient().when(paymentRepository.existsByWorker_WorkerId(workerId)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());
        lenient().when(eventOrganizerRepository.save(any(EventOrganizer.class))).thenAnswer(i -> i.getArgument(0));

        LoginResponse response = authService.googleLogin(req);

        assertThat(response).isNotNull();
        assertThat(response.getUser().getRole()).isEqualTo(UserRole.EVENT_ORGANIZER);
        verify(workerRepository, never()).delete(any(Worker.class));
        verify(userRepository, atLeastOnce()).save(argThat(u -> u.getRole() == UserRole.EVENT_ORGANIZER));
    }

    @Test
    @DisplayName("UTC-SEC-008: googleLogin - Worker rác không có Payment, không có CCCD -> Cho phép xóa worker row an toàn")
    void googleLogin_JunkWorkerNoPayment_TargetEO_DeletesWorkerRow() {
        UUID userId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        String email = "junk_worker@cani.vn";

        User user = User.builder()
                .userId(userId)
                .email(email)
                .fullName("Junk Worker")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();

        Worker worker = Worker.builder()
                .workerId(workerId)
                .user(user)
                .cccdNumber(null)
                .totalShiftsCompleted(0)
                .build();

        GoogleLoginRequest req = GoogleLoginRequest.builder()
                .supabaseAccessToken("valid.token")
                .targetRole("EVENT_ORGANIZER")
                .build();

        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getSubject()).thenReturn(userId.toString());
        when(mockJwt.getClaimAsString("email")).thenReturn(email);
        lenient().when(mockJwt.getClaimAsString("name")).thenReturn("Junk Worker");
        when(jwtDecoder.decode("valid.token")).thenReturn(mockJwt);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(paymentRepository.existsByWorker_WorkerId(workerId)).thenReturn(false);
        lenient().when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());
        lenient().when(eventOrganizerRepository.save(any(EventOrganizer.class))).thenAnswer(i -> i.getArgument(0));

        LoginResponse response = authService.googleLogin(req);

        assertThat(response).isNotNull();
        assertThat(response.getUser().getRole()).isEqualTo(UserRole.EVENT_ORGANIZER);
        verify(workerRepository).delete(argThat(w -> w.getWorkerId().equals(workerId)));
    }

    @Test
    @DisplayName("UTC-SEC-009: googleLogin - Worker thực thụ đã có CCCD đăng nhập TargetEO -> Giữ nguyên vai trò WORKER, không leo quyền và không xóa worker")
    void googleLogin_EstablishedWorkerWithCccd_TargetEO_KeepsWorkerRole() {
        UUID userId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        String email = "real_worker@cani.vn";

        User user = User.builder()
                .userId(userId)
                .email(email)
                .fullName("Real Worker")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();

        Worker worker = Worker.builder()
                .workerId(workerId)
                .user(user)
                .cccdNumber("012345678901")
                .totalShiftsCompleted(0)
                .build();

        GoogleLoginRequest req = GoogleLoginRequest.builder()
                .supabaseAccessToken("valid.token")
                .targetRole("EVENT_ORGANIZER")
                .build();

        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getSubject()).thenReturn(userId.toString());
        when(mockJwt.getClaimAsString("email")).thenReturn(email);
        lenient().when(mockJwt.getClaimAsString("name")).thenReturn("Real Worker");
        when(jwtDecoder.decode("valid.token")).thenReturn(mockJwt);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        LoginResponse response = authService.googleLogin(req);

        assertThat(response).isNotNull();
        assertThat(response.getUser().getRole()).isEqualTo(UserRole.WORKER);
        verify(workerRepository, never()).delete(any(Worker.class));
    }

    @Test
    @DisplayName("UTC-SEC-010: googleLogin - Worker có Payment chuyển sang EO -> Thay đổi vai trò được lưu xuống DB UserRepository an toàn")
    void googleLogin_WorkerHasPayment_TargetEO_RoleChangeIsPersisted() {
        UUID userId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        String email = "worker_payment_persisted@cani.vn";

        User user = User.builder()
                .userId(userId)
                .email(email)
                .fullName("Worker Persist")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();

        Worker worker = Worker.builder()
                .workerId(workerId)
                .user(user)
                .cccdNumber(null)
                .totalShiftsCompleted(0)
                .build();

        GoogleLoginRequest req = GoogleLoginRequest.builder()
                .supabaseAccessToken("valid.token")
                .targetRole("EVENT_ORGANIZER")
                .build();

        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getSubject()).thenReturn(userId.toString());
        when(mockJwt.getClaimAsString("email")).thenReturn(email);
        lenient().when(mockJwt.getClaimAsString("name")).thenReturn("Worker Persist");
        when(jwtDecoder.decode("valid.token")).thenReturn(mockJwt);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        lenient().when(paymentRepository.existsByWorker_WorkerId(workerId)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());
        lenient().when(eventOrganizerRepository.save(any(EventOrganizer.class))).thenAnswer(i -> i.getArgument(0));

        authService.googleLogin(req);

        verify(userRepository, atLeastOnce()).save(argThat(u -> u.getRole() == UserRole.EVENT_ORGANIZER));
        verify(workerRepository, never()).delete(any(Worker.class));
    }
}
