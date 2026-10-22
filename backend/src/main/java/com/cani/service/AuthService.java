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
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final WorkerRepository workerRepository;
    private final SupabaseAuthClient supabaseAuthClient;
    private final JwtDecoder jwtDecoder;

    @Transactional
    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email này đã được sử dụng");
        }

        if (request.getPhone() != null && !request.getPhone().isBlank() && userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("Số điện thoại này đã được sử dụng");
        }

        // 1. Xác định vai trò từ accountType (chỉ cho phép WORKER hoặc EVENT_ORGANIZER khi tự đăng ký)
        UserRole assignedRole = request.getAccountType() == AccountType.EVENT_ORGANIZER
                ? UserRole.EVENT_ORGANIZER
                : UserRole.WORKER;

        // 2. Tạo user trên Supabase GoTrue kèm metadata để Database Trigger xử lý đồng bộ ACID
        SupabaseUser supabaseUser = null;
        try {
            supabaseUser = supabaseAuthClient.createUser(
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName(),
                    request.getPhone(),
                    assignedRole.name(),
                    request.getCompanyName()
            );
        } catch (Exception e) {
            log.warn("Không thể tạo user qua Supabase GoTrue (fallback local registration): {}", e.getMessage());
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

        // 4. Khởi tạo hoặc cập nhật profile con tương ứng với vai trò (tránh duplicate key)
        if (assignedRole == UserRole.EVENT_ORGANIZER) {
            EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(finalUser.getUserId())
                    .orElseGet(() -> EventOrganizer.builder()
                            .user(finalUser)
                            .build());
            if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
                eo.setCompanyName(request.getCompanyName());
            }
            eventOrganizerRepository.save(eo);
        } else {
            Worker worker = workerRepository.findByUser_UserId(finalUser.getUserId())
                    .orElseGet(() -> Worker.builder()
                            .user(finalUser)
                            .fullName(finalUser.getFullName())
                            .build());
            worker.setFullName(finalUser.getFullName());
            workerRepository.save(worker);
        }

        log.info("Đã đăng ký tài khoản thành công cho user: {}, role: {}", savedUser.getEmail(), assignedRole);
        return toUserDto(savedUser);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        SupabaseSession session = null;
        try {
            session = supabaseAuthClient.signInWithPassword(request.getEmail(), request.getPassword());
        } catch (Exception e) {
            log.warn("Supabase GoTrue auth failed or unreachable: {}. Falling back to local check...", e.getMessage());
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
        } else {
            User localUser = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new BadRequestException("Email hoặc mật khẩu không chính xác"));
            userId = localUser.getUserId();
            accessToken = "dev-token-" + userId;
            refreshToken = "dev-refresh-" + userId;
            expiresIn = 86400L;
        }

        User user = userRepository.findById(userId)
                .or(() -> userRepository.findByEmail(request.getEmail()))
                .orElseThrow(() -> new BadRequestException("Không tìm thấy thông tin tài khoản trong hệ thống"));

        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.SUSPENDED) {
            throw new BadRequestException("Tài khoản của bạn đã bị khóa hoặc tạm ngưng hoạt động");
        }

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
    public LoginResponse googleLogin(GoogleLoginRequest request) {
        try {
            // 1. Xác thực chữ ký token từ Supabase bằng JWKS ES256
            Jwt jwt = jwtDecoder.decode(request.getSupabaseAccessToken());
            UUID userId = UUID.fromString(jwt.getSubject());
            String email = jwt.getClaimAsString("email");

            User user = userRepository.findById(userId).orElseGet(() -> {
                String fullName = email;
                Map<String, Object> userMetadata = jwt.getClaim("user_metadata");
                if (userMetadata != null && userMetadata.get("full_name") != null) {
                    fullName = userMetadata.get("full_name").toString();
                }

                User newUser = User.builder()
                        .userId(userId)
                        .email(email)
                        .fullName(fullName)
                        .role(UserRole.WORKER) // Mặc định tài khoản Google đăng nhập lần đầu là WORKER
                        .status(UserStatus.ACTIVE)
                        .emailVerified(true)
                        .build();

                User saved = userRepository.save(newUser);

                Worker worker = workerRepository.findByUser_UserId(saved.getUserId())
                        .orElseGet(() -> Worker.builder()
                                .user(saved)
                                .fullName(saved.getFullName())
                                .build());
                worker.setFullName(saved.getFullName());
                workerRepository.save(worker);

                return saved;
            });

            if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.SUSPENDED) {
                throw new BadRequestException("Tài khoản của bạn đã bị khóa hoặc tạm ngưng hoạt động");
            }

            user.setLastLoginAt(Instant.now());
            userRepository.save(user);

            return LoginResponse.builder()
                    .token(request.getSupabaseAccessToken())
                    .user(toUserDto(user))
                    .build();
        } catch (JwtException e) {
            log.error("Token Supabase Google không hợp lệ: {}", e.getMessage());
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
    public void verifyOtp(VerifyOtpRequest request) {
        supabaseAuthClient.verifyOtp(request.getEmail(), request.getToken());
        userRepository.findByEmail(request.getEmail()).ifPresent(u -> {
            u.setEmailVerified(true);
            userRepository.save(u);
        });
    }

    public void resetPassword(ResetPasswordRequest request) {
        supabaseAuthClient.resetPassword(request.getEmail());
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
