package com.cani.service;

import com.cani.dto.payment.ConfirmPaymentRequestDto;
import com.cani.dto.payment.DeclarePaymentRequestDto;
import com.cani.dto.payment.PaymentResponseDto;
import com.cani.entity.*;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.PaymentStatus;
import com.cani.exception.AppException;
import com.cani.repository.DisputeRepository;
import com.cani.repository.PaymentRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private ShiftRepository shiftRepository;

    @Autowired(required = false)
    private com.cani.repository.EventRepository eventRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    @org.springframework.context.annotation.Lazy
    @Autowired(required = false)
    private DisputeService disputeService;

    @Autowired(required = false)
    private DisputeRepository disputeRepository;

    @Autowired(required = false)
    private EmailService emailService;

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
            BigDecimal shiftAllowance = shift.getAllowance() != null ? shift.getAllowance() : BigDecimal.ZERO;
            BigDecimal emergencyBonus = assignment.getEmergencyBonus() != null ? assignment.getEmergencyBonus() : BigDecimal.ZERO;
            agreedAmount = shift.getHourlyWage().multiply(hours)
                    .add(shiftAllowance)
                    .add(emergencyBonus);
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
            throw new com.cani.exception.BadRequestException("Không đủ thông tin sự kiện/ca làm hoặc nhân sự để khởi tạo thanh toán.");
        }

        if (!payment.getEventOrganizer().getEoId().equals(eoId)) {
            throw new com.cani.exception.AppException("Bạn không có quyền khai báo thanh toán cho phân công này.", org.springframework.http.HttpStatus.FORBIDDEN);
        }

        if (assignment.getStatus() == com.cani.entity.enums.AssignmentStatus.CHECKED_IN
                && !Boolean.TRUE.equals(assignment.getEoConfirmed())) {
            throw new com.cani.exception.BadRequestException(
                    "Vui lòng bấm Duyệt công (xác nhận hoàn thành công việc) cho nhân sự trước khi khai báo thanh toán.");
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
            Shift shift = assignment.getShift();
            Event event = shift != null ? shift.getEvent() : null;
            String shiftLabel = describeShift(shift, event);
            String amountText = formatVnd(dto.getDeclaredAmount());
            String methodText = describeMethod(dto.getPaymentMethod());
            boolean hasProof = dto.getPaymentProofUrl() != null && !dto.getPaymentProofUrl().isBlank();
            try {
                notificationService.createAndSend(
                        payment.getWorker().getUser().getUserId(),
                        "Ban tổ chức đã thanh toán thù lao",
                        "Ban tổ chức đã khai báo trả " + amountText + " (" + methodText + ") cho ca " + shiftLabel
                                + (hasProof ? ", kèm chứng từ." : ".") + " Vui lòng kiểm tra và phản hồi số tiền đã nhận.",
                        NotificationType.PAYMENT,
                        "ASSIGNMENT",
                        dto.getAssignmentId(),
                        buildPaymentPayload(dto.getAssignmentId(), shift, event, "PAYMENT_DECLARED")
                );
            } catch (Exception ex) {
                log.warn("Không thể gửi thông báo thanh toán tới Worker: {}", ex.getMessage());
            }

            Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Ca làm việc", shiftLabel);
            details.put("Số tiền đã khai trả", amountText);
            details.put("Thù lao thỏa thuận", formatVnd(payment.getAgreedAmount()));
            details.put("Phương thức", methodText);
            details.put("Mã giao dịch", dto.getExternalRef());
            details.put("Thời điểm khai báo", formatInstant(saved.getDeclaredAt()));
            sendEmailAfterCommit(
                    payment.getWorker().getUser().getEmail(),
                    "[CANi] Ban tổ chức đã thanh toán thù lao ca " + shiftLabel,
                    "Ban tổ chức đã khai báo thanh toán thù lao",
                    "Xin chào " + safe(payment.getWorker().getFullName())
                            + ", Ban tổ chức vừa khai báo đã trả thù lao cho bạn. Vui lòng kiểm tra số tiền thực nhận "
                            + "và phản hồi trên ứng dụng CANi (Đã nhận đủ / Chỉ nhận một phần / Chưa nhận được).",
                    details,
                    dto.getPaymentProofUrl(),
                    "CANi ghi nhận thanh toán trực tiếp giữa Ban tổ chức và Nhân sự, không giữ tiền hộ. "
                            + "Nếu chưa nhận đủ, hãy phản hồi trên ứng dụng để hệ thống mở đối soát."
            );
        }

        return mapToDto(saved);
    }

    private static final java.time.ZoneId VN_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
    private static final java.time.format.DateTimeFormatter VN_DATE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(VN_ZONE);
    private static final String METHOD_BANK_TRANSFER = "BANK_TRANSFER";
    private static final String METHOD_CASH = "CASH";

    private String formatVnd(BigDecimal amount) {
        if (amount == null) return "—";
        java.text.NumberFormat nf = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("vi-VN"));
        return nf.format(amount.setScale(0, RoundingMode.HALF_UP)) + " VNĐ";
    }

    private String formatInstant(Instant instant) {
        return instant != null ? VN_DATE_TIME.format(instant) : "—";
    }

    private String describeMethod(String method) {
        if (METHOD_BANK_TRANSFER.equals(method)) return "Chuyển khoản ngân hàng";
        if (METHOD_CASH.equals(method)) return "Tiền mặt trực tiếp";
        return method != null ? method : "—";
    }

    private String describeShift(Shift shift, Event event) {
        String job = shift != null && shift.getJobType() != null ? shift.getJobType() : "ca làm";
        return event != null && event.getName() != null ? "\"" + job + "\" (" + event.getName() + ")" : "\"" + job + "\"";
    }

    private String safe(String value) {
        return value != null && !value.isBlank() ? value : "bạn";
    }

    private Map<String, String> buildPaymentPayload(UUID assignmentId, Shift shift, Event event, String type) {
        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("assignmentId", assignmentId.toString());
        payload.put("type", type);
        if (shift != null && shift.getShiftId() != null) payload.put("shiftId", shift.getShiftId().toString());
        if (event != null && event.getEventId() != null) payload.put("eventId", event.getEventId().toString());
        return payload;
    }

    /**
     * Gửi email sau khi transaction commit thành công: tránh gửi mail cho thao tác bị rollback
     * và không kéo dài thời gian giữ transaction vì chờ SMTP. Mọi dữ liệu đã được tính sẵn
     * thành chuỗi trước khi đăng ký callback để không chạm lazy entity ngoài session.
     */
    private void sendEmailAfterCommit(String toEmail, String subject, String heading, String intro,
                                      Map<String, String> details, String proofImage, String footerNote) {
        if (emailService == null) return;
        Runnable task = () -> emailService.sendPaymentEmail(toEmail, subject, heading, intro, details, proofImage, footerNote);
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            task.run();
                        }
                    });
        } else {
            task.run();
        }
    }

    /**
     * Worker xác nhận tình trạng nhận tiền sau khi EO khai báo
     */
    @Transactional
    public PaymentResponseDto confirmPayment(UUID workerId, UUID assignmentId, ConfirmPaymentRequestDto dto) {
        Payment payment = paymentRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new com.cani.exception.ResourceNotFoundException("Không tìm thấy thông tin thanh toán cho ca làm này."));

        if (!payment.getWorker().getWorkerId().equals(workerId)) {
            throw new com.cani.exception.AppException("Bạn không có quyền xác nhận thanh toán này.", org.springframework.http.HttpStatus.FORBIDDEN);
        }

        if (payment.getStatus() == PaymentStatus.NOT_DECLARED) {
            throw new com.cani.exception.BadRequestException(
                    "Ban tổ chức chưa khai báo thanh toán thù lao (Bước 4) cho ca làm việc này.");
        }

        if (assignmentRepository != null) {
            ShiftAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
            if (assignment != null && assignment.getStatus() != null) {
                if (assignment.getStatus() != com.cani.entity.enums.AssignmentStatus.CHECKED_IN
                        && assignment.getStatus() != com.cani.entity.enums.AssignmentStatus.COMPLETED) {
                    throw new com.cani.exception.BadRequestException(
                            "Xác nhận thanh toán chỉ mở sau khi đã check-in vào ca.");
                }
                if (assignment.getStatus() == com.cani.entity.enums.AssignmentStatus.CHECKED_IN
                        && !Boolean.TRUE.equals(assignment.getEoConfirmed())) {
                    throw new com.cani.exception.BadRequestException(
                            "Ban tổ chức cần Duyệt công hoàn thành (Bước 3) trước khi xác nhận thanh toán thù lao.");
                }
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
            throw new com.cani.exception.BadRequestException("Trạng thái xác nhận thanh toán không hợp lệ.");
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

        if (assignmentRepository != null) {
            assignmentRepository.findById(assignmentId).ifPresent(sa -> {
                Shift shift = sa.getShift();
                Event ev = shift != null ? shift.getEvent() : null;
                if (ev != null && ev.getEventOrganizer() != null && ev.getEventOrganizer().getUser() != null
                        && ev.getEventOrganizer().getUser().getUserId() != null) {
                    User eoUser = ev.getEventOrganizer().getUser();
                    UUID eoUserId = eoUser.getUserId();
                    String workerName = sa.getWorker() != null ? sa.getWorker().getFullName() : "Nhân sự";
                    String shiftLabel = describeShift(shift, ev);
                    String title;
                    String body;
                    String statusLabel;
                    NotificationType nType;
                    if ("RECEIVED_FULL".equals(normalizedConfirmStatus)) {
                        title = "Nhân sự xác nhận đã nhận đủ thù lao";
                        body = workerName + " đã xác nhận nhận đủ tiền công cho ca " + shiftLabel + ".";
                        statusLabel = "Đã nhận đủ";
                        nType = NotificationType.PAYMENT;
                    } else if ("RECEIVED_PARTIAL".equals(normalizedConfirmStatus)) {
                        title = "Nhân sự báo mới nhận một phần thù lao";
                        body = workerName + " phản hồi mới nhận một phần thù lao ca " + shiftLabel + ". Vui lòng kiểm tra lại.";
                        statusLabel = "Chỉ nhận một phần";
                        nType = NotificationType.PAYMENT;
                    } else {
                        title = "Khiếu nại chưa nhận được thù lao";
                        body = workerName + " báo chưa nhận được thù lao ca " + shiftLabel + ". Hệ thống đã mở hồ sơ tranh chấp.";
                        statusLabel = "Chưa nhận được (Mở tranh chấp)";
                        nType = NotificationType.DISPUTE;
                    }
                    if (notificationService != null) {
                        try {
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

                    Map<String, String> details = new java.util.LinkedHashMap<>();
                    details.put("Ca làm việc", shiftLabel);
                    details.put("Nhân sự", workerName);
                    details.put("Trạng thái phản hồi", statusLabel);
                    details.put("Số tiền đã khai trả", formatVnd(saved.getDeclaredAmount()));
                    details.put("Thù lao thỏa thuận", formatVnd(saved.getAgreedAmount()));
                    details.put("Phương thức", describeMethod(saved.getPaymentMethod()));
                    details.put("Mã giao dịch", saved.getExternalRef());
                    details.put("Ghi chú nhân sự", dto.getComment());
                    details.put("Thời điểm phản hồi", formatInstant(saved.getWorkerConfirmedAt()));
                    sendEmailAfterCommit(
                            eoUser.getEmail(),
                            "[CANi] " + title + " — " + shiftLabel,
                            title,
                            body,
                            details,
                            saved.getPaymentProofUrl(),
                            "Bạn có thể xem lại chứng từ và trạng thái đối soát trực tiếp trong màn Quản lý ca làm việc trên CANi Web."
                    );
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
        return mapListToDtos(paymentRepository.findByShiftId(shiftId));
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
        return mapListToDtos(payments);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getPaymentsByEo(UUID eoId) {
        return mapListToDtos(paymentRepository.findByEventOrganizer_EoIdOrderByCreatedAtDesc(eoId));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getPaymentsByWorker(UUID workerId) {
        return mapListToDtos(paymentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(workerId));
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

    private List<PaymentResponseDto> mapListToDtos(List<Payment> payments) {
        if (payments == null || payments.isEmpty()) {
            return List.of();
        }
        Map<UUID, Shift> shiftMap = new HashMap<>();
        if (shiftRepository != null) {
            Set<UUID> shiftIds = payments.stream()
                    .map(Payment::getShiftId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!shiftIds.isEmpty()) {
                shiftRepository.findAllById(shiftIds).forEach(s -> shiftMap.put(s.getShiftId(), s));
            }
        }

        Map<UUID, Event> eventMap = new HashMap<>();
        if (eventRepository != null) {
            Set<UUID> eventIds = payments.stream()
                    .map(Payment::getEventId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!eventIds.isEmpty()) {
                eventRepository.findAllById(eventIds).forEach(e -> eventMap.put(e.getEventId(), e));
            }
        }

        return payments.stream()
                .map(p -> {
                    Shift shift = p.getShiftId() != null ? shiftMap.get(p.getShiftId()) : null;
                    Event event = p.getEventId() != null ? eventMap.get(p.getEventId()) : null;
                    if (event == null && shift != null) {
                        event = shift.getEvent();
                    }
                    return mapToDto(p, shift, event);
                })
                .collect(Collectors.toList());
    }

    private PaymentResponseDto mapToDto(Payment p) {
        Shift shift = (shiftRepository != null && p.getShiftId() != null)
                ? shiftRepository.findById(p.getShiftId()).orElse(null)
                : null;
        Event event = (eventRepository != null && p.getEventId() != null)
                ? eventRepository.findById(p.getEventId()).orElse(null)
                : (shift != null ? shift.getEvent() : null);
        return mapToDto(p, shift, event);
    }

    private PaymentResponseDto mapToDto(Payment p, Shift shift, Event event) {
        Worker w = p.getWorker();
        String bankCode = w != null ? w.getBankCode() : null;
        String bankAccountNumber = w != null ? w.getBankAccountNumber() : null;
        String bankAccountName = w != null ? w.getBankAccountName() : null;
        if ((bankAccountName == null || bankAccountName.isBlank()) && w != null && w.getUser() != null) {
            bankAccountName = w.getUser().getFullName();
        }
        String workerName = (w != null && w.getFullName() != null && !w.getFullName().isBlank())
                ? w.getFullName()
                : (w != null && w.getUser() != null ? w.getUser().getFullName() : "");
        BigDecimal amount = p.getDeclaredAmount() != null ? p.getDeclaredAmount() : p.getAgreedAmount();
        String workerVietQrUrl = VietQrUtil.buildVietQrUrl(bankCode, bankAccountNumber, bankAccountName, amount);

        UUID disputeId = null;
        String disputeStatus = null;
        String disputeOutcome = null;
        String disputeRationale = null;
        String disputeResolution = null;
        Instant disputeResolvedAt = null;

        if (disputeRepository != null && p.getAssignmentId() != null) {
            List<Dispute> disputes = disputeRepository.findByAssignmentIdOrderByCreatedAtDesc(p.getAssignmentId());
            if (disputes != null && !disputes.isEmpty()) {
                Dispute d = disputes.get(0);
                disputeId = d.getDisputeId();
                disputeStatus = d.getStatus() != null ? d.getStatus().name() : null;
                disputeOutcome = d.getOutcome() != null ? d.getOutcome().name() : null;
                disputeRationale = d.getFinalDecisionRationale() != null ? d.getFinalDecisionRationale() : d.getDecisionRationale();
                disputeResolution = d.getResolution();
                disputeResolvedAt = d.getFinalDecidedAt() != null ? d.getFinalDecidedAt() : d.getResolvedAt();
            }
        }

        return PaymentResponseDto.builder()
                .paymentId(p.getPaymentId())
                .eventId(p.getEventId())
                .eventName(event != null ? event.getName() : null)
                .shiftId(p.getShiftId())
                .jobType(shift != null ? shift.getJobType() : null)
                .shiftStartTime(shift != null ? shift.getStartTime() : null)
                .shiftEndTime(shift != null ? shift.getEndTime() : null)
                .assignmentId(p.getAssignmentId())
                .eoId(p.getEventOrganizer() != null ? p.getEventOrganizer().getEoId() : null)
                .eoCompanyName(p.getEventOrganizer() != null ? p.getEventOrganizer().getCompanyName() : "")
                .workerId(w != null ? w.getWorkerId() : null)
                .workerName(workerName)
                .workerCccd(w != null ? w.getCccdNumber() : null)
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
                .bankAccountNumberMasked(w != null ? w.getBankAccountNumberMasked() : com.cani.security.crypto.AesGcmCryptoUtil.mask(bankAccountNumber))
                .bankAccountName(bankAccountName)
                .workerVietQrUrl(workerVietQrUrl)
                .disputeId(disputeId)
                .disputeStatus(disputeStatus)
                .disputeOutcome(disputeOutcome)
                .disputeRationale(disputeRationale)
                .disputeResolution(disputeResolution)
                .disputeResolvedAt(disputeResolvedAt)
                .build();
    }
}
