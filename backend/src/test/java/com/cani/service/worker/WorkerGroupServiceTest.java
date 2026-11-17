package com.cani.service.worker;

import com.cani.dto.group.CreateWorkerGroupRequestDto;
import com.cani.dto.group.GroupShiftResponseDto;
import com.cani.dto.group.InviteGroupMemberRequestDto;
import com.cani.dto.group.UpdateWorkerGroupRequestDto;
import com.cani.dto.group.WorkerGroupMemberResponseDto;
import com.cani.dto.group.WorkerGroupResponseDto;
import com.cani.entity.Shift;
import com.cani.entity.ShiftAssignment;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerGroup;
import com.cani.entity.WorkerGroupMembership;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.GroupRole;
import com.cani.entity.enums.MembershipStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.WorkerGroupMembershipRepository;
import com.cani.repository.WorkerGroupRepository;
import com.cani.repository.WorkerRepository;
import com.cani.service.WorkerGroupService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
}
