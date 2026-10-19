package com.cani.service;

import com.cani.dto.auth.LoginRequest;
import com.cani.dto.auth.LoginResponse;
import com.cani.dto.auth.RegisterRequest;
import com.cani.dto.auth.UserDto;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.BadRequestException;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final WorkerRepository workerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Email hoặc mật khẩu không chính xác"));

        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.SUSPENDED) {
            throw new BadRequestException("Tài khoản của bạn đã bị khóa hoặc tạm ngưng hoạt động");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= 5) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(Instant.now().plusSeconds(900)); // Khóa 15 phút
                log.warn("Tài khoản {} đã bị tạm khóa do nhập sai mật khẩu 5 lần", user.getEmail());
            }
            userRepository.save(user);
            throw new BadRequestException("Email hoặc mật khẩu không chính xác");
        }

        // Đăng nhập thành công -> Reset số lần đăng nhập sai
        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Tạo token phiên làm việc (JWT giả lập hoặc UUID token)
        String token = "cani-jwt-" + UUID.randomUUID().toString();

        UserDto userDto = UserDto.builder()
                .id(user.getUserId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .avatarUrl(user.getAvatarUrl())
                .build();

        return LoginResponse.builder()
                .token(token)
                .user(userDto)
                .build();
    }

    @Transactional
    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email này đã được sử dụng");
        }

        if (request.getPhone() != null && !request.getPhone().isBlank() && userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("Số điện thoại này đã được sử dụng");
        }

        User user = User.builder()
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(user);

        // Tự động khởi tạo hồ sơ phụ thuộc tương ứng với vai trò
        if (request.getRole() == UserRole.EVENT_ORGANIZER) {
            EventOrganizer eo = EventOrganizer.builder()
                    .user(savedUser)
                    .companyName(request.getCompanyName())
                    .build();
            eventOrganizerRepository.save(eo);
        } else if (request.getRole() == UserRole.WORKER) {
            Worker worker = Worker.builder()
                    .user(savedUser)
                    .fullName(savedUser.getFullName())
                    .build();
            workerRepository.save(worker);
        }

        return UserDto.builder()
                .id(savedUser.getUserId())
                .email(savedUser.getEmail())
                .phone(savedUser.getPhone())
                .fullName(savedUser.getFullName())
                .role(savedUser.getRole())
                .status(savedUser.getStatus())
                .avatarUrl(savedUser.getAvatarUrl())
                .build();
    }
}
