-- =============================================================================
-- Flyway Migration: V10__fix_handle_new_auth_user_search_path.sql
-- Description: 
--   Khắc phục lỗi search_path của trigger handle_new_auth_user() khi được gọi
--   từ Supabase GoTrue (supabase_auth_admin context).
--   Thêm SET search_path = public, auth, pg_temp, chuẩn hóa kiểu public.user_role
--   và khối EXCEPTION an toàn để không bao giờ làm sập luồng đăng ký của hệ thống.
-- =============================================================================

CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS trigger 
LANGUAGE plpgsql 
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
  assigned_role public.user_role := 'WORKER';
  user_full_name text;
  user_phone text;
  user_avatar text;
BEGIN
  -- 1. Trích xuất thông tin cơ bản từ raw_user_meta_data
  user_full_name := COALESCE(
    new.raw_user_meta_data->>'full_name',
    new.raw_user_meta_data->>'name',
    split_part(new.email, '@', 1)
  );
  
  -- Chuẩn hóa số điện thoại: rỗng chuyển thành NULL để không vi phạm UNIQUE constraint
  user_phone := NULLIF(TRIM(new.raw_user_meta_data->>'phone'), '');
  
  user_avatar := COALESCE(
    new.raw_user_meta_data->>'avatar_url',
    new.raw_user_meta_data->>'picture'
  );

  -- 2. Xác định vai trò đăng ký
  IF (new.raw_user_meta_data->>'role') = 'EVENT_ORGANIZER' THEN
    assigned_role := 'EVENT_ORGANIZER'::public.user_role;
  ELSIF (new.raw_user_meta_data->>'role') = 'CUSTOMER_CARE' THEN
    assigned_role := 'CUSTOMER_CARE'::public.user_role;
  ELSIF (new.raw_user_meta_data->>'role') = 'ADMIN' THEN
    assigned_role := 'ADMIN'::public.user_role;
  ELSE
    assigned_role := 'WORKER'::public.user_role;
  END IF;

  -- 3. Ghi nhận người dùng vào public.users (Idempotent ON CONFLICT)
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
    'ACTIVE'::public.user_status,
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

  -- 4. Tự động khởi tạo profile con tương ứng nếu chưa tồn tại
  IF assigned_role = 'EVENT_ORGANIZER'::public.user_role THEN
    INSERT INTO public.event_organizers (user_id, company_name)
    VALUES (new.id, COALESCE(new.raw_user_meta_data->>'company_name', user_full_name))
    ON CONFLICT (user_id) DO NOTHING;
  ELSIF assigned_role = 'WORKER'::public.user_role THEN
    INSERT INTO public.workers (user_id, full_name)
    VALUES (new.id, user_full_name)
    ON CONFLICT (user_id) DO NOTHING;
  END IF;

  RETURN new;
EXCEPTION WHEN OTHERS THEN
  RAISE WARNING 'handle_new_auth_user sync failed for user %: %', new.id, SQLERRM;
  RETURN new;
END;
$$;
