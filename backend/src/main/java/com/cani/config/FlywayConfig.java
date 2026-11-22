package com.cani.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Flyway Migration Strategy.
 * Không tự động chạy flyway.repair() khi startup để tránh ghi đè/sửa đổi lịch sử
 * migration flyway_schema_history trên môi trường cơ sở dữ liệu chung (Supabase).
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return (Flyway flyway) -> {
            log.info("[FlywayConfig] Tiến hành thực thi migrations mới (migrate)...");
            flyway.migrate();
        };
    }
}
