package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.dto.worker.JobProposalResponseDto;
import com.cani.dto.worker.ShiftAssignmentResponseDto;
import com.cani.dto.worker.WorkerOnlineStatusRequestDto;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.exception.BadRequestException;
import com.cani.service.WorkerJobService;
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
    public ResponseEntity<ApiResponse<com.cani.dto.worker.AttendanceCheckResponseDto>> checkin(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestBody com.cani.dto.worker.AttendanceCheckRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        com.cani.dto.worker.AttendanceCheckResponseDto response = workerJobService.checkinAssignment(userId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMessage()));
    }

    /**
     * Worker quét QR Check-out hoàn thành ca làm việc
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/assignments/{assignmentId}/checkout")
    public ResponseEntity<ApiResponse<com.cani.dto.worker.AttendanceCheckResponseDto>> checkout(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestBody com.cani.dto.worker.AttendanceCheckRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        com.cani.dto.worker.AttendanceCheckResponseDto response = workerJobService.checkoutAssignment(userId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMessage()));
    }

    /**
     * Quét và tìm kiếm các ca làm việc xung quanh vị trí Worker trên bản đồ (Job Radar Map)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/shifts/nearby")
    public ResponseEntity<ApiResponse<List<com.cani.dto.worker.NearbyShiftResponseDto>>> getNearbyShifts(
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "radiusKm", required = false, defaultValue = "15.0") Double radiusKm
    ) {
        List<com.cani.dto.worker.NearbyShiftResponseDto> shifts = workerJobService.getNearbyShifts(lat, lng, radiusKm);
        return ResponseEntity.ok(ApiResponse.ok(shifts, "Quét tìm ca làm việc xung quanh thành công (Tìm thấy " + shifts.size() + " ca)"));
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
