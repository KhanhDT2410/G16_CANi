-- =============================================================================
-- Migration V70: Liên kết Hệ thống Live Chat (V30) và Support Tickets (V13)
-- Triển khai mô hình Omnichannel Support & Ngăn ngừa Phân mảnh dữ liệu
-- =============================================================================

-- 1. Bổ sung liên kết từ Support Ticket sang Conversation gốc
ALTER TABLE support_tickets 
    ADD COLUMN IF NOT EXISTS conversation_id UUID REFERENCES support_conversations(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_st_tickets_conversation_id ON support_tickets(conversation_id);

COMMENT ON COLUMN support_tickets.conversation_id IS 'Mã phiên Live Chat gốc sinh ra ticket này (nếu được Escalate từ chat)';

-- 2. Bổ sung liên kết từ Support Conversation sang Ticket đang kích hoạt
ALTER TABLE support_conversations 
    ADD COLUMN IF NOT EXISTS active_ticket_id UUID REFERENCES support_tickets(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_sc_active_ticket_id ON support_conversations(active_ticket_id);

COMMENT ON COLUMN support_conversations.active_ticket_id IS 'Mã Ticket SLA đang hoạt động gắn với phiên chat này';
