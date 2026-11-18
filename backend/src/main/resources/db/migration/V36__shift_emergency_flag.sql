-- =============================================================================
-- Migration V36: Bổ sung cờ khẩn cấp (emergency) cho bảng shifts
-- Chỉ khi emergency = TRUE, ca làm việc mới cho phép Worker ứng tuyển trực tiếp (free-pick) trên Job Radar
-- =============================================================================

ALTER TABLE shifts ADD COLUMN IF NOT EXISTS emergency BOOLEAN NOT NULL DEFAULT FALSE;
