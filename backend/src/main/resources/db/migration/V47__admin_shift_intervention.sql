-- V47__admin_shift_intervention.sql
-- Bổ sung trạng thái SUSPENDED cho ca làm việc và các cột lưu vết can thiệp của Quản trị viên

DO $$ BEGIN
    ALTER TYPE shift_status ADD VALUE IF NOT EXISTS 'SUSPENDED';
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

ALTER TABLE shifts ADD COLUMN IF NOT EXISTS admin_note TEXT;
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS cancelled_by UUID REFERENCES users(id);
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ;
