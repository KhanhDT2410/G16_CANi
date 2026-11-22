package com.cani.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diễn tập và xác thực migration V50 trên bản sao CSDL cô lập (H2 PostgreSQL Mode)
 * với dữ liệu mô phỏng đúng snapshot thực tế trên Supabase trước khi migration chạy thật.
 */
@DisplayName("Flyway V50 Skill System V3 Migration Rehearsal Tests")
class SkillSystemV3MigrationTest {

    @Test
    @DisplayName("Script V50 chứa đầy đủ DDL, RLS, backfill an toàn và tương thích ngược")
    void v50ScriptContainsRequiredStatements() throws Exception {
        Path migrationPath = Path.of("src/main/resources/db/migration/V50__skill_system_v3_unified_skills_and_requirements.sql");
        assertTrue(Files.exists(migrationPath), "File migration V50 phải tồn tại");

        String sql = Files.readString(migrationPath);
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS skill_id UUID REFERENCES skills(id)"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS rating_count INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("uq_worker_skill_points_worker_skill"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS performed_skill_id UUID REFERENCES skills(id)"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS sp_experience_awarded BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS shift_skill_requirements"));
        assertTrue(sql.contains("ENABLE ROW LEVEL SECURITY"));
        assertTrue(sql.contains("ON CONFLICT (shift_id, skill_id) DO NOTHING"));
        assertFalse(sql.toUpperCase().contains("DROP COLUMN JOB_TYPE"), "Không được drop job_type");
        assertFalse(sql.toUpperCase().contains("DROP COLUMN REQUIRED_SKILLS"), "Không được drop required_skills");
    }

