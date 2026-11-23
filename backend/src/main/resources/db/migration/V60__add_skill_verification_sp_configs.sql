-- ============================================================================
-- Migration: V60__add_skill_verification_sp_configs.sql
-- Description: Cấu hình tham số điểm kỹ năng (SP) cho xác thực chứng chỉ AI
--              và bảo chứng thực chiến qua ca làm việc (ADM-09, BR-16, BR-65)
-- ============================================================================

INSERT INTO system_config (key, value, description)
VALUES
    ('skill_verify_sp_ai_document', '25', 'Số điểm kỹ năng (SP) cộng thêm cho Worker khi vượt qua xác thực chứng chỉ/bằng cấp qua AI'),
    ('skill_verify_sp_platform_proven', '50', 'Số điểm kỹ năng (SP) cộng thêm cho Worker khi đạt chuẩn bảo chứng thực chiến qua ca làm việc')
ON CONFLICT (key) DO NOTHING;
