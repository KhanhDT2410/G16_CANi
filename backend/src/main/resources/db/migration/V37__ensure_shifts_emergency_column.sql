-- =============================================================================
-- Migration V37: Đảm bảo cột emergency tồn tại trên bảng shifts
-- Tránh lỗi Hibernate Schema-validation khi V35 bị bỏ qua do đã có trong history
-- =============================================================================

ALTER TABLE shifts ADD COLUMN IF NOT EXISTS emergency BOOLEAN NOT NULL DEFAULT FALSE;
