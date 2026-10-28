package com.cani.config;

import com.cani.entity.EventOrganizer;
import com.cani.entity.Skill;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.entity.enums.VerificationStatus;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.SkillRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
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
                .verificationStatus(VerificationStatus.VERIFIED)
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
    }
}
