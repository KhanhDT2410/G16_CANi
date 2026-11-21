package com.cani.service.worker;

import com.cani.dto.group.AcceptGroupProposalResponseDto;
import com.cani.dto.group.CreateWorkerGroupRequestDto;
import com.cani.dto.group.GroupProposalResponseDto;
import com.cani.dto.group.GroupShiftResponseDto;
import com.cani.dto.group.InviteGroupMemberRequestDto;
import com.cani.dto.group.UpdateWorkerGroupRequestDto;
import com.cani.dto.group.WorkerGroupMemberResponseDto;
import com.cani.dto.group.WorkerGroupResponseDto;
import com.cani.entity.DispatchProposal;
import com.cani.entity.Event;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Shift;
import com.cani.entity.ShiftAssignment;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerGroup;
import com.cani.entity.WorkerGroupMembership;
import com.cani.entity.enums.KycStatus;
import com.cani.entity.enums.AssignmentSource;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.GroupRole;
import com.cani.entity.enums.MembershipStatus;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.ProposalStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.UserRole;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.DispatchProposalRepository;
import com.cani.repository.EventRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerGroupMembershipRepository;
import com.cani.repository.WorkerGroupRepository;
import com.cani.repository.WorkerRepository;
import com.cani.service.DirectPaymentService;
import com.cani.service.MatchingDispatchService;
import com.cani.service.NotificationService;
import com.cani.service.WorkerGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerGroupServiceTest {

    @Mock
    private WorkerGroupRepository workerGroupRepository;

    @Mock
    private WorkerGroupMembershipRepository membershipRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private DispatchProposalRepository dispatchProposalRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DirectPaymentService directPaymentService;

    @Mock
    private MatchingDispatchService matchingDispatchService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private WorkerGroupService workerGroupService;

    private UUID leaderUserId;
    private UUID leaderWorkerId;
    private Worker leaderWorker;
    private User leaderUser;

    private UUID memberUserId;
    private UUID memberWorkerId;
    private Worker memberWorker;
    private User memberUser;

    private UUID groupId;
    private WorkerGroup mockGroup;

    @BeforeEach
    void setUp() {
        leaderUserId = UUID.randomUUID();
        leaderWorkerId = UUID.randomUUID();
        leaderUser = User.builder().userId(leaderUserId).fullName("Nguyễn Văn Trưởng").phone("0912345678").build();
        leaderWorker = Worker.builder().workerId(leaderWorkerId).user(leaderUser).fullName("Nguyễn Văn Trưởng").reliabilityScore(95).ratingAvg(BigDecimal.valueOf(4.8)).build();

        memberUserId = UUID.randomUUID();
        memberWorkerId = UUID.randomUUID();
        memberUser = User.builder().userId(memberUserId).fullName("Trần Thị Thợ").phone("0987654321").build();
        memberWorker = Worker.builder().workerId(memberWorkerId).user(memberUser).fullName("Trần Thị Thợ").reliabilityScore(85).ratingAvg(null).build();

        groupId = UUID.randomUUID();
        mockGroup = WorkerGroup.builder()
                .groupId(groupId)
                .name("Đội Phục Vụ Tiệc Sài Gòn")
                .description("Chuyên cung cấp nhân sự tiệc cưới")
                .skillFocus("F&B")
                .leaderWorkerId(leaderWorkerId)
                .minSize(2)
                .maxSize(5)
                .memberCount(1)
                .avgRating(BigDecimal.valueOf(4.8))
                .reliabilityScore(95)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("UTC080 - createGroup() - Normal: Leader creates group successfully")
    void createGroup_Success() {
        CreateWorkerGroupRequestDto req = CreateWorkerGroupRequestDto.builder()
                .name("Đội Phục Vụ Tiệc Sài Gòn")
                .description("Chuyên phục vụ sự kiện")
                .skillFocus("F&B")
                .minSize(2)
                .maxSize(10)
                .build();

        when(workerRepository.findByUser_UserId(leaderUserId)).thenReturn(Optional.of(leaderWorker));
        when(workerGroupRepository.existsByNameIgnoreCase("Đội Phục Vụ Tiệc Sài Gòn")).thenReturn(false);
        when(workerGroupRepository.save(any(WorkerGroup.class))).thenAnswer(i -> {
            WorkerGroup g = i.getArgument(0);
            g.setGroupId(groupId);
            return g;
        });

        WorkerGroupResponseDto result = workerGroupService.createGroup(leaderUserId, req);

        assertNotNull(result);
        assertEquals("Đội Phục Vụ Tiệc Sài Gòn", result.getName());
        assertEquals(leaderWorkerId, result.getLeaderWorkerId());
        verify(membershipRepository).save(any(WorkerGroupMembership.class));
    }

    @Test
    @DisplayName("UTC081 - createGroup() - Abnormal: Duplicate group name throws BadRequest")
    void createGroup_DuplicateName_ThrowsBadRequest() {
        CreateWorkerGroupRequestDto req = CreateWorkerGroupRequestDto.builder()
                .name("Đội Phục Vụ Tiệc Sài Gòn")
                .build();

        when(workerRepository.findByUser_UserId(leaderUserId)).thenReturn(Optional.of(leaderWorker));
        when(workerGroupRepository.existsByNameIgnoreCase("Đội Phục Vụ Tiệc Sài Gòn")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> workerGroupService.createGroup(leaderUserId, req));
    }

    @Test
    @DisplayName("UTC082 - updateGroup() - Normal: Leader updates group info successfully")
    void updateGroup_Success_LeaderOnly() {
        UpdateWorkerGroupRequestDto req = UpdateWorkerGroupRequestDto.builder()
                .name("Đội Phục Vụ VIP Sài Gòn")
                .maxSize(8)
                .build();

        when(workerRepository.findByUser_UserId(leaderUserId)).thenReturn(Optional.of(leaderWorker));
        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));
        when(workerGroupRepository.save(any(WorkerGroup.class))).thenReturn(mockGroup);

        WorkerGroupResponseDto result = workerGroupService.updateGroup(leaderUserId, groupId, req);

        assertNotNull(result);
        verify(workerGroupRepository).save(mockGroup);
    }

    @Test
    @DisplayName("UTC083 - updateGroup() - Abnormal: Non-leader updating group throws BadRequest")
    void updateGroup_NotLeader_ThrowsBadRequest() {
        UpdateWorkerGroupRequestDto req = UpdateWorkerGroupRequestDto.builder().name("New Name").build();

        when(workerRepository.findByUser_UserId(memberUserId)).thenReturn(Optional.of(memberWorker));
        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));

        assertThrows(BadRequestException.class, () -> workerGroupService.updateGroup(memberUserId, groupId, req));
    }

    @Test
    @DisplayName("UTC084 - getGroupMembers() - Normal: Member sees full phone, Non-member sees masked phone (PII Protection)")
    void getGroupMembers_PII_Masking() {
        WorkerGroupMembership m1 = WorkerGroupMembership.builder()
                .membershipId(UUID.randomUUID())
                .workerGroup(mockGroup)
                .worker(leaderWorker)
                .roleInGroup(GroupRole.LEADER)
                .status(MembershipStatus.ACTIVE)
                .build();

        WorkerGroupMembership m2 = WorkerGroupMembership.builder()
                .membershipId(UUID.randomUUID())
                .workerGroup(mockGroup)
                .worker(memberWorker)
                .roleInGroup(GroupRole.MEMBER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));
        when(membershipRepository.findByGroupIdWithWorkerDetails(groupId)).thenReturn(List.of(m1, m2));

        // 1. Leader views: Sees full phone
        when(workerRepository.findByUser_UserId(leaderUserId)).thenReturn(Optional.of(leaderWorker));
        List<WorkerGroupMemberResponseDto> leaderView = workerGroupService.getGroupMembers(leaderUserId, groupId);
        assertEquals("0912345678", leaderView.get(0).getPhone());
        assertEquals("0987654321", leaderView.get(1).getPhone());

        // 2. Outsider (non-member) views: Phone is masked
        UUID outsiderUserId = UUID.randomUUID();
        Worker outsiderWorker = Worker.builder().workerId(UUID.randomUUID()).build();
        when(workerRepository.findByUser_UserId(outsiderUserId)).thenReturn(Optional.of(outsiderWorker));
        when(membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, outsiderWorker.getWorkerId()))
                .thenReturn(Optional.empty());

        List<WorkerGroupMemberResponseDto> outsiderView = workerGroupService.getGroupMembers(outsiderUserId, groupId);
        assertEquals("091****678", outsiderView.get(0).getPhone());
        assertEquals("098****321", outsiderView.get(1).getPhone());
    }

    @Test
    @DisplayName("UTC085 - inviteMember() - Normal: Leader invites candidate successfully")
    void inviteMember_Success() {
        InviteGroupMemberRequestDto req = InviteGroupMemberRequestDto.builder()
                .workerId(memberWorkerId)
                .roleTitle("Phục vụ chính")
                .build();

        when(workerRepository.findByUser_UserId(leaderUserId)).thenReturn(Optional.of(leaderWorker));
        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));
        when(workerRepository.findById(memberWorkerId)).thenReturn(Optional.of(memberWorker));
        when(membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, memberWorkerId)).thenReturn(Optional.empty());
        when(membershipRepository.countByWorkerGroup_GroupIdAndStatus(groupId, MembershipStatus.ACTIVE)).thenReturn(1);
        when(membershipRepository.save(any(WorkerGroupMembership.class))).thenAnswer(i -> i.getArgument(0));

        WorkerGroupMemberResponseDto result = workerGroupService.inviteMember(leaderUserId, groupId, req);

        assertNotNull(result);
        assertEquals(MembershipStatus.PENDING, result.getStatus());
        verify(membershipRepository).save(any(WorkerGroupMembership.class));
    }

    @Test
    @DisplayName("UTC086 - inviteMember() - Boundary: Cannot invite when group reaches maxSize")
    void inviteMember_GroupFull_ThrowsBadRequest() {
        InviteGroupMemberRequestDto req = InviteGroupMemberRequestDto.builder().workerId(memberWorkerId).build();

        when(workerRepository.findByUser_UserId(leaderUserId)).thenReturn(Optional.of(leaderWorker));
        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));
        when(workerRepository.findById(memberWorkerId)).thenReturn(Optional.of(memberWorker));
        when(membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, memberWorkerId)).thenReturn(Optional.empty());
        when(membershipRepository.countByWorkerGroup_GroupIdAndStatus(groupId, MembershipStatus.ACTIVE)).thenReturn(5); // maxSize is 5

        assertThrows(BadRequestException.class, () -> workerGroupService.inviteMember(leaderUserId, groupId, req));
    }

    @Test
    @DisplayName("UTC087 - acceptGroupInvite() - Normal: Pending member accepts and becomes ACTIVE")
    void acceptGroupInvite_Success() {
        WorkerGroupMembership pendingMembership = WorkerGroupMembership.builder()
                .membershipId(UUID.randomUUID())
                .workerGroup(mockGroup)
                .worker(memberWorker)
                .status(MembershipStatus.PENDING)
                .build();

        when(workerRepository.findByUser_UserId(memberUserId)).thenReturn(Optional.of(memberWorker));
        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));
        when(membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, memberWorkerId))
                .thenReturn(Optional.of(pendingMembership));
        when(membershipRepository.countByWorkerGroup_GroupIdAndStatus(groupId, MembershipStatus.ACTIVE)).thenReturn(1);
        when(membershipRepository.save(any(WorkerGroupMembership.class))).thenAnswer(i -> i.getArgument(0));

        WorkerGroupMemberResponseDto result = workerGroupService.acceptGroupInvite(memberUserId, groupId);

        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.getStatus());
        verify(membershipRepository).save(pendingMembership);
    }

    @Test
    @DisplayName("UTC088 - acceptGroupInvite() - Security: Kicked member (REMOVED) cannot self-rejoin without pending invite")
    void acceptGroupInvite_KickedMember_ThrowsBadRequest() {
        WorkerGroupMembership removedMembership = WorkerGroupMembership.builder()
                .membershipId(UUID.randomUUID())
                .workerGroup(mockGroup)
                .worker(memberWorker)
                .status(MembershipStatus.REMOVED) // Previously kicked!
                .build();

        when(workerRepository.findByUser_UserId(memberUserId)).thenReturn(Optional.of(memberWorker));
        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));
        when(membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, memberWorkerId))
                .thenReturn(Optional.of(removedMembership));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> workerGroupService.acceptGroupInvite(memberUserId, groupId));
        assertTrue(ex.getMessage().contains("lời mời hợp lệ"));
    }

    @Test
    @DisplayName("UTC089 - acceptGroupInvite() - Boundary: Cannot accept if group is full")
    void acceptGroupInvite_GroupFull_ThrowsBadRequest() {
        WorkerGroupMembership pendingMembership = WorkerGroupMembership.builder()
                .membershipId(UUID.randomUUID())
                .workerGroup(mockGroup)
                .worker(memberWorker)
                .status(MembershipStatus.PENDING)
                .build();

        when(workerRepository.findByUser_UserId(memberUserId)).thenReturn(Optional.of(memberWorker));
        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));
        when(membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, memberWorkerId))
                .thenReturn(Optional.of(pendingMembership));
        when(membershipRepository.countByWorkerGroup_GroupIdAndStatus(groupId, MembershipStatus.ACTIVE)).thenReturn(5); // maxSize = 5

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> workerGroupService.acceptGroupInvite(memberUserId, groupId));
        assertTrue(ex.getMessage().contains("tối đa"));
    }

    @Test
    @DisplayName("UTC090 - removeMember() - Normal: Leader kicks member successfully")
    void removeMember_LeaderKicksMember_Success() {
        WorkerGroupMembership activeMember = WorkerGroupMembership.builder()
                .membershipId(UUID.randomUUID())
                .workerGroup(mockGroup)
                .worker(memberWorker)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(workerRepository.findByUser_UserId(leaderUserId)).thenReturn(Optional.of(leaderWorker));
        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));
        when(membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, memberWorkerId))
                .thenReturn(Optional.of(activeMember));

        workerGroupService.removeMember(leaderUserId, groupId, memberWorkerId);

        assertEquals(MembershipStatus.REMOVED, activeMember.getStatus());
        verify(membershipRepository).save(activeMember);
    }

    @Test
    @DisplayName("UTC091 - removeMember() - Boundary: Leader cannot leave group if other active members exist")
    void removeMember_LeaderCannotLeaveWhenMembersExist() {
        when(workerRepository.findByUser_UserId(leaderUserId)).thenReturn(Optional.of(leaderWorker));
        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));
        when(membershipRepository.countByWorkerGroup_GroupIdAndStatus(groupId, MembershipStatus.ACTIVE)).thenReturn(3);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> workerGroupService.removeMember(leaderUserId, groupId, leaderWorkerId));
        assertTrue(ex.getMessage().contains("chuyển giao quyền Trưởng nhóm"));
    }

    @Test
    @DisplayName("UTC092 - recalculateGroupMetrics() - Integrity: Unrated workers (null rating) do not inflate avgRating to 5.0")
    void recalculateGroupMetrics_HonestRating() {
        // Leader has 4.5, member has null (no rating yet)
        Worker w1 = Worker.builder().workerId(leaderWorkerId).ratingAvg(BigDecimal.valueOf(4.5)).reliabilityScore(90).build();
        Worker w2 = Worker.builder().workerId(memberWorkerId).ratingAvg(null).reliabilityScore(80).build();

        WorkerGroupMembership m1 = WorkerGroupMembership.builder().worker(w1).status(MembershipStatus.ACTIVE).build();
        WorkerGroupMembership m2 = WorkerGroupMembership.builder().worker(w2).status(MembershipStatus.ACTIVE).build();

        when(workerRepository.findByUser_UserId(leaderUserId)).thenReturn(Optional.of(leaderWorker));
        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));
        when(membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, memberWorkerId))
                .thenReturn(Optional.of(m2));
        when(membershipRepository.findByWorkerGroup_GroupIdAndStatus(groupId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(m1, m2));

        workerGroupService.removeMember(leaderUserId, groupId, memberWorkerId);

        // Group avgRating should only be calculated from w1 (4.5), NOT (4.5 + 5.0)/2 = 4.75!
        assertEquals(BigDecimal.valueOf(4.50).setScale(2), mockGroup.getAvgRating());
        assertEquals(85, mockGroup.getReliabilityScore()); // (90 + 80) / 2
    }

    @Test
    @DisplayName("UTC093 - getGroupShifts() - Normal: Active member views group shifts")
    void getGroupShifts_Success_Member() {
        WorkerGroupMembership activeMember = WorkerGroupMembership.builder()
                .worker(memberWorker)
                .status(MembershipStatus.ACTIVE)
                .build();

        Shift mockShift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .jobType("Phục vụ bàn")
                .hourlyWage(BigDecimal.valueOf(50000))
                .build();

        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(UUID.randomUUID())
                .shift(mockShift)
                .worker(memberWorker)
                .status(AssignmentStatus.CONFIRMED)
                .groupId(groupId)
                .isGroupLeader(false)
                .build();

        when(workerRepository.findByUser_UserId(memberUserId)).thenReturn(Optional.of(memberWorker));
        when(workerGroupRepository.existsById(groupId)).thenReturn(true);
        when(membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, memberWorkerId))
                .thenReturn(Optional.of(activeMember));
        when(shiftAssignmentRepository.findByGroupId(groupId)).thenReturn(List.of(sa));

        List<GroupShiftResponseDto> result = workerGroupService.getGroupShifts(memberUserId, groupId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Phục vụ bàn", result.get(0).getJobType());
        assertEquals(1, result.get(0).getConfirmedCount());
    }

    @Test
    @DisplayName("UTC094 - getGroupShifts() - Abnormal: Non-member throws BadRequest")
    void getGroupShifts_NotMember_ThrowsBadRequest() {
        when(workerRepository.findByUser_UserId(memberUserId)).thenReturn(Optional.of(memberWorker));
        when(workerGroupRepository.existsById(groupId)).thenReturn(true);
        when(membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, memberWorkerId))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> workerGroupService.getGroupShifts(memberUserId, groupId));
    }

    // ============================= TASK 3: Group shift proposals (leader) =============================

    private UUID secondUserId;
    private UUID secondWorkerId;
    private Worker secondWorker;
    private User secondUser;
    private UUID shiftId;
    private Shift task3Shift;
    private Event task3Event;
    private UUID proposalId;
    private DispatchProposal task3Proposal;

    private WorkerGroupMembership leaderMembership() {
        return WorkerGroupMembership.builder()
                .workerGroup(mockGroup)
                .worker(leaderWorker)
                .roleInGroup(GroupRole.LEADER)
                .status(MembershipStatus.ACTIVE)
                .build();
    }

    private WorkerGroupMembership memberMembership(Worker w) {
        return WorkerGroupMembership.builder()
                .workerGroup(mockGroup)
                .worker(w)
                .roleInGroup(GroupRole.MEMBER)
                .status(MembershipStatus.ACTIVE)
                .build();
    }

    /**
     * Chuẩn bị context Task 3: leader là leader thật của mockGroup, 3 member ACTIVE (leader + 2),
     * shift ACTIVE cần 3 người còn 2 slot, proposal PENDING chưa hết hạn, mọi member đủ điều kiện.
     * Stub dùng chung đánh dấu lenient (không phải test nào cũng chạm mọi nhánh).
     */
    private void setupTask3Context() {
        leaderWorker.setKycStatus(KycStatus.APPROVED);
        secondUser = User.builder().userId(secondUserId = UUID.randomUUID()).fullName("Lê Văn Hai").build();
        secondWorker = Worker.builder().workerId(secondWorkerId = UUID.randomUUID()).user(secondUser)
                .fullName("Lê Văn Hai").kycStatus(KycStatus.APPROVED).reliabilityScore(90).build();

        User eoUser = User.builder().userId(UUID.randomUUID()).fullName("EO Tiệc Cưới").build();
        task3Event = Event.builder().eventId(UUID.randomUUID()).name("Tiệc Cưới A & B")
                .eventOrganizer(EventOrganizer.builder().user(eoUser).build())
                .build();
        task3Shift = Shift.builder()
                .shiftId(shiftId = UUID.randomUUID())
                .event(task3Event)
                .jobType("Phục vụ bàn")
                .startTime(Instant.now().plus(Duration.ofDays(1)))
                .endTime(Instant.now().plus(Duration.ofDays(1)).plus(Duration.ofHours(8)))
                .hourlyWage(BigDecimal.valueOf(50000))
                .requiredWorkers(3)
                .confirmedWorkers(1)
                .remainingWorkers(2)
                .status(ShiftStatus.ACTIVE)
                .build();

        task3Proposal = DispatchProposal.builder()
                .proposalId(proposalId = UUID.randomUUID())
                .shift(task3Shift)
                .groupId(groupId)
                .source(AssignmentSource.GROUP)
                .status(ProposalStatus.PENDING)
                .matchScore(BigDecimal.valueOf(0.92))
                .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                .build();

        // Leader check: user WORKER + worker = leaderWorkerId của group
        when(userRepository.findById(leaderUserId)).thenReturn(Optional.of(
                User.builder().userId(leaderUserId).role(UserRole.WORKER).build()));
        when(workerRepository.findByUser_UserId(leaderUserId)).thenReturn(Optional.of(leaderWorker));
        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));
        lenient().when(dispatchProposalRepository.findById(proposalId)).thenReturn(Optional.of(task3Proposal));
        lenient().when(membershipRepository.findByWorkerGroup_GroupIdAndStatus(groupId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(leaderMembership(), memberMembership(memberWorker), memberMembership(secondWorker)));
        // Mọi member đủ điều kiện (kết quả re-check)
        lenient().when(matchingDispatchService.groupMemberEligibilityReason(any(Worker.class), eq(task3Shift)))
                .thenReturn(null);
        lenient().when(shiftAssignmentRepository.save(any(ShiftAssignment.class))).thenAnswer(i -> {
            ShiftAssignment a = i.getArgument(0);
            a.setAssignmentId(UUID.randomUUID());
            return a;
        });
        lenient().when(workerRepository.findAllById(anyList())).thenAnswer(i -> {
            @SuppressWarnings("unchecked")
            List<UUID> ids = i.getArgument(0);
            java.util.List<Worker> out = new java.util.ArrayList<>();
            if (ids.contains(memberWorkerId)) out.add(memberWorker);
            if (ids.contains(secondWorkerId)) out.add(secondWorker);
            if (ids.contains(leaderWorkerId)) out.add(leaderWorker);
            return out;
        });
    }

    @Test
    @DisplayName("T3-1 - acceptGroupProposal() - Normal: k=2 → 2 assignment CONFIRMED/GROUP/groupId, isGroupLeader theo role, counters, payment, notify")
    void accept_group_proposal_creates_k_assignments_source_GROUP() {
        setupTask3Context();
        when(shiftRepository.incrementConfirmedWorkersAtomicBy(shiftId, 2)).thenReturn(1);

        AcceptGroupProposalResponseDto result = workerGroupService.acceptGroupProposal(
                leaderUserId, groupId, proposalId, List.of(memberWorkerId, secondWorkerId));

        assertEquals(2, result.getAssignmentIds().size());
        assertEquals(shiftId, result.getShiftId());
        assertEquals(2, result.getMemberCount());

        // 2 ShiftAssignment: CONFIRMED + source GROUP + groupId, isGroupLeader đúng theo role membership
        ArgumentCaptor<ShiftAssignment> captor = ArgumentCaptor.forClass(ShiftAssignment.class);
        verify(shiftAssignmentRepository, times(2)).save(captor.capture());
        List<ShiftAssignment> saved = captor.getAllValues();
        for (ShiftAssignment a : saved) {
            assertEquals(AssignmentStatus.CONFIRMED, a.getStatus());
            assertEquals(AssignmentSource.GROUP, a.getSource());
            assertEquals(groupId, a.getGroupId());
            assertEquals(proposalId, a.getProposalId());
        }
        assertEquals(false, saved.get(0).getIsGroupLeader()); // MEMBER
        assertEquals(false, saved.get(1).getIsGroupLeader()); // MEMBER

        // Counters: shift confirmed += 2, remaining -= 2; event counter += 2
        assertEquals(3, task3Shift.getConfirmedWorkers());
        assertEquals(0, task3Shift.getRemainingWorkers());
        assertEquals(ShiftStatus.FULLY_STAFFED, task3Shift.getStatus());
        assertEquals(2, task3Event.getTotalConfirmedWorkers());
        verify(eventRepository).save(task3Event);

        // Payment per member + proposal ACCEPTED
        verify(directPaymentService, times(2)).createInitialPayment(any(ShiftAssignment.class));
        assertEquals(ProposalStatus.ACCEPTED, task3Proposal.getStatus());
        verify(dispatchProposalRepository).save(task3Proposal);

        // Notify: EO + 2 member + leader = 4 bản FCM
        verify(notificationService, atLeast(4)).createAndSend(any(UUID.class), anyString(), anyString(),
                eq(NotificationType.GROUP), anyString(), isNull(), isNull());
    }

    @Test
    @DisplayName("T3-2 - acceptGroupProposal() - Abnormal: k > claimableSlots → BadRequest, 0 assignment")
    void accept_k_exceeds_claimable_slots_rejects_400() {
        setupTask3Context();
        // Còn 2 slot, đủ 3 member → chọn cả 3 > claimableSlots
        assertThrows(BadRequestException.class, () -> workerGroupService.acceptGroupProposal(
                leaderUserId, groupId, proposalId, List.of(leaderWorkerId, memberWorkerId, secondWorkerId)));
        verify(shiftAssignmentRepository, never()).save(any(ShiftAssignment.class));
        verify(shiftRepository, never()).incrementConfirmedWorkersAtomicBy(any(UUID.class), anyInt());
    }

    @Test
    @DisplayName("T3-3 - acceptGroupProposal() - Conflict: atomic counter trả 0 → AppException 409, không persistence gì")
    void accept_atomic_counter_rejects_409() {
        setupTask3Context();
        when(shiftRepository.incrementConfirmedWorkersAtomicBy(shiftId, 2)).thenReturn(0);

        AppException ex = assertThrows(AppException.class, () -> workerGroupService.acceptGroupProposal(
                leaderUserId, groupId, proposalId, List.of(memberWorkerId, secondWorkerId)));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatus());
        verify(shiftAssignmentRepository, never()).save(any(ShiftAssignment.class));
        verify(directPaymentService, never()).createInitialPayment(any(ShiftAssignment.class));
    }

    @Test
    @DisplayName("T3-4 - acceptGroupProposal() - All-or-nothing: 1 member conflict → cả nhóm fail, 0 assignment")
    void accept_one_member_conflict_rejects_all() {
        setupTask3Context();
        // 1 trong 2 member chọn bị SHIFT_CONFLICT → cả thao tác fail (before any writes)
        when(matchingDispatchService.groupMemberEligibilityReason(eq(memberWorker), eq(task3Shift)))
                .thenReturn("SHIFT_CONFLICT");

        assertThrows(BadRequestException.class, () -> workerGroupService.acceptGroupProposal(
                leaderUserId, groupId, proposalId, List.of(memberWorkerId, secondWorkerId)));
        verify(shiftAssignmentRepository, never()).save(any(ShiftAssignment.class));
        verify(shiftRepository, never()).incrementConfirmedWorkersAtomicBy(any(UUID.class), anyInt());
    }

    @Test
    @DisplayName("T3-5 - acceptGroupProposal() - Security: caller không phải leader → AppException 403")
    void accept_by_non_leader_rejects() {
        // memberUserId không phải leaderWorkerId của mockGroup
        when(userRepository.findById(memberUserId)).thenReturn(Optional.of(
                User.builder().userId(memberUserId).role(UserRole.WORKER).build()));
        when(workerRepository.findByUser_UserId(memberUserId)).thenReturn(Optional.of(memberWorker));
        when(workerGroupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));

        AppException ex = assertThrows(AppException.class, () -> workerGroupService.acceptGroupProposal(
                memberUserId, groupId, proposalId, List.of(memberWorkerId)));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    @DisplayName("T3-6 - rejectGroupProposal() - Normal: proposal → REJECTED + gọi dispatchForShift (re-dispatch)")
    void reject_group_proposal_marks_rejected_and_triggers_redispatch() {
        setupTask3Context();

        workerGroupService.rejectGroupProposal(leaderUserId, groupId, proposalId, "Ca trùng với đội khác");

        assertEquals(ProposalStatus.REJECTED, task3Proposal.getStatus());
        assertNotNull(task3Proposal.getRespondedAt());
        verify(dispatchProposalRepository).save(task3Proposal);
        verify(matchingDispatchService).dispatchForShift(shiftId);
    }

    @Test
    @DisplayName("T3-7 - listGroupProposals() - leader-only + lọc PENDING + EXPIRED trong 24h")
    void list_group_proposals_leader_only_pending_and_expired_24h() {
        setupTask3Context();
        // Chỉ leader là thành viên ACTIVE (override stub lenient của setup)
        when(membershipRepository.findByWorkerGroup_GroupIdAndStatus(groupId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(leaderMembership()));
        when(matchingDispatchService.groupMemberEligibilityReason(any(Worker.class), eq(task3Shift)))
                .thenReturn(null);

        // PENDING proposal (createdAt mới) + EXPIRED proposal (createdAt trong 24h)
        task3Proposal.setStatus(ProposalStatus.PENDING);
        task3Proposal.setCreatedAt(Instant.now());
        DispatchProposal expiredP = DispatchProposal.builder()
                .proposalId(UUID.randomUUID())
                .shift(task3Shift)
                .groupId(groupId)
                .source(AssignmentSource.GROUP)
                .status(ProposalStatus.EXPIRED)
                .matchScore(BigDecimal.valueOf(0.8))
                .createdAt(Instant.now().minus(Duration.ofHours(2)))
                .build();
        DispatchProposal oldExpiredP = DispatchProposal.builder()
                .proposalId(UUID.randomUUID())
                .shift(task3Shift)
                .groupId(groupId)
                .source(AssignmentSource.GROUP)
                .status(ProposalStatus.EXPIRED)
                .createdAt(Instant.now().minus(Duration.ofHours(48)))
                .build();
        when(dispatchProposalRepository.findByGroupIdAndStatus(groupId, ProposalStatus.PENDING))
                .thenReturn(List.of(task3Proposal));
        when(dispatchProposalRepository.findByGroupIdAndStatus(groupId, ProposalStatus.EXPIRED))
                .thenReturn(List.of(expiredP, oldExpiredP));

        List<GroupProposalResponseDto> list = workerGroupService.listGroupProposals(leaderUserId, groupId);

        // Chỉ giữ PENDING + EXPIRED ≤ 24h (bỏ oldExpiredP)
        assertEquals(2, list.size());
        assertEquals(ProposalStatus.PENDING, list.get(0).getStatus());
        assertEquals(ProposalStatus.EXPIRED, list.get(1).getStatus());

        // Non-leader → 403
        when(userRepository.findById(memberUserId)).thenReturn(Optional.of(
                User.builder().userId(memberUserId).role(UserRole.WORKER).build()));
        when(workerRepository.findByUser_UserId(memberUserId)).thenReturn(Optional.of(memberWorker));
        AppException ex = assertThrows(AppException.class,
                () -> workerGroupService.listGroupProposals(memberUserId, groupId));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatus());
    }
}
