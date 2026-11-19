package com.cani.service;

import com.cani.dto.profile.EoPublicProfileResponseDto;
import com.cani.dto.profile.WorkerPublicProfileResponseDto;
import com.cani.dto.profile.WorkerReviewSummaryDto;
import com.cani.dto.profile.WorkerSkillProfileDto;
import com.cani.entity.*;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.VerificationStatus;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    /**
     * Lấy hồ sơ nghề nghiệp công khai của Worker cho Event Organizer hoặc Admin xem.
     * Hỗ trợ tìm kiếm theo workerId hoặc userId của Worker.
     */
    @Transactional(readOnly = true)
    public WorkerPublicProfileResponseDto getWorkerProfileForEo(UUID eoUserId, UUID workerId) {
        Worker worker = workerRepository.findById(workerId)
                .or(() -> workerRepository.findByUser_UserId(workerId))
                .orElseThrow(() -> new ResourceNotFoundException("Nhân sự (Worker)", "id", workerId));

        User user = worker.getUser();
        UUID actualWorkerId = worker.getWorkerId();
        UUID workerUserId = user != null ? user.getUserId() : null;

        // 1. Tính tuổi Worker từ eKYC hoặc kinh nghiệm
        Integer workerAge = calculateWorkerAge(actualWorkerId, worker.getYearsExperience());

        // 2. Nạp tổng điểm kỹ năng (SP)
        List<WorkerSkillPoint> skillPoints = workerSkillPointRepository.findByWorker_WorkerId(actualWorkerId);
        int totalSp = skillPoints.stream()
                .mapToInt(sp -> sp.getTotalSp() != null ? sp.getTotalSp() : 0)
                .sum();

        // 3. Nạp danh sách kỹ năng (Phân tầng Tier 1, Tier 2 tích xanh AI, Tier 3)
        List<WorkerSkill> workerSkills = workerSkillRepository.findByWorker_WorkerId(actualWorkerId);
        List<WorkerSkillProfileDto> skillDtos = workerSkills.stream()
                .map(ws -> WorkerSkillProfileDto.builder()
                        .skillId(ws.getSkill() != null ? ws.getSkill().getSkillId() : null)
                        .skillName(ws.getSkill() != null ? ws.getSkill().getName() : "Kỹ năng")
                        .category(ws.getSkill() != null ? ws.getSkill().getCategory() : "Khác")
                        .proficiency(ws.getProficiency())
                        .verificationTier(ws.getVerificationTier())
                        .build())
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
                .phone(user != null ? user.getPhone() : null)
                .email(user != null ? user.getEmail() : null)
                .bio(worker.getBio())
                .yearsExperience(worker.getYearsExperience() != null ? worker.getYearsExperience() : 0)
                .isOnline(Boolean.TRUE.equals(worker.getIsOnline()))
                .age(workerAge)
                .baseAddress(worker.getBaseAddress())
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
     * Tuyệt đối không để lộ mã số thuế (taxId).
     */
    @Transactional(readOnly = true)
    public EoPublicProfileResponseDto getEoPublicProfile(UUID eoId) {
        EventOrganizer eo = eventOrganizerRepository.findById(eoId)
                .or(() -> eventOrganizerRepository.findByUser_UserId(eoId))
                .orElseThrow(() -> new ResourceNotFoundException("Nhà tổ chức sự kiện (EO)", "id", eoId));

        User user = eo.getUser();
        boolean isVerified = eo.getVerificationStatus() == VerificationStatus.APPROVED;

        return EoPublicProfileResponseDto.builder()
                .eoId(eo.getEoId())
                .userId(user != null ? user.getUserId() : null)
                .companyName(eo.getCompanyName() != null ? eo.getCompanyName() : "Ban Tổ Chức Sự Kiện")
                .avatarUrl(user != null ? user.getAvatarUrl() : null)
                .address(eo.getAddress())
                .phone(user != null ? user.getPhone() : null)
                .email(user != null ? user.getEmail() : null)
                .description("Đơn vị tổ chức sự kiện chuyên nghiệp trên nền tảng CANi")
                .isVerified(isVerified)
                .ratingAvg(eo.getRatingAvg() != null ? eo.getRatingAvg() : BigDecimal.valueOf(5.0))
                .ratingCount(eo.getRatingCount() != null ? eo.getRatingCount() : 0)
                .totalEventsCompleted(eo.getTotalEventsCompleted() != null ? eo.getTotalEventsCompleted() : 0)
                .build();
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

    private Integer calculateWorkerAge(UUID workerId, Integer yearsExperience) {
        if (workerId == null) return 22;

        Optional<WorkerEkycVerification> ekycOpt = workerEkycVerificationRepository
                .findTopByWorker_WorkerIdOrderByCreatedAtDesc(workerId);

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
                    return Period.between(birthDate, LocalDate.now()).getYears();
                }
            } catch (Exception ignored) {
                // Parse fallback
            }
        }

        if (yearsExperience != null && yearsExperience > 0) {
            return 18 + yearsExperience;
        }
        return 22;
    }
}
