-- =============================================================================
-- Flyway Migration: V8__worker_ekyc_verification.sql
-- Description: Bảng lưu trữ hồ sơ và kết quả thẩm định eKYC CCCD bằng AI (UC-18 & UC-89)
-- =============================================================================

CREATE TABLE IF NOT EXISTS worker_ekyc_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    cccd_number_masked VARCHAR(50),
    cccd_number_hash VARCHAR(64),
    full_name VARCHAR(255),
    dob VARCHAR(50),
    gender VARCHAR(20),
    nationality VARCHAR(50) DEFAULT 'Việt Nam',
    origin_address TEXT,
    residence_address TEXT,
    expiry_date VARCHAR(50),
    issue_date VARCHAR(50),
    identification_features TEXT,
    front_image_url TEXT,
    back_image_url TEXT,
    selfie_image_url TEXT,
    face_match_score NUMERIC(5, 2) DEFAULT 0.00,
    ocr_confidence NUMERIC(5, 2) DEFAULT 0.00,
    fraud_flags JSONB,
    routing_decision VARCHAR(50) NOT NULL,
    routing_reason TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    reviewed_by UUID REFERENCES users(id),
    review_notes TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ekyc_worker_id ON worker_ekyc_verifications(worker_id);
CREATE INDEX IF NOT EXISTS idx_ekyc_status ON worker_ekyc_verifications(status);
CREATE INDEX IF NOT EXISTS idx_ekyc_cccd_hash ON worker_ekyc_verifications(cccd_number_hash);
