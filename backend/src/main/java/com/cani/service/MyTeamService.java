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
     * Lấy danh sách nhân sự ĐÃ TỪNG CỘNG TÁC trong các sự kiện của Event Organizer
     * Tự động tổng hợp từ lịch sử phân công (ShiftAssignment), kèm trạng thái đã lưu vào danh sách quen thuộc hay chưa
     */
    @Transactional(readOnly = true)
    public List<MyTeamMemberResponseDto> getPastCollaborators(UUID eoUserId, String search) {
        EventOrganizer eo = getEventOrganizerByUserId(eoUserId);

        List<com.cani.entity.enums.AssignmentStatus> statuses = List.of(
                com.cani.entity.enums.AssignmentStatus.CONFIRMED,
                com.cani.entity.enums.AssignmentStatus.CHECKED_IN,
                com.cani.entity.enums.AssignmentStatus.COMPLETED
        );
        List<com.cani.entity.ShiftAssignment> assignments = shiftAssignmentRepository.findCollaboratedWorkersByEoId(eo.getEoId(), statuses);

        // Bản đồ các nhân sự đã lưu vào eo_workers
        java.util.Map<UUID, EoWorker> savedMap = eoWorkerRepository.findMyTeamMembers(eo.getEoId(), null, false)
                .stream()
                .filter(ew -> ew.getWorker() != null)
                .collect(Collectors.toMap(ew -> ew.getWorker().getWorkerId(), ew -> ew, (a, b) -> a));

        // Nhóm các ca theo workerId
        java.util.Map<UUID, List<com.cani.entity.ShiftAssignment>> byWorker = assignments.stream()
                .filter(sa -> sa.getWorker() != null)
                .collect(Collectors.groupingBy(sa -> sa.getWorker().getWorkerId(), java.util.LinkedHashMap::new, Collectors.toList()));

        String q = (search != null && !search.isBlank()) ? search.trim().toLowerCase() : null;
        List<MyTeamMemberResponseDto> result = new java.util.ArrayList<>();

        for (java.util.Map.Entry<UUID, List<com.cani.entity.ShiftAssignment>> entry : byWorker.entrySet()) {
            UUID workerId = entry.getKey();
            List<com.cani.entity.ShiftAssignment> workerAssignments = entry.getValue();
            com.cani.entity.ShiftAssignment latest = workerAssignments.get(0);
            Worker w = latest.getWorker();
            User u = w != null ? w.getUser() : null;

            String fullName = w != null ? w.getFullName() : (u != null ? u.getFullName() : "");
            String phone = u != null ? u.getPhone() : "";

            if (q != null) {
                boolean matchName = fullName != null && fullName.toLowerCase().contains(q);
                boolean matchPhone = phone != null && phone.toLowerCase().contains(q);
                if (!matchName && !matchPhone) continue;
            }

            EoWorker savedEoWorker = savedMap.get(workerId);
            if (savedEoWorker != null && Boolean.TRUE.equals(savedEoWorker.getIsBlacklisted())) {
                continue;
            }

            java.time.Instant lastWorked = latest.getShift() != null ? latest.getShift().getStartTime() : latest.getCreatedAt();
            String lastJob = latest.getShift() != null ? latest.getShift().getJobType() : "";

            result.add(MyTeamMemberResponseDto.builder()
                    .eoWorkerId(savedEoWorker != null ? savedEoWorker.getEoWorkerId() : null)
                    .workerId(workerId)
                    .userId(u != null ? u.getUserId() : null)
                    .fullName(fullName)
                    .phone(phone)
                    .email(u != null ? u.getEmail() : null)
                    .avatarUrl(u != null ? u.getAvatarUrl() : null)
                    .skillTier(w != null ? w.getSkillTier() : null)
                    .reliabilityScore(w != null ? w.getReliabilityScore() : null)
                    .ratingAvg(w != null ? w.getRatingAvg() : null)
                    .totalShiftsTogether(workerAssignments.size())
                    .completionRate(savedEoWorker != null ? savedEoWorker.getCompletionRate() : BigDecimal.valueOf(100))
                    .avgRatingGiven(savedEoWorker != null ? savedEoWorker.getAvgRatingGiven() : BigDecimal.ZERO)
                    .lastWorkedAt(lastWorked)
                    .lastJobType(lastJob)
                    .internalNote(savedEoWorker != null ? savedEoWorker.getInternalNote() : null)
                    .isFavorited(savedEoWorker != null && Boolean.TRUE.equals(savedEoWorker.getIsFavorited()))
                    .isBlacklisted(false)
                    .isSavedInMyTeam(savedEoWorker != null)
                    .isOnline(w != null ? w.getIsOnline() : false)
                    .addedAt(savedEoWorker != null ? savedEoWorker.getCreatedAt() : latest.getCreatedAt())
                    .build());
        }

        return result;
    }

    /**
     * Tìm kiếm nhân sự (Worker) trên toàn hệ thống theo tên, số điện thoại hoặc email
     * để EO có thể xem hồ sơ hoặc lưu vào danh sách Nhân Sự Quen Thuộc.
     */
    @Transactional(readOnly = true)
    public List<MyTeamMemberResponseDto> searchWorkers(UUID eoUserId, String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        EventOrganizer eo = getEventOrganizerByUserId(eoUserId);
        String q = query.trim();
        String digits = q.replaceAll("[^0-9]", "");
        String altPhone = q;
        if (digits.length() >= 8) {
            altPhone = digits.startsWith("84") ? "0" + digits.substring(2)
                    : (digits.startsWith("0") ? "84" + digits.substring(1) : digits);
        }

        java.util.Map<UUID, EoWorker> savedMap = eoWorkerRepository.findMyTeamMembers(eo.getEoId(), null, false)
                .stream()
                .filter(ew -> ew.getWorker() != null)
                .collect(Collectors.toMap(ew -> ew.getWorker().getWorkerId(), ew -> ew, (a, b) -> a));

        return workerRepository.searchWorkersByKeyword(q, altPhone).stream()
                .limit(20)
                .map(w -> {
                    EoWorker saved = savedMap.get(w.getWorkerId());
                    if (saved != null && Boolean.TRUE.equals(saved.getIsBlacklisted())) {
                        return null;
                    }
                    User u = w.getUser();
                    return MyTeamMemberResponseDto.builder()
                            .eoWorkerId(saved != null ? saved.getEoWorkerId() : null)
                            .workerId(w.getWorkerId())
                            .userId(u != null ? u.getUserId() : null)
                            .fullName(w.getFullName() != null ? w.getFullName() : (u != null ? u.getFullName() : "Nhân sự"))
                            .phone(u != null ? u.getPhone() : null)
                            .email(u != null ? u.getEmail() : null)
                            .avatarUrl(u != null ? u.getAvatarUrl() : null)
                            .skillTier(w.getSkillTier())
                            .reliabilityScore(w.getReliabilityScore())
                            .ratingAvg(w.getRatingAvg())
                            .totalShiftsTogether(saved != null ? saved.getTotalShiftsTogether() : 0)
                            .completionRate(saved != null ? saved.getCompletionRate() : BigDecimal.valueOf(100))
                            .avgRatingGiven(saved != null ? saved.getAvgRatingGiven() : BigDecimal.ZERO)
                            .internalNote(saved != null ? saved.getInternalNote() : null)
                            .isFavorited(saved != null && Boolean.TRUE.equals(saved.getIsFavorited()))
                            .isBlacklisted(false)
                            .isSavedInMyTeam(saved != null)
                            .isOnline(Boolean.TRUE.equals(w.getIsOnline()))
                            .addedAt(saved != null ? saved.getCreatedAt() : null)
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Lấy chi tiết thông tin 1 thành viên trong My Team
     */
    @Transactional(readOnly = true)
    public MyTeamMemberResponseDto getMyTeamMemberDetail(UUID eoUserId, UUID workerId) {
        EventOrganizer eo = getEventOrganizerByUserId(eoUserId);
        EoWorker eoWorker = eoWorkerRepository.findByEventOrganizer_EoIdAndWorker_WorkerId(eo.getEoId(), workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Nhân sự này không tồn tại trong danh sách nhân sự quen thuộc của bạn"));
        return mapToResponseDto(eoWorker);
    }

    /**
     * UC-66: Thêm Worker vào danh sách Nhân sự quen thuộc (qua workerId hoặc số điện thoại)
     */
    @Transactional
    public MyTeamMemberResponseDto addWorkerToMyTeam(UUID eoUserId, AddMyTeamMemberRequestDto request) {
        EventOrganizer eo = getEventOrganizerByUserId(eoUserId);
        Worker worker = resolveWorker(request);

        // Kiểm tra xem đã có trong danh sách chưa (hoặc đang bị blacklist -> khôi phục lại)
        java.util.Optional<EoWorker> existingOpt = eoWorkerRepository
                .findByEventOrganizer_EoIdAndWorker_WorkerId(eo.getEoId(), worker.getWorkerId());
        if (existingOpt.isPresent()) {
            EoWorker existing = existingOpt.get();
            if (Boolean.TRUE.equals(existing.getIsBlacklisted())) {
                existing.setIsBlacklisted(false);
                if (request.getInternalNote() != null) {
                    existing.setInternalNote(request.getInternalNote());
                }
                if (request.getIsFavorited() != null) {
                    existing.setIsFavorited(request.getIsFavorited());
                }
                EoWorker restored = eoWorkerRepository.save(existing);
                return mapToResponseDto(restored);
            }
            throw new BadRequestException("Nhân sự này đã có trong danh sách nhân sự quen thuộc của bạn");
        }
        boolean alreadyExists = eoWorkerRepository.existsByEventOrganizer_EoIdAndWorker_WorkerId(eo.getEoId(), worker.getWorkerId());
        if (alreadyExists) {
            throw new BadRequestException("Nhân sự này đã có trong danh sách nhân sự quen thuộc của bạn");
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
                .orElseThrow(() -> new ResourceNotFoundException("Nhân sự này không tồn tại trong danh sách nhân sự quen thuộc của bạn"));

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
                .orElseThrow(() -> new ResourceNotFoundException("Nhân sự này không tồn tại trong danh sách nhân sự quen thuộc của bạn"));

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
            User user = findUserByPhoneFlexible(request.getPhone().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Người dùng với số điện thoại " + request.getPhone() + " không tồn tại"));
            return workerRepository.findByUser_UserId(user.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tài khoản này chưa đăng ký hồ sơ nhân sự (Worker)"));
        }
        throw new BadRequestException("Vui lòng cung cấp workerId hoặc số điện thoại của nhân sự để thêm vào đội");
    }

    private java.util.Optional<User> findUserByPhoneFlexible(String rawPhone) {
        String cleaned = rawPhone.replaceAll("\\s+", "");
        java.util.Set<String> candidates = new java.util.LinkedHashSet<>();
        candidates.add(cleaned);
        if (cleaned.startsWith("+84")) {
            candidates.add("0" + cleaned.substring(3));
            candidates.add(cleaned.substring(1));
        } else if (cleaned.startsWith("84") && cleaned.length() >= 10) {
            candidates.add("0" + cleaned.substring(2));
            candidates.add("+" + cleaned);
        } else if (cleaned.startsWith("0") && cleaned.length() >= 9) {
            candidates.add("+84" + cleaned.substring(1));
            candidates.add("84" + cleaned.substring(1));
        }
        for (String phone : candidates) {
            java.util.Optional<User> found = userRepository.findByPhone(phone);
            if (found.isPresent()) {
                return found;
            }
        }
        return java.util.Optional.empty();
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
                .userId(u != null ? u.getUserId() : null)
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
                .isSavedInMyTeam(true)
                .isOnline(w != null ? w.getIsOnline() : false)
                .addedAt(ew.getCreatedAt())
                .build();
    }
}
