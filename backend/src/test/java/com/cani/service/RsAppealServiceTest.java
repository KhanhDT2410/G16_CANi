package com.cani.service;

import com.cani.dto.admin.appeal.ReviewRsAppealRequestDto;
import com.cani.dto.admin.appeal.RsAppealResponseDto;
import com.cani.dto.worker.appeal.SubmitRsAppealRequestDto;
import com.cani.entity.*;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.RsAppealStatus;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.repository.ReliabilityLogRepository;
import com.cani.repository.RsAppealRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RsAppealService — Kiểm thử nghiệp vụ Kháng cáo điểm uy tín RS (UC-94)")
class RsAppealServiceTest {

    @Mock
    private RsAppealRepository rsAppealRepository;

    @Mock
    private ReliabilityLogRepository reliabilityLogRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private RsAppealService rsAppealService;

    private User mockUser;
    private Worker mockWorker;
    private UUID userId;
    private UUID workerId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workerId = UUID.randomUUID();

        mockUser = User.builder()
                .userId(userId)
                .fullName("Lê Văn B")
                .email("worker@cani.vn")
                .build();

        mockWorker = Worker.builder()
                .workerId(workerId)
                .user(mockUser)
                .reliabilityScore(75)
                .build();
    }

    @Nested
    @DisplayName("1. Nộp đơn kháng cáo (submitAppeal)")
    class SubmitAppealTests {

        @Test
        @DisplayName("Nộp đơn kháng cáo thành công trong vòng 7 ngày kể từ khi bị phạt")
        void shouldSubmitAppealSuccessfullyWithin7Days() {
            ReliabilityLog penaltyLog = ReliabilityLog.builder()
                    .logId(UUID.randomUUID())
                    .worker(mockWorker)
                    .changeAmount(-25)
                    .reason("Vắng mặt không báo trước (No-show)")
                    .scoreBefore(100)
                    .scoreAfter(75)
                    .createdAt(Instant.now().minus(3, ChronoUnit.DAYS))
                    .build();

            SubmitRsAppealRequestDto request = SubmitRsAppealRequestDto.builder()
                    .reliabilityLogId(penaltyLog.getLogId())
                    .reasonCode("FORCE_MAJEURE_ACCIDENT")
                    .reasonDetail("Bị tai nạn giao thông trên đường đến sự kiện, có giấy nhập viện")
                    .evidence("https://storage.cani.vn/evidence/hospital-report.pdf")
                    .build();

            when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockWorker));
            when(reliabilityLogRepository.findById(penaltyLog.getLogId())).thenReturn(Optional.of(penaltyLog));
            when(rsAppealRepository.existsByReliabilityLog_LogId(penaltyLog.getLogId())).thenReturn(false);
            when(rsAppealRepository.save(any(RsAppeal.class))).thenAnswer(invocation -> {
                RsAppeal a = invocation.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });

            RsAppealResponseDto response = rsAppealService.submitAppeal(userId, request);

            assertThat(response).isNotNull();
            assertThat(response.getPenaltyAmount()).isEqualTo(25);
            assertThat(response.getStatus()).isEqualTo(RsAppealStatus.SUBMITTED);
            assertThat(response.getReasonCode()).isEqualTo("FORCE_MAJEURE_ACCIDENT");

            verify(auditLogService).logAction(argThat(log -> "SUBMIT_RS_APPEAL".equals(log.getAction())));
            verify(notificationService).sendNotification(eq(mockUser), anyString(), anyString(), eq(NotificationType.RS_APPEAL), any(), any());
        }

        @Test
        @DisplayName("Chặn nộp kháng cáo khi đã quá thời hạn 7 ngày kể từ lúc bị trừ điểm")
        void shouldThrowExceptionWhenOver7Days() {
            ReliabilityLog oldPenaltyLog = ReliabilityLog.builder()
                    .logId(UUID.randomUUID())
                    .worker(mockWorker)
                    .changeAmount(-20)
                    .reason("Hủy ca muộn (Late cancel)")
                    .scoreBefore(95)
                    .scoreAfter(75)
                    .createdAt(Instant.now().minus(8, ChronoUnit.DAYS)) // Đã qua 8 ngày
                    .build();

            SubmitRsAppealRequestDto request = SubmitRsAppealRequestDto.builder()
                    .reliabilityLogId(oldPenaltyLog.getLogId())
                    .reasonCode("FORCE_MAJEURE")
                    .reasonDetail("Lý do cá nhân bất khả kháng")
                    .build();

            when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockWorker));
            when(reliabilityLogRepository.findById(oldPenaltyLog.getLogId())).thenReturn(Optional.of(oldPenaltyLog));

            assertThatThrownBy(() -> rsAppealService.submitAppeal(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Đã quá thời hạn 7 ngày");
        }

        @Test
        @DisplayName("Chặn nộp kháng cáo trùng lặp cho cùng một bản ghi trừ điểm")
        void shouldThrowExceptionWhenDuplicateAppeal() {
            ReliabilityLog penaltyLog = ReliabilityLog.builder()
                    .logId(UUID.randomUUID())
                    .worker(mockWorker)
                    .changeAmount(-10)
                    .reason("Hủy ca T-24h")
                    .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                    .build();

            SubmitRsAppealRequestDto request = SubmitRsAppealRequestDto.builder()
                    .reliabilityLogId(penaltyLog.getLogId())
                    .reasonCode("FORCE_MAJEURE")
                    .reasonDetail("Lý do bất khả kháng")
                    .build();

            when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockWorker));
            when(reliabilityLogRepository.findById(penaltyLog.getLogId())).thenReturn(Optional.of(penaltyLog));
            when(rsAppealRepository.existsByReliabilityLog_LogId(penaltyLog.getLogId())).thenReturn(true);

            assertThatThrownBy(() -> rsAppealService.submitAppeal(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("đã được nộp kháng cáo trước đó");
        }

        @Test
        @DisplayName("Chặn nộp kháng cáo cho bản ghi không phải điểm phạt (changeAmount >= 0)")
        void shouldThrowExceptionWhenNotPenalty() {
            ReliabilityLog rewardLog = ReliabilityLog.builder()
                    .logId(UUID.randomUUID())
                    .worker(mockWorker)
                    .changeAmount(5) // Cộng điểm thưởng
                    .reason("Hoàn thành ca làm xuất sắc")
                    .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                    .build();

            SubmitRsAppealRequestDto request = SubmitRsAppealRequestDto.builder()
                    .reliabilityLogId(rewardLog.getLogId())
                    .reasonCode("OTHER")
                    .reasonDetail("Thử kháng cáo điểm thưởng")
                    .build();

            when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockWorker));
            when(reliabilityLogRepository.findById(rewardLog.getLogId())).thenReturn(Optional.of(rewardLog));

            assertThatThrownBy(() -> rsAppealService.submitAppeal(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Chỉ có thể nộp kháng cáo cho các bản ghi bị trừ điểm");
        }

        @Test
        @DisplayName("Chặn nộp kháng cáo bản ghi điểm uy tín của Worker khác (Bảo vệ IDOR / Quyền sở hữu)")
        void shouldThrowForbiddenWhenLogBelongsToDifferentWorker() {
            Worker otherWorker = Worker.builder().workerId(UUID.randomUUID()).build();
            ReliabilityLog otherLog = ReliabilityLog.builder()
                    .logId(UUID.randomUUID())
                    .worker(otherWorker)
                    .changeAmount(-20)
                    .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                    .build();

            SubmitRsAppealRequestDto request = SubmitRsAppealRequestDto.builder()
                    .reliabilityLogId(otherLog.getLogId())
                    .reasonCode("FORCE_MAJEURE")
                    .reasonDetail("Kháng cáo nhầm của người khác")
                    .build();

            when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockWorker));
            when(reliabilityLogRepository.findById(otherLog.getLogId())).thenReturn(Optional.of(otherLog));

            assertThatThrownBy(() -> rsAppealService.submitAppeal(userId, request))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Bạn không có quyền kháng cáo bản ghi điểm uy tín này");
        }
    }

    @Nested
    @DisplayName("2. Thẩm định và ra quyết định kháng cáo (reviewAppeal)")
    class ReviewAppealTests {

        @Test
        @DisplayName("Chấp thuận kháng cáo: khôi phục đúng số điểm RS, ghi ReliabilityLog mới với changeAmount dương và referenceType RS_APPEAL")
        void shouldApproveAppealAndRestoreScore() {
            UUID appealId = UUID.randomUUID();
            UUID reviewerId = UUID.randomUUID();

            mockWorker.setReliabilityScore(75);

            RsAppeal appeal = RsAppeal.builder()
                    .id(appealId)
                    .worker(mockWorker)
                    .penaltyAmount(25)
                    .status(RsAppealStatus.SUBMITTED)
                    .build();

            ReviewRsAppealRequestDto request = ReviewRsAppealRequestDto.builder()
                    .approved(true)
                    .decisionNote("Chấp nhận lý do bất khả kháng, có giấy viện phí hợp lệ")
                    .build();

            when(rsAppealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
            when(rsAppealRepository.save(any(RsAppeal.class))).thenAnswer(invocation -> invocation.getArgument(0));

            RsAppealResponseDto response = rsAppealService.reviewAppeal(reviewerId, appealId, request);

            assertThat(response).isNotNull();
            assertThat(appeal.getStatus()).isEqualTo(RsAppealStatus.APPROVED);
            assertThat(appeal.getReviewerId()).isEqualTo(reviewerId);
            assertThat(appeal.getDecisionNote()).contains("Chấp nhận lý do");

            // Kiểm tra Worker được khôi phục điểm: 75 + 25 = 100
            assertThat(mockWorker.getReliabilityScore()).isEqualTo(100);
            verify(workerRepository).save(mockWorker);

            // Kiểm tra ghi ReliabilityLog khôi phục điểm
            ArgumentCaptor<ReliabilityLog> logCaptor = ArgumentCaptor.forClass(ReliabilityLog.class);
            verify(reliabilityLogRepository).save(logCaptor.capture());
            ReliabilityLog restoredLog = logCaptor.getValue();
            assertThat(restoredLog.getChangeAmount()).isEqualTo(25);
            assertThat(restoredLog.getReferenceType()).isEqualTo("RS_APPEAL");
            assertThat(restoredLog.getReferenceId()).isEqualTo(appealId);
            assertThat(restoredLog.getScoreBefore()).isEqualTo(75);
            assertThat(restoredLog.getScoreAfter()).isEqualTo(100);

            // Kiểm tra AuditLog và Notification
            verify(auditLogService).logAction(argThat(log -> "APPROVE_RS_APPEAL".equals(log.getAction())));
            verify(notificationService).sendNotification(eq(mockUser), anyString(), anyString(), eq(NotificationType.RS_APPEAL), any(), eq(appealId));
        }

        @Test
        @DisplayName("Từ chối kháng cáo: chuyển trạng thái REJECTED, không khôi phục điểm RS, gửi thông báo và ghi audit")
        void shouldRejectAppealWithoutScoreChange() {
            UUID appealId = UUID.randomUUID();
            UUID reviewerId = UUID.randomUUID();

            mockWorker.setReliabilityScore(80);

            RsAppeal appeal = RsAppeal.builder()
                    .id(appealId)
                    .worker(mockWorker)
                    .penaltyAmount(20)
                    .status(RsAppealStatus.SUBMITTED)
                    .build();

            ReviewRsAppealRequestDto request = ReviewRsAppealRequestDto.builder()
                    .approved(false)
                    .decisionNote("Bằng chứng không đủ tính xác thực")
                    .build();

            when(rsAppealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
            when(rsAppealRepository.save(any(RsAppeal.class))).thenAnswer(invocation -> invocation.getArgument(0));

            RsAppealResponseDto response = rsAppealService.reviewAppeal(reviewerId, appealId, request);

            assertThat(response).isNotNull();
            assertThat(appeal.getStatus()).isEqualTo(RsAppealStatus.REJECTED);
            assertThat(mockWorker.getReliabilityScore()).isEqualTo(80); // Điểm giữ nguyên

            verify(workerRepository, never()).save(any());
            verify(reliabilityLogRepository, never()).save(any());
            verify(auditLogService).logAction(argThat(log -> "REJECT_RS_APPEAL".equals(log.getAction())));
            verify(notificationService).sendNotification(eq(mockUser), anyString(), anyString(), eq(NotificationType.RS_APPEAL), any(), eq(appealId));
        }

        @Test
        @DisplayName("Chặn sửa đổi khi kháng cáo đã có quyết định cuối cùng (APPROVED hoặc REJECTED)")
        void shouldThrowExceptionWhenReviewingFinalizedAppeal() {
            UUID appealId = UUID.randomUUID();
            RsAppeal finalizedAppeal = RsAppeal.builder()
                    .id(appealId)
                    .worker(mockWorker)
                    .status(RsAppealStatus.APPROVED)
                    .build();

            ReviewRsAppealRequestDto request = ReviewRsAppealRequestDto.builder()
                    .approved(false)
                    .decisionNote("Cố gắng sửa đổi quyết định đã duyệt")
                    .build();

            when(rsAppealRepository.findById(appealId)).thenReturn(Optional.of(finalizedAppeal));

            assertThatThrownBy(() -> rsAppealService.reviewAppeal(UUID.randomUUID(), appealId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("đã có quyết định cuối cùng, không thể sửa đổi");
        }
    }
}

