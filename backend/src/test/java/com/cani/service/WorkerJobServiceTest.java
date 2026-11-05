package com.cani.service;

import com.cani.dto.worker.*;
import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerJobServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private DispatchProposalRepository dispatchProposalRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private MatchingDispatchService matchingDispatchService;

    @Mock
    private DirectPaymentService directPaymentService;

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private WorkerAvailabilityRepository workerAvailabilityRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProposalStatusUpdater proposalStatusUpdater;

    @Mock
    private PaymentRepository paymentRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks
    private WorkerJobService workerJobService;

    private UUID userId;
    private User workerUser;
    private Worker worker;
    private Event mockEvent;
    private Shift activeShift;
    private DispatchProposal pendingProposal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        workerUser = User.builder()
                .userId(userId)
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();

        worker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(workerUser)
                .fullName("Lê Văn Worker")
                .isOnline(true)
                .reliabilityScore(90)
                .totalShiftsCompleted(5)
                .ratingAvg(BigDecimal.valueOf(4.8))
                .build();

        User eoUser = User.builder().userId(UUID.randomUUID()).role(UserRole.EVENT_ORGANIZER).build();
        EventOrganizer eo = EventOrganizer.builder().eoId(UUID.randomUUID()).user(eoUser).build();

        mockEvent = Event.builder()
                .eventId(UUID.randomUUID())
                .eventOrganizer(eo)
                .name("Sự kiện CANi Tech Expo")
                .venueAddress("77 Võ Văn Tần, Q3, TP.HCM")
                .venueLat(10.7769)
                .venueLng(106.7009)
                .totalConfirmedWorkers(2)
                .build();

        activeShift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(mockEvent)
                .jobType("Bảo vệ sự kiện")
                .description("Hỗ trợ điều phối an ninh vòng ngoài")
                .locationAddress("77 Võ Văn Tần, Q3")
                .locationLat(10.7769)
                .locationLng(106.7009)
                .startTime(Instant.now().plus(24, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(28, ChronoUnit.HOURS))
                .requiredWorkers(4)
                .confirmedWorkers(1)
                .remainingWorkers(3)
                .hourlyWage(BigDecimal.valueOf(35000))
                .allowance(BigDecimal.valueOf(10000))
                .status(ShiftStatus.ACTIVE)
                .checklist("[\"Có mặt trước 15 phút\", \"Đồng phục chỉn chu\"]")
                .build();

        pendingProposal = DispatchProposal.builder()
                .proposalId(UUID.randomUUID())
                .shift(activeShift)
                .worker(worker)
                .matchScore(BigDecimal.valueOf(88.5))
                .status(ProposalStatus.PENDING)
                .expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .build();
    }

    // =========================================================================
    // 1. setOnlineStatus() - UTC001 to UTC003
    // =========================================================================
    @Test
    @DisplayName("UTC001 - setOnlineStatus() - Normal: Cập nhật online và tọa độ GPS thành công")
    void setOnlineStatus_Success_UpdatesOnlineAndGps() {
        WorkerOnlineStatusRequestDto dto = WorkerOnlineStatusRequestDto.builder()
                .isOnline(true).currentLat(10.7769).currentLng(106.7009).build();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(any(Worker.class))).thenAnswer(i -> i.getArgument(0));

        Worker result = workerJobService.setOnlineStatus(userId, dto);

        assertThat(result.getIsOnline()).isTrue();
        assertThat(result.getBaseLat()).isEqualTo(10.7769);
        assertThat(result.getBaseLng()).isEqualTo(106.7009);
        verify(workerRepository).save(worker);
    }

    @Test
    @DisplayName("UTC002 - setOnlineStatus() - Abnormal: Không tìm thấy Worker ném ResourceNotFoundException")
    void setOnlineStatus_Abnormal_WorkerNotFoundThrowsResourceNotFoundException() {
        WorkerOnlineStatusRequestDto dto = WorkerOnlineStatusRequestDto.builder().isOnline(true).build();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workerJobService.setOnlineStatus(userId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy thông tin Worker");
    }

    @Test
    @DisplayName("UTC003 - setOnlineStatus() - Boundary: Tọa độ GPS null chỉ cập nhật trạng thái isOnline")
    void setOnlineStatus_Boundary_NullGpsCoordinatesKeepsBaseCoordinates() {
        worker.setBaseLat(10.5);
        worker.setBaseLng(106.5);
        WorkerOnlineStatusRequestDto dto = WorkerOnlineStatusRequestDto.builder()
                .isOnline(false).currentLat(null).currentLng(null).build();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(any(Worker.class))).thenAnswer(i -> i.getArgument(0));

        Worker result = workerJobService.setOnlineStatus(userId, dto);

        assertThat(result.getIsOnline()).isFalse();
        assertThat(result.getBaseLat()).isEqualTo(10.5);
        assertThat(result.getBaseLng()).isEqualTo(106.5);
    }

    // =========================================================================
    // 2. getMyProposals() - UTC004 to UTC006
    // =========================================================================
    @Test
    @DisplayName("UTC004 - getMyProposals() - Normal: Lấy danh sách đề xuất việc làm đang chờ hợp lệ")
    void getMyProposals_Success_ReturnsPendingUnexpiredProposals() {
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(dispatchProposalRepository.findByWorker_WorkerIdAndStatusOrderByCreatedAtDesc(worker.getWorkerId(), ProposalStatus.PENDING))
                .thenReturn(List.of(pendingProposal));

        List<JobProposalResponseDto> result = workerJobService.getMyProposals(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProposalId()).isEqualTo(pendingProposal.getProposalId());
        assertThat(result.get(0).getJobType()).isEqualTo("Bảo vệ sự kiện");
    }

    @Test
    @DisplayName("UTC005 - getMyProposals() - Abnormal: Worker không tồn tại ném ResourceNotFoundException")
    void getMyProposals_Abnormal_WorkerNotFoundThrowsResourceNotFoundException() {
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workerJobService.getMyProposals(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("UTC006 - getMyProposals() - Boundary: Đề xuất đã hết hạn (expiresAt < now) bị lọc bỏ")
    void getMyProposals_Boundary_ExpiredProposalsFilteredOut() {
        pendingProposal.setExpiresAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(dispatchProposalRepository.findByWorker_WorkerIdAndStatusOrderByCreatedAtDesc(worker.getWorkerId(), ProposalStatus.PENDING))
                .thenReturn(List.of(pendingProposal));

        List<JobProposalResponseDto> result = workerJobService.getMyProposals(userId);

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // 3. acceptProposal() - UTC007 to UTC010
    // =========================================================================
    @Test
    @DisplayName("UTC007 - acceptProposal() - Normal: Nhận việc thành công, tạo ShiftAssignment CONFIRMED")
    void acceptProposal_Success_ConfirmsAssignmentAndAcceptsProposal() {
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(dispatchProposalRepository.findById(pendingProposal.getProposalId())).thenReturn(Optional.of(pendingProposal));
        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(List.of());
        when(shiftRepository.incrementConfirmedWorkersAtomic(activeShift.getShiftId())).thenReturn(1);
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class))).thenAnswer(i -> {
            ShiftAssignment sa = i.getArgument(0);
            sa.setAssignmentId(UUID.randomUUID());
            return sa;
        });

        ShiftAssignmentResponseDto res = workerJobService.acceptProposal(userId, pendingProposal.getProposalId());

        assertThat(res.getStatus()).isEqualTo(AssignmentStatus.CONFIRMED);
        assertThat(pendingProposal.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        verify(directPaymentService).createInitialPayment(any());
        verify(eventRepository).save(mockEvent);
    }

    @Test
    @DisplayName("UTC008 - acceptProposal() - Abnormal: Đề xuất không ở trạng thái PENDING ném BadRequestException")
    void acceptProposal_Abnormal_ProposalNotPendingThrowsBadRequestException() {
        pendingProposal.setStatus(ProposalStatus.ACCEPTED);
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(dispatchProposalRepository.findById(pendingProposal.getProposalId())).thenReturn(Optional.of(pendingProposal));

        assertThatThrownBy(() -> workerJobService.acceptProposal(userId, pendingProposal.getProposalId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không còn khả dụng");
    }

    @Test
    @DisplayName("UTC009 - acceptProposal() - Abnormal: Trùng khung giờ ca làm khác ném BadRequestException")
    void acceptProposal_Abnormal_TimeConflictThrowsBadRequestException() {
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(dispatchProposalRepository.findById(pendingProposal.getProposalId())).thenReturn(Optional.of(pendingProposal));
        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any()))
                .thenReturn(List.of(ShiftAssignment.builder().assignmentId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> workerJobService.acceptProposal(userId, pendingProposal.getProposalId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("trùng khung giờ");
    }

    @Test
    @DisplayName("UTC010 - acceptProposal() - Boundary: Tranh chấp đồng thời, ca vừa đủ người ném BadRequestException")
    void acceptProposal_Boundary_ShiftFullRejectsProposalAndThrowsBadRequestException() {
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(dispatchProposalRepository.findById(pendingProposal.getProposalId())).thenReturn(Optional.of(pendingProposal));
        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(List.of());
        when(shiftRepository.incrementConfirmedWorkersAtomic(activeShift.getShiftId())).thenReturn(0);

        assertThatThrownBy(() -> workerJobService.acceptProposal(userId, pendingProposal.getProposalId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vừa được người khác nhận đủ");

        verify(proposalStatusUpdater).updateStatusInNewTransaction(eq(pendingProposal.getProposalId()), eq(ProposalStatus.REJECTED));
        verify(shiftAssignmentRepository, never()).save(any());
    }


    @Test
    @DisplayName("UTC010b - acceptProposal() - Boundary: Đề xuất hết hạn gọi updateStatusInNewTransaction EXPIRED và ném BadRequestException")
    void acceptProposal_Boundary_ExpiredProposalUpdatesStatusInNewTxAndThrowsBadRequest() {
        UUID proposalId = UUID.randomUUID();
        DispatchProposal expiredProposal = DispatchProposal.builder()
                .proposalId(proposalId)
                .worker(worker)
                .shift(activeShift)
                .status(ProposalStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(dispatchProposalRepository.findById(proposalId)).thenReturn(Optional.of(expiredProposal));

        assertThatThrownBy(() -> workerJobService.acceptProposal(userId, proposalId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hết hạn phản hồi");

        verify(proposalStatusUpdater).updateStatusInNewTransaction(eq(proposalId), eq(ProposalStatus.EXPIRED));
    }

    // =========================================================================
    // 4. rejectProposal() - UTC011 to UTC013
    // =========================================================================
    @Test
    @DisplayName("UTC011 - rejectProposal() - Normal: Từ chối đề xuất thành công và kích hoạt redispatch")
    void rejectProposal_Success_MarksRejectedAndTriggersRedispatch() {
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(dispatchProposalRepository.findById(pendingProposal.getProposalId())).thenReturn(Optional.of(pendingProposal));

        workerJobService.rejectProposal(userId, pendingProposal.getProposalId(), "Bận việc gia đình");

        assertThat(pendingProposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        verify(matchingDispatchService).dispatchForShift(activeShift.getShiftId());
    }

    @Test
    @DisplayName("UTC012 - rejectProposal() - Abnormal: Đề xuất đã được xử lý ném BadRequestException")
    void rejectProposal_Abnormal_AlreadyAcceptedThrowsBadRequestException() {
        pendingProposal.setStatus(ProposalStatus.ACCEPTED);
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(dispatchProposalRepository.findById(pendingProposal.getProposalId())).thenReturn(Optional.of(pendingProposal));

        assertThatThrownBy(() -> workerJobService.rejectProposal(userId, pendingProposal.getProposalId(), "Bận"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã ở trạng thái: ACCEPTED");
    }

    @Test
    @DisplayName("UTC013 - rejectProposal() - Boundary: Lỗi redispatch được xử lý mềm không văng ngoại lệ")
    void rejectProposal_Boundary_RedispatchFailureDoesNotBreakRejection() {
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(dispatchProposalRepository.findById(pendingProposal.getProposalId())).thenReturn(Optional.of(pendingProposal));
        doThrow(new RuntimeException("Redispatch service timeout")).when(matchingDispatchService).dispatchForShift(any());

        workerJobService.rejectProposal(userId, pendingProposal.getProposalId(), "Trùng lịch");

        assertThat(pendingProposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
    }

    // =========================================================================
    // 5. getMyAssignments() - UTC014 to UTC015
    // =========================================================================
    @Test
    @DisplayName("UTC014 - getMyAssignments() - Normal: Lấy danh sách ca làm việc đã nhận kèm điểm danh")
    void getMyAssignments_Success_ReturnsMappedAssignments() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId())).thenReturn(List.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.empty());

        List<ShiftAssignmentResponseDto> list = workerJobService.getMyAssignments(userId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getAssignmentId()).isEqualTo(assignmentId);
        assertThat(list.get(0).getAttendanceStatus()).isEqualTo("NOT_CHECKED_IN");
    }

    @Test
    @DisplayName("UTC015 - getMyAssignments() - Boundary: Worker chưa nhận ca nào trả về danh sách rỗng")
    void getMyAssignments_Boundary_NoAssignmentsReturnsEmptyList() {
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId())).thenReturn(List.of());

        List<ShiftAssignmentResponseDto> list = workerJobService.getMyAssignments(userId);

        assertThat(list).isEmpty();
    }

    // =========================================================================
    // 6. checkinAssignment() - UTC016 to UTC019
    // =========================================================================
    @Test
    @DisplayName("UTC016 - checkinAssignment() - Normal: Quét mã QR hợp lệ, điểm danh đúng giờ thành công")
    void checkinAssignment_Success_OnTimeCheckin() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();
        AttendanceCheckRequestDto req = AttendanceCheckRequestDto.builder()
                .qrContent("CANI_CHECKIN:" + activeShift.getShiftId()).lat(10.7769).lng(106.7009).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenAnswer(i -> {
            AttendanceLog log = i.getArgument(0);
            log.setAttendanceId(UUID.randomUUID());
            return log;
        });

        AttendanceCheckResponseDto res = workerJobService.checkinAssignment(userId, assignmentId, req);

        assertThat(res.getAttendanceStatus()).isEqualTo(AttendanceStatus.ON_TIME);
        assertThat(res.getIsOnTime()).isTrue();
        assertThat(sa.getStatus()).isEqualTo(AssignmentStatus.CHECKED_IN);
    }

    @Test
    @DisplayName("UTC017 - checkinAssignment() - Abnormal: Quét nhầm mã QR Check-out ném BadRequestException")
    void checkinAssignment_Abnormal_ScannedCheckoutQrThrowsBadRequestException() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();
        AttendanceCheckRequestDto req = AttendanceCheckRequestDto.builder().qrContent("CANI_CHECKOUT:xyz").build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        assertThatThrownBy(() -> workerJobService.checkinAssignment(userId, assignmentId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Đây là mã QR Check-out");
    }

    @Test
    @DisplayName("UTC018 - checkinAssignment() - Abnormal: Quét mã QR ca khác ném BadRequestException")
    void checkinAssignment_Abnormal_ScannedMismatchShiftQrThrowsBadRequestException() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();
        AttendanceCheckRequestDto req = AttendanceCheckRequestDto.builder()
                .qrContent("CANI_CHECKIN:" + UUID.randomUUID()).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        assertThatThrownBy(() -> workerJobService.checkinAssignment(userId, assignmentId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thuộc ca làm việc hiện tại");
    }

    @Test
    @DisplayName("UTC019 - checkinAssignment() - Boundary: Điểm danh muộn quá 15 phút ghi nhận LATE")
    void checkinAssignment_Boundary_LateCheckinSetsStatusLate() {
        UUID assignmentId = UUID.randomUUID();
        activeShift.setStartTime(Instant.now().minus(20, ChronoUnit.MINUTES));
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();
        AttendanceCheckRequestDto req = AttendanceCheckRequestDto.builder()
                .qrContent("CANI_CHECKIN:" + activeShift.getShiftId()).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenAnswer(i -> i.getArgument(0));

        AttendanceCheckResponseDto res = workerJobService.checkinAssignment(userId, assignmentId, req);

        assertThat(res.getAttendanceStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(res.getIsOnTime()).isFalse();
    }


    @Test
    @DisplayName("UTC019b - checkinAssignment() - Abnormal: Ca làm việc chưa CONFIRMED (đã CANCELLED) ném BadRequestException")
    void checkinAssignment_Abnormal_AssignmentNotConfirmedThrowsBadRequestException() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CANCELLED).build();
        AttendanceCheckRequestDto req = AttendanceCheckRequestDto.builder()
                .qrContent("CANI_CHECKIN:" + activeShift.getShiftId()).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        assertThatThrownBy(() -> workerJobService.checkinAssignment(userId, assignmentId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chỉ có thể check-in cho ca làm việc ở trạng thái CONFIRMED");
    }

    @Test
    @DisplayName("UTC019c - checkinAssignment() - Abnormal: Khoảng cách GPS vượt quá 200m ném BadRequestException")
    void checkinAssignment_Abnormal_DistanceGreaterThan200mThrowsBadRequestException() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();
        AttendanceCheckRequestDto req = AttendanceCheckRequestDto.builder()
                .qrContent("CANI_CHECKIN:" + activeShift.getShiftId())
                .lat(11.5).lng(107.5) // ~100km away
                .build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        assertThatThrownBy(() -> workerJobService.checkinAssignment(userId, assignmentId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vượt quá bán kính điểm danh 200m");
    }

    @Test
    @DisplayName("UTC019d - checkinAssignment() - Boundary: Điểm danh đúng chuẩn mốc biên 15 phút ghi nhận ON_TIME")
    void checkinAssignment_Boundary_ExactlyFifteenMinutesOnTime() {
        UUID assignmentId = UUID.randomUUID();
        activeShift.setStartTime(Instant.now().minus(14, ChronoUnit.MINUTES).minus(50, ChronoUnit.SECONDS));
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();
        AttendanceCheckRequestDto req = AttendanceCheckRequestDto.builder()
                .qrContent("CANI_CHECKIN:" + activeShift.getShiftId()).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenAnswer(i -> i.getArgument(0));

        AttendanceCheckResponseDto res = workerJobService.checkinAssignment(userId, assignmentId, req);

        assertThat(res.getAttendanceStatus()).isEqualTo(AttendanceStatus.ON_TIME);
        assertThat(res.getIsOnTime()).isTrue();
    }

    // =========================================================================
    // 7. checkoutAssignment() - UTC020 to UTC023
    // =========================================================================
    @Test
    @DisplayName("UTC020 - checkoutAssignment() - Normal: Check-out thành công, tính giờ làm và hoàn thành ca")
    void checkoutAssignment_Success_CalculatesHoursAndMarksCompleted() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CHECKED_IN).build();
        AttendanceLog log = AttendanceLog.builder()
                .attendanceId(UUID.randomUUID()).shiftAssignment(sa).worker(worker)
                .checkinTime(Instant.now().minus(4, ChronoUnit.HOURS)).build();
        AttendanceCheckRequestDto req = AttendanceCheckRequestDto.builder()
                .qrContent("CANI_CHECKOUT:" + activeShift.getShiftId()).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.of(log));

        AttendanceCheckResponseDto res = workerJobService.checkoutAssignment(userId, assignmentId, req);

        assertThat(res.getAttendanceStatus()).isEqualTo(AttendanceStatus.CHECKED_OUT);
        assertThat(res.getHoursWorked()).isGreaterThan(BigDecimal.valueOf(3.5));
        assertThat(sa.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        verify(workerRepository).save(worker);
    }

    @Test
    @DisplayName("UTC021 - checkoutAssignment() - Abnormal: Quét nhầm mã QR Check-in ném BadRequestException")
    void checkoutAssignment_Abnormal_ScannedCheckinQrThrowsBadRequestException() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CHECKED_IN).build();
        AttendanceCheckRequestDto req = AttendanceCheckRequestDto.builder().qrContent("CANI_CHECKIN:abc").build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        assertThatThrownBy(() -> workerJobService.checkoutAssignment(userId, assignmentId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Đây là mã QR Check-in");
    }

    @Test
    @DisplayName("UTC022 - checkoutAssignment() - Abnormal: Chưa từng check-in ném BadRequestException")
    void checkoutAssignment_Abnormal_NoCheckinRecordThrowsBadRequestException() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CHECKED_IN).build();
        AttendanceCheckRequestDto req = AttendanceCheckRequestDto.builder()
                .qrContent("CANI_CHECKOUT:" + activeShift.getShiftId()).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workerJobService.checkoutAssignment(userId, assignmentId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bạn chưa check-in vào ca");
    }

    @Test
    @DisplayName("UTC023 - checkoutAssignment() - Boundary: Đã check-out trước đó ném BadRequestException")
    void checkoutAssignment_Boundary_AlreadyCheckedOutThrowsBadRequestException() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CHECKED_IN).build();
        AttendanceLog log = AttendanceLog.builder()
                .checkinTime(Instant.now().minus(4, ChronoUnit.HOURS)).checkoutTime(Instant.now().minus(1, ChronoUnit.HOURS)).build();
        AttendanceCheckRequestDto req = AttendanceCheckRequestDto.builder()
                .qrContent("CANI_CHECKOUT:" + activeShift.getShiftId()).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> workerJobService.checkoutAssignment(userId, assignmentId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bạn đã check-out hoàn thành ca");
    }


    @Test
    @DisplayName("UTC023b - checkoutAssignment() - Abnormal: Ca làm việc chưa CHECKED_IN (vẫn CONFIRMED) ném BadRequestException")
    void checkoutAssignment_Abnormal_AssignmentNotCheckedInThrowsBadRequestException() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();
        AttendanceCheckRequestDto req = AttendanceCheckRequestDto.builder()
                .qrContent("CANI_CHECKOUT:" + activeShift.getShiftId()).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        assertThatThrownBy(() -> workerJobService.checkoutAssignment(userId, assignmentId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chỉ có thể check-out cho ca làm việc đang ở trạng thái CHECKED_IN");
    }

    // =========================================================================
    // 8. getNearbyShifts() - UTC024 to UTC026
    // =========================================================================
    @Test
    @DisplayName("UTC024 - getNearbyShifts() - Normal: Lấy các ca đang tuyển gần vị trí tìm kiếm")
    void getNearbyShifts_Success_ReturnsSortedShiftsWithinRadius() {
        when(shiftRepository.findByStatus(ShiftStatus.ACTIVE)).thenReturn(List.of(activeShift));

        List<NearbyShiftResponseDto> list = workerJobService.getNearbyShifts(10.7769, 106.7009, 10.0);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getShiftId()).isEqualTo(activeShift.getShiftId());
        assertThat(list.get(0).getDistanceKm()).isLessThan(1.0);
    }

    @Test
    @DisplayName("UTC025 - getNearbyShifts() - Boundary: Bán kính null mặc định 15km")
    void getNearbyShifts_Boundary_NullRadiusFallsBackToDefault() {
        when(shiftRepository.findByStatus(ShiftStatus.ACTIVE)).thenReturn(List.of(activeShift));

        List<NearbyShiftResponseDto> list = workerJobService.getNearbyShifts(10.7769, 106.7009, null);

        assertThat(list).hasSize(1);
    }

    @Test
    @DisplayName("UTC026 - getNearbyShifts() - Boundary: Tọa độ null dùng trung tâm mặc định TP.HCM")
    void getNearbyShifts_Boundary_NullLatLongFallsBackToDefaultCenter() {
        when(shiftRepository.findByStatus(ShiftStatus.ACTIVE)).thenReturn(List.of(activeShift));

        List<NearbyShiftResponseDto> list = workerJobService.getNearbyShifts(null, null, 20.0);

        assertThat(list).hasSize(1);
    }

    // =========================================================================
    // 9. applyForShift() - UTC027 to UTC030
    // =========================================================================
    @Test
    @DisplayName("UTC027 - applyForShift() - Normal: Ứng tuyển trực tiếp thành công trên Job Radar")
    void applyForShift_Success_CreatesConfirmedAssignment() {
        UUID shiftId = activeShift.getShiftId();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(activeShift));
        when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(shiftId, worker.getWorkerId())).thenReturn(false);
        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(List.of());
        when(shiftRepository.incrementConfirmedWorkersAtomic(shiftId)).thenReturn(1);
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class))).thenAnswer(i -> {
            ShiftAssignment sa = i.getArgument(0);
            sa.setAssignmentId(UUID.randomUUID());
            return sa;
        });

        ShiftAssignmentResponseDto res = workerJobService.applyForShift(userId, shiftId);

        assertThat(res.getStatus()).isEqualTo(AssignmentStatus.CONFIRMED);
        verify(directPaymentService).createInitialPayment(any());
        verify(eventRepository).save(mockEvent);
    }

    @Test
    @DisplayName("UTC028 - applyForShift() - Abnormal: Ca không mở tuyển (COMPLETED) ném BadRequestException")
    void applyForShift_Abnormal_ShiftNotActiveThrowsBadRequestException() {
        activeShift.setStatus(ShiftStatus.COMPLETED);
        UUID shiftId = activeShift.getShiftId();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(activeShift));

        assertThatThrownBy(() -> workerJobService.applyForShift(userId, shiftId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không còn mở tuyển nhân sự");
    }

    @Test
    @DisplayName("UTC029 - applyForShift() - Abnormal: Đã ứng tuyển ca này trước đó ném BadRequestException")
    void applyForShift_Abnormal_AlreadyAppliedThrowsBadRequestException() {
        UUID shiftId = activeShift.getShiftId();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(activeShift));
        when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(shiftId, worker.getWorkerId())).thenReturn(true);

        assertThatThrownBy(() -> workerJobService.applyForShift(userId, shiftId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bạn đã nhận hoặc ứng tuyển vào ca làm việc này rồi");
    }

    @Test
    @DisplayName("UTC030 - applyForShift() - Boundary: Hết chỗ vào giây cuối (atomic increment = 0) ném BadRequestException")
    void applyForShift_Boundary_ShiftAlreadyFullThrowsBadRequestException() {
        UUID shiftId = activeShift.getShiftId();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(activeShift));
        when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(shiftId, worker.getWorkerId())).thenReturn(false);
        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(List.of());
        when(shiftRepository.incrementConfirmedWorkersAtomic(shiftId)).thenReturn(0);

        assertThatThrownBy(() -> workerJobService.applyForShift(userId, shiftId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vừa đủ số lượng nhân sự");
    }

    // =========================================================================
    // 10. getWorkerAvailability() - UTC031 to UTC032
    // =========================================================================
    @Test
    @DisplayName("UTC031 - getWorkerAvailability() - Normal: Lấy danh sách lịch rảnh hàng tuần")
    void getWorkerAvailability_Success_ReturnsWeeklyScheduleList() {
        WorkerAvailability avail = WorkerAvailability.builder()
                .dayOfWeek(1).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).isAvailable(true).build();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(workerAvailabilityRepository.findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(worker.getWorkerId()))
                .thenReturn(List.of(avail));

        List<WorkerAvailabilityDto> list = workerJobService.getWorkerAvailability(userId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getDayOfWeek()).isEqualTo(1);
        assertThat(list.get(0).getStartTime()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("UTC032 - getWorkerAvailability() - Boundary: Chưa cài lịch rảnh trả về danh sách rỗng")
    void getWorkerAvailability_Boundary_EmptyScheduleReturnsEmptyList() {
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(workerAvailabilityRepository.findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(worker.getWorkerId()))
                .thenReturn(List.of());

        List<WorkerAvailabilityDto> list = workerJobService.getWorkerAvailability(userId);

        assertThat(list).isEmpty();
    }

    // =========================================================================
    // 11. updateWorkerAvailability() - UTC033 to UTC035
    // =========================================================================
    @Test
    @DisplayName("UTC033 - updateWorkerAvailability() - Normal: Cập nhật lịch rảnh thành công")
    void updateWorkerAvailability_Success_DeletesOldAndSavesNew() {
        UpdateAvailabilityRequestDto req = UpdateAvailabilityRequestDto.builder()
                .schedules(List.of(WorkerAvailabilityDto.builder().dayOfWeek(2).startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(21, 0)).isAvailable(true).build()))
                .build();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(workerAvailabilityRepository.findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(worker.getWorkerId()))
                .thenReturn(List.of(WorkerAvailability.builder().dayOfWeek(2).startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(21, 0)).isAvailable(true).build()));

        List<WorkerAvailabilityDto> res = workerJobService.updateWorkerAvailability(userId, req);

        verify(workerAvailabilityRepository).deleteByWorker_WorkerId(worker.getWorkerId());
        verify(workerAvailabilityRepository).saveAll(any());
        assertThat(res).hasSize(1);
    }

    @Test
    @DisplayName("UTC034 - updateWorkerAvailability() - Abnormal: Worker không tồn tại ném ResourceNotFoundException")
    void updateWorkerAvailability_Abnormal_WorkerNotFoundThrowsResourceNotFoundException() {
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workerJobService.updateWorkerAvailability(userId, UpdateAvailabilityRequestDto.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("UTC035 - updateWorkerAvailability() - Boundary: Danh sách rỗng xóa toàn bộ lịch rảnh")
    void updateWorkerAvailability_Boundary_EmptySchedulesListDeletesOldAndReturnsEmpty() {
        UpdateAvailabilityRequestDto req = UpdateAvailabilityRequestDto.builder().schedules(List.of()).build();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(workerAvailabilityRepository.findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(worker.getWorkerId()))
                .thenReturn(List.of());

        List<WorkerAvailabilityDto> res = workerJobService.updateWorkerAvailability(userId, req);

        verify(workerAvailabilityRepository).deleteByWorker_WorkerId(worker.getWorkerId());
        verify(workerAvailabilityRepository, never()).saveAll(any());
        assertThat(res).isEmpty();
    }

    // =========================================================================
    // 12. getWorkerPreferences() - UTC036 to UTC037
    // =========================================================================
    @Test
    @DisplayName("UTC036 - getWorkerPreferences() - Normal: Lấy tiêu chí matching tìm việc của Worker")
    void getWorkerPreferences_Success_ReturnsExistingPreferences() {
        worker.setBaseAddress("123 Lê Lợi");
        worker.setBaseLat(10.77);
        worker.setBaseLng(106.70);
        worker.setMinWage(BigDecimal.valueOf(40000));
        worker.setMaxDistanceKm(12.5);

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));

        WorkerPreferencesDto dto = workerJobService.getWorkerPreferences(userId);

        assertThat(dto.getBaseAddress()).isEqualTo("123 Lê Lợi");
        assertThat(dto.getMinWage()).isEqualTo(BigDecimal.valueOf(40000));
        assertThat(dto.getMaxDistanceKm()).isEqualTo(12.5);
    }

    @Test
    @DisplayName("UTC037 - getWorkerPreferences() - Boundary: Các trường null trả về giá trị mặc định")
    void getWorkerPreferences_Boundary_NullPreferencesReturnsDefaultValues() {
        worker.setMinWage(null);
        worker.setMaxDistanceKm(null);
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));

        WorkerPreferencesDto dto = workerJobService.getWorkerPreferences(userId);

        assertThat(dto.getMinWage()).isEqualTo(BigDecimal.ZERO);
        assertThat(dto.getMaxDistanceKm()).isEqualTo(20.0);
    }

    // =========================================================================
    // 13. updateWorkerPreferences() - UTC038 to UTC039
    // =========================================================================
    @Test
    @DisplayName("UTC038 - updateWorkerPreferences() - Normal: Cập nhật đầy đủ tiêu chí tìm việc")
    void updateWorkerPreferences_Success_UpdatesAllFields() {
        WorkerPreferencesDto req = WorkerPreferencesDto.builder()
                .baseAddress("Quận 1").baseLat(10.78).baseLng(106.69)
                .minWage(BigDecimal.valueOf(45000)).maxDistanceKm(10.0).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));

        WorkerPreferencesDto res = workerJobService.updateWorkerPreferences(userId, req);

        verify(workerRepository).save(worker);
        assertThat(res.getBaseAddress()).isEqualTo("Quận 1");
        assertThat(res.getMinWage()).isEqualTo(BigDecimal.valueOf(45000));
    }

    @Test
    @DisplayName("UTC039 - updateWorkerPreferences() - Boundary: Chỉ cập nhật một vài trường không null")
    void updateWorkerPreferences_Boundary_PartialUpdateOnlyModifiesNonNullFields() {
        worker.setBaseAddress("Địa chỉ cũ");
        WorkerPreferencesDto req = WorkerPreferencesDto.builder()
                .minWage(BigDecimal.valueOf(50000)).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));

        WorkerPreferencesDto res = workerJobService.updateWorkerPreferences(userId, req);

        assertThat(res.getBaseAddress()).isEqualTo("Địa chỉ cũ");
        assertThat(res.getMinWage()).isEqualTo(BigDecimal.valueOf(50000));
    }

    // =========================================================================
    // 14. cancelAssignment() - UTC040 to UTC043
    // =========================================================================
    @Test
    @DisplayName("UTC040 - cancelAssignment() - Normal: Hủy ca sớm (>24h) bị trừ phạt 5 điểm uy tín")
    void cancelAssignment_Success_EarlyCancellationSmallPenalty() {
        UUID assignmentId = UUID.randomUUID();
        activeShift.setStartTime(Instant.now().plus(48, ChronoUnit.HOURS));
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        workerJobService.cancelAssignment(userId, assignmentId, "Bận thi học kỳ");

        assertThat(sa.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(worker.getReliabilityScore()).isEqualTo(85); // 90 - 5 = 85
        verify(shiftRepository).decrementConfirmedWorkersAtomic(eq(activeShift.getShiftId()));
        verify(matchingDispatchService).dispatchForShift(activeShift.getShiftId());
    }

    @Test
    @DisplayName("UTC041 - cancelAssignment() - Abnormal: Ca đã bị hủy trước đó ném BadRequestException")
    void cancelAssignment_Abnormal_AlreadyCancelledThrowsBadRequestException() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CANCELLED).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        assertThatThrownBy(() -> workerJobService.cancelAssignment(userId, assignmentId, "Lý do"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã được hủy trước đó");
    }

    @Test
    @DisplayName("UTC042 - cancelAssignment() - Abnormal: Ca đã hoàn thành ném BadRequestException")
    void cancelAssignment_Abnormal_AlreadyCompletedThrowsBadRequestException() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.COMPLETED).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        assertThatThrownBy(() -> workerJobService.cancelAssignment(userId, assignmentId, "Lý do"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thể hủy");
    }

    @Test
    @DisplayName("UTC043 - cancelAssignment() - Boundary: Hủy sát giờ (<12h) trừ 20 điểm và không âm dưới 0")
    void cancelAssignment_Boundary_LateCancellationHeavyPenaltyAndScoreFloorZero() {
        UUID assignmentId = UUID.randomUUID();
        activeShift.setStartTime(Instant.now().plus(4, ChronoUnit.HOURS));
        worker.setReliabilityScore(10); // thấp hơn 20 điểm
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        workerJobService.cancelAssignment(userId, assignmentId, "Bệnh đột xuất");

        assertThat(worker.getReliabilityScore()).isEqualTo(0); // Không âm
    }

    // =========================================================================
    // 15. getShiftChecklist() - UTC044 to UTC046
    // =========================================================================
    @Test
    @DisplayName("UTC044 - getShiftChecklist() - Normal: Merge template với tiến độ cá nhân của Worker")
    void getShiftChecklist_Success_MergesTemplateWithWorkerProgress() {
        UUID assignmentId = UUID.randomUUID();
        activeShift.setChecklist("[\"Nhiệm vụ 1\", \"Nhiệm vụ 2\"]");
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId).shift(activeShift).worker(worker)
                .checklistProgress("{\"chk_1\":{\"isCompleted\":true}}").build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        List<ShiftChecklistItemDto> items = workerJobService.getShiftChecklist(userId, assignmentId);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getId()).isEqualTo("chk_1");
        assertThat(items.get(0).getIsCompleted()).isTrue();
        assertThat(items.get(1).getIsCompleted()).isFalse();
    }

    @Test
    @DisplayName("UTC045 - getShiftChecklist() - Abnormal: Worker không sở hữu assignment ném BadRequestException")
    void getShiftChecklist_Abnormal_UnauthorizedWorkerThrowsBadRequestException() {
        UUID assignmentId = UUID.randomUUID();
        Worker anotherWorker = Worker.builder().workerId(UUID.randomUUID()).build();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).worker(anotherWorker).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        assertThatThrownBy(() -> workerJobService.getShiftChecklist(userId, assignmentId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không có quyền");
    }

    @Test
    @DisplayName("UTC046 - getShiftChecklist() - Boundary: Shift không có template checklist trả về rỗng")
    void getShiftChecklist_Boundary_NullOrEmptyShiftChecklistReturnsEmptyList() {
        UUID assignmentId = UUID.randomUUID();
        activeShift.setChecklist(null);
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        List<ShiftChecklistItemDto> items = workerJobService.getShiftChecklist(userId, assignmentId);

        assertThat(items).isEmpty();
    }

    // =========================================================================
    // 16. updateShiftChecklist() - UTC047 to UTC049
    // =========================================================================
    @Test
    @DisplayName("UTC047 - updateShiftChecklist() - Normal: Cập nhật checklist progress vào Assignment")
    void updateShiftChecklist_Success_SavesProgressWithoutModifyingShift() {
        UUID assignmentId = UUID.randomUUID();
        activeShift.setChecklist("[\"Việc 1\"]");
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId).shift(activeShift).worker(worker).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        UpdateChecklistRequestDto req = UpdateChecklistRequestDto.builder()
                .items(List.of(ShiftChecklistItemDto.builder().id("chk_1").isCompleted(true).build())).build();

        List<ShiftChecklistItemDto> res = workerJobService.updateShiftChecklist(userId, assignmentId, req);

        verify(shiftAssignmentRepository).save(sa);
        verify(shiftRepository, never()).save(any());
        assertThat(res.get(0).getIsCompleted()).isTrue();
    }

    @Test
    @DisplayName("UTC048 - updateShiftChecklist() - Abnormal: Assignment không tìm thấy ném ResourceNotFoundException")
    void updateShiftChecklist_Abnormal_AssignmentNotFoundThrowsResourceNotFoundException() {
        UUID assignmentId = UUID.randomUUID();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workerJobService.updateShiftChecklist(userId, assignmentId, UpdateChecklistRequestDto.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("UTC049 - updateShiftChecklist() - Boundary: Item ID không thuộc template bị bỏ qua")
    void updateShiftChecklist_Boundary_InvalidItemIdIgnored() {
        UUID assignmentId = UUID.randomUUID();
        activeShift.setChecklist("[\"Việc 1\"]");
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        UpdateChecklistRequestDto req = UpdateChecklistRequestDto.builder()
                .items(List.of(ShiftChecklistItemDto.builder().id("invalid_id").isCompleted(true).build())).build();

        List<ShiftChecklistItemDto> res = workerJobService.updateShiftChecklist(userId, assignmentId, req);

        assertThat(res.get(0).getIsCompleted()).isFalse();
    }

    // =========================================================================
    // 17. parseChecklistTemplate() - UTC050 to UTC051
    // =========================================================================
    @Test
    @DisplayName("UTC050 - parseChecklistTemplate() - Normal: Parse định dạng mảng chuỗi và mảng đối tượng")
    void parseChecklistTemplate_Success_ParsesStringAndObjectArrays() {
        String json = "[\"Nhiệm vụ A\", {\"id\":\"custom_1\", \"title\":\"Nhiệm vụ B\"}]";

        List<ShiftChecklistItemDto> list = workerJobService.parseChecklistTemplate(json);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getTitle()).isEqualTo("Nhiệm vụ A");
        assertThat(list.get(1).getId()).isEqualTo("custom_1");
    }

    @Test
    @DisplayName("UTC051 - parseChecklistTemplate() - Boundary: JSON lỗi cú pháp trả về mảng rỗng")
    void parseChecklistTemplate_Boundary_MalformedJsonReturnsEmptyList() {
        List<ShiftChecklistItemDto> list = workerJobService.parseChecklistTemplate("{invalid_json");

        assertThat(list).isEmpty();
    }

    // =========================================================================
    // 18. parseChecklistProgress() - UTC052 to UTC053
    // =========================================================================
    @Test
    @DisplayName("UTC052 - parseChecklistProgress() - Normal: Parse map tiến độ JSON hợp lệ")
    void parseChecklistProgress_Success_ParsesValidJsonMap() {
        String json = "{\"chk_1\":{\"isCompleted\":true}}";

        Map<String, ChecklistProgressDto> map = workerJobService.parseChecklistProgress(json);

        assertThat(map).containsKey("chk_1");
        assertThat(map.get("chk_1").getIsCompleted()).isTrue();
    }

    @Test
    @DisplayName("UTC053 - parseChecklistProgress() - Boundary: Chuỗi rỗng hoặc null trả về map rỗng")
    void parseChecklistProgress_Boundary_NullOrEmptyReturnsEmptyMap() {
        assertThat(workerJobService.parseChecklistProgress(null)).isEmpty();
        assertThat(workerJobService.parseChecklistProgress("")).isEmpty();
    }

    // =========================================================================
    // 19. createReview() - UTC054 to UTC055
    // =========================================================================
    @Test
    @DisplayName("UTC054 - createReview() - Normal: Worker gửi đánh giá EO thành công")
    void createReview_Success_CreatesReviewForOrganizer() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();
        CreateReviewRequestDto req = CreateReviewRequestDto.builder()
                .rating(5).criteriaWorkEnvironment(5).criteriaSupport(5).criteriaPaymentTiming(5)
                .comment("Ban tổ chức hỗ trợ chu đáo").build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(reviewRepository.findByAssignmentIdAndReviewerId(assignmentId, userId)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> {
            Review r = i.getArgument(0);
            r.setReviewId(UUID.randomUUID());
            return r;
        });

        ReviewResponseDto res = workerJobService.createReview(userId, assignmentId, req);

        assertThat(res.getRating()).isEqualTo(5);
        assertThat(res.getComment()).contains("chu đáo");
        verify(reviewRepository).save(any());
    }

    @Test
    @DisplayName("UTC055 - createReview() - Abnormal: Đã đánh giá trước đó ném BadRequestException")
    void createReview_Abnormal_DuplicateReviewThrowsBadRequestException() {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(activeShift).worker(worker).status(AssignmentStatus.CONFIRMED).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(reviewRepository.findByAssignmentIdAndReviewerId(assignmentId, userId))
                .thenReturn(Optional.of(Review.builder().build()));

        assertThatThrownBy(() -> workerJobService.createReview(userId, assignmentId, CreateReviewRequestDto.builder().build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bạn đã gửi đánh giá");
    }

    // =========================================================================
    // 20. getAssignmentReview() - UTC056 to UTC057
    // =========================================================================
    @Test
    @DisplayName("UTC056 - getAssignmentReview() - Normal: Lấy đánh giá đã thực hiện cho ca làm việc")
    void getAssignmentReview_Success_ReturnsReviewDto() {
        UUID assignmentId = UUID.randomUUID();
        Review r = Review.builder().reviewId(UUID.randomUUID()).assignmentId(assignmentId).reviewerId(userId).rating(4).comment("Tốt").build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(reviewRepository.findByAssignmentIdAndReviewerId(assignmentId, userId)).thenReturn(Optional.of(r));

        Optional<ReviewResponseDto> opt = workerJobService.getAssignmentReview(userId, assignmentId);

        assertThat(opt).isPresent();
        assertThat(opt.get().getRating()).isEqualTo(4);
    }

    @Test
    @DisplayName("UTC057 - getAssignmentReview() - Boundary: Chưa có đánh giá trả về Optional.empty()")
    void getAssignmentReview_Boundary_NoReviewReturnsEmpty() {
        UUID assignmentId = UUID.randomUUID();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(reviewRepository.findByAssignmentIdAndReviewerId(assignmentId, userId)).thenReturn(Optional.empty());

        Optional<ReviewResponseDto> opt = workerJobService.getAssignmentReview(userId, assignmentId);

        assertThat(opt).isEmpty();
    }

    // =========================================================================
    // 21. getIncomeSummary() - UTC058 to UTC060
    // =========================================================================
    @Test
    @DisplayName("UTC058 - getIncomeSummary() - Normal: Thống kê thu nhập đã nhận, đang chờ và tổng giờ làm")
    void getIncomeSummary_Success_CalculatesEarningsAndHours() {
        Payment paid = Payment.builder().declaredAmount(BigDecimal.valueOf(200000)).status(PaymentStatus.CONFIRMED_FULL).build();
        Payment pending = Payment.builder().agreedAmount(BigDecimal.valueOf(150000)).status(PaymentStatus.DECLARED_PENDING_WORKER).build();
        AttendanceLog log = AttendanceLog.builder().hoursWorked(BigDecimal.valueOf(4.5)).build();

        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(paymentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId())).thenReturn(List.of(paid, pending));
        when(attendanceLogRepository.findByWorker_WorkerId(worker.getWorkerId())).thenReturn(List.of(log));

        WorkerIncomeSummaryDto summary = workerJobService.getIncomeSummary(userId);

        assertThat(summary.getTotalEarnings()).isEqualTo(BigDecimal.valueOf(200000));
        assertThat(summary.getPendingEarnings()).isEqualTo(BigDecimal.valueOf(150000));
        assertThat(summary.getTotalHoursWorked()).isEqualTo(BigDecimal.valueOf(4.5));
        assertThat(summary.getCompletedShiftsCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("UTC059 - getIncomeSummary() - Boundary: Chưa có thanh toán nào trả về 0 thu nhập")
    void getIncomeSummary_Boundary_NoPaymentsOrLogsReturnsZero() {
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(paymentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId())).thenReturn(List.of());
        when(attendanceLogRepository.findByWorker_WorkerId(worker.getWorkerId())).thenReturn(List.of());

        WorkerIncomeSummaryDto summary = workerJobService.getIncomeSummary(userId);

        assertThat(summary.getTotalEarnings()).isEqualTo(BigDecimal.ZERO);
        assertThat(summary.getPendingEarnings()).isEqualTo(BigDecimal.ZERO);
        assertThat(summary.getTotalHoursWorked()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("UTC060 - getIncomeSummary() - Boundary: RatingAvg null mặc định là BigDecimal.ZERO")
    void getIncomeSummary_Boundary_NullRatingDefaultsToZero() {
        worker.setRatingAvg(null);
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(paymentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId())).thenReturn(List.of());
        when(attendanceLogRepository.findByWorker_WorkerId(worker.getWorkerId())).thenReturn(List.of());

        WorkerIncomeSummaryDto summary = workerJobService.getIncomeSummary(userId);

        assertThat(summary.getAvgRating()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("UTC060b - getIncomeSummary() - Boundary: Payment có cả declaredAmount và agreedAmount đều null tính bằng ZERO an toàn")
    void getIncomeSummary_Boundary_NullPaymentAmountsCalculatesZeroGracefully() {
        Payment nullAmountPayment = Payment.builder().declaredAmount(null).agreedAmount(null).status(PaymentStatus.CONFIRMED_FULL).build();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(paymentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId())).thenReturn(List.of(nullAmountPayment));
        when(attendanceLogRepository.findByWorker_WorkerId(worker.getWorkerId())).thenReturn(List.of());

        WorkerIncomeSummaryDto summary = workerJobService.getIncomeSummary(userId);

        assertThat(summary.getTotalEarnings()).isEqualTo(BigDecimal.ZERO);
    }

}
