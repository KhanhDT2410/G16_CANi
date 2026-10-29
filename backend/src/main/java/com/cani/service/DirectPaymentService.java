package com.cani.service;

import com.cani.dto.payment.ConfirmPaymentRequestDto;
import com.cani.dto.payment.DeclarePaymentRequestDto;
import com.cani.dto.payment.PaymentResponseDto;
import com.cani.entity.*;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.PaymentStatus;
import com.cani.exception.AppException;
import com.cani.repository.PaymentRepository;
import com.cani.repository.ShiftAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service quản lý thanh toán trực tiếp (Direct Payment) giữa EO và Worker.
 * Tuân thủ tuyệt đối quy định Non-Escrow (Mục 11 & 12 trong Báo cáo tổng quan dự án):
 * CANi là nền tảng điều phối, không giữ tiền cọc ký quỹ (Escrow) của EO và Worker.
 * EO chi trả tiền thù lao trực tiếp cho Worker (tiền mặt / chuyển khoản ngân hàng ngoài).
 * Nền tảng ghi nhận khai báo đối soát (Payment Declaration) và hỗ trợ xác nhận/khiếu nại (Dispute).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DirectPaymentService {

    private final PaymentRepository paymentRepository;
    private final ShiftAssignmentRepository assignmentRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    /**
     * Khởi tạo bản ghi Payment khi Worker được gán vào ca làm việc
     */
    @Transactional
    public Payment createInitialPayment(ShiftAssignment assignment) {
        Shift shift = assignment.getShift();
        Event event = shift != null ? shift.getEvent() : null;
        EventOrganizer eo = event != null ? event.getEventOrganizer() : null;
        Worker worker = assignment.getWorker();

        if (eo == null || worker == null || shift == null) {
            log.warn("Không đủ thông tin để tạo bản ghi Payment cho assignment {}", assignment.getAssignmentId());
            return null;
        }

        // Tính thù lao dự kiến
        BigDecimal agreedAmount = BigDecimal.ZERO;
        if (shift.getHourlyWage() != null && shift.getStartTime() != null && shift.getEndTime() != null) {
            long minutes = Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes();
            double hours = Math.max(1.0, minutes / 60.0);
            agreedAmount = shift.getHourlyWage().multiply(BigDecimal.valueOf(hours))
                    .add(shift.getAllowance() != null ? shift.getAllowance() : BigDecimal.ZERO);
        }

        Payment payment = Payment.builder()
                .eventId(event.getEventId())
                .shiftId(shift.getShiftId())
                .assignmentId(assignment.getAssignmentId())
                .eventOrganizer(eo)
                .worker(worker)
                .agreedAmount(agreedAmount)
                .status(PaymentStatus.NOT_DECLARED)
                .build();

        return paymentRepository.save(payment);
    }

    /**
     * EO khai báo đã thanh toán trực tiếp cho Worker sau khi hoàn thành ca làm
     */
    @Transactional
    public PaymentResponseDto declarePayment(UUID eoId, DeclarePaymentRequestDto dto) {
        ShiftAssignment assignment = assignmentRepository.findById(dto.getAssignmentId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phân công công việc: " + dto.getAssignmentId()));

        Payment payment = paymentRepository.findByAssignmentId(dto.getAssignmentId())
                .orElseGet(() -> createInitialPayment(assignment));

        if (payment == null || payment.getEventOrganizer() == null) {
            throw new IllegalArgumentException("Không đủ thông tin sự kiện/ca làm hoặc Worker để khởi tạo thanh toán cho phân công: " + dto.getAssignmentId());
        }

        if (!payment.getEventOrganizer().getEoId().equals(eoId)) {
            throw new IllegalStateException("Bạn không có quyền khai báo thanh toán cho phân công này");
        }

        payment.setDeclaredAmount(dto.getDeclaredAmount());
        payment.setPaymentMethod(dto.getPaymentMethod());
        payment.setExternalRef(dto.getExternalRef());
        payment.setPaymentProofUrl(dto.getPaymentProofUrl());
        payment.setDeclaredAt(Instant.now());
        payment.setStatus(PaymentStatus.DECLARED_PENDING_WORKER);

        Payment saved = paymentRepository.save(payment);
        log.info("EO {} đã khai báo thanh toán {} VND cho Worker {} (Assignment {})",
                eoId, dto.getDeclaredAmount(), payment.getWorker().getWorkerId(), dto.getAssignmentId());

        if (notificationService != null && payment.getWorker() != null
                && payment.getWorker().getUser() != null && payment.getWorker().getUser().getUserId() != null) {
            try {
                notificationService.createAndSend(
                        payment.getWorker().getUser().getUserId(),
                        "Thù lao ca làm đã được khai báo",
                        "Ban tổ chức đã khai báo thanh toán " + dto.getDeclaredAmount() + " VNĐ. Vui lòng kiểm tra và xác nhận!",
                        NotificationType.PAYMENT,
                        "ASSIGNMENT",
                        dto.getAssignmentId(),
                        Map.of("assignmentId", dto.getAssignmentId().toString(), "type", "PAYMENT_DECLARED")
                );
            } catch (Exception ex) {
                log.warn("Không thể gửi thông báo thanh toán tới Worker: {}", ex.getMessage());
            }
        }

        return mapToDto(saved);
    }

    /**
     * Worker xác nhận tình trạng nhận tiền sau khi EO khai báo
     */
    @Transactional
    public PaymentResponseDto confirmPayment(UUID workerId, UUID assignmentId, ConfirmPaymentRequestDto dto) {
        Payment payment = paymentRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản ghi thanh toán cho assignment: " + assignmentId));

        if (!payment.getWorker().getWorkerId().equals(workerId)) {
            throw new IllegalStateException("Bạn không có quyền xác nhận thanh toán này");
        }

        String rawStatus = dto.getConfirmStatus() != null ? dto.getConfirmStatus().trim().toUpperCase() : "";
        String normalizedConfirmStatus;
        if ("RECEIVED_FULL".equals(rawStatus) || "CONFIRMED_FULL".equals(rawStatus)) {
            payment.setStatus(PaymentStatus.CONFIRMED_FULL);
            normalizedConfirmStatus = "RECEIVED_FULL";
        } else if ("RECEIVED_PARTIAL".equals(rawStatus) || "CONFIRMED_PARTIAL".equals(rawStatus)) {
            payment.setStatus(PaymentStatus.CONFIRMED_PARTIAL);
            normalizedConfirmStatus = "RECEIVED_PARTIAL";
        } else if ("NOT_RECEIVED".equals(rawStatus) || "DISPUTED".equals(rawStatus)) {
            payment.setStatus(PaymentStatus.DISPUTED);
            normalizedConfirmStatus = "NOT_RECEIVED";
        } else {
            throw new IllegalArgumentException("Trạng thái xác nhận không hợp lệ. Chỉ chấp nhận: RECEIVED_FULL (hoặc CONFIRMED_FULL), RECEIVED_PARTIAL, NOT_RECEIVED");
        }

        payment.setWorkerConfirmedAt(Instant.now());
        payment.setWorkerConfirmStatus(normalizedConfirmStatus);
        payment.setWorkerComment(dto.getComment());

        Payment saved = paymentRepository.save(payment);
        log.info("Worker {} xác nhận thanh toán: {} cho Assignment {}", workerId, normalizedConfirmStatus, assignmentId);

        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getPaymentsByShift(UUID shiftId) {
        return paymentRepository.findByShiftId(shiftId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getPaymentsByShift(UUID shiftId, UUID callerEoId, boolean isAdmin) {
        List<Payment> payments = paymentRepository.findByShiftId(shiftId);
        if (!isAdmin) {
            boolean unauthorized = payments.stream().anyMatch(p ->
                    p.getEventOrganizer() == null || !p.getEventOrganizer().getEoId().equals(callerEoId));
            if (unauthorized) {
                throw new AppException("Không tìm thấy thông tin thanh toán của ca làm việc", HttpStatus.NOT_FOUND);
            }
        }
        return payments.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getPaymentsByWorker(UUID workerId) {
        return paymentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(workerId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentByAssignment(UUID assignmentId) {
        return paymentRepository.findByAssignmentId(assignmentId)
                .map(this::mapToDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentByAssignment(UUID assignmentId, UUID callerId, boolean isAdmin) {
        Payment payment = paymentRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new AppException("Không tìm thấy thông tin thanh toán của ca làm việc", HttpStatus.NOT_FOUND));

        if (!isAdmin) {
            boolean isWorkerOwner = payment.getWorker() != null && (
                    (payment.getWorker().getUser() != null && callerId != null && callerId.equals(payment.getWorker().getUser().getUserId()))
                    || (callerId != null && callerId.equals(payment.getWorker().getWorkerId()))
            );
            boolean isEoOwner = payment.getEventOrganizer() != null && (
                    (payment.getEventOrganizer().getUser() != null && callerId != null && callerId.equals(payment.getEventOrganizer().getUser().getUserId()))
                    || (callerId != null && callerId.equals(payment.getEventOrganizer().getEoId()))
            );
            if (!isWorkerOwner && !isEoOwner) {
                throw new AppException("Không tìm thấy thông tin thanh toán của ca làm việc", HttpStatus.NOT_FOUND);
            }
        }

        return mapToDto(payment);
    }

    private PaymentResponseDto mapToDto(Payment p) {
        return PaymentResponseDto.builder()
                .paymentId(p.getPaymentId())
                .eventId(p.getEventId())
                .shiftId(p.getShiftId())
                .assignmentId(p.getAssignmentId())
                .eoId(p.getEventOrganizer() != null ? p.getEventOrganizer().getEoId() : null)
                .eoCompanyName(p.getEventOrganizer() != null ? p.getEventOrganizer().getCompanyName() : "")
                .workerId(p.getWorker() != null ? p.getWorker().getWorkerId() : null)
                .workerName(p.getWorker() != null && p.getWorker().getUser() != null ? p.getWorker().getUser().getFullName() : "")
                .agreedAmount(p.getAgreedAmount())
                .declaredAmount(p.getDeclaredAmount())
                .status(p.getStatus())
                .paymentMethod(p.getPaymentMethod())
                .declaredAt(p.getDeclaredAt())
                .externalRef(p.getExternalRef())
                .paymentProofUrl(p.getPaymentProofUrl())
                .workerConfirmedAt(p.getWorkerConfirmedAt())
                .workerConfirmStatus(p.getWorkerConfirmStatus())
                .workerComment(p.getWorkerComment())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
