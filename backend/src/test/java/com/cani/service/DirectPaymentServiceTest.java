package com.cani.service;

import com.cani.dto.payment.ConfirmPaymentRequestDto;
import com.cani.dto.payment.DeclarePaymentRequestDto;
import com.cani.dto.payment.PaymentResponseDto;
import com.cani.entity.*;
import com.cani.entity.enums.PaymentStatus;
import com.cani.exception.AppException;
import com.cani.repository.PaymentRepository;
import com.cani.repository.ShiftAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectPaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ShiftAssignmentRepository assignmentRepository;

    @InjectMocks
    private DirectPaymentService directPaymentService;

    private EventOrganizer eo;
    private Worker worker;
    private Shift shift;
    private ShiftAssignment assignment;
    private Payment initialPayment;

    @BeforeEach
    void setUp() {
        eo = EventOrganizer.builder()
                .eoId(UUID.randomUUID())
                .companyName("Công ty Sự kiện Sao Mai")
                .build();

        Event event = Event.builder()
                .eventId(UUID.randomUUID())
                .name("Triển lãm Tech Fair 2026")
                .eventOrganizer(eo)
                .build();

        worker = Worker.builder()
                .workerId(UUID.randomUUID())
                .fullName("Nguyễn Văn An")
                .build();

        shift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(event)
                .jobType("Lễ tân (Receptionist)")
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS))
                .hourlyWage(new BigDecimal("50000"))
                .allowance(new BigDecimal("20000"))
                .build();

        assignment = ShiftAssignment.builder()
                .assignmentId(UUID.randomUUID())
                .shift(shift)
                .worker(worker)
                .build();

        initialPayment = Payment.builder()
                .paymentId(UUID.randomUUID())
                .eventId(event.getEventId())
                .shiftId(shift.getShiftId())
                .assignmentId(assignment.getAssignmentId())
                .eventOrganizer(eo)
                .worker(worker)
                .agreedAmount(new BigDecimal("220000"))
                .status(PaymentStatus.NOT_DECLARED)
                .build();
    }

    @Test
    @DisplayName("Khởi tạo bản ghi Payment thành công khi nhận ca làm việc")
    void shouldCreateInitialPaymentSuccessfully() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = directPaymentService.createInitialPayment(assignment);

        assertThat(result).isNotNull();
        assertThat(result.getAgreedAmount()).isEqualByComparingTo(new BigDecimal("220000"));
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.NOT_DECLARED);
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("EO khai báo đã thanh toán trực tiếp cho Worker thành công")
    void shouldDeclarePaymentSuccessfully() {
        DeclarePaymentRequestDto request = DeclarePaymentRequestDto.builder()
                .assignmentId(assignment.getAssignmentId())
                .declaredAmount(new BigDecimal("220000"))
                .paymentMethod("BANK_TRANSFER")
                .externalRef("VCB123456789")
                .paymentProofUrl("https://storage.cani.vn/proofs/bill.jpg")
                .build();

        when(assignmentRepository.findById(assignment.getAssignmentId())).thenReturn(Optional.of(assignment));
        when(paymentRepository.findByAssignmentId(assignment.getAssignmentId())).thenReturn(Optional.of(initialPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponseDto response = directPaymentService.declarePayment(eo.getEoId(), request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLARED_PENDING_WORKER);
        assertThat(response.getDeclaredAmount()).isEqualByComparingTo(new BigDecimal("220000"));
        assertThat(response.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
        assertThat(response.getExternalRef()).isEqualTo("VCB123456789");
    }

    @Test
    @DisplayName("Worker xác nhận đã nhận đủ tiền thành công")
    void shouldConfirmPaymentReceivedFull() {
        initialPayment.setStatus(PaymentStatus.DECLARED_PENDING_WORKER);
        initialPayment.setDeclaredAmount(new BigDecimal("220000"));

        ConfirmPaymentRequestDto request = ConfirmPaymentRequestDto.builder()
                .confirmStatus("RECEIVED_FULL")
                .comment("Đã nhận đủ tiền từ EO, cảm ơn.")
                .build();

        when(paymentRepository.findByAssignmentId(assignment.getAssignmentId())).thenReturn(Optional.of(initialPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponseDto response = directPaymentService.confirmPayment(worker.getWorkerId(), assignment.getAssignmentId(), request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CONFIRMED_FULL);
        assertThat(response.getWorkerConfirmStatus()).isEqualTo("RECEIVED_FULL");
        assertThat(response.getWorkerComment()).contains("Đã nhận đủ tiền");
    }

    @Test
    @DisplayName("Worker báo chưa nhận được tiền -> Chuyển sang DISPUTED")
    void shouldConfirmPaymentNotReceivedDispute() {
        initialPayment.setStatus(PaymentStatus.DECLARED_PENDING_WORKER);

        ConfirmPaymentRequestDto request = ConfirmPaymentRequestDto.builder()
                .confirmStatus("NOT_RECEIVED")
                .comment("EO chưa chuyển khoản, đã qua 24h.")
                .build();

        when(paymentRepository.findByAssignmentId(assignment.getAssignmentId())).thenReturn(Optional.of(initialPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponseDto response = directPaymentService.confirmPayment(worker.getWorkerId(), assignment.getAssignmentId(), request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.DISPUTED);
        assertThat(response.getWorkerConfirmStatus()).isEqualTo("NOT_RECEIVED");
    }

    @Test
    @DisplayName("Worker khác không thể xác nhận thanh toán của Worker trong ca")
    void shouldThrowWhenUnauthorizedWorkerConfirms() {
        UUID strangerWorkerId = UUID.randomUUID();
        ConfirmPaymentRequestDto request = ConfirmPaymentRequestDto.builder()
                .confirmStatus("RECEIVED_FULL")
                .build();

        when(paymentRepository.findByAssignmentId(assignment.getAssignmentId())).thenReturn(Optional.of(initialPayment));

        assertThatThrownBy(() -> directPaymentService.confirmPayment(strangerWorkerId, assignment.getAssignmentId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bạn không có quyền xác nhận thanh toán này");
    }

    @Test
    @DisplayName("W2: Worker hoặc EO chủ sở hữu xem được thanh toán của Assignment")
    void shouldReturnPaymentWhenOwnerWorkerOrEoRequests() {
        User workerUser = User.builder().userId(UUID.randomUUID()).fullName("Nguyễn Văn An").build();
        User eoUser = User.builder().userId(UUID.randomUUID()).fullName("CEO Sao Mai").build();
        worker.setUser(workerUser);
        eo.setUser(eoUser);

        when(paymentRepository.findByAssignmentId(assignment.getAssignmentId())).thenReturn(Optional.of(initialPayment));

        PaymentResponseDto byWorker = directPaymentService.getPaymentByAssignment(
                assignment.getAssignmentId(), workerUser.getUserId(), false);
        PaymentResponseDto byEo = directPaymentService.getPaymentByAssignment(
                assignment.getAssignmentId(), eoUser.getUserId(), false);

        assertThat(byWorker).isNotNull();
        assertThat(byWorker.getAssignmentId()).isEqualTo(assignment.getAssignmentId());
        assertThat(byEo).isNotNull();
        assertThat(byEo.getEoId()).isEqualTo(eo.getEoId());
    }

    @Test
    @DisplayName("W2: Worker/EO lạ dò UUID của Assignment khác sẽ nhận 404 NOT_FOUND (chống IDOR)")
    void shouldThrow404WhenStrangerRequestsAssignmentPayment() {
        User workerUser = User.builder().userId(UUID.randomUUID()).fullName("Nguyễn Văn An").build();
        User eoUser = User.builder().userId(UUID.randomUUID()).fullName("CEO Sao Mai").build();
        worker.setUser(workerUser);
        eo.setUser(eoUser);
        UUID strangerUserId = UUID.randomUUID();

        when(paymentRepository.findByAssignmentId(assignment.getAssignmentId())).thenReturn(Optional.of(initialPayment));

        assertThatThrownBy(() -> directPaymentService.getPaymentByAssignment(
                assignment.getAssignmentId(), strangerUserId, false))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
