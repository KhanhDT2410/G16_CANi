-- =============================================================================
-- Migration V39: Hệ Thống Tin Nhắn Trực Tiếp & Theo Ca Làm Việc (Direct & Shift Chat)
-- Kênh trao đổi 2 chiều giữa Event Organizer (EO) và Nhân sự (Worker)
-- =============================================================================

CREATE TABLE IF NOT EXISTS chat_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL DEFAULT 'DIRECT', -- 'DIRECT' hoặc 'SHIFT'
    shift_id UUID REFERENCES shifts(id) ON DELETE SET NULL,
    last_message_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    last_message_preview VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_conv_shift_id ON chat_conversations(shift_id);
CREATE INDEX IF NOT EXISTS idx_chat_conv_last_msg_at ON chat_conversations(last_message_at DESC);

CREATE TABLE IF NOT EXISTS chat_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    unread_count INT NOT NULL DEFAULT 0,
    last_read_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chat_participant UNIQUE(conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_part_user_id ON chat_participants(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_part_conv_id ON chat_participants(conversation_id);

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_text TEXT NOT NULL,
    attachment_url VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_msg_conv_created ON chat_messages(conversation_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_chat_msg_sender ON chat_messages(sender_id);

-- RLS deny-all đồng bộ quy chuẩn V6 & V13
ALTER TABLE chat_conversations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON chat_conversations;
DROP POLICY IF EXISTS "deny_authenticated" ON chat_conversations;
CREATE POLICY "deny_anon" ON chat_conversations FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON chat_conversations FOR ALL TO authenticated USING (false);

ALTER TABLE chat_participants ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON chat_participants;
DROP POLICY IF EXISTS "deny_authenticated" ON chat_participants;
CREATE POLICY "deny_anon" ON chat_participants FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON chat_participants FOR ALL TO authenticated USING (false);

ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON chat_messages;
DROP POLICY IF EXISTS "deny_authenticated" ON chat_messages;
CREATE POLICY "deny_anon" ON chat_messages FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON chat_messages FOR ALL TO authenticated USING (false);

GRANT ALL ON chat_conversations, chat_participants, chat_messages TO postgres;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON chat_conversations, chat_participants, chat_messages TO service_role;
    END IF;
END $$;
