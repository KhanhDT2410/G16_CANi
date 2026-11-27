-- =============================================================================
-- Migration V75: Nâng cấp cấu hình Điều phối khẩn cấp (Emergency Dispatch)
-- - Lưu bán kính khẩn cấp EO đã chọn, chế độ nới lỏng kỹ năng có kiểm soát,
--   sàn điểm uy tín riêng cho đợt khẩn cấp, phụ cấp khẩn cấp và mốc thời gian
--   kích hoạt gần nhất để hỗ trợ cooldown & re-dispatch.
-- - Snapshot emergency_bonus vào từng shift_assignments phát sinh trong đợt
--   khẩn cấp để không làm thay đổi thỏa thuận thù lao của nhân sự đã chốt trước đó.
-- =============================================================================

ALTER TABLE shifts
    ADD COLUMN IF NOT EXISTS emergency_radius_km DOUBLE PRECISION NULL,
    ADD COLUMN IF NOT EXISTS emergency_skill_relaxation VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS emergency_min_rs INTEGER NULL,
    ADD COLUMN IF NOT EXISTS emergency_bonus NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS emergency_triggered_at TIMESTAMPTZ NULL;

ALTER TABLE shifts
    DROP CONSTRAINT IF EXISTS chk_shifts_emergency_skill_relaxation;
ALTER TABLE shifts
    ADD CONSTRAINT chk_shifts_emergency_skill_relaxation
    CHECK (emergency_skill_relaxation IN ('NONE', 'LOWER_TIER', 'PREFER_ONLY'));

ALTER TABLE shifts
    DROP CONSTRAINT IF EXISTS chk_shifts_emergency_bonus_non_negative;
ALTER TABLE shifts
    ADD CONSTRAINT chk_shifts_emergency_bonus_non_negative
    CHECK (emergency_bonus >= 0);

ALTER TABLE shift_assignments
    ADD COLUMN IF NOT EXISTS emergency_bonus NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE shift_assignments
    DROP CONSTRAINT IF EXISTS chk_shift_assignments_emergency_bonus_non_negative;
ALTER TABLE shift_assignments
    ADD CONSTRAINT chk_shift_assignments_emergency_bonus_non_negative
    CHECK (emergency_bonus >= 0);
