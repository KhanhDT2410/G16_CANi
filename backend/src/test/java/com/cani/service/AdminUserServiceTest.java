package com.cani.service;

import com.cani.dto.admin.user.UpdateUserStatusRequestDto;
import com.cani.dto.admin.user.UserDetailResponseDto;
import com.cani.dto.admin.user.UserFilterRequest;
import com.cani.dto.response.PageResponse;
import com.cani.entity.AuditLog;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.KycStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.entity.enums.VerificationStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.AuditLogRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AdminUserService adminUserService;

    private User sampleWorkerUser;
    private User sampleEoUser;
    private Worker sampleWorker;
    private EventOrganizer sampleEo;
    private UUID workerUserId;
    private UUID eoUserId;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        workerUserId = UUID.randomUUID();
        eoUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();

        sampleWorkerUser = User.builder()
                .userId(workerUserId)
                .email("worker@example.com")
                .phone("0901234567")
                .fullName("Nguyen Van Worker")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        sampleWorker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(sampleWorkerUser)
                .fullName("Nguyen Van Worker")
                .cccdNumber("012345678901")
                .kycStatus(KycStatus.APPROVED)
                .reliabilityScore(98)
                .ratingAvg(new BigDecimal("4.85"))
                .ratingCount(15)
                .totalShiftsCompleted(20)
                .yearsExperience(2)
                .build();

        sampleEoUser = User.builder()
                .userId(eoUserId)
                .email("eo@example.com")
                .phone("0912345678")
                .fullName("Tran Thi EO")
                .role(UserRole.EVENT_ORGANIZER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        sampleEo = EventOrganizer.builder()
                .eoId(UUID.randomUUID())
                .user(sampleEoUser)
                .companyName("Event Corp")
                .taxId("0312345678")
                .address("123 Nguyen Hue, Q1, TP.HCM")
                .verificationStatus(VerificationStatus.APPROVED)
                .ratingAvg(new BigDecimal("4.90"))
                .ratingCount(10)
                .totalEventsCompleted(12)
                .build();
    }

    @Test
    @DisplayName("getUsers - Trả về danh sách người dùng phân trang thành công")
    void testGetUsers_Success() {
        Page<User> page = new PageImpl<>(List.of(sampleWorkerUser, sampleEoUser), PageRequest.of(0, 10), 2);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        UserFilterRequest filter = UserFilterRequest.builder()
                .search("Worker")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();

        PageResponse<?> result = adminUserService.getUsers(filter, PageRequest.of(0, 10));

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getPageNumber()).isZero();
        verify(userRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getUserById - Trả về chi tiết người dùng Worker kèm thông tin Worker profile")
    void testGetUserById_Worker_Success() {
        when(userRepository.findById(workerUserId)).thenReturn(Optional.of(sampleWorkerUser));
        when(workerRepository.findByUser_UserId(workerUserId)).thenReturn(Optional.of(sampleWorker));

        UserDetailResponseDto result = adminUserService.getUserById(workerUserId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(workerUserId);
        assertThat(result.getFullName()).isEqualTo("Nguyen Van Worker");
        assertThat(result.getWorkerProfile()).isNotNull();
        assertThat(result.getWorkerProfile().getKycStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(result.getWorkerProfile().getReliabilityScore()).isEqualTo(98);
        assertThat(result.getEoProfile()).isNull();
    }

    @Test
    @DisplayName("getUserById - Trả về chi tiết người dùng EO kèm thông tin EO profile")
    void testGetUserById_Eo_Success() {
        when(userRepository.findById(eoUserId)).thenReturn(Optional.of(sampleEoUser));
        when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(sampleEo));

        UserDetailResponseDto result = adminUserService.getUserById(eoUserId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(eoUserId);
        assertThat(result.getFullName()).isEqualTo("Tran Thi EO");
        assertThat(result.getEoProfile()).isNotNull();
        assertThat(result.getEoProfile().getCompanyName()).isEqualTo("Event Corp");
        assertThat(result.getWorkerProfile()).isNull();
    }

    @Test
    @DisplayName("getUserById - Ném lỗi ResourceNotFoundException khi không tìm thấy user")
    void testGetUserById_NotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUserById(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateUserStatus - Khóa tài khoản thành công và ghi AuditLog")
    void testUpdateUserStatus_LockUser_Success() {
        when(userRepository.findById(workerUserId)).thenReturn(Optional.of(sampleWorkerUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleWorkerUser);

        UpdateUserStatusRequestDto request = UpdateUserStatusRequestDto.builder()
                .status(UserStatus.LOCKED)
                .reason("Vi phạm chính sách bỏ ca nhiều lần")
                .build();

        UserDetailResponseDto result = adminUserService.updateUserStatus(workerUserId, adminUserId, request);

        assertThat(result).isNotNull();
        assertThat(sampleWorkerUser.getStatus()).isEqualTo(UserStatus.LOCKED);
        verify(userRepository, times(1)).save(sampleWorkerUser);
        verify(auditLogService, times(1)).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("updateUserStatus - Ngăn Admin tự khóa tài khoản của chính mình")
    void testUpdateUserStatus_SelfLock_ThrowsBadRequest() {
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(sampleWorkerUser));

        UpdateUserStatusRequestDto request = UpdateUserStatusRequestDto.builder()
                .status(UserStatus.LOCKED)
                .reason("Tự khóa")
                .build();

        assertThatThrownBy(() -> adminUserService.updateUserStatus(adminUserId, adminUserId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Quản trị viên không thể tự thay đổi trạng thái tài khoản của chính mình");

        verify(userRepository, never()).save(any());
        verify(auditLogService, never()).logAction(any());
    }
}
