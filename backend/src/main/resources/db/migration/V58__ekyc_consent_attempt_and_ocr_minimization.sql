-- =============================================================================
-- Flyway Migration: V54__ekyc_consent_attempt_and_ocr_minimization.sql
-- Description:
--   1. Lưu trạng thái đủ tuổi lao động tối thiểu (meets_minimum_age) thay vì tiếp tục
--      ghi các trường OCR không cần thiết (vẫn giữ cột cũ để tương thích ngược).
--   2. Theo dõi processing_started_at & attempt_token cho phục hồi lượt PROCESSING
--      quá hạn và ngăn phản hồi VNPT về muộn ghi đè lượt mới.
--   3. Lưu vết đồng ý xử lý dữ liệu cá nhân & sinh trắc học (Nghị định 13/2023)
--      trước khi gửi ảnh sang VNPT.
-- =============================================================================

ALTER TABLE worker_ekyc_verifications
    ADD COLUMN IF NOT EXISTS meets_minimum_age BOOLEAN,
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS attempt_token VARCHAR(64),
    ADD COLUMN IF NOT EXISTS consent_notice_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS consented_at TIMESTAMPTZ;

ALTER TABLE eo_ekyc_verifications
    ADD COLUMN IF NOT EXISTS meets_minimum_age BOOLEAN,
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS attempt_token VARCHAR(64),
    ADD COLUMN IF NOT EXISTS consent_notice_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS consented_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS ekyc_consent_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    target_role VARCHAR(32) NOT NULL,
    notice_version VARCHAR(64) NOT NULL,
    consented_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ekyc_consent_user
    ON ekyc_consent_records(user_id, created_at DESC);

ALTER TABLE ekyc_consent_records ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON ekyc_consent_records FROM PUBLIC, anon, authenticated;
