-- =============================================================================
-- Flyway Migration: V4__worker_skill_verification.sql
-- Description: Bảng lưu trữ yêu cầu và kết quả thẩm định kỹ năng qua AI Pipeline (UC-43 & UC-46)
-- =============================================================================

CREATE TABLE IF NOT EXISTS skill_verification_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    skill_id UUID REFERENCES skills(id) ON DELETE SET NULL,
    job_type VARCHAR(100),
    document_url TEXT,
    document_name VARCHAR(255),
    document_type VARCHAR(100),
    recipient_name VARCHAR(255),
    identity_number VARCHAR(50),
    institution VARCHAR(255),
    issue_date VARCHAR(50),
    extracted_skills JSONB,
    has_seal_or_signature BOOLEAN DEFAULT FALSE,
    overall_confidence NUMERIC(3, 2) DEFAULT 0.00,
    routing_decision VARCHAR(50) NOT NULL,
    routing_reason TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    reviewed_by UUID REFERENCES users(id),
    review_notes TEXT,
    sp_awarded INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_skill_verif_worker ON skill_verification_requests(worker_id);
CREATE INDEX IF NOT EXISTS idx_skill_verif_status ON skill_verification_requests(status);

-- Bật RLS và cấp quyền bảo vệ
ALTER TABLE skill_verification_requests ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON skill_verification_requests;
DROP POLICY IF EXISTS "deny_authenticated" ON skill_verification_requests;
CREATE POLICY "deny_anon" ON skill_verification_requests FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON skill_verification_requests FOR ALL TO authenticated USING (false);

GRANT ALL ON TABLE skill_verification_requests TO postgres, service_role;
