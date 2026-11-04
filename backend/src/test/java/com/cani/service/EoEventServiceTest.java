package com.cani.service;

import com.cani.dto.event.CreateEventRequestDto;
import com.cani.dto.event.CreateShiftRequestDto;
import com.cani.dto.event.EventResponseDto;
import com.cani.dto.event.ShiftResponseDto;
import com.cani.entity.Event;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Shift;
import com.cani.entity.User;
import com.cani.entity.enums.EventMode;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EoEventServiceTest {

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private MatchingDispatchService matchingDispatchService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private EoEventService eoEventService;

    private UUID userId;
    private UUID eoId;
    private User mockUser;
    private EventOrganizer mockEo;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eoId = UUID.randomUUID();

        mockUser = User.builder()
                .userId(userId)
                .email("eo@cani.vn")
                .fullName("Duy Hưng")
                .build();

        mockEo = EventOrganizer.builder()
                .eoId(eoId)
                .user(mockUser)
                .companyName("Cani Event Co")
                .build();

        ReflectionTestUtils.setField(eoEventService, "subscriptionService", subscriptionService);
    }

    @Test
    @DisplayName("Tạo sự kiện draft thành công mà không bị chặn bởi Quota trước")
    void createEvent_Success_DraftWithoutPreQuota() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event e = invocation.getArgument(0);
            e.setEventId(UUID.randomUUID());
            return e;
        });

        CreateEventRequestDto dto = CreateEventRequestDto.builder()
                .name("Hội nghị Tech 2026")
                .description("Hội nghị công nghệ cao cấp")
                .venueAddress("Trung tâm Hội nghị Quốc gia, Hà Nội")
                .venueLat(21.006)
                .venueLng(105.783)
                .eventDate(LocalDate.now().plusDays(7))
                .mode(EventMode.MATCHING)
                .build();

        EventResponseDto result = eoEventService.createEvent(userId, dto);

        assertNotNull(result);
        assertEquals("Hội nghị Tech 2026", result.getName());
        assertEquals(EventStatus.DRAFT, result.getStatus());
        verify(eventRepository, times(1)).save(any(Event.class));
        // Verify rằng quota chưa bị trừ ở bước tạo draft
        verify(subscriptionService, never()).validateAndDeductEventQuota(any(EventOrganizer.class));
    }

    @Test
    @DisplayName("Thêm ca làm việc (Shift) thành công kèm checklist JSON và cập nhật tổng tiền sự kiện")
    void addShiftToEvent_Success_WithChecklistAndEventTotals() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .name("Sự kiện Test")
                .status(EventStatus.DRAFT)
                .venueAddress("Hà Nội")
                .totalShifts(0)
                .totalRequiredWorkers(0)
                .totalEstimatedCost(BigDecimal.ZERO)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> {
            Shift s = invocation.getArgument(0);
            s.setShiftId(UUID.randomUUID());
            return s;
        });

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        String checklistJson = "[\"Check-in đúng giờ\",\"Mặc đồng phục\",\"Hoàn thành dọn dẹp\"]";

        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Lễ tân / Check-in")
                .description("Đón khách VIP tại sảnh A")
                .startTime(start)
                .endTime(end)
                .requiredWorkers(4)
                .hourlyWage(BigDecimal.valueOf(50000))
                .allowance(BigDecimal.valueOf(20000))
                .checklist(checklistJson)
                .build();

        ShiftResponseDto result = eoEventService.addShiftToEvent(userId, eventId, shiftDto);

        assertNotNull(result);
        assertEquals("Lễ tân / Check-in", result.getJobType());
        assertEquals(checklistJson, result.getChecklist());
        assertEquals(4, result.getRequiredWorkers());
        // 4 hours * 4 workers * 50,000 + 20,000 allowance = 820,000 VND
        assertEquals(0, new BigDecimal("820000").compareTo(result.getTotalCost()));

        // Verify tổng ca và chi phí của Event được cập nhật
        assertEquals(1, mockEvent.getTotalShifts());
        assertEquals(4, mockEvent.getTotalRequiredWorkers());
        assertEquals(0, new BigDecimal("820000").compareTo(mockEvent.getTotalEstimatedCost()));
        verify(eventRepository, times(1)).save(mockEvent);
    }

    @Test
    @DisplayName("Hủy ca làm việc thành công và chuyển trạng thái CANCELLED")
    void cancelShift_Success() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(UUID.randomUUID())
                .eventOrganizer(mockEo)
                .build();

        Shift mockShift = Shift.builder()
                .shiftId(shiftId)
                .event(mockEvent)
                .jobType("Bảo vệ")
                .status(ShiftStatus.DRAFT)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(Collections.emptyList());

        ShiftResponseDto result = eoEventService.cancelShift(userId, shiftId, "Ban tổ chức điều chỉnh quy mô");

        assertNotNull(result);
        assertEquals(ShiftStatus.CANCELLED, result.getStatus());
        verify(shiftRepository, times(1)).save(mockShift);
    }

    @Test
    @DisplayName("Publish sự kiện kiểm tra và trừ quota subscription")
    void publishEvent_Success_ConsumesQuota() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .name("Sự kiện cần kích hoạt")
                .status(EventStatus.DRAFT)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(Collections.emptyList());

        EventResponseDto result = eoEventService.publishEvent(userId, eventId);

        assertNotNull(result);
        assertEquals(EventStatus.ACTIVE, result.getStatus());
        verify(subscriptionService, times(1)).validateAndDeductEventQuota(mockEo);
    }
}
