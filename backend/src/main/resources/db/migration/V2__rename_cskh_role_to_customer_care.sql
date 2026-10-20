-- =============================================================================
-- Flyway Migration: V2__rename_cskh_role_to_customer_care.sql
-- Đổi role CSKH -> CUSTOMER_CARE trong bảng users
-- =============================================================================

-- 1. Cập nhật dữ liệu hiện có (nếu có user đang dùng role CSKH)
UPDATE users SET role = 'CUSTOMER_CARE' WHERE role = 'CSKH';

-- 2. Drop và tạo lại CHECK constraint với danh sách role mới
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('ADMIN', 'CUSTOMER_CARE', 'FINANCE', 'EVENT_ORGANIZER', 'WORKER'));
