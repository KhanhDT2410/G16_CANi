package com.cani.service;

import com.cani.dto.group.*;
import com.cani.dto.response.PageResponse;
import com.cani.entity.DispatchProposal;
import com.cani.entity.Event;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Shift;
import com.cani.entity.ShiftAssignment;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerGroup;
import com.cani.entity.WorkerGroupMembership;
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
import com.cani.repository.ShiftRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerGroupMembershipRepository;
import com.cani.repository.WorkerGroupRepository;
import com.cani.repository.WorkerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerGroupService {

    private final WorkerGroupRepository workerGroupRepository;
    private final WorkerGroupMembershipRepository membershipRepository;
    private final WorkerRepository workerRepository;
    private final com.cani.repository.ShiftAssignmentRepository shiftAssignmentRepository;
    private final DispatchProposalRepository dispatchProposalRepository;
    private final ShiftRepository shiftRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final DirectPaymentService directPaymentService;
    private final MatchingDispatchService matchingDispatchService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

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
                .inviteCode(generateUniqueInviteCode())
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
        log.info("Đã tạo nhóm thợ thành công: groupId={}, leaderWorkerId={}", savedGroup.getGroupId(),
                leader.getWorkerId());

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
                worker.getWorkerId(), MembershipStatus.ACTIVE);

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
                worker.getWorkerId(), MembershipStatus.PENDING);

        if (memberships.isEmpty()) {
            return List.of();
        }

        // Batch fetch leaders to avoid N+1 queries
        Set<UUID> leaderIds = memberships.stream()
                .map(m -> m.getWorkerGroup().getLeaderWorkerId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> leaderNameMap = workerRepository.findAllById(leaderIds).stream()
                .collect(Collectors.toMap(
                        Worker::getWorkerId,
                        w -> w.getUser() != null ? w.getUser().getFullName() : w.getFullName(),
                        (existing, replacement) -> existing));

        return memberships.stream()
                .map(m -> {
                    WorkerGroup g = m.getWorkerGroup();
                    String leaderName = g.getLeaderWorkerId() != null
                            ? leaderNameMap.getOrDefault(g.getLeaderWorkerId(), "N/A")
                            : "N/A";
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
     * Xem danh sách thành viên trong nhóm thợ (Mask PII nếu không phải thành viên
     * nhóm)
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
                    canViewFullPii = membershipRepository
                            .findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, callerWorkerId)
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
     * UC-75 (WBS 223.0): Xem danh sách các ca làm việc của Nhóm thợ (View Group
     * Shifts)
     */
    @Transactional(readOnly = true)
    public List<GroupShiftResponseDto> getGroupShifts(UUID userId, UUID groupId) {
        Worker caller = getWorkerByUserId(userId);
        if (!workerGroupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("WorkerGroup", "id", groupId);
        }

        // Kiểm tra quyền: phải là thành viên active hoặc trưởng nhóm
        boolean isMember = membershipRepository
                .findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, caller.getWorkerId())
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
        if (!isMember) {
            throw new BadRequestException("Bạn không phải thành viên chính thức của nhóm này");
        }

        List<com.cani.entity.ShiftAssignment> assignments = shiftAssignmentRepository.findByGroupId(groupId);

        // Nhóm theo Shift
        java.util.Map<com.cani.entity.Shift, List<com.cani.entity.ShiftAssignment>> groupedByShift = assignments
                .stream()
                .filter(sa -> sa.getShift() != null)
                .collect(Collectors.groupingBy(com.cani.entity.ShiftAssignment::getShift));

        List<GroupShiftResponseDto> results = new ArrayList<>();
        for (java.util.Map.Entry<com.cani.entity.Shift, List<com.cani.entity.ShiftAssignment>> entry : groupedByShift
                .entrySet()) {
            com.cani.entity.Shift shift = entry.getKey();
            List<com.cani.entity.ShiftAssignment> shiftAssignments = entry.getValue();

            int confirmed = 0;
            int checkedIn = 0;
            int completed = 0;
            int cancelled = 0;

            List<GroupShiftResponseDto.MemberAssignmentDto> memberDtos = new ArrayList<>();
            for (com.cani.entity.ShiftAssignment sa : shiftAssignments) {
                Worker w = sa.getWorker();
                if (sa.getStatus() == com.cani.entity.enums.AssignmentStatus.CONFIRMED)
                    confirmed++;
                else if (sa.getStatus() == com.cani.entity.enums.AssignmentStatus.CHECKED_IN)
                    checkedIn++;
                else if (sa.getStatus() == com.cani.entity.enums.AssignmentStatus.COMPLETED)
                    completed++;
                else if (sa.getStatus() == com.cani.entity.enums.AssignmentStatus.CANCELLED)
                    cancelled++;

                memberDtos.add(GroupShiftResponseDto.MemberAssignmentDto.builder()
                        .workerId(w != null ? w.getWorkerId() : null)
                        .fullName(w != null && w.getUser() != null ? w.getUser().getFullName()
                                : (w != null ? w.getFullName() : "N/A"))
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
                groupId, candidate.getWorkerId());
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
                groupId, worker.getWorkerId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lời mời gia nhập nhóm này"));

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
            int activeCount = membershipRepository.countByWorkerGroup_GroupIdAndStatus(groupId,
                    MembershipStatus.ACTIVE);
            if (activeCount > 1) {
                throw new BadRequestException(
                        "Trưởng nhóm không thể tự rời nhóm khi nhóm vẫn còn thành viên. Vui lòng chuyển giao quyền Trưởng nhóm trước.");
            }
        }

        WorkerGroupMembership membership = membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(
                groupId, targetWorkerId)
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên không tồn tại trong nhóm này"));

        membership.setStatus(MembershipStatus.REMOVED);
        membershipRepository.save(membership);

        recalculateGroupMetrics(groupId);

        // F4: Auto-dissolve khi ACTIVE về 0
        int remainingActive = membershipRepository.countByWorkerGroup_GroupIdAndStatus(groupId,
                MembershipStatus.ACTIVE);
        if (remainingActive == 0 && Boolean.TRUE.equals(group.getIsActive())) {
            group.setIsActive(false);
            workerGroupRepository.save(group);
            log.info("Đội {} đã tự động tan rã vì không còn thành viên ACTIVE", groupId);

            if (group.getLeaderWorkerId() != null) {
                membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, group.getLeaderWorkerId())
                        .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                        .ifPresent(m -> {
                            Worker leader = workerRepository.findById(group.getLeaderWorkerId()).orElse(null);
                            if (leader != null && leader.getUser() != null) {
                                try {
                                    notificationService.sendNotification(
                                            leader.getUser(),
                                            "Đội đã giải tán",
                                            "Đội " + group.getName()
                                                    + " đã tan rã vì không còn thành viên. Tạo đội mới khi cần.",
                                            NotificationType.SYSTEM,
                                            "GROUP_DISSOLVED",
                                            groupId);
                                } catch (Exception ex) {
                                    log.warn("Không thể gửi thông báo FCM giải tán cho leader đội {}: {}", groupId,
                                            ex.getMessage());
                                }
                            }
                        });
            }
        }

        log.info("Đã xóa/rời thành viên {} khỏi nhóm {}", targetWorkerId, groupId);
    }

    /**
     * F4: Nút ý chí giải tán đội (Leader hoặc ADMIN)
     */
    @Transactional
    public void dissolveGroup(UUID callerUserId, UUID groupId) {
        checkGroupLeaderPermission(callerUserId, groupId);
        WorkerGroup group = getGroupOrThrow(groupId);

        if (!Boolean.TRUE.equals(group.getIsActive())) {
            throw new BadRequestException("Nhóm đã ngừng hoạt động");
        }

        // 1. Mọi DispatchProposal PENDING của group -> EXPIRED + saveAll
        List<DispatchProposal> pending = dispatchProposalRepository.findByGroupIdAndStatus(groupId,
                ProposalStatus.PENDING);
        if (!pending.isEmpty()) {
            pending.forEach(p -> p.setStatus(ProposalStatus.EXPIRED));
            dispatchProposalRepository.saveAll(pending);
        }

        // 2. group.setIsActive(false)
        group.setIsActive(false);
        workerGroupRepository.save(group);

        // 3. FCM leader: "Đội [Tên] đã được giải tán."
        if (group.getLeaderWorkerId() != null) {
            Worker leader = workerRepository.findById(group.getLeaderWorkerId()).orElse(null);
            if (leader != null && leader.getUser() != null) {
                try {
                    notificationService.sendNotification(
                            leader.getUser(),
                            "Đội đã giải tán",
                            "Đội " + group.getName() + " đã được giải tán.",
                            NotificationType.SYSTEM,
                            "GROUP_DISSOLVED",
                            groupId);
                } catch (Exception ex) {
                    log.warn("Không thể gửi thông báo FCM giải tán cho leader đội {}: {}", groupId, ex.getMessage());
                }
            }
        }

        log.info("Nhóm {} đã được giải tán bởi user {}", groupId, callerUserId);
    }

    private void recalculateGroupMetrics(UUID groupId) {
        WorkerGroup group = workerGroupRepository.findById(groupId).orElse(null);
        if (group == null)
            return;

        List<WorkerGroupMembership> activeMembers = membershipRepository.findByWorkerGroup_GroupIdAndStatus(
                groupId, MembershipStatus.ACTIVE);

        int count = activeMembers.size();
        group.setMemberCount(count);

        if (count > 0) {
            List<WorkerGroupMembership> ratedMembers = activeMembers.stream()
                    .filter(m -> m.getWorker() != null && m.getWorker().getRatingAvg() != null
                            && m.getWorker().getRatingAvg().compareTo(BigDecimal.ZERO) > 0)
                    .toList();

            if (!ratedMembers.isEmpty()) {
                double avgRatingSum = ratedMembers.stream()
                        .mapToDouble(m -> m.getWorker().getRatingAvg().doubleValue())
                        .sum();
                group.setAvgRating(
                        BigDecimal.valueOf(avgRatingSum / ratedMembers.size()).setScale(2, RoundingMode.HALF_UP));
            } else {
                group.setAvgRating(BigDecimal.valueOf(5.0));
            }

            int rsSum = activeMembers.stream()
                    .map(m -> m.getWorker() != null && m.getWorker().getReliabilityScore() != null
                            ? m.getWorker().getReliabilityScore()
                            : 100)
                    .reduce(0, Integer::sum);

            group.setReliabilityScore(rsSum / count);
        } else {
            group.setAvgRating(BigDecimal.valueOf(5.0));
            group.setReliabilityScore(100);
        }

        workerGroupRepository.save(group);
    }

    // ============================= TASK 3: Group shift proposals (leader)
    // =============================

    /**
     * Xem danh sách đề xuất ca làm việc gửi cho nhóm (chỉ Leader hoặc ADMIN)
     * Kèm: PENDING + EXPIRED trong 24h qua
     */
    @Transactional(readOnly = true)
    public List<GroupProposalResponseDto> listGroupProposals(UUID callerUserId, UUID groupId) {
        checkGroupLeaderPermission(callerUserId, groupId);

        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        List<GroupProposalResponseDto> results = new ArrayList<>();
        results.addAll(dispatchProposalRepository.findByGroupIdAndStatus(groupId, ProposalStatus.PENDING).stream()
                .map(this::toGroupProposalDto).toList());
        dispatchProposalRepository.findByGroupIdAndStatus(groupId, ProposalStatus.EXPIRED).stream()
                .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(cutoff))
                .map(this::toGroupProposalDto)
                .forEach(results::add);
        return results;
    }

    /**
     * Re-check (kết quả mới tính, không tạo data) trạng thái đủ điều kiện của từng
     * thành viên ACTIVE
     * để leader chọn member trước khi accept
     */
    @Transactional(readOnly = true)
    public GroupProposalResponseDto getEligibleMembers(UUID callerUserId, UUID groupId, UUID proposalId) {
        checkGroupLeaderPermission(callerUserId, groupId);
        return toGroupProposalDto(findGroupProposalOrThrow(groupId, proposalId));
    }

    /**
     * Leader chấp nhận đề xuất ca cho nhóm — toàn bộ hoặc không (1 transaction):
     * k × (ShiftAssignment source=GROUP + payment ban đầu + counter event + FCM),
     * giữ slot bằng atomic counter
     */
    @Transactional
    public AcceptGroupProposalResponseDto acceptGroupProposal(UUID callerUserId, UUID groupId,
            UUID proposalId, List<UUID> memberWorkerIds) {
        checkGroupLeaderPermission(callerUserId, groupId);
        Worker leader = getWorkerByUserId(callerUserId);
        WorkerGroup group = getGroupOrThrow(groupId);
        DispatchProposal proposal = findGroupProposalOrThrow(groupId, proposalId);

        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new BadRequestException("Đề xuất ca này đã được xử lý (Trạng thái: " + proposal.getStatus() + ")");
        }
        if (proposal.getExpiresAt() != null && proposal.getExpiresAt().isBefore(Instant.now())) {
            proposal.setStatus(ProposalStatus.EXPIRED);
            dispatchProposalRepository.save(proposal);
            throw new BadRequestException("Đề xuất ca làm việc này đã hết hạn phản hồi");
        }
        Shift shift = proposal.getShift();
        if (shift.getStatus() != ShiftStatus.ACTIVE) {
            throw new BadRequestException("Ca làm việc này không còn ở trạng thái đang mở");
        }

        // Parse breakdown snapshot
        String breakdown = proposal.getScoreBreakdown();
        if (breakdown == null || breakdown.isBlank()) {
            throw new BadRequestException("Đề xuất này không có snapshot đội, vui lòng chờ dispatch lại");
        }
        List<UUID> snapshotWorkerIds = new ArrayList<>();
        int snapshotPackSize;
        try {
            JsonNode node = objectMapper.readTree(breakdown);
            if (!node.hasNonNull("packWorkerIds") || !node.hasNonNull("packSize")) {
                throw new BadRequestException("Đề xuất này không có snapshot đội, vui lòng chờ dispatch lại");
            }
            snapshotPackSize = node.get("packSize").asInt();
            JsonNode idsNode = node.get("packWorkerIds");
            if (!idsNode.isArray()) {
                throw new BadRequestException("Đề xuất này không có snapshot đội, vui lòng chờ dispatch lại");
            }
            for (JsonNode idNode : idsNode) {
                snapshotWorkerIds.add(UUID.fromString(idNode.asText()));
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Đề xuất này không có snapshot đội, vui lòng chờ dispatch lại");
        }

        if (snapshotWorkerIds.size() != snapshotPackSize) {
            throw new BadRequestException("Đề xuất này không có snapshot đội, vui lòng chờ dispatch lại");
        }

        List<WorkerGroupMembership> activeMembers = membershipRepository
                .findByWorkerGroup_GroupIdAndStatus(groupId, MembershipStatus.ACTIVE);
        Map<UUID, WorkerGroupMembership> activeByWorker = activeMembers.stream()
                .filter(m -> m.getWorker() != null)
                .collect(Collectors.toMap(m -> m.getWorker().getWorkerId(), Function.identity(), (a, b) -> a));

        if (activeMembers.isEmpty() || activeByWorker.isEmpty()) {
            throw new BadRequestException("Nhóm không có thành viên nào đang hoạt động");
        }

        Set<UUID> currentActiveIds = activeByWorker.keySet();
        Set<UUID> snapshotIdSet = new HashSet<>(snapshotWorkerIds);
        if (!currentActiveIds.equals(snapshotIdSet)) {
            throw new AppException("Đội đã thay đổi kể từ khi nhận đề xuất, vui lòng chờ hệ thống dispatch lại",
                    HttpStatus.CONFLICT);
        }

        int packSize = snapshotPackSize;

        // CẤM partial:
        // Request null/rỗng memberWorkerIds -> dùng toàn bộ snapshot (k = packSize).
        // Request truyền danh sách KHÔNG khớp đúng tập snapshot -> 400 BadRequest.
        List<UUID> selectedIds = snapshotWorkerIds;
        if (memberWorkerIds != null && !memberWorkerIds.isEmpty()) {
            Set<UUID> requestedSet = new HashSet<>(memberWorkerIds);
            if (!requestedSet.equals(snapshotIdSet)) {
                throw new BadRequestException("Group proposal chỉ hỗ trợ nhận ca trọn đội");
            }
        }

        // Re-check eligibility TOÀN pack theo snapshot trong transaction (1 fail ->
        // toàn bộ rollback, 0 assignment)
        for (UUID workerId : snapshotWorkerIds) {
            WorkerGroupMembership m = activeByWorker.get(workerId);
            if (m == null || m.getWorker() == null) {
                throw new AppException("Đội đã thay đổi kể từ khi nhận đề xuất, vui lòng chờ hệ thống dispatch lại",
                        HttpStatus.CONFLICT);
            }
            String reason = matchingDispatchService.groupMemberEligibilityReason(m.getWorker(), shift);
            if (reason != null) {
                throw new BadRequestException("Member " + workerId + " không đủ điều kiện cho ca này (" + reason + ")");
            }
        }

        int remaining = shift.getRemainingWorkers() != null ? shift.getRemainingWorkers() : 0;
        if (remaining < packSize) {
            throw new AppException("Ca vừa đủ người trước khi đội xác nhận", HttpStatus.CONFLICT);
        }

        // ATOMIC: giữ packSize slot cùng lúc — ca vừa đủ người thì 409 + rollback toàn
        // bộ
        int updatedRows = shiftRepository.incrementConfirmedWorkersAtomicBy(shift.getShiftId(), packSize);
        if (updatedRows == 0) {
            throw new AppException("Ca vừa đủ người trước khi bạn xác nhận", HttpStatus.CONFLICT);
        }

        // Guard chống trùng lặp assignment active trong cùng shift
        // shortcut: H2 không hỗ trợ partial unique index; production PostgreSQL mới có
        // index, nâng cấp test sang embedded PG khi có CI.
        List<AssignmentStatus> activeStatuses = List.of(
                AssignmentStatus.PENDING,
                AssignmentStatus.CONFIRMED,
                AssignmentStatus.CHECKED_IN);
        for (UUID workerId : snapshotWorkerIds) {
            if (shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(shift.getShiftId(),
                    workerId, activeStatuses)) {
                throw new AppException("Thành viên " + workerId + " đã có ca làm việc đang hoạt động trong ca này",
                        HttpStatus.CONFLICT);
            }
        }

        Event event = shift.getEvent();
        String eventName = event != null ? event.getName() : "Sự kiện";
        List<UUID> assignmentIds = new ArrayList<>();

        try {
            // packSize × (ShiftAssignment CONFIRMED source=GROUP + payment ban đầu)
            for (UUID workerId : snapshotWorkerIds) {
                WorkerGroupMembership m = activeByWorker.get(workerId);
                Worker member = m.getWorker();
                ShiftAssignment assignment = ShiftAssignment.builder()
                        .shift(shift)
                        .worker(member)
                        .proposalId(proposal.getProposalId())
                        .source(AssignmentSource.GROUP)
                        .status(AssignmentStatus.CONFIRMED)
                        .groupId(groupId)
                        .isGroupLeader(m.getRoleInGroup() == GroupRole.LEADER)
                        .eoConfirmed(false)
                        .build();
                assignment = shiftAssignmentRepository.save(assignment);
                assignmentIds.add(assignment.getAssignmentId());
                directPaymentService.createInitialPayment(assignment);
            }

            // Counter event += packSize
            if (event != null) {
                event.setTotalConfirmedWorkers(
                        (event.getTotalConfirmedWorkers() != null ? event.getTotalConfirmedWorkers() : 0) + packSize);
                eventRepository.save(event);
            }

            // Sync bộ đếm trong memory (native query đã persist vào DB)
            shift.setConfirmedWorkers(
                    (shift.getConfirmedWorkers() != null ? shift.getConfirmedWorkers() : 0) + packSize);
            int newRemaining = Math.max(0, remaining - packSize);
            shift.setRemainingWorkers(newRemaining);
            if (newRemaining == 0) {
                if (shift.getStatus() == ShiftStatus.ACTIVE) {
                    shift.setStatus(ShiftStatus.FULLY_STAFFED);
                }
                shift.setEmergency(false);
                shift.setUnderstaffedAlertSent(false);
            }

            // Accept proposal: di chuyển xuống SAU KHI các assignment và counter đã persist
            // thành công
            proposal.setStatus(ProposalStatus.ACCEPTED);
            proposal.setRespondedAt(Instant.now());
            dispatchProposalRepository.save(proposal);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Lỗi vi phạm toàn vẹn khi tạo phân công ca cho nhóm {} trong ca {}: {}", groupId,
                    shift.getShiftId(), ex.getMessage());
            throw new AppException("Trùng lịch với ca khác, vui lòng thử lại", HttpStatus.CONFLICT);
        }

        // Notifications: FCM gửi SAU KHI commit DB thành công
        String arriveHint = shift.getArrivalTime() != null
                ? " — có mặt trước " + shift.getArrivalTime()
                        .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                : "";

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeNotify(event, shift, leader, group, selectedIds, eventName, arriveHint);
                }
            });
        } else {
            safeNotify(event, shift, leader, group, selectedIds, eventName, arriveHint);
        }

        log.info("Leader {} đã nhận ca {} cho nhóm {} với {} thành viên",
                leader.getWorkerId(), shift.getShiftId(), groupId, packSize);

        return AcceptGroupProposalResponseDto.builder()
                .assignmentIds(assignmentIds)
                .shiftId(shift.getShiftId())
                .memberCount(packSize)
                .build();
    }

    /**
     * Leader từ chối đề xuất ca cho nhóm → proposal REJECTED + re-dispatch slot còn
     * thiếu
     */
    @Transactional
    public void rejectGroupProposal(UUID callerUserId, UUID groupId, UUID proposalId, String reason) {
        checkGroupLeaderPermission(callerUserId, groupId);
        DispatchProposal proposal = findGroupProposalOrThrow(groupId, proposalId);

        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new BadRequestException("Đề xuất ca này đã được xử lý (Trạng thái: " + proposal.getStatus() + ")");
        }

        proposal.setStatus(ProposalStatus.REJECTED);
        proposal.setRespondedAt(Instant.now());
        dispatchProposalRepository.save(proposal);
        log.info("Leader nhóm {} đã từ chối đề xuất ca {}. Lý do: {}", groupId, proposalId, reason);

        try {
            matchingDispatchService.dispatchForShift(proposal.getShift().getShiftId());
        } catch (Exception e) {
            log.warn("Lỗi khi dispatch lại sau khi từ chối đề xuất nhóm: {}", e.getMessage());
        }
    }

    private void checkGroupLeaderPermission(UUID callerUserId, UUID groupId) {
        WorkerGroup group = getGroupOrThrow(groupId);
        User caller = userRepository.findById(callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", callerUserId));
        if (caller.getRole() == UserRole.ADMIN) {
            return;
        }
        Worker callerWorker = getWorkerByUserId(callerUserId);
        if (!callerWorker.getWorkerId().equals(group.getLeaderWorkerId())) {
            throw new AppException("Chỉ Trưởng nhóm (hoặc Quản trị viên) mới có quyền xử lý đề xuất ca của nhóm",
                    HttpStatus.FORBIDDEN);
        }
    }

    private WorkerGroup getGroupOrThrow(UUID groupId) {
        return workerGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerGroup", "id", groupId));
    }

    private DispatchProposal findGroupProposalOrThrow(UUID groupId, UUID proposalId) {
        return dispatchProposalRepository.findById(proposalId)
                .filter(p -> groupId.equals(p.getGroupId()))
                .orElseThrow(() -> new ResourceNotFoundException("DispatchProposal", "id", proposalId));
    }

    private GroupProposalResponseDto toGroupProposalDto(DispatchProposal proposal) {
        Shift shift = proposal.getShift();
        Event event = shift != null ? shift.getEvent() : null;

        List<WorkerGroupMembership> activeMembers = membershipRepository
                .findByWorkerGroup_GroupIdAndStatus(proposal.getGroupId(), MembershipStatus.ACTIVE);
        List<GroupProposalResponseDto.EligibleMemberDto> memberDtos = new ArrayList<>();
        int eligibleCount = 0;
        for (WorkerGroupMembership m : activeMembers) {
            if (m.getWorker() == null)
                continue;
            Worker w = m.getWorker();
            String reason = matchingDispatchService.groupMemberEligibilityReason(w, shift);
            boolean eligible = reason == null;
            if (eligible)
                eligibleCount++;
            memberDtos.add(GroupProposalResponseDto.EligibleMemberDto.builder()
                    .workerId(w.getWorkerId())
                    .fullName(w.getUser() != null ? w.getUser().getFullName() : w.getFullName())
                    .eligible(eligible)
                    .reasonCode(reason)
                    .build());
        }

        int packSize = activeMembers.size();
        int remaining = shift != null && shift.getRemainingWorkers() != null ? shift.getRemainingWorkers() : 0;
        int claimableSlots = packSize;

        return GroupProposalResponseDto.builder()
                .shiftId(shift != null ? shift.getShiftId() : null)
                .jobType(shift != null ? shift.getJobType() : null)
                .startTime(shift != null ? shift.getStartTime() : null)
                .endTime(shift != null ? shift.getEndTime() : null)
                .hourlyWage(shift != null ? shift.getHourlyWage() : null)
                .eventName(event != null ? event.getName() : null)
                .requiredWorkers(shift != null ? shift.getRequiredWorkers() : null)
                .remainingWorkers(remaining)
                .emergency(shift != null ? shift.getEmergency() : null)
                .proposalId(proposal.getProposalId())
                .status(proposal.getStatus())
                .matchScore(proposal.getMatchScore())
                .expiresAt(proposal.getExpiresAt())
                .claimableSlots(claimableSlots)
                .eligibleCount(eligibleCount)
                .eligibleMembers(memberDtos)
                .source(proposal.getSource())
                .build();
    }

    private void safeNotify(Event event, Shift shift, Worker leader, WorkerGroup group,
            List<UUID> selectedWorkerIds, String eventName, String arriveHint) {
        String groupName = group.getName();
        // 1. EO
        if (event != null && event.getEventOrganizer() != null && event.getEventOrganizer().getUser() != null) {
            sendSafe(event.getEventOrganizer().getUser().getUserId(),
                    "Đội " + groupName + " nhận ca " + shift.getJobType(),
                    "Đội " + groupName + " đã nhận ca \"" + shift.getJobType() + "\" với " + selectedWorkerIds.size()
                            + " thành viên (sự kiện \"" + eventName + "\").");
        }
        // 2. Từng member đã chọn
        Map<UUID, Worker> workersById = workerRepository.findAllById(selectedWorkerIds).stream()
                .collect(Collectors.toMap(Worker::getWorkerId, Function.identity(), (a, b) -> a));
        for (UUID workerId : selectedWorkerIds) {
            Worker member = workersById.get(workerId);
            if (member == null || member.getUser() == null)
                continue;
            sendSafe(member.getUser().getUserId(),
                    "Bạn được phân công ca làm việc [NHÓM]",
                    "Trưởng nhóm " + leader.getFullName() + " đã phân công bạn ca \"" + shift.getJobType()
                            + "\" (" + eventName + ")" + arriveHint + ".");
        }
        // 3. Leader
        if (leader.getUser() != null) {
            sendSafe(leader.getUser().getUserId(),
                    "Đã nhận ca cho đội",
                    "Đội " + groupName + " đã nhận ca \"" + shift.getJobType() + "\" với "
                            + selectedWorkerIds.size() + " thành viên.");
        }
    }

    private void sendSafe(UUID targetUserId, String title, String body) {
        if (targetUserId == null)
            return;
        try {
            notificationService.createAndSend(targetUserId, title, body, NotificationType.GROUP,
                    "SHIFT", null, null);
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo nhóm cho user {}: {}", targetUserId, e.getMessage());
        }
    }

    // ============================= TASK 5: Transfer group leadership
    // =============================

    /**
     * Chuyển giao Trưởng nhóm (1 transaction): đổi atomic cả 3 nguồn sự thật
     * (group.leaderWorkerId, role membership cũ, role membership mới) + hết hạn
     * toàn bộ đề xuất ca PENDING của nhóm + FCM cho leader mới
     */
    @Transactional
    public void transferLeader(UUID callerUserId, UUID groupId, UUID newLeaderWorkerId) {
        if (newLeaderWorkerId == null) {
            throw new BadRequestException("Vui lòng chỉ định người tiếp quản Trưởng nhóm");
        }
        checkGroupLeaderPermission(callerUserId, groupId);
        WorkerGroup group = getGroupOrThrow(groupId);

        if (newLeaderWorkerId.equals(group.getLeaderWorkerId())) {
            throw new BadRequestException("Người này đã là Trưởng nhóm của đội");
        }

        WorkerGroupMembership newLeaderMembership = membershipRepository
                .findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, newLeaderWorkerId)
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên", "workerId", newLeaderWorkerId));
        if (newLeaderMembership.getStatus() != MembershipStatus.ACTIVE) {
            throw new BadRequestException("Người tiếp quản phải là thành viên ACTIVE của nhóm");
        }

        // ATOMIC: 3 nguồn sự thật đổi cùng transaction — nếu một nguồn lệch,
        // kênh dispatch (role membership) và endpoint accept (leaderWorkerId) sẽ mâu thuẫn
        UUID oldLeaderWorkerId = group.getLeaderWorkerId();
        WorkerGroupMembership oldLeaderMembership = oldLeaderWorkerId != null
                ? membershipRepository.findByWorkerGroup_GroupIdAndWorker_WorkerId(groupId, oldLeaderWorkerId).orElse(null)
                : null;
        if (oldLeaderMembership != null && oldLeaderMembership.getRoleInGroup() == GroupRole.LEADER) {
            oldLeaderMembership.setRoleInGroup(GroupRole.MEMBER);
            oldLeaderMembership.setRoleTitle("Thành viên");
            membershipRepository.save(oldLeaderMembership);
        }
        newLeaderMembership.setRoleInGroup(GroupRole.LEADER);
        newLeaderMembership.setRoleTitle("Trưởng nhóm");
        membershipRepository.save(newLeaderMembership);

        group.setLeaderWorkerId(newLeaderWorkerId);
        workerGroupRepository.save(group);

        List<DispatchProposal> pendingProposals = dispatchProposalRepository.findByGroupIdAndStatus(
                groupId, ProposalStatus.PENDING);
        pendingProposals.forEach(p -> {
            p.setStatus(ProposalStatus.EXPIRED);
            p.setRespondedAt(Instant.now());
        });
        dispatchProposalRepository.saveAll(pendingProposals);

        Worker newLeader = newLeaderMembership.getWorker();
        if (newLeader != null && newLeader.getUser() != null) {
            sendSafe(newLeader.getUser().getUserId(),
                    "Chuyển giao đội trưởng",
                    "Bạn đã được chỉ định làm đội trưởng của đội " + group.getName()
                            + ". Các lời mời ca trước đó đã hết hạn.");
        }
        log.info("Chuyển giao Trưởng nhóm {} -> {} cho nhóm {}", oldLeaderWorkerId, newLeaderWorkerId, groupId);
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
                .inviteCode(g.getInviteCode())
                .createdAt(g.getCreatedAt())
                .build();
    }

    public String generateUniqueInviteCode() {
        final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        String code;
        do {
            StringBuilder sb = new StringBuilder("CAN-");
            for (int i = 0; i < 4; i++) {
                sb.append(CHARS.charAt(java.util.concurrent.ThreadLocalRandom.current().nextInt(CHARS.length())));
            }
            code = sb.toString();
        } while (workerGroupRepository.existsByInviteCode(code));
                    
        return code;
    }

    /**
     * Tham gia nhóm bằng Mã mời (Self-service Join via Invite Code)
     */
    @Transactional
    public WorkerGroupResponseDto joinByCode(UUID userId, JoinGroupByCodeRequestDto request) {
        if (request == null || request.getInviteCode() == null || request.getInviteCode().trim().isEmpty()) {
            throw new BadRequestException("Mã mời không được để trống");
        }
        String cleanCode = request.getInviteCode().trim().toUpperCase();
        WorkerGroup group = workerGroupRepository.findByInviteCodeIgnoreCase(cleanCode)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerGroup", "inviteCode", cleanCode));

                
        if (!Boolean.TRUE.equals(group.getIsActive())) {
            throw new BadRequestException("Nhóm này hiện đang tạm ngưng hoạt động");
        }

        Worker worker = getWorkerByUserId(userId);

        Optional<WorkerGroupMembership> existingOpt = membershipRepository
                .findByWorkerGroup_GroupIdAndWorker_WorkerId(group.getGroupId(), worker.getWorkerId());

        if (existingOpt.isPresent()) {
            WorkerGroupMembership existing = existingOpt.get();
            if (existing.getStatus() == MembershipStatus.ACTIVE) {
                                    
                throw new BadRequestException("Bạn đã là thành viên chính thức của nhóm \"" + group.getName() + "\"");
            }
            // Tái kích hoạt thành viên nếu trước đó bị xóa/rời nhóm
            existing.setStatus(MembershipStatus.ACTIVE);
            existing.setJoinedAt(Instant.now());
            membershipRepository.save(existing);
        } else {
            if (group.getMaxSize() != null && group.getMemberCount() != null && group.getMemberCount() >= group.getMaxSize()) {
                throw new BadRequestException("Nhóm đã đạt số lượng thành viên tối đa (" + group.getMaxSize() + ")");
            }

            WorkerGroupMembership membership = WorkerGroupMembership.builder()
                    .workerGroup(group)
                    .worker(worker)
                    .roleInGroup(GroupRole.MEMBER)
                    .roleTitle("Thành viên")
                    .status(MembershipStatus.ACTIVE)
                    .joinedAt(Instant.now())
                    .build();
            membershipRepository.save(membership);
        }

        int activeCount = membershipRepository.countByWorkerGroup_GroupIdAndStatus(group.getGroupId(), MembershipStatus.ACTIVE);
        group.setMemberCount(activeCount);
        workerGroupRepository.save(group);

        log.info("Worker {} đã gia nhập nhóm {} qua mã mời {}", worker.getWorkerId(), group.getGroupId(), cleanCode);

        // Thông báo cho Leader
        if (group.getLeaderWorkerId() != null) {
            workerRepository.findById(group.getLeaderWorkerId()).ifPresent(leader -> {
                if (leader.getUser() != null) {
                    sendSafe(leader.getUser().getUserId(),
                            "Thành viên mới gia nhập đội",
                            worker.getFullName() + " vừa gia nhập đội \"" + group.getName() + "\" bằng mã mời " + cleanCode + ".");
                }
            });
        }

        String leaderName = "N/A";
        if (group.getLeaderWorkerId() != null) {
            leaderName = workerRepository.findById(group.getLeaderWorkerId())
                    .map(w -> w.getUser() != null ? w.getUser().getFullName() : w.getFullName())
                    .orElse("N/A");
        }
        return mapToGroupDto(group, leaderName);
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
