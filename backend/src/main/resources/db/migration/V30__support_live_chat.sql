-- =============================================================================
-- Migration V30: Hệ Thống Tin Nhắn Hỗ Trợ Trực Tuyến Thời Gian Thực (Live Chat Support)
-- Quản lý phiên chat 2 chiều độc lập với Phiếu hỗ trợ SLA (support_tickets)
-- =============================================================================

CREATE TABLE IF NOT EXISTS support_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_message_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    last_message_preview VARCHAR(500),
    unread_staff_count INT NOT NULL DEFAULT 0,
    unread_user_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sc_user_id ON support_conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_sc_last_message_at ON support_conversations(last_message_at DESC);

CREATE TABLE IF NOT EXISTS support_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES support_conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id),
    sender_role VARCHAR(30) NOT NULL,
    sender_name VARCHAR(100),
    message_text TEXT NOT NULL,
    attachment_url VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sm_conversation_id ON support_messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_sm_created_at ON support_messages(created_at ASC);

-- RLS deny-all dự phòng đồng bộ quy chuẩn V6 & V13
ALTER TABLE support_conversations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON support_conversations;
DROP POLICY IF EXISTS "deny_authenticated" ON support_conversations;
CREATE POLICY "deny_anon" ON support_conversations FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON support_conversations FOR ALL TO authenticated USING (false);

ALTER TABLE support_messages ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON support_messages;
DROP POLICY IF EXISTS "deny_authenticated" ON support_messages;
CREATE POLICY "deny_anon" ON support_messages FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON support_messages FOR ALL TO authenticated USING (false);

GRANT ALL ON support_conversations, support_messages TO postgres;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON support_conversations, support_messages TO service_role;
    END IF;
END $$;
