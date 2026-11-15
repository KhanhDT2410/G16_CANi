package com.cani.service;

import com.cani.dto.team.AddMyTeamMemberRequestDto;
import com.cani.dto.team.MyTeamMemberResponseDto;
import com.cani.dto.team.UpdateMyTeamMemberRequestDto;
import com.cani.entity.EoWorker;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EoWorkerRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyTeamService {

    private final EoWorkerRepository eoWorkerRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final com.cani.repository.ShiftRepository shiftRepository;
    private final com.cani.repository.DispatchProposalRepository dispatchProposalRepository;
    private final com.cani.repository.ShiftAssignmentRepository shiftAssignmentRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationService notificationService;

    /**
     * UC-65: Lấy danh sách thợ quen trong My Team của Event Organizer
     */
    @Transactional(readOnly = true)
    public List<MyTeamMemberResponseDto> getMyTeam(UUID eoUserId, String search, Boolean favoritedOnly) {
        EventOrganizer eo = getEventOrganizerByUserId(eoUserId);
        List<EoWorker> members = eoWorkerRepository.findMyTeamMembers(eo.getEoId(), search, favoritedOnly);
        return members.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Lấy chi tiết thông tin 1 thành viên trong My Team
     */
    @Transactional(readOnly = true)
    public MyTeamMemberResponseDto getMyTeamMemberDetail(UUID eoUserId, UUID workerId) {
        EventOrganizer eo = getEventOrganizerByUserId(eoUserId);
        EoWorker eoWorker = eoWorkerRepository.findByEventOrganizer_EoIdAndWorker_WorkerId(eo.getEoId(), workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Nhân sự này không tồn tại trong danh sách My Team của bạn"));
        return mapToResponseDto(eoWorker);
    }

    /**
     * UC-66: Thêm Worker vào My Team (qua workerId hoặc số điện thoại)
     */
    @Transactional
    public MyTeamMemberResponseDto addWorkerToMyTeam(UUID eoUserId, AddMyTeamMemberRequestDto request) {
        EventOrganizer eo = getEventOrganizerByUserId(eoUserId);
        Worker worker = resolveWorker(request);

        // Kiểm tra xem đã có trong My Team chưa
        boolean alreadyExists = eoWorkerRepository.existsByEventOrganizer_EoIdAndWorker_WorkerId(eo.getEoId(), worker.getWorkerId());
        if (alreadyExists) {
            throw new BadRequestException("Nhân sự này đã có trong danh sách My Team của bạn");
        }

        EoWorker eoWorker = EoWorker.builder()
                .eventOrganizer(eo)
                .worker(worker)
                .internalNote(request.getInternalNote())
                .isFavorited(Boolean.TRUE.equals(request.getIsFavorited()))
                .isBlacklisted(false)
                .totalShiftsTogether(0)
                .completionRate(BigDecimal.ZERO)
                .avgRatingGiven(BigDecimal.ZERO)
                .build();

        EoWorker saved = eoWorkerRepository.save(eoWorker);
        log.info("EO [{}] added Worker [{}] to My Team", eo.getEoId(), worker.getWorkerId());
        return mapToResponseDto(saved);
    }

    /**
     * Cập nhật ghi chú nội bộ, gắn sao yêu thích hoặc đánh dấu blacklist cho thành viên My Team
     */
    @Transactional
    public MyTeamMemberResponseDto updateMyTeamMember(UUID eoUserId, UUID workerId, UpdateMyTeamMemberRequestDto request) {
        EventOrganizer eo = getEventOrganizerByUserId(eoUserId);
        EoWorker eoWorker = eoWorkerRepository.findByEventOrganizer_EoIdAndWorker_WorkerId(eo.getEoId(), workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Nhân sự này không tồn tại trong danh sách My Team của bạn"));

        if (request.getInternalNote() != null) {
            eoWorker.setInternalNote(request.getInternalNote());
        }
        if (request.getIsFavorited() != null) {
            eoWorker.setIsFavorited(request.getIsFavorited());
        }
        if (request.getIsBlacklisted() != null) {
            eoWorker.setIsBlacklisted(request.getIsBlacklisted());
        }

        EoWorker updated = eoWorkerRepository.save(eoWorker);
        log.info("EO [{}] updated My Team member [{}]", eo.getEoId(), workerId);
        return mapToResponseDto(updated);
    }

    /**
     * UC-67: Xóa Worker khỏi My Team (Silent removal - không ảnh hưởng tới Worker)
     */
    @Transactional
    public void removeWorkerFromMyTeam(UUID eoUserId, UUID workerId) {
        EventOrganizer eo = getEventOrganizerByUserId(eoUserId);
        EoWorker eoWorker = eoWorkerRepository.findByEventOrganizer_EoIdAndWorker_WorkerId(eo.getEoId(), workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Nhân sự này không tồn tại trong danh sách My Team của bạn"));

        eoWorkerRepository.delete(eoWorker);
        log.info("EO [{}] removed Worker [{}] from My Team (Silent removal)", eo.getEoId(), workerId);
    }

    private EventOrganizer getEventOrganizerByUserId(UUID userId) {
        return eventOrganizerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("EventOrganizer", "userId", userId));
    }

    private Worker resolveWorker(AddMyTeamMemberRequestDto request) {
        if (request.getWorkerId() != null) {
            return workerRepository.findById(request.getWorkerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Worker", "id", request.getWorkerId()));
        }
        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            User user = userRepository.findByPhone(request.getPhone().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Người dùng với số điện thoại " + request.getPhone() + " không tồn tại"));
            return workerRepository.findByUser_UserId(user.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tài khoản này chưa đăng ký hồ sơ nhân sự (Worker)"));
        }
        throw new BadRequestException("Vui lòng cung cấp workerId hoặc số điện thoại của nhân sự để thêm vào đội");
    }

    /**
     * UC-68 (WBS 209.0): EO gửi lời mời hàng loạt tới thành viên trong My Team (Send Bulk Invite)
     */
    @Transactional
    public int sendBulkInvite(UUID eoUserId, com.cani.dto.team.BulkInviteMyTeamRequestDto request) {
        EventOrganizer eo = getEventOrganizerByUserId(eoUserId);
        com.cani.entity.Shift shift = shiftRepository.findById(request.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", request.getShiftId()));

        if (shift.getEvent() == null || !eo.getEoId().equals(shift.getEvent().getEventOrganizer().getEoId())) {
            throw new BadRequestException("Bạn không sở hữu sự kiện của ca làm việc này");
        }

        List<EoWorker> teamMembers = eoWorkerRepository.findMyTeamMembers(eo.getEoId(), null, null);
        List<Worker> targetWorkers;

        if (request.getWorkerIds() != null && !request.getWorkerIds().isEmpty()) {
            java.util.Set<UUID> targetIds = new java.util.HashSet<>(request.getWorkerIds());
            targetWorkers = teamMembers.stream()
                    .filter(ew -> targetIds.contains(ew.getWorker().getWorkerId()) && !Boolean.TRUE.equals(ew.getIsBlacklisted()))
                    .map(EoWorker::getWorker)
                    .toList();
        } else {
            targetWorkers = teamMembers.stream()
                    .filter(ew -> !Boolean.TRUE.equals(ew.getIsBlacklisted()))
                    .map(EoWorker::getWorker)
                    .toList();
        }

        int sentCount = 0;
        java.time.Instant expiresAt = java.time.Instant.now().plus(java.time.Duration.ofHours(24));

        for (Worker worker : targetWorkers) {
            UUID workerId = worker.getWorkerId();
            if (dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerId(shift.getShiftId(), workerId) ||
                shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(shift.getShiftId(), workerId)) {
                continue;
            }

            com.cani.entity.DispatchProposal proposal = com.cani.entity.DispatchProposal.builder()
                    .shift(shift)
                    .worker(worker)
                    .matchScore(java.math.BigDecimal.valueOf(100.00))
                    .scoreBreakdown("{\"myTeamBonus\":100.0,\"reason\":\"Thành viên thân thiết trong My Team của Ban tổ chức\"}")
                    .status(com.cani.entity.enums.ProposalStatus.PENDING)
                    .expiresAt(expiresAt)
                    .build();

            com.cani.entity.DispatchProposal saved = dispatchProposalRepository.save(proposal);
            sentCount++;

            if (notificationService != null && worker.getUser() != null) {
                try {
                    String eventName = shift.getEvent() != null ? shift.getEvent().getName() : "Sự kiện";
                    String msg = request.getMessage() != null && !request.getMessage().isBlank()
                            ? request.getMessage()
                            : "Ban tổ chức quen thuộc vừa gửi lời mời ưu tiên cho bạn tham gia ca làm: " + shift.getJobType();
                    notificationService.createAndSend(
                            worker.getUser().getUserId(),
                            "Lời mời làm việc từ My Team: " + shift.getJobType(),
                            msg,
                            com.cani.entity.enums.NotificationType.PROPOSAL,
                            "PROPOSAL",
                            saved.getProposalId(),
                            java.util.Map.of("shiftId", shift.getShiftId().toString(), "type", "MY_TEAM_INVITE")
                    );
                } catch (Exception ex) {
                    log.warn("Lỗi gửi thông báo FCM mời My Team: {}", ex.getMessage());
                }
            }
        }

        log.info("EO {} đã gửi hàng loạt {} lời mời My Team cho ca {}", eo.getEoId(), sentCount, shift.getShiftId());
        return sentCount;
    }

    private MyTeamMemberResponseDto mapToResponseDto(EoWorker ew) {
        Worker w = ew.getWorker();
        User u = w != null ? w.getUser() : null;

        return MyTeamMemberResponseDto.builder()
                .eoWorkerId(ew.getEoWorkerId())
                .workerId(w != null ? w.getWorkerId() : null)
                .fullName(w != null ? w.getFullName() : (u != null ? u.getFullName() : null))
                .phone(u != null ? u.getPhone() : null)
                .email(u != null ? u.getEmail() : null)
                .avatarUrl(u != null ? u.getAvatarUrl() : null)
                .skillTier(w != null ? w.getSkillTier() : null)
                .reliabilityScore(w != null ? w.getReliabilityScore() : null)
                .ratingAvg(w != null ? w.getRatingAvg() : null)
                .totalShiftsTogether(ew.getTotalShiftsTogether())
                .completionRate(ew.getCompletionRate())
                .avgRatingGiven(ew.getAvgRatingGiven())
                .lastWorkedAt(ew.getLastWorkedAt())
                .internalNote(ew.getInternalNote())
                .isFavorited(ew.getIsFavorited())
                .isBlacklisted(ew.getIsBlacklisted())
                .isOnline(w != null ? w.getIsOnline() : false)
                .addedAt(ew.getCreatedAt())
                .build();
    }
}
