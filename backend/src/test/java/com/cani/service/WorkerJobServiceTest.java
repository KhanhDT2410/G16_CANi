package com.cani.service;

import com.cani.dto.worker.ShiftAssignmentResponseDto;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.ProposalStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.BadRequestException;
import com.cani.repository.DispatchProposalRepository;
import com.cani.repository.EventRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import com.cani.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @org.mockito.Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks
    private WorkerJobService workerJobService;

    private User workerUser;
    private Worker worker;
    private Shift activeShift;
    private DispatchProposal pendingProposal;

    @BeforeEach
    void setUp() {
        workerUser = User.builder()
                .userId(UUID.randomUUID())
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();

        worker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(workerUser)
                .fullName("Lê Văn Worker")
                .isOnline(true)
                .reliabilityScore(90)
                .build();

        activeShift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .jobType("Bảo vệ sự kiện")
                .startTime(Instant.now().plusSeconds(3600 * 24))
                .endTime(Instant.now().plusSeconds(3600 * 28))
                .requiredWorkers(2)
                .confirmedWorkers(0)
                .remainingWorkers(2)
                .hourlyWage(BigDecimal.valueOf(35000))
                .status(ShiftStatus.ACTIVE)
                .build();

        pendingProposal = DispatchProposal.builder()
                .proposalId(UUID.randomUUID())
                .shift(activeShift)
                .worker(worker)
                .matchScore(BigDecimal.valueOf(88.5))
                .status(ProposalStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    @DisplayName("Nhận việc thành công: Atomic Increment trả về 1, tạo ShiftAssignment CONFIRMED")
    void shouldAcceptProposalSuccessfully() {
        when(workerRepository.findByUser_UserId(workerUser.getUserId())).thenReturn(Optional.of(worker));
        when(dispatchProposalRepository.findById(pendingProposal.getProposalId())).thenReturn(Optional.of(pendingProposal));
        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(List.of());
        when(shiftRepository.incrementConfirmedWorkersAtomic(activeShift.getShiftId())).thenReturn(1);
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class))).thenAnswer(invocation -> {
            ShiftAssignment sa = invocation.getArgument(0);
            sa.setAssignmentId(UUID.randomUUID());
            return sa;
        });

        ShiftAssignmentResponseDto response = workerJobService.acceptProposal(workerUser.getUserId(), pendingProposal.getProposalId());

        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.CONFIRMED);
        assertThat(pendingProposal.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        verify(shiftAssignmentRepository, times(1)).save(any(ShiftAssignment.class));
    }

    @Test
    @DisplayName("Tranh chấp đồng thời (Race condition): Nếu hết quân số, ném BadRequestException")
    void shouldRejectWhenShiftIsAlreadyFull() {
        when(workerRepository.findByUser_UserId(workerUser.getUserId())).thenReturn(Optional.of(worker));
        when(dispatchProposalRepository.findById(pendingProposal.getProposalId())).thenReturn(Optional.of(pendingProposal));
        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(List.of());
        when(shiftRepository.incrementConfirmedWorkersAtomic(activeShift.getShiftId())).thenReturn(0); // Hết chỗ!

        assertThatThrownBy(() -> workerJobService.acceptProposal(workerUser.getUserId(), pendingProposal.getProposalId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vừa được người khác nhận đủ");

        assertThat(pendingProposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("UC-45 Checklist: Parse an toàn template cả khi lưu mảng String cũ lẫn mảng Object")
    void shouldParseStringArrayTemplateResiliently() {
        String legacyStringArray = "[\"Có mặt trước 15-30 phút\", \"Đồng phục chỉn chu\"]";
        List<com.cani.dto.worker.ShiftChecklistItemDto> items = workerJobService.parseChecklistTemplate(legacyStringArray);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getId()).isEqualTo("chk_1");
        assertThat(items.get(0).getTitle()).isEqualTo("Có mặt trước 15-30 phút");
        assertThat(items.get(0).getIsCompleted()).isFalse();

        String objectArray = "[{\"id\":\"chk_custom\",\"title\":\"Quét QR đầu ca\"}]";
        List<com.cani.dto.worker.ShiftChecklistItemDto> objectItems = workerJobService.parseChecklistTemplate(objectArray);
        assertThat(objectItems).hasSize(1);
        assertThat(objectItems.get(0).getId()).isEqualTo("chk_custom");
        assertThat(objectItems.get(0).getTitle()).isEqualTo("Quét QR đầu ca");
    }

    @Test
    @DisplayName("UC-45 Checklist: getShiftChecklist merge đúng tiến độ riêng của từng Worker")
    void shouldGetShiftChecklistMergedWithWorkerProgress() {
        UUID assignmentId = UUID.randomUUID();
        activeShift.setChecklist("[\"Có mặt trước 15 phút\", \"Đồng phục đúng quy định\"]");

        ShiftAssignment assignment = ShiftAssignment.builder()
                .assignmentId(assignmentId)
                .shift(activeShift)
                .worker(worker)
                .checklistProgress("{\"chk_1\":{\"isCompleted\":true,\"completedAt\":\"2026-11-04T12:00:00Z\"}}")
                .build();

        when(workerRepository.findByUser_UserId(workerUser.getUserId())).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        List<com.cani.dto.worker.ShiftChecklistItemDto> checklist = workerJobService.getShiftChecklist(workerUser.getUserId(), assignmentId);

        assertThat(checklist).hasSize(2);
        assertThat(checklist.get(0).getId()).isEqualTo("chk_1");
        assertThat(checklist.get(0).getIsCompleted()).isTrue();
        assertThat(checklist.get(0).getCompletedAt()).isNotNull();

        assertThat(checklist.get(1).getId()).isEqualTo("chk_2");
        assertThat(checklist.get(1).getIsCompleted()).isFalse();
    }

    @Test
    @DisplayName("UC-45 Checklist: updateShiftChecklist chỉ lưu vào ShiftAssignment, KHÔNG ghi đè Shift")
    void shouldUpdateShiftChecklistInAssignmentWithoutModifyingShift() {
        UUID assignmentId = UUID.randomUUID();
        String originalShiftChecklist = "[\"Việc 1\", \"Việc 2\"]";
        activeShift.setChecklist(originalShiftChecklist);

        ShiftAssignment assignment = ShiftAssignment.builder()
                .assignmentId(assignmentId)
                .shift(activeShift)
                .worker(worker)
                .checklistProgress(null)
                .build();

        when(workerRepository.findByUser_UserId(workerUser.getUserId())).thenReturn(Optional.of(worker));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class))).thenAnswer(i -> i.getArgument(0));

        com.cani.dto.worker.UpdateChecklistRequestDto request = com.cani.dto.worker.UpdateChecklistRequestDto.builder()
                .items(List.of(
                        com.cani.dto.worker.ShiftChecklistItemDto.builder().id("chk_1").isCompleted(true).build()
                ))
                .build();

        List<com.cani.dto.worker.ShiftChecklistItemDto> result = workerJobService.updateShiftChecklist(workerUser.getUserId(), assignmentId, request);

        // Verify assignment saved with jsonb progress
        verify(shiftAssignmentRepository, times(1)).save(assignment);
        assertThat(assignment.getChecklistProgress()).contains("\"chk_1\"");
        assertThat(assignment.getChecklistProgress()).contains("\"isCompleted\":true");

        // Verify shift was NEVER modified or saved!
        verify(shiftRepository, never()).save(any());
        assertThat(activeShift.getChecklist()).isEqualTo(originalShiftChecklist);

        // Result reflects merged progress
        assertThat(result.get(0).getIsCompleted()).isTrue();
        assertThat(result.get(1).getIsCompleted()).isFalse();
    }
}
