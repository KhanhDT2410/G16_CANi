-- =============================================================================
-- Migration: V46__create_account_appeals.sql
-- Description: Bảng lưu đơn giải trình / khiếu nại khi tài khoản bị khóa (Locked Account Appeal)
--              Phục vụ Event Organizer (và Worker) gửi giải trình kèm tài liệu khắc phục lên Admin.
-- =============================================================================

CREATE TABLE IF NOT EXISTS account_appeals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    user_role VARCHAR(50) NOT NULL,
    reason_category VARCHAR(100) NOT NULL,
    appeal_content TEXT NOT NULL,
    attachment_urls TEXT,
    lock_reason_snapshot TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'SUBMITTED',
    reviewer_id UUID REFERENCES users(id),
    admin_notes TEXT,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_account_appeals_user_id ON account_appeals(user_id);
CREATE INDEX IF NOT EXISTS idx_account_appeals_email ON account_appeals(email);
CREATE INDEX IF NOT EXISTS idx_account_appeals_status ON account_appeals(status);
CREATE INDEX IF NOT EXISTS idx_account_appeals_created_at ON account_appeals(created_at);

-- RLS deny-all đồng bộ với convention của CANi
ALTER TABLE account_appeals ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON account_appeals;
DROP POLICY IF EXISTS "deny_authenticated" ON account_appeals;
CREATE POLICY "deny_anon" ON account_appeals FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON account_appeals FOR ALL TO authenticated USING (false);

GRANT ALL ON account_appeals TO postgres;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON account_appeals TO service_role;
    END IF;
END $$;
