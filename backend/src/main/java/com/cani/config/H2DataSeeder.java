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
        private final PlanRepository planRepository;
        private final SubscriptionRepository subscriptionRepository;
        private final PaymentRepository paymentRepository;
        private final ReliabilityLogRepository reliabilityLogRepository;
        private final RsAppealRepository rsAppealRepository;
        private final FaqRepository faqRepository;
        private final WorkerSkillRepository workerSkillRepository;
        private final WorkerSkillPointRepository workerSkillPointRepository;
        private final SkillVerificationRequestRepository skillVerificationRequestRepository;

        @Override
        public void run(String... args) {
                log.info(
                                "==> [H2 Profile] Seeding local taxonomy skills and standard demo accounts (Password: Password123!)...");

                if (skillRepository.count() == 0) {
                        List<Skill> skills = List.of(
                                        Skill.builder().name("Phục vụ bàn").category("F&B")
                                                        .description("Phục vụ món ăn và thức uống tại bàn tiệc")
                                                        .isActive(true).build(),
                                        Skill.builder().name("Phụ bếp").category("F&B")
                                                        .description("Sơ chế nguyên liệu và hỗ trợ đầu bếp")
                                                        .isActive(true).build(),
                                        Skill.builder().name("Pha chế (Bartender/Barista)").category("F&B")
                                                        .description("Pha chế đồ uống, cocktail, cà phê").isActive(true)
                                                        .build(),
                                        Skill.builder().name("Thu ngân").category("Operations")
                                                        .description("Vận hành máy POS và thanh toán").isActive(true)
                                                        .build(),
                                        Skill.builder().name("Lễ tân / Check-in").category("Guest Services")
                                                        .description("Đón khách, phát thẻ và hướng dẫn chỗ ngồi")
                                                        .isActive(true).build(),
                                        Skill.builder().name("Điều phối sự kiện").category("Operations")
                                                        .description("Hỗ trợ quản lý timeline và điều phối nhân sự")
                                                        .isActive(true).build(),
                                        Skill.builder().name("Kỹ thuật âm thanh - ánh sáng").category("Technical")
                                                        .description("Vận hành thiết bị AV cơ bản cho sự kiện")
                                                        .isActive(true).build(),
                                        Skill.builder().name("Bảo vệ / An ninh").category("Security")
                                                        .description("Kiểm soát lối ra vào và duy trì trật tự sự kiện")
                                                        .isActive(true).build(),
                                        Skill.builder().name("Hậu cần / Setup sự kiện").category("Logistics")
                                                        .description("Khuân vác, lắp đặt bàn ghế, backdrop, dọn dẹp")
                                                        .isActive(true).build(),
                                        Skill.builder().name("PG / PB").category("Marketing")
                                                        .description("Quảng bá sản phẩm, chụp hình và tương tác với khách")
                                                        .isActive(true).build());
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
                                        .baseLat(10.7769)
                                        .baseLng(106.7009)
                                        .build();
                        workerRepository.save(worker);
                        log.info("==> [H2 Profile] Seeded default Worker user: {} (id: {})", workerEmail,
                                        savedUser.getUserId());
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
                        log.info("==> [H2 Profile] Seeded default Customer Care user: {} (id: {})", ccEmail,
                                        cc.getUserId());
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
                        log.info("==> [H2 Profile] Seeded default Admin user: {} (id: {})", adminEmail,
                                        admin.getUserId());
                }

                // 4. Seed default Event Organizer (EO) and test subscriptions & revenue data
                String eoEmail = "demo.eo@cani.vn";
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
                                        .companyName("Sự Kiện Sài Gòn Star")
                                        .address("123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh")
                                        .verificationStatus(VerificationStatus.APPROVED)
                                        .verifiedAt(Instant.now().minus(30, ChronoUnit.DAYS))
                                        .build();
                        eo = eventOrganizerRepository.save(eo);
                        log.info("==> [H2 Profile] Seeded default Event Organizer: {} (id: {})", eoEmail, eo.getEoId());
                } else {
                        eo = eventOrganizerRepository.findAll().stream().findFirst().orElse(null);
                }

                // Seed Sample Plans & Subscriptions (UC-108 Revenue)
                if (planRepository.count() == 0) {
                        List<Plan> samplePlans = List.of(
                                        Plan.builder()
                                                        .planName("Gói Trải Nghiệm (Khởi Nghiệp)")
                                                        .planType(PlanType.FREE_TRIAL)
                                                        .price(BigDecimal.ZERO)
                                                        .durationDays(30)
                                                        .maxEvents(2)
                                                        .maxShifts(6)
                                                        .maxWorkers(20)
                                                        .baseMatchingQuota(2)
                                                        .build(),
                                        Plan.builder()
                                                        .planName("Gói Tiêu Chuẩn (Standard)")
                                                        .planType(PlanType.MATCHING_PLUS)
                                                        .price(BigDecimal.valueOf(99000))
                                                        .durationDays(30)
                                                        .maxEvents(5)
                                                        .maxShifts(25)
                                                        .maxWorkers(80)
                                                        .baseMatchingQuota(5)
                                                        .build(),
                                        Plan.builder()
                                                        .planName("Gói Chuyên Nghiệp (Pro / Doanh Nghiệp)")
                                                        .planType(PlanType.ENTERPRISE)
                                                        .price(BigDecimal.valueOf(249000))
                                                        .durationDays(30)
                                                        .maxEvents(20)
                                                        .maxShifts(100)
                                                        .maxWorkers(300)
                                                        .baseMatchingQuota(20)
                                                        .build());
                        planRepository.saveAll(samplePlans);
                }

                if (eo != null && subscriptionRepository.count() == 0) {
                        Plan matchingPlan = planRepository.findFirstByPlanType(PlanType.MATCHING_PLUS).orElse(null);
                        Plan freeTrialPlan = planRepository.findFirstByPlanType(PlanType.FREE_TRIAL).orElse(null);
                        Instant now = Instant.now();
                        List<Subscription> sampleSubs = List.of(
                                        Subscription.builder()
                                                        .eventOrganizer(eo)
                                                        .plan(freeTrialPlan)
                                                        .planName("Gói Trải Nghiệm (Khởi Nghiệp)")
                                                        .planType(PlanType.FREE_TRIAL)
                                                        .durationDays(30)
                                                        .price(BigDecimal.ZERO)
                                                        .status(SubscriptionStatus.ACTIVE)
                                                        .startsAt(now.minus(15, ChronoUnit.DAYS))
                                                        .expiresAt(now.plus(15, ChronoUnit.DAYS))
                                                        .createdAt(now.minus(15, ChronoUnit.DAYS))
                                                        .build(),
                                        Subscription.builder()
                                                        .eventOrganizer(eo)
                                                        .plan(matchingPlan)
                                                        .planName("Gói Tiêu Chuẩn (Standard)")
                                                        .planType(PlanType.MATCHING_PLUS)
                                                        .durationDays(30)
                                                        .price(BigDecimal.valueOf(99000))
                                                        .status(SubscriptionStatus.ACTIVE)
                                                        .startsAt(now.minus(5, ChronoUnit.DAYS))
                                                        .expiresAt(now.plus(25, ChronoUnit.DAYS))
                                                        .createdAt(now.minus(5, ChronoUnit.DAYS))
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
                        log.info("==> [H2 Profile] Seeded {} sample direct payments for UC-108.",
                                        samplePayments.size());
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
                        worker.setTotalShiftsCompleted(5);
                        worker.setKycStatus(KycStatus.APPROVED);
                        worker.setIsOnline(true);
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
                                log.info("==> [H2 Profile] Seeded 1 sample RS Appeal for UC-94 (appealId: {}).",
                                                appeal.getId());
                        }

                        // 4b. Seed WorkerSkill, WorkerSkillPoint & SkillVerificationRequest cho demo
                        // Worker
                        if (workerSkillRepository.count() == 0) {
                                Skill sLeTan = skillRepository.findByName("Lễ tân / Check-in").orElse(null);
                                Skill sPhucVu = skillRepository.findByName("Phục vụ bàn").orElse(null);
                                Skill sHauCan = skillRepository.findByName("Hậu cần / Setup sự kiện").orElse(null);

                                if (sLeTan != null) {
                                        workerSkillRepository.save(WorkerSkill.builder()
                                                        .worker(worker)
                                                        .skill(sLeTan)
                                                        .proficiency(ExperienceLevel.ADVANCED)
                                                        .verificationTier(VerificationTier.AI_DOCUMENT)
                                                        .spVerifyAwarded(50)
                                                        .verifiedAt(now.minus(5, ChronoUnit.DAYS))
                                                        .build());
                                }
                                if (sPhucVu != null) {
                                        workerSkillRepository.save(WorkerSkill.builder()
                                                        .worker(worker)
                                                        .skill(sPhucVu)
                                                        .proficiency(ExperienceLevel.INTERMEDIATE)
                                                        .verificationTier(VerificationTier.SELF_DECLARED)
                                                        .spVerifyAwarded(0)
                                                        .build());
                                }
                                if (sHauCan != null) {
                                        workerSkillRepository.save(WorkerSkill.builder()
                                                        .worker(worker)
                                                        .skill(sHauCan)
                                                        .proficiency(ExperienceLevel.BEGINNER)
                                                        .verificationTier(VerificationTier.SELF_DECLARED)
                                                        .spVerifyAwarded(0)
                                                        .build());
                                }
                                log.info("==> [H2 Profile] Seeded demo WorkerSkills for worker {}",
                                                worker.getWorkerId());
                        }

                        if (workerSkillPointRepository.count() == 0) {
                                Skill sLeTan = skillRepository.findByName("Lễ tân / Check-in").orElse(null);
                                Skill sPhucVu = skillRepository.findByName("Phục vụ bàn").orElse(null);

                                workerSkillPointRepository.save(WorkerSkillPoint.builder()
                                                .worker(worker)
                                                .skill(sLeTan)
                                                .jobType("Lễ tân / Check-in")
                                                .spVerify(50)
                                                .spExperience(200)
                                                .spRating(110)
                                                .totalSp(360)
                                                .tier(SkillTier.GOLD)
                                                .totalShiftsCompleted(5)
                                                .totalShiftsCancelled(0)
                                                .avgRating(new BigDecimal("4.90"))
                                                .ratingCount(5)
                                                .build());

                                workerSkillPointRepository.save(WorkerSkillPoint.builder()
                                                .worker(worker)
                                                .skill(sPhucVu)
                                                .jobType("Phục vụ bàn")
                                                .spVerify(0)
                                                .spExperience(120)
                                                .spRating(60)
                                                .totalSp(180)
                                                .tier(SkillTier.SILVER)
                                                .totalShiftsCompleted(3)
                                                .totalShiftsCancelled(0)
                                                .avgRating(new BigDecimal("4.75"))
                                                .ratingCount(3)
                                                .build());
                                log.info("==> [H2 Profile] Seeded demo WorkerSkillPoints (GOLD & SILVER)");
                        }

                        if (skillVerificationRequestRepository.count() == 0) {
                                Skill sLeTan = skillRepository.findByName("Lễ tân / Check-in").orElse(null);
                                Skill sPhaChe = skillRepository.findByName("Pha chế (Bartender/Barista)").orElse(null);

                                skillVerificationRequestRepository.save(SkillVerificationRequest.builder()
                                                .worker(worker)
                                                .skill(sLeTan)
                                                .jobType("Lễ tân / Check-in")
                                                .documentUrl("https://storage.cani.vn/certificates/cert-hospitality-reception.jpg")
                                                .documentName("Chứng chỉ Nghiệp vụ Lễ tân Sự kiện & Khách sạn Quốc tế")
                                                .documentType("CERTIFICATE")
                                                .recipientName("NGUYỄN VĂN AN")
                                                .institution("Trường Cao đẳng Du lịch Sài Gòn")
                                                .issueDate("15/06/2025")
                                                .extractedSkills(
                                                                "[{\"skillName\":\"Nghiệp vụ Lễ tân Sự kiện\",\"taxonomyRole\":\"Lễ tân / Check-in\",\"confidenceScore\":0.95,\"evidenceSnippet\":\"Hoàn thành khóa đào tạo Nghiệp vụ Lễ tân Sự kiện\"}]")
                                                .hasSealOrSignature(true)
                                                .overallConfidence(new BigDecimal("0.92"))
                                                .routingDecision(RoutingDecision.ADMIN_REVIEW)
                                                .routingReason("Hồ sơ chứng chỉ mẫu đã được Admin thẩm định thủ công hợp lệ.")
                                                .status(SkillVerificationStatus.APPROVED)
                                                .spAwarded(25)
                                                .build());

                                skillVerificationRequestRepository.save(SkillVerificationRequest.builder()
                                                .worker(worker)
                                                .skill(sPhaChe)
                                                .jobType("Pha chế (Bartender/Barista)")
                                                .documentUrl("https://storage.cani.vn/certificates/cert-barista-basic.jpg")
                                                .documentName("Giấy chứng nhận Khóa học Pha chế Barista Chuyên nghiệp")
                                                .documentType("CERTIFICATE")
                                                .recipientName("NGUYỄN VĂN AN")
                                                .institution("Trung tâm Đào tạo Pha chế Á Âu")
                                                .issueDate("10/09/2025")
                                                .routingDecision(RoutingDecision.ADMIN_REVIEW)
                                                .routingReason("Chờ Admin / CSKH kiểm tra tệp chứng chỉ và duyệt thủ công.")
                                                .status(SkillVerificationStatus.PENDING_REVIEW)
                                                .spAwarded(0)
                                                .build());
                                log.info("==> [H2 Profile] Seeded 2 demo SkillVerificationRequests (APPROVED & PENDING_REVIEW)");
                        }
                }

                // 5. Seed Demo Events & Shifts for Job Radar Map
                if (shiftRepository.count() == 0) {
                        EventOrganizer defaultEo = eo != null ? eo
                                        : eventOrganizerRepository.findAll().stream().findFirst().orElse(null);
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

                                Skill sLeTanRef = skillRepository.findByName("Lễ tân / Check-in").orElse(null);
                                UUID[] shift1Skills = (sLeTanRef != null && sLeTanRef.getSkillId() != null)
                                                ? new UUID[] { sLeTanRef.getSkillId() }
                                                : null;

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
                                                .requiredSkills(shift1Skills)
                                                .emergency(false)
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
                                                .emergency(true)
                                                .build();

                                shiftRepository.saveAll(List.of(shift1, shift2, shift3));
                                log.info("==> [H2 Profile] Seeded {} demo Events and {} ACTIVE Shifts for Job Radar Map successfully.",
                                                3, 3);
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
                                                                .question("Điểm uy tín là gì và ảnh hưởng thế nào đến cơ hội nhận ca?")
                                                                .answer("Mỗi tài khoản bắt đầu với điểm uy tín nền tảng (khởi điểm 80–100 điểm tùy mức hoàn thiện hồ sơ). Khi bạn đi làm đúng giờ, hoàn thành tốt và nhận đánh giá tích cực, điểm uy tín sẽ tăng. Ngược lại, hủy ca sát giờ hoặc tự ý vắng mặt không báo trước sẽ bị trừ điểm nặng (tối đa 25 điểm/lần). Nếu điểm uy tín xuống dưới 60, hệ thống sẽ tạm ngừng gửi đề xuất ca tự động; dưới 20 điểm tài khoản sẽ bị khóa để đảm bảo chất lượng chung.")
                                                                .status(FaqStatus.PUBLISHED)
                                                                .publishedAt(Instant.now())
                                                                .build(),
                                                Faq.builder()
                                                                .category(FaqCategory.ACCOUNT)
                                                                .question("Làm sao để nâng hạng kỹ năng và tăng cơ hội nhận ca lương cao?")
                                                                .answer("Mỗi lần hoàn thành ca làm thuộc một nhóm công việc (Phục vụ bàn, Lễ tân, Thu ngân, Pha chế, Kỹ thuật sự kiện...), bạn sẽ được cộng điểm kinh nghiệm cho kỹ năng đó. Ngoài ra, bạn có thể tải lên chứng chỉ nghề nghiệp để hệ thống kiểm tra và gắn huy hiệu xác thực, giúp hồ sơ nổi bật hơn khi Ban tổ chức chọn nhân sự.")
                                                                .status(FaqStatus.PUBLISHED)
                                                                .publishedAt(Instant.now())
                                                                .build(),

                                                // SHIFT (3 câu)
                                                Faq.builder()
                                                                .category(FaqCategory.SHIFT)
                                                                .question("Hệ thống gợi ý và gửi ca làm việc phù hợp cho tôi như thế nào?")
                                                                .answer("Khi có sự kiện mới, CANi sẽ tự động đối chiếu với lịch rảnh, kỹ năng và khu vực bạn muốn làm việc (thường trong bán kính 15 km), sau đó ưu tiên gửi đề xuất dựa trên 4 tiêu chí: khoảng cách từ bạn đến địa điểm sự kiện (40%), điểm đánh giá từ các ban tổ chức trước đó (30%), số ca bạn đã hoàn thành (20%) và điểm uy tín tài khoản (10%). Nhờ vậy, bạn càng ở gần và giữ uy tín tốt thì cơ hội nhận ca phù hợp càng cao.")
                                                                .status(FaqStatus.PUBLISHED)
                                                                .publishedAt(Instant.now())
                                                                .build(),
                                                Faq.builder()
                                                                .category(FaqCategory.SHIFT)
                                                                .question("Cách quét mã QR Check-in và Check-out khi đi làm sự kiện?")
                                                                .answer("Khi đến địa điểm tổ chức đúng giờ quy định, bạn mở chi tiết ca làm trên ứng dụng CANi và bấm \"Quét QR Check-in\" để quét mã do Ban tổ chức hiển thị tại quầy điều phối. Cuối ca, sau khi hoàn tất công việc và xác nhận thù lao, bạn quét mã QR Check-out để hệ thống chốt giờ làm thực tế.")
                                                                .status(FaqStatus.PUBLISHED)
                                                                .publishedAt(Instant.now())
                                                                .build(),
                                                Faq.builder()
                                                                .category(FaqCategory.SHIFT)
                                                                .question("Tôi có thể hủy ca làm việc sau khi đã bấm Chấp nhận không?")
                                                                .answer("Người lao động có thể hủy ca tự do trước thời điểm bắt đầu ca trên 24 giờ mà không bị phạt. Nếu hủy ca trong vòng 4 đến 24 giờ trước ca làm, bạn sẽ bị trừ điểm uy tín. Nếu hủy ca sát giờ hoặc tự ý vắng mặt không báo trước, hệ thống sẽ ghi nhận lỗi No-show và trừ tối đa 25 điểm uy tín.")
                                                                .status(FaqStatus.PUBLISHED)
                                                                .publishedAt(Instant.now())
                                                                .build(),

                                                // PAYMENT (3 câu)
                                                Faq.builder()
                                                                .category(FaqCategory.PAYMENT)
                                                                .question("Hình thức trả thù lao trực tiếp trên CANi hoạt động ra sao?")
                                                                .answer("Ban tổ chức sẽ thanh toán thù lao trực tiếp cho bạn bằng chuyển khoản ngân hàng hoặc tiền mặt trong vòng 48 giờ sau khi kết thúc ca (CANi không giữ tiền hộ). Khi Ban tổ chức khai báo đã chuyển khoản và đính kèm chứng từ, bạn kiểm tra tài khoản rồi bấm xác nhận trên ứng dụng để hoàn tất.")
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
                                                                .build());
                                faqRepository.saveAll(faqs);
                                log.info("==> [H2 Profile] Seeded {} standard business FAQs successfully.",
                                                faqs.size());
                        }
                }
        }
}
