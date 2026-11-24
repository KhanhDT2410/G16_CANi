-- =============================================================================
-- Migration V71: Mở rộng kích thước cột reference_no của bảng support_tickets
-- Ngăn ngừa lỗi value too long for type character varying(20) khi sinh mã phiếu
-- =============================================================================

ALTER TABLE support_tickets ALTER COLUMN reference_no TYPE VARCHAR(50);
COMMENT ON COLUMN support_tickets.reference_no IS 'Mã phiếu hỗ trợ SLA độc nhất (TKT-YYYYMMDD-XXXXXX)';
