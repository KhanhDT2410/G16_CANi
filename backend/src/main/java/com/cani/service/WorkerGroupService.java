package com.cani.service;

import com.cani.dto.group.*;
import com.cani.dto.response.PageResponse;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerGroup;
import com.cani.entity.WorkerGroupMembership;
import com.cani.entity.enums.GroupRole;
import com.cani.entity.enums.MembershipStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.WorkerGroupMembershipRepository;
import com.cani.repository.WorkerGroupRepository;
import com.cani.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerGroupService {

    private final WorkerGroupRepository workerGroupRepository;
    private final WorkerGroupMembershipRepository membershipRepository;
    private final WorkerRepository workerRepository;
    private final com.cani.repository.ShiftAssignmentRepository shiftAssignmentRepository;

    /**
     * UC-70: Worker (Group Leader) tạo nhóm thợ sự kiện mới
     */
    @Transactional
    public WorkerGroupResponseDto createGroup(UUID userId, CreateWorkerGroupRequestDto request) {
        Worker leader = getWorkerByUserId(userId);

        if (workerGroupRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new BadRequestException("Tên nhóm thợ này đã tồn tại, vui lòng chọn tên khác");
        }

        WorkerGroup group = WorkerGroup.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .skillFocus(request.getSkillFocus())
                .leaderWorkerId(leader.getWorkerId())
                .minSize(request.getMinSize() != null ? request.getMinSize() : 5)
                .maxSize(request.getMaxSize() != null ? request.getMaxSize() : 20)
                .memberCount(1)
                .avgRating(leader.getRatingAvg() != null ? leader.getRatingAvg() : BigDecimal.ZERO)
                .reliabilityScore(leader.getReliabilityScore() != null ? leader.getReliabilityScore() : 100)
                .isActive(true)
                .build();

        WorkerGroup savedGroup = workerGroupRepository.save(group);

        WorkerGroupMembership leaderMembership = WorkerGroupMembership.builder()
                .workerGroup(savedGroup)
                .worker(leader)
                .roleInGroup(GroupRole.LEADER)
                .roleTitle("Trưởng nhóm")
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        membershipRepository.save(leaderMembership);
        log.info("Đã tạo nhóm thợ thành công: groupId={}, leaderWorkerId={}", savedGroup.getGroupId(), leader.getWorkerId());

        return mapToGroupDto(savedGroup, leader.getFullName());
    }

    /**
     * Xem chi tiết thông tin một nhóm thợ
     */
    @Transactional(readOnly = true)
    public WorkerGroupResponseDto getGroupDetail(UUID groupId) {
        WorkerGroup group = workerGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerGroup", "id", groupId));

        String leaderName = "N/A";
        if (group.getLeaderWorkerId() != null) {
            leaderName = workerRepository.findById(group.getLeaderWorkerId())
                    .map(w -> w.getUser() != null ? w.getUser().getFullName() : w.getFullName())
                    .orElse("N/A");
        }

        return mapToGroupDto(group, leaderName);
    }

    /**
     * Xem danh sách nhóm thợ mà Worker hiện tại đang tham gia
     */
    @Transactional(readOnly = true)
    public List<WorkerGroupResponseDto> getMyGroups(UUID userId) {
        Worker worker = getWorkerByUserId(userId);
        List<WorkerGroupMembership> memberships = membershipRepository.findByWorker_WorkerIdAndStatus(
                worker.getWorkerId(), MembershipStatus.ACTIVE
        );

        return memberships.stream()
                .map(m -> {
                    WorkerGroup g = m.getWorkerGroup();
                    String leaderName = worker.getFullName();
                    if (!worker.getWorkerId().equals(g.getLeaderWorkerId()) && g.getLeaderWorkerId() != null) {
                        leaderName = workerRepository.findById(g.getLeaderWorkerId())
                                .map(w -> w.getUser() != null ? w.getUser().getFullName() : w.getFullName())
                                .orElse("N/A");
                    }
                    return mapToGroupDto(g, leaderName);
                })
                .toList();
    }

    /**
     * Xem danh sách các lời mời gia nhập nhóm đang chờ xác nhận (Pending Invites)
     */
    @Transactional(readOnly = true)
    public List<WorkerGroupResponseDto> getMyPendingInvites(UUID userId) {
        Worker worker = getWorkerByUserId(userId);
        List<WorkerGroupMembership> memberships = membershipRepository.findByWorker_WorkerIdAndStatus(
                worker.getWorkerId(), MembershipStatus.PENDING
        );

        return memberships.stream()
                .map(m -> {
                    WorkerGroup g = m.getWorkerGroup();
                    String leaderName = "N/A";
                    if (g.getLeaderWorkerId() != null) {
                        leaderName = workerRepository.findById(g.getLeaderWorkerId())
                                .map(w -> w.getUser() != null ? w.getUser().getFullName() : w.getFullName())
                                .orElse("N/A");
                    }
                    return mapToGroupDto(g, leaderName);
                })
                .toList();
    }

    /**
     * Tìm kiếm và duyệt danh sách các nhóm thợ trên nền tảng
     */
    @Transactional(readOnly = true)
    public PageResponse<WorkerGroupResponseDto> searchGroups(String search, Boolean isActive, Pageable pageable) {
        Page<WorkerGroup> page = workerGroupRepository.searchWorkerGroups(search, isActive, pageable);
        return PageResponse.from(page.map(g -> {
            String leaderName = "N/A";
            if (g.getLeaderWorkerId() != null) {
                leaderName = workerRepository.findById(g.getLeaderWorkerId())
                        .map(w -> w.getUser() != null ? w.getUser().getFullName() : w.getFullName())
                        .orElse("N/A");
            }
            return mapToGroupDto(g, leaderName);
        }));
    }

    /**
     * UC-71: Cập nhật thông tin nhóm thợ (Chỉ Leader)
     */
    @Transactional
    public WorkerGroupResponseDto updateGroup(UUID userId, UUID groupId, UpdateWorkerGroupRequestDto request) {
        Worker caller = getWorkerByUserId(userId);
        WorkerGroup group = workerGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerGroup", "id", groupId));

        if (!caller.getWorkerId().equals(group.getLeaderWorkerId())) {
            throw new BadRequestException("Chỉ Trưởng nhóm (Leader) mới có quyền chỉnh sửa thông tin nhóm");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            group.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            group.setDescription(request.getDescription());
        }
        if (request.getSkillFocus() != null) {
            group.setSkillFocus(request.getSkillFocus());
        }
        if (request.getMinSize() != null) {
            group.setMinSize(request.getMinSize());
        }
        if (request.getMaxSize() != null) {
            group.setMaxSize(request.getMaxSize());
        }
        if (request.getIsActive() != null) {
            group.setIsActive(request.getIsActive());
        }

        WorkerGroup saved = workerGroupRepository.save(group);
        log.info("Cập nhật thông tin nhóm thợ thành công: groupId={}", saved.getGroupId());
        return mapToGroupDto(saved, caller.getFullName());
    }

    /**
     * Xem danh sách thành viên trong nhóm thợ (Mask PII nếu không phải thành viên nhóm)
     */
    @Transactional(readOnly = true)
    public List<WorkerGroupMemberResponseDto> getGroupMembers(UUID currentUserId, UUID groupId) {
        WorkerGroup group = workerGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerGroup", "id", groupId));

        boolean canViewFullPii = false;
        if (currentUserId != null) {
            Optional<Worker> callerOpt = workerRepository.findByUser_UserId(currentUserId);
            if (callerOpt.isPresent()) {
                UUID callerWorkerId = callerOpt.get().getWorkerId();
                if (callerWorkerId.equals(group.getLeaderWorkerId())) {
                    canViewFullPii = true;
                } else {
                    canViewFullPii = membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, callerWorkerId)
                            .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                            .isPresent();
                }
            }
        }

        final boolean hasFullAccess = canViewFullPii;
        List<WorkerGroupMembership> list = membershipRepository.findByGroupIdWithWorkerDetails(groupId);
        return list.stream().map(m -> mapToMemberDto(m, hasFullAccess)).toList();
    }

    /**
     * UC-75 (WBS 223.0): Xem danh sách các ca làm việc của Nhóm thợ (View Group Shifts)
     */
    @Transactional(readOnly = true)
    public List<GroupShiftResponseDto> getGroupShifts(UUID userId, UUID groupId) {
        Worker caller = getWorkerByUserId(userId);
        if (!workerGroupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("WorkerGroup", "id", groupId);
        }

        // Kiểm tra quyền: phải là thành viên active hoặc trưởng nhóm
        boolean isMember = membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, caller.getWorkerId())
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
        if (!isMember) {
            throw new BadRequestException("Bạn không phải thành viên chính thức của nhóm này");
        }

        List<com.cani.entity.ShiftAssignment> assignments = shiftAssignmentRepository.findByGroupId(groupId);

        // Nhóm theo Shift
        java.util.Map<com.cani.entity.Shift, List<com.cani.entity.ShiftAssignment>> groupedByShift = assignments.stream()
                .filter(sa -> sa.getShift() != null)
                .collect(Collectors.groupingBy(com.cani.entity.ShiftAssignment::getShift));

        List<GroupShiftResponseDto> results = new ArrayList<>();
        for (java.util.Map.Entry<com.cani.entity.Shift, List<com.cani.entity.ShiftAssignment>> entry : groupedByShift.entrySet()) {
            com.cani.entity.Shift shift = entry.getKey();
            List<com.cani.entity.ShiftAssignment> shiftAssignments = entry.getValue();

            int confirmed = 0;
            int checkedIn = 0;
            int completed = 0;
            int cancelled = 0;

            List<GroupShiftResponseDto.MemberAssignmentDto> memberDtos = new ArrayList<>();
            for (com.cani.entity.ShiftAssignment sa : shiftAssignments) {
                Worker w = sa.getWorker();
                if (sa.getStatus() == com.cani.entity.enums.AssignmentStatus.CONFIRMED) confirmed++;
                else if (sa.getStatus() == com.cani.entity.enums.AssignmentStatus.CHECKED_IN) checkedIn++;
                else if (sa.getStatus() == com.cani.entity.enums.AssignmentStatus.COMPLETED) completed++;
                else if (sa.getStatus() == com.cani.entity.enums.AssignmentStatus.CANCELLED) cancelled++;

                memberDtos.add(GroupShiftResponseDto.MemberAssignmentDto.builder()
                        .workerId(w != null ? w.getWorkerId() : null)
                        .fullName(w != null && w.getUser() != null ? w.getUser().getFullName() : (w != null ? w.getFullName() : "N/A"))
                        .phone(w != null && w.getUser() != null ? w.getUser().getPhone() : null)
                        .isGroupLeader(Boolean.TRUE.equals(sa.getIsGroupLeader()))
                        .assignmentStatus(sa.getStatus())
                        .build());
            }

            String eventName = shift.getEvent() != null ? shift.getEvent().getName() : "Sự kiện";
            results.add(GroupShiftResponseDto.builder()
                    .shiftId(shift.getShiftId())
                    .jobType(shift.getJobType())
                    .eventName(eventName)
                    .venueAddress(shift.getLocationAddress())
                    .startTime(shift.getStartTime())
                    .endTime(shift.getEndTime())
                    .hourlyWage(shift.getHourlyWage())
                    .totalGroupMembers(shiftAssignments.size())
                    .confirmedCount(confirmed)
                    .checkedInCount(checkedIn)
                    .completedCount(completed)
                    .cancelledCount(cancelled)
                    .members(memberDtos)
                    .build());
        }

        return results;
    }

    /**
     * Mời một Worker gia nhập nhóm thợ
     */
    @Transactional
    public WorkerGroupMemberResponseDto inviteMember(UUID userId, UUID groupId, InviteGroupMemberRequestDto request) {
        Worker leader = getWorkerByUserId(userId);
        WorkerGroup group = workerGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerGroup", "id", groupId));

        if (!leader.getWorkerId().equals(group.getLeaderWorkerId())) {
            throw new BadRequestException("Chỉ Trưởng nhóm mới có quyền mời thành viên mới");
        }

        Worker candidate = workerRepository.findById(request.getWorkerId())
                .orElseThrow(() -> new ResourceNotFoundException("Worker", "id", request.getWorkerId()));

        Optional<WorkerGroupMembership> existing = membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(
                groupId, candidate.getWorkerId()
        );
        if (existing.isPresent()) {
            if (existing.get().getStatus() == MembershipStatus.ACTIVE) {
                throw new BadRequestException("Thợ này đã là thành viên chính thức của nhóm");
            }
            if (existing.get().getStatus() == MembershipStatus.PENDING) {
                throw new BadRequestException("Lời mời gia nhập nhóm đã được gửi trước đó và đang chờ phản hồi");
            }
        }

        int activeCount = membershipRepository.countByWorkerGroup_GroupIdAndStatus(groupId, MembershipStatus.ACTIVE);
        if (group.getMaxSize() != null && activeCount >= group.getMaxSize()) {
            throw new BadRequestException("Nhóm đã đạt số lượng thành viên tối đa (" + group.getMaxSize() + " người)");
        }

        WorkerGroupMembership membership = existing.orElse(WorkerGroupMembership.builder()
                .workerGroup(group)
                .worker(candidate)
                .build());

        membership.setRoleInGroup(GroupRole.MEMBER);
        membership.setRoleTitle(request.getRoleTitle() != null ? request.getRoleTitle() : "Thành viên");
        membership.setStatus(MembershipStatus.PENDING);
        membership.setInvitedBy(leader.getWorkerId());

        WorkerGroupMembership saved = membershipRepository.save(membership);
        log.info("Đã gửi lời mời vào nhóm: groupId={}, targetWorkerId={}", groupId, candidate.getWorkerId());
        return mapToMemberDto(saved);
    }

    /**
     * Chấp nhận lời mời gia nhập nhóm thợ
     */
    @Transactional
    public WorkerGroupMemberResponseDto acceptGroupInvite(UUID userId, UUID groupId) {
        Worker worker = getWorkerByUserId(userId);
        WorkerGroup group = workerGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerGroup", "id", groupId));

        WorkerGroupMembership membership = membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(
                groupId, worker.getWorkerId()
        ).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lời mời gia nhập nhóm này"));

        if (membership.getStatus() == MembershipStatus.ACTIVE) {
            throw new BadRequestException("Bạn đã là thành viên chính thức của nhóm");
        }

        if (membership.getStatus() != MembershipStatus.PENDING) {
            throw new BadRequestException("Bạn không có lời mời hợp lệ để gia nhập nhóm này.");
        }

        int activeCount = membershipRepository.countByWorkerGroup_GroupIdAndStatus(groupId, MembershipStatus.ACTIVE);
        if (group.getMaxSize() != null && activeCount >= group.getMaxSize()) {
            throw new BadRequestException("Nhóm đã đạt số lượng thành viên tối đa (" + group.getMaxSize() + " người)");
        }

        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(Instant.now());
        WorkerGroupMembership saved = membershipRepository.save(membership);

        recalculateGroupMetrics(groupId);
        log.info("Worker {} đã gia nhập nhóm {}", worker.getWorkerId(), groupId);
        return mapToMemberDto(saved, true);
    }

    /**
     * Xóa thành viên hoặc rời khỏi nhóm thợ
     */
    @Transactional
    public void removeMember(UUID userId, UUID groupId, UUID targetWorkerId) {
        Worker caller = getWorkerByUserId(userId);
        WorkerGroup group = workerGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerGroup", "id", groupId));

        boolean isLeader = caller.getWorkerId().equals(group.getLeaderWorkerId());
        boolean isSelf = caller.getWorkerId().equals(targetWorkerId);

        if (!isLeader && !isSelf) {
            throw new BadRequestException("Bạn không có quyền thực hiện thao tác này");
        }

        if (isLeader && isSelf) {
            int activeCount = membershipRepository.countByWorkerGroup_GroupIdAndStatus(groupId, MembershipStatus.ACTIVE);
            if (activeCount > 1) {
                throw new BadRequestException("Trưởng nhóm không thể tự rời nhóm khi nhóm vẫn còn thành viên. Vui lòng chuyển giao quyền Trưởng nhóm trước.");
            }
        }

        WorkerGroupMembership membership = membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(
                groupId, targetWorkerId
        ).orElseThrow(() -> new ResourceNotFoundException("Thành viên không tồn tại trong nhóm này"));

        membership.setStatus(MembershipStatus.REMOVED);
        membershipRepository.save(membership);

        recalculateGroupMetrics(groupId);
        log.info("Đã xóa/rời thành viên {} khỏi nhóm {}", targetWorkerId, groupId);
    }

    private void recalculateGroupMetrics(UUID groupId) {
        WorkerGroup group = workerGroupRepository.findById(groupId).orElse(null);
        if (group == null) return;

        List<WorkerGroupMembership> activeMembers = membershipRepository.findByWorkerGroup_GroupIdAndStatus(
                groupId, MembershipStatus.ACTIVE
        );

        int count = activeMembers.size();
        group.setMemberCount(count);

        if (count > 0) {
            List<WorkerGroupMembership> ratedMembers = activeMembers.stream()
                    .filter(m -> m.getWorker() != null && m.getWorker().getRatingAvg() != null && m.getWorker().getRatingAvg().compareTo(BigDecimal.ZERO) > 0)
                    .toList();

            if (!ratedMembers.isEmpty()) {
                double avgRatingSum = ratedMembers.stream()
                        .mapToDouble(m -> m.getWorker().getRatingAvg().doubleValue())
                        .sum();
                group.setAvgRating(BigDecimal.valueOf(avgRatingSum / ratedMembers.size()).setScale(2, RoundingMode.HALF_UP));
            } else {
                group.setAvgRating(BigDecimal.ZERO);
            }

            int rsSum = activeMembers.stream()
                    .map(m -> m.getWorker() != null && m.getWorker().getReliabilityScore() != null ? m.getWorker().getReliabilityScore() : 100)
                    .reduce(0, Integer::sum);

            group.setReliabilityScore(rsSum / count);
        } else {
            group.setAvgRating(BigDecimal.ZERO);
            group.setReliabilityScore(100);
        }

        workerGroupRepository.save(group);
    }

    private Worker getWorkerByUserId(UUID userId) {
        return workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BadRequestException("Tài khoản chưa được kích hoạt hồ sơ Worker"));
    }

    private WorkerGroupResponseDto mapToGroupDto(WorkerGroup g, String leaderName) {
        return WorkerGroupResponseDto.builder()
                .groupId(g.getGroupId())
                .name(g.getName())
                .description(g.getDescription())
                .skillFocus(g.getSkillFocus())
                .leaderWorkerId(g.getLeaderWorkerId())
                .leaderName(leaderName)
                .minSize(g.getMinSize())
                .maxSize(g.getMaxSize())
                .memberCount(g.getMemberCount())
                .avgRating(g.getAvgRating())
                .reliabilityScore(g.getReliabilityScore())
                .isActive(g.getIsActive())
                .createdAt(g.getCreatedAt())
                .build();
    }

    private WorkerGroupMemberResponseDto mapToMemberDto(WorkerGroupMembership m) {
        return mapToMemberDto(m, true);
    }

    private WorkerGroupMemberResponseDto mapToMemberDto(WorkerGroupMembership m, boolean canViewFullPii) {
        Worker w = m.getWorker();
        User u = w != null ? w.getUser() : null;
        String rawPhone = u != null ? u.getPhone() : null;
        String displayPhone = rawPhone;
        if (!canViewFullPii && rawPhone != null && rawPhone.length() >= 7) {
            displayPhone = rawPhone.substring(0, 3) + "****" + rawPhone.substring(rawPhone.length() - 3);
        } else if (!canViewFullPii && rawPhone != null) {
            displayPhone = "***";
        }

        return WorkerGroupMemberResponseDto.builder()
                .membershipId(m.getMembershipId())
                .workerId(w != null ? w.getWorkerId() : null)
                .userId(u != null ? u.getUserId() : null)
                .fullName(u != null ? u.getFullName() : (w != null ? w.getFullName() : "N/A"))
                .phone(displayPhone)
                .avatarUrl(u != null ? u.getAvatarUrl() : null)
                .roleInGroup(m.getRoleInGroup())
                .roleTitle(m.getRoleTitle())
                .status(m.getStatus())
                .reliabilityScore(w != null ? w.getReliabilityScore() : 100)
                .avgRating(w != null ? w.getRatingAvg() : BigDecimal.ZERO)
                .totalShiftsCompleted(w != null ? w.getTotalShiftsCompleted() : 0)
                .joinedAt(m.getJoinedAt())
                .build();
    }
}
