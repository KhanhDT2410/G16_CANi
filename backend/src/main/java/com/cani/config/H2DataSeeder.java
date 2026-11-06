package com.cani.config;

import com.cani.entity.Event;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Shift;
import com.cani.entity.Skill;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.EventMode;
import com.cani.entity.enums.EventStatus;
import com.cani.entity.enums.ExperienceLevel;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.entity.enums.VerificationStatus;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.EventRepository;
import com.cani.repository.ShiftRepository;
import com.cani.repository.SkillRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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

    @Override
    public void run(String... args) {
        log.info("==> [H2 Profile] Seeding local taxonomy skills and standard demo accounts (Password: Password123!)...");

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

        // 2. Seed Event Organizer user (eo@cani.vn)
        String eoEmail = "eo@cani.vn";
        if (!userRepository.existsByEmail(eoEmail)) {
            User user = User.builder()
                .userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .email(eoEmail)
                .fullName("Phạm Duy Hưng (EO)")
                .role(UserRole.EVENT_ORGANIZER)
                .status(UserStatus.ACTIVE)
                .phone("0912345678")
                .emailVerified(true)
                .build();
            User savedUser = userRepository.save(user);

            EventOrganizer eo = EventOrganizer.builder()
                .user(savedUser)
                .companyName("CANi Event Management")
                .taxId("0109876543")
                .address("799 Nguyễn Văn Linh, Quận 7, TP.HCM")
                .verificationStatus(VerificationStatus.APPROVED)
                .verifiedAt(Instant.now())
                .build();
            eventOrganizerRepository.save(eo);
            log.info("==> [H2 Profile] Seeded default EO user: {} (id: {})", eoEmail, savedUser.getUserId());
        }

        // 3. Seed Customer Care user (cskh@cani.vn)
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

        // 4. Seed Admin user (admin@cani.vn)
        String adminEmail = "admin@cani.vn";
        if (!userRepository.existsByEmail(adminEmail)) {
            User user = User.builder()
                .userId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .email(adminEmail)
                .fullName("Quản Trị Viên Hệ Thống")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .phone("0909999999")
                .emailVerified(true)
                .build();
            userRepository.save(user);
            log.info("==> [H2 Profile] Seeded default Admin user: {}", adminEmail);
        }

        // 5. Seed Demo Events & Active Shifts for Job Radar Map
        if (eventRepository.count() == 0) {
            EventOrganizer defaultEo = eventOrganizerRepository.findAll().stream().findFirst().orElse(null);
            if (defaultEo != null) {
                // Event 1: Gem Center Q1 (~1.2 km from center)
                Event event1 = Event.builder()
                        .eventOrganizer(defaultEo)
                        .name("Hội Nghị Thượng Đỉnh Công Nghệ AI 2026")
                        .description("Sự kiện công nghệ quy tụ hơn 1,000 chuyên gia và lãnh đạo doanh nghiệp")
                        .venueAddress("GEM Center, 8 Nguyễn Bỉnh Khiêm, Đa Kao, Quận 1, TP.HCM")
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
                        .description("Đón tiếp khách VIP, hướng dẫn check-in mã QR tại sảnh chính")
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
    }
}
