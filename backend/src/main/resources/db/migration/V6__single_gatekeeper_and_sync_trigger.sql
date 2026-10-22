-- =============================================================================
-- Flyway Migration: V6__single_gatekeeper_and_sync_trigger.sql
-- Description: Triển khai cơ chế Single Gatekeeper & Database Trigger đồng bộ Auth
-- 1. Thu hồi quyền sử dụng schema public đối với anon/authenticated (Chặn PostgREST).
-- 2. Đảm bảo toàn bộ bảng trong public được bảo vệ bằng RLS deny-all.
-- 3. Tạo trigger đồng bộ nguyên tử giữa auth.users và public.users (Triệt tiêu Dual-Write).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. SINGLE GATEKEEPER: Thu hồi quyền truy cập schema public của PostgREST
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  -- Thu hồi quyền của role anon (unauthenticated client)
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    REVOKE USAGE ON SCHEMA public FROM anon;
    REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon;
    REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM anon;
    REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon;
  END IF;

  -- Thu hồi quyền của role authenticated (client có JWT token)
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    REVOKE USAGE ON SCHEMA public FROM authenticated;
    REVOKE ALL ON ALL TABLES IN SCHEMA public FROM authenticated;
    REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM authenticated;
    REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM authenticated;
  END IF;

  -- Cấp toàn quyền cho role backend Spring Boot (postgres & service_role)
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
    GRANT USAGE, CREATE ON SCHEMA public TO service_role;
    GRANT ALL ON ALL TABLES IN SCHEMA public TO service_role;
    GRANT ALL ON ALL ROUTINES IN SCHEMA public TO service_role;
    GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO service_role;
  END IF;

  GRANT USAGE, CREATE ON SCHEMA public TO postgres;
  GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres;
  GRANT ALL ON ALL ROUTINES IN SCHEMA public TO postgres;
  GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres;
END $$;

-- -----------------------------------------------------------------------------
-- 2. RLS DENY-ALL DỰ PHÒNG: Bật RLS và chặn toàn bộ bảng đối với PostgREST
-- -----------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------
-- 3. DATABASE TRIGGER: Đồng bộ hóa nguyên tử từ auth.users sang public.users
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS trigger AS $$
DECLARE
  assigned_role user_role := 'WORKER';
  user_full_name text;
  user_phone text;
  user_avatar text;
BEGIN
  -- Trích xuất thông tin cơ bản từ raw_user_meta_data (hỗ trợ cả Password đăng ký và Google OAuth)
  user_full_name := COALESCE(
    new.raw_user_meta_data->>'full_name',
    new.raw_user_meta_data->>'name',
    split_part(new.email, '@', 1)
  );
  user_phone := new.raw_user_meta_data->>'phone';
  user_avatar := COALESCE(
    new.raw_user_meta_data->>'avatar_url',
    new.raw_user_meta_data->>'picture'
  );

  -- Xác định vai trò đăng ký
  IF (new.raw_user_meta_data->>'role') = 'EVENT_ORGANIZER' THEN
    assigned_role := 'EVENT_ORGANIZER';
  ELSIF (new.raw_user_meta_data->>'role') = 'CUSTOMER_CARE' THEN
    assigned_role := 'CUSTOMER_CARE';
  ELSIF (new.raw_user_meta_data->>'role') = 'ADMIN' THEN
    assigned_role := 'ADMIN';
  ELSE
    assigned_role := 'WORKER';
  END IF;

  -- Ghi nhận người dùng vào public.users (Idempotent ON CONFLICT)
  INSERT INTO public.users (
    id,
    email,
    phone,
    role,
    status,
    full_name,
    avatar_url,
    email_verified,
    created_at,
    updated_at
  ) VALUES (
    new.id,
    new.email,
    user_phone,
    assigned_role,
    'ACTIVE',
    user_full_name,
    user_avatar,
    COALESCE(new.email_confirmed_at IS NOT NULL, false),
    NOW(),
    NOW()
  )
  ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    full_name = EXCLUDED.full_name,
    phone = COALESCE(EXCLUDED.phone, public.users.phone),
    avatar_url = COALESCE(EXCLUDED.avatar_url, public.users.avatar_url),
    email_verified = EXCLUDED.email_verified,
    updated_at = NOW();

  -- Tự động khởi tạo profile con tương ứng nếu chưa tồn tại
  IF assigned_role = 'EVENT_ORGANIZER' THEN
    INSERT INTO public.event_organizers (user_id, company_name)
    VALUES (new.id, COALESCE(new.raw_user_meta_data->>'company_name', user_full_name))
    ON CONFLICT (user_id) DO NOTHING;
  ELSIF assigned_role = 'WORKER' THEN
    INSERT INTO public.workers (user_id, full_name)
    VALUES (new.id, user_full_name)
    ON CONFLICT (user_id) DO NOTHING;
  END IF;

  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Gắn Trigger vào schema auth.users (nếu schema auth tồn tại)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
    DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
    CREATE TRIGGER on_auth_user_created
      AFTER INSERT ON auth.users
      FOR EACH ROW EXECUTE PROCEDURE public.handle_new_auth_user();
  END IF;
END $$;
