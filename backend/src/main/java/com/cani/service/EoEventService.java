package com.cani.service;

import com.cani.dto.event.CreateEventRequestDto;
import com.cani.dto.event.CreateShiftRequestDto;
import com.cani.dto.event.EventResponseDto;
import com.cani.dto.event.ShiftResponseDto;
import com.cani.entity.Event;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Shift;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.EventRepository;
import com.cani.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EoEventService {

    private final EventOrganizerRepository eventOrganizerRepository;
    private final EventRepository eventRepository;
    private final ShiftRepository shiftRepository;
    private final MatchingDispatchService matchingDispatchService;

    /**
     * EO tạo Sự kiện mới (Trạng thái DRAFT)
     */
    @Transactional
    public EventResponseDto createEvent(UUID userId, CreateEventRequestDto dto) {
        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin nhà tổ chức sự kiện cho User ID: " + userId));

        Event event = Event.builder()
                .eventOrganizer(eo)
                .name(dto.getName())
                .description(dto.getDescription())
                .venueAddress(dto.getVenueAddress())
                .venueLat(dto.getVenueLat())
                .venueLng(dto.getVenueLng())
                .eventDate(dto.getEventDate())
                .mode(dto.getMode())
                .status(EventStatus.DRAFT)
                .readinessScore(0)
                .totalShifts(0)
                .totalRequiredWorkers(0)
                .totalConfirmedWorkers(0)
                .totalEstimatedCost(BigDecimal.ZERO)
                .totalPaid(BigDecimal.ZERO)
                .build();

        event = eventRepository.save(event);
        log.info("EO {} đã tạo sự kiện: {} (ID: {})", userId, event.getName(), event.getEventId());

        return mapToEventResponseDto(event, List.of());
    }

    /**
     * Thêm Ca làm việc (Shift) vào Sự kiện
     */
    @Transactional
    public ShiftResponseDto addShiftToEvent(UUID userId, UUID eventId, CreateShiftRequestDto dto) {
        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin nhà tổ chức sự kiện cho User ID: " + userId));

        Event event = eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eo.getEoId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện ID: " + eventId + " thuộc nhà tổ chức này"));

        if (dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new BadRequestException("Thời gian kết thúc ca phải sau thời gian bắt đầu");
        }

        // Tính chi phí ca làm việc: hourlyWage * số giờ * requiredWorkers + allowance
        long minutes = Duration.between(dto.getStartTime(), dto.getEndTime()).toMinutes();
        double hours = Math.max(0.5, minutes / 60.0);
        BigDecimal hourlyWage = dto.getHourlyWage();
        int workers = dto.getRequiredWorkers();
        BigDecimal allowance = dto.getAllowance() != null ? dto.getAllowance() : BigDecimal.ZERO;

        BigDecimal shiftCost = hourlyWage.multiply(BigDecimal.valueOf(hours)).multiply(BigDecimal.valueOf(workers)).add(allowance);

        Shift shift = Shift.builder()
                .event(event)
                .jobType(dto.getJobType())
                .description(dto.getDescription())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .requiredWorkers(workers)
                .confirmedWorkers(0)
                .remainingWorkers(workers)
                .hourlyWage(hourlyWage)
                .allowance(allowance)
                .totalCost(shiftCost)
                .status(event.getStatus() == EventStatus.ACTIVE ? ShiftStatus.ACTIVE : ShiftStatus.DRAFT)
                .riskScore(0)
                .locationAddress(dto.getLocationAddress() != null ? dto.getLocationAddress() : event.getVenueAddress())
                .locationLat(dto.getLocationLat() != null ? dto.getLocationLat() : event.getVenueLat())
                .locationLng(dto.getLocationLng() != null ? dto.getLocationLng() : event.getVenueLng())
                .requiredSkills(dto.getRequiredSkills())
                .minReliabilityScore(dto.getMinReliabilityScore())
                .maxDistanceKm(dto.getMaxDistanceKm())
                .minExperienceLevel(dto.getMinExperienceLevel())
                .dressCode(dto.getDressCode())
                .build();

        shift = shiftRepository.save(shift);

        // Cập nhật lại thống kê trên Event
        event.setTotalShifts((event.getTotalShifts() != null ? event.getTotalShifts() : 0) + 1);
        event.setTotalRequiredWorkers((event.getTotalRequiredWorkers() != null ? event.getTotalRequiredWorkers() : 0) + workers);
        BigDecimal currentCost = event.getTotalEstimatedCost() != null ? event.getTotalEstimatedCost() : BigDecimal.ZERO;
        event.setTotalEstimatedCost(currentCost.add(shiftCost));
        eventRepository.save(event);

        log.info("Đã thêm ca làm việc '{}' (cần {} người) vào sự kiện {}", shift.getJobType(), workers, event.getName());
        return mapToShiftResponseDto(shift);
    }

    /**
     * Danh sách tất cả các sự kiện của EO
     */
    @Transactional(readOnly = true)
    public List<EventResponseDto> getMyEvents(UUID userId) {
        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin nhà tổ chức sự kiện cho User ID: " + userId));

        List<Event> events = eventRepository.findByEventOrganizer_EoIdOrderByCreatedAtDesc(eo.getEoId());
        return events.stream().map(e -> {
            List<Shift> shifts = shiftRepository.findByEvent_EventId(e.getEventId());
            return mapToEventResponseDto(e, shifts);
        }).collect(Collectors.toList());
    }

    /**
     * Xem chi tiết sự kiện
     */
    @Transactional(readOnly = true)
    public EventResponseDto getEventDetail(UUID userId, UUID eventId) {
        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin nhà tổ chức sự kiện cho User ID: " + userId));

        Event event = eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eo.getEoId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện ID: " + eventId));

        List<Shift> shifts = shiftRepository.findByEvent_EventId(eventId);
        return mapToEventResponseDto(event, shifts);
    }

    /**
     * EO xuất bản Sự kiện và tự động kích hoạt Matching Engine cho tất cả các ca (Không cần ví/ký quỹ)
     */
    @Transactional
    public EventResponseDto publishEvent(UUID userId, UUID eventId) {
        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin nhà tổ chức sự kiện cho User ID: " + userId));

        Event event = eventRepository.findByEventIdAndEventOrganizer_EoId(eventId, eo.getEoId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện ID: " + eventId));

        event.setStatus(EventStatus.ACTIVE);
        event = eventRepository.save(event);

        List<Shift> shifts = shiftRepository.findByEvent_EventId(eventId);
        for (Shift s : shifts) {
            s.setStatus(ShiftStatus.ACTIVE);
            shiftRepository.save(s);
            // Kích hoạt Matching Engine tìm Worker
            matchingDispatchService.dispatchForShift(s.getShiftId());
        }

        log.info("EO {} đã xuất bản sự kiện {} và kích hoạt Matching cho {} ca làm việc",
                eo.getEoId(), event.getName(), shifts.size());

        return mapToEventResponseDto(event, shifts);
    }

    /**
     * Kích hoạt điều phối tìm ứng viên (Dispatch) cho 1 ca cụ thể
     */
    @Transactional
    public ShiftResponseDto publishShift(UUID userId, UUID shiftId) {
        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin nhà tổ chức sự kiện"));

        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc: " + shiftId));

        if (!shift.getEvent().getEventOrganizer().getEoId().equals(eo.getEoId())) {
            throw new IllegalStateException("Bạn không sở hữu ca làm việc này");
        }

        shift.setStatus(ShiftStatus.ACTIVE);
        shift = shiftRepository.save(shift);

        matchingDispatchService.dispatchForShift(shiftId);
        log.info("EO {} kích hoạt điều phối ứng viên cho ca {}", eo.getEoId(), shiftId);

        return mapToShiftResponseDto(shift);
    }

    private EventResponseDto mapToEventResponseDto(Event e, List<Shift> shifts) {
        return EventResponseDto.builder()
                .eventId(e.getEventId())
                .name(e.getName())
                .description(e.getDescription())
                .venueAddress(e.getVenueAddress())
                .venueLat(e.getVenueLat())
                .venueLng(e.getVenueLng())
                .eventDate(e.getEventDate())
                .mode(e.getMode())
                .status(e.getStatus())
                .readinessScore(e.getReadinessScore())
                .totalShifts(e.getTotalShifts())
                .totalRequiredWorkers(e.getTotalRequiredWorkers())
                .totalConfirmedWorkers(e.getTotalConfirmedWorkers())
                .totalEstimatedCost(e.getTotalEstimatedCost())
                .totalPaid(e.getTotalPaid())
                .createdAt(e.getCreatedAt())
                .shifts(shifts.stream().map(this::mapToShiftResponseDto).collect(Collectors.toList()))
                .build();
    }

    private ShiftResponseDto mapToShiftResponseDto(Shift s) {
        return ShiftResponseDto.builder()
                .shiftId(s.getShiftId())
                .eventId(s.getEvent() != null ? s.getEvent().getEventId() : null)
                .jobType(s.getJobType())
                .description(s.getDescription())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .requiredWorkers(s.getRequiredWorkers())
                .confirmedWorkers(s.getConfirmedWorkers())
                .remainingWorkers(s.getRemainingWorkers())
                .hourlyWage(s.getHourlyWage())
                .allowance(s.getAllowance())
                .totalCost(s.getTotalCost())
                .status(s.getStatus())
                .riskScore(s.getRiskScore())
                .locationAddress(s.getLocationAddress())
                .locationLat(s.getLocationLat())
                .locationLng(s.getLocationLng())
                .minReliabilityScore(s.getMinReliabilityScore())
                .minExperienceLevel(s.getMinExperienceLevel())
                .dressCode(s.getDressCode())
                .build();
    }
}
