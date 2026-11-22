package com.cani.service.invite;

import com.cani.dto.invite.CreateInviteRequestDto;
import com.cani.dto.invite.InviteResponseDto;
import com.cani.dto.invite.JoinInviteResponseDto;
import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.repository.*;
import com.cani.service.InviteService;
import com.cani.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock
    private InviteRepository inviteRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private WorkerGroupMembershipRepository workerGroupMembershipRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private InviteService inviteService;

    private UUID eoUserId;
    private UUID workerUserId;
    private EventOrganizer eo;
    private Event inviteEvent;
    private Shift inviteShift;
    private Worker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inviteService, "workerGroupMembershipRepository", workerGroupMembershipRepository);
        ReflectionTestUtils.setField(inviteService, "notificationService", notificationService);

        eoUserId = UUID.randomUUID();
        workerUserId = UUID.randomUUID();

        User eoUser = User.builder().userId(eoUserId).role(UserRole.EVENT_ORGANIZER).status(UserStatus.ACTIVE).build();
        eo = EventOrganizer.builder().eoId(UUID.randomUUID()).user(eoUser).build();

        inviteEvent = Event.builder()
                .eventId(UUID.randomUUID())
                .eventOrganizer(eo)
                .name("Gala Nội Bộ INVITE")
                .venueAddress("123 Nguyễn Huệ, Q1")
                .mode(EventMode.INVITE)
                .status(EventStatus.ACTIVE)
                .build();

        inviteShift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(inviteEvent)
                .jobType("Lễ tân VIP")
                .startTime(Instant.now().plus(24, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(28, ChronoUnit.HOURS))
                .requiredWorkers(3)
                .confirmedWorkers(0)
                .remainingWorkers(3)
                .hourlyWage(BigDecimal.valueOf(60000))
                .status(ShiftStatus.ACTIVE)
                .build();

        User wUser = User.builder().userId(workerUserId).role(UserRole.WORKER).status(UserStatus.ACTIVE).build();
        worker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(wUser)
                .fullName("Nguyễn Thị Invite")
                .kycStatus(KycStatus.APPROVED)
                .build();
    }

    @Test
    @DisplayName("Direct Invitation trong INVITE mode: EO tạo mã mời và Worker join thành công với source DIRECT")
    void inviteMode_DirectInviteAndJoin_WorksIndependentlyOfMatching() {
        when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(eo));
        when(shiftRepository.findById(inviteShift.getShiftId())).thenReturn(Optional.of(inviteShift));
        when(inviteRepository.findFirstByShift_ShiftIdAndIsActiveTrueOrderByCreatedAtDesc(inviteShift.getShiftId()))
                .thenReturn(Optional.empty());
        when(inviteRepository.save(any(Invite.class))).thenAnswer(i -> {
            Invite inv = i.getArgument(0);
            inv.setInviteId(UUID.randomUUID());
            return inv;
        });

        CreateInviteRequestDto req = new CreateInviteRequestDto();
        req.setMaxUses(3);
        req.setExpiryHours(24);
        InviteResponseDto createdInvite = inviteService.generateShiftInvite(eoUserId, inviteShift.getShiftId(), req);

        assertNotNull(createdInvite.getCode());
        assertEquals(3, createdInvite.getMaxUses());

        Invite storedInvite = Invite.builder()
                .inviteId(createdInvite.getInviteId())
                .event(inviteEvent)
                .shift(inviteShift)
                .code(createdInvite.getCode())
                .maxUses(3)
                .usedCount(0)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .isActive(true)
                .build();

        when(workerRepository.findByUser_UserId(workerUserId)).thenReturn(Optional.of(worker));
        when(inviteRepository.findByCodeAndIsActiveTrue(createdInvite.getCode())).thenReturn(Optional.of(storedInvite));
        when(shiftAssignmentRepository.findByShift_ShiftIdAndWorker_WorkerId(inviteShift.getShiftId(), worker.getWorkerId()))
                .thenReturn(Optional.empty());
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class))).thenAnswer(i -> {
            ShiftAssignment sa = i.getArgument(0);
            sa.setAssignmentId(UUID.randomUUID());
            return sa;
        });

        JoinInviteResponseDto joined = inviteService.joinViaInviteCode(workerUserId, createdInvite.getCode());

        assertEquals(AssignmentStatus.CONFIRMED.name(), joined.getStatus());
        assertEquals(1, storedInvite.getUsedCount());
        assertEquals(1, inviteShift.getConfirmedWorkers());
    }
}
