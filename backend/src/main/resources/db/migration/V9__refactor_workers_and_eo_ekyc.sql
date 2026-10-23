-- =============================================================================
-- Flyway Migration: V9__refactor_workers_and_eo_ekyc.sql
-- Description:
--   1. Chuẩn hóa CSDL cho bảng workers (3NF):
--      * Tách bảng worker_preferences (Lưu vị trí, tọa độ GPS & tiêu chí tìm việc).
--      * Loại bỏ các cột trùng lặp eKYC (đã có ở V8 worker_ekyc_verifications).
--      * Loại bỏ cột legacy skills UUID[] (đã có ở V5 worker_skills).
--   2. Tạo bảng eo_ekyc_verifications (Phục vụ eKYC Nhà tổ chức sự kiện EO).
--   3. Tạo bảng email_otp_verifications (Phục vụ xác thực đăng ký Email OTP).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- BƯỚC 1: TẠO BẢNG worker_preferences & DI CHUYỂN DỮ LIỆU
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS worker_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id UUID NOT NULL UNIQUE REFERENCES workers(id) ON DELETE CASCADE,
    base_address TEXT,
    base_lat DOUBLE PRECISION,
    base_lng DOUBLE PRECISION,
    min_wage NUMERIC(12, 2) DEFAULT 0,
    max_distance_km DOUBLE PRECISION DEFAULT 20.0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_worker_pref_worker ON worker_preferences(worker_id);

-- Di chuyển dữ liệu hiện có từ workers sang worker_preferences (chỉ thực hiện nếu cột còn tồn tại trong workers)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'workers' 
          AND column_name = 'base_address'
    ) THEN
        EXECUTE '
            INSERT INTO worker_preferences (worker_id, base_address, base_lat, base_lng, min_wage, max_distance_km)
            SELECT 
                id, 
                base_address, 
                base_lat, 
                base_lng, 
                COALESCE(min_wage, 0), 
                COALESCE(max_distance_km, 20.0)
            FROM workers
            ON CONFLICT (worker_id) DO NOTHING
        ';
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- BƯỚC 2: CẮT TỈA CÁC CỘT THỪA / TRÙNG LẶP TRONG BẢNG workers
-- -----------------------------------------------------------------------------
-- Xóa các cột ảnh CCCD và audit eKYC (đã chuyển sang worker_ekyc_verifications)
ALTER TABLE workers DROP COLUMN IF EXISTS cccd_front_url;
ALTER TABLE workers DROP COLUMN IF EXISTS cccd_back_url;
ALTER TABLE workers DROP COLUMN IF EXISTS selfie_url;
ALTER TABLE workers DROP COLUMN IF EXISTS kyc_submitted_at;
ALTER TABLE workers DROP COLUMN IF EXISTS kyc_reviewed_by;

-- Xóa cột legacy skills UUID[] (đã có bảng worker_skills)
ALTER TABLE workers DROP COLUMN IF EXISTS skills;

-- Xóa các cột preferences đã chuyển sang bảng worker_preferences
ALTER TABLE workers DROP COLUMN IF EXISTS base_address;
ALTER TABLE workers DROP COLUMN IF EXISTS base_lat;
ALTER TABLE workers DROP COLUMN IF EXISTS base_lng;
ALTER TABLE workers DROP COLUMN IF EXISTS min_wage;
ALTER TABLE workers DROP COLUMN IF EXISTS max_distance_km;

-- -----------------------------------------------------------------------------
-- BƯỚC 3: TẠO BẢNG eo_ekyc_verifications (eKYC CHO NHÀ TỔ CHỨC SỰ KIỆN EO)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS eo_ekyc_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    eo_id UUID NOT NULL REFERENCES event_organizers(id) ON DELETE CASCADE,
    representative_name VARCHAR(255),
    cccd_number_masked VARCHAR(50),
    cccd_number_hash VARCHAR(64),
    dob VARCHAR(50),
    gender VARCHAR(20),
    nationality VARCHAR(50) DEFAULT 'Việt Nam',
    origin_address TEXT,
    residence_address TEXT,
    expiry_date VARCHAR(50),
    issue_date VARCHAR(50),
    front_image_url TEXT,
    back_image_url TEXT,
    selfie_image_url TEXT,
    business_license_url TEXT,
    face_match_score NUMERIC(5, 2) DEFAULT 0.00,
    ocr_confidence NUMERIC(5, 2) DEFAULT 0.00,
    fraud_flags JSONB,
    routing_decision VARCHAR(50) NOT NULL DEFAULT 'MANUAL_REVIEW',
    routing_reason TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    reviewed_by UUID REFERENCES users(id),
    review_notes TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_eo_ekyc_eo_id ON eo_ekyc_verifications(eo_id);
CREATE INDEX IF NOT EXISTS idx_eo_ekyc_status ON eo_ekyc_verifications(status);

-- -----------------------------------------------------------------------------
-- BƯỚC 4: TẠO BẢNG email_otp_verifications (XÁC THỰC OTP KHI ĐĂNG KÝ EMAIL)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS email_otp_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    otp_code VARCHAR(10) NOT NULL,
    otp_type VARCHAR(50) NOT NULL DEFAULT 'REGISTER', -- REGISTER, RESET_PASSWORD
    expires_at TIMESTAMPTZ NOT NULL,
    is_used BOOLEAN DEFAULT FALSE,
    attempts INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_email_otp_lookup ON email_otp_verifications(email, otp_type, is_used);
