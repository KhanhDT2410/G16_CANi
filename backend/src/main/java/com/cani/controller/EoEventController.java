package com.cani.controller;

import com.cani.dto.event.CreateEventRequestDto;
import com.cani.dto.event.CreateShiftRequestDto;
import com.cani.dto.event.EventResponseDto;
import com.cani.dto.event.ShiftResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.exception.BadRequestException;
import com.cani.service.EoEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/eo/events")
@RequiredArgsConstructor
public class EoEventController {

    private final EoEventService eoEventService;

    /**
     * EO tạo Sự kiện mới (Trạng thái DRAFT)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<EventResponseDto>> createEvent(
            @Valid @RequestBody CreateEventRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        EventResponseDto created = eoEventService.createEvent(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(created, "Tạo sự kiện thành công (Trạng thái: DRAFT). Hãy thêm các ca làm việc và xuất bản sự kiện để mở tìm nhân sự."));
    }

    /**
     * Thêm Ca làm việc (Shift) vào Sự kiện
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/{eventId}/shifts")
    public ResponseEntity<ApiResponse<ShiftResponseDto>> addShift(
            @PathVariable("eventId") UUID eventId,
            @Valid @RequestBody CreateShiftRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        ShiftResponseDto shift = eoEventService.addShiftToEvent(userId, eventId, request);
        return ResponseEntity.ok(ApiResponse.ok(shift, "Thêm ca làm việc thành công vào sự kiện."));
    }

    /**
     * Danh sách tất cả sự kiện của EO đang đăng nhập
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponseDto>>> getMyEvents(
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        List<EventResponseDto> list = eoEventService.getMyEvents(userId);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách sự kiện thành công"));
    }

    /**
     * Xem chi tiết sự kiện
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponseDto>> getEventDetail(
            @PathVariable("eventId") UUID eventId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        EventResponseDto detail = eoEventService.getEventDetail(userId, eventId);
        return ResponseEntity.ok(ApiResponse.ok(detail, "Lấy chi tiết sự kiện thành công"));
    }

    /**
     * EO xuất bản Sự kiện và tự động kích hoạt Matching Engine (Non-Escrow)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/{eventId}/publish")
    public ResponseEntity<ApiResponse<EventResponseDto>> publishEvent(
            @PathVariable("eventId") UUID eventId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        EventResponseDto published = eoEventService.publishEvent(userId, eventId);
        return ResponseEntity.ok(ApiResponse.ok(published, "Xuất bản sự kiện thành công! Hệ thống đang tự động điều phối Matching tìm Worker phù hợp."));
    }

    /**
     * EO kích hoạt Matching/Dispatch cho một ca làm việc cụ thể
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/shifts/{shiftId}/publish")
    public ResponseEntity<ApiResponse<ShiftResponseDto>> publishShift(
            @PathVariable("shiftId") UUID shiftId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        ShiftResponseDto shift = eoEventService.publishShift(userId, shiftId);
        return ResponseEntity.ok(ApiResponse.ok(shift, "Kích hoạt điều phối tìm Worker cho ca làm việc thành công!"));
    }

    private UUID resolveUserId(Authentication authentication, UUID explicitUserId) {
        if (explicitUserId != null) {
            return explicitUserId;
        }
        if (authentication != null && authentication.getPrincipal() instanceof User u) {
            return u.getUserId();
        }
        if (authentication != null && authentication.getName() != null) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (Exception ignored) {}
        }
        throw new BadRequestException("Vui lòng đăng nhập hoặc cung cấp userId hợp lệ");
    }
}
