-- V21: Thêm cột image_url cho bảng shifts và mở rộng các gói cước subscription_plan

ALTER TABLE shifts ADD COLUMN IF NOT EXISTS image_url TEXT;

DO $$
BEGIN
    ALTER TYPE subscription_plan ADD VALUE IF NOT EXISTS 'FREE_TRIAL';
    ALTER TYPE subscription_plan ADD VALUE IF NOT EXISTS 'ENTERPRISE';
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;
