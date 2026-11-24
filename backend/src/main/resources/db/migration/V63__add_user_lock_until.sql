-- V63: Thêm cột lock_until vào bảng users phục vụ tạm khóa tài khoản có thời hạn và tự động mở khóa
ALTER TABLE users ADD COLUMN IF NOT EXISTS lock_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_users_status_lock_until ON users (status, lock_until);
