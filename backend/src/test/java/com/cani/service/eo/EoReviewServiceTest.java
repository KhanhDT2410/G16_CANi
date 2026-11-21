package com.cani.service.eo;

import com.cani.client.ekyc.EkycProvider;
import com.cani.dto.event.EoReviewResponseDto;
import com.cani.entity.Review;
import com.cani.entity.User;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.ReviewRepository;
import com.cani.repository.UserRepository;
import com.cani.service.EventOrganizerEkycService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EoReviewServiceTest {

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private EoEkycVerificationRepository eoEkycVerificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EkycProvider ekycProvider;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private com.cani.repository.ShiftAssignmentRepository shiftAssignmentRepository;

    @InjectMocks
    private EventOrganizerEkycService eoEkycService;

    private UUID eoUserId;
    private UUID workerUserId;

    @BeforeEach
    void setUp() {
        eoUserId = UUID.randomUUID();
        workerUserId = UUID.randomUUID();
        ReflectionTestUtils.setField(eoEkycService, "reviewRepository", reviewRepository);
        ReflectionTestUtils.setField(eoEkycService, "shiftAssignmentRepository", shiftAssignmentRepository);
        lenient().when(eventOrganizerRepository.findByUser_UserId(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Lấy danh sách review của EO thành công khi có đánh giá từ Worker")
    void getEoReviews_Success() {
        Review review = Review.builder()
                .reviewId(UUID.randomUUID())
                .assignmentId(UUID.randomUUID())
                .reviewerId(workerUserId)
                .revieweeId(eoUserId)
                .rating(5)
                .criteriaShiftClarity(5)
                .criteriaTiming(4)
                .criteriaWorkEnvironment(5)
                .criteriaSupport(5)
                .criteriaPaymentTiming(5)
                .comment("Ban tổ chức hỗ trợ chu đáo, thanh toán đúng giờ!")
                .tags(new String[]{"Thanh toán nhanh", "Chuyên nghiệp"})
                .createdAt(Instant.now())
                .build();

        User worker = User.builder()
                .userId(workerUserId)
                .fullName("Nguyễn Văn An")
                .build();

        when(reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(eoUserId))
                .thenReturn(List.of(review));
        when(userRepository.findAllById(Set.of(workerUserId)))
                .thenReturn(List.of(worker));

        List<EoReviewResponseDto> result = eoEkycService.getEoReviews(eoUserId);

        assertNotNull(result);
        assertEquals(1, result.size());
        EoReviewResponseDto dto = result.get(0);
        assertEquals(5, dto.getRating());
        assertEquals("Nguyễn Văn An", dto.getReviewerWorkerName());
        assertEquals("Ban tổ chức hỗ trợ chu đáo, thanh toán đúng giờ!", dto.getComment());
        assertArrayEquals(new String[]{"Thanh toán nhanh", "Chuyên nghiệp"}, dto.getTags());
    }

    @Test
    @DisplayName("Trả về danh sách rỗng khi EO chưa nhận được đánh giá nào")
    void getEoReviews_Empty() {
        when(reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(eoUserId))
                .thenReturn(List.of());

        List<EoReviewResponseDto> result = eoEkycService.getEoReviews(eoUserId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Trả về danh sách rỗng khi userId là null")
    void getEoReviews_NullUserId() {
        List<EoReviewResponseDto> result = eoEkycService.getEoReviews(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(reviewRepository, never()).findByRevieweeIdOrderByCreatedAtDesc(any());
    }
}
