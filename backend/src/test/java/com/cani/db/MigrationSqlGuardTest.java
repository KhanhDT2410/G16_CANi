package com.cani.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guard test để đảm bảo migration V47 (PostgreSQL partial unique index)
 * được định nghĩa đúng cấu trúc chống trùng assignment active.
 *
 * Lưu ý kỹ thuật:
 * H2 in-memory DB không hỗ trợ cú pháp partial index (WHERE clause trên index)
 * của PostgreSQL, do đó test môi trường unit/integration với H2 không chạy
 * index này. Guard test này xác nhận tính đúng đắn của DDL script trước khi apply trên staging/production.
 */
class MigrationSqlGuardTest {

    private static final String MIGRATION_FILE = "/db/migration/V47__unique_active_shift_assignment.sql";

    @Test
    @DisplayName("V47 - Kiểm tra cú pháp Partial Unique Index cho Shift Assignment Active")
    void v47_containsRequiredPartialUniqueIndexStructure() throws IOException {
        try (InputStream is = getClass().getResourceAsStream(MIGRATION_FILE)) {
            assertNotNull(is, "File migration " + MIGRATION_FILE + " phải tồn tại trong classpath resources");
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_active_shift_worker_assignment"),
                    "V47 phải tạo unique index uq_active_shift_worker_assignment");
            assertTrue(sql.contains("ON shift_assignments (shift_id, worker_id)"),
                    "V47 phải đánh index trên (shift_id, worker_id)");
            assertTrue(sql.contains("WHERE status IN"),
                    "V47 phải có mệnh đề WHERE status IN để tạo partial index");
            assertTrue(sql.contains("'PENDING'"), "V47 phải bao gồm status PENDING");
            assertTrue(sql.contains("'CONFIRMED'"), "V47 phải bao gồm status CONFIRMED");
            assertTrue(sql.contains("'CHECKED_IN'"), "V47 phải bao gồm status CHECKED_IN");
            assertFalse(sql.contains("'REJECTED'"), "V47 không được bao gồm status REJECTED (thợ bị từ chối được phép nhận ca khác)");
        }
    }
}
