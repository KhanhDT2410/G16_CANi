-- =============================================================================
-- Migration V69: Mở rộng kích thước cột bank_account_number để lưu trữ chuỗi mã hóa FLE AES-256-GCM
-- Tuân thủ: Nghị định 13/2023/NĐ-CP & Tiêu chuẩn an toàn dữ liệu cá nhân
-- =============================================================================

ALTER TABLE workers ALTER COLUMN bank_account_number TYPE VARCHAR(255);
COMMENT ON COLUMN workers.bank_account_number IS 'Số tài khoản ngân hàng thụ hưởng của Worker (Mã hóa FLE AES-256-GCM)';
