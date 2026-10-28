package com.cani.controller;

import com.cani.dto.event.*;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.Review;
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
import java.util.Map;
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
     * UC-11: Chỉnh sửa Sự kiện (Edit Event)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponseDto>> updateEvent(
            @PathVariable("eventId") UUID eventId,
            @Valid @RequestBody UpdateEventRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        EventResponseDto updated = eoEventService.updateEvent(userId, eventId, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Cập nhật sự kiện thành công!"));
    }

    /**
     * UC-12: Hủy Sự kiện (Cancel Event)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/{eventId}/cancel")
    public ResponseEntity<ApiResponse<EventResponseDto>> cancelEvent(
            @PathVariable("eventId") UUID eventId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        String reason = body != null ? body.get("reason") : "Ban tổ chức hủy sự kiện";
        EventResponseDto cancelled = eoEventService.cancelEvent(userId, eventId, reason);
        return ResponseEntity.ok(ApiResponse.ok(cancelled, "Đã hủy sự kiện thành công!"));
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
     * UC-18: Chỉnh sửa Ca làm việc (Edit Shift)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PutMapping("/shifts/{shiftId}")
    public ResponseEntity<ApiResponse<ShiftResponseDto>> updateShift(
            @PathVariable("shiftId") UUID shiftId,
            @Valid @RequestBody UpdateShiftRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        ShiftResponseDto shift = eoEventService.updateShift(userId, shiftId, request);
        return ResponseEntity.ok(ApiResponse.ok(shift, "Cập nhật ca làm việc thành công!"));
    }

    /**
     * UC-19: Hủy Ca làm việc (Cancel Shift)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/shifts/{shiftId}/cancel")
    public ResponseEntity<ApiResponse<ShiftResponseDto>> cancelShift(
            @PathVariable("shiftId") UUID shiftId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        String reason = body != null ? body.get("reason") : "Ban tổ chức hủy ca";
        ShiftResponseDto shift = eoEventService.cancelShift(userId, shiftId, reason);
        return ResponseEntity.ok(ApiResponse.ok(shift, "Đã hủy ca làm việc thành công!"));
    }

    /**
     * UC-52: Sinh mã QR Điểm danh của Ca làm việc (Check-in & Check-out QR Generator)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/shifts/{shiftId}/qr")
    public ResponseEntity<ApiResponse<ShiftQrCodeResponseDto>> getShiftQr(
            @PathVariable("shiftId") UUID shiftId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        ShiftQrCodeResponseDto qr = eoEventService.generateShiftQr(userId, shiftId);
        return ResponseEntity.ok(ApiResponse.ok(qr, "Lấy mã QR điểm danh ca thành công!"));
    }

    /**
     * UC-48: Bảng theo dõi điểm danh trực tiếp của Ca làm việc (Live Attendance Dashboard)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/shifts/{shiftId}/attendance")
    public ResponseEntity<ApiResponse<List<ShiftAttendanceDto>>> getShiftAttendance(
            @PathVariable("shiftId") UUID shiftId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        List<ShiftAttendanceDto> attendanceList = eoEventService.getShiftAttendanceList(userId, shiftId);
        return ResponseEntity.ok(ApiResponse.ok(attendanceList, "Lấy danh sách điểm danh ca thành công!"));
    }

    /**
     * UC-49: EO xác nhận hoàn thành ca & duyệt công cho Worker (Confirm Completion)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/assignments/{assignmentId}/verify")
    public ResponseEntity<ApiResponse<ShiftAttendanceDto>> verifyAttendance(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        ShiftAttendanceDto verified = eoEventService.verifyAttendance(userId, assignmentId);
        return ResponseEntity.ok(ApiResponse.ok(verified, "Xác nhận hoàn thành ca và duyệt công cho Worker thành công!"));
    }

    /**
     * UC-69: EO đánh giá Worker sau ca làm việc (Rate Worker 1-5★)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/assignments/{assignmentId}/reviews")
    public ResponseEntity<ApiResponse<Review>> rateWorker(
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody CreateEoReviewRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        Review review = eoEventService.rateWorker(userId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.ok(review, "Đã gửi đánh giá Worker thành công!"));
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
