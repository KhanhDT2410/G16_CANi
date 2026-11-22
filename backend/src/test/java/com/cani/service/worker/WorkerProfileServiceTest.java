package com.cani.service.worker;

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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(workerProfileService, "eventRepository", eventRepository);
        ReflectionTestUtils.setField(workerProfileService, "shiftAssignmentRepository", shiftAssignmentRepository);
    }

    @Test
    @DisplayName("getEoPublicProfile() tính live số sự kiện, nhân sự chốt ca và reviews thật từ DB")
    void getEoPublicProfile_ComputesRealStatsAndReviewsFromDatabase() {
        UUID eoId = UUID.randomUUID();
        UUID eoUserId = UUID.randomUUID();
        UUID workerUserId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        User eoUser = User.builder()
                .userId(eoUserId)
                .fullName("Trương Hồng Quy")
                .email("quy@cani.vn")
                .phone("098536459")
                .build();

        EventOrganizer eo = EventOrganizer.builder()
                .eoId(eoId)
                .user(eoUser)
                .companyName("Quy club hà nội")
                .address("Thôn Trung, Hà Nội")
                .verificationStatus(VerificationStatus.APPROVED)
                .verifiedAt(Instant.now())
                .totalEventsCompleted(0) // Cột tĩnh chưa cập nhật
                .ratingCount(0)
                .ratingAvg(BigDecimal.ZERO)
                .build();

        Event activeEvent = Event.builder()
                .eventId(UUID.randomUUID())
                .eventOrganizer(eo)
                .name("Nhà giáo Việt Nam")
                .status(EventStatus.ACTIVE)
                .totalConfirmedWorkers(3)
                .build();
        Event completedEvent = Event.builder()
                .eventId(UUID.randomUUID())
                .eventOrganizer(eo)
                .name("Tech Expo")
                .status(EventStatus.COMPLETED)
                .totalConfirmedWorkers(5)
                .build();
        Event cancelledEvent = Event.builder()
                .eventId(UUID.randomUUID())
                .eventOrganizer(eo)
                .name("Canceled")
                .status(EventStatus.CANCELLED)
                .totalConfirmedWorkers(10)
                .build();
        Event draftEvent = Event.builder()
                .eventId(UUID.randomUUID())
                .eventOrganizer(eo)
                .name("Draft")
                .status(EventStatus.DRAFT)
                .totalConfirmedWorkers(0)
                .build();

        User workerUser = User.builder().userId(workerUserId).fullName("Nguyễn Văn Worker").build();
        Shift shift = Shift.builder().shiftId(UUID.randomUUID()).event(completedEvent).jobType("Phục vụ bàn").build();
        ShiftAssignment assignment = ShiftAssignment.builder().assignmentId(assignmentId).shift(shift).build();
        Review review = Review.builder()
                .reviewId(UUID.randomUUID())
                .assignmentId(assignmentId)
                .reviewerId(workerUserId)
                .revieweeId(eoUserId)
                .rating(5)
                .comment("Ban tổ chức hỗ trợ nhiệt tình, trả lương đúng hẹn")
                .createdAt(Instant.now())
                .build();

        when(eventOrganizerRepository.findById(eoId)).thenReturn(Optional.of(eo));
        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eoId))
                .thenReturn(List.of(activeEvent, completedEvent, cancelledEvent, draftEvent));
        when(reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(eoUserId)).thenReturn(List.of(review));
        when(reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(eoId)).thenReturn(List.of());
        when(userRepository.findAllById(List.of(workerUserId).stream().collect(java.util.stream.Collectors.toSet())))
                .thenReturn(List.of(workerUser));
        when(shiftAssignmentRepository.findAllById(List.of(assignmentId).stream().collect(java.util.stream.Collectors.toSet())))
                .thenReturn(List.of(assignment));

        EoPublicProfileResponseDto profile = workerProfileService.getEoPublicProfile(eoId);

        assertThat(profile.getTotalEventsCompleted()).isEqualTo(2); // active + completed (bỏ draft & cancelled)
        assertThat(profile.getTotalEventsCreated()).isEqualTo(3); // bỏ cancelled
        assertThat(profile.getTotalConfirmedWorkers()).isEqualTo(8); // 3 + 5
        assertThat(profile.getRatingCount()).isEqualTo(1);
        assertThat(profile.getRatingAvg()).isEqualByComparingTo("5.0");
        assertThat(profile.getDescription()).isNull(); // Không hardcode câu giới thiệu giả
        assertThat(profile.getRepresentativeName()).isEqualTo("Trương Hồng Quy");
        assertThat(profile.getReviews()).hasSize(1);
        assertThat(profile.getReviews().get(0).getComment()).contains("hỗ trợ nhiệt tình");
        assertThat(profile.getReviews().get(0).getShiftName()).isEqualTo("Phục vụ bàn");
    }
}
