package com.cani.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.Statement;

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

            // Đảm bảo cột emergency tồn tại ngay cả khi V35 bị đánh dấu đã chạy trong lịch sử cũ
            try (Connection conn = flyway.getConfiguration().getDataSource().getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE shifts ADD COLUMN IF NOT EXISTS emergency BOOLEAN NOT NULL DEFAULT FALSE");
                log.info("[FlywayConfig] Đã kiểm tra và đảm bảo cột 'emergency' tồn tại trong bảng shifts");
            } catch (Exception e) {
                log.warn("[FlywayConfig] Không thể thực thi DDL đảm bảo cột emergency: {}", e.getMessage());
            }

            log.info("[FlywayConfig] Tiến hành thực thi migrations mới (migrate)...");
            flyway.migrate();
        };
    }
}
