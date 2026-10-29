package com.cani.controller;

import com.cani.dto.payment.ConfirmPaymentRequestDto;
import com.cani.dto.payment.DeclarePaymentRequestDto;
import com.cani.dto.payment.PaymentResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.WorkerRepository;
import com.cani.service.DirectPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class DirectPaymentController {

    private final DirectPaymentService directPaymentService;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final WorkerRepository workerRepository;

    /**
     * EO khai báo thanh toán trực tiếp cho Worker (Mục 11 SRS)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/eo/declare")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> declarePayment(
            @Valid @RequestBody DeclarePaymentRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new IllegalStateException("Tài khoản chưa đăng ký hồ sơ Nhà tổ chức sự kiện (EO)"));

        PaymentResponseDto response = directPaymentService.declarePayment(eo.getEoId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Khai báo thanh toán thành công, đang chờ Worker xác nhận"));
    }

    /**
     * Worker xác nhận tình trạng nhận tiền công trực tiếp (Mục 12 SRS)
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/worker/confirm/{assignmentId}")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> confirmPayment(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody ConfirmPaymentRequestDto request,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new IllegalStateException("Tài khoản chưa đăng ký hồ sơ Worker"));

        PaymentResponseDto response = directPaymentService.confirmPayment(worker.getWorkerId(), assignmentId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Xác nhận trạng thái nhận tiền thành công"));
    }

    /**
     * Lấy danh sách thanh toán theo Ca làm việc (Dành cho EO)
     */
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/shifts/{shiftId}")
    public ResponseEntity<ApiResponse<List<PaymentResponseDto>>> getShiftPayments(
            @PathVariable UUID shiftId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication) {
        boolean isAdmin = isAdmin(authentication);
        UUID callerEoId = null;
        if (!isAdmin) {
            UUID userId = resolveUserId(authentication, explicitUserId);
            EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(userId)
                    .orElseThrow(() -> new IllegalStateException("Tài khoản chưa đăng ký hồ sơ Nhà tổ chức sự kiện (EO)"));
            callerEoId = eo.getEoId();
        }
        List<PaymentResponseDto> payments = directPaymentService.getPaymentsByShift(shiftId, callerEoId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok(payments, "Lấy danh sách thanh toán của ca thành công"));
    }

    /**
     * Lấy danh sách lịch sử thanh toán của Worker
     */
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/worker/history")
    public ResponseEntity<ApiResponse<List<PaymentResponseDto>>> getWorkerPaymentHistory(
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication) {
        UUID userId = resolveUserId(authentication, explicitUserId);
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new IllegalStateException("Tài khoản chưa đăng ký hồ sơ Worker"));

        List<PaymentResponseDto> payments = directPaymentService.getPaymentsByWorker(worker.getWorkerId());
        return ResponseEntity.ok(ApiResponse.ok(payments, "Lấy lịch sử thanh toán thành công"));
    }

    /**
     * Xem trạng thái thanh toán của một Assignment
     */
    @PreAuthorize("hasAnyRole('WORKER', 'EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/assignments/{assignmentId}")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> getAssignmentPayment(
            @PathVariable UUID assignmentId,
            @RequestParam(value = "userId", required = false) UUID explicitUserId,
            Authentication authentication) {
        boolean isAdmin = isAdmin(authentication);
        UUID callerId = isAdmin ? null : resolveUserId(authentication, explicitUserId);
        PaymentResponseDto payment = directPaymentService.getPaymentByAssignment(assignmentId, callerId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok(payment, "Lấy thông tin thanh toán thành công"));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
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
