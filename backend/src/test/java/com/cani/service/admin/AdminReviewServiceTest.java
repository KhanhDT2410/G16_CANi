package com.cani.service.admin;

import com.cani.dto.admin.review.AdminModerateReviewRequestDto;
import com.cani.dto.admin.review.AdminReviewFilterRequestDto;
import com.cani.dto.admin.review.AdminReviewResponseDto;
import com.cani.entity.Review;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.UserRole;
import com.cani.repository.*;
import com.cani.service.AdminReviewService;
import com.cani.service.SkillProgressionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private SkillProgressionService skillProgressionService;

    @InjectMocks
    private AdminReviewService adminReviewService;

    private UUID adminUserId;
    private UUID workerUserId;
    private UUID eoUserId;
    private UUID reviewId;

    @BeforeEach
    void setUp() {
        adminUserId = UUID.randomUUID();
        workerUserId = UUID.randomUUID();
        eoUserId = UUID.randomUUID();
        reviewId = UUID.randomUUID();

        ReflectionTestUtils.setField(adminReviewService, "skillProgressionService", skillProgressionService);
    }

    @Test
    @DisplayName("Lấy danh sách review với bộ lọc thành công")
    void getReviews_Success() {
        Review review = Review.builder()
                .reviewId(reviewId)
                .reviewerId(eoUserId)
                .revieweeId(workerUserId)
                .rating(5)
                .comment("Làm rất tốt")
                .createdAt(Instant.now())
                .build();

        Page<Review> page = new PageImpl<>(List.of(review), PageRequest.of(0, 10), 1);
        when(reviewRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(List.of(
                User.builder().userId(eoUserId).fullName("Ban Tổ Chức A").role(UserRole.EVENT_ORGANIZER).build(),
                User.builder().userId(workerUserId).fullName("Nguyễn Văn Worker").role(UserRole.WORKER).build()
        ));

        AdminReviewFilterRequestDto filter = AdminReviewFilterRequestDto.builder().rating(5).build();
        Page<AdminReviewResponseDto> result = adminReviewService.getReviews(filter, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Làm rất tốt", result.getContent().get(0).getComment());
        assertEquals("Ban Tổ Chức A", result.getContent().get(0).getReviewerName());
    }

    @Test
    @DisplayName("Gỡ review 1★ oan uổng: Tính lại ratingAvg tăng lên và hoàn trả +2 điểm Uy tín (RS)")
    void hideReview_WorkerPenalized_RestoresRatingAndReliabilityScore() {
        Review abusiveReview = Review.builder()
                .reviewId(reviewId)
                .reviewerId(eoUserId)
                .revieweeId(workerUserId)
                .rating(1)
                .comment("Đánh giá oan 1 sao")
                .isHidden(false)
                .createdAt(Instant.now())
                .build();

        Review validGoodReview = Review.builder()
                .reviewId(UUID.randomUUID())
                .reviewerId(UUID.randomUUID())
                .revieweeId(workerUserId)
                .rating(5)
                .comment("Làm việc tốt")
                .isHidden(false)
                .createdAt(Instant.now())
                .build();

        Worker worker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(User.builder().userId(workerUserId).build())
                .ratingAvg(BigDecimal.valueOf(3.0)) // trung bình khi có cả 1★ và 5★
                .ratingCount(2)
                .reliabilityScore(88) // Đã bị trừ 2 điểm lúc nhận 1★
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(abusiveReview));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));
        when(workerRepository.findByUser_UserId(workerUserId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(any(Worker.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                User.builder().userId(adminUserId).fullName("Admin Moderation").role(UserRole.ADMIN).build()
        ));

        // Sau khi review 1★ bị ẩn, chỉ còn review 5★
        when(reviewRepository.findByRevieweeIdAndIsHiddenFalse(workerUserId)).thenReturn(List.of(validGoodReview));

        AdminModerateReviewRequestDto request = AdminModerateReviewRequestDto.builder()
                .reason("EO đánh giá oan uổng do mâu thuẫn cá nhân")
                .build();

        AdminReviewResponseDto result = adminReviewService.hideReview(adminUserId, reviewId, request);

        assertTrue(result.getIsHidden());
        assertEquals("EO đánh giá oan uổng do mâu thuẫn cá nhân", result.getHiddenReason());

        // Kiểm tra phục hồi dữ liệu Worker:
        // 1. Rating count giảm về 1
        assertEquals(1, worker.getRatingCount());
        // 2. Rating avg tăng lại thành 5.00
        assertEquals(new BigDecimal("5.00"), worker.getRatingAvg());
        // 3. Điểm uy tín RS được hoàn lại +2 (88 + 2 = 90)
        assertEquals(90, worker.getReliabilityScore());

        // 4. Đã gọi recheck thăng hạng Tier 3
        verify(skillProgressionService, times(1)).recheckTierProgression(worker);
    }

    @Test
    @DisplayName("Khôi phục review đã ẩn: Áp dụng lại số liệu")
    void unhideReview_Success() {
        Review hiddenReview = Review.builder()
                .reviewId(reviewId)
                .reviewerId(eoUserId)
                .revieweeId(workerUserId)
                .rating(1)
                .isHidden(true)
                .hiddenReason("Lý do cũ")
                .createdAt(Instant.now())
                .build();

        Worker worker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(User.builder().userId(workerUserId).build())
                .ratingAvg(BigDecimal.valueOf(5.0))
                .ratingCount(1)
                .reliabilityScore(90)
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(hiddenReview));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));
        when(workerRepository.findByUser_UserId(workerUserId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(any(Worker.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(reviewRepository.findByRevieweeIdAndIsHiddenFalse(workerUserId)).thenReturn(List.of(hiddenReview));

        AdminReviewResponseDto result = adminReviewService.unhideReview(adminUserId, reviewId);

        assertFalse(result.getIsHidden());
        assertNull(hiddenReview.getHiddenReason());
        // RS bị trừ lại 2 điểm (90 - 2 = 88)
        assertEquals(88, worker.getReliabilityScore());
    }

    @Test
    @DisplayName("Gắn cờ nghi vấn (Flag) review lạm dụng thành công")
    void flagReview_Success() {
        Review review = Review.builder()
                .reviewId(reviewId)
                .reviewerId(eoUserId)
                .revieweeId(workerUserId)
                .rating(5)
                .isFlagged(false)
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        AdminModerateReviewRequestDto request = AdminModerateReviewRequestDto.builder()
                .reason("Nghi vấn spam đánh giá chéo giữa các tài khoản thân thiết")
                .build();

        AdminReviewResponseDto result = adminReviewService.flagReview(adminUserId, reviewId, request);

        assertTrue(result.getIsFlagged());
        assertEquals("Nghi vấn spam đánh giá chéo giữa các tài khoản thân thiết", result.getFlagReason());
    }
}

