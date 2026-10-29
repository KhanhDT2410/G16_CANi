package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.dto.worker.*;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.service.WorkerJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/workers")
@RequiredArgsConstructor
public class WorkerJobController {

    private final WorkerJobService workerJobService;

    /**
     * Worker bật / tắt nút "Bắt đầu kiếm việc" (Online Status)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/status")
    public ResponseEntity<ApiResponse<Boolean>> setOnlineStatus(
            @Valid @RequestBody WorkerOnlineStatusRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        Worker worker = workerJobService.setOnlineStatus(userId, request);
        String msg = Boolean.TRUE.equals(worker.getIsOnline())
                ? "Đã bật chế độ 'Bắt đầu kiếm việc'. Hệ thống sẽ tự động ghép nối và gửi đề xuất việc làm phù hợp cho bạn."
                : "Đã tắt chế độ kiếm việc. Bạn sẽ tạm thời không nhận thêm đề xuất mới.";
        return ResponseEntity.ok(ApiResponse.ok(worker.getIsOnline(), msg));
    }

    /**
     * Danh sách các đề xuất việc làm đang chờ Worker phản hồi (Proposals)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/proposals")
    public ResponseEntity<ApiResponse<List<JobProposalResponseDto>>> getMyProposals(
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        List<JobProposalResponseDto> proposals = workerJobService.getMyProposals(userId);
        return ResponseEntity.ok(ApiResponse.ok(proposals, "Lấy danh sách đề xuất việc làm thành công"));
    }

    /**
     * Worker chấp nhận lời mời làm việc (Accept Job Proposal)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/proposals/{proposalId}/accept")
    public ResponseEntity<ApiResponse<ShiftAssignmentResponseDto>> acceptProposal(
            @PathVariable("proposalId") UUID proposalId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        ShiftAssignmentResponseDto assignment = workerJobService.acceptProposal(userId, proposalId);
        return ResponseEntity.ok(ApiResponse.ok(assignment, "Chúc mừng! Bạn đã nhận thành công ca làm việc. Vui lòng có mặt đúng giờ theo yêu cầu của sự kiện."));
    }

    /**
     * Worker từ chối lời mời làm việc (Reject Job Proposal)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/proposals/{proposalId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectProposal(
            @PathVariable("proposalId") UUID proposalId,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        workerJobService.rejectProposal(userId, proposalId, reason);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã từ chối đề xuất việc làm thành công."));
    }

    /**
     * Lịch làm việc của tôi (Các ca làm việc đã xác nhận)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/assignments")
    public ResponseEntity<ApiResponse<List<ShiftAssignmentResponseDto>>> getMyAssignments(
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        List<ShiftAssignmentResponseDto> assignments = workerJobService.getMyAssignments(userId);
        return ResponseEntity.ok(ApiResponse.ok(assignments, "Lấy lịch làm việc thành công"));
    }

    /**
     * Worker quét QR Check-in điểm danh vào ca làm việc
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/assignments/{assignmentId}/checkin")
    public ResponseEntity<ApiResponse<AttendanceCheckResponseDto>> checkin(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestBody AttendanceCheckRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        AttendanceCheckResponseDto response = workerJobService.checkinAssignment(userId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMessage()));
    }

    /**
     * Worker quét QR Check-out hoàn thành ca làm việc
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/assignments/{assignmentId}/checkout")
    public ResponseEntity<ApiResponse<AttendanceCheckResponseDto>> checkout(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestBody AttendanceCheckRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        AttendanceCheckResponseDto response = workerJobService.checkoutAssignment(userId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMessage()));
    }

    /**
     * Quét và tìm kiếm các ca làm việc xung quanh vị trí Worker trên bản đồ (Job Radar Map)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/shifts/nearby")
    public ResponseEntity<ApiResponse<List<NearbyShiftResponseDto>>> getNearbyShifts(
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "radiusKm", required = false, defaultValue = "15.0") Double radiusKm
    ) {
        List<NearbyShiftResponseDto> shifts = workerJobService.getNearbyShifts(lat, lng, radiusKm);
        return ResponseEntity.ok(ApiResponse.ok(shifts, "Quét tìm ca làm việc xung quanh thành công (Tìm thấy " + shifts.size() + " ca)"));
    }

    /**
     * Worker ứng tuyển trực tiếp vào ca làm việc từ Job Radar Map
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/shifts/{shiftId}/apply")
    public ResponseEntity<ApiResponse<ShiftAssignmentResponseDto>> applyForShift(
            @PathVariable("shiftId") UUID shiftId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        ShiftAssignmentResponseDto assignment = workerJobService.applyForShift(userId, shiftId);
        return ResponseEntity.ok(ApiResponse.ok(assignment, "Ứng tuyển thành công! Ca làm đã được thêm vào Lịch làm việc của bạn."));
    }

    /**
     * UC-25: Lấy danh sách lịch rảnh tuần của Worker
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<List<WorkerAvailabilityDto>>> getAvailability(
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        List<WorkerAvailabilityDto> list = workerJobService.getWorkerAvailability(userId);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách lịch rảnh thành công"));
    }

    /**
     * UC-25: Cập nhật lịch rảnh tuần của Worker (Atomic Replace)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PutMapping("/availability")
    public ResponseEntity<ApiResponse<List<WorkerAvailabilityDto>>> updateAvailability(
            @Valid @RequestBody UpdateAvailabilityRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        List<WorkerAvailabilityDto> list = workerJobService.updateWorkerAvailability(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(list, "Cập nhật lịch rảnh tuần thành công"));
    }

    /**
     * UC-26 & UC-34: Lấy cài đặt bán kính tìm việc và mức lương mong muốn
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<WorkerPreferencesDto>> getPreferences(
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        WorkerPreferencesDto prefs = workerJobService.getWorkerPreferences(userId);
        return ResponseEntity.ok(ApiResponse.ok(prefs, "Lấy thông tin tiêu chí tìm việc thành công"));
    }

    /**
     * UC-26 & UC-34: Cập nhật tiêu chí tìm việc (Bán kính, lương, địa chỉ)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<WorkerPreferencesDto>> updatePreferences(
            @Valid @RequestBody WorkerPreferencesDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        WorkerPreferencesDto prefs = workerJobService.updateWorkerPreferences(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(prefs, "Cập nhật tiêu chí tìm việc thành công"));
    }

    /**
     * UC-53: Worker hủy ca làm việc đã nhận (Kèm trừ điểm uy tín theo quy định)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/assignments/{assignmentId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelAssignment(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        workerJobService.cancelAssignment(userId, assignmentId, reason);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã hủy nhận ca làm việc thành công. Điểm uy tín của bạn đã được cập nhật."));
    }

    /**
     * UC-45: Lấy checklist đầu việc của ca làm
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/assignments/{assignmentId}/checklist")
    public ResponseEntity<ApiResponse<List<ShiftChecklistItemDto>>> getShiftChecklist(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        List<ShiftChecklistItemDto> items = workerJobService.getShiftChecklist(userId, assignmentId);
        return ResponseEntity.ok(ApiResponse.ok(items, "Lấy danh sách checklist công việc thành công"));
    }

    /**
     * UC-45: Cập nhật tiến độ checklist đầu việc của ca làm
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PutMapping("/assignments/{assignmentId}/checklist")
    public ResponseEntity<ApiResponse<List<ShiftChecklistItemDto>>> updateShiftChecklist(
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody UpdateChecklistRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        List<ShiftChecklistItemDto> items = workerJobService.updateShiftChecklist(userId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.ok(items, "Cập nhật checklist công việc thành công"));
    }

    /**
     * UC-69: Worker đánh giá Ban tổ chức (EO) sau khi hoàn thành ca
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/assignments/{assignmentId}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponseDto>> createReview(
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody CreateReviewRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        ReviewResponseDto review = workerJobService.createReview(userId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.ok(review, "Gửi đánh giá thành công! Cảm ơn bạn đã phản hồi."));
    }

    /**
     * UC-69: Xem đánh giá đã gửi cho ca làm
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/assignments/{assignmentId}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponseDto>> getAssignmentReview(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        return workerJobService.getAssignmentReview(userId, assignmentId)
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r, "Lấy đánh giá thành công")))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok(null, "Chưa có đánh giá cho ca này")));
    }

    /**
     * UC-60 / UC-64: Thống kê thu nhập và ví làm việc cá nhân
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/income-summary")
    public ResponseEntity<ApiResponse<WorkerIncomeSummaryDto>> getIncomeSummary(
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        WorkerIncomeSummaryDto summary = workerJobService.getIncomeSummary(userId);
        return ResponseEntity.ok(ApiResponse.ok(summary, "Lấy thống kê thu nhập thành công"));
    }

    private UUID resolveUserId(Authentication authentication, UUID explicitUserId) {
        UUID callerId = null;
        if (authentication != null && authentication.getPrincipal() instanceof User u) {
            callerId = u.getUserId();
        } else if (authentication != null && authentication.getName() != null) {
            try {
                callerId = UUID.fromString(authentication.getName());
            } catch (Exception ignored) {}
        }
        if (callerId == null) {
            throw new BadRequestException("Vui lòng đăng nhập hoặc cung cấp userId hợp lệ");
        }
        // An toàn: chặn IDOR — chỉ được thao tác với đúng userId của người đang đăng nhập.
        if (explicitUserId != null && !explicitUserId.equals(callerId)) {
            throw new AppException("Không có quyền thao tác thay cho người dùng khác", HttpStatus.FORBIDDEN);
        }
        return callerId;
    }
}
