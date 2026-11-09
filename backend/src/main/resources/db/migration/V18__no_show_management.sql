-- =============================================================================
-- V18: Quản lý Vi phạm Vắng mặt không báo trước (No-show - SRS FE-09, UC-88)
-- - Bảng no_show_records: Lưu trữ vết và thống kê các ca vi phạm No-show
-- =============================================================================

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'no_show_detected_by') THEN
        CREATE TYPE no_show_detected_by AS ENUM ('SYSTEM_CRON', 'ADMIN', 'CUSTOMER_CARE', 'EVENT_ORGANIZER');
    END IF;
END $$;

CREATE TABLE no_show_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL REFERENCES shift_assignments(id) ON DELETE CASCADE,
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    shift_id UUID NOT NULL REFERENCES shifts(id) ON DELETE CASCADE,
    event_id UUID REFERENCES events(id) ON DELETE SET NULL,
    detected_by no_show_detected_by NOT NULL DEFAULT 'SYSTEM_CRON',
    detected_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    rs_penalty_log_id UUID REFERENCES reliability_logs(id) ON DELETE SET NULL,
    penalty_amount INTEGER NOT NULL DEFAULT 25,
    is_account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    emergency_dispatched BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_no_show_assignment UNIQUE (assignment_id)
);

CREATE INDEX idx_no_show_worker_id ON no_show_records(worker_id);
CREATE INDEX idx_no_show_shift_id ON no_show_records(shift_id);
CREATE INDEX idx_no_show_detected_at ON no_show_records(detected_at);
CREATE INDEX idx_no_show_penalty_log ON no_show_records(rs_penalty_log_id);

CREATE TRIGGER trg_no_show_records_updated_at BEFORE UPDATE ON no_show_records
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- RLS deny-all dự phòng
ALTER TABLE no_show_records ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON no_show_records;
DROP POLICY IF EXISTS "deny_authenticated" ON no_show_records;
CREATE POLICY "deny_anon" ON no_show_records FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON no_show_records FOR ALL TO authenticated USING (false);

GRANT ALL ON no_show_records TO postgres;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON no_show_records TO service_role;
    END IF;
END $$;

