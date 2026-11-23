-- =============================================================================
-- Flyway Migration: V48__ekyc_vnpt_production_hardening.sql
-- Description:
--   Chuẩn hóa bảng worker_ekyc_verifications và eo_ekyc_verifications cho luồng
--   VNPT eKYC production-ready:
--   - Lưu metadata nhà cung cấp (provider, provider_reference), mã lỗi kỹ thuật
--     (failure_code), số lần thử lại (retry_count), khóa lạc quan (version).
--   - Lưu thông tin chính sách lưu giữ và xóa ảnh riêng tư
--     (image_retention_until, images_purged_at).
--   - Mở rộng độ chính xác cột face_match_score và ocr_confidence lên NUMERIC(8,4)
--     để không làm mất độ chính xác dù thang điểm VNPT là 0-1 hay 0-100 và cho phép NULL
--     khi không có dữ liệu thực tế từ VNPT.
--   - Bổ sung index cho cccd_number_hash bên EO và index dọn dẹp ảnh hết hạn.
--   100% tương thích ngược, không xóa dữ liệu hiện có.
-- =============================================================================

ALTER TABLE worker_ekyc_verifications
    ADD COLUMN IF NOT EXISTS provider VARCHAR(50) DEFAULT 'VNPT_EKYC',
    ADD COLUMN IF NOT EXISTS provider_reference VARCHAR(120),
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS image_retention_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS images_purged_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE worker_ekyc_verifications
    ALTER COLUMN face_match_score TYPE NUMERIC(8, 4),
    ALTER COLUMN face_match_score DROP DEFAULT,
    ALTER COLUMN ocr_confidence TYPE NUMERIC(8, 4),
    ALTER COLUMN ocr_confidence DROP DEFAULT;

ALTER TABLE eo_ekyc_verifications
    ADD COLUMN IF NOT EXISTS provider VARCHAR(50) DEFAULT 'VNPT_EKYC',
    ADD COLUMN IF NOT EXISTS provider_reference VARCHAR(120),
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS image_retention_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS images_purged_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE eo_ekyc_verifications
    ALTER COLUMN face_match_score TYPE NUMERIC(8, 4),
    ALTER COLUMN face_match_score DROP DEFAULT,
    ALTER COLUMN ocr_confidence TYPE NUMERIC(8, 4),
    ALTER COLUMN ocr_confidence DROP DEFAULT;

CREATE INDEX IF NOT EXISTS idx_eo_ekyc_cccd_hash
    ON eo_ekyc_verifications(cccd_number_hash);

CREATE INDEX IF NOT EXISTS idx_worker_ekyc_retention
    ON worker_ekyc_verifications(image_retention_until)
    WHERE images_purged_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_eo_ekyc_retention
    ON eo_ekyc_verifications(image_retention_until)
    WHERE images_purged_at IS NULL;
