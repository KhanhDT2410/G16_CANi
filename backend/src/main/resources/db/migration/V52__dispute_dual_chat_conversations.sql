-- ============================================================================
-- V52: Dispute Dual Chat Conversations & Retention Indexing
-- Hỗ trợ cơ chế chat đôi phân xử tranh chấp (Admin <-> Worker, Admin <-> EO)
-- Tận dụng bảng support_conversations và support_messages
-- ============================================================================

-- 1. Bổ sung liên kết tranh chấp vào support_conversations
ALTER TABLE support_conversations
    ADD COLUMN IF NOT EXISTS dispute_id UUID REFERENCES disputes(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS dispute_party VARCHAR(20);

COMMENT ON COLUMN support_conversations.dispute_id IS 'Mã tranh chấp gắn với phiên trao đổi';
COMMENT ON COLUMN support_conversations.dispute_party IS 'Bên tham gia phiên trao đổi (WORKER hoặc EO)';

-- 2. Chỉ mục phục vụ truy vấn hội thoại tranh chấp
CREATE INDEX IF NOT EXISTS idx_sc_dispute_id ON support_conversations(dispute_id);
CREATE INDEX IF NOT EXISTS idx_sc_dispute_party ON support_conversations(dispute_id, dispute_party);

-- 3. Chỉ mục tối ưu hoá tác vụ định kỳ dọn dẹp dữ liệu (Retention Scheduler)
CREATE INDEX IF NOT EXISTS idx_chat_messages_created_at ON chat_messages(created_at);
CREATE INDEX IF NOT EXISTS idx_support_messages_created_at ON support_messages(created_at);
CREATE INDEX IF NOT EXISTS idx_disputes_resolved_at ON disputes(resolved_at);
