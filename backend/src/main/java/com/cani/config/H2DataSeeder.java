package com.cani.config;

import com.cani.entity.Skill;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.SkillRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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
    }
}
