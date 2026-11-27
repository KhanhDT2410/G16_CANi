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

    @Test
    @DisplayName("UTC006b - createEvent() - Abnormal: Event name is keyboard mash / gibberish (e.g. sdfdsfsdfsd)")
    void createEvent_ThrowsBadRequest_WhenNameIsGibberish() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));

        CreateEventRequestDto dto = CreateEventRequestDto.builder()
                .name("sdfdsfsdfsd")
                .venueAddress("Hà Nội")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.createEvent(userId, dto));
        assertTrue(ex.getMessage().contains("không hợp lệ") || ex.getMessage().contains("vô nghĩa"));
    }

    @Test
    @DisplayName("UTC006c - createEvent() - Abnormal: Event description is keyboard mash / gibberish")
    void createEvent_ThrowsBadRequest_WhenDescriptionIsGibberish() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));

        CreateEventRequestDto dto = CreateEventRequestDto.builder()
                .name("Hội nghị Công nghệ 2026")
                .description("sdfdsfsdfsd")
                .venueAddress("Hà Nội")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.createEvent(userId, dto));
        assertTrue(ex.getMessage().contains("Mô tả") || ex.getMessage().contains("vô nghĩa"));
    }

    @Test
    @DisplayName("UTC006d - createEvent() - Abnormal: End time is before or equal to start time")
    void createEvent_ThrowsBadRequest_WhenEndTimeBeforeStartTime() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));

        Instant now = Instant.now();
        CreateEventRequestDto dto = CreateEventRequestDto.builder()
                .name("Hội nghị Công nghệ 2026")
                .venueAddress("Hà Nội")
                .startTime(now.plusSeconds(3600))
                .endTime(now)
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.createEvent(userId, dto));
        assertTrue(ex.getMessage().contains("sau"));
    }

    @Test
    @DisplayName("UTC006h - createEvent() - Abnormal: Start time in the past is rejected")
    void createEvent_ThrowsBadRequest_WhenStartTimeInPast() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));

        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        CreateEventRequestDto dto = CreateEventRequestDto.builder()
                .name("Hội nghị Quá Khứ 2026")
                .venueAddress("Hà Nội")
                .startTime(past)
                .endTime(past.plus(4, ChronoUnit.HOURS))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.createEvent(userId, dto));
        assertTrue(ex.getMessage().contains("quá khứ"));
    }

    @Test
    @DisplayName("UTC006e - createEvent() - Abnormal: Vietnamese Telex keyboard mash in event name (e.g. sdada ád ádasd ád da ád)")
    void createEvent_ThrowsBadRequest_WhenNameIsVietnameseTelexMash() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));

        CreateEventRequestDto dto = CreateEventRequestDto.builder()
                .name("sdada ád ádasd ád da ád")
                .venueAddress("Hà Nội")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.createEvent(userId, dto));
        assertTrue(ex.getMessage().contains("không hợp lệ") || ex.getMessage().contains("vô nghĩa"));
    }

    @Test
    @DisplayName("UTC006f - createEvent() - Abnormal: Vietnamese Telex keyboard mash in event description (e.g. ádas ád ád ád ádasd ád)")
    void createEvent_ThrowsBadRequest_WhenDescriptionIsVietnameseTelexMash() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));

        CreateEventRequestDto dto = CreateEventRequestDto.builder()
                .name("Hung event")
                .description("ádas ád ád ád ádasd ád")
                .venueAddress("Hà Nội")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.createEvent(userId, dto));
        assertTrue(ex.getMessage().contains("Mô tả") || ex.getMessage().contains("vô nghĩa"));
    }

    @Test
    @DisplayName("UTC006g - createEvent() - Normal: Venue address with acronyms and postal code (ĐHQG, 13131) must NOT be blocked")
    void createEvent_Success_WhenVenueAddressContainsAcronymsAndPostalCode() {
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event e = invocation.getArgument(0);
            e.setEventId(UUID.randomUUID());
            return e;
        });

        CreateEventRequestDto dto = CreateEventRequestDto.builder()
                .name("Hung event")
                .description("Vừa phải")
                .venueAddress("Ngõ 2 Cổng Phụ ĐHQG, Xã Hòa Lạc, Hà Nội, 13131, Việt Nam")
                .venueLat(21.012)
                .venueLng(105.525)
                .build();

        EventResponseDto result = eoEventService.createEvent(userId, dto);
        assertNotNull(result);
        assertEquals("Hung event", result.getName());
        assertEquals("Ngõ 2 Cổng Phụ ĐHQG, Xã Hòa Lạc, Hà Nội, 13131, Việt Nam", result.getVenueAddress());
    }

    @Test
    @DisplayName("UTC006h - isGibberishOrSpam() - Unit test comprehensive cases")
    void isGibberishOrSpam_ComprehensiveUnitTests() {
        // True spam / mash
        assertTrue(EoEventService.isGibberishOrSpam("sdada ád ádasd ád da ád"));
        assertTrue(EoEventService.isGibberishOrSpam("ádas ád ád ád ádasd ád"));
        assertTrue(EoEventService.isGibberishOrSpam("ád ád ád"));
        assertTrue(EoEventService.isGibberishOrSpam("test test test"));
        assertTrue(EoEventService.isGibberishOrSpam("asdasdasd asdasd"));
        assertTrue(EoEventService.isGibberishOrSpam("qwertyuiop"));
        assertTrue(EoEventService.isGibberishOrSpam("asdfasdfasdfasdfasdfasdfasdfasdf"));

        // Valid legitimate texts
        assertFalse(EoEventService.isGibberishOrSpam("Hung event"));
        assertFalse(EoEventService.isGibberishOrSpam("Vừa phải"));
        assertFalse(EoEventService.isGibberishOrSpam("Hội nghị Công nghệ FPT 2026"));
        assertFalse(EoEventService.isGibberishOrSpam("Gala Dinner tri ân khách hàng"));
        assertFalse(EoEventService.isGibberishOrSpam("Workshop hướng dẫn kỹ năng"));
        assertFalse(EoEventService.isGibberishOrSpam(
                "CES 2027 là một trong những triển lãm công nghệ lớn nhất thế giới, được tổ chức tại Las Vegas, Hoa Kỳ. " +
                "Sự kiện quy tụ hơn 148.000 người tham dự từ khắp thế giới, hơn 4.100 doanh nghiệp triển lãm và khoảng 1.200 startup công nghệ."
        ));
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
    @DisplayName("UTC015 - cancelEvent() - Boundary: User authenticated as EO; Throws BadRequestException when reason is blank")
    void cancelEvent_ThrowsBadRequest_WhenBlankReason() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.cancelEvent(userId, eventId, "   "));
        assertTrue(ex.getMessage().contains("lý do"));
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

        eoEventService.cancelEvent(userId, eventId, "Lý do bất khả kháng dịch bệnh");

        assertEquals("Lý do bất khả kháng dịch bệnh", sa.getCancellationReason());
        assertEquals(userId, sa.getCancelledBy());
    }

    @Test
    @DisplayName("UTC017b - cancelEvent() - Abnormal: Throws BadRequestException when reason is null or too short")
    void cancelEvent_ThrowsBadRequest_WhenReasonNullOrTooShort() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.ACTIVE).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        assertThrows(BadRequestException.class, () -> eoEventService.cancelEvent(userId, eventId, null));
        assertThrows(BadRequestException.class, () -> eoEventService.cancelEvent(userId, eventId, "   "));
        assertThrows(BadRequestException.class, () -> eoEventService.cancelEvent(userId, eventId, "ngắn"));
    }

    @Test
    @DisplayName("UTC017c - cancelEvent() - Abnormal: Throws BadRequestException when event is COMPLETED")
    void cancelEvent_ThrowsBadRequest_WhenEventCompleted() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.COMPLETED).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.cancelEvent(userId, eventId, "Lý do hủy sự kiện hợp lệ"));
        assertTrue(ex.getMessage().contains("hoàn thành"));
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
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));
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
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Phục vụ").startTime(start).endTime(start.minus(1, ChronoUnit.HOURS))
                .requiredWorkers(2).hourlyWage(BigDecimal.valueOf(50000)).build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.addShiftToEvent(userId, eventId, shiftDto));
        assertTrue(ex.getMessage().contains("Thời gian kết thúc ca làm việc phải sau thời gian bắt đầu"));
    }

    @Test
    @DisplayName("UTC019b - addShiftToEvent() - Abnormal: Shift start time in the past is rejected")
    void addShiftToEvent_ThrowsBadRequest_WhenStartTimeInPast() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).build();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        Instant past = Instant.now().minus(3, ChronoUnit.HOURS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Lễ tân").startTime(past).endTime(past.plus(2, ChronoUnit.HOURS))
                .requiredWorkers(2).hourlyWage(BigDecimal.valueOf(50000)).build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.addShiftToEvent(userId, eventId, shiftDto));
        assertTrue(ex.getMessage().contains("quá khứ"));
    }

    @Test
    @DisplayName("UTC019c - addShiftToEvent() - Abnormal: Arrival time after shift startTime is rejected")
    void addShiftToEvent_ThrowsBadRequest_WhenArrivalTimeAfterStartTime() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).build();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Lễ tân").startTime(start).endTime(start.plus(4, ChronoUnit.HOURS))
                .arrivalTime(start.plus(15, ChronoUnit.MINUTES))
                .requiredWorkers(2).hourlyWage(BigDecimal.valueOf(50000)).build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.addShiftToEvent(userId, eventId, shiftDto));
        assertTrue(ex.getMessage().contains("trước hoặc bằng"));
    }

    @Test
    @DisplayName("UTC019d - addShiftToEvent() - Normal: Arrival time 20 minutes before shift startTime is saved")
    void addShiftToEvent_Success_WhenArrivalTimeBeforeStartTime() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).build();
        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> {
            Shift s = i.getArgument(0);
            s.setShiftId(UUID.randomUUID());
            return s;
        });

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant arrival = start.minus(20, ChronoUnit.MINUTES);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Lễ tân").startTime(start).endTime(start.plus(4, ChronoUnit.HOURS))
                .arrivalTime(arrival)
                .requiredWorkers(2).hourlyWage(BigDecimal.valueOf(50000)).build();

        ShiftResponseDto result = eoEventService.addShiftToEvent(userId, eventId, shiftDto);
        assertEquals(arrival, result.getArrivalTime());
    }

    @Test
    @DisplayName("UTC020 - addShiftToEvent() - Boundary: User authenticated as EO; Event exists")
    void addShiftToEvent_Success_BoundaryDurationUnder30Min_AppliesMinBillableHours() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));
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
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));
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
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));
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
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.empty());

        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder().jobType("Bảo vệ").build();

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eoEventService.addShiftToEvent(userId, eventId, shiftDto));
        assertTrue(ex.getMessage().contains("Không tìm thấy sự kiện ID"));
    }

    @Test
    @DisplayName("UTC023b - addShiftToEvent() - Normal: EO có thể nhập mức lương thấp hơn gợi ý miễn đạt từ 25.000 VNĐ/giờ")
    void addShiftToEvent_Success_AllowsCustomHourlyWageBelowSuggestedFloor() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> {
            Shift s = i.getArgument(0);
            s.setShiftId(UUID.randomUUID());
            return s;
        });

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Phục vụ").startTime(start).endTime(start.plus(2, ChronoUnit.HOURS))
                .requiredWorkers(1).hourlyWage(BigDecimal.valueOf(28000)).build();

        ShiftResponseDto result = eoEventService.addShiftToEvent(userId, eventId, shiftDto);

        assertNotNull(result);
        assertEquals(0, BigDecimal.valueOf(28000).compareTo(result.getHourlyWage()));
    }

    @Test
    @DisplayName("UTC023c - addShiftToEvent() - Abnormal (EO-004): Chặn lưu khi mức thù lao thấp hơn mức tối thiểu vùng theo Nghị định 293/2025/NĐ-CP (Vùng I: 25.500 VNĐ/giờ)")
    void addShiftToEvent_ThrowsBadRequest_WhenHourlyWageBelowStatutoryFloor25k() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Phục vụ").startTime(start).endTime(start.plus(2, ChronoUnit.HOURS))
                .requiredWorkers(1).hourlyWage(BigDecimal.valueOf(24000)).build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.addShiftToEvent(userId, eventId, shiftDto));
        assertEquals("Mức thù lao không được thấp hơn mức tối thiểu quy định của Vùng I (TP. Hồ Chí Minh): 25.500 VNĐ/giờ (theo Nghị định 293/2025/NĐ-CP)", ex.getMessage());
    }

    @Test
    @DisplayName("UTC023e - addShiftToEvent() - Normal: Cho phép mức thù lao 23.000 VNĐ/giờ tại Đà Nẵng (Vùng II: sàn tối thiểu 22.700 VNĐ/giờ theo Nghị định 293)")
    void addShiftToEvent_Success_AllowsDaNangHourlyWageAboveRegion2StatutoryFloor() {
        UUID eventId = UUID.randomUUID();
        Event mockDaNangEvent = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .status(EventStatus.DRAFT)
                .venueAddress("Trung tâm Hội chợ Triển lãm Đà Nẵng, Cách Mạng Tháng 8, Cẩm Lệ, Đà Nẵng")
                .venueLat(16.0321)
                .venueLng(108.2154)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockDaNangEvent));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> {
            Shift s = i.getArgument(0);
            s.setShiftId(UUID.randomUUID());
            return s;
        });

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Phục vụ")
                .startTime(start)
                .endTime(start.plus(3, ChronoUnit.HOURS))
                .requiredWorkers(2)
                .hourlyWage(BigDecimal.valueOf(23000))
                .build();

        ShiftResponseDto result = eoEventService.addShiftToEvent(userId, eventId, shiftDto);

        assertNotNull(result);
        assertEquals(0, BigDecimal.valueOf(23000).compareTo(result.getHourlyWage()));
    }

    @Test
    @DisplayName("UTC023f - addShiftToEvent() - Abnormal: Chặn lưu khi mức thù lao tại Đà Nẵng thấp hơn sàn Vùng II (22.700 VNĐ/giờ)")
    void addShiftToEvent_ThrowsBadRequest_WhenDaNangHourlyWageBelowRegion2StatutoryFloor() {
        UUID eventId = UUID.randomUUID();
        Event mockDaNangEvent = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .status(EventStatus.DRAFT)
                .venueAddress("Đà Nẵng, Việt Nam")
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockDaNangEvent));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Phục vụ")
                .startTime(start)
                .endTime(start.plus(3, ChronoUnit.HOURS))
                .requiredWorkers(2)
                .hourlyWage(BigDecimal.valueOf(22000))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.addShiftToEvent(userId, eventId, shiftDto));
        assertEquals("Mức thù lao không được thấp hơn mức tối thiểu quy định của Vùng II (Đà Nẵng): 22.700 VNĐ/giờ (theo Nghị định 293/2025/NĐ-CP)", ex.getMessage());
    }

    @Test
    @DisplayName("UTC023g - addShiftToEvent() - Abnormal: Chặn lưu khi mức thù lao <= 0")
    void addShiftToEvent_ThrowsBadRequest_WhenHourlyWageZeroOrNegative() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Phục vụ")
                .startTime(start)
                .endTime(start.plus(3, ChronoUnit.HOURS))
                .requiredWorkers(2)
                .hourlyWage(BigDecimal.ZERO)
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.addShiftToEvent(userId, eventId, shiftDto));
        assertEquals("Mức thù lao theo giờ phải lớn hơn 0", ex.getMessage());
    }

    @Test
    @DisplayName("UTC023h - addShiftToEvent() - Normal: Cho phép mức thù lao 20.500 VNĐ/giờ tại Cà Mau (Vùng III: sàn tối thiểu 20.000 VNĐ/giờ)")
    void addShiftToEvent_Success_AllowsRegion3HourlyWageAbove20k() {
        UUID eventId = UUID.randomUUID();
        Event mockRegion3Event = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .status(EventStatus.DRAFT)
                .venueAddress("Quảng trường Hùng Vương, TP. Cà Mau")
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockRegion3Event));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> {
            Shift s = i.getArgument(0);
            s.setShiftId(UUID.randomUUID());
            return s;
        });

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Phục vụ")
                .startTime(start)
                .endTime(start.plus(3, ChronoUnit.HOURS))
                .requiredWorkers(2)
                .hourlyWage(BigDecimal.valueOf(20500))
                .build();

        ShiftResponseDto result = eoEventService.addShiftToEvent(userId, eventId, shiftDto);
        assertNotNull(result);
        assertEquals(0, BigDecimal.valueOf(20500).compareTo(result.getHourlyWage()));
    }

    @Test
    @DisplayName("UTC023i - addShiftToEvent() - Abnormal: Chặn lưu khi mức thù lao tại Hà Giang thấp hơn sàn Vùng IV (17.800 VNĐ/giờ)")
    void addShiftToEvent_ThrowsBadRequest_WhenRegion4HourlyWageBelow17800() {
        UUID eventId = UUID.randomUUID();
        Event mockRegion4Event = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .status(EventStatus.DRAFT)
                .venueAddress("TP. Hà Giang, Hà Giang")
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockRegion4Event));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("Phục vụ")
                .startTime(start)
                .endTime(start.plus(3, ChronoUnit.HOURS))
                .requiredWorkers(2)
                .hourlyWage(BigDecimal.valueOf(17000))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.addShiftToEvent(userId, eventId, shiftDto));
        assertEquals("Mức thù lao không được thấp hơn mức tối thiểu quy định của Vùng IV: 17.800 VNĐ/giờ (theo Nghị định 293/2025/NĐ-CP)", ex.getMessage());
    }

    @Test
    @DisplayName("UTC023d - addShiftToEvent() - Abnormal (EO-005): Khóa sự kiện & chặn nhân bản Shift trùng vị trí cùng khung giờ")
    void addShiftToEvent_ThrowsBadRequest_WhenDuplicateShiftAlreadyExists() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.DRAFT).build();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant end = start.plus(4, ChronoUnit.HOURS);

        Shift existingShift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(mockEvent)
                .jobType("Lễ tân / Check-in")
                .startTime(start)
                .endTime(end)
                .status(ShiftStatus.DRAFT)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.findByEvent_EventIdAndStatusNot(eventId, ShiftStatus.CANCELLED)).thenReturn(List.of(existingShift));

        CreateShiftRequestDto shiftDto = CreateShiftRequestDto.builder()
                .jobType("  lễ tân / check-in  ").startTime(start).endTime(end)
                .requiredWorkers(2).hourlyWage(BigDecimal.valueOf(50000)).build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.addShiftToEvent(userId, eventId, shiftDto));
        assertTrue(ex.getMessage().contains("Đã tồn tại ca làm việc"));
        verify(eventRepository, times(1)).findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId);
        verify(shiftRepository, never()).save(any(Shift.class));
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
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.of(mockShift));
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
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.of(mockShift));

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
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.of(mockShift));
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
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.empty());

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
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.of(mockShift));

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
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.of(mockShift));
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
        assertTrue(qr.getCheckinQrContent().startsWith("CANI_CHECKIN:" + shiftId));
        assertTrue(qr.getCheckoutQrContent().startsWith("CANI_CHECKOUT:" + shiftId));
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
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId))
                .thenReturn(Optional.of(AttendanceLog.builder()
                        .checkinTime(Instant.now().minus(4, ChronoUnit.HOURS))
                        .checkoutTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                        .hoursWorked(BigDecimal.valueOf(4))
                        .build()));
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
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId))
                .thenReturn(Optional.of(AttendanceLog.builder()
                        .checkinTime(Instant.now().minus(4, ChronoUnit.HOURS))
                        .checkoutTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                        .hoursWorked(BigDecimal.valueOf(4))
                        .build()));
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

    @Test
    @DisplayName("UTC046b - verifyAttendance() - Data Integrity: Does not overwrite agreedAmount if payment is already CONFIRMED")
    void verifyAttendance_PreservesPaymentAmount_WhenAlreadyConfirmed() {
        UUID assignmentId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).hourlyWage(BigDecimal.valueOf(50000)).event(mockEvent).build();
        Worker mockWorker = Worker.builder()
                .workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).fullName("Worker 1").build())
                .totalShiftsCompleted(5).build();
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId).shift(mockShift).worker(mockWorker).status(AssignmentStatus.CONFIRMED).eoConfirmed(false).build();

        Payment declaredPayment = Payment.builder()
                .assignmentId(assignmentId)
                .status(com.cani.entity.enums.PaymentStatus.CONFIRMED_FULL)
                .agreedAmount(BigDecimal.valueOf(300000))
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId))
                .thenReturn(Optional.of(AttendanceLog.builder()
                        .checkinTime(Instant.now().minus(4, ChronoUnit.HOURS))
                        .checkoutTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                        .hoursWorked(BigDecimal.valueOf(4))
                        .build()));
        when(paymentRepository.findByAssignmentId(assignmentId)).thenReturn(Optional.of(declaredPayment));
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class))).thenAnswer(i -> i.getArgument(0));

        eoEventService.verifyAttendance(userId, assignmentId);

        assertEquals(BigDecimal.valueOf(300000), declaredPayment.getAgreedAmount());
        verify(paymentRepository, never()).save(declaredPayment);
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
    @DisplayName("UTC055 - publishEvent() - Abnormal: User authenticated as EO; Event status=DRAFT with 0 Shifts")
    void publishEvent_ThrowsBadRequest_WhenZeroShifts() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(Collections.emptyList());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.publishEvent(userId, eventId));
        assertTrue(ex.getMessage().contains("chưa có ca làm việc"));
        verify(subscriptionService, never()).validateAndDeductEventQuota(any());
        verify(matchingDispatchService, never()).dispatchForShift(any());
    }

    @Test
    @DisplayName("UTC055b - publishEvent() - Abnormal: Event endTime already passed")
    void publishEvent_ThrowsBadRequest_WhenEventEndTimeInPast() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .status(EventStatus.DRAFT)
                .endTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.publishEvent(userId, eventId));
        assertTrue(ex.getMessage().contains("đã trôi qua"));
        verify(subscriptionService, never()).validateAndDeductEventQuota(any());
    }

    @Test
    @DisplayName("UTC056 - publishEvent() - Normal: User authenticated as EO; Subscription service throws quota exception")
    void publishEvent_Success_FaultTolerant_WhenSubscriptionThrowsException() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).status(EventStatus.DRAFT).build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).status(ShiftStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(List.of(mockShift));
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
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).status(ShiftStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(List.of(mockShift));
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
        Shift shift1 = Shift.builder().shiftId(UUID.randomUUID()).event(event1).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eoId)).thenReturn(List.of(event1, event2));
        when(shiftRepository.findByEvent_EventIdIn(anyCollection())).thenReturn(List.of(shift1));

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
        when(shiftRepository.findByEvent_EventIdIn(anyCollection())).thenReturn(Collections.emptyList());

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

    // =========================================================================
    // deleteEvent()
    // =========================================================================

    @Test
    @DisplayName("UTC067 - deleteEvent() - Normal: Successfully soft-deletes DRAFT event")
    void deleteEvent_Success_WhenEventStatusIsDraft() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .status(EventStatus.DRAFT)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> eoEventService.deleteEvent(userId, eventId));
        assertNotNull(mockEvent.getDeletedAt());
        verify(eventRepository).save(mockEvent);
    }

    @Test
    @DisplayName("UTC068 - deleteEvent() - Abnormal: Throws BadRequestException when deleting ACTIVE event")
    void deleteEvent_ThrowsBadRequest_WhenEventStatusIsNotDraft() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .status(EventStatus.ACTIVE)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.deleteEvent(userId, eventId));
        assertTrue(ex.getMessage().contains("Bản nháp (DRAFT)"));
        assertNull(mockEvent.getDeletedAt());
        verify(eventRepository, never()).save(any(Event.class));
    }

    // =========================================================================
    // addBulkShifts() & triggerEmergencyDispatch()
    // =========================================================================

    @Test
    @DisplayName("UTC069 - addBulkShifts() - Normal: Bulk create multiple time slots for DRAFT event")
    void addBulkShifts_Success_CreatesMultipleShifts() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .name("Sự kiện tạo ca hàng loạt")
                .status(EventStatus.DRAFT)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .build();

        CreateBulkShiftsRequestDto.TimeSlotItem slot1 = new CreateBulkShiftsRequestDto.TimeSlotItem();
        slot1.setStartTime(mockEvent.getStartTime().plus(1, ChronoUnit.HOURS));
        slot1.setEndTime(mockEvent.getStartTime().plus(5, ChronoUnit.HOURS));

        CreateBulkShiftsRequestDto dto = new CreateBulkShiftsRequestDto();
        dto.setJobType("Phục vụ bàn");
        dto.setRequiredWorkers(3);
        dto.setHourlyWage(BigDecimal.valueOf(50000));
        dto.setAllowance(BigDecimal.ZERO);
        dto.setTimeSlots(List.of(slot1));

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> {
            Shift s = i.getArgument(0);
            s.setShiftId(UUID.randomUUID());
            return s;
        });

        List<ShiftResponseDto> result = eoEventService.addBulkShifts(userId, eventId, dto);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Phục vụ bàn", result.get(0).getJobType());
    }

    @Test
    @DisplayName("UTC070 - triggerEmergencyDispatch() - Normal: Triggers emergency dispatch when shift is ACTIVE")
    void triggerEmergencyDispatch_Success_DelegatesToMatchingDispatch() {
        UUID shiftId = UUID.randomUUID();
        Shift mockShift = Shift.builder()
                .shiftId(shiftId)
                .status(ShiftStatus.ACTIVE)
                .requiredWorkers(2)
                .confirmedWorkers(0)
                .event(Event.builder().eventId(UUID.randomUUID()).status(EventStatus.ACTIVE).mode(EventMode.MATCHING).eventOrganizer(mockEo).build())
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));
        when(matchingDispatchService.triggerEmergencyDispatch(shiftId, 30.0)).thenReturn(5);

        int count = eoEventService.triggerEmergencyDispatch(userId, shiftId, 30.0);

        assertEquals(5, count);
        verify(matchingDispatchService).triggerEmergencyDispatch(shiftId, 30.0);
    }

    @Test
    @DisplayName("UTC071 - publishEvent() - INVITE mode: Không trừ quota và không kích hoạt Matching")
    void publishEvent_InviteMode_ActivatesWithoutQuotaOrMatching() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .name("Sự kiện Mời Riêng")
                .mode(EventMode.INVITE)
                .status(EventStatus.DRAFT)
                .build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).event(mockEvent).status(ShiftStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRepository.findByEvent_EventId(eventId)).thenReturn(List.of(mockShift));

        EventResponseDto result = eoEventService.publishEvent(userId, eventId);

        assertEquals(EventStatus.ACTIVE, result.getStatus());
        assertEquals(ShiftStatus.ACTIVE, mockShift.getStatus());
        verify(subscriptionService, never()).validateAndDeductEventQuota(any());
        verify(matchingDispatchService, never()).dispatchForShift(any());
    }

    @Test
    @DisplayName("UTC072 - publishShift(), addShiftToEvent(), addBulkShifts() - INVITE mode: Không gọi Matching")
    void shiftOperations_InviteMode_NeverInvokeMatching() {
        UUID eventId = UUID.randomUUID();
        Event inviteEvent = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .name("Sự kiện Nội Bộ")
                .mode(EventMode.INVITE)
                .status(EventStatus.ACTIVE)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .build();
        UUID draftShiftId = UUID.randomUUID();
        Shift draftShift = Shift.builder().shiftId(draftShiftId).event(inviteEvent).status(ShiftStatus.DRAFT).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(draftShiftId)).thenReturn(Optional.of(draftShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> {
            Shift s = i.getArgument(0);
            if (s.getShiftId() == null) s.setShiftId(UUID.randomUUID());
            return s;
        });
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(inviteEvent));

        ShiftResponseDto published = eoEventService.publishShift(userId, draftShiftId);
        assertEquals(ShiftStatus.ACTIVE, published.getStatus());

        Instant start = inviteEvent.getStartTime().plus(1, ChronoUnit.HOURS);
        CreateShiftRequestDto singleDto = CreateShiftRequestDto.builder()
                .jobType("Lễ tân")
                .startTime(start)
                .endTime(start.plus(3, ChronoUnit.HOURS))
                .requiredWorkers(2)
                .hourlyWage(BigDecimal.valueOf(50000))
                .build();
        eoEventService.addShiftToEvent(userId, eventId, singleDto);

        CreateBulkShiftsRequestDto.TimeSlotItem slot = new CreateBulkShiftsRequestDto.TimeSlotItem();
        slot.setStartTime(start);
        slot.setEndTime(start.plus(3, ChronoUnit.HOURS));
        CreateBulkShiftsRequestDto bulkDto = new CreateBulkShiftsRequestDto();
        bulkDto.setJobType("Phục vụ");
        bulkDto.setRequiredWorkers(2);
        bulkDto.setHourlyWage(BigDecimal.valueOf(50000));
        bulkDto.setTimeSlots(List.of(slot));
        eoEventService.addBulkShifts(userId, eventId, bulkDto);

        verify(matchingDispatchService, never()).dispatchForShift(any());
    }

    @Test
    @DisplayName("UTC073 - triggerEmergencyDispatch() - INVITE mode: Chặn hoàn toàn với BadRequestException")
    void triggerEmergencyDispatch_InviteMode_ThrowsBadRequest() {
        UUID shiftId = UUID.randomUUID();
        Event inviteEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).mode(EventMode.INVITE).build();
        Shift mockShift = Shift.builder().shiftId(shiftId).status(ShiftStatus.ACTIVE).event(inviteEvent).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(mockShift));

        assertThrows(BadRequestException.class, () -> eoEventService.triggerEmergencyDispatch(userId, shiftId, 20.0));
        verify(matchingDispatchService, never()).triggerEmergencyDispatch(any(), any());
    }

    @Test
    @DisplayName("UTC074 - triggerEmergencyDispatch() - Ca đã đủ người ngay trước khi bấm -> từ chối")
    void triggerEmergencyDispatch_FullShift_ThrowsBadRequest() {
        UUID shiftId = UUID.randomUUID();
        Event activeEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).mode(EventMode.MATCHING).status(EventStatus.ACTIVE).build();
        Shift fullShift = Shift.builder().shiftId(shiftId).status(ShiftStatus.ACTIVE).requiredWorkers(4).confirmedWorkers(4).event(activeEvent).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(fullShift));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.triggerEmergencyDispatch(userId, shiftId, 25.0));
        assertTrue(ex.getMessage().contains("đủ nhân sự"));
        verify(matchingDispatchService, never()).triggerEmergencyDispatch(any(), any());
    }

    @Test
    @DisplayName("UTC075 - triggerEmergencyDispatch() - Ca IN_PROGRESS còn >= 30 phút cho phép bật thủ công; còn < 30 phút hoặc đã kết thúc bị chặn")
    void triggerEmergencyDispatch_InProgressOrEndedShift_ChecksRemainingTime() {
        UUID shiftId = UUID.randomUUID();
        Event ongoingEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).mode(EventMode.MATCHING).status(EventStatus.ONGOING).build();
        Shift inProgressEnoughTime = Shift.builder()
                .shiftId(shiftId)
                .status(ShiftStatus.ACTIVE) // DB chưa kịp chuyển sang IN_PROGRESS nhưng startTime đã qua và còn 3 giờ
                .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(3, ChronoUnit.HOURS))
                .requiredWorkers(4)
                .confirmedWorkers(2)
                .emergency(false)
                .event(ongoingEvent)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.of(inProgressEnoughTime));
        when(matchingDispatchService.triggerEmergencyDispatch(shiftId, 25.0)).thenReturn(2);

        int dispatched = eoEventService.triggerEmergencyDispatch(userId, shiftId, 25.0);
        assertEquals(2, dispatched);
        assertTrue(Boolean.TRUE.equals(inProgressEnoughTime.getEmergency()));
        verify(matchingDispatchService).triggerEmergencyDispatch(shiftId, 25.0);

        UUID shortInProgressId = UUID.randomUUID();
        Shift shortInProgressShift = Shift.builder()
                .shiftId(shortInProgressId)
                .status(ShiftStatus.IN_PROGRESS)
                .startTime(Instant.now().minus(2, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(15, ChronoUnit.MINUTES))
                .requiredWorkers(4)
                .confirmedWorkers(2)
                .emergency(false)
                .event(ongoingEvent)
                .build();
        when(shiftRepository.findByIdForUpdate(shortInProgressId)).thenReturn(Optional.of(shortInProgressShift));

        BadRequestException exShort = assertThrows(BadRequestException.class,
                () -> eoEventService.triggerEmergencyDispatch(userId, shortInProgressId, 25.0));
        assertTrue(exShort.getMessage().contains("30 phút"));

        UUID endedShiftId = UUID.randomUUID();
        Shift endedShift = Shift.builder()
                .shiftId(endedShiftId)
                .status(ShiftStatus.IN_PROGRESS)
                .startTime(Instant.now().minus(4, ChronoUnit.HOURS))
                .endTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                .requiredWorkers(4)
                .confirmedWorkers(2)
                .event(ongoingEvent)
                .build();
        when(shiftRepository.findByIdForUpdate(endedShiftId)).thenReturn(Optional.of(endedShift));

        BadRequestException exEnded = assertThrows(BadRequestException.class,
                () -> eoEventService.triggerEmergencyDispatch(userId, endedShiftId, 25.0));
        assertTrue(exEnded.getMessage().contains("kết thúc"));
        verify(matchingDispatchService, times(1)).triggerEmergencyDispatch(any(), any());
    }

    @Test
    @DisplayName("UTC076 - triggerEmergencyDispatch() - Bấm lần 2 hoặc đã bật Emergency -> chặn chống điều phối trùng")
    void triggerEmergencyDispatch_AlreadyEmergencyEnabled_ThrowsBadRequest() {
        UUID shiftId = UUID.randomUUID();
        Event activeEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).mode(EventMode.MATCHING).status(EventStatus.ACTIVE).build();
        Shift alreadyEmergency = Shift.builder()
                .shiftId(shiftId)
                .status(ShiftStatus.ACTIVE)
                .startTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(5, ChronoUnit.HOURS))
                .requiredWorkers(4)
                .confirmedWorkers(1)
                .emergency(true)
                .event(activeEvent)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(alreadyEmergency));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> eoEventService.triggerEmergencyDispatch(userId, shiftId, 25.0));
        assertTrue(ex.getMessage().contains("đã được bật điều phối khẩn cấp"));
        verify(matchingDispatchService, never()).triggerEmergencyDispatch(any(), any());
    }

    @Test
    @DisplayName("UTC077 - triggerEmergencyDispatchWithDetails() - Không có ứng viên phù hợp -> vẫn bật emergency & trả hasCandidates=false")
    void triggerEmergencyDispatchWithDetails_ZeroCandidates_ReturnsHasCandidatesFalse() {
        UUID shiftId = UUID.randomUUID();
        Event activeEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).mode(EventMode.MATCHING).status(EventStatus.ACTIVE).build();
        Shift shift = Shift.builder()
                .shiftId(shiftId)
                .status(ShiftStatus.ACTIVE)
                .startTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(5, ChronoUnit.HOURS))
                .requiredWorkers(4)
                .confirmedWorkers(2)
                .emergency(false)
                .event(activeEvent)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(matchingDispatchService.triggerEmergencyDispatch(shiftId, 25.0)).thenReturn(0);

        Map<String, Object> result = eoEventService.triggerEmergencyDispatchWithDetails(userId, shiftId, null);

        assertEquals(true, result.get("emergency"));
        assertEquals(0, result.get("dispatchedCount"));
        assertEquals(false, result.get("hasCandidates"));
        assertEquals(25.0, result.get("effectiveRadiusKm"));
        assertTrue(Boolean.TRUE.equals(shift.getEmergency()));
    }

    @Test
    @DisplayName("Skill V3 - addShiftToEvent(): Validate active taxonomy & lưu ShiftSkillRequirement chuẩn hóa")
    void addShiftToEvent_SkillV3_ValidatesActiveTaxonomyAndSavesRequirements() {
        SkillRepository skillRepo = mock(SkillRepository.class);
        ShiftSkillRequirementRepository shiftSkillReqRepo = mock(ShiftSkillRequirementRepository.class);
        ReflectionTestUtils.setField(eoEventService, "skillRepository", skillRepo);
        ReflectionTestUtils.setField(eoEventService, "shiftSkillRequirementRepository", shiftSkillReqRepo);

        UUID eventId = UUID.randomUUID();
        UUID mainSkillId = UUID.randomUUID();
        UUID prefSkillId = UUID.randomUUID();
        Skill mainSkill = Skill.builder().skillId(mainSkillId).name("Phục vụ bàn").isActive(true).build();
        Skill prefSkill = Skill.builder().skillId(prefSkillId).name("Tiếng Anh giao tiếp").isActive(true).build();

        Event activeEvent = Event.builder()
                .eventId(eventId)
                .eventOrganizer(mockEo)
                .name("Gala Dinner 2026")
                .mode(EventMode.INVITE)
                .status(EventStatus.ACTIVE)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(activeEvent));
        when(skillRepo.findById(mainSkillId)).thenReturn(Optional.of(mainSkill));
        when(skillRepo.findById(prefSkillId)).thenReturn(Optional.of(prefSkill));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> {
            Shift s = i.getArgument(0);
            if (s.getShiftId() == null) s.setShiftId(UUID.randomUUID());
            return s;
        });

        Instant start = activeEvent.getStartTime().plus(2, ChronoUnit.HOURS);
        CreateShiftRequestDto dto = CreateShiftRequestDto.builder()
                .jobType("Phục vụ tiệc")
                .startTime(start)
                .endTime(start.plus(4, ChronoUnit.HOURS))
                .requiredWorkers(2)
                .hourlyWage(BigDecimal.valueOf(50000))
                .performedSkillId(mainSkillId)
                .skillRequirements(List.of(
                        ShiftSkillRequirementDto.builder()
                                .skillId(mainSkillId)
                                .requirementType(SkillRequirementType.REQUIRED)
                                .minVerificationTier(VerificationTier.AI_DOCUMENT)
                                .requiresValidCertificate(false)
                                .build(),
                        ShiftSkillRequirementDto.builder()
                                .skillId(prefSkillId)
                                .requirementType(SkillRequirementType.PREFERRED)
                                .minVerificationTier(VerificationTier.SELF_DECLARED)
                                .requiresValidCertificate(false)
                                .build()
                ))
                .build();

        ShiftResponseDto res = eoEventService.addShiftToEvent(userId, eventId, dto);

        assertEquals(mainSkillId, res.getPerformedSkillId());
        verify(shiftRepository).save(argThat(s ->
                mainSkillId.equals(s.getPerformedSkillId())
                        && s.getRequiredSkills() != null
                        && s.getRequiredSkills().length == 1
                        && mainSkillId.equals(s.getRequiredSkills()[0])
        ));
        verify(shiftSkillReqRepo).saveAll(argThat((List<ShiftSkillRequirement> list) -> list.size() == 2));
    }

    @Test
    @DisplayName("Skill V3 - verifyAttendance(): Xác nhận kỹ năng thực hiện chính và gọi SkillProgressionService")
    void verifyAttendance_SkillV3_ConfirmsPerformedSkillAndTriggersProgression() {
        SkillRepository skillRepo = mock(SkillRepository.class);
        SkillProgressionService progService = mock(SkillProgressionService.class);
        ReflectionTestUtils.setField(eoEventService, "skillRepository", skillRepo);
        ReflectionTestUtils.setField(eoEventService, "skillProgressionService", progService);

        UUID assignmentId = UUID.randomUUID();
        UUID confirmedSkillId = UUID.randomUUID();
        Skill confirmedSkill = Skill.builder().skillId(confirmedSkillId).name("Phục vụ bàn").isActive(true).build();

        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).name("Gala").build();
        Shift mockShift = Shift.builder().shiftId(UUID.randomUUID()).event(mockEvent).jobType("Phục vụ").hourlyWage(BigDecimal.valueOf(50000)).build();
        Worker mockWorker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(User.builder().userId(UUID.randomUUID()).fullName("Worker V3").build())
                .totalShiftsCompleted(2)
                .build();
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId)
                .shift(mockShift)
                .worker(mockWorker)
                .status(AssignmentStatus.CHECKED_IN)
                .eoConfirmed(false)
                .build();
        AttendanceLog attLog = AttendanceLog.builder()
                .shiftAssignment(sa)
                .worker(mockWorker)
                .checkinTime(Instant.now().minus(4, ChronoUnit.HOURS))
                .checkoutTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                .hoursWorked(BigDecimal.valueOf(4))
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.of(attLog));
        when(skillRepo.findById(confirmedSkillId)).thenReturn(Optional.of(confirmedSkill));
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findByAssignmentId(assignmentId)).thenReturn(Optional.empty());

        VerifyAttendanceRequestDto req = VerifyAttendanceRequestDto.builder()
                .confirmedPerformedSkillId(confirmedSkillId)
                .confirmPerformedSkill(true)
                .build();

        ShiftAttendanceDto result = eoEventService.verifyAttendance(userId, assignmentId, req);

        assertNotNull(result);
        assertEquals(confirmedSkillId, sa.getConfirmedPerformedSkillId());
        assertTrue(sa.getEoConfirmed());
        verify(progService).recordCompletedShiftForAssignment(sa, confirmedSkillId);
    }

    @Test
    @DisplayName("UTC078 - getMyEventsPage() - Normal: Trả về trang sự kiện EO kèm metadata phân trang & batch shifts")
    void getMyEventsPage_Success_ReturnsPaginatedEventsWithShifts() {
        UUID e1 = UUID.randomUUID();
        Event event1 = Event.builder().eventId(e1).name("Paginated Event 1").status(EventStatus.ACTIVE).build();
        Shift shift1 = Shift.builder().shiftId(UUID.randomUUID()).event(event1).jobType("Lễ tân").requiredWorkers(2).confirmedWorkers(1).build();

        org.springframework.data.domain.Page<Event> eventPage = new org.springframework.data.domain.PageImpl<>(
                List.of(event1),
                org.springframework.data.domain.PageRequest.of(0, 1),
                3L
        );

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq(eoId), any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(eventPage);
        when(shiftRepository.findByEvent_EventIdIn(List.of(e1))).thenReturn(List.of(shift1));

        com.cani.dto.eo.EoEventPageResponseDto result = eoEventService.getMyEventsPage(userId, 0, 1);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals("Paginated Event 1", result.getItems().get(0).getName());
        assertEquals(1, result.getItems().get(0).getShifts().size());
        assertEquals(0, result.getPage());
        assertEquals(1, result.getSize());
        assertEquals(3L, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertTrue(result.isHasNext());
        assertFalse(result.isHasPrevious());
    }

    @Test
    @DisplayName("UTC079 - getEventWorkersList() - Normal: Batch-fetches assignments, attendance logs, payments và reviews theo O(1)")
    void getEventWorkersList_Success_UsesBatchQueriesWithoutNPlusOne() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).name("Batch Workers Event").build();
        UUID s1Id = UUID.randomUUID();
        UUID s2Id = UUID.randomUUID();
        Shift s1 = Shift.builder().shiftId(s1Id).event(mockEvent).jobType("Lễ tân").hourlyWage(BigDecimal.valueOf(50000)).build();
        Shift s2 = Shift.builder().shiftId(s2Id).event(mockEvent).jobType("Bảo vệ").hourlyWage(BigDecimal.valueOf(60000)).build();

        UUID a1Id = UUID.randomUUID();
        UUID a2Id = UUID.randomUUID();
        Worker w1 = Worker.builder().workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).fullName("Worker A").build()).build();
        Worker w2 = Worker.builder().workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).fullName("Worker B").build()).build();
        ShiftAssignment sa1 = ShiftAssignment.builder().assignmentId(a1Id).shift(s1).worker(w1).status(AssignmentStatus.COMPLETED).eoConfirmed(true).build();
        ShiftAssignment sa2 = ShiftAssignment.builder().assignmentId(a2Id).shift(s2).worker(w2).status(AssignmentStatus.CHECKED_IN).eoConfirmed(false).build();

        AttendanceLog log1 = AttendanceLog.builder().shiftAssignment(sa1).worker(w1).hoursWorked(BigDecimal.valueOf(4.0)).isOnTime(true).build();
        Payment pay1 = Payment.builder().assignmentId(a1Id).status(PaymentStatus.CONFIRMED_FULL).declaredAmount(BigDecimal.valueOf(200000)).build();
        Review rev1 = Review.builder().assignmentId(a1Id).reviewerId(userId).rating(5).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.findByEvent_EventIdAndStatusNot(eventId, ShiftStatus.CANCELLED)).thenReturn(List.of(s1, s2));
        when(shiftAssignmentRepository.findByShift_ShiftIdIn(List.of(s1Id, s2Id))).thenReturn(List.of(sa1, sa2));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentIdIn(List.of(a1Id, a2Id))).thenReturn(List.of(log1));
        when(paymentRepository.findByAssignmentIdIn(List.of(a1Id, a2Id))).thenReturn(List.of(pay1));
        when(reviewRepository.findByAssignmentIdIn(List.of(a1Id, a2Id))).thenReturn(List.of(rev1));

        List<ShiftAttendanceDto> workers = eoEventService.getEventWorkersList(userId, eventId);

        assertNotNull(workers);
        assertEquals(2, workers.size());
        assertEquals("Worker A", workers.get(0).getWorkerName());
        assertEquals("CONFIRMED_FULL", workers.get(0).getPaymentStatus());
        assertTrue(workers.get(0).getHasReviewedWorker());
        assertEquals("Worker B", workers.get(1).getWorkerName());
        assertEquals("NOT_DECLARED", workers.get(1).getPaymentStatus());
        assertFalse(workers.get(1).getHasReviewedWorker());

        verify(shiftAssignmentRepository, times(1)).findByShift_ShiftIdIn(List.of(s1Id, s2Id));
        verify(shiftAssignmentRepository, never()).findByShift_ShiftId(any());
        verify(attendanceLogRepository, never()).findByShiftAssignment_AssignmentId(any());
        verify(paymentRepository, never()).findByAssignmentId(any());
    }

    @Test
    @DisplayName("UTC080 - addShiftToEvent() - Chặn tạo ca trùng vị trí và trùng khung giờ trong cùng sự kiện")
    void addShiftToEvent_ThrowsBadRequest_WhenDuplicateRoleAndTimeSlot() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).name("Sự kiện Test").status(EventStatus.DRAFT).build();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant end = start.plus(4, ChronoUnit.HOURS);

        Shift existingShift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(mockEvent)
                .jobType("Lễ tân / Check-in")
                .startTime(start)
                .endTime(end)
                .status(ShiftStatus.ACTIVE)
                .build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(eventRepository.findByEventIdAndEventOrganizer_EoIdForUpdate(eventId, eoId)).thenReturn(Optional.of(mockEvent));
        when(shiftRepository.findByEvent_EventIdAndStatusNot(eventId, ShiftStatus.CANCELLED))
                .thenReturn(List.of(existingShift));

        CreateShiftRequestDto duplicateDto = CreateShiftRequestDto.builder()
                .jobType("  lễ tân /   check-in ")
                .startTime(start.plusSeconds(15))
                .endTime(end.plusSeconds(15))
                .requiredWorkers(2)
                .hourlyWage(BigDecimal.valueOf(50000))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> eoEventService.addShiftToEvent(userId, eventId, duplicateDto));
        assertTrue(ex.getMessage().contains("Đã tồn tại ca làm việc vị trí"));
        verify(shiftRepository, never()).save(any(Shift.class));
    }

    @Test
    @DisplayName("UTC081 - getWorkerReviewOverview() - Tổng hợp lượt chờ đánh giá và đã đánh giá")
    void getWorkerReviewOverview_ReturnsPendingFirstAndPopulatesExistingReviews() {
        UUID eventId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(eventId).eventOrganizer(mockEo).name("Tech Expo").status(EventStatus.ACTIVE).build();
        Instant pastStart = Instant.now().minus(6, ChronoUnit.HOURS);
        Instant pastEnd = Instant.now().minus(2, ChronoUnit.HOURS);
        Shift shift = Shift.builder().shiftId(UUID.randomUUID()).event(mockEvent).jobType("Lễ tân").startTime(pastStart).endTime(pastEnd).status(ShiftStatus.COMPLETED).build();

        UUID aPendingId = UUID.randomUUID();
        UUID aReviewedId = UUID.randomUUID();
        Worker w1 = Worker.builder().workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).fullName("Nguyễn Văn A").build()).build();
        Worker w2 = Worker.builder().workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).fullName("Trần Thị B").build()).build();
        ShiftAssignment saPending = ShiftAssignment.builder().assignmentId(aPendingId).shift(shift).worker(w1).status(AssignmentStatus.COMPLETED).eoConfirmed(true).build();
        ShiftAssignment saReviewed = ShiftAssignment.builder().assignmentId(aReviewedId).shift(shift).worker(w2).status(AssignmentStatus.COMPLETED).eoConfirmed(true).build();

        AttendanceLog log1 = AttendanceLog.builder().shiftAssignment(saPending).worker(w1).checkoutTime(pastEnd).build();
        AttendanceLog log2 = AttendanceLog.builder().shiftAssignment(saReviewed).worker(w2).checkoutTime(pastEnd).build();
        Review rev2 = Review.builder().assignmentId(aReviewedId).reviewerId(userId).rating(5).comment("Làm việc rất tốt").tags(new String[]{"Đúng giờ"}).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findCollaboratedWorkersByEoId(eq(eoId), anyList())).thenReturn(List.of(saReviewed, saPending));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentIdIn(anyCollection())).thenReturn(List.of(log1, log2));
        when(reviewRepository.findByReviewerIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(rev2));

        List<EoWorkerReviewItemDto> list = eoEventService.getWorkerReviewOverview(userId);

        assertEquals(2, list.size());
        assertEquals(aPendingId, list.get(0).getAssignmentId());
        assertTrue(list.get(0).isCanReview());
        assertFalse(list.get(0).isHasReviewedWorker());

        assertEquals(aReviewedId, list.get(1).getAssignmentId());
        assertFalse(list.get(1).isCanReview());
        assertTrue(list.get(1).isHasReviewedWorker());
        assertEquals(5, list.get(1).getRating());
        assertEquals("Làm việc rất tốt", list.get(1).getComment());
    }

    @Test
    @DisplayName("UTC082 - updateShift() - Khi ca đã vào giờ -> cho phép tăng số lượng nhân sự để tuyển thêm, mở lại trạng thái ACTIVE")
    void updateShift_AllowsIncreasingWorkersWhenShiftAlreadyStarted() {
        UUID shiftId = UUID.randomUUID();
        Instant pastStart = Instant.now().minus(30, ChronoUnit.MINUTES);
        Instant futureEnd = Instant.now().plus(3, ChronoUnit.HOURS);
        Event mockEvent = Event.builder()
                .eventId(UUID.randomUUID()).eventOrganizer(mockEo).status(EventStatus.ACTIVE)
                .totalRequiredWorkers(2).totalEstimatedCost(new BigDecimal("400000")).build();
        Shift startedShift = Shift.builder()
                .shiftId(shiftId).event(mockEvent).jobType("Lễ tân")
                .status(ShiftStatus.FULLY_STAFFED)
                .startTime(pastStart).endTime(futureEnd).arrivalTime(pastStart)
                .hourlyWage(BigDecimal.valueOf(50000)).allowance(BigDecimal.ZERO)
                .requiredWorkers(2).confirmedWorkers(2).remainingWorkers(0)
                .totalCost(new BigDecimal("400000")).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.of(startedShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> i.getArgument(0));

        UpdateShiftRequestDto dto = UpdateShiftRequestDto.builder()
                .jobType("Lễ tân")
                .startTime(pastStart.truncatedTo(ChronoUnit.MINUTES))
                .endTime(futureEnd.truncatedTo(ChronoUnit.MINUTES))
                .requiredWorkers(5)
                .hourlyWage(BigDecimal.valueOf(50000))
                .allowance(BigDecimal.ZERO)
                .build();

        ShiftResponseDto res = eoEventService.updateShift(userId, shiftId, dto);
        assertEquals(5, res.getRequiredWorkers());
        assertEquals(3, res.getRemainingWorkers());
        assertEquals(ShiftStatus.ACTIVE, startedShift.getStatus());
        assertEquals(ShiftStatus.IN_PROGRESS, res.getStatus());
    }

    @Test
    @DisplayName("UTC083 - updateShift() - Khi ca đã vào giờ -> chặn sửa thời gian, số tiền hoặc hạ dưới số người đã nhận")
    void updateShift_RejectsTimeWageOrUnderConfirmedWorkersWhenStarted() {
        UUID shiftId = UUID.randomUUID();
        Instant pastStart = Instant.now().minus(30, ChronoUnit.MINUTES);
        Instant futureEnd = Instant.now().plus(3, ChronoUnit.HOURS);
        Event mockEvent = Event.builder()
                .eventId(UUID.randomUUID()).eventOrganizer(mockEo).status(EventStatus.ACTIVE).build();
        Shift startedShift = Shift.builder()
                .shiftId(shiftId).event(mockEvent).jobType("Lễ tân")
                .status(ShiftStatus.IN_PROGRESS)
                .startTime(pastStart).endTime(futureEnd).arrivalTime(pastStart)
                .hourlyWage(BigDecimal.valueOf(50000)).allowance(BigDecimal.ZERO)
                .requiredWorkers(3).confirmedWorkers(2).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.of(startedShift));

        UpdateShiftRequestDto timeChangedDto = UpdateShiftRequestDto.builder()
                .jobType("Lễ tân")
                .startTime(pastStart.plus(10, ChronoUnit.MINUTES))
                .endTime(futureEnd)
                .requiredWorkers(4)
                .hourlyWage(BigDecimal.valueOf(50000))
                .allowance(BigDecimal.ZERO)
                .build();
        BadRequestException timeEx = assertThrows(BadRequestException.class,
                () -> eoEventService.updateShift(userId, shiftId, timeChangedDto));
        assertTrue(timeEx.getMessage().contains("không thể chỉnh sửa thời gian"));

        UpdateShiftRequestDto wageChangedDto = UpdateShiftRequestDto.builder()
                .jobType("Lễ tân")
                .startTime(pastStart)
                .endTime(futureEnd)
                .requiredWorkers(4)
                .hourlyWage(BigDecimal.valueOf(70000))
                .allowance(BigDecimal.ZERO)
                .build();
        BadRequestException wageEx = assertThrows(BadRequestException.class,
                () -> eoEventService.updateShift(userId, shiftId, wageChangedDto));
        assertTrue(wageEx.getMessage().contains("không thể chỉnh sửa mức thù lao"));

        UpdateShiftRequestDto reduceBelowConfirmedDto = UpdateShiftRequestDto.builder()
                .jobType("Lễ tân")
                .startTime(pastStart)
                .endTime(futureEnd)
                .requiredWorkers(1)
                .hourlyWage(BigDecimal.valueOf(50000))
                .allowance(BigDecimal.ZERO)
                .build();
        BadRequestException workersEx = assertThrows(BadRequestException.class,
                () -> eoEventService.updateShift(userId, shiftId, reduceBelowConfirmedDto));
        assertTrue(workersEx.getMessage().contains("không được nhỏ hơn số nhân sự đã nhận ca"));
    }

    @Test
    @DisplayName("UTC084 - rateWorker() & getWorkerReviewOverview() - Cho phép EO đánh giá ngay sau khi Worker Check-out dù chưa hết giờ ca")
    void rateWorkerAndOverview_AllowImmediateReviewAfterCheckoutBeforeShiftEndTime() {
        UUID assignmentId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        Event mockEvent = Event.builder().eventId(UUID.randomUUID()).eventOrganizer(mockEo).name("Tech Expo").status(EventStatus.ACTIVE).build();
        Instant futureEnd = Instant.now().plus(2, ChronoUnit.HOURS);
        Shift ongoingShift = Shift.builder()
                .shiftId(shiftId).event(mockEvent).jobType("Lễ tân")
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS)).endTime(futureEnd)
                .status(ShiftStatus.IN_PROGRESS).build();
        Worker mockWorker = Worker.builder()
                .workerId(UUID.randomUUID()).user(User.builder().userId(UUID.randomUUID()).fullName("Nguyễn Văn B").build())
                .ratingAvg(BigDecimal.valueOf(4.0)).ratingCount(1).reliabilityScore(90).build();
        ShiftAssignment sa = ShiftAssignment.builder()
                .assignmentId(assignmentId).shift(ongoingShift).worker(mockWorker)
                .status(AssignmentStatus.COMPLETED).build();
        AttendanceLog att = AttendanceLog.builder()
                .shiftAssignment(sa).worker(mockWorker).checkoutTime(Instant.now()).build();

        when(eventOrganizerRepository.findByUser_UserId(userId)).thenReturn(Optional.of(mockEo));
        when(shiftAssignmentRepository.findCollaboratedWorkersByEoId(eq(eoId), anyList())).thenReturn(List.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentIdIn(anyCollection())).thenReturn(List.of(att));
        when(reviewRepository.findByReviewerIdOrderByCreatedAtDesc(userId)).thenReturn(Collections.emptyList());

        List<EoWorkerReviewItemDto> overview = eoEventService.getWorkerReviewOverview(userId);
        assertEquals(1, overview.size());
        assertTrue(overview.get(0).isCanReview());
        assertNull(overview.get(0).getReviewBlockReason());

        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sa));
        when(attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)).thenReturn(Optional.of(att));
        when(reviewRepository.findByAssignmentIdAndReviewerId(assignmentId, userId)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        Review saved = eoEventService.rateWorker(userId, assignmentId, CreateEoReviewRequestDto.builder().rating(5).comment("Xong sớm").build());
        assertEquals(5, saved.getRating());
    }

}
