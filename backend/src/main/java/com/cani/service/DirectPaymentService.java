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
import com.cani.util.VietQrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.dao.DataIntegrityViolationException;
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
    private com.cani.repository.EventRepository eventRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    @org.springframework.context.annotation.Lazy
    @Autowired(required = false)
    private DisputeService disputeService;

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

        // Tính thù lao dự kiến chuẩn xác qua BigDecimal (chống làm tròn sai số qua double)
        BigDecimal agreedAmount = BigDecimal.ZERO;
        if (shift.getHourlyWage() != null && shift.getStartTime() != null && shift.getEndTime() != null) {
            long minutes = Math.max(60, Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes());
            BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            agreedAmount = shift.getHourlyWage().multiply(hours)
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
                .orElseGet(() -> {
                    try {
                        return createInitialPayment(assignment);
                    } catch (DataIntegrityViolationException ex) {
                        return paymentRepository.findByAssignmentId(dto.getAssignmentId())
                                .orElseThrow(() -> ex);
                    }
                });

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
        updateEventTotalPaid(saved.getEventId());
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

        if (assignmentRepository != null) {
            ShiftAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
            if (assignment != null && assignment.getStatus() != null
                    && assignment.getStatus() != com.cani.entity.enums.AssignmentStatus.CHECKED_IN
                    && assignment.getStatus() != com.cani.entity.enums.AssignmentStatus.COMPLETED) {
                throw new com.cani.exception.BadRequestException(
                        "Xác nhận thanh toán chỉ mở sau khi đã check-in vào ca (trạng thái hiện tại: " + assignment.getStatus() + ")");
            }
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
        updateEventTotalPaid(saved.getEventId());

        if ("NOT_RECEIVED".equals(normalizedConfirmStatus) && disputeService != null) {
            UUID workerUserId = (payment.getWorker() != null && payment.getWorker().getUser() != null)
                    ? payment.getWorker().getUser().getUserId()
                    : workerId;
            String description = (dto.getComment() != null && !dto.getComment().isBlank())
                    ? dto.getComment().trim()
                    : "Worker xác nhận chưa nhận được thù lao khi kết thúc ca làm việc.";
            try {
                com.cani.dto.dispute.CreateDisputeRequestDto disputeReq = com.cani.dto.dispute.CreateDisputeRequestDto.builder()
                        .assignmentId(assignmentId)
                        .type(com.cani.entity.enums.DisputeType.PAYMENT)
                        .title("Khiếu nại chưa nhận đủ thù lao")
                        .description(description)
                        .build();
                disputeService.createDispute(workerUserId, disputeReq);
                log.info("Đã tự động tạo Dispute thanh toán cho Assignment {}", assignmentId);
            } catch (Exception ex) {
                log.warn("Không tạo thêm Dispute mới cho Assignment {} (có thể đã tồn tại tranh chấp mở): {}", assignmentId, ex.getMessage());
            }
        }

        log.info("Worker {} xác nhận thanh toán: {} cho Assignment {}", workerId, normalizedConfirmStatus, assignmentId);

        if (notificationService != null && assignmentRepository != null) {
            assignmentRepository.findById(assignmentId).ifPresent(sa -> {
                Shift shift = sa.getShift();
                Event ev = shift != null ? shift.getEvent() : null;
                if (ev != null && ev.getEventOrganizer() != null && ev.getEventOrganizer().getUser() != null
                        && ev.getEventOrganizer().getUser().getUserId() != null) {
                    try {
                        UUID eoUserId = ev.getEventOrganizer().getUser().getUserId();
                        String workerName = sa.getWorker() != null ? sa.getWorker().getFullName() : "Nhân sự";
                        String title;
                        String body;
                        NotificationType nType;
                        if ("RECEIVED_FULL".equals(normalizedConfirmStatus)) {
                            title = "Nhân sự xác nhận đã nhận đủ thù lao";
                            body = workerName + " đã xác nhận nhận đủ tiền công cho ca \"" + shift.getJobType() + "\" (" + ev.getName() + ").";
                            nType = NotificationType.PAYMENT;
                        } else if ("RECEIVED_PARTIAL".equals(normalizedConfirmStatus)) {
                            title = "Nhân sự báo mới nhận một phần thù lao";
                            body = workerName + " phản hồi mới nhận một phần thù lao ca \"" + shift.getJobType() + "\" (" + ev.getName() + "). Vui lòng kiểm tra lại.";
                            nType = NotificationType.PAYMENT;
                        } else {
                            title = "Khiếu nại chưa nhận được thù lao";
                            body = workerName + " báo chưa nhận được thù lao ca \"" + shift.getJobType() + "\" (" + ev.getName() + "). Hệ thống đã mở hồ sơ tranh chấp.";
                            nType = NotificationType.DISPUTE;
                        }
                        notificationService.createAndSend(
                                eoUserId,
                                title,
                                body,
                                nType,
                                "SHIFT",
                                shift.getShiftId(),
                                Map.of(
                                        "eventId", ev.getEventId().toString(),
                                        "shiftId", shift.getShiftId().toString(),
                                        "assignmentId", assignmentId.toString()
                                )
                        );
                    } catch (Exception ex) {
                        log.warn("Không thể gửi thông báo xác nhận thanh toán cho EO: {}", ex.getMessage());
                    }
                }
            });
        }

        return mapToDto(saved);
    }

    private void updateEventTotalPaid(UUID eventId) {
        if (eventId == null || eventRepository == null) return;
        eventRepository.findById(eventId).ifPresent(event -> {
            List<Payment> payments = paymentRepository.findByEventId(eventId);
            if (payments != null) {
                BigDecimal sum = payments.stream()
                        .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED_FULL
                                || p.getStatus() == PaymentStatus.CONFIRMED_PARTIAL
                                || p.getStatus() == PaymentStatus.DECLARED_PENDING_WORKER)
                        .map(p -> p.getDeclaredAmount() != null ? p.getDeclaredAmount() : p.getAgreedAmount())
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                event.setTotalPaid(sum);
                eventRepository.save(event);
            }
        });
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
        Worker w = p.getWorker();
        String bankCode = w != null ? w.getBankCode() : null;
        String bankAccountNumber = w != null ? w.getBankAccountNumber() : null;
        String bankAccountName = w != null ? w.getBankAccountName() : null;
        if ((bankAccountName == null || bankAccountName.isBlank()) && w != null && w.getUser() != null) {
            bankAccountName = w.getUser().getFullName();
        }
        BigDecimal amount = p.getDeclaredAmount() != null ? p.getDeclaredAmount() : p.getAgreedAmount();
        String workerVietQrUrl = VietQrUtil.buildVietQrUrl(bankCode, bankAccountNumber, bankAccountName, amount);

        return PaymentResponseDto.builder()
                .paymentId(p.getPaymentId())
                .eventId(p.getEventId())
                .shiftId(p.getShiftId())
                .assignmentId(p.getAssignmentId())
                .eoId(p.getEventOrganizer() != null ? p.getEventOrganizer().getEoId() : null)
                .eoCompanyName(p.getEventOrganizer() != null ? p.getEventOrganizer().getCompanyName() : "")
                .workerId(w != null ? w.getWorkerId() : null)
                .workerName(w != null && w.getUser() != null ? w.getUser().getFullName() : "")
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
                .bankCode(bankCode)
                .bankAccountNumber(bankAccountNumber)
                .bankAccountName(bankAccountName)
                .workerVietQrUrl(workerVietQrUrl)
                .build();
    }
}
