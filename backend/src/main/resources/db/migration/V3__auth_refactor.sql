-- =============================================================================
-- Flyway Migration: V3__auth_refactor.sql
-- Description: Khóa ngoại liên kết users -> auth.users, bảo vệ RLS và cấu hình Grants
-- =============================================================================

-- 1) FK users -> auth.users (guard: chỉ tạo khi schema auth tồn tại trên Supabase)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
    ALTER TABLE public.users DROP CONSTRAINT IF EXISTS users_auth_fk;
    ALTER TABLE public.users ADD CONSTRAINT users_auth_fk
      FOREIGN KEY (id) REFERENCES auth.users(id) ON DELETE CASCADE;
  END IF;
END $$;

-- 2) Xóa các cột GoTrue nếu còn tồn tại từ schema cũ (idempotent)
ALTER TABLE public.users
  DROP COLUMN IF EXISTS password_hash,
  DROP COLUMN IF EXISTS email_otp,
  DROP COLUMN IF EXISTS email_otp_expires_at,
  DROP COLUMN IF EXISTS password_reset_otp,
  DROP COLUMN IF EXISTS password_reset_otp_expires_at,
  DROP COLUMN IF EXISTS failed_login_attempts,
  DROP COLUMN IF EXISTS locked_until;

-- 3) Đổi giá trị enum CSKH sang CUSTOMER_CARE nếu database đang mang giá trị cũ
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_enum e 
    JOIN pg_type t ON e.enumtypid = t.oid 
    WHERE t.typname = 'user_role' AND e.enumlabel = 'CSKH'
  ) THEN
    ALTER TYPE user_role RENAME VALUE 'CSKH' TO 'CUSTOMER_CARE';
  END IF;
END $$;

-- 3) Bật RLS và thiết lập policy deny-all cho anon/authenticated trên mọi bảng public
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename != 'flyway_schema_history') LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY;', r.tablename);
    EXECUTE format('DROP POLICY IF EXISTS "deny_anon" ON public.%I;', r.tablename);
    EXECUTE format('DROP POLICY IF EXISTS "deny_authenticated" ON public.%I;', r.tablename);
    EXECUTE format('CREATE POLICY "deny_anon" ON public.%I FOR ALL TO anon USING (false);', r.tablename);
    EXECUTE format('CREATE POLICY "deny_authenticated" ON public.%I FOR ALL TO authenticated USING (false);', r.tablename);
  END LOOP;
END $$;

-- 4) Cấp quyền schema: RLS bảo vệ dữ liệu ở mức bảng, nhưng schema cần USAGE để Supabase GoTrue đọc metadata
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON SCHEMA public TO postgres, service_role;
