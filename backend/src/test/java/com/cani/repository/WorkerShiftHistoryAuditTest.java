package com.cani.repository;

import com.cani.dto.worker.ShiftAssignmentResponseDto;
import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.service.WorkerJobService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quy chuẩn Kiểm thử & Xác thực CSDL (Rule 5 AGENTS.md / Post-Test DB Audit)
 * Kiểm định tích hợp đa tầng: DB H2 -> Repositories -> WorkerJobService -> DTO làm giàu
 */
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
public class WorkerShiftHistoryAuditTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired
    private WorkerJobService workerJobService;

    @Test
    @DisplayName("RULE 5 AUDIT - Kiểm tra Lịch sử Ca làm: Đối soát tính đúng đắn giữa DB thực tế và DTO làm giàu")
    void testWorkerShiftHistory_EndToEnd_And_DatabaseAudit() {
        Instant now = Instant.now();

        // 1. Tạo User & Worker
        UUID workerUserId = UUID.randomUUID();
        User workerUser = User.builder()
                .userId(workerUserId)
                .email("audit_worker_" + UUID.randomUUID() + "@cani.vn")
                .fullName("Nguyễn Văn Worker")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();
        em.persist(workerUser);

        Worker worker = Worker.builder()
                .user(workerUser)
                .fullName("Nguyễn Văn Worker")
                .reliabilityScore(100)
                .ratingAvg(BigDecimal.valueOf(5.0))
                .kycStatus(KycStatus.APPROVED)
                .totalShiftsCompleted(1)
                .build();
        em.persist(worker);

        // 2. Tạo User & Event Organizer (EO)
        UUID eoUserId = UUID.randomUUID();
        User eoUser = User.builder()
                .userId(eoUserId)
                .email("audit_eo_" + UUID.randomUUID() + "@cani.vn")
                .fullName("Công Ty Tổ Chức Sự Kiện ABC")
                .role(UserRole.EVENT_ORGANIZER)
                .status(UserStatus.ACTIVE)
                .build();
        em.persist(eoUser);

        EventOrganizer eo = EventOrganizer.builder()
                .user(eoUser)
                .companyName("ABC Event Corp")
                .verificationStatus(VerificationStatus.APPROVED)
                .ratingAvg(BigDecimal.valueOf(4.9))
                .build();
        em.persist(eo);

        // 3. Tạo Event & Shift
        Event event = Event.builder()
                .eventOrganizer(eo)
                .name("Triển lãm Tech Expo 2026")
                .venueAddress("Trung tâm SECC, Quận 7, TP.HCM")
                .venueLat(10.7769)
                .venueLng(106.7009)
                .startTime(now.minus(Duration.ofDays(2)))
                .endTime(now.minus(Duration.ofDays(1)))
                .status(EventStatus.COMPLETED)
                .build();
        em.persist(event);

        Shift shift = Shift.builder()
                .event(event)
                .jobType("Điều phối sự kiện")
                .startTime(now.minus(Duration.ofDays(2)).plus(Duration.ofHours(8)))
                .endTime(now.minus(Duration.ofDays(2)).plus(Duration.ofHours(12)))
                .hourlyWage(BigDecimal.valueOf(60000))
                .requiredWorkers(5)
                .status(ShiftStatus.COMPLETED)
                .build();
        em.persist(shift);

        // 4. Tạo 3 ca làm việc với các trạng thái khác nhau:
        // Ca 1: COMPLETED (Lịch sử)
        ShiftAssignment saCompleted = ShiftAssignment.builder()
                .shift(shift)
                .worker(worker)
                .status(AssignmentStatus.COMPLETED)
                .source(AssignmentSource.MATCHING)
                .eoConfirmed(true)
                .build();
        em.persist(saCompleted);

        // Ca 2: CANCELLED (Lịch sử)
        ShiftAssignment saCancelled = ShiftAssignment.builder()
                .shift(shift)
                .worker(worker)
                .status(AssignmentStatus.CANCELLED)
                .cancellationReason("Bận việc gia đình đột xuất")
                .source(AssignmentSource.MATCHING)
                .eoConfirmed(false)
                .build();
        em.persist(saCancelled);

        // Ca 3: CONFIRMED (Ca tương lai - KHÔNG ĐƯỢC lọt vào lịch sử)
        ShiftAssignment saUpcoming = ShiftAssignment.builder()
                .shift(shift)
                .worker(worker)
                .status(AssignmentStatus.CONFIRMED)
                .source(AssignmentSource.MATCHING)
                .eoConfirmed(true)
                .build();
        em.persist(saUpcoming);

        // 5. Tạo Chấm công (AttendanceLog) cho Ca 1
        AttendanceLog attendanceLog = AttendanceLog.builder()
                .shiftAssignment(saCompleted)
                .worker(worker)
                .checkinTime(shift.getStartTime())
                .checkoutTime(shift.getEndTime())
                .hoursWorked(BigDecimal.valueOf(4.0))
                .attendanceStatus(AttendanceStatus.CHECKED_OUT)
                .proofImageUrl("https://cani.vn/proof_audit.jpg")
                .proofSubmittedAt(shift.getEndTime())
                .build();
        em.persist(attendanceLog);

        // 6. Tạo Quyết toán (Payment) cho Ca 1
        Payment payment = Payment.builder()
                .eventId(event.getEventId())
                .shiftId(shift.getShiftId())
                .assignmentId(saCompleted.getAssignmentId())
                .eventOrganizer(eo)
                .worker(worker)
                .agreedAmount(BigDecimal.valueOf(240000))
                .declaredAmount(BigDecimal.valueOf(240000))
                .status(PaymentStatus.CONFIRMED_FULL)
                .paymentMethod("BANK_TRANSFER")
                .workerConfirmStatus("CONFIRMED_FULL")
                .build();
        em.persist(payment);

        // 7. Tạo Đánh giá 2 chiều (Reviews) cho Ca 1
        // EO đánh giá Worker: 5 sao
        Review eoReview = Review.builder()
                .assignmentId(saCompleted.getAssignmentId())
                .reviewerId(eoUser.getUserId())
                .revieweeId(workerUser.getUserId())
                .rating(5)
                .comment("Worker rất đúng giờ, chăm chỉ và hỗ trợ khách chu đáo")
                .build();
        em.persist(eoReview);

        // Worker đánh giá lại EO: 5 sao
        Review workerReview = Review.builder()
                .assignmentId(saCompleted.getAssignmentId())
                .reviewerId(workerUser.getUserId())
                .revieweeId(eoUser.getUserId())
                .rating(5)
                .comment("Ban tổ chức chuyên nghiệp, thanh toán nhanh")
                .build();
        em.persist(workerReview);

        // Đẩy toàn bộ thay đổi xuống CSDL H2
        em.flush();
        em.clear();

        // =========================================================================
        // BƯỚC 1: KIỂM THỬ THỰC THI SERVICE API VỚI PHÂN TRANG VÀ BATCH FETCHING
        // =========================================================================
        Page<ShiftAssignmentResponseDto> historyPage = workerJobService.getAssignmentHistory(
                workerUser.getUserId(),
                null, // Lấy toàn bộ lịch sử (COMPLETED, CANCELLED, NO_SHOW)
                PageRequest.of(0, 10)
        );

        // Kiểm tra số lượng ca: Chỉ có 2 ca (COMPLETED & CANCELLED), ca CONFIRMED không được xuất hiện
        assertThat(historyPage).isNotNull();
        assertThat(historyPage.getTotalElements()).isEqualTo(2);

        // Tìm ca hoàn thành trong trang kết quả
        ShiftAssignmentResponseDto completedDto = historyPage.getContent().stream()
                .filter(dto -> dto.getAssignmentId().equals(saCompleted.getAssignmentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không tìm thấy ca hoàn thành trong lịch sử"));

        assertThat(completedDto.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(completedDto.getEventName()).isEqualTo("Triển lãm Tech Expo 2026");
        assertThat(completedDto.getJobType()).isEqualTo("Điều phối sự kiện");
        assertThat(completedDto.getAttendanceStatus()).isEqualTo("CHECKED_OUT");
        assertThat(completedDto.getHoursWorked()).isEqualByComparingTo(BigDecimal.valueOf(4.0));
        assertThat(completedDto.getPaymentStatus()).isEqualTo("CONFIRMED_FULL");
        assertThat(completedDto.getEoRating()).isEqualTo(5);
        assertThat(completedDto.getEoReviewComment()).isEqualTo("Worker rất đúng giờ, chăm chỉ và hỗ trợ khách chu đáo");
        assertThat(completedDto.getHasWorkerReviewed()).isTrue();

        // =========================================================================
        // BƯỚC 2: QUY CHUẨN ĐỐI SOÁT CSDL TRỰC TIẾP (POST-TEST DB AUDIT - RULE 5)
        // =========================================================================
        // 1. Đối soát bảng shift_assignments: Phải có đúng 3 bản ghi của worker này
        Long totalAssignmentsInDb = em.createQuery(
                "SELECT COUNT(sa) FROM ShiftAssignment sa WHERE sa.worker.workerId = :wid", Long.class)
                .setParameter("wid", worker.getWorkerId())
                .getSingleResult();
        assertThat(totalAssignmentsInDb).isEqualTo(3);

        // 2. Đối soát bảng attendance_logs: Bản ghi chấm công phải tồn tại và gắn đúng ca
        Long attendanceLogsCount = em.createQuery(
                "SELECT COUNT(a) FROM AttendanceLog a WHERE a.shiftAssignment.assignmentId = :aid AND a.hoursWorked = 4.0", Long.class)
                .setParameter("aid", saCompleted.getAssignmentId())
                .getSingleResult();
        assertThat(attendanceLogsCount).isEqualTo(1);

        // 3. Đối soát bảng payments: Bản ghi thanh toán phải ở trạng thái CONFIRMED_FULL
        Long paymentsCount = em.createQuery(
                "SELECT COUNT(p) FROM Payment p WHERE p.assignmentId = :aid AND p.status = :status", Long.class)
                .setParameter("aid", saCompleted.getAssignmentId())
                .setParameter("status", PaymentStatus.CONFIRMED_FULL)
                .getSingleResult();
        assertThat(paymentsCount).isEqualTo(1);

        // 4. Đối soát bảng reviews: Có đủ 2 đánh giá (1 từ EO và 1 từ Worker)
        Long reviewsCount = em.createQuery(
                "SELECT COUNT(r) FROM Review r WHERE r.assignmentId = :aid", Long.class)
                .setParameter("aid", saCompleted.getAssignmentId())
                .getSingleResult();
        assertThat(reviewsCount).isEqualTo(2);
    }
}
