package com.cani.service;

import com.cani.dto.admin.review.AdminModerateReviewRequestDto;
import com.cani.dto.admin.review.AdminReviewFilterRequestDto;
import com.cani.dto.admin.review.AdminReviewResponseDto;
import com.cani.entity.*;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final WorkerRepository workerRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired(required = false)
    private SkillProgressionService skillProgressionService;

    /**
     * Lấy danh sách đánh giá có phân trang và lọc đa tiêu chí phục vụ kiểm duyệt của Admin.
     */
    @Transactional(readOnly = true)
    public Page<AdminReviewResponseDto> getReviews(AdminReviewFilterRequestDto filter, Pageable pageable) {
        Specification<Review> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getRevieweeId() != null) {
                predicates.add(cb.equal(root.get("revieweeId"), filter.getRevieweeId()));
            }
            if (filter.getReviewerId() != null) {
                predicates.add(cb.equal(root.get("reviewerId"), filter.getReviewerId()));
            }
            if (filter.getRating() != null) {
                predicates.add(cb.equal(root.get("rating"), filter.getRating()));
            }
            if (filter.getIsHidden() != null) {
                predicates.add(cb.equal(root.get("isHidden"), filter.getIsHidden()));
            }
            if (filter.getIsFlagged() != null) {
                predicates.add(cb.equal(root.get("isFlagged"), filter.getIsFlagged()));
            }
            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFromDate()));
            }
            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getToDate()));
            }
            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String keyword = "%" + filter.getSearch().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("comment")), keyword));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Review> page = reviewRepository.findAll(spec, pageable);
        List<AdminReviewResponseDto> dtoList = mapReviewsToDtos(page.getContent());
        return new PageImpl<>(dtoList, pageable, page.getTotalElements());
    }

    /**
     * Lấy thông tin chi tiết một đánh giá.
     */
    @Transactional(readOnly = true)
    public AdminReviewResponseDto getReviewDetail(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá với ID: " + reviewId));
        return mapReviewsToDtos(List.of(review)).get(0);
    }

    /**
     * Admin ẩn/gỡ đánh giá sai lệch, tự động tính lại điểm trung bình Worker/EO và hoàn trả điểm Uy tín.
     */
    @Transactional
    public AdminReviewResponseDto hideReview(UUID adminUserId, UUID reviewId, AdminModerateReviewRequestDto request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá với ID: " + reviewId));

        if (Boolean.TRUE.equals(review.getIsHidden())) {
            throw new BadRequestException("Đánh giá này đã được ẩn trước đó.");
        }

        review.setIsHidden(true);
        review.setHiddenReason(request.getReason());
        review.setHiddenByAdminId(adminUserId);
        review.setHiddenAt(Instant.now());
        review = reviewRepository.save(review);

        // Tái tính toán số liệu và chỉ số matching
        recomputeRevieweeStats(review.getRevieweeId(), review, true);

        log.info("Admin {} đã ẩn đánh giá {} với lý do: {}", adminUserId, reviewId, request.getReason());
        return mapReviewsToDtos(List.of(review)).get(0);
    }

    /**
     * Admin khôi phục đánh giá đã ẩn và tính toán lại điểm số tương ứng.
     */
    @Transactional
    public AdminReviewResponseDto unhideReview(UUID adminUserId, UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá với ID: " + reviewId));

        if (!Boolean.TRUE.equals(review.getIsHidden())) {
            throw new BadRequestException("Đánh giá này đang ở trạng thái hiển thị bình thường.");
        }

        review.setIsHidden(false);
        review.setHiddenReason(null);
        review.setHiddenByAdminId(null);
        review.setHiddenAt(null);
        review = reviewRepository.save(review);

        // Tái tính toán lại số liệu
        recomputeRevieweeStats(review.getRevieweeId(), review, false);

        log.info("Admin {} đã khôi phục hiển thị cho đánh giá {}", adminUserId, reviewId);
        return mapReviewsToDtos(List.of(review)).get(0);
    }

    /**
     * Admin gắn cờ nghi vấn (Flag) đánh giá có dấu hiệu spam, câu điểm hoặc lạm dụng.
     */
    @Transactional
    public AdminReviewResponseDto flagReview(UUID adminUserId, UUID reviewId, AdminModerateReviewRequestDto request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá với ID: " + reviewId));

        review.setIsFlagged(true);
        review.setFlagReason(request.getReason());
        review = reviewRepository.save(review);

        log.info("Admin {} đã gắn cờ cảnh báo cho đánh giá {}: {}", adminUserId, reviewId, request.getReason());
        return mapReviewsToDtos(List.of(review)).get(0);
    }

    /**
     * Gỡ cờ cảnh báo của đánh giá.
     */
    @Transactional
    public AdminReviewResponseDto unflagReview(UUID adminUserId, UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá với ID: " + reviewId));

        review.setIsFlagged(false);
        review.setFlagReason(null);
        review = reviewRepository.save(review);

        log.info("Admin {} đã gỡ cờ cảnh báo cho đánh giá {}", adminUserId, reviewId);
        return mapReviewsToDtos(List.of(review)).get(0);
    }

    /**
     * Logic tính toán lại Rating Avg, Count, Reliability Score và kiểm tra chuẩn Tier 3.
     */
    private void recomputeRevieweeStats(UUID revieweeUserId, Review targetReview, boolean isHiding) {
        // 1. Kiểm tra nếu Reviewee là Worker
        Optional<Worker> workerOpt = workerRepository.findByUser_UserId(revieweeUserId);
        if (workerOpt.isPresent()) {
            Worker worker = workerOpt.get();
            List<Review> validReviews = reviewRepository.findByRevieweeIdAndIsHiddenFalse(revieweeUserId);
            int count = validReviews.size();
            BigDecimal avg = count == 0 ? null : BigDecimal.valueOf(
                    validReviews.stream().filter(r -> r.getRating() != null).mapToInt(Review::getRating).average().orElse(0.0)
            ).setScale(2, RoundingMode.HALF_UP);

            worker.setRatingCount(count);
            worker.setRatingAvg(avg);

            // Bù / Trừ lại điểm uy tín (Reliability Score)
            int currentRs = worker.getReliabilityScore() != null ? worker.getReliabilityScore() : 80;
            if (isHiding) {
                // Khi ẩn: Nếu review bị ẩn là 1-2 sao -> hoàn trả +2 điểm RS
                if (targetReview.getRating() != null && targetReview.getRating() <= 2) {
                    worker.setReliabilityScore(Math.min(100, currentRs + 2));
                } else if (targetReview.getRating() != null && targetReview.getRating() >= 4) {
                    // Nếu ẩn review bơm điểm (>= 4 sao) -> trừ lại -1 điểm RS
                    worker.setReliabilityScore(Math.max(0, currentRs - 1));
                }
            } else {
                // Khi khôi phục: áp dụng lại đòn phạt / thưởng
                if (targetReview.getRating() != null && targetReview.getRating() <= 2) {
                    worker.setReliabilityScore(Math.max(0, currentRs - 2));
                } else if (targetReview.getRating() != null && targetReview.getRating() >= 4) {
                    worker.setReliabilityScore(Math.min(100, currentRs + 1));
                }
            }
            workerRepository.save(worker);

            // Tái kiểm tra điều kiện thăng hạng thực chiến Tier 3 (PLATFORM_PROVEN)
            if (skillProgressionService != null) {
                try {
                    skillProgressionService.recheckTierProgression(worker);
                } catch (Exception e) {
                    log.warn("Lỗi khi recheckTierProgression cho worker {}: {}", worker.getWorkerId(), e.getMessage());
                }
            }
            return;
        }

        // 2. Kiểm tra nếu Reviewee là EventOrganizer
        Optional<EventOrganizer> eoOpt = eventOrganizerRepository.findByUser_UserId(revieweeUserId);
        if (eoOpt.isEmpty()) {
            eoOpt = eventOrganizerRepository.findById(revieweeUserId);
        }
        if (eoOpt.isPresent()) {
            EventOrganizer eo = eoOpt.get();
            List<Review> validReviews = reviewRepository.findByRevieweeIdAndIsHiddenFalse(revieweeUserId);
            int count = validReviews.size();
            BigDecimal avg = count == 0 ? null : BigDecimal.valueOf(
                    validReviews.stream().filter(r -> r.getRating() != null).mapToInt(Review::getRating).average().orElse(0.0)
            ).setScale(2, RoundingMode.HALF_UP);

            eo.setRatingCount(count);
            eo.setRatingAvg(avg);
            eventOrganizerRepository.save(eo);
        }
    }

    /**
     * Batch mapper biến đổi Entity sang DTO kèm thông tin người dùng và sự kiện liên quan.
     */
    private List<AdminReviewResponseDto> mapReviewsToDtos(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> userIds = new HashSet<>();
        Set<UUID> assignmentIds = new HashSet<>();
        for (Review r : reviews) {
            if (r.getReviewerId() != null) userIds.add(r.getReviewerId());
            if (r.getRevieweeId() != null) userIds.add(r.getRevieweeId());
            if (r.getHiddenByAdminId() != null) userIds.add(r.getHiddenByAdminId());
            if (r.getAssignmentId() != null) assignmentIds.add(r.getAssignmentId());
        }

        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, u -> u));

        Map<UUID, ShiftAssignment> assignmentMap = shiftAssignmentRepository.findAllById(assignmentIds).stream()
                .collect(Collectors.toMap(ShiftAssignment::getAssignmentId, sa -> sa));

        List<AdminReviewResponseDto> result = new ArrayList<>();
        for (Review r : reviews) {
            User reviewer = userMap.get(r.getReviewerId());
            User reviewee = userMap.get(r.getRevieweeId());
            User admin = r.getHiddenByAdminId() != null ? userMap.get(r.getHiddenByAdminId()) : null;

            ShiftAssignment sa = r.getAssignmentId() != null ? assignmentMap.get(r.getAssignmentId()) : null;
            Shift shift = sa != null ? sa.getShift() : null;
            Event event = shift != null ? shift.getEvent() : null;

            AdminReviewResponseDto dto = AdminReviewResponseDto.builder()
                    .reviewId(r.getReviewId())
                    .assignmentId(r.getAssignmentId())
                    .shiftId(shift != null ? shift.getShiftId() : null)
                    .shiftTitle(shift != null ? shift.getJobType() : null)
                    .eventId(event != null ? event.getEventId() : null)
                    .eventTitle(event != null ? event.getName() : null)
                    .reviewerId(r.getReviewerId())
                    .reviewerName(reviewer != null ? reviewer.getFullName() : null)
                    .reviewerEmail(reviewer != null ? reviewer.getEmail() : null)
                    .reviewerRole(reviewer != null && reviewer.getRole() != null ? reviewer.getRole().name() : null)
                    .revieweeId(r.getRevieweeId())
                    .revieweeName(reviewee != null ? reviewee.getFullName() : null)
                    .revieweeEmail(reviewee != null ? reviewee.getEmail() : null)
                    .revieweeRole(reviewee != null && reviewee.getRole() != null ? reviewee.getRole().name() : null)
                    .rating(r.getRating())
                    .criteriaWorkCompletion(r.getCriteriaWorkCompletion())
                    .criteriaPunctuality(r.getCriteriaPunctuality())
                    .criteriaProfessionalism(r.getCriteriaProfessionalism())
                    .criteriaVenueRules(r.getCriteriaVenueRules())
                    .criteriaTeamwork(r.getCriteriaTeamwork())
                    .criteriaShiftClarity(r.getCriteriaShiftClarity())
                    .criteriaTiming(r.getCriteriaTiming())
                    .criteriaWorkEnvironment(r.getCriteriaWorkEnvironment())
                    .criteriaSupport(r.getCriteriaSupport())
                    .criteriaPaymentTiming(r.getCriteriaPaymentTiming())
                    .comment(r.getComment())
                    .tags(r.getTags())
                    .isHidden(Boolean.TRUE.equals(r.getIsHidden()))
                    .hiddenReason(r.getHiddenReason())
                    .hiddenByAdminId(r.getHiddenByAdminId())
                    .hiddenByAdminName(admin != null ? admin.getFullName() : null)
                    .hiddenAt(r.getHiddenAt())
                    .isFlagged(Boolean.TRUE.equals(r.getIsFlagged()))
                    .flagReason(r.getFlagReason())
                    .isAutoClosed(Boolean.TRUE.equals(r.getIsAutoClosed()))
                    .createdAt(r.getCreatedAt())
                    .build();

            result.add(dto);
        }

        return result;
    }
}
