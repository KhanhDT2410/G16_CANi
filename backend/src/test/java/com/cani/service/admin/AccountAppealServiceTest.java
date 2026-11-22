package com.cani.service.admin;

import com.cani.dto.appeal.AccountAppealResponseDto;
import com.cani.dto.appeal.ReviewAccountAppealRequestDto;
import com.cani.dto.appeal.SubmitAccountAppealRequestDto;
import com.cani.entity.AccountAppeal;
import com.cani.entity.AuditLog;
import com.cani.entity.User;
import com.cani.entity.enums.AccountAppealStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.AccountAppealRepository;
import com.cani.repository.AuditLogRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.UserRepository;
import com.cani.service.AccountAppealService;
import com.cani.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountAppealServiceTest {

    @Mock
    private AccountAppealRepository accountAppealRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AccountAppealService accountAppealService;

    private User lockedEoUser;
    private User adminUser;
    private AccountAppeal sampleAppeal;

    @BeforeEach
    void setUp() {
        lockedEoUser = User.builder()
                .userId(UUID.randomUUID())
                .email("eo@cani.vn")
                .fullName("Nguyễn Văn EO")
                .role(UserRole.EVENT_ORGANIZER)
                .status(UserStatus.LOCKED)
                .build();

        adminUser = User.builder()
                .userId(UUID.randomUUID())
                .email("admin@cani.vn")
                .fullName("Quản trị viên Hệ thống")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        sampleAppeal = AccountAppeal.builder()
                .id(UUID.randomUUID())
                .user(lockedEoUser)
                .email(lockedEoUser.getEmail())
                .userRole("EVENT_ORGANIZER")
                .reasonCategory("VIOLATION_RESOLVED")
                .appealContent("Tôi đã đền bù đầy đủ tiền công và xin rút kinh nghiệm sâu sắc.")
                .lockReasonSnapshot("Vi phạm thanh toán chậm")
                .status(AccountAppealStatus.SUBMITTED)
                .build();
    }

    @Test
    @DisplayName("Nộp đơn giải trình thành công khi tài khoản đang bị LOCKED")
    void submitAppeal_Success() {
        SubmitAccountAppealRequestDto request = SubmitAccountAppealRequestDto.builder()
                .email("eo@cani.vn")
                .reasonCategory("VIOLATION_RESOLVED")
                .appealContent("Tôi đã thanh toán và xin giải trình mở lại tài khoản.")
                .attachmentUrls("https://storage.cani.vn/appeals/evidence.jpg")
                .build();

        when(userRepository.findByEmail("eo@cani.vn")).thenReturn(Optional.of(lockedEoUser));
        when(accountAppealRepository.existsByUser_UserIdAndStatusIn(any(), any())).thenReturn(false);
        when(accountAppealRepository.save(any(AccountAppeal.class))).thenAnswer(invocation -> {
            AccountAppeal a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AccountAppealResponseDto result = accountAppealService.submitAppeal(request);

        assertNotNull(result);
        assertEquals("eo@cani.vn", result.getEmail());
        assertEquals(AccountAppealStatus.SUBMITTED, result.getStatus());
        verify(accountAppealRepository).save(any(AccountAppeal.class));
        verify(auditLogService).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("Nộp đơn giải trình thất bại khi tài khoản đang ACTIVE (không bị khóa)")
    void submitAppeal_UserNotLocked_ThrowsBadRequest() {
        lockedEoUser.setStatus(UserStatus.ACTIVE);
        SubmitAccountAppealRequestDto request = SubmitAccountAppealRequestDto.builder()
                .email("eo@cani.vn")
                .reasonCategory("OTHER")
                .appealContent("Nội dung giải trình")
                .build();

        when(userRepository.findByEmail("eo@cani.vn")).thenReturn(Optional.of(lockedEoUser));

        assertThrows(BadRequestException.class, () -> accountAppealService.submitAppeal(request));
        verify(accountAppealRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nộp đơn giải trình thất bại khi đang có đơn chờ Admin xét duyệt")
    void submitAppeal_AlreadyPending_ThrowsBadRequest() {
        SubmitAccountAppealRequestDto request = SubmitAccountAppealRequestDto.builder()
                .email("eo@cani.vn")
                .reasonCategory("OTHER")
                .appealContent("Nội dung giải trình lần 2")
                .build();

        when(userRepository.findByEmail("eo@cani.vn")).thenReturn(Optional.of(lockedEoUser));
        when(accountAppealRepository.existsByUser_UserIdAndStatusIn(any(), any())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> accountAppealService.submitAppeal(request));
        verify(accountAppealRepository, never()).save(any());
    }

    @Test
    @DisplayName("Admin phê duyệt APPROVED: Đơn thành APPROVED và User tự động chuyển sang ACTIVE")
    void reviewAppeal_Approved_UnlocksUser() {
        UUID appealId = sampleAppeal.getId();
        ReviewAccountAppealRequestDto request = ReviewAccountAppealRequestDto.builder()
                .decision("APPROVED")
                .adminNotes("Đã xác minh khắc phục đầy đủ. Mở khóa tài khoản.")
                .build();

        when(accountAppealRepository.findById(appealId)).thenReturn(Optional.of(sampleAppeal));
        when(userRepository.findById(adminUser.getUserId())).thenReturn(Optional.of(adminUser));
        when(accountAppealRepository.save(any(AccountAppeal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountAppealResponseDto result = accountAppealService.reviewAppeal(adminUser.getUserId(), appealId, request);

        assertEquals(AccountAppealStatus.APPROVED, result.getStatus());
        assertEquals(UserStatus.ACTIVE, lockedEoUser.getStatus()); // Tài khoản đã được mở khóa
        verify(userRepository).save(lockedEoUser);
        verify(auditLogService).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("Admin từ chối REJECTED: Đơn thành REJECTED và User vẫn giữ nguyên LOCKED")
    void reviewAppeal_Rejected_KeepsUserLocked() {
        UUID appealId = sampleAppeal.getId();
        ReviewAccountAppealRequestDto request = ReviewAccountAppealRequestDto.builder()
                .decision("REJECTED")
                .adminNotes("Chứng từ chưa đủ cơ sở chứng minh.")
                .build();

        when(accountAppealRepository.findById(appealId)).thenReturn(Optional.of(sampleAppeal));
        when(userRepository.findById(adminUser.getUserId())).thenReturn(Optional.of(adminUser));
        when(accountAppealRepository.save(any(AccountAppeal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountAppealResponseDto result = accountAppealService.reviewAppeal(adminUser.getUserId(), appealId, request);

        assertEquals(AccountAppealStatus.REJECTED, result.getStatus());
        assertEquals(UserStatus.LOCKED, lockedEoUser.getStatus()); // Vẫn bị khóa
        verify(userRepository, never()).save(lockedEoUser);
        verify(auditLogService).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("Tra cứu tiến độ giải trình theo email")
    void getLatestAppealByEmail_Success() {
        when(accountAppealRepository.findTopByEmailIgnoreCaseOrderByCreatedAtDesc("eo@cani.vn"))
                .thenReturn(Optional.of(sampleAppeal));

        AccountAppealResponseDto result = accountAppealService.getLatestAppealByEmail("eo@cani.vn");

        assertNotNull(result);
        assertEquals("eo@cani.vn", result.getEmail());
    }
}