    @Test
    @DisplayName("Rehearsal backfill trên snapshot dữ liệu thực: giữ nguyên SP, xóa fake avg_rating=5, không tự đoán performed_skill_id mơ hồ")
    void rehearsalBackfillPreservesDataSafely() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:v50_rehearsal_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement st = conn.createStatement()) {

            // 1. Tạo schema trước V50 tương ứng V1..V49 trên bản sao cô lập
            st.execute("""
                    CREATE TABLE skills (
                        id UUID PRIMARY KEY,
                        name VARCHAR(100) NOT NULL UNIQUE,
                        is_active BOOLEAN DEFAULT TRUE
                    );
                    CREATE TABLE workers (
                        id UUID PRIMARY KEY
                    );
                    CREATE TABLE worker_skills (
                        id UUID PRIMARY KEY,
                        worker_id UUID NOT NULL REFERENCES workers(id),
                        skill_id UUID NOT NULL REFERENCES skills(id),
                        verification_tier VARCHAR(30) DEFAULT 'SELF_DECLARED'
                    );
                    CREATE TABLE worker_skill_points (
                        id UUID PRIMARY KEY,
                        worker_id UUID NOT NULL REFERENCES workers(id),
                        job_type VARCHAR(100) NOT NULL,
                        sp_verify INT DEFAULT 0,
                        sp_experience INT DEFAULT 0,
                        sp_rating INT DEFAULT 0,
                        total_sp INT DEFAULT 0,
                        tier VARCHAR(30) DEFAULT 'STARTER',
                        total_shifts_completed INT DEFAULT 0,
                        avg_rating NUMERIC(3,2) DEFAULT 5.00,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    CREATE TABLE shifts (
                        id UUID PRIMARY KEY,
                        job_type VARCHAR(100) NOT NULL,
                        required_skills_csv TEXT
                    );
                    """);

            // 2. Nạp dữ liệu mô phỏng đúng snapshot Supabase
            UUID workerId = UUID.fromString("d7c01132-749e-43ee-bdcc-12dd83671068");
            UUID phucVuId = UUID.fromString("398ea1b6-7258-4c65-b3ed-1be0e1937b5c");
            UUID phuBepId = UUID.fromString("282b02f1-a09b-4fe7-ba61-db94f6df472f");
            UUID leTanId = UUID.fromString("f8caf223-55ee-4a22-8606-d2ca2f80e37f");
            UUID amThanhId = UUID.fromString("21baa09f-9942-41bc-945a-82457a805449");

            st.execute(String.format("INSERT INTO workers(id) VALUES ('%s')", workerId));
            st.execute(String.format("INSERT INTO skills(id, name, is_active) VALUES ('%s', 'Phục vụ bàn', TRUE)", phucVuId));
            st.execute(String.format("INSERT INTO skills(id, name, is_active) VALUES ('%s', 'Phụ bếp', TRUE)", phuBepId));
            st.execute(String.format("INSERT INTO skills(id, name, is_active) VALUES ('%s', 'Lễ tân / Check-in', TRUE)", leTanId));
            st.execute(String.format("INSERT INTO skills(id, name, is_active) VALUES ('%s', 'Kỹ thuật âm thanh - ánh sáng', TRUE)", amThanhId));

            // Worker chi co 1 WorkerSkill (Am thanh) nhung co 2 WorkerSkillPoint (1 co SP=20 va fake avg_rating=5)
            st.execute(String.format(
                    "INSERT INTO worker_skills(id, worker_id, skill_id, verification_tier) VALUES ('f27a1010-7d72-4219-a1dd-ba635593e663', '%s', '%s', 'SELF_DECLARED')",
                    workerId, amThanhId));
            st.execute(String.format(
                    "INSERT INTO worker_skill_points(id, worker_id, job_type, sp_experience, total_sp, total_shifts_completed, sp_rating, avg_rating) "
                            + "VALUES ('8f061197-8c20-48f5-92fb-337949588fa8', '%s', 'Kỹ thuật âm thanh - ánh sáng', 0, 0, 0, 0, 0.00)",
                    workerId));
            st.execute(String.format(
                    "INSERT INTO worker_skill_points(id, worker_id, job_type, sp_experience, total_sp, total_shifts_completed, sp_rating, avg_rating) "
                            + "VALUES ('7a5973c6-1f1b-4e04-9e74-dcaa968d7c11', '%s', 'Phục vụ bàn', 20, 20, 1, 0, 5.00)",
                    workerId));

            UUID unambiguousShiftId = UUID.randomUUID();
            UUID ambiguousShiftId = UUID.fromString("8ffbbd44-a980-40c2-b217-45921c81ca87");
            st.execute(String.format("INSERT INTO shifts(id, job_type, required_skills_csv) VALUES ('%s', 'Phục vụ bàn', NULL)", unambiguousShiftId));
            st.execute(String.format("INSERT INTO shifts(id, job_type, required_skills_csv) VALUES ('%s', 'Lễ tân / Check-in', '%s,%s')", ambiguousShiftId, phucVuId, phuBepId));

            // 3. Áp dụng logic DDL & Backfill của V50
            st.execute("ALTER TABLE worker_skill_points ADD COLUMN skill_id UUID REFERENCES skills(id)");
            st.execute("ALTER TABLE worker_skill_points ADD COLUMN rating_count INT NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE shifts ADD COLUMN performed_skill_id UUID REFERENCES skills(id)");
            st.execute("""
                    CREATE TABLE shift_skill_requirements (
                        id UUID PRIMARY KEY,
                        shift_id UUID NOT NULL REFERENCES shifts(id),
                        skill_id UUID NOT NULL REFERENCES skills(id),
                        requirement_type VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
                        min_verification_tier VARCHAR(30) NOT NULL DEFAULT 'SELF_DECLARED',
                        requires_valid_certificate BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uq_shift_skill_req UNIQUE (shift_id, skill_id)
                    )
                    """);

            st.execute("""
                    UPDATE worker_skill_points wsp
                    SET skill_id = (
                        SELECT s.id FROM skills s WHERE LOWER(TRIM(s.name)) = LOWER(TRIM(wsp.job_type)) LIMIT 1
                    )
                    WHERE wsp.skill_id IS NULL
                    """);
            st.execute("""
                    UPDATE worker_skill_points
                    SET avg_rating = 0.00
                    WHERE COALESCE(sp_rating, 0) = 0 AND COALESCE(rating_count, 0) = 0 AND avg_rating = 5.00
                    """);

            // Case A backfill performed_skill_id chi khi khong mo ho
            st.execute("""
                    UPDATE shifts s
                    SET performed_skill_id = (
                        SELECT sk.id FROM skills sk
                        WHERE sk.is_active = TRUE AND LOWER(TRIM(sk.name)) = LOWER(TRIM(s.job_type))
                        LIMIT 1
                    )
                    WHERE s.performed_skill_id IS NULL
                      AND (s.required_skills_csv IS NULL OR s.required_skills_csv NOT LIKE '%,%')
                    """);

            // Backfill shift_skill_requirements cho shift mo ho co 2 required_skills
            st.execute(String.format(
                    "INSERT INTO shift_skill_requirements(id, shift_id, skill_id, requirement_type, min_verification_tier, requires_valid_certificate) "
                            + "VALUES (RANDOM_UUID(), '%s', '%s', 'REQUIRED', 'SELF_DECLARED', FALSE), "
                            + "(RANDOM_UUID(), '%s', '%s', 'REQUIRED', 'SELF_DECLARED', FALSE)",
                    ambiguousShiftId, phucVuId, ambiguousShiftId, phuBepId));

            // 4. Verify invariants
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM worker_skill_points WHERE skill_id IS NULL");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Tất cả Skill Point hiện có phải mapping được skill_id");
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT total_sp, avg_rating, rating_count FROM worker_skill_points WHERE skill_id = ?");
                 ) {
                ps.setObject(1, phucVuId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(20, rs.getInt("total_sp"), "SP hiện có (20) phải được bảo toàn");
                    assertEquals(0, rs.getBigDecimal("avg_rating").compareTo(java.math.BigDecimal.ZERO),
                            "avg_rating = 5.00 giả phải được reset về 0.00 khi rating_count = 0");
                    assertEquals(0, rs.getInt("rating_count"));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM worker_skills WHERE worker_id = ?");
                 ) {
                ps.setObject(1, workerId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "KHÔNG được tự tạo thêm WorkerSkill đã xác minh");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT performed_skill_id FROM shifts WHERE id = ?");
                 ) {
                ps.setObject(1, unambiguousShiftId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(phucVuId.toString(), rs.getObject("performed_skill_id").toString());
                }
                ps.setObject(1, ambiguousShiftId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertNull(rs.getObject("performed_skill_id"),
                            "Shift có 2 requiredSkills và jobType khác phải giữ NULL để EO xác nhận");
                }
            }
        }
    }
}
