-- =============================================================================
-- V17: Bảng ghi nhận cảnh báo vi phạm SLA (SLA Monitoring & Alerting)
-- - Đảm bảo tính Idempotent cho Background Job kiểm soát hạn chót Ticket & Dispute
-- =============================================================================

CREATE TABLE sla_alert_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    breach_stage VARCHAR(50) NOT NULL,
    alerted_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sla_alert_log UNIQUE (entity_type, entity_id, breach_stage)
);

CREATE INDEX idx_sla_alert_lookup ON sla_alert_logs(entity_type, entity_id, breach_stage);

-- RLS deny-all dự phòng
ALTER TABLE sla_alert_logs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON sla_alert_logs;
DROP POLICY IF EXISTS "deny_authenticated" ON sla_alert_logs;
CREATE POLICY "deny_anon" ON sla_alert_logs FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON sla_alert_logs FOR ALL TO authenticated USING (false);

GRANT ALL ON sla_alert_logs TO postgres;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON sla_alert_logs TO service_role;
    END IF;
END $$;

