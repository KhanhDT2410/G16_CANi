-- =============================================================================
-- Migration: V39__drop_tax_id_and_legacy_eo_columns.sql
-- Description: Chuyển đổi mô hình Event Organizer sang 100% Cá nhân (Individual-Only).
--              - Xóa cột tax_id và cột chết verified_by trong bảng event_organizers.
--              - Xóa cột business_license_url trong bảng eo_ekyc_verifications.
-- =============================================================================

-- 1. Cắt tỉa các cột trong bảng event_organizers
ALTER TABLE event_organizers DROP COLUMN IF EXISTS tax_id;
ALTER TABLE event_organizers DROP COLUMN IF EXISTS verified_by;

-- 2. Cắt tỉa cột giấy phép kinh doanh trong bảng eo_ekyc_verifications
ALTER TABLE eo_ekyc_verifications DROP COLUMN IF EXISTS business_license_url;
