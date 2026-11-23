package com.cani.service.worker;

import com.cani.dto.event.EoReviewResponseDto;
import com.cani.dto.profile.EoPublicProfileResponseDto;
import com.cani.entity.*;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.VerificationStatus;
import com.cani.repository.*;
import com.cani.service.WorkerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerProfileServiceTest {

    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WorkerSkillRepository workerSkillRepository;
    @Mock
    private WorkerSkillPointRepository workerSkillPointRepository;
    @Mock
    private WorkerEkycVerificationRepository workerEkycVerificationRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private EventOrganizerRepository eventOrganizerRepository;
    @Mock
    private EoWorkerRepository eoWorkerRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @InjectMocks
    private WorkerProfileService workerProfileService;

    private UUID eoId;
    private UUID eoUserId;
    private User eoUser;
    private EventOrganizer eo;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(workerProfileService, "eventRepository", eventRepository);
        ReflectionTestUtils.setField(workerProfileService, "shiftAssignmentRepository", shiftAssignmentRepository);

        eoId = UUID.randomUUID();
        eoUserId = UUID.randomUUID();
        eoUser = User.builder()
                .userId(eoUserId)
                .fullName("Trương Hồng Quy")
                .email("quytruong1702@gmail.com")
                .phone("098536459")
                .build();

        eo = EventOrganizer.builder()
                .eoId(eoId)
                .user(eoUser)
                .companyName("Quy Club Hà Nội")
                .address("Thôn Trung, Tam Thuấn, Phúc Thọ, Hà Nội")
                .verificationStatus(VerificationStatus.APPROVED)
                .totalEventsCompleted(99) // Giá trị cũ/lệch trên bảng EO không được dùng Math.max ghi đè thực tế
                .ratingCount(0)
                .ratingAvg(BigDecimal.ZERO)
                .build();

        lenient().when(eventOrganizerRepository.findById(eoId)).thenReturn(Optional.of(eo));
    }

    @Test
    void onlyOwnerCanReadPrivateFieldsInWorkerProfile() {
        UUID userId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        User owner = User.builder().userId(userId).email("owner@example.invalid").phone("0900000000").build();
        Worker worker = Worker.builder().workerId(workerId).user(owner).baseAddress("Synthetic private address").build();
        when(workerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(worker));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        var ownProfile = workerProfileService.getMyProfile(userId);
        var publicProfile = workerProfileService.getWorkerProfileForEo(null, workerId);

        assertEquals(owner.getPhone(), ownProfile.getPhone());
        assertEquals(owner.getEmail(), ownProfile.getEmail());
        assertEquals(worker.getBaseAddress(), ownProfile.getBaseAddress());
        assertNull(publicProfile.getPhone());
        assertNull(publicProfile.getEmail());
        assertNull(publicProfile.getBaseAddress());
        assertNull(publicProfile.getAge());
    }

    @Test
    @DisplayName("1. EO không có Event và không có Review")
    void getEoPublicProfile_WhenNoEventsAndNoReviews_ReturnsZeroStats() {
        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eoId))
                .thenReturn(Collections.emptyList());

        EoPublicProfileResponseDto result = workerProfileService.getEoPublicProfile(eoId);

        assertEquals(0, result.getTotalEventsCreated());
        assertEquals(0, result.getTotalEventsPublished());
        assertEquals(0, result.getTotalEventsCompleted());
        assertEquals(0, result.getTotalConfirmedWorkers());
        assertEquals(0, result.getRatingCount());
        assertEquals(BigDecimal.ZERO, result.getRatingAvg());
        assertTrue(result.getReviews().isEmpty());
        // Quyền riêng tư: phone & email được che một phần khi người gọi chưa có ca làm chung với EO
        assertEquals("098***459", result.getPhone());
        assertEquals("qu***@gmail.com", result.getEmail());
    }

    @Test
    @DisplayName("2. Thống kê chính xác từng trạng thái Event: DRAFT, ACTIVE, ONGOING, COMPLETED, CANCELLED")
    void getEoPublicProfile_CalculatesEventStatsCorrectlyAcrossAllStatuses() {
        Event draftEvent = Event.builder().eventId(UUID.randomUUID()).status(EventStatus.DRAFT).totalConfirmedWorkers(1).build();
        Event activeEvent = Event.builder().eventId(UUID.randomUUID()).status(EventStatus.ACTIVE).totalConfirmedWorkers(3).build();
        Event ongoingEvent = Event.builder().eventId(UUID.randomUUID()).status(EventStatus.ONGOING).totalConfirmedWorkers(4).build();
        Event completedEvent1 = Event.builder().eventId(UUID.randomUUID()).status(EventStatus.COMPLETED).totalConfirmedWorkers(5).build();
        Event completedEvent2 = Event.builder().eventId(UUID.randomUUID()).status(EventStatus.COMPLETED).totalConfirmedWorkers(2).build();
        Event cancelledEvent = Event.builder().eventId(UUID.randomUUID()).status(EventStatus.CANCELLED).totalConfirmedWorkers(10).build();

        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eoId))
                .thenReturn(List.of(draftEvent, activeEvent, ongoingEvent, completedEvent1, completedEvent2, cancelledEvent));

        EoPublicProfileResponseDto result = workerProfileService.getEoPublicProfile(eoId);

        // Created = DRAFT (1) + ACTIVE (1) + ONGOING (1) + COMPLETED (2) = 5 (không tính CANCELLED)
        assertEquals(5, result.getTotalEventsCreated());
        // Published = ACTIVE (1) + ONGOING (1) + COMPLETED (2) = 4 (không tính DRAFT, CANCELLED)
        assertEquals(4, result.getTotalEventsPublished());
        // Completed = chỉ tính COMPLETED (2), không dùng Math.max với eo.totalEventsCompleted (99) hay Published (4)
        assertEquals(2, result.getTotalEventsCompleted());
        // Confirmed workers = 1 + 3 + 4 + 5 + 2 = 15 (không tính CANCELLED)
        assertEquals(15, result.getTotalConfirmedWorkers());
    }

    @Test
    @DisplayName("3. EO chỉ có Event DRAFT hoặc CANCELLED thì Published và Completed đều bằng 0")
    void getEoPublicProfile_OnlyDraftAndCancelledEvents_ReturnsZeroPublishedAndCompleted() {
        Event draft = Event.builder().eventId(UUID.randomUUID()).status(EventStatus.DRAFT).totalConfirmedWorkers(0).build();
        Event cancelled = Event.builder().eventId(UUID.randomUUID()).status(EventStatus.CANCELLED).totalConfirmedWorkers(5).build();

        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eoId))
                .thenReturn(List.of(draft, cancelled));

        EoPublicProfileResponseDto result = workerProfileService.getEoPublicProfile(eoId);

        assertEquals(1, result.getTotalEventsCreated());
        assertEquals(0, result.getTotalEventsPublished());
        assertEquals(0, result.getTotalEventsCompleted());
        assertEquals(0, result.getTotalConfirmedWorkers());
    }

    @Test
    @DisplayName("4. EO có nhiều hơn 10 Review: chỉ trả tối đa 10 Review mới nhất")
    void getEoPublicProfile_MoreThan10Reviews_ReturnsAtMost10LatestReviews() {
        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eoId))
                .thenReturn(Collections.emptyList());

        List<Review> twelveReviews = new ArrayList<>();
        Instant baseTime = Instant.parse("2026-11-20T00:00:00Z");
        for (int i = 1; i <= 12; i++) {
            twelveReviews.add(Review.builder()
                    .reviewId(UUID.randomUUID())
                    .reviewerId(UUID.randomUUID())
                    .revieweeId(eoUserId)
                    .rating(5)
                    .comment("Review #" + i)
                    .createdAt(baseTime.plusSeconds(i * 3600L))
                    .build());
        }

        when(reviewRepository.findLatestByRevieweeIds(any(), eq(PageRequest.of(0, 10))))
                .thenReturn(twelveReviews);

        EoPublicProfileResponseDto result = workerProfileService.getEoPublicProfile(eoId);

        assertEquals(10, result.getReviews().size());
        // Mới nhất (#12) phải đứng đầu, #3 đứng thứ 10 (#1 và #2 cũ nhất bị loại)
        assertEquals("Review #12", result.getReviews().get(0).getComment());
        assertEquals("Review #3", result.getReviews().get(9).getComment());
    }

    @Test
    @DisplayName("5. Review trùng ID giữa hai nguồn (eoUserId và eoId) và phân trang không làm mất Review hợp lệ thứ 10")
    void getEoPublicProfile_DuplicateReviewsAcrossSources_DeduplicatesAndFetchesNextPageToKeep10ValidReviews() {
        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eoId))
                .thenReturn(Collections.emptyList());

        Instant base = Instant.parse("2026-11-20T00:00:00Z");
        List<Review> page0WithDuplicate = new ArrayList<>();
        UUID dupId = UUID.randomUUID();
        Review dup1 = Review.builder().reviewId(dupId).reviewerId(UUID.randomUUID()).revieweeId(eoUserId)
                .rating(5).comment("Dup Review").createdAt(base.plusSeconds(1000)).build();
        Review dup2 = Review.builder().reviewId(dupId).reviewerId(dup1.getReviewerId()).revieweeId(eoId)
                .rating(5).comment("Dup Review").createdAt(base.plusSeconds(1000)).build();
        page0WithDuplicate.add(dup1);
        page0WithDuplicate.add(dup2);

        for (int i = 1; i <= 8; i++) {
            page0WithDuplicate.add(Review.builder()
                    .reviewId(UUID.randomUUID())
                    .reviewerId(UUID.randomUUID())
                    .revieweeId(eoUserId)
                    .rating(4)
                    .comment("Unique Page0 #" + i)
                    .createdAt(base.plusSeconds(900 - i * 10L))
                    .build());
        }
        // Page 0 trả về đủ 10 bản ghi nhưng sau loại trùng chỉ còn 9 Review duy nhất -> tự động lấy Page 1 để bù Review thứ 10
        Review tenthUniqueOnPage1 = Review.builder()
                .reviewId(UUID.randomUUID())
                .reviewerId(UUID.randomUUID())
                .revieweeId(eoId)
                .rating(5)
                .comment("Valid 10th Review From Page 1")
                .createdAt(base.plusSeconds(100))
                .build();

        when(reviewRepository.findLatestByRevieweeIds(any(), eq(PageRequest.of(0, 10))))
                .thenReturn(page0WithDuplicate);
        when(reviewRepository.findLatestByRevieweeIds(any(), eq(PageRequest.of(1, 10))))
                .thenReturn(List.of(tenthUniqueOnPage1));

        EoPublicProfileResponseDto result = workerProfileService.getEoPublicProfile(eoId);

        assertEquals(10, result.getReviews().size());
        assertEquals(10, result.getReviews().stream().map(EoReviewResponseDto::getReviewId).distinct().count());
        assertEquals("Dup Review", result.getReviews().get(0).getComment());
        assertEquals("Valid 10th Review From Page 1", result.getReviews().get(9).getComment());
    }

    @Test
    @DisplayName("6. Review có createdAt == null luôn bị đẩy xuống cuối danh sách, không đứng trước Review có thời gian hợp lệ")
    void getEoPublicProfile_ReviewWithNullCreatedAt_PlacedAtTheEnd() {
        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eoId))
                .thenReturn(Collections.emptyList());

        Review nullDateReview = Review.builder()
                .reviewId(UUID.randomUUID())
                .reviewerId(UUID.randomUUID())
                .revieweeId(eoUserId)
                .rating(5)
                .comment("Legacy Null Date Review")
                .createdAt(null)
                .build();
        Review olderReview = Review.builder()
                .reviewId(UUID.randomUUID())
                .reviewerId(UUID.randomUUID())
                .revieweeId(eoUserId)
                .rating(4)
                .comment("Older Valid Review")
                .createdAt(Instant.parse("2026-11-18T10:00:00Z"))
                .build();
        Review newerReview = Review.builder()
                .reviewId(UUID.randomUUID())
                .reviewerId(UUID.randomUUID())
                .revieweeId(eoUserId)
                .rating(5)
                .comment("Newer Valid Review")
                .createdAt(Instant.parse("2026-11-21T10:00:00Z"))
                .build();

        when(reviewRepository.findLatestByRevieweeIds(any(), any(Pageable.class)))
                .thenReturn(List.of(nullDateReview, olderReview, newerReview));

        EoPublicProfileResponseDto result = workerProfileService.getEoPublicProfile(eoId);

        assertEquals(3, result.getReviews().size());
        assertEquals("Newer Valid Review", result.getReviews().get(0).getComment());
        assertEquals("Older Valid Review", result.getReviews().get(1).getComment());
        assertEquals("Legacy Null Date Review", result.getReviews().get(2).getComment());
        assertNull(result.getReviews().get(2).getCreatedAt());
    }

    @Test
    @DisplayName("7. Xử lý Rating không hợp lệ (null, < 1, > 5) và tách biệt đúng reviewerUserId với reviewerWorkerId")
    void getEoPublicProfile_InvalidRatingsAndReviewerIdentity_MapsAccurately() {
        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eoId))
                .thenReturn(Collections.emptyList());

        UUID workerUserId = UUID.randomUUID();
        UUID actualWorkerEntityId = UUID.randomUUID();
        User workerUser = User.builder().userId(workerUserId).fullName("Nguyễn Văn A").build();
        Worker workerEntity = Worker.builder().workerId(actualWorkerEntityId).user(workerUser).build();

        UUID nonWorkerUserId = UUID.randomUUID();

        Review validReview = Review.builder()
                .reviewId(UUID.randomUUID())
                .reviewerId(workerUserId)
                .revieweeId(eoUserId)
                .rating(4)
                .comment("Valid 4 stars")
                .createdAt(Instant.parse("2026-11-21T12:00:00Z"))
                .build();
        Review outOfRangeHighReview = Review.builder()
                .reviewId(UUID.randomUUID())
                .reviewerId(nonWorkerUserId)
                .revieweeId(eoUserId)
                .rating(9) // > 5 -> không hợp lệ
                .comment("Invalid rating 9")
                .createdAt(Instant.parse("2026-11-21T11:00:00Z"))
                .build();
        Review outOfRangeLowReview = Review.builder()
                .reviewId(UUID.randomUUID())
                .reviewerId(nonWorkerUserId)
                .revieweeId(eoUserId)
                .rating(0) // < 1 -> không hợp lệ
                .comment("Invalid rating 0")
                .createdAt(Instant.parse("2026-11-21T10:00:00Z"))
                .build();
        Review nullRatingReview = Review.builder()
                .reviewId(UUID.randomUUID())
                .reviewerId(nonWorkerUserId)
                .revieweeId(eoUserId)
                .rating(null)
                .comment("Null rating")
                .createdAt(Instant.parse("2026-11-21T09:00:00Z"))
                .build();

        when(reviewRepository.findLatestByRevieweeIds(any(), any(Pageable.class)))
                .thenReturn(List.of(validReview, outOfRangeHighReview, outOfRangeLowReview, nullRatingReview));
        when(userRepository.findAllById(any())).thenReturn(List.of(workerUser));
        when(workerRepository.findByUser_UserIdIn(any())).thenReturn(List.of(workerEntity));

        EoPublicProfileResponseDto result = workerProfileService.getEoPublicProfile(eoId);

        // Chỉ có 1 review có rating hợp lệ (4 sao) được tính vào ratingCount và ratingAvg
        assertEquals(1, result.getRatingCount());
        assertEquals(new BigDecimal("4.0"), result.getRatingAvg());

        EoReviewResponseDto dto0 = result.getReviews().get(0);
        assertEquals(workerUserId, dto0.getReviewerUserId());
        assertEquals(actualWorkerEntityId, dto0.getReviewerWorkerId());
        assertEquals(4, dto0.getRating());

        // Người đánh giá không có bản ghi Worker thì reviewerWorkerId phải là null (không được gán nhầm UserId)
        EoReviewResponseDto dto1 = result.getReviews().get(1);
        assertEquals(nonWorkerUserId, dto1.getReviewerUserId());
        assertNull(dto1.getReviewerWorkerId());
        assertNull(dto1.getRating());

        assertNull(result.getReviews().get(2).getRating());
        assertNull(result.getReviews().get(3).getRating());
    }
}
