-- =============================================================================
-- Flyway Migration: V38__seed_demo_worker_skills_and_gps.sql
-- Description: Seed dữ liệu demo (profile local/Supabase) để Matching Engine chạy được:
--   * worker_skills: 3 skill phổ biến (từ taxonomy V2) cho demo worker
--     (users.email = 'worker@cani.vn') — tier SELF_DECLARED.
--   * worker_preferences: GPS (base_lat/base_lng) quanh khu vực demo (Q.5 TP.HCM)
--     để hard-filter khoảng cách không loại worker (distance = MAX_VALUE khi null).
--   * Idempotent: chạy no-op an toàn nếu DB không có demo worker.
-- =============================================================================

-- 1. Skill demo (SELF_DECLARED — đủ để matching hard-filter requiredSkills)
INSERT INTO worker_skills (worker_id, skill_id, proficiency, verification_tier)
SELECT DISTINCT ON (s.name)
       w.id,
       sk.id,
       'INTERMEDIATE',
       'SELF_DECLARED'
FROM workers w
JOIN users u ON u.id = w.user_id
JOIN skills sk ON sk.name IN ('Lễ tân / Check-in', 'Phục vụ bàn', 'Hậu cần / Setup sự kiện')
WHERE u.email = 'worker@cani.vn'
  AND sk.is_active = TRUE
ON CONFLICT (worker_id, skill_id) DO NOTHING;

-- 2. GPS demo: tạo mới hoặc cập nhật worker_preferences của demo worker
INSERT INTO worker_preferences (worker_id, base_address, base_lat, base_lng)
SELECT w.id, 'Nguyễn Thiện Thuật, Q. Phú Nhuận, TP.HCM', 10.7769, 106.7009
FROM workers w
JOIN users u ON u.id = w.user_id
WHERE u.email = 'worker@cani.vn'
ON CONFLICT (worker_id) DO UPDATE
SET base_lat = EXCLUDED.base_lat,
    base_lng = EXCLUDED.base_lng
WHERE worker_preferences.base_lat IS NULL OR worker_preferences.base_lng IS NULL;
