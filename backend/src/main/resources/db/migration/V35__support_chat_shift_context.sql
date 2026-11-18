-- =============================================================================
-- Migration V35: Bổ sung liên kết Ca Làm Việc (shift_id) vào Hệ Thống Tin Nhắn Trực Tuyến
-- Phân biệt giữa Tin nhắn Hỗ trợ Tự do (shift_id = NULL) và Tin nhắn trong Ca làm việc
-- =============================================================================

ALTER TABLE support_messages
    ADD COLUMN IF NOT EXISTS shift_id UUID REFERENCES shifts(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_sm_shift_id ON support_messages(shift_id);
