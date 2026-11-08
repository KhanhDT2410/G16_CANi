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
import java.time.Instant;
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
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final ReliabilityLogRepository reliabilityLogRepository;
    private final RsAppealRepository rsAppealRepository;

    @Override
    public void run(String... args) {
        log.info("==> [H2 Profile] Seeding local taxonomy skills and mock worker...");

        if (skillRepository.count() == 0) {
            List<Skill> skills = List.of(
                Skill.builder().name("Phục vụ bàn").category("F&B").description("Phục vụ món ăn và thức uống tại bàn tiệc").isActive(true).build(),
                Skill.builder().name("Phụ bếp").category("F&B").description("Sơ chế nguyên liệu và hỗ trợ đầu bếp").isActive(true).build(),
                Skill.builder().name("Pha chế (Bartender/Barista)").category("F&B").description("Pha chế đồ uống, cocktail, cà phê").isActive(true).build(),
                Skill.builder().name("Thu ngân").category("Operations").description("Vận hành máy POS và thanh toán").isActive(true).build(),
                Skill.builder().name("Lễ tân / Check-in").category("Guest Services").description("Đón khách, phát thẻ và hướng dẫn chỗ ngồi").isActive(true).build(),
                Skill.builder().name("Điều phối sự kiện").category("Operations").description("Hỗ trợ quản lý timeline và điều phối nhân sự").isActive(true).build(),
                Skill.builder().name("Kỹ thuật âm thanh - ánh sáng").category("Technical").description("Vận hành thiết bị AV cơ bản cho sự kiện").isActive(true).build(),
                Skill.builder().name("Bảo vệ / An ninh").category("Security").description("Kiểm soát lối ra vào và duy trì trật tự sự kiện").isActive(true).build(),
                Skill.builder().name("Hậu cần / Setup sự kiện").category("Logistics").description("Khuân vác, lắp đặt bàn ghế, backdrop, dọn dẹp").isActive(true).build(),
                Skill.builder().name("PG / PB").category("Marketing").description("Quảng bá sản phẩm, chụp hình và tương tác với khách").isActive(true).build()
            );
            skillRepository.saveAll(skills);
            log.info("==> [H2 Profile] Seeded {} taxonomy skills successfully.", skills.size());
        }

        // Seed default Worker user if not exists
        String testEmail = "worker@cani.vn";
        if (!userRepository.existsByEmail(testEmail)) {
            User user = User.builder()
                .userId(UUID.fromString("08d4cbd3-9d94-4307-99c5-b66484d5de84"))
                .email(testEmail)
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
            log.info("==> [H2 Profile] Seeded default Worker user: {} (id: {})", testEmail, savedUser.getUserId());
        }

        // Seed default Customer Care user if not exists
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

        // Seed default Admin user if not exists
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

        // Seed default Event Organizer (EO) and test subscriptions & revenue data
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
                    .build()
            );
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
                    .build()
            );
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
                    .reasonDetail("Trên đường di chuyển đến ca làm việc gặp sự cố giao thông bất khả kháng, có giấy khám và viện phí từ Bệnh viện Chợ Rẫy đính kèm.")
                    .evidence("https://storage.cani.vn/appeals/evidence-medical-hospital-ch在该.pdf")
                    .status(RsAppealStatus.SUBMITTED)
                    .createdAt(now.minus(1, ChronoUnit.DAYS))
                    .build();
                rsAppealRepository.save(appeal);
                log.info("==> [H2 Profile] Seeded 1 sample RS Appeal for UC-94 (appealId: {}).", appeal.getId());
            }
        }
    }
}
