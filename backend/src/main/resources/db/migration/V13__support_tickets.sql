-- =============================================================================
-- V13: Support Tickets theo SRS (UC-105, UC-106, SCR-25, SCR-26, BR-58, BR-70)
-- - Bảng support_tickets: lưu thông tin ticket, danh mục, mức ưu tiên, hạn SLA
-- - Bảng support_ticket_messages: lưu thread trao đổi & ghi chú nội bộ CC (is_internal)
-- =============================================================================

CREATE TYPE ticket_status AS ENUM ('OPEN', 'IN_PROGRESS', 'CLOSED');
CREATE TYPE ticket_category AS ENUM ('ACCOUNT', 'BILLING', 'TECHNICAL', 'GENERAL', 'OTHER');
CREATE TYPE ticket_priority AS ENUM ('HIGH', 'MEDIUM', 'LOW');
ALTER TYPE notif_type ADD VALUE 'SUPPORT_TICKET';

CREATE TABLE support_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_no VARCHAR(30) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id),
    category ticket_category NOT NULL,
    priority ticket_priority NOT NULL,
    subject VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    attachments JSONB,
    status ticket_status NOT NULL DEFAULT 'OPEN',
    assigned_to UUID REFERENCES users(id),
    sla_deadline TIMESTAMPTZ NOT NULL,
    resolved_by UUID REFERENCES users(id),
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_st_tickets_user ON support_tickets(user_id);
CREATE INDEX idx_st_tickets_status ON support_tickets(status);
CREATE INDEX idx_st_tickets_assigned ON support_tickets(assigned_to);
CREATE INDEX idx_st_tickets_sla ON support_tickets(sla_deadline);

CREATE TRIGGER trg_st_tickets_updated_at BEFORE UPDATE ON support_tickets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE support_ticket_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES support_tickets(id),
    sender_id UUID NOT NULL REFERENCES users(id),
    body TEXT NOT NULL,
    is_internal BOOLEAN NOT NULL DEFAULT FALSE,
    attachment_url VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_st_messages_ticket ON support_ticket_messages(ticket_id);

-- RLS deny-all dự phòng (đồng bộ convention V6)
ALTER TABLE support_tickets ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON support_tickets;
DROP POLICY IF EXISTS "deny_authenticated" ON support_tickets;
CREATE POLICY "deny_anon" ON support_tickets FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON support_tickets FOR ALL TO authenticated USING (false);

ALTER TABLE support_ticket_messages ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON support_ticket_messages;
DROP POLICY IF EXISTS "deny_authenticated" ON support_ticket_messages;
CREATE POLICY "deny_anon" ON support_ticket_messages FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON support_ticket_messages FOR ALL TO authenticated USING (false);

GRANT ALL ON support_tickets, support_ticket_messages TO postgres;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON support_tickets, support_ticket_messages TO service_role;
    END IF;
END $$;

