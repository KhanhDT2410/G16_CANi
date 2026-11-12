package com.cani.service.eo;

import com.cani.service.*;

import com.cani.dto.event.*;
import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Exhaustive Unit Test Suite for EoEventService conforming to SKILL.md and Report-5.1_Unit Test.xlsx standard.
 * Covers 66 test cases (Normal, Abnormal, Boundary) across all 14 service methods using AAA pattern.
 */
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
    private WorkerEkycVerificationRepository workerEkycVerificationRepository;

    @Mock
    private MatchingDispatchService matchingDispatchService;

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
                .role(UserRole.EVENT_ORGANIZER)
                .build();

        mockEo = EventOrganizer.builder()
                .eoId(eoId)
                .user(mockUser)
                .companyName("Cani Event Co")
                .verificationStatus(VerificationStatus.APPROVED)
                .build();

        ReflectionTestUtils.setField(eoEventService, "subscriptionService", subscriptionService);
        ReflectionTestUtils.setField(eoEventService, "objectMapper", new ObjectMapper());
    }


    // =========================================================================
    // createEvent()
    // =========================================================================

    @Test
    @DisplayName("UTC001 - createEvent() - Normal: User authenticated as EO; EventRepository mocked")
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
        verify(subscriptionService, never()).validateAndDeductEventQuota(any(EventOrganizer.class));
    }

    @Test
    @DisplayName("UTC001_EKYC - createEvent() - Abnormal: EO not yet verified eKYC (status=PENDING)")
    void createEvent_ThrowsBadRequest_WhenEoNotVerifiedEkyc() {
        EventOrganizer unverifiedEo = EventOrganizer.builder()
                .eoId(eoId)
                .user(mockUser)
                .companyName("Unverified EO")
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(unverifiedEo));

        CreateEventRequestDto dto = CreateEventRequestDto.builder()
                .name("Sự kiện chưa eKYC")
                .venueAddress("Hà Nội")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.createEvent(userId, dto));
        assertTrue(ex.getMessage().contains("chưa hoàn tất định danh eKYC"));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("UTC002 - createEvent() - Abnormal: User exists with role=WORKER; EO not found in system")
    void createEvent_ThrowsBadRequest_WhenUserIsNotEoOrAdmin() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());
        User workerUser = User.builder().userId(userId).role(UserRole.WORKER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(workerUser));

        CreateEventRequestDto dto = CreateEventRequestDto.builder().name("Sự kiện Worker").venueAddress("Hà Nội").build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.createEvent(userId, dto));
        assertTrue(ex.getMessage().contains("Tài khoản không phải là Nhà tổ chức sự kiện"));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("UTC002b - createEvent() - Abnormal: User exists with role=ADMIN; Blocked from creating event")
    void createEvent_ThrowsBadRequest_WhenUserIsAdmin() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());
        User adminUser = User.builder().userId(userId).role(UserRole.ADMIN).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        CreateEventRequestDto dto = CreateEventRequestDto.builder().name("Sự kiện Admin").venueAddress("TP HCM").build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.createEvent(userId, dto));
        assertTrue(ex.getMessage().contains("Tài khoản không phải là Nhà tổ chức sự kiện"));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("UTC003 - createEvent() - Boundary: User authenticated as EO; EventRepository mocked")
    void createEvent_Success_BoundaryCoordinatesAndCurrentDate() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event e = invocation.getArgument(0);
            e.setEventId(UUID.randomUUID());
            return e;
        });

        CreateEventRequestDto dto = CreateEventRequestDto.builder()
                .name("Sự kiện vùng biên").venueAddress("Cực Bắc")
                .venueLat(90.0).venueLng(180.0).eventDate(LocalDate.now()).mode(EventMode.INVITE).build();

        EventResponseDto result = eoEventService.createEvent(userId, dto);

        assertNotNull(result);
        assertEquals(90.0, result.getVenueLat());
        assertEquals(180.0, result.getVenueLng());
        assertEquals(LocalDate.now(), result.getEventDate());
    }

    @Test
    @DisplayName("UTC004 - createEvent() - Normal: User has role=EVENT_ORGANIZER but no EO entity exists yet")
    void createEvent_Success_AutoProvisionsEo_WhenUserIsEo() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(eventOrganizerRepository.save(any(EventOrganizer.class))).thenReturn(mockEo);
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> {
            Event e = i.getArgument(0);
            e.setEventId(UUID.randomUUID());
            return e;
        });

        CreateEventRequestDto dto = CreateEventRequestDto.builder().name("Sự kiện Khởi tạo").venueAddress("Hà Nội").build();
        EventResponseDto result = eoEventService.createEvent(userId, dto);

        assertNotNull(result);
        assertEquals("Sự kiện Khởi tạo", result.getName());
        verify(eventOrganizerRepository, times(1)).save(any(EventOrganizer.class));
    }

    @Test
    @DisplayName("UTC005 - createEvent() - Abnormal: User ID does not exist in userRepository")
    void createEvent_ThrowsNotFound_WhenUserDoesNotExist() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CreateEventRequestDto dto = CreateEventRequestDto.builder().name("Sự kiện Vô danh").build();

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eoEventService.createEvent(userId, dto));
        assertTrue(ex.getMessage().contains("Không tìm thấy thông tin User"));
    }

    @Test
    @DisplayName("UTC006 - createEvent() - Boundary: User authenticated as EO; EventRepository mocked")
    void createEvent_Success_NegativeBoundaryCoordinatesAndNullDesc() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> {
            Event e = i.getArgument(0);
            e.setEventId(UUID.randomUUID());
            return e;
        });

        CreateEventRequestDto dto = CreateEventRequestDto.builder()
                .name("Sự kiện Nam Cực").venueAddress("Nam Cực")
                .venueLat(-90.0).venueLng(-180.0).description(null).build();

        EventResponseDto result = eoEventService.createEvent(userId, dto);

        assertNotNull(result);
        assertEquals(-90.0, result.getVenueLat());
        assertEquals(-180.0, result.getVenueLng());
        assertNull(result.getDescription());
    }


    // =========================================================================
    // updateEvent()
    // =========================================================================

    @Test
    @DisplayName("UTC007 - updateEvent() - Normal: User authenticated as EO; Event status=DRAFT exists")
    void updateEvent_Success_DraftEvent() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(eventId).eventOrganizer(mockEo)
                .name("Tên cũ").description("Mô tả cũ").status(EventStatus.DRAFT)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(Collections.emptyList());

        UpdateEventRequestDto dto = UpdateEventRequestDto.builder()
                .name("Tên mới đã sửa").description("Mô tả mới").venueAddress("Đà Nẵng").build();

        EventResponseDto result = eoEventService.updateEvent(userId, eventId, dto);

        assertNotNull(result);
        assertEquals("Tên mới đã sửa", result.getName());
        verify(eventRepository, times(1)).save(mockEvent);
    }

    @Test
    @DisplayName("UTC008 - updateEvent() - Abnormal: User authenticated as EO; Event status=CANCELLED")
    void updateEvent_ThrowsBadRequest_WhenEventCancelled() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.CANCELLED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        UpdateEventRequestDto dto = UpdateEventRequestDto.builder().name("Sửa sự kiện đã hủy").build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.updateEvent(userId, eventId, dto));
        assertTrue(ex.getMessage().contains("Không thể chỉnh sửa sự kiện đã hoàn thành hoặc đã bị hủy"));
    }

    @Test
    @DisplayName("UTC009 - updateEvent() - Boundary: User authenticated as EO; Event status=DRAFT exists with coordinates")
    void updateEvent_Success_BoundaryNullCoordinatesAndDate() {
        UUID eventId = UUID.randomUUID();
        LocalDate originalDate = LocalDate.now().plusDays(5);
        Event mockEvent = Event.builder()
                .eventId(eventId).eventOrganizer(mockEo).name("Sự kiện gốc")
                .venueLat(10.776).venueLng(106.700).eventDate(originalDate).status(EventStatus.DRAFT)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(Collections.emptyList());

        UpdateEventRequestDto dto = UpdateEventRequestDto.builder().name("Sự kiện đổi tên").build();

        EventResponseDto result = eoEventService.updateEvent(userId, eventId, dto);

        assertNotNull(result);
        assertEquals(10.776, result.getVenueLat());
        assertEquals(106.700, result.getVenueLng());
        assertEquals(originalDate, result.getEventDate());
    }

    @Test
    @DisplayName("UTC010 - updateEvent() - Abnormal: User authenticated as EO; Event status=COMPLETED")
    void updateEvent_ThrowsBadRequest_WhenEventCompleted() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.COMPLETED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        UpdateEventRequestDto dto = UpdateEventRequestDto.builder().name("Sửa sự kiện đã kết thúc").build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.updateEvent(userId, eventId, dto));
        assertTrue(ex.getMessage().contains("Không thể chỉnh sửa sự kiện đã hoàn thành hoặc đã bị hủy"));
    }

    @Test
    @DisplayName("UTC011 - updateEvent() - Abnormal: User authenticated as EO; Event does not belong to EO")
    void updateEvent_ThrowsNotFound_WhenEventNotOwnedByEo() {
        UUID eventId = UUID.randomUUID();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.empty());

        UpdateEventRequestDto dto = UpdateEventRequestDto.builder().name("Đổi tên sự kiện người khác").build();

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eoEventService.updateEvent(userId, eventId, dto));
        assertTrue(ex.getMessage().contains("Không tìm thấy sự kiện ID"));
    }

    @Test
    @DisplayName("UTC012 - updateEvent() - Normal: User authenticated as EO; Event status=DRAFT exists with mode=MATCHING")
    void updateEvent_Success_SwitchesEventMode() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).mode(EventMode.MATCHING).status(EventStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(Collections.emptyList());

        UpdateEventRequestDto dto = UpdateEventRequestDto.builder().name("Sự kiện").mode(EventMode.INVITE).build();

        EventResponseDto result = eoEventService.updateEvent(userId, eventId, dto);

        assertNotNull(result);
        assertEquals(EventMode.INVITE, result.getMode());
    }


    // =========================================================================
    // cancelEvent()
    // =========================================================================

    @Test
    @DisplayName("UTC013 - cancelEvent() - Normal: User authenticated as EO; Event status=ACTIVE with 1 active Shift")
    void cancelEvent_Success_CancelsEventAndChildShifts() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.ACTIVE).build();
        UUID shiftId = UUID.randomUUID();
        Shift mockShift = Shift.builder().shiftId(shiftId).status(ShiftStatus.ACTIVE).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(List.of(mockShift));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(Collections.emptyList());

        EventResponseDto result = eoEventService.cancelEvent(userId, eventId, "Lý do thời tiết xấu");

        assertNotNull(result);
        assertEquals(EventStatus.CANCELLED, mockEvent.getStatus());
        assertEquals(ShiftStatus.CANCELLED, mockShift.getStatus());
    }

    @Test
    @DisplayName("UTC014 - cancelEvent() - Abnormal: User authenticated as EO; Event already CANCELLED")
    void cancelEvent_ThrowsBadRequest_WhenAlreadyCancelled() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.CANCELLED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.cancelEvent(userId, eventId, "Hủy tiếp"));
        assertTrue(ex.getMessage().contains("Sự kiện này đã bị hủy trước đó"));
    }

    @Test
    @DisplayName("UTC015 - cancelEvent() - Boundary: User authenticated as EO; Event status=DRAFT with 1 Assignment")
    void cancelEvent_Success_BoundaryBlankReason_UsesDefaultReason() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.DRAFT).build();
        UUID shiftId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(UUID.randomUUID()).status(AssignmentStatus.CONFIRMED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(List.of(Shift.builder().shiftId(shiftId).build()));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(List.of(sa));

        EventResponseDto result = eoEventService.cancelEvent(userId, eventId, "   ");

        assertNotNull(result);
        assertEquals("Ban tổ chức hủy sự kiện", sa.getCancellationReason());
        assertEquals(AssignmentStatus.CANCELLED, sa.getStatus());
    }

    @Test
    @DisplayName("UTC016 - cancelEvent() - Normal: User authenticated as EO; Shift has 1 COMPLETED and 1 CONFIRMED assignment")
    void cancelEvent_Success_PreservesCompletedAssignments() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.ACTIVE).build();
        UUID shiftId = UUID.randomUUID();
        Shift mockShift = Shift.builder().shiftId(shiftId).status(ShiftStatus.ACTIVE).build();
        ShiftAssignment completedSa = ShiftAssignment.builder().assignmentId(UUID.randomUUID()).status(AssignmentStatus.COMPLETED).build();
        ShiftAssignment activeSa = ShiftAssignment.builder().assignmentId(UUID.randomUUID()).status(AssignmentStatus.CONFIRMED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(List.of(mockShift));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(List.of(completedSa, activeSa));

        eoEventService.cancelEvent(userId, eventId, "Hủy dịch bệnh");

        assertEquals(AssignmentStatus.COMPLETED, completedSa.getStatus());
        assertEquals(AssignmentStatus.CANCELLED, activeSa.getStatus());
    }

    @Test
    @DisplayName("UTC017 - cancelEvent() - Boundary: User authenticated as EO; EO user field is null")
    void cancelEvent_Success_NullReasonAndNullEoUser_UsesFallback() {
        UUID eventId = UUID.randomUUID();
        EventOrganizer eoWithoutUser = EventOrganizer.builder().eoId(eoId).user(null).build();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(eoWithoutUser).status(EventStatus.DRAFT).build();
        UUID shiftId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(UUID.randomUUID()).status(AssignmentStatus.CONFIRMED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(eoWithoutUser));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(List.of(Shift.builder().shiftId(shiftId).build()));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(List.of(sa));

        eoEventService.cancelEvent(userId, eventId, null);

        assertEquals("Ban tổ chức hủy sự kiện", sa.getCancellationReason());
        assertEquals(userId, sa.getCancelledBy());
    }


    // =========================================================================
    // addShiftToEvent()
    // =========================================================================

    @Test
    @DisplayName("UTC018 - addShiftToEvent() - Normal: User authenticated as EO; Event status=DRAFT")
    void addShiftToEvent_Success_WithChecklistAndEventTotals() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).name("Sự kiện Test").status(EventStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> {
            Shift s = invocation.getArgument(0);
            s.setShiftId(UUID.randomUUID());
            return s;
        });

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(4, ChronoUnit.HOURS);

        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Lễ tân / Check-in").startTime(start).endTime(end)
                .requiredWorkers(4).hourlyWage(BigDecimal.valueOf(50000)).allowance(BigDecimal.valueOf(20000))
                .build();

        ShiftResponseDto result = eoEventService.addShiftToEvent(userId, eventId, shiftDto);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("820000").compareTo(result.getTotalCost()));
        assertEquals(1, mockEvent.getTotalShifts());
    }

    @Test
    @DisplayName("UTC019 - addShiftToEvent() - Abnormal: User authenticated as EO; Event exists")
    void addShiftToEvent_ThrowsBadRequest_WhenEndTimeBeforeStartTime() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Phục vụ").startTime(start).endTime(start.minus(1, ChronoUnit.HOURS))
                .requiredWorkers(2).hourlyWage(BigDecimal.valueOf(50000)).build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.addShiftToEvent(userId, eventId, shiftDto));
        assertTrue(ex.getMessage().contains("Thời gian kết thúc ca làm việc phải sau thời gian bắt đầu"));
    }

    @Test
    @DisplayName("UTC020 - addShiftToEvent() - Boundary: User authenticated as EO; Event exists")
    void addShiftToEvent_Success_BoundaryDurationUnder30Min_AppliesMinBillableHours() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> {
            Shift s = i.getArgument(0);
            s.setShiftId(UUID.randomUUID());
            return s;
        });

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Hỗ trợ nhanh").startTime(start).endTime(start.plus(15, ChronoUnit.MINUTES))
                .requiredWorkers(1).hourlyWage(BigDecimal.valueOf(60000)).build();

        ShiftResponseDto result = eoEventService.addShiftToEvent(userId, eventId, shiftDto);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("30000").compareTo(result.getTotalCost()));
    }

    @Test
    @DisplayName("UTC021 - addShiftToEvent() - Normal: User authenticated as EO; Event exists with venueAddress='Hà Nội', lat=21.0, lng=105.8")
    void addShiftToEvent_Success_InheritsEventLocation_WhenShiftLocationNull() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(eventId).eventOrganizer(mockEo).venueAddress("Hà Nội").venueLat(21.0).venueLng(105.8).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> {
            Shift s = i.getArgument(0);
            s.setShiftId(UUID.randomUUID());
            return s;
        });

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Hậu cần").startTime(start).endTime(start.plus(2, ChronoUnit.HOURS))
                .requiredWorkers(2).hourlyWage(BigDecimal.valueOf(50000)).locationAddress(null).build();

        ShiftResponseDto result = eoEventService.addShiftToEvent(userId, eventId, shiftDto);

        assertNotNull(result);
        assertEquals("Hà Nội", result.getLocationAddress());
        assertEquals(21.0, result.getLocationLat());
    }

    @Test
    @DisplayName("UTC022 - addShiftToEvent() - Normal: User authenticated as EO; Event status=ACTIVE")
    void addShiftToEvent_Success_ShiftStatusActive_WhenParentEventIsActive() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.ACTIVE).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> {
            Shift s = i.getArgument(0);
            s.setShiftId(UUID.randomUUID());
            return s;
        });

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Bảo vệ").startTime(start).endTime(start.plus(3, ChronoUnit.HOURS))
                .requiredWorkers(2).hourlyWage(BigDecimal.valueOf(50000)).build();

        ShiftResponseDto result = eoEventService.addShiftToEvent(userId, eventId, shiftDto);

        assertNotNull(result);
        assertEquals(ShiftStatus.ACTIVE, result.getStatus());
    }

    @Test
    @DisplayName("UTC023 - addShiftToEvent() - Abnormal: User authenticated as EO; Event does not exist in repo")
    void addShiftToEvent_ThrowsNotFound_WhenEventNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.empty());

        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder().jobType("Bảo vệ").build();

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eoEventService.addShiftToEvent(userId, eventId, shiftDto));
        assertTrue(ex.getMessage().contains("Không tìm thấy sự kiện ID"));
    }


    // =========================================================================
    // updateShift()
    // =========================================================================

    @Test
    @DisplayName("UTC024 - updateShift() - Normal: User authenticated as EO; Shift exists with requiredWorkers=2, confirmedWorkers=1")
    void updateShift_Success_RecalculatesTotalsAndWorkerDiff() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(UUID.randomUUID()).eventOrganizer(mockEo)
                .totalRequiredWorkers(2).totalEstimatedCost(new BigDecimal("200000")).build();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Shift mockShift = Shift.builder()
                .shiftId(shiftId).event(mockEvent).requiredWorkers(2).confirmedWorkers(1)
                .totalCost(new BigDecimal("200000")).startTime(start).endTime(end).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> i.getArgument(0));

        UpdateShiftRequestDto dto = UpdateShiftRequestDto.builder()
                .jobType("Pha chế").startTime(start).endTime(end).requiredWorkers(4)
                .hourlyWage(BigDecimal.valueOf(50000)).allowance(BigDecimal.ZERO).build();

        ShiftResponseDto result = eoEventService.updateShift(userId, shiftId, dto);

        assertNotNull(result);
        assertEquals(4, result.getRequiredWorkers());
        assertEquals(3, result.getRemainingWorkers());
        assertEquals(0, new BigDecimal("400000").compareTo(result.getTotalCost()));
    }

    @Test
    @DisplayName("UTC025 - updateShift() - Abnormal: User authenticated as EO; Shift owned by another EO")
    void updateShift_ThrowsBadRequest_WhenShiftNotOwnedByEo() {
        UUID shiftId = UUID.randomUUID();
        Event otherEoEvent = Event.builder()
                .eventId(UUID.randomUUID()).eventOrganizer(EventOrganizer.builder().eoId(UUID.randomUUID()).build()).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(otherEoEvent).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));

        UpdateShiftRequestDto dto = UpdateShiftRequestDto.builder()
                .jobType("Bảo vệ").startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .requiredWorkers(1).hourlyWage(BigDecimal.valueOf(50000)).build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.updateShift(userId, shiftId, dto));
        assertTrue(ex.getMessage().contains("Bạn không sở hữu ca làm việc này"));
    }

    @Test
    @DisplayName("UTC026 - updateShift() - Boundary: User authenticated as EO; Shift has confirmedWorkers=4")
    void updateShift_Success_BoundaryRequiredWorkersLessThanConfirmed_ClampRemainingToZero() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Shift mockShift = Shift.builder()
                .shiftId(shiftId).event(mockEvent).requiredWorkers(5).confirmedWorkers(4)
                .totalCost(BigDecimal.valueOf(500000)).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> i.getArgument(0));

        UpdateShiftRequestDto dto = UpdateShiftRequestDto.builder()
                .jobType("PG").startTime(start).endTime(end).requiredWorkers(2).hourlyWage(BigDecimal.valueOf(50000)).build();

        ShiftResponseDto result = eoEventService.updateShift(userId, shiftId, dto);

        assertNotNull(result);
        assertEquals(2, result.getRequiredWorkers());
        assertEquals(0, result.getRemainingWorkers());
    }

    @Test
    @DisplayName("UTC027 - updateShift() - Abnormal: User authenticated as EO; Shift does not exist")
    void updateShift_ThrowsNotFound_WhenShiftNotFound() {
        UUID shiftId = UUID.randomUUID();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.empty());

        UpdateShiftRequestDto dto = UpdateShiftRequestDto.builder().jobType("Phục vụ").build();

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eoEventService.updateShift(userId, shiftId, dto));
        assertTrue(ex.getMessage().contains("Không tìm thấy ca làm việc ID"));
    }

    @Test
    @DisplayName("UTC028 - updateShift() - Abnormal: User authenticated as EO; Shift exists")
    void updateShift_ThrowsBadRequest_WhenEndTimeBeforeStartTime() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(mockEvent).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        UpdateShiftRequestDto dto = UpdateShiftRequestDto.builder()
                .startTime(start).endTime(start.minus(1, ChronoUnit.HOURS))
                .requiredWorkers(2).hourlyWage(BigDecimal.valueOf(50000)).build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.updateShift(userId, shiftId, dto));
        assertTrue(ex.getMessage().contains("Thời gian kết thúc ca làm việc phải sau thời gian bắt đầu"));
    }

    @Test
    @DisplayName("UTC029 - updateShift() - Normal: User authenticated as EO; Shift exists with requiredWorkers=5")
    void updateShift_Success_DecreasesWorkerCountAndEventTotals() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(UUID.randomUUID()).eventOrganizer(mockEo)
                .totalRequiredWorkers(10).totalEstimatedCost(new BigDecimal("1000000")).build();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Shift mockShift = Shift.builder()
                .shiftId(shiftId).event(mockEvent).requiredWorkers(5).confirmedWorkers(2)
                .totalCost(new BigDecimal("500000")).startTime(start).endTime(end).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> i.getArgument(0));

        UpdateShiftRequestDto dto = UpdateShiftRequestDto.builder()
                .jobType("PG").startTime(start).endTime(end).requiredWorkers(3)
                .hourlyWage(BigDecimal.valueOf(50000)).allowance(BigDecimal.ZERO).build();

        ShiftResponseDto result = eoEventService.updateShift(userId, shiftId, dto);

        assertNotNull(result);
        assertEquals(3, result.getRequiredWorkers());
        assertEquals(8, mockEvent.getTotalRequiredWorkers()); // 10 - 2 = 8
    }


    // =========================================================================
    // cancelShift()
    // =========================================================================

    @Test
    @DisplayName("UTC030 - cancelShift() - Normal: User authenticated as EO; Shift status=DRAFT exists")
    void cancelShift_Success_CancelsShiftAndAssignments() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(mockEvent).jobType("Bảo vệ").status(ShiftStatus.DRAFT).build();

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
    @DisplayName("UTC031 - cancelShift() - Abnormal: User authenticated as EO; Shift does not exist")
    void cancelShift_ThrowsNotFound_WhenShiftDoesNotExist() {
        UUID shiftId = UUID.randomUUID();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eoEventService.cancelShift(userId, shiftId, "Lý do"));
        assertTrue(ex.getMessage().contains("Không tìm thấy ca làm việc ID"));
    }

    @Test
    @DisplayName("UTC032 - cancelShift() - Boundary: User authenticated as EO; Shift status=ACTIVE with 1 Assignment")
    void cancelShift_Success_BoundaryNullReason_UsesDefaultReason() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(mockEvent).status(ShiftStatus.ACTIVE).build();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(UUID.randomUUID()).status(AssignmentStatus.PENDING).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(List.of(sa));

        ShiftResponseDto result = eoEventService.cancelShift(userId, shiftId, null);

        assertNotNull(result);
        assertEquals(ShiftStatus.CANCELLED, result.getStatus());
        assertEquals("Ban tổ chức hủy ca", sa.getCancellationReason());
    }

    @Test
    @DisplayName("UTC033 - cancelShift() - Normal: User authenticated as EO; Shift has 1 COMPLETED and 1 PENDING assignment")
    void cancelShift_Success_PreservesCompletedAssignments() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(mockEvent).status(ShiftStatus.ACTIVE).build();
        ShiftAssignment completedSa = ShiftAssignment.builder().assignmentId(UUID.randomUUID()).status(AssignmentStatus.COMPLETED).build();
        ShiftAssignment pendingSa = ShiftAssignment.builder().assignmentId(UUID.randomUUID()).status(AssignmentStatus.PENDING).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(List.of(completedSa, pendingSa));

        eoEventService.cancelShift(userId, shiftId, "Hủy ca");

        assertEquals(AssignmentStatus.COMPLETED, completedSa.getStatus());
        assertEquals(AssignmentStatus.CANCELLED, pendingSa.getStatus());
    }


    // =========================================================================
    // generateShiftQr()
    // =========================================================================

    @Test
    @DisplayName("UTC034 - generateShiftQr() - Normal: User authenticated as EO; Shift exists")
    void generateShiftQr_Success_ReturnsValidPrefixFormat() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(UUID.randomUUID()).name("Sự kiện QR").venueAddress("Hà Nội").eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(mockEvent).jobType("Lễ tân").build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));

        ShiftQrCodeResponseDto qr = eoEventService.generateShiftQr(userId, shiftId);

        assertNotNull(qr);
        assertEquals("CANI_CHECKIN:" + shiftId, qr.getCheckinQrContent());
        assertEquals("CANI_CHECKOUT:" + shiftId, qr.getCheckoutQrContent());
    }

    @Test
    @DisplayName("UTC035 - generateShiftQr() - Abnormal: User authenticated as EO; Shift owned by another EO")
    void generateShiftQr_ThrowsBadRequest_WhenShiftNotOwnedByEo() {
        UUID shiftId = UUID.randomUUID();
        Event otherEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(EventOrganizer.builder().eoId(UUID.randomUUID()).build()).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(otherEvent).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.generateShiftQr(userId, shiftId));
        assertTrue(ex.getMessage().contains("Bạn không sở hữu ca làm việc này"));
    }

    @Test
    @DisplayName("UTC036 - generateShiftQr() - Boundary: User authenticated as EO; Shift has null event but valid eoId via mock")
    void generateShiftQr_ThrowsBadRequest_WhenEventIsNull() {
        UUID shiftId = UUID.randomUUID();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(null).jobType("Lễ tân").build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.generateShiftQr(userId, shiftId));
        assertTrue(ex.getMessage().contains("Bạn không sở hữu ca làm việc này"));
    }


    // =========================================================================
    // getShiftAttendanceList()
    // =========================================================================

    @Test
    @DisplayName("UTC037 - getShiftAttendanceList() - Normal: User authenticated as EO; Shift has 1 completed Assignment")
    void getShiftAttendanceList_Success_ReturnsMappedAttendance() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(mockEvent).hourlyWage(BigDecimal.valueOf(50000)).build();
        UUID assignmentId = UUID.randomUUID();
        Worker mockWorker = Worker.builder()
                .workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).fullName("Nguyễn Văn A").phone("0987654321").build())
                .reliabilityScore(95).ratingAvg(BigDecimal.valueOf(4.8)).build();
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId).shift(mockShift).worker(mockWorker).status(AssignmentStatus.COMPLETED).eoConfirmed(true).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(List.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(paymentRepository.findByAssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(reviewRepository.findByAssignmentIdAndReviewerId(eq(assignmentId), any())).thenReturn(Optional.empty());

        List<ShiftAttendanceDto> list = eoEventService.getShiftAttendanceList(userId, shiftId);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("Nguyễn Văn A", list.get(0).getWorkerName());
        assertTrue(list.get(0).getEoConfirmed());
        assertEquals("NOT_DECLARED", list.get(0).getPaymentStatus());
    }

    @Test
    @DisplayName("UTC038 - getShiftAttendanceList() - Abnormal: User authenticated as EO; Shift does not exist")
    void getShiftAttendanceList_ThrowsNotFound_WhenShiftNotFound() {
        UUID shiftId = UUID.randomUUID();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eoEventService.getShiftAttendanceList(userId, shiftId));
        assertTrue(ex.getMessage().contains("Không tìm thấy ca làm việc ID"));
    }

    @Test
    @DisplayName("UTC039 - getShiftAttendanceList() - Normal: User authenticated as EO; Shift has assignment with AttendanceLog & Payment")
    void getShiftAttendanceList_Success_WithFullAttendanceLogAndPayment() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(mockEvent).build();
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(mockShift).status(AssignmentStatus.COMPLETED).build();

        AttendanceLog logEntity = AttendanceLog.builder()
                .checkinTime(Instant.now().minus(4, ChronoUnit.HOURS)).checkoutTime(Instant.now())
                .isOnTime(true).hoursWorked(BigDecimal.valueOf(4.0)).build();
        Payment payEntity = Payment.builder().status(PaymentStatus.CONFIRMED_FULL).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(List.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.of(logEntity));
        when(paymentRepository.findByAssignmentId(assignmentId)).thenReturn(Optional.of(payEntity));
        when(reviewRepository.findByAssignmentIdAndReviewerId(eq(assignmentId), any())).thenReturn(Optional.of(Review.builder().build()));

        List<ShiftAttendanceDto> list = eoEventService.getShiftAttendanceList(userId, shiftId);

        assertNotNull(list);
        assertEquals(BigDecimal.valueOf(4.0), list.get(0).getHoursWorked());
        assertTrue(list.get(0).getIsOnTime());
        assertEquals("CONFIRMED_FULL", list.get(0).getPaymentStatus());
        assertTrue(list.get(0).getHasReviewedWorker());
    }

    @Test
    @DisplayName("UTC040 - getShiftAttendanceList() - Normal: User authenticated as EO; Shift has checklist template & progress JSON object")
    void getShiftAttendanceList_Success_CalculatesChecklistSummaryObject() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        String tpl = "[{\"id\":\"c1\",\"title\":\"Task 1\"},{\"id\":\"c2\",\"title\":\"Task 2\"}]";
        Shift mockShift = Shift.builder().shiftId(shiftId).event(mockEvent).checklist(tpl).build();
        UUID assignmentId = UUID.randomUUID();
        String prog = "{\"c1\":{\"isCompleted\":true},\"c2\":{\"isCompleted\":false}}";
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(mockShift).checklistProgress(prog).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(List.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(paymentRepository.findByAssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(reviewRepository.findByAssignmentIdAndReviewerId(eq(assignmentId), any())).thenReturn(Optional.empty());

        List<ShiftAttendanceDto> list = eoEventService.getShiftAttendanceList(userId, shiftId);

        assertNotNull(list);
        assertEquals("1/2 việc", list.get(0).getChecklistProgress());
    }

    @Test
    @DisplayName("UTC041 - getShiftAttendanceList() - Boundary: User authenticated as EO; Shift checklist is null or blank")
    void getShiftAttendanceList_Success_BoundaryNullChecklist_ReturnsEmptyString() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(mockEvent).checklist(null).build();
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(mockShift).status(AssignmentStatus.COMPLETED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));
        when(shiftAssignmentRepository.findByShift_ShiftId(shiftId)).thenReturn(List.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(paymentRepository.findByAssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(reviewRepository.findByAssignmentIdAndReviewerId(eq(assignmentId), any())).thenReturn(Optional.empty());

        List<ShiftAttendanceDto> list = eoEventService.getShiftAttendanceList(userId, shiftId);

        assertNotNull(list);
        assertEquals("", list.get(0).getChecklistProgress());
    }


    // =========================================================================
    // verifyAttendance()
    // =========================================================================

    @Test
    @DisplayName("UTC042 - verifyAttendance() - Normal: User authenticated as EO; Assignment status=CONFIRMED; Worker totalShiftsCompleted=5")
    void verifyAttendance_Success_ConfirmsAndIncrementsWorkerCompletedShifts() {
        UUID assignmentId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).event(mockEvent).build();
        Worker mockWorker = Worker.builder()
                .workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).fullName("Worker 1").build())
                .totalShiftsCompleted(5).build();
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId).shift(mockShift).worker(mockWorker).status(AssignmentStatus.CONFIRMED).eoConfirmed(false).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class))).thenAnswer(i -> i.getArgument(0));

        ShiftAttendanceDto result = eoEventService.verifyAttendance(userId, assignmentId);

        assertNotNull(result);
        assertTrue(sa.getEoConfirmed());
        assertEquals(AssignmentStatus.COMPLETED, sa.getStatus());
        assertEquals(6, mockWorker.getTotalShiftsCompleted());
        verify(workerRepository, times(1)).save(mockWorker);
    }

    @Test
    @DisplayName("UTC043 - verifyAttendance() - Abnormal: User authenticated as EO; Assignment already eoConfirmed=true")
    void verifyAttendance_ThrowsBadRequest_WhenAlreadyConfirmed() {
        UUID assignmentId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).event(mockEvent).build();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(mockShift).eoConfirmed(true).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.verifyAttendance(userId, assignmentId));
        assertTrue(ex.getMessage().contains("Phân công việc này đã được duyệt hoàn thành trước đó"));
        verify(workerRepository, never()).save(any(Worker.class));
    }

    @Test
    @DisplayName("UTC044 - verifyAttendance() - Boundary: User authenticated as EO; Assignment status=COMPLETED (auto checkout); Worker totalShiftsCompleted=10")
    void verifyAttendance_Success_BoundaryAlreadyCompleted_DoesNotDoubleIncrementWorkerShifts() {
        UUID assignmentId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).event(mockEvent).build();
        Worker mockWorker = Worker.builder()
                .workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).fullName("Worker 1").build())
                .totalShiftsCompleted(10).build();
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId).shift(mockShift).worker(mockWorker).status(AssignmentStatus.COMPLETED).eoConfirmed(false).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class))).thenAnswer(i -> i.getArgument(0));

        ShiftAttendanceDto result = eoEventService.verifyAttendance(userId, assignmentId);

        assertNotNull(result);
        assertTrue(sa.getEoConfirmed());
        assertEquals(10, mockWorker.getTotalShiftsCompleted());
        verify(workerRepository, never()).save(mockWorker);
    }

    @Test
    @DisplayName("UTC045 - verifyAttendance() - Abnormal: User authenticated as EO; Assignment ID does not exist")
    void verifyAttendance_ThrowsNotFound_WhenAssignmentNotFound() {
        UUID assignmentId = UUID.randomUUID();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eoEventService.verifyAttendance(userId, assignmentId));
        assertTrue(ex.getMessage().contains("Không tìm thấy phân công việc ID"));
    }

    @Test
    @DisplayName("UTC046 - verifyAttendance() - Abnormal: User authenticated as EO; Assignment belongs to an event of another EO")
    void verifyAttendance_ThrowsBadRequest_WhenAssignmentNotOwnedByEo() {
        UUID assignmentId = UUID.randomUUID();
        Event otherEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(EventOrganizer.builder().eoId(UUID.randomUUID()).build()).build();
        Shift otherShift = Shift.builder().shiftId(UUID.randomUUID()).event(otherEvent).build();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(otherShift).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.verifyAttendance(userId, assignmentId));
        assertTrue(ex.getMessage().contains("Bạn không sở hữu sự kiện của ca làm việc này"));
    }


    // =========================================================================
    // rateWorker()
    // =========================================================================

    @Test
    @DisplayName("UTC047 - rateWorker() - Normal: User authenticated as EO; Worker ratingAvg=4.0, count=1, reliability=90")
    void rateWorker_Success_FiveStars_IncreasesReliabilityScore() {
        UUID assignmentId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).event(mockEvent).build();
        Worker mockWorker = Worker.builder()
                .workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).build())
                .ratingAvg(BigDecimal.valueOf(4.0)).ratingCount(1).reliabilityScore(90).build();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(mockShift).worker(mockWorker).status(AssignmentStatus.COMPLETED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(reviewRepository.findByAssignmentIdAndReviewerId(eq(assignmentId), eq(userId))).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        CreateEoReviewRequestDto request = CreateEoReviewRequestDto.builder()
                .rating(5).comment("Làm việc rất xuất sắc").criteriaWorkCompletion(5).criteriaPunctuality(5).criteriaProfessionalism(5).build();

        Review review = eoEventService.rateWorker(userId, assignmentId, request);

        assertNotNull(review);
        assertEquals(5, review.getRating());
        assertEquals(2, mockWorker.getRatingCount());
        assertEquals(0, new BigDecimal("4.50").compareTo(mockWorker.getRatingAvg()));
        assertEquals(91, mockWorker.getReliabilityScore());
        verify(workerRepository, times(1)).save(mockWorker);
    }

    @Test
    @DisplayName("UTC048 - rateWorker() - Abnormal: User authenticated as EO; EO already reviewed worker in assignment")
    void rateWorker_ThrowsBadRequest_WhenAlreadyReviewedInAssignment() {
        UUID assignmentId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).event(mockEvent).build();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(mockShift).status(AssignmentStatus.COMPLETED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(reviewRepository.findByAssignmentIdAndReviewerId(eq(assignmentId), eq(userId))).thenReturn(Optional.of(Review.builder().build()));

        CreateEoReviewRequestDto request = CreateEoReviewRequestDto.builder().rating(5).build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.rateWorker(userId, assignmentId, request));
        assertTrue(ex.getMessage().contains("Bạn đã gửi đánh giá cho Worker này"));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("UTC049 - rateWorker() - Boundary: User authenticated as EO; Worker reliabilityScore=1")
    void rateWorker_Success_BoundaryOneStar_DecreasesReliabilityScoreBoundedByZero() {
        UUID assignmentId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).event(mockEvent).build();
        Worker mockWorker = Worker.builder()
                .workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).build())
                .ratingAvg(BigDecimal.valueOf(3.0)).ratingCount(1).reliabilityScore(1).build();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(mockShift).worker(mockWorker).status(AssignmentStatus.COMPLETED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(reviewRepository.findByAssignmentIdAndReviewerId(eq(assignmentId), eq(userId))).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        CreateEoReviewRequestDto request = CreateEoReviewRequestDto.builder().rating(1).build();

        Review review = eoEventService.rateWorker(userId, assignmentId, request);

        assertNotNull(review);
        assertEquals(0, mockWorker.getReliabilityScore());
        verify(workerRepository, times(1)).save(mockWorker);
    }

    @Test
    @DisplayName("UTC050 - rateWorker() - Normal: User authenticated as EO; Worker reliabilityScore=100 (Max)")
    void rateWorker_Success_FourStars_CappedAtMaxReliability100() {
        UUID assignmentId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).event(mockEvent).build();
        Worker mockWorker = Worker.builder()
                .workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).build())
                .ratingAvg(BigDecimal.valueOf(4.5)).ratingCount(2).reliabilityScore(100).build();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(mockShift).worker(mockWorker).status(AssignmentStatus.COMPLETED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(reviewRepository.findByAssignmentIdAndReviewerId(eq(assignmentId), eq(userId))).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        CreateEoReviewRequestDto request = CreateEoReviewRequestDto.builder().rating(4).build();

        Review review = eoEventService.rateWorker(userId, assignmentId, request);

        assertNotNull(review);
        assertEquals(100, mockWorker.getReliabilityScore());
    }

    @Test
    @DisplayName("UTC051 - rateWorker() - Normal: User authenticated as EO; Worker reliabilityScore=80")
    void rateWorker_Success_ThreeStars_ReliabilityScoreUnchanged() {
        UUID assignmentId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).event(mockEvent).build();
        Worker mockWorker = Worker.builder()
                .workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).build())
                .ratingAvg(BigDecimal.valueOf(4.0)).ratingCount(1).reliabilityScore(80).build();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(mockShift).worker(mockWorker).status(AssignmentStatus.COMPLETED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(reviewRepository.findByAssignmentIdAndReviewerId(eq(assignmentId), eq(userId))).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        CreateEoReviewRequestDto request = CreateEoReviewRequestDto.builder().rating(3).build();

        Review review = eoEventService.rateWorker(userId, assignmentId, request);

        assertNotNull(review);
        assertEquals(80, mockWorker.getReliabilityScore());
    }

    @Test
    @DisplayName("UTC052 - rateWorker() - Abnormal: User authenticated as EO; Worker or User entity is null")
    void rateWorker_ThrowsBadRequest_WhenWorkerOrUserNull() {
        UUID assignmentId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).event(mockEvent).build();
        ShiftAssignment sa = ShiftAssignment.builder().assignmentId(assignmentId).shift(mockShift).worker(null).status(AssignmentStatus.COMPLETED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));

        CreateEoReviewRequestDto request = CreateEoReviewRequestDto.builder().rating(5).build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.rateWorker(userId, assignmentId, request));
        assertTrue(ex.getMessage().contains("Không tìm thấy thông tin Worker để đánh giá"));
    }


    // =========================================================================
    // publishEvent()
    // =========================================================================

    @Test
    @DisplayName("UTC053 - publishEvent() - Normal: User authenticated as EO; Event status=DRAFT with 1 draft Shift")
    void publishEvent_Success_ConsumesQuotaAndDispatchesShifts() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).name("Sự kiện cần kích hoạt").status(EventStatus.DRAFT).build();
        UUID shiftId = UUID.randomUUID();
        Shift mockShift = Shift.builder().shiftId(shiftId).status(ShiftStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(List.of(mockShift));

        EventResponseDto result = eoEventService.publishEvent(userId, eventId);

        assertNotNull(result);
        assertEquals(EventStatus.ACTIVE, result.getStatus());
        assertEquals(ShiftStatus.ACTIVE, mockShift.getStatus());
        verify(subscriptionService, times(1)).validateAndDeductEventQuota(mockEo);
        verify(matchingDispatchService, times(1)).dispatchForShift(shiftId);
    }

    @Test
    @DisplayName("UTC054 - publishEvent() - Abnormal: User authenticated as EO; Event does not belong to EO")
    void publishEvent_ThrowsNotFound_WhenEventNotOwnedByEo() {
        UUID eventId = UUID.randomUUID();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eoEventService.publishEvent(userId, eventId));
        assertTrue(ex.getMessage().contains("Không tìm thấy sự kiện ID"));
        verify(matchingDispatchService, never()).dispatchForShift(any());
    }

    @Test
    @DisplayName("UTC055 - publishEvent() - Boundary: User authenticated as EO; Event status=DRAFT with 0 Shifts")
    void publishEvent_Success_BoundaryZeroShifts_PublishesSafely() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(Collections.emptyList());

        EventResponseDto result = eoEventService.publishEvent(userId, eventId);

        assertNotNull(result);
        assertEquals(EventStatus.ACTIVE, result.getStatus());
        verify(matchingDispatchService, never()).dispatchForShift(any());
    }

    @Test
    @DisplayName("UTC056 - publishEvent() - Normal: User authenticated as EO; Subscription service throws quota exception")
    void publishEvent_Success_FaultTolerant_WhenSubscriptionThrowsException() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Quota service down")).when(subscriptionService).validateAndDeductEventQuota(any());

        EventResponseDto result = eoEventService.publishEvent(userId, eventId);

        assertNotNull(result);
        assertEquals(EventStatus.ACTIVE, result.getStatus());
    }

    @Test
    @DisplayName("UTC056b - publishEvent() - Abnormal: Subscription quota exceeded; Throws BadRequestException")
    void publishEvent_ThrowsBadRequest_WhenSubscriptionQuotaExceeded() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        doThrow(new BadRequestException("Bạn cần mua Gói dịch vụ để tạo sự kiện")).when(subscriptionService).validateAndDeductEventQuota(any());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.publishEvent(userId, eventId));
        assertTrue(ex.getMessage().contains("Bạn cần mua Gói dịch vụ để tạo sự kiện"));
        verify(matchingDispatchService, never()).dispatchForShift(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTC057 - publishEvent() - Normal: User authenticated as EO; Event has 3 draft shifts")
    void publishEvent_Success_DispatchesMultipleShifts() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.DRAFT).build();
        Shift s1 = Shift.builder().shiftId(UUID.randomUUID()).status(ShiftStatus.DRAFT).build();
        Shift s2 = Shift.builder().shiftId(UUID.randomUUID()).status(ShiftStatus.DRAFT).build();
        Shift s3 = Shift.builder().shiftId(UUID.randomUUID()).status(ShiftStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(List.of(s1, s2, s3));

        EventResponseDto result = eoEventService.publishEvent(userId, eventId);

        assertNotNull(result);
        assertEquals(ShiftStatus.ACTIVE, s1.getStatus());
        assertEquals(ShiftStatus.ACTIVE, s2.getStatus());
        assertEquals(ShiftStatus.ACTIVE, s3.getStatus());
        verify(matchingDispatchService, times(3)).dispatchForShift(any());
    }


    // =========================================================================
    // publishShift()
    // =========================================================================

    @Test
    @DisplayName("UTC058 - publishShift() - Normal: User authenticated as EO; Shift status=DRAFT")
    void publishShift_Success_SetsActiveAndDispatches() {
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(mockEvent).status(ShiftStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> i.getArgument(0));

        ShiftResponseDto result = eoEventService.publishShift(userId, shiftId);

        assertNotNull(result);
        assertEquals(ShiftStatus.ACTIVE, result.getStatus());
        verify(matchingDispatchService, times(1)).dispatchForShift(shiftId);
    }

    @Test
    @DisplayName("UTC059 - publishShift() - Abnormal: User authenticated as EO; Shift does not exist")
    void publishShift_ThrowsNotFound_WhenShiftNotFound() {
        UUID shiftId = UUID.randomUUID();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eoEventService.publishShift(userId, shiftId));
        assertTrue(ex.getMessage().contains("Không tìm thấy ca làm việc ID"));
        verify(matchingDispatchService, never()).dispatchForShift(any());
    }

    @Test
    @DisplayName("UTC060 - publishShift() - Abnormal: User authenticated as EO; Shift belongs to another EO")
    void publishShift_ThrowsBadRequest_WhenShiftNotOwnedByEo() {
        UUID shiftId = UUID.randomUUID();
        Event otherEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(EventOrganizer.builder().eoId(UUID.randomUUID()).build()).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).event(otherEvent).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.publishShift(userId, shiftId));
        assertTrue(ex.getMessage().contains("Bạn không sở hữu ca làm việc này"));
        verify(matchingDispatchService, never()).dispatchForShift(any());
    }


    // =========================================================================
    // getMyEvents()
    // =========================================================================

    @Test
    @DisplayName("UTC061 - getMyEvents() - Normal: User authenticated as EO; EO has 2 events with shifts")
    void getMyEvents_Success_ReturnsListOfEventsWithShifts() {
        UUID e1 = UUID.randomUUID();
        UUID e2 = UUID.randomUUID();
        Event event1 = Event.builder().eventId(e1).name("Event 1").build();
        Event event2 = Event.builder().eventId(e2).name("Event 2").build();
        Shift shift1 = Shift.builder().shiftId(UUID.randomUUID()).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eoId)).thenReturn(List.of(event1, event2));
        when(shiftRepository.findByEvent_EventId(e1)).thenReturn(List.of(shift1));
        when(shiftRepository.findByEvent_EventId(e2)).thenReturn(Collections.emptyList());

        List<EventResponseDto> list = eoEventService.getMyEvents(userId);

        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals(1, list.get(0).getShifts().size());
        assertEquals(0, list.get(1).getShifts().size());
    }

    @Test
    @DisplayName("UTC062 - getMyEvents() - Boundary: User authenticated as EO; EO has 0 events")
    void getMyEvents_Success_BoundaryZeroEvents_ReturnsEmptyList() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eoId)).thenReturn(Collections.emptyList());

        List<EventResponseDto> list = eoEventService.getMyEvents(userId);

        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("UTC063 - getMyEvents() - Normal: User authenticated as EO; Events ordered by createdAt desc")
    void getMyEvents_Success_PreservesCreatedAtDescOrder() {
        Event eNew = Event.builder().eventId(UUID.randomUUID()).name("Sự kiện Mới").build();
        Event eOld = Event.builder().eventId(UUID.randomUUID()).name("Sự kiện Cũ").build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eoId)).thenReturn(List.of(eNew, eOld));
        when(shiftRepository.findByEvent_EventId(any())).thenReturn(Collections.emptyList());

        List<EventResponseDto> list = eoEventService.getMyEvents(userId);

        assertNotNull(list);
        assertEquals("Sự kiện Mới", list.get(0).getName());
        assertEquals("Sự kiện Cũ", list.get(1).getName());
    }


    // =========================================================================
    // getEventDetail()
    // =========================================================================

    @Test
    @DisplayName("UTC064 - getEventDetail() - Normal: User authenticated as EO; Event exists with 2 child shifts")
    void getEventDetail_Success_ReturnsDetailWithShifts() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).name("Đại nhạc hội").build();
        Shift s1 = Shift.builder().shiftId(UUID.randomUUID()).build();
        Shift s2 = Shift.builder().shiftId(UUID.randomUUID()).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(List.of(s1, s2));

        EventResponseDto result = eoEventService.getEventDetail(userId, eventId);

        assertNotNull(result);
        assertEquals("Đại nhạc hội", result.getName());
        assertEquals(2, result.getShifts().size());
    }

    @Test
    @DisplayName("UTC065 - getEventDetail() - Abnormal: User authenticated as EO; Event does not exist or not owned by EO")
    void getEventDetail_ThrowsNotFound_WhenEventNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eoEventService.getEventDetail(userId, eventId));
        assertTrue(ex.getMessage().contains("Không tìm thấy sự kiện ID"));
    }

    @Test
    @DisplayName("UTC066 - getEventDetail() - Boundary: User authenticated as EO; Event exists with 0 shifts")
    void getEventDetail_Success_BoundaryZeroShifts_ReturnsEmptyShiftsList() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).name("Sự kiện chưa có ca").build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(Collections.emptyList());

        EventResponseDto result = eoEventService.getEventDetail(userId, eventId);

        assertNotNull(result);
        assertEquals("Sự kiện chưa có ca", result.getName());
        assertTrue(result.getShifts().isEmpty());
    }

}
