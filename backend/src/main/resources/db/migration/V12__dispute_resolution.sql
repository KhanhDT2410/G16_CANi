-- =============================================================================
-- V11: Dispute & Resolution theo SRS (UC-96 → UC-104, BR-52..59, BR-63, BR-68)
-- - Thay enum dispute_status / dispute_type bằng bộ giá trị đúng SRS
-- - Thêm cột nghiệp vụ: gán CC, SLA, kết quả, lý do, escalation, quyết định cuối
-- - Bảng bằng chứng riêng (UC-98)
-- Chỉ chạy trên PostgreSQL/Supabase (profile h2 dùng ddl-auto + h2-init.sql, flyway tắt).
-- =============================================================================

-- 1. Enum mới (đúng SRS)
CREATE TYPE dispute_status_new AS ENUM (
    'OPEN',
    'INVESTIGATING',
    'RESOLVED',
    'UNVERIFIED',
    'ESCALATED',
    'FINAL_RESOLVED'
);

CREATE TYPE dispute_type_new AS ENUM (
    'PAYMENT',
    'WORK_QUALITY',
    'ATTENDANCE',
    'CONDUCT',
    'OTHER'
);

CREATE TYPE dispute_outcome AS ENUM (
    'EO_FAULT',
    'WORKER_FAULT',
    'PARTIAL',
    'UNVERIFIED',
    'UPHOLD'
);

CREATE TYPE dispute_evidence_type AS ENUM (
    'IMAGE',
    'VIDEO',
    'DOCUMENT',
    'TRANSACTION_RECORD'
);

-- 2. Bảng bằng chứng (UC-98, BR-56: giữ 36 tháng)
CREATE TABLE dispute_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id UUID NOT NULL REFERENCES disputes(id),
    party UUID NOT NULL REFERENCES users(id),
    file_type dispute_evidence_type,
    file_url VARCHAR(500),
    label VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_dispute_evidence_dispute ON dispute_evidence(dispute_id);
CREATE INDEX idx_dispute_evidence_party ON dispute_evidence(party);

-- 3. Cột nghiệp vụ mới trên disputes
ALTER TABLE disputes
    ADD COLUMN assigned_to UUID REFERENCES users(id),
    ADD COLUMN sla_deadline TIMESTAMPTZ,
    ADD COLUMN outcome dispute_outcome,
    ADD COLUMN decision_rationale TEXT,
    ADD COLUMN escalation_reason TEXT,
    ADD COLUMN escalated_by UUID REFERENCES users(id),
    ADD COLUMN escalated_at TIMESTAMPTZ,
    ADD COLUMN final_decision_rationale TEXT,
    ADD COLUMN final_decided_by UUID REFERENCES users(id),
    ADD COLUMN final_decided_at TIMESTAMPTZ,
    ADD COLUMN rs_penalty_applied BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_disputes_status ON disputes(status);
CREATE INDEX idx_disputes_complainant ON disputes(complainant_id);
CREATE INDEX idx_disputes_respondent ON disputes(respondent_id);
CREATE INDEX idx_disputes_assignment ON disputes(assignment_id);
CREATE INDEX idx_disputes_assigned_to ON disputes(assigned_to);
CREATE INDEX idx_disputes_sla_deadline ON disputes(sla_deadline);

-- 4. Migrate giá trị enum cũ → mới (an toàn khi DB đã có dữ liệu từ V1)
-- 4a. dispute_status
ALTER TABLE disputes ADD COLUMN status_migrated dispute_status_new;
UPDATE disputes SET status_migrated = CASE status::TEXT
    WHEN 'SUBMITTED' THEN 'OPEN'::dispute_status_new
    WHEN 'IN_REVIEW' THEN 'INVESTIGATING'::dispute_status_new
    WHEN 'RESOLVED' THEN 'RESOLVED'::dispute_status_new
    WHEN 'ESCALATED' THEN 'ESCALATED'::dispute_status_new
    ELSE 'OPEN'::dispute_status_new
END;
ALTER TABLE disputes DROP COLUMN status;
ALTER TABLE disputes RENAME COLUMN status_migrated TO status;
ALTER TABLE disputes ALTER COLUMN status SET NOT NULL;
ALTER TABLE disputes ALTER COLUMN status SET DEFAULT 'OPEN';
ALTER TYPE dispute_status RENAME TO dispute_status_old;
ALTER TYPE dispute_status_new RENAME TO dispute_status;
DROP TYPE dispute_status_old;

-- 4b. dispute_type
ALTER TABLE disputes ADD COLUMN type_migrated dispute_type_new;
UPDATE disputes SET type_migrated = CASE type::TEXT
    WHEN 'PAYMENT' THEN 'PAYMENT'::dispute_type_new
    WHEN 'CONDUCT' THEN 'CONDUCT'::dispute_type_new
    WHEN 'OVERTIME' THEN 'CONDUCT'::dispute_type_new
    ELSE 'OTHER'::dispute_type_new
END;
ALTER TABLE disputes DROP COLUMN type;
ALTER TABLE disputes RENAME COLUMN type_migrated TO type;
ALTER TYPE dispute_type RENAME TO dispute_type_old;
ALTER TYPE dispute_type_new RENAME TO dispute_type;
DROP TYPE dispute_type_old;

-- 5. RLS deny-all dự phòng cho bảng mới (V6 chỉ phủ các bảng tồn tại tại thời điểm V6)
ALTER TABLE dispute_evidence ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON dispute_evidence;
DROP POLICY IF EXISTS "deny_authenticated" ON dispute_evidence;
CREATE POLICY "deny_anon" ON dispute_evidence FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON dispute_evidence FOR ALL TO authenticated USING (false);

-- 6. Cấp quyền backend (đồng bộ V6: postgres luôn, service_role nếu có)
GRANT ALL ON dispute_evidence TO postgres;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON dispute_evidence TO service_role;
    END IF;
END $$;
