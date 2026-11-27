package com.cani.controller;

import com.cani.dto.admin.noshow.NoShowResponseDto;
import com.cani.dto.eo.EoEventPageResponseDto;
import com.cani.dto.event.*;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.Review;
import com.cani.security.SecurityUtils;
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
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
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
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
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
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        String reason = body != null ? body.get("reason") : null;
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
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        ShiftResponseDto shift = eoEventService.addShiftToEvent(userId, eventId, request);
        return ResponseEntity.ok(ApiResponse.ok(shift, "Thêm ca làm việc thành công vào sự kiện."));
    }

    /**
     * UC-20 (WBS 74.0): EO tạo hàng loạt Ca làm việc (Bulk Create Shifts)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/{eventId}/shifts/bulk")
    public ResponseEntity<ApiResponse<List<ShiftResponseDto>>> addBulkShifts(
            @PathVariable("eventId") UUID eventId,
            @Valid @RequestBody CreateBulkShiftsRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<ShiftResponseDto> shifts = eoEventService.addBulkShifts(userId, eventId, request);
        return ResponseEntity.ok(ApiResponse.ok(shifts, "Tạo hàng loạt " + shifts.size() + " ca làm việc thành công!"));
    }

    /**
     * UC-18: Chỉnh sửa Ca làm việc (Edit Shift)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PutMapping("/shifts/{shiftId}")
    public ResponseEntity<ApiResponse<ShiftResponseDto>> updateShift(
            @PathVariable("shiftId") UUID shiftId,
            @Valid @RequestBody UpdateShiftRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
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
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
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
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
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
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
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
            @RequestBody(required = false) VerifyAttendanceRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        ShiftAttendanceDto verified = eoEventService.verifyAttendance(userId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.ok(verified, "Xác nhận hoàn thành ca và duyệt công cho Worker thành công!"));
    }

    /**
     * UC-83: EO báo cáo công việc không đạt yêu cầu / chưa hoàn thành (Report Incomplete Work)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/assignments/{assignmentId}/report-incomplete")
    public ResponseEntity<ApiResponse<com.cani.dto.dispute.DisputeDetailResponseDto>> reportIncompleteWork(
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody ReportIncompleteWorkRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        com.cani.dto.dispute.DisputeDetailResponseDto dispute = eoEventService.reportIncompleteWork(userId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.ok(dispute, "Đã gửi báo cáo công việc không đạt yêu cầu tới Bộ phận CSKH xử lý!"));
    }

    /**
     * UC-88: EO báo cáo Worker vắng mặt không báo trước (Report No-Show)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/assignments/{assignmentId}/no-show")
    public ResponseEntity<ApiResponse<NoShowResponseDto>> reportNoShow(
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody(required = false) ReportNoShowRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        NoShowResponseDto response = eoEventService.reportNoShow(userId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã ghi nhận vắng mặt (No-show)! Hệ thống tự động kích hoạt điều phối khẩn cấp để tìm nhân sự thay thế cho sự kiện."));
    }

    /**
     * UC-69: EO đánh giá Worker sau ca làm việc (Rate Worker 1-5★)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/assignments/{assignmentId}/reviews")
    public ResponseEntity<ApiResponse<Review>> rateWorker(
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody CreateEoReviewRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        Review review = eoEventService.rateWorker(userId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.ok(review, "Đã gửi đánh giá Worker thành công!"));
    }

    /**
     * Tổng hợp danh sách nhân sự cần đánh giá & đã đánh giá trên tất cả sự kiện của EO
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/worker-reviews")
    public ResponseEntity<ApiResponse<List<EoWorkerReviewItemDto>>> getWorkerReviewOverview(
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<EoWorkerReviewItemDto> list = eoEventService.getWorkerReviewOverview(userId);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách đánh giá nhân sự thành công"));
    }

    /**
     * Danh sách tất cả sự kiện của EO đang đăng nhập
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponseDto>>> getMyEvents(
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<EventResponseDto> list = eoEventService.getMyEvents(userId);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách sự kiện thành công"));
    }

    /**
     * Danh sách sự kiện của EO có phân trang (Batch-fetched O(1) queries)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<EoEventPageResponseDto>> getMyEventsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        EoEventPageResponseDto pageResult = eoEventService.getMyEventsPage(userId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(pageResult, "Lấy danh sách sự kiện phân trang thành công"));
    }

    /**
     * Xem chi tiết sự kiện
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponseDto>> getEventDetail(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        EventResponseDto detail = eoEventService.getEventDetail(userId, eventId);
        return ResponseEntity.ok(ApiResponse.ok(detail, "Lấy chi tiết sự kiện thành công"));
    }

    /**
     * Xóa Sự kiện khỏi danh sách của EO (Soft delete)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        eoEventService.deleteEvent(userId, eventId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã xóa sự kiện khỏi danh sách thành công!"));
    }

    /**
     * EO xuất bản Sự kiện và tự động kích hoạt Matching Engine (Non-Escrow)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/{eventId}/publish")
    public ResponseEntity<ApiResponse<EventResponseDto>> publishEvent(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
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
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        ShiftResponseDto shift = eoEventService.publishShift(userId, shiftId);
        return ResponseEntity.ok(ApiResponse.ok(shift, "Đã kích hoạt điều phối tìm Worker cho ca làm việc. Hệ thống gửi đề xuất cho các ứng viên phù hợp (xem log nếu không có ứng viên đạt điều kiện)."));
    }

    /**
     * UC-59: EO kích hoạt điều phối khẩn cấp (Emergency Dispatch) khi ca làm việc thiếu nhân sự
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/shifts/{shiftId}/emergency-dispatch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerEmergencyDispatch(
            @PathVariable("shiftId") UUID shiftId,
            @RequestParam(required = false) Double radiusKm,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        EmergencyDispatchRequestDto requestDto = parseEmergencyRequest(radiusKm, body);
        Map<String, Object> result = eoEventService.triggerEmergencyDispatchWithOptions(userId, shiftId, requestDto);
        return ResponseEntity.ok(ApiResponse.ok(result, buildEmergencyDispatchResponseMessage(result, false)));
    }

    /**
     * UC-59: EO cập nhật tham số (bán kính, nới lỏng kỹ năng, phụ cấp) và quét lại điều phối khẩn cấp
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/shifts/{shiftId}/emergency-dispatch/redispatch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> redispatchEmergencyDispatch(
            @PathVariable("shiftId") UUID shiftId,
            @RequestParam(required = false) Double radiusKm,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        EmergencyDispatchRequestDto requestDto = parseEmergencyRequest(radiusKm, body);
        Map<String, Object> result = eoEventService.redispatchEmergencyDispatch(userId, shiftId, requestDto);
        return ResponseEntity.ok(ApiResponse.ok(result, buildEmergencyDispatchResponseMessage(result, true)));
    }

    private EmergencyDispatchRequestDto parseEmergencyRequest(Double radiusKm, Map<String, Object> body) {
        Double resolvedRadiusKm = radiusKm;
        com.cani.entity.enums.EmergencySkillRelaxation skillRelaxation = null;
        Integer minReliabilityScore = null;
        java.math.BigDecimal emergencyBonus = null;

        if (body != null) {
            if (resolvedRadiusKm == null) {
                Object raw = body.get("radiusKm") != null ? body.get("radiusKm") : body.get("expandedRadiusKm");
                if (raw instanceof Number n) {
                    resolvedRadiusKm = n.doubleValue();
                } else if (raw != null) {
                    try {
                        resolvedRadiusKm = Double.parseDouble(raw.toString().trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            Object rawRelax = body.get("skillRelaxation");
            if (rawRelax != null && !rawRelax.toString().isBlank()) {
                try {
                    skillRelaxation = com.cani.entity.enums.EmergencySkillRelaxation.valueOf(
                            rawRelax.toString().trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }
            Object rawMinRs = body.get("minReliabilityScore");
            if (rawMinRs instanceof Number n) {
                minReliabilityScore = n.intValue();
            } else if (rawMinRs != null && !rawMinRs.toString().isBlank()) {
                try {
                    minReliabilityScore = Integer.parseInt(rawMinRs.toString().trim());
                } catch (NumberFormatException ignored) {
                }
            }
            Object rawBonus = body.get("emergencyBonus");
            if (rawBonus instanceof Number n) {
                emergencyBonus = java.math.BigDecimal.valueOf(n.doubleValue());
            } else if (rawBonus != null && !rawBonus.toString().isBlank()) {
                try {
                    emergencyBonus = new java.math.BigDecimal(rawBonus.toString().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return EmergencyDispatchRequestDto.builder()
                .radiusKm(resolvedRadiusKm)
                .skillRelaxation(skillRelaxation)
                .minReliabilityScore(minReliabilityScore)
                .emergencyBonus(emergencyBonus)
                .build();
    }

    private String buildEmergencyDispatchResponseMessage(Map<String, Object> result, boolean isRedispatch) {
        int dispatched = ((Number) result.get("dispatchedCount")).intValue();
        double effectiveRadius = ((Number) result.get("effectiveRadiusKm")).doubleValue();
        String radiusLabel = (effectiveRadius == Math.rint(effectiveRadius))
                ? String.valueOf((long) effectiveRadius)
                : String.valueOf(effectiveRadius);
        if (dispatched > 0) {
            return (isRedispatch ? "Đã quét lại điều phối khẩn cấp (" : "Kích hoạt điều phối khẩn cấp thành công (")
                    + radiusLabel + " km)! Đã gửi " + dispatched + " đề xuất khẩn cấp đến Worker phù hợp.";
        }
        return (isRedispatch ? "Đã cập nhật & quét lại khẩn cấp (" : "Đã bật điều phối khẩn cấp (")
                + radiusLabel + " km) · Chưa tìm được Worker phù hợp.";
    }

    /**
     * Tắt điều phối khẩn cấp (Emergency Dispatch) cho ca làm việc
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/shifts/{shiftId}/emergency-dispatch/off")
    public ResponseEntity<ApiResponse<Map<String, Object>>> disableEmergencyDispatch(
            @PathVariable("shiftId") UUID shiftId,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        eoEventService.disableEmergencyDispatch(userId, shiftId);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("shiftId", shiftId, "emergency", false),
                "Đã tắt chế độ điều phối khẩn cấp cho ca làm việc."
        ));
    }

    /**
     * Lấy danh sách toàn bộ nhân sự tham gia trong sự kiện kèm điểm uy tín, tuổi, trạng thái
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/{eventId}/workers")
    public ResponseEntity<ApiResponse<List<ShiftAttendanceDto>>> getEventWorkers(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<ShiftAttendanceDto> workers = eoEventService.getEventWorkersList(userId, eventId);
        return ResponseEntity.ok(ApiResponse.ok(workers, "Lấy danh sách nhân sự sự kiện thành công!"));
    }

    /**
     * Tải ảnh minh họa sự kiện hoặc ca làm việc từ thiết bị local lên máy chủ
     * POST /api/v1/eo/events/upload-image
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping(value = "/upload-image", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadEventImage(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        String url = eoEventService.uploadEventImage(file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url), "Tải ảnh từ thiết bị lên thành công!"));
    }

    /**
     * Xem / phục vụ ảnh sự kiện & ca làm việc
     * GET /api/v1/eo/events/images/{filename}
     */
    @GetMapping("/images/{filename:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getEventImage(@PathVariable("filename") String filename) {
        org.springframework.core.io.Resource resource = eoEventService.loadEventImage(filename);
        String contentType = "image/jpeg";
        if (filename.toLowerCase().endsWith(".png")) contentType = "image/png";
        else if (filename.toLowerCase().endsWith(".webp")) contentType = "image/webp";
        else if (filename.toLowerCase().endsWith(".gif")) contentType = "image/gif";

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
