-- Migration V27: Thêm thông tin tài khoản ngân hàng thụ hưởng (VietQR) cho Worker
-- Phục vụ luồng chi trả thù lao trực tiếp Non-Escrow (Mục 11 & 12 SRS)

ALTER TABLE workers ADD COLUMN IF NOT EXISTS bank_code VARCHAR(30);
ALTER TABLE workers ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(50);
ALTER TABLE workers ADD COLUMN IF NOT EXISTS bank_account_name VARCHAR(100);

COMMENT ON COLUMN workers.bank_code IS 'Mã định danh ngân hàng Napas (ví dụ: MB, VCB, TCB, VPB, ACB...)';
COMMENT ON COLUMN workers.bank_account_number IS 'Số tài khoản ngân hàng thụ hưởng của Worker';
COMMENT ON COLUMN workers.bank_account_name IS 'Họ và tên chủ tài khoản ngân hàng thụ hưởng (In hoa không dấu)';
