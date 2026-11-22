package com.cani.service;

import com.cani.client.SupabaseAuthClient;
import com.cani.client.dto.SupabaseSession;
import com.cani.client.dto.SupabaseUser;
import com.cani.dto.auth.*;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.AccountLockedException;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.PaymentRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final WorkerRepository workerRepository;
    private final PaymentRepository paymentRepository;
    private final SupabaseAuthClient supabaseAuthClient;
    private final JwtDecoder jwtDecoder;
    private final EmailOtpService emailOtpService;
    private final NotificationService notificationService;
    private final org.springframework.core.env.Environment environment;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AccountAppealService accountAppealService;

    @Transactional
    public UserDto register(RegisterRequest request) {
        // 0. Xác thực mã OTP 6 số đã gửi tới email người dùng
        emailOtpService.verifyOtp(request.getEmail(), request.getOtpCode(), "REGISTER");

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email này đã được sử dụng");
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()
                && userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("Số điện thoại này đã được sử dụng");
        }

        // 1. Xác định vai trò từ accountType (chỉ cho phép WORKER hoặc EVENT_ORGANIZER
        // khi tự đăng ký)
        UserRole assignedRole = request.getAccountType() == AccountType.EVENT_ORGANIZER
                ? UserRole.EVENT_ORGANIZER
                : UserRole.WORKER;

        // 2. Tạo user trên Supabase GoTrue kèm metadata để Database Trigger xử lý đồng
        // bộ ACID
        SupabaseUser supabaseUser = null;
        try {
            supabaseUser = supabaseAuthClient.createUser(
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName(),
                    request.getPhone(),
                    assignedRole.name(),
                    request.getCompanyName());
        } catch (Exception e) {
            log.warn("Không thể tạo user qua Supabase GoTrue (fallback local registration): {}", e.getClass().getSimpleName());
        }

        UUID userId = (supabaseUser != null && supabaseUser.getId() != null)
                ? supabaseUser.getId()
                : UUID.nameUUIDFromBytes(request.getEmail().toLowerCase().getBytes());

        // 3. Lấy hoặc cập nhật bản ghi người dùng trong public.users
        User savedUser = userRepository.findById(userId)
                .or(() -> userRepository.findByEmail(request.getEmail()))
                .orElseGet(() -> {
                    User user = User.builder()
                            .userId(userId)
                            .email(request.getEmail())
                            .phone(request.getPhone())
                            .fullName(request.getFullName())
                            .role(assignedRole)
                            .status(UserStatus.ACTIVE)
                            .emailVerified(true)
                            .build();
                    return userRepository.save(user);
                });

        savedUser.setFullName(request.getFullName());
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            savedUser.setPhone(request.getPhone());
        }
        savedUser.setRole(assignedRole);
        savedUser = userRepository.save(savedUser);

        final User finalUser = savedUser;

        // 4. Khởi tạo hoặc cập nhật profile con tương ứng với vai trò (tránh duplicate
        // key)
        if (assignedRole == UserRole.EVENT_ORGANIZER) {
            EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(finalUser.getUserId())
                    .orElseGet(() -> EventOrganizer.builder()
                            .user(finalUser)
                            .build());
            if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
                eo.setCompanyName(request.getCompanyName().trim());
            } else if (eo.getCompanyName() == null) {
                eo.setCompanyName(request.getFullName().trim());
            }
            eventOrganizerRepository.save(eo);
        } else {
            Worker worker = workerRepository.findByUser_UserId(finalUser.getUserId())
                    .orElseGet(() -> Worker.builder()
                            .user(finalUser)
                            .fullName(finalUser.getFullName())
                            .ratingAvg(BigDecimal.valueOf(5.0))
                            .build());
            worker.setFullName(finalUser.getFullName());
            if (worker.getRatingCount() == null || worker.getRatingCount() == 0) {
                if (worker.getRatingAvg() == null || worker.getRatingAvg().compareTo(BigDecimal.ZERO) == 0) {
                    worker.setRatingAvg(BigDecimal.valueOf(5.0));
                }
            }
            workerRepository.save(worker);

            if (notificationService != null) {
                try {
                    notificationService.sendNotification(
                            finalUser,
                            "Chào mừng bạn đến với CANi!",
                            "Vui lòng hoàn tất xác thực danh tính eKYC (CCCD) để mở khóa Job Radar và nhận các ca làm việc gần bạn.",
                            com.cani.entity.enums.NotificationType.SYSTEM,
                            "EKYC",
                            finalUser.getUserId());
                } catch (Exception e) {
                    log.warn("Không thể gửi thông báo chào mừng eKYC: {}", e.getClass().getSimpleName());
                }
            }
        }

        log.info("Đã đăng ký tài khoản thành công cho user: {}, role: {}", savedUser.getUserId(), assignedRole);
        return toUserDto(savedUser);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        SupabaseSession session = null;
        try {
            session = supabaseAuthClient.signInWithPassword(email, request.getPassword());
        } catch (BadRequestException e) {
            // Re-throw lỗi xác thực sai mật khẩu từ GoTrue, không nuốt thành lỗi kết nối
            throw e;
        } catch (Exception e) {
            log.warn("Supabase GoTrue auth failed or unreachable: {}. Falling back to local check...", e.getClass().getSimpleName());
        }

        UUID userId;
        String accessToken;
        String refreshToken;
        Long expiresIn;

        if (session != null) {
            userId = session.getUser().getId();
            accessToken = session.getAccessToken();
            refreshToken = session.getRefreshToken();
            expiresIn = session.getExpiresIn();
        } else if (environment.acceptsProfiles(Profiles.of("h2"))) {
            // Xác thực mật khẩu seed ở profile H2 offline (theo tài liệu run.md:
            // Password123!)
            String expectedH2Password = "Password123!";
            if (request.getPassword() == null || !expectedH2Password.equals(request.getPassword().trim())) {
                throw new BadRequestException("Email hoặc mật khẩu không chính xác");
            }

            // Chỉ profile h2 (dev offline, H2DataSeeder) được phép fallback dev-token.
            User localUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BadRequestException("Email hoặc mật khẩu không chính xác"));
            userId = localUser.getUserId();
            accessToken = "dev-token-" + userId;
            refreshToken = "dev-refresh-" + userId;
            expiresIn = 86400L;
        } else {
            // ponytail: Supabase không thể đạt ở prod/dev-thẳng-Supabase → 503 thay vì
            // dev-token (auth bypass).
            log.error("Supabase GoTrue không khả dụng, không thể cấp token cho {}", email);
            throw new AppException("Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau.",
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }

        User user = userRepository.findById(userId)
                .or(() -> userRepository.findByEmail(email))
                .orElseThrow(() -> new BadRequestException("Không tìm thấy thông tin tài khoản trong hệ thống"));

        checkUserNotLocked(user);

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .user(toUserDto(user))
                .build();
    }

    @Transactional
    public synchronized LoginResponse googleLogin(GoogleLoginRequest request) {
        try {
            // 1. Xác thực chữ ký token từ Supabase bằng JWKS ES256
            Jwt jwt = jwtDecoder.decode(request.getSupabaseAccessToken());
            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) {
                throw new BadRequestException("Không tìm thấy thông tin email từ phiên xác thực Google");
            }

            UUID parsedUserId;
            try {
                parsedUserId = UUID.fromString(jwt.getSubject());
            } catch (Exception e) {
                parsedUserId = UUID.nameUUIDFromBytes(email.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            final UUID finalUserId = parsedUserId;

            User user = userRepository.findById(finalUserId)
                    .or(() -> userRepository.findByEmail(email))
                    .orElseGet(() -> {
                        String fullName = email;
                        Map<String, Object> userMetadata = jwt.getClaim("user_metadata");
                        if (userMetadata != null && userMetadata.get("full_name") != null) {
                            fullName = userMetadata.get("full_name").toString();
                        }

                        // Xác định vai trò ban đầu: Nếu từ Web gửi EVENT_ORGANIZER thì cấp quyền EO,
                        // ngược lại mặc định WORKER
                        UserRole initialRole = UserRole.WORKER;
                        if ("EVENT_ORGANIZER".equalsIgnoreCase(request.getTargetRole())) {
                            initialRole = UserRole.EVENT_ORGANIZER;
                        }

                        User newUser = User.builder()
                                .userId(finalUserId)
                                .email(email)
                                .fullName(fullName)
                                .role(initialRole)
                                .status(UserStatus.ACTIVE)
                                .emailVerified(true)
                                .build();

                        User saved = userRepository.save(newUser);

                        if (initialRole == UserRole.EVENT_ORGANIZER) {
                            EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(saved.getUserId())
                                    .orElseGet(() -> EventOrganizer.builder()
                                            .user(saved)
                                            .build());
                            eventOrganizerRepository.save(eo);
                        } else {
                            Worker worker = workerRepository.findByUser_UserId(saved.getUserId())
                                    .orElseGet(() -> Worker.builder()
                                            .user(saved)
                                            .fullName(saved.getFullName())
                                            .ratingAvg(BigDecimal.valueOf(5.0))
                                            .build());
                            worker.setFullName(saved.getFullName());
                            if (worker.getRatingCount() == null || worker.getRatingCount() == 0) {
                                if (worker.getRatingAvg() == null
                                        || worker.getRatingAvg().compareTo(BigDecimal.ZERO) == 0) {
                                    worker.setRatingAvg(BigDecimal.valueOf(5.0));
                                }
                            }
                            workerRepository.save(worker);
                        }

                        return saved;
                    });

            // Nếu người dùng đăng nhập từ Web Portal với targetRole là EVENT_ORGANIZER
            // và tài khoản hiện tại đang ở vai trò WORKER:
            // - Kiểm tra tài khoản có phải Worker thực thụ (đã có CCCD / đã hoàn thành ca
            // làm) hay không.
            // - Nếu là tài khoản mới / chưa có lịch sử làm việc (do trigger Supabase tự
            // động gán WORKER):
            // Tiến hành chuyển đổi vai trò sang EVENT_ORGANIZER và xóa profile Worker rác.
            if ("EVENT_ORGANIZER".equalsIgnoreCase(request.getTargetRole())
                    && user.getRole() == UserRole.WORKER
                    && !"worker@cani.vn".equalsIgnoreCase(user.getEmail())) {

                Optional<Worker> workerOpt = workerRepository.findByUser_UserId(user.getUserId());
                boolean isEstablishedWorker = workerOpt
                        .map(w -> (w.getCccdNumber() != null && !w.getCccdNumber().isBlank()) ||
                                (w.getTotalShiftsCompleted() != null && w.getTotalShiftsCompleted() > 0))
                        .orElse(false);

                if (!isEstablishedWorker) {
                    log.info("Cập nhật vai trò người dùng Google OAuth {} sang EVENT_ORGANIZER từ Web Portal",
                            user.getEmail());
                    user.setRole(UserRole.EVENT_ORGANIZER);
                    user = userRepository.save(user);

                    // Xóa profile worker rác do trigger Supabase tự sinh (chỉ xóa khi chưa có dữ
                    // liệu thanh toán)
                    workerOpt.ifPresent(w -> {
                        boolean hasFinancialRecords = paymentRepository != null
                                && paymentRepository.existsByWorker_WorkerId(w.getWorkerId());
                        if (!hasFinancialRecords) {
                            workerRepository.delete(w);
                        } else {
                            log.info(
                                    "Worker {} đã có dữ liệu thanh toán (Payment), bảo toàn dữ liệu và không xóa khi nâng cấp lên EO",
                                    w.getWorkerId());
                        }
                    });
                }
            }

            checkUserNotLocked(user);

            // Đảm bảo hồ sơ EventOrganizer luôn sẵn sàng nếu user là EVENT_ORGANIZER
            if (user.getRole() == UserRole.EVENT_ORGANIZER) {
                final User eoUser = user;
                eventOrganizerRepository.findByUser_UserId(eoUser.getUserId())
                        .orElseGet(() -> eventOrganizerRepository.save(
                                EventOrganizer.builder()
                                        .user(eoUser)
                                        .companyName(eoUser.getFullName() != null && !eoUser.getFullName().isBlank()
                                                ? eoUser.getFullName()
                                                : "Ban Tổ Chức")
                                        .build()));
            }

            user.setLastLoginAt(Instant.now());
            User finalSavedUser = userRepository.save(user);

            return LoginResponse.builder()
                    .token(request.getSupabaseAccessToken())
                    .user(toUserDto(finalSavedUser))
                    .build();
        } catch (JwtException e) {
            log.error("Token Supabase Google không hợp lệ: {}", e.getClass().getSimpleName());
            throw new BadRequestException("Token xác thực Google không hợp lệ hoặc đã hết hạn");
        }
    }

    @Transactional(readOnly = true)
    public UserDto me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));
        return toUserDto(user);
    }

    @Transactional
    public UserDto updateBasicProfile(UUID userId, UpdateBasicProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));

        String cleanName = request.getFullName().trim();
        String cleanPhone = request.getPhone().trim();

        if (!cleanPhone.isBlank()) {
            userRepository.findByPhone(cleanPhone).ifPresent(existingUser -> {
                if (!existingUser.getUserId().equals(userId)) {
                    throw new BadRequestException("Số điện thoại này đã được sử dụng bởi một tài khoản khác trong hệ thống. Vui lòng kiểm tra lại hoặc sử dụng số khác.");
                }
            });
        }

        user.setFullName(cleanName);
        user.setPhone(cleanPhone);
        User saved = userRepository.save(user);

        if (saved.getRole() == UserRole.WORKER) {
            workerRepository.findByUser_UserId(userId).ifPresent(w -> {
                w.setFullName(cleanName);
                workerRepository.save(w);
            });
        }

        return toUserDto(saved);
    }

    @Transactional
    public void verifyOtp(VerifyOtpRequest request) {
        emailOtpService.verifyOtp(request.getEmail(), request.getToken(), "REGISTER");
        userRepository.findByEmail(request.getEmail()).ifPresent(u -> {
            u.setEmailVerified(true);
            userRepository.save(u);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        emailOtpService.sendOtp(email, "RESET_PASSWORD");
    }

    @Transactional
    public void confirmResetPassword(ConfirmResetPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // 1. Xác thực OTP loại RESET_PASSWORD
        emailOtpService.verifyOtp(email, request.getOtp(), "RESET_PASSWORD");

        // 2. Tìm User trong cơ sở dữ liệu
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy tài khoản với email: " + email));

        // 3. Cập nhật mật khẩu trong Supabase qua GoTrue Admin API
        supabaseAuthClient.updateUserPassword(user.getUserId(), request.getNewPassword());
        log.info("Khôi phục mật khẩu thành công cho email: {}", email);
    }

    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        SupabaseSession session = supabaseAuthClient.refreshToken(request.getRefreshToken());
        UUID userId = session.getUser().getId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy thông tin tài khoản người dùng"));

        return LoginResponse.builder()
                .token(session.getAccessToken())
                .refreshToken(session.getRefreshToken())
                .expiresIn(session.getExpiresIn())
                .user(toUserDto(user))
                .build();
    }

    private void checkUserNotLocked(User user) {
        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.SUSPENDED) {
            String lockReason = accountAppealService != null
                    ? accountAppealService.findLatestLockReason(user.getUserId())
                    : "Tài khoản bị tạm khóa do vi phạm chính sách hoặc điều khoản hoạt động của hệ thống CANi.";
            String appealToken = "appeal-token-" + user.getUserId() + "-" + System.currentTimeMillis();
            throw new AccountLockedException(
                    "Tài khoản của bạn đã bị khóa hoặc tạm ngưng hoạt động",
                    user.getEmail(),
                    user.getRole() != null ? user.getRole().name() : "EVENT_ORGANIZER",
                    user.getStatus().name(),
                    lockReason,
                    appealToken
            );
        }
    }

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getUserId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(user.getEmailVerified())
                .build();
    }
}
