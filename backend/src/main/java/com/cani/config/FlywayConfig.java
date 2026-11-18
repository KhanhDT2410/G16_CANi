package com.cani.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Flyway Migration Strategy
 * Tự động chạy flyway.repair() trước khi migrate để tự chữa lành lỗi lệch checksum
 * do định dạng xuống dòng (CRLF/LF trên Windows) hoặc cập nhật chú thích SQL.
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return (Flyway flyway) -> {
            log.info("[FlywayConfig] Tự động sửa lỗi checksum và đồng bộ schema history (repair)...");
            flyway.repair();
            log.info("[FlywayConfig] Tiến hành thực thi migrations mới (migrate)...");
            flyway.migrate();
        };
    }
}
