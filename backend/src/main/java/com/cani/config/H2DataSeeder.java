package com.cani.config;

import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Profile("h2")
@RequiredArgsConstructor
public class H2DataSeeder implements CommandLineRunner {

    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final WorkerRepository workerRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final EventRepository eventRepository;
    private final ShiftRepository shiftRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final ReliabilityLogRepository reliabilityLogRepository;
    private final RsAppealRepository rsAppealRepository;
    private final FaqRepository faqRepository;

    @Override
    public void run(String... args) {
        log.info(
                "==> [H2 Profile] Seeding local taxonomy skills and standard demo accounts (Password: Password123!)...");

        if (skillRepository.count() == 0) {
            List<Skill> skills = List.of(
                    Skill.builder().name("Phục vụ bàn").category("F&B")
                            .description("Phục vụ món ăn và thức uống tại bàn tiệc").isActive(true).build(),
                    Skill.builder().name("Phụ bếp").category("F&B").description("Sơ chế nguyên liệu và hỗ trợ đầu bếp")
                            .isActive(true).build(),
                    Skill.builder().name("Pha chế (Bartender/Barista)").category("F&B")
                            .description("Pha chế đồ uống, cocktail, cà phê").isActive(true).build(),
                    Skill.builder().name("Thu ngân").category("Operations")
                            .description("Vận hành máy POS và thanh toán").isActive(true).build(),
                    Skill.builder().name("Lễ tân / Check-in").category("Guest Services")
                            .description("Đón khách, phát thẻ và hướng dẫn chỗ ngồi").isActive(true).build(),
                    Skill.builder().name("Điều phối sự kiện").category("Operations")
                            .description("Hỗ trợ quản lý timeline và điều phối nhân sự").isActive(true).build(),
                    Skill.builder().name("Kỹ thuật âm thanh - ánh sáng").category("Technical")
                            .description("Vận hành thiết bị AV cơ bản cho sự kiện").isActive(true).build(),
                    Skill.builder().name("Bảo vệ / An ninh").category("Security")
                            .description("Kiểm soát lối ra vào và duy trì trật tự sự kiện").isActive(true).build(),
                    Skill.builder().name("Hậu cần / Setup sự kiện").category("Logistics")
                            .description("Khuân vác, lắp đặt bàn ghế, backdrop, dọn dẹp").isActive(true).build(),
                    Skill.builder().name("PG / PB").category("Marketing")
                            .description("Quảng bá sản phẩm, chụp hình và tương tác với khách").isActive(true).build());
            skillRepository.saveAll(skills);
            log.info("==> [H2 Profile] Seeded {} taxonomy skills successfully.", skills.size());
        }

        // 1. Seed Worker user (worker@cani.vn)
        String workerEmail = "worker@cani.vn";
        if (!userRepository.existsByEmail(workerEmail)) {
            User user = User.builder()
                    .userId(UUID.fromString("08d4cbd3-9d94-4307-99c5-b66484d5de84"))
                    .email(workerEmail)
                    .fullName("Nguyễn Văn An")
                    .role(UserRole.WORKER)
                    .status(UserStatus.ACTIVE)
                    .phone("0987654321")
                    .emailVerified(true)
                    .build();
            User savedUser = userRepository.save(user);

            Worker worker = Worker.builder()
                    .user(savedUser)
                    .fullName(savedUser.getFullName())
                    .build();
            workerRepository.save(worker);
            log.info("==> [H2 Profile] Seeded default Worker user: {} (id: {})", workerEmail, savedUser.getUserId());
        }

        // 2. Seed Customer Care user (cc@cani.vn & cskh@cani.vn)
        String ccEmail = "cc@cani.vn";
        if (!userRepository.existsByEmail(ccEmail)) {
            User cc = User.builder()
                    .userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                    .email(ccEmail)
                    .fullName("Nhân viên CSKH")
                    .role(UserRole.CUSTOMER_CARE)
                    .status(UserStatus.ACTIVE)
                    .phone("0912345678")
                    .emailVerified(true)
                    .build();
            userRepository.save(cc);
            log.info("==> [H2 Profile] Seeded default Customer Care user: {} (id: {})", ccEmail, cc.getUserId());
        }

        String cskhEmail = "cskh@cani.vn";
        if (!userRepository.existsByEmail(cskhEmail)) {
            User user = User.builder()
                    .userId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                    .email(cskhEmail)
                    .fullName("Bộ Phận CSKH")
                    .role(UserRole.CUSTOMER_CARE)
                    .status(UserStatus.ACTIVE)
                    .phone("0903333333")
                    .emailVerified(true)
                    .build();
            userRepository.save(user);
            log.info("==> [H2 Profile] Seeded default CSKH user: {}", cskhEmail);
        }

        // 3. Seed Admin user (admin@cani.vn)
        String adminEmail = "admin@cani.vn";
        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = User.builder()
                    .userId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                    .email(adminEmail)
                    .fullName("Quản Trị Viên Hệ Thống")
                    .role(UserRole.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .phone("0901000001")
                    .emailVerified(true)
                    .build();
            userRepository.save(admin);
            log.info("==> [H2 Profile] Seeded default Admin user: {} (id: {})", adminEmail, admin.getUserId());
        }

        // 4. Seed default Event Organizer (EO) and test subscriptions & revenue data
        String eoEmail = "eo@cani.vn";
        EventOrganizer eo = null;
        if (!userRepository.existsByEmail(eoEmail)) {
            User eoUser = User.builder()
                    .userId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                    .email(eoEmail)
                    .fullName("Bà Trần Thị Ngọc (EO Sài Gòn Star)")
                    .role(UserRole.EVENT_ORGANIZER)
                    .status(UserStatus.ACTIVE)
                    .phone("0902222333")
                    .emailVerified(true)
                    .build();
            User savedEoUser = userRepository.save(eoUser);

            eo = EventOrganizer.builder()
                    .user(savedEoUser)
                    .companyName("Công ty TNHH Sự Kiện Sài Gòn Star")
                    .taxId("0312345678")
                    .address("123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh")
                    .verificationStatus(VerificationStatus.APPROVED)
                    .verifiedAt(Instant.now().minus(30, ChronoUnit.DAYS))
                    .build();
            eo = eventOrganizerRepository.save(eo);
            log.info("==> [H2 Profile] Seeded default Event Organizer: {} (id: {})", eoEmail, eo.getEoId());
        } else {
            eo = eventOrganizerRepository.findAll().stream().findFirst().orElse(null);
        }

        // Seed Sample Subscriptions (UC-108 Revenue)
        if (eo != null && subscriptionRepository.count() == 0) {
            Instant now = Instant.now();
            List<Subscription> sampleSubs = List.of(
                    Subscription.builder()
                            .eventOrganizer(eo)
                            .planName("Gói Khởi Nghiệp (Mời Đích Danh)")
                            .planType(PlanType.INVITE_ONLY)
                            .durationDays(30)
                            .price(BigDecimal.valueOf(1500000))
                            .status(SubscriptionStatus.ACTIVE)
                            .startsAt(now.minus(15, ChronoUnit.DAYS))
                            .expiresAt(now.plus(15, ChronoUnit.DAYS))
                            .createdAt(now.minus(15, ChronoUnit.DAYS))
                            .build(),
                    Subscription.builder()
                            .eventOrganizer(eo)
                            .planName("Gói Ghép Ca Nâng Cao (Matching Plus)")
                            .planType(PlanType.MATCHING_PLUS)
                            .durationDays(60)
                            .price(BigDecimal.valueOf(3500000))
                            .status(SubscriptionStatus.ACTIVE)
                            .startsAt(now.minus(5, ChronoUnit.DAYS))
                            .expiresAt(now.plus(55, ChronoUnit.DAYS))
                            .createdAt(now.minus(5, ChronoUnit.DAYS))
                            .build(),
                    Subscription.builder()
                            .eventOrganizer(eo)
                            .planName("Gói Sự Kiện Mùa Thu")
                            .planType(PlanType.MATCHING_PLUS)
                            .durationDays(30)
                            .price(BigDecimal.valueOf(2500000))
                            .status(SubscriptionStatus.ACTIVE)
                            .startsAt(now.minus(2, ChronoUnit.DAYS))
                            .expiresAt(now.plus(28, ChronoUnit.DAYS))
                            .createdAt(now.minus(2, ChronoUnit.DAYS))
                            .build());
            subscriptionRepository.saveAll(sampleSubs);
            log.info("==> [H2 Profile] Seeded {} sample subscriptions for UC-108.", sampleSubs.size());
        }

        // Seed Sample Event & Payments
        Worker worker = workerRepository.findAll().stream().findFirst().orElse(null);
        if (eo != null && worker != null && paymentRepository.count() == 0) {
            Instant now = Instant.now();
            Event event = Event.builder()
                    .eventOrganizer(eo)
                    .name("Lễ Hội Âm Nhạc Mùa Hè 2026")
                    .mode(EventMode.MATCHING)
                    .createdAt(now.minus(10, ChronoUnit.DAYS))
                    .build();
            event = eventRepository.save(event);

            List<Payment> samplePayments = List.of(
                    Payment.builder()
                            .eventId(event.getEventId())
                            .eventOrganizer(eo)
                            .worker(worker)
                            .agreedAmount(BigDecimal.valueOf(450000))
                            .declaredAmount(BigDecimal.valueOf(450000))
                            .status(PaymentStatus.CONFIRMED_FULL)
                            .paymentMethod("VNPAY_QR")
                            .externalRef("VNPAY_TX_1001")
                            .createdAt(now.minus(3, ChronoUnit.DAYS))
                            .build(),
                    Payment.builder()
                            .eventId(event.getEventId())
                            .eventOrganizer(eo)
                            .worker(worker)
                            .agreedAmount(BigDecimal.valueOf(600000))
                            .declaredAmount(BigDecimal.valueOf(600000))
                            .status(PaymentStatus.CONFIRMED_FULL)
                            .paymentMethod("BANK_TRANSFER")
                            .externalRef("MB_9823192")
                            .createdAt(now.minus(2, ChronoUnit.DAYS))
                            .build(),
                    Payment.builder()
                            .eventId(event.getEventId())
                            .eventOrganizer(eo)
                            .worker(worker)
                            .agreedAmount(BigDecimal.valueOf(500000))
                            .declaredAmount(BigDecimal.valueOf(500000))
                            .status(PaymentStatus.DISPUTED)
                            .paymentMethod("VNPAY")
                            .externalRef("VNPAY_TX_1002")
                            .createdAt(now.minus(1, ChronoUnit.DAYS))
                            .build(),
                    Payment.builder()
                            .eventId(event.getEventId())
                            .eventOrganizer(eo)
                            .worker(worker)
                            .agreedAmount(BigDecimal.valueOf(350000))
                            .status(PaymentStatus.UNVERIFIED)
                            .paymentMethod("VNPAY")
                            .externalRef("VNPAY_TX_1003")
                            .createdAt(now.minus(12, ChronoUnit.HOURS))
                            .build());
            paymentRepository.saveAll(samplePayments);
            log.info("==> [H2 Profile] Seeded {} sample direct payments for UC-108.", samplePayments.size());
        }

        // Seed Sample ReliabilityLog & RS Appeal (UC-94)
        if (worker != null && reliabilityLogRepository.count() == 0) {
            Instant now = Instant.now();
            ReliabilityLog penaltyLog = ReliabilityLog.builder()
                    .worker(worker)
                    .changeAmount(-25)
                    .reason("Vắng mặt không báo trước (No-show) tại ca tiệc cưới White Palace")
                    .scoreBefore(100)
                    .scoreAfter(75)
                    .createdAt(now.minus(3, ChronoUnit.DAYS))
                    .build();
            penaltyLog = reliabilityLogRepository.save(penaltyLog);

            worker.setReliabilityScore(75);
            workerRepository.save(worker);

            if (rsAppealRepository.count() == 0) {
                RsAppeal appeal = RsAppeal.builder()
                        .worker(worker)
                        .reliabilityLog(penaltyLog)
                        .penaltyAmount(25)
                        .reasonCode("FORCE_MAJEURE_ACCIDENT")
                        .reasonDetail(
                                "Trên đường di chuyển đến ca làm việc gặp sự cố giao thông bất khả kháng, có giấy khám và viện phí từ Bệnh viện Chợ Rẫy đính kèm.")
                        .evidence("https://storage.cani.vn/appeals/evidence-medical-hospital-cho-ray.pdf")
                        .status(RsAppealStatus.SUBMITTED)
                        .createdAt(now.minus(1, ChronoUnit.DAYS))
                        .build();
                rsAppealRepository.save(appeal);
                log.info("==> [H2 Profile] Seeded 1 sample RS Appeal for UC-94 (appealId: {}).", appeal.getId());
            }
        }

        // 5. Seed Demo Events & Shifts for Job Radar Map
        if (shiftRepository.count() == 0) {
            EventOrganizer defaultEo = eo != null ? eo : eventOrganizerRepository.findAll().stream().findFirst().orElse(null);
            if (defaultEo != null) {
                // Event 1: Gem Center Q1 (~1.2 km from center)
                Event event1 = Event.builder()
                        .eventOrganizer(defaultEo)
                        .name("Hội Nghị Thượng Đỉnh Công Nghệ AI 2026")
                        .description("Sự kiện diễn đàn công nghệ thường niên quy mô 1.500 khách tại Gem Center")
                        .venueAddress("Gem Center, 8 Nguyễn Bỉnh Khiêm, Đa Kao, Quận 1, TP.HCM")
                        .venueLat(10.7885)
                        .venueLng(106.7011)
                        .eventDate(LocalDate.now().plusDays(2))
                        .mode(EventMode.MATCHING)
                        .status(EventStatus.ACTIVE)
                        .totalShifts(1)
                        .totalRequiredWorkers(4)
                        .totalConfirmedWorkers(1)
                        .build();
                Event savedEvent1 = eventRepository.save(event1);

                Shift shift1 = Shift.builder()
                        .event(savedEvent1)
                        .jobType("Lễ tân / Check-in")
                        .description("Check-in đại biểu, phát tài liệu và hướng dẫn khách vào hội trường")
                        .locationAddress(savedEvent1.getVenueAddress())
                        .locationLat(10.7885)
                        .locationLng(106.7011)
                        .hourlyWage(new BigDecimal("50000"))
                        .allowance(new BigDecimal("50000"))
                        .requiredWorkers(4)
                        .confirmedWorkers(1)
                        .remainingWorkers(3)
                        .startTime(Instant.now().plus(Duration.ofHours(3)))
                        .endTime(Instant.now().plus(Duration.ofHours(9)))
                        .status(ShiftStatus.ACTIVE)
                        .minExperienceLevel(ExperienceLevel.BEGINNER)
                        .build();

                // Event 2: SECC Q7 (~6.5 km from center)
                Event event2 = Event.builder()
                        .eventOrganizer(defaultEo)
                        .name("Triển Lãm Ô Tô & Năng Lượng Xanh 2026")
                        .description("Triển lãm quốc tế về phương tiện di chuyển thông minh và xe điện")
                        .venueAddress("SECC, 799 Nguyễn Văn Linh, Tân Phú, Quận 7, TP.HCM")
                        .venueLat(10.7302)
                        .venueLng(106.7219)
                        .eventDate(LocalDate.now().plusDays(3))
                        .mode(EventMode.MATCHING)
                        .status(EventStatus.ACTIVE)
                        .totalShifts(1)
                        .totalRequiredWorkers(6)
                        .totalConfirmedWorkers(2)
                        .build();
                Event savedEvent2 = eventRepository.save(event2);

                Shift shift2 = Shift.builder()
                        .event(savedEvent2)
                        .jobType("Điều phối sự kiện")
                        .description("Hỗ trợ điều phối khu vực trưng bày xe và phát quà lưu niệm")
                        .locationAddress(savedEvent2.getVenueAddress())
                        .locationLat(10.7302)
                        .locationLng(106.7219)
                        .hourlyWage(new BigDecimal("55000"))
                        .allowance(new BigDecimal("60000"))
                        .requiredWorkers(6)
                        .confirmedWorkers(2)
                        .remainingWorkers(4)
                        .startTime(Instant.now().plus(Duration.ofHours(5)))
                        .endTime(Instant.now().plus(Duration.ofHours(11)))
                        .status(ShiftStatus.ACTIVE)
                        .minExperienceLevel(ExperienceLevel.INTERMEDIATE)
                        .build();

                // Event 3: White Palace Tân Bình / Phú Nhuận (~4.5 km from center)
                Event event3 = Event.builder()
                        .eventOrganizer(defaultEo)
                        .name("Đại Tiệc Gala Doanh Nhân Trẻ TP.HCM")
                        .description("Dạ tiệc tri ân và trao giải doanh nghiệp khởi nghiệp tiêu biểu")
                        .venueAddress("White Palace, 194 Hoàng Văn Thụ, Phường 9, Phú Nhuận, TP.HCM")
                        .venueLat(10.7989)
                        .venueLng(106.6667)
                        .eventDate(LocalDate.now().plusDays(1))
                        .mode(EventMode.MATCHING)
                        .status(EventStatus.ACTIVE)
                        .totalShifts(1)
                        .totalRequiredWorkers(5)
                        .totalConfirmedWorkers(1)
                        .build();
                Event savedEvent3 = eventRepository.save(event3);

                Shift shift3 = Shift.builder()
                        .event(savedEvent3)
                        .jobType("Phục vụ bàn")
                        .description("Phục vụ tiệc set menu tiêu chuẩn VIP cho quan khách")
                        .locationAddress(savedEvent3.getVenueAddress())
                        .locationLat(10.7989)
                        .locationLng(106.6667)
                        .hourlyWage(new BigDecimal("45000"))
                        .allowance(new BigDecimal("40000"))
                        .requiredWorkers(5)
                        .confirmedWorkers(1)
                        .remainingWorkers(4)
                        .startTime(Instant.now().plus(Duration.ofHours(4)))
                        .endTime(Instant.now().plus(Duration.ofHours(10)))
                        .status(ShiftStatus.ACTIVE)
                        .minExperienceLevel(ExperienceLevel.BEGINNER)
                        .build();

                shiftRepository.saveAll(List.of(shift1, shift2, shift3));
                log.info("==> [H2 Profile] Seeded {} demo Events and {} ACTIVE Shifts for Job Radar Map successfully.", 3, 3);
            }

            if (faqRepository.count() == 0) {
                List<Faq> faqs = List.of(
                        // ACCOUNT (3 câu)
                        Faq.builder()
                                .category(FaqCategory.ACCOUNT)
                                .question("Quy trình xác thực định danh (eKYC) trên ứng dụng CANi diễn ra như thế nào?")
                                .answer("Người lao động cần chụp rõ nét hai mặt Căn cước công dân (CCCD) gắn chip và một ảnh chân dung selfie khớp khuôn mặt. Hệ thống VNPT eKYC tích hợp của CANi sẽ tự động trích xuất thông tin OCR và đối soát sinh trắc học để kích hoạt tài khoản nhận ca trong vòng 1-3 phút.")
                                .status(FaqStatus.PUBLISHED)
                                .publishedAt(Instant.now())
                                .build(),
                        Faq.builder()
                                .category(FaqCategory.ACCOUNT)
                                .question("Điểm uy tín (RS - Reliability Score) là gì và ảnh hưởng như thế nào đến việc nhận ca làm?")
                                .answer("Điểm uy tín (RS) khởi điểm của mỗi tài khoản là 80 điểm. Hoàn thành tốt ca làm và nhận đánh giá 5 sao từ nhà tổ chức sẽ được cộng điểm; vi phạm hủy ca sát giờ hoặc vắng mặt không phép (No-show) sẽ bị trừ 25 điểm. Nếu điểm RS dưới 60, hệ thống sẽ tạm dừng đề xuất ca tự động; nếu giảm dưới 20 điểm, tài khoản sẽ bị tự động khóa để bảo vệ uy tín nền tảng.")
                                .status(FaqStatus.PUBLISHED)
                                .publishedAt(Instant.now())
                                .build(),
                        Faq.builder()
                                .category(FaqCategory.ACCOUNT)
                                .question("Làm thế nào để nâng bậc Điểm Kỹ Năng (Skill Points - SP) cho các vị trí chuyên môn?")
                                .answer("Mỗi ca làm hoàn thành thành công theo từng loại kỹ năng (Phục vụ bàn, Lễ tân, Thu ngân, Pha chế, Kỹ thuật AV, v.v.) sẽ tích lũy Điểm Kỹ Năng (SP). Người lao động cũng có thể tải lên chứng chỉ chuyên môn hoặc tham gia bài kiểm tra thẩm định AI để nâng hạng từ Cơ bản (Tier 1) lên Chuyên nghiệp (Tier 2) và Trưởng nhóm (Tier 3) để nhận thù lao cao hơn.")
                                .status(FaqStatus.PUBLISHED)
                                .publishedAt(Instant.now())
                                .build(),

                        // SHIFT (3 câu)
                        Faq.builder()
                                .category(FaqCategory.SHIFT)
                                .question("Thuật toán Ghép nối tự động (Matching Engine) hoạt động dựa trên các tiêu chí nào?")
                                .answer("Hệ thống tự động chấm Điểm Phù Hợp (Matching Score) theo 4 trọng số nghiệp vụ: Khoảng cách GPS địa lý (W_dist = 40%), Điểm đánh giá sao trung bình (W_rating = 30%), Số ca làm đã hoàn thành (W_exp = 20%) và Điểm uy tín sàn (W_rs = 10%). Người lao động có điểm phù hợp cao nhất trong bán kính tối đa (15 km) sẽ được ưu tiên nhận đề xuất nhận ca trước.")
                                .status(FaqStatus.PUBLISHED)
                                .publishedAt(Instant.now())
                                .build(),
                        Faq.builder()
                                .category(FaqCategory.SHIFT)
                                .question("Quy trình quét mã QR Check-in và Check-out tại sự kiện được thực hiện ra sao?")
                                .answer("Trước giờ bắt đầu ca làm 15-30 phút, người lao động có mặt tại địa điểm tổ chức, mở ứng dụng CANi và quét mã QR do Quản lý sự kiện (Event Coordinator) hiển thị tại bàn tiếp đón. Khi kết thúc ca, người lao động thực hiện quét mã QR Check-out lần 2 để hệ thống chốt số giờ làm việc thực tế và chuyển sang trạng thái chờ đối soát thù lao.")
                                .status(FaqStatus.PUBLISHED)
                                .publishedAt(Instant.now())
                                .build(),
                        Faq.builder()
                                .category(FaqCategory.SHIFT)
                                .question("Tôi có thể hủy ca làm việc sau khi đã bấm Chấp nhận không?")
                                .answer("Người lao động có thể hủy ca tự do trước thời điểm bắt đầu ca trên 24 giờ mà không bị phạt. Nếu hủy ca trong vòng 4 đến 24 giờ trước ca làm, bạn sẽ bị trừ 8 điểm uy tín (RS). Nếu hủy ca sát giờ dưới 4 tiếng hoặc không đến làm việc, hệ thống sẽ tự động ghi nhận lỗi No-show và trừ 25 điểm RS.")
                                .status(FaqStatus.PUBLISHED)
                                .publishedAt(Instant.now())
                                .build(),

                        // PAYMENT (3 câu)
                        Faq.builder()
                                .category(FaqCategory.PAYMENT)
                                .question("Phương thức thanh toán thù lao trực tiếp (Non-Escrow) trên CANi vận hành như thế nào?")
                                .answer("Sau khi ca làm việc kết thúc và hoàn tất Check-out, Nhà tổ chức sự kiện (EO) có thời hạn 48 giờ để thực hiện chuyển khoản thù lao trực tiếp vào số tài khoản ngân hàng của người lao động. Khi chuyển khoản thành công, EO đính kèm ảnh chụp biên lai giao dịch trên Cổng EO để hệ thống xác nhận thanh toán.")
                                .status(FaqStatus.PUBLISHED)
                                .publishedAt(Instant.now())
                                .build(),
                        Faq.builder()
                                .category(FaqCategory.PAYMENT)
                                .question("Nhà tổ chức sự kiện có phải trả phí hoa hồng khi tuyển dụng nhân sự trên sàn không?")
                                .answer("Trong giai đoạn hiện tại (MVP), tỷ lệ hoa hồng nền tảng áp dụng là 0.00%. Nhà tổ chức chỉ chi trả đúng mức thù lao theo thỏa thuận trên hợp đồng ca làm việc cho người lao động và phí gói đăng ký dịch vụ (Subscription) hàng tháng nếu sử dụng các tính năng nâng cao (Radar tìm nhân sự khẩn cấp, Đề xuất AI).")
                                .status(FaqStatus.PUBLISHED)
                                .publishedAt(Instant.now())
                                .build(),
                        Faq.builder()
                                .category(FaqCategory.PAYMENT)
                                .question("Các phương thức thanh toán gói đăng ký dịch vụ (Subscription) được hỗ trợ?")
                                .answer("Nhà tổ chức sự kiện có thể nâng cấp và gia hạn các gói dịch vụ (Gói Cơ bản, Gói Doanh nghiệp, Gói Sự kiện lớn) thông qua cổng thanh toán trực tuyến PayOS (hỗ trợ VietQR quét mã chuyển khoản nhanh 24/7) hoặc cổng VNPAY (thẻ ATM nội địa, Visa/MasterCard, ví điện tử).")
                                .status(FaqStatus.PUBLISHED)
                                .publishedAt(Instant.now())
                                .build(),

                        // DISPUTE (3 câu)
                        Faq.builder()
                                .category(FaqCategory.DISPUTE)
                                .question("Khi nào người lao động hoặc nhà tổ chức nên gửi đơn Tranh chấp & Khiếu nại?")
                                .answer("Người dùng có thể tạo đơn khiếu nại trong các trường hợp: Nhà tổ chức chậm trễ thanh toán thù lao quá 48 giờ sau ca làm, thù lao nhận được không đúng thỏa thuận ban đầu, bị xếp lỗi No-show oan uổng do lỗi kỹ thuật quét QR, hoặc phát sinh mâu thuẫn về khối lượng công việc thực tế tại sự kiện.")
                                .status(FaqStatus.PUBLISHED)
                                .publishedAt(Instant.now())
                                .build(),
                        Faq.builder()
                                .category(FaqCategory.DISPUTE)
                                .question("Quy trình giải quyết tranh chấp (Dispute Resolution) qua bộ phận Chăm sóc khách hàng?")
                                .answer("Khi nhận được đơn khiếu nại, nhân viên CSKH (Customer Care) sẽ tiếp nhận xử lý Cấp độ 1 trong vòng 24 giờ làm việc. CSKH mở luồng hội thoại đối soát 3 bên trực tiếp, kiểm tra nhật ký GPS, thời gian quét QR và hình ảnh chứng từ thanh toán. Nếu hai bên không thống nhất, Admin sẽ ra phán quyết chung thẩm cuối cùng.")
                                .status(FaqStatus.PUBLISHED)
                                .publishedAt(Instant.now())
                                .build(),
                        Faq.builder()
                                .category(FaqCategory.DISPUTE)
                                .question("Làm thế nào để người lao động nộp đơn Kháng cáo điểm uy tín (RS Appeal) khi bị trừ điểm No-show?")
                                .answer("Nếu bị ghi nhận No-show do trường hợp bất khả kháng (ốm đau đột xuất có giấy khám bệnh, tai nạn giao thông, sự cố bất khả kháng trên đường đi), người lao động vào mục Hồ sơ -> Lịch sử điểm uy tín -> chọn vi phạm và bấm Kháng cáo. Sau khi tải lên tài liệu minh chứng hợp lệ, bộ phận CSKH sẽ thẩm định và hoàn trả lại điểm RS đã trừ trong vòng 48 giờ.")
                                .status(FaqStatus.PUBLISHED)
                                .publishedAt(Instant.now())
                                .build()
                );
                faqRepository.saveAll(faqs);
                log.info("==> [H2 Profile] Seeded {} standard business FAQs successfully.", faqs.size());
            }
        }
    }
}
