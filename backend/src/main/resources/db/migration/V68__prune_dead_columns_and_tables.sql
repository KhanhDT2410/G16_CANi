-- =============================================================================
-- Migration: V68__prune_dead_columns_and_tables.sql
-- Description: Prune dead/unused tables and columns:
--              1. Drop orphan tables: payment_logs, wage_recommendations
--              2. Drop dead columns in shifts: min_experience_level, risk_score
--              3. Drop constant/unused column in users: language
--              4. Drop mock column in events: readiness_score
--              5. Drop legacy V1 columns in shift_assignments: is_replacement, replaced_by
--              6. Drop empty column in audit_logs: user_agent
--              7. Ensure tax_id & verified_by dropped in event_organizers
-- =============================================================================

-- 1. Xóa các bảng chết/không sử dụng
DROP TABLE IF EXISTS payment_logs CASCADE;
DROP TABLE IF EXISTS wage_recommendations CASCADE;

-- 2. Cắt tỉa các cột rác trong bảng shifts
ALTER TABLE shifts DROP COLUMN IF EXISTS min_experience_level;
ALTER TABLE shifts DROP COLUMN IF EXISTS risk_score;

-- 3. Cắt tỉa cột fix cứng trong bảng users
ALTER TABLE users DROP COLUMN IF EXISTS language;

-- 4. Cắt tỉa cột giả lập trong bảng events
ALTER TABLE events DROP COLUMN IF EXISTS readiness_score;

-- 5. Cắt tỉa các cột thừa kế V1 trong bảng shift_assignments
ALTER TABLE shift_assignments DROP COLUMN IF EXISTS is_replacement;
ALTER TABLE shift_assignments DROP COLUMN IF EXISTS replaced_by;

-- 6. Cắt tỉa cột rỗng trong bảng audit_logs
ALTER TABLE audit_logs DROP COLUMN IF EXISTS user_agent;

-- 7. Đảm bảo loại bỏ dứt điểm tax_id & verified_by trong event_organizers
ALTER TABLE event_organizers DROP COLUMN IF EXISTS tax_id;
ALTER TABLE event_organizers DROP COLUMN IF EXISTS verified_by;
