-- =============================================================================
-- V16: Quản lý Kháng cáo điểm uy tín Worker (SRS FE-10, UC-94: RS Penalty & Appeal)
-- - Bảng rs_appeals: lưu thông tin kháng cáo điểm phạt RS của Worker
-- =============================================================================

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'rs_appeal_status') THEN
        CREATE TYPE rs_appeal_status AS ENUM ('SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED');
    END IF;
END $$;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'notif_type') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'RS_APPEAL' AND enumtypid = 'notif_type'::regtype) THEN
            ALTER TYPE notif_type ADD VALUE 'RS_APPEAL';
        END IF;
    END IF;
END $$;

CREATE TABLE rs_appeals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    reliability_log_id UUID NOT NULL UNIQUE REFERENCES reliability_logs(id) ON DELETE CASCADE,
    penalty_amount INTEGER NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    reason_detail TEXT NOT NULL,
    evidence TEXT,
    status rs_appeal_status NOT NULL DEFAULT 'SUBMITTED',
    reviewer_id UUID REFERENCES users(id),
    decision_note TEXT,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rs_appeals_worker_id ON rs_appeals(worker_id);
CREATE INDEX idx_rs_appeals_status ON rs_appeals(status);
CREATE INDEX idx_rs_appeals_log_id ON rs_appeals(reliability_log_id);

CREATE TRIGGER trg_rs_appeals_updated_at BEFORE UPDATE ON rs_appeals
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- RLS deny-all dự phòng (đồng bộ convention V6/V13/V15)
ALTER TABLE rs_appeals ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON rs_appeals;
DROP POLICY IF EXISTS "deny_authenticated" ON rs_appeals;
CREATE POLICY "deny_anon" ON rs_appeals FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON rs_appeals FOR ALL TO authenticated USING (false);

GRANT ALL ON rs_appeals TO postgres;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON rs_appeals TO service_role;
    END IF;
END $$;

