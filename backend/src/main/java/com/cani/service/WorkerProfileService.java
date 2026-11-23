package com.cani.service;

import com.cani.dto.event.EoReviewResponseDto;
import com.cani.dto.profile.EoPublicProfileResponseDto;
import com.cani.dto.profile.UpdateWorkerProfileRequestDto;
import com.cani.dto.profile.WorkerPublicProfileResponseDto;
import com.cani.dto.profile.WorkerReviewSummaryDto;
import com.cani.dto.profile.WorkerSkillProfileDto;
import com.cani.entity.*;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.VerificationStatus;
import com.cani.entity.enums.VerificationTier;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerProfileService {

    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final WorkerSkillRepository workerSkillRepository;
    private final WorkerSkillPointRepository workerSkillPointRepository;
    private final WorkerEkycVerificationRepository workerEkycVerificationRepository;
    private final ReviewRepository reviewRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final EoWorkerRepository eoWorkerRepository;

    @Autowired(required = false)
    private EventRepository eventRepository;

    @Autowired(required = false)
    private ShiftAssignmentRepository shiftAssignmentRepository;

    /**
     * Lấy hồ sơ đầy đủ của chính Worker đang đăng nhập.
     */
    @Transactional(readOnly = true)
    public WorkerPublicProfileResponseDto getMyProfile(UUID userId) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ nhân sự (Worker)", "userId", userId));
        return buildWorkerProfile(worker, null, true);
    }

    /**
     * Cập nhật hồ sơ cá nhân của chính Worker đang đăng nhập.
     */
    @Transactional
    public WorkerPublicProfileResponseDto updateMyProfile(UUID userId, UpdateWorkerProfileRequestDto request) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ nhân sự (Worker)", "userId", userId));

        User user = worker.getUser();
        if (request != null) {
            if (request.getFullName() != null && !request.getFullName().isBlank()) {
                String trimmedName = request.getFullName().trim();
                worker.setFullName(trimmedName);
                if (user != null) {
                    user.setFullName(trimmedName);
                }
            }
            if (request.getBio() != null) {
                worker.setBio(request.getBio().trim());
            }
            if (request.getYearsExperience() != null) {
                worker.setYearsExperience(request.getYearsExperience());
            }
            if (request.getBaseAddress() != null) {
                worker.setBaseAddress(request.getBaseAddress().trim());
            }
            if (request.getAvatarUrl() != null && user != null) {
                user.setAvatarUrl(request.getAvatarUrl().trim());
            }
        }

        if (user != null) {
            userRepository.save(user);
        }
        workerRepository.save(worker);

        return buildWorkerProfile(worker, null, true);
    }

    /**
     * Lấy hồ sơ nghề nghiệp công khai của Worker cho Event Organizer hoặc Admin xem.
     * Hỗ trợ tìm kiếm theo workerId hoặc userId của Worker.
     */
    @Transactional(readOnly = true)
    public WorkerPublicProfileResponseDto getWorkerProfileForEo(UUID eoUserId, UUID workerId) {
        Worker worker = workerRepository.findById(workerId)
                .or(() -> workerRepository.findByUser_UserId(workerId))
                .orElseThrow(() -> new ResourceNotFoundException("Nhân sự (Worker)", "id", workerId));

        return buildWorkerProfile(worker, eoUserId, false);
    }

    private WorkerPublicProfileResponseDto buildWorkerProfile(Worker worker, UUID eoUserId, boolean owner) {
        User user = worker.getUser();
        UUID actualWorkerId = worker.getWorkerId();
        UUID workerUserId = user != null ? user.getUserId() : null;

        Integer workerAge = owner ? calculateWorkerAge(actualWorkerId) : null;

        // 2. Nạp tổng điểm kỹ năng (SP) và index theo skill_id / tên kỹ năng
        List<WorkerSkillPoint> skillPoints = workerSkillPointRepository.findByWorker_WorkerId(actualWorkerId);
        int totalSp = skillPoints.stream()
                .mapToInt(sp -> sp.getTotalSp() != null ? sp.getTotalSp() : 0)
                .sum();
        Map<UUID, WorkerSkillPoint> spBySkillId = new HashMap<>();
        Map<String, WorkerSkillPoint> spByJobTypeLower = new HashMap<>();
        for (WorkerSkillPoint sp : skillPoints) {
            if (sp.getSkill() != null && sp.getSkill().getSkillId() != null) {
                spBySkillId.putIfAbsent(sp.getSkill().getSkillId(), sp);
            }
            if (sp.getJobType() != null && !sp.getJobType().isBlank()) {
                spByJobTypeLower.putIfAbsent(sp.getJobType().trim().toLowerCase(Locale.ROOT), sp);
            }
        }

        // 3. Nạp danh sách kỹ năng (giữ độc lập VerificationTier và SkillRank, không lộ file chứng chỉ riêng tư)
        Instant now = Instant.now();
        List<WorkerSkill> workerSkills = workerSkillRepository.findByWorker_WorkerId(actualWorkerId);
        List<WorkerSkillProfileDto> skillDtos = workerSkills.stream()
                .map(ws -> {
                    UUID skId = ws.getSkill() != null ? ws.getSkill().getSkillId() : null;
                    String skName = ws.getSkill() != null ? ws.getSkill().getName() : "Kỹ năng";
                    WorkerSkillPoint matchedSp = skId != null ? spBySkillId.get(skId) : null;
                    if (matchedSp == null && skName != null) {
                        matchedSp = spByJobTypeLower.get(skName.trim().toLowerCase(Locale.ROOT));
                    }

                    int spRatingCount = matchedSp != null && matchedSp.getRatingCount() != null
                            ? matchedSp.getRatingCount()
                            : 0;
                    int spRatingPoints = matchedSp != null && matchedSp.getSpRating() != null
                            ? matchedSp.getSpRating()
                            : 0;
                    BigDecimal rawSkillAvg = matchedSp != null && matchedSp.getAvgRating() != null
                            ? matchedSp.getAvgRating()
                            : BigDecimal.ZERO;
                    BigDecimal safeSkillAvg = (spRatingCount == 0 && spRatingPoints == 0
                            && rawSkillAvg.compareTo(new BigDecimal("5.00")) == 0)
                            ? BigDecimal.ZERO
                            : rawSkillAvg;

                    boolean hasCertEvidence = ws.getEvidenceRequestId() != null
                            || ws.getVerificationTier() == VerificationTier.AI_DOCUMENT;
                    boolean certNotExpired = ws.getExpiresAt() == null || ws.getExpiresAt().isAfter(now);
                    boolean hasValidCert = hasCertEvidence && certNotExpired;

                    return WorkerSkillProfileDto.builder()
                            .skillId(skId)
                            .skillName(skName)
                            .category(ws.getSkill() != null ? ws.getSkill().getCategory() : "Khác")
                            .proficiency(ws.getProficiency())
                            .verificationTier(ws.getVerificationTier())
                            .skillTier(matchedSp != null && matchedSp.getTier() != null ? matchedSp.getTier() : SkillTier.STARTER)
                            .totalSp(matchedSp != null && matchedSp.getTotalSp() != null ? matchedSp.getTotalSp() : 0)
                            .totalShiftsCompleted(matchedSp != null && matchedSp.getTotalShiftsCompleted() != null ? matchedSp.getTotalShiftsCompleted() : 0)
                            .avgRating(safeSkillAvg)
                            .ratingCount(spRatingCount)
                            .hasValidCertificate(hasValidCert)
                            .certificateExpiresAt(ws.getExpiresAt())
                            .build();
                })
                .collect(Collectors.toList());

        // 4. Nạp lịch sử đánh giá (Reviews) từ các EO khác
        List<WorkerReviewSummaryDto> reviewDtos = loadWorkerReviews(workerUserId);

        // 5. Kiểm tra quan hệ với EO đang tra cứu (My Team, Ghi chú nội bộ, Yêu thích)
        Boolean isMyTeamMember = false;
        Boolean isFavorited = false;
        String internalNote = null;

        if (eoUserId != null) {
            Optional<EventOrganizer> eoOpt = eventOrganizerRepository.findByUser_UserId(eoUserId);
            if (eoOpt.isPresent()) {
                Optional<EoWorker> eoWorkerOpt = eoWorkerRepository
                        .findByEventOrganizer_EoIdAndWorker_WorkerId(eoOpt.get().getEoId(), actualWorkerId);
                if (eoWorkerOpt.isPresent()) {
                    isMyTeamMember = true;
                    isFavorited = Boolean.TRUE.equals(eoWorkerOpt.get().getIsFavorited());
                    internalNote = eoWorkerOpt.get().getInternalNote();
                }
            }
        }

        return WorkerPublicProfileResponseDto.builder()
                .workerId(actualWorkerId)
                .userId(workerUserId)
                .fullName(worker.getFullName() != null ? worker.getFullName() : (user != null ? user.getFullName() : "Nhân sự CANi"))
                .avatarUrl(user != null ? user.getAvatarUrl() : null)
                .phone(user != null && (owner || isMyTeamMember) ? user.getPhone() : null)
                .email(user != null && owner ? user.getEmail() : null)
                .bio(worker.getBio())
                .yearsExperience(worker.getYearsExperience() != null ? worker.getYearsExperience() : 0)
                .isOnline(Boolean.TRUE.equals(worker.getIsOnline()))
                .age(workerAge)
                .baseAddress(owner ? worker.getBaseAddress() : null)
                .skillTier(worker.getSkillTier() != null ? worker.getSkillTier() : SkillTier.STARTER)
                .totalSp(totalSp)
                .reliabilityScore(worker.getReliabilityScore() != null ? worker.getReliabilityScore() : 100)
                .ratingAvg(worker.getRatingAvg() != null ? worker.getRatingAvg() : BigDecimal.valueOf(5.0))
                .ratingCount(worker.getRatingCount() != null ? worker.getRatingCount() : 0)
                .totalShiftsCompleted(worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0)
                .totalCancellations(worker.getTotalCancellations() != null ? worker.getTotalCancellations() : 0)
                .totalNoshows(worker.getTotalNoshows() != null ? worker.getTotalNoshows() : 0)
                .skills(skillDtos)
                .reviews(reviewDtos)
                .isMyTeamMember(isMyTeamMember)
                .isFavorited(isFavorited)
                .internalNote(internalNote)
                .build();
    }

    /**
     * Lấy hồ sơ công khai của Nhà tổ chức sự kiện (EO) cho Worker hoặc bất kỳ người dùng nào xem.
     * Tính toán số liệu thực tế từ bảng events & reviews, tuyệt đối không hardcode mô tả giả hay để lộ taxId.
     */
    @Transactional(readOnly = true)
    public EoPublicProfileResponseDto getEoPublicProfile(UUID eoId) {
        EventOrganizer eo = eventOrganizerRepository.findById(eoId)
                .or(() -> eventOrganizerRepository.findByUser_UserId(eoId))
                .orElseThrow(() -> new ResourceNotFoundException("Nhà tổ chức sự kiện (EO)", "id", eoId));

        User user = eo.getUser();
        boolean isVerified = eo.getVerificationStatus() == VerificationStatus.APPROVED;

        int totalEventsCreated = 0;
        int totalEventsPublished = 0;
        int totalEventsCompleted = 0;
        int totalConfirmedWorkers = 0;

        if (eventRepository != null) {
            List<Event> eoEvents = eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eo.getEoId());
            if (eoEvents != null) {
                // totalEventsCreated: Tổng số sự kiện hợp lệ đã tạo (không tính sự kiện đã bị hủy CANCELLED)
                totalEventsCreated = (int) eoEvents.stream()
                        .filter(ev -> ev.getStatus() != null && ev.getStatus() != EventStatus.CANCELLED)
                        .count();
                // totalEventsPublished: Số sự kiện đã xuất bản (ACTIVE, ONGOING, COMPLETED; không tính DRAFT, CANCELLED)
                totalEventsPublished = (int) eoEvents.stream()
                        .filter(ev -> ev.getStatus() == EventStatus.ACTIVE
                                || ev.getStatus() == EventStatus.ONGOING
                                || ev.getStatus() == EventStatus.COMPLETED)
                        .count();
                // totalEventsCompleted: Chỉ đếm sự kiện đã hoàn thành (COMPLETED)
                totalEventsCompleted = (int) eoEvents.stream()
                        .filter(ev -> ev.getStatus() == EventStatus.COMPLETED)
                        .count();
                totalConfirmedWorkers = eoEvents.stream()
                        .filter(ev -> ev.getStatus() != null && ev.getStatus() != EventStatus.CANCELLED)
                        .mapToInt(ev -> ev.getTotalConfirmedWorkers() != null ? ev.getTotalConfirmedWorkers() : 0)
                        .sum();
            }
        } else {
            totalEventsCompleted = eo.getTotalEventsCompleted() != null ? eo.getTotalEventsCompleted() : 0;
        }

        UUID eoUserId = user != null ? user.getUserId() : null;
        Set<UUID> revieweeIds = collectEoRevieweeIds(eo, eoUserId);
        List<EoReviewResponseDto> reviewDtos = loadEoReviews(eo, eoUserId);

        List<Integer> validDbRatings = (reviewRepository != null && !revieweeIds.isEmpty())
                ? Optional.ofNullable(reviewRepository.findValidRatingsByRevieweeIds(revieweeIds))
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(r -> r != null && r >= 1 && r <= 5)
                        .toList()
                : Collections.emptyList();

        List<Integer> validLoadedRatings = reviewDtos.stream()
                .map(EoReviewResponseDto::getRating)
                .filter(r -> r != null && r >= 1 && r <= 5)
                .toList();

        int actualRatingCount = 0;
        BigDecimal actualRatingAvg = BigDecimal.ZERO;

        List<Integer> ratingsToAggregate = !validDbRatings.isEmpty() ? validDbRatings : validLoadedRatings;
        if (!ratingsToAggregate.isEmpty()) {
            actualRatingCount = ratingsToAggregate.size();
            double avg = ratingsToAggregate.stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0);
            actualRatingAvg = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);
        } else if (reviewDtos.isEmpty()
                && eo.getRatingCount() != null && eo.getRatingCount() > 0
                && eo.getRatingAvg() != null
                && eo.getRatingAvg().compareTo(BigDecimal.ONE) >= 0
                && eo.getRatingAvg().compareTo(BigDecimal.valueOf(5)) <= 0) {
            actualRatingCount = eo.getRatingCount();
            actualRatingAvg = eo.getRatingAvg();
        }

        boolean showFullContact = canViewFullEoContact(eo, user);
        String publicPhone = user != null
                ? (showFullContact ? user.getPhone() : maskPhone(user.getPhone()))
                : null;
        String publicEmail = user != null
                ? (showFullContact ? user.getEmail() : maskEmail(user.getEmail()))
                : null;

        return EoPublicProfileResponseDto.builder()
                .eoId(eo.getEoId())
                .userId(eoUserId)
                .companyName(eo.getCompanyName() != null && !eo.getCompanyName().isBlank()
                        ? eo.getCompanyName() : "Ban Tổ Chức Sự Kiện")
                .representativeName(user != null ? user.getFullName() : null)
                .avatarUrl(user != null ? user.getAvatarUrl() : null)
                .address(eo.getAddress())
                .phone(publicPhone)
                .email(publicEmail)
                .description(null)
                .isVerified(isVerified)
                .verifiedAt(eo.getVerifiedAt())
                .ratingAvg(actualRatingAvg)
                .ratingCount(actualRatingCount)
                .totalEventsCompleted(totalEventsCompleted)
                .totalEventsPublished(totalEventsPublished)
                .totalEventsCreated(totalEventsCreated)
                .totalConfirmedWorkers(totalConfirmedWorkers)
                .reviews(reviewDtos)
                .build();
    }

    private Set<UUID> collectEoRevieweeIds(EventOrganizer eo, UUID eoUserId) {
        Set<UUID> revieweeIds = new LinkedHashSet<>();
        if (eoUserId != null) {
            revieweeIds.add(eoUserId);
        }
        if (eo != null && eo.getEoId() != null) {
            revieweeIds.add(eo.getEoId());
        }
        return revieweeIds;
    }

    private boolean canViewFullEoContact(EventOrganizer eo, User eoUser) {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return false;
            }
            boolean isAdmin = auth.getAuthorities() != null && auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
            if (isAdmin) {
                return true;
            }
            String principalName = auth.getName();
            if (principalName == null || principalName.isBlank()) {
                return false;
            }
            if (eoUser != null) {
                if (principalName.equalsIgnoreCase(eoUser.getEmail())
                        || (eoUser.getUserId() != null && principalName.equals(eoUser.getUserId().toString()))) {
                    return true;
                }
            }
            if (workerRepository != null && shiftAssignmentRepository != null && eo != null && eo.getEoId() != null) {
                Optional<Worker> callerWorker = Optional.empty();
                try {
                    UUID callerUserId = UUID.fromString(principalName);
                    callerWorker = workerRepository.findByUser_UserId(callerUserId);
                } catch (IllegalArgumentException ignored) {
                    if (userRepository != null) {
                        callerWorker = userRepository.findByEmail(principalName)
                                .flatMap(u -> workerRepository.findByUser_UserId(u.getUserId()));
                    }
                }
                if (callerWorker.isPresent() && callerWorker.get().getWorkerId() != null) {
                    List<ShiftAssignment> activeOrPast = shiftAssignmentRepository.findByWorker_WorkerIdAndStatusIn(
                            callerWorker.get().getWorkerId(),
                            List.of(
                                    com.cani.entity.enums.AssignmentStatus.CONFIRMED,
                                    com.cani.entity.enums.AssignmentStatus.CHECKED_IN,
                                    com.cani.entity.enums.AssignmentStatus.COMPLETED
                            )
                    );
                    if (activeOrPast != null) {
                        return activeOrPast.stream().anyMatch(sa ->
                                sa.getShift() != null
                                        && sa.getShift().getEvent() != null
                                        && sa.getShift().getEvent().getEventOrganizer() != null
                                        && eo.getEoId().equals(sa.getShift().getEvent().getEventOrganizer().getEoId()));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String trimmed = phone.trim();
        if (trimmed.length() <= 5) return "***";
        return trimmed.substring(0, 3) + "***" + trimmed.substring(trimmed.length() - 3);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String trimmed = email.trim();
        int atIndex = trimmed.indexOf('@');
        if (atIndex <= 0) return "***";
        String local = trimmed.substring(0, atIndex);
        String domain = trimmed.substring(atIndex);
        String prefix = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return prefix + "***" + domain;
    }

    private List<EoReviewResponseDto> loadEoReviews(EventOrganizer eo, UUID eoUserId) {
        if (reviewRepository == null) return List.of();
        Set<UUID> revieweeIds = collectEoRevieweeIds(eo, eoUserId);
        if (revieweeIds.isEmpty()) return List.of();

        Comparator<Review> latestFirstNullsLast = Comparator.comparing(
                Review::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        );

        List<Review> rawCandidates = new ArrayList<>();
        Set<UUID> seenReviewIds = new HashSet<>();
        int page = 0;
        final int pageSize = 10;
        final int maxPages = 5;

        while (page < maxPages) {
            List<Review> pageBatch = reviewRepository.findLatestByRevieweeIds(
                    revieweeIds,
                    org.springframework.data.domain.PageRequest.of(page, pageSize)
            );
            if (pageBatch == null || pageBatch.isEmpty()) {
                break;
            }
            int addedInPage = 0;
            for (Review r : pageBatch) {
                if (r == null) continue;
                UUID rid = r.getReviewId();
                if (rid == null || seenReviewIds.add(rid)) {
                    rawCandidates.add(r);
                    addedInPage++;
                }
            }
            if (rawCandidates.size() >= pageSize || pageBatch.size() < pageSize || addedInPage == 0) {
                break;
            }
            page++;
        }

        // Fallback tương thích nếu repository chỉ có dữ liệu qua phương thức cũ theo từng revieweeId
        if (rawCandidates.isEmpty()) {
            for (UUID targetId : revieweeIds) {
                List<Review> byTarget = reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(targetId);
                if (byTarget != null) {
                    for (Review r : byTarget) {
                        if (r == null) continue;
                        UUID rid = r.getReviewId();
                        if (rid == null || seenReviewIds.add(rid)) {
                            rawCandidates.add(r);
                        }
                    }
                }
            }
        }

        if (rawCandidates.isEmpty()) return List.of();

        List<Review> topReviews = rawCandidates.stream()
                .sorted(latestFirstNullsLast)
                .limit(pageSize)
                .toList();

        Set<UUID> reviewerIds = topReviews.stream()
                .map(Review::getReviewerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, User> userMap = (userRepository == null || reviewerIds.isEmpty())
                ? Collections.emptyMap()
                : Optional.ofNullable(userRepository.findAllById(reviewerIds))
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(u -> u != null && u.getUserId() != null)
                        .collect(Collectors.toMap(User::getUserId, u -> u, (u1, u2) -> u1));

        Map<UUID, Worker> workerByUserId = (workerRepository == null || reviewerIds.isEmpty())
                ? Collections.emptyMap()
                : Optional.ofNullable(workerRepository.findByUser_UserIdIn(reviewerIds))
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(w -> w != null && w.getUser() != null && w.getUser().getUserId() != null)
                        .collect(Collectors.toMap(w -> w.getUser().getUserId(), w -> w, (w1, w2) -> w1));

        Set<UUID> assignmentIds = topReviews.stream()
                .map(Review::getAssignmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, ShiftAssignment> assignmentMap = (shiftAssignmentRepository == null || assignmentIds.isEmpty())
                ? Collections.emptyMap()
                : Optional.ofNullable(shiftAssignmentRepository.findAllById(assignmentIds))
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(sa -> sa != null && sa.getAssignmentId() != null)
                        .collect(Collectors.toMap(ShiftAssignment::getAssignmentId, sa -> sa, (s1, s2) -> s1));

        return topReviews.stream()
                .map(r -> {
                    User reviewer = r.getReviewerId() != null ? userMap.get(r.getReviewerId()) : null;
                    Worker reviewerWorker = r.getReviewerId() != null ? workerByUserId.get(r.getReviewerId()) : null;
                    UUID actualWorkerId = reviewerWorker != null ? reviewerWorker.getWorkerId() : null;

                    String workerName = (reviewer != null && reviewer.getFullName() != null && !reviewer.getFullName().isBlank())
                            ? reviewer.getFullName()
                            : "Nhân sự CANi";
                    String workerAvatar = reviewer != null ? reviewer.getAvatarUrl() : null;

                    ShiftAssignment sa = r.getAssignmentId() != null ? assignmentMap.get(r.getAssignmentId()) : null;
                    Shift s = sa != null ? sa.getShift() : null;
                    Event ev = s != null ? s.getEvent() : null;

                    Integer validRating = (r.getRating() != null && r.getRating() >= 1 && r.getRating() <= 5)
                            ? r.getRating()
                            : null;

                    return EoReviewResponseDto.builder()
                            .reviewId(r.getReviewId())
                            .assignmentId(r.getAssignmentId())
                            .eventName(ev != null ? ev.getName() : null)
                            .shiftName(s != null ? s.getJobType() : null)
                            .reviewerUserId(r.getReviewerId())
                            .reviewerWorkerId(actualWorkerId)
                            .reviewerWorkerName(workerName)
                            .workerAvatarUrl(workerAvatar)
                            .rating(validRating)
                            .criteriaShiftClarity(r.getCriteriaShiftClarity())
                            .criteriaTiming(r.getCriteriaTiming())
                            .criteriaWorkEnvironment(r.getCriteriaWorkEnvironment())
                            .criteriaSupport(r.getCriteriaSupport())
                            .criteriaPaymentTiming(r.getCriteriaPaymentTiming())
                            .comment(r.getComment())
                            .tags(r.getTags())
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<WorkerReviewSummaryDto> loadWorkerReviews(UUID workerUserId) {
        if (workerUserId == null) return List.of();

        List<Review> reviews = reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(workerUserId);
        if (reviews.isEmpty()) return List.of();

        Set<UUID> reviewerUserIds = reviews.stream()
                .map(Review::getReviewerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, User> userMap = userRepository.findAllById(reviewerUserIds).stream()
                .collect(Collectors.toMap(User::getUserId, u -> u));

        Map<UUID, EventOrganizer> eoMap = eventOrganizerRepository.findByUser_UserIdIn(reviewerUserIds).stream()
                .filter(eo -> eo.getUser() != null)
                .collect(Collectors.toMap(eo -> eo.getUser().getUserId(), eo -> eo, (e1, e2) -> e1));

        return reviews.stream()
                .limit(10)
                .map(r -> {
                    User reviewer = userMap.get(r.getReviewerId());
                    EventOrganizer eo = eoMap.get(r.getReviewerId());
                    String reviewerName = reviewer != null ? reviewer.getFullName() : "Ban Tổ Chức";
                    String companyName = eo != null ? eo.getCompanyName() : null;

                    return WorkerReviewSummaryDto.builder()
                            .reviewId(r.getReviewId())
                            .rating(r.getRating())
                            .criteriaWorkCompletion(r.getCriteriaWorkCompletion())
                            .criteriaPunctuality(r.getCriteriaPunctuality())
                            .criteriaProfessionalism(r.getCriteriaProfessionalism())
                            .criteriaVenueRules(r.getCriteriaVenueRules())
                            .criteriaTeamwork(r.getCriteriaTeamwork())
                            .comment(r.getComment())
                            .reviewerName(reviewerName)
                            .companyName(companyName)
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Integer calculateWorkerAge(UUID workerId) {
        if (workerId == null) return null;

        Optional<WorkerEkycVerification> ekycOpt = workerEkycVerificationRepository
                .findTopByWorker_WorkerIdAndStatusInOrderByCreatedAtDesc(workerId, List.of("APPROVED"));

        if (ekycOpt.isPresent() && ekycOpt.get().getDob() != null && !ekycOpt.get().getDob().isBlank()) {
            String workerDob = ekycOpt.get().getDob().trim();
            try {
                LocalDate birthDate = null;
                if (workerDob.contains("/")) {
                    String[] parts = workerDob.split("/");
                    if (parts.length == 3) {
                        birthDate = LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                    }
                } else if (workerDob.contains("-")) {
                    birthDate = LocalDate.parse(workerDob);
                }
                if (birthDate != null) {
                    int age = Period.between(birthDate, LocalDate.now()).getYears();
                    return age >= 0 ? age : null;
                }
            } catch (Exception ignored) {
                // Parse fallback
            }
        }

        return null;
    }
}
