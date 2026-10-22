# CANi Architecture - Kế Hoạch Chuyển Đổi Sang Supabase Database Duy Nhất & Cơ Chế Single Gatekeeper

> **Tài liệu:** Kế hoạch Kỹ nghệ chuyển dịch CSDL và chuẩn hóa quyền truy cập  
> **Áp dụng cho:** Toàn bộ thành viên CANi (Backend, Web Frontend, Mobile)  
> **Trạng thái:** Đã phê duyệt (Approved)

---

## 1. Bối Cảnh & Mục Tiêu Kỹ Thuật

### 1.1. Vấn đề thực tế
Trước đây, dự án duy trì cấu hình song song:
- **Local Dev:** Chạy PostgreSQL độc lập trên máy từng thành viên (`localhost:5432/cani_dev`), thiếu schema `auth`, khiến các ràng buộc khóa ngoại (Foreign Key) và quy trình kiểm thử bị lệch pha.
- **Cloud:** Chạy Supabase Auth trên đám mây, dẫn đến hiện tượng phân tán dữ liệu và nguy cơ sinh ra "Tài khoản ma" (Ghost Account) do bài toán Dual-Write không nguyên tử giữa GoTrue và Local DB.

### 1.2. Quyết định Kiến trúc (Architectural Decision)
1. **Loại bỏ 100% CSDL PostgreSQL local.**
2. **Sử dụng duy nhất một CSDL PostgreSQL trên Supabase Cloud** cho toàn bộ môi trường phát triển và kiểm thử của dự án.
3. **Thực thi mô hình Single Gatekeeper (Người gác cổng duy nhất):**
   - Spring Boot Backend là đơn vị duy nhất được cấp quyền truy cập và thao tác trên schema `public`.
   - Frontend (Web/Mobile) bị **chặn tuyệt đối** không thể truy vấn trực tiếp vào CSDL qua Supabase PostgREST.
4. **Đồng bộ hóa tài khoản bằng Database Trigger nguyên tử (ACID)** trực tiếp giữa `auth.users` và `public.users`.

---

## 2. Mô Hình Kiến Trúc Mục Tiêu (Target Architecture)

```
┌────────────────────────────────────────────────────────┐
│               Client Layer (Web / Mobile)              │
└───────────────────────────┬────────────────────────────┘
                            │ (1) HTTP REST API (/api/...)
                            ▼
┌────────────────────────────────────────────────────────┐
│           Backend Layer: Spring Boot 3.5.1             │
│                 (Single Gatekeeper)                    │
│  - Business Logic & RBAC (Role-Based Access Control)   │
│  - Jakarta Validation, SecurityFilterChain             │
│  - Quản lý giao dịch nội bộ (@Transactional)           │
└───────────────┬──────────────────────────┬─────────────┘
                │                          │
 (2) Admin API  │ (HTTP REST)              │ (3) JDBC HikariCP
 (Xác thực IdP) │                          │ (Port 6543 Pooler)
                ▼                          ▼
┌────────────────────────────────────────────────────────┐
│           Supabase PostgreSQL Cloud Instance           │
│                                                        │
│  ┌────────────────────────┐  ┌──────────────────────┐  │
│  │      Schema: auth      │  │    Schema: public    │  │
│  │      (auth.users)      │  │ (users, events,...)  │  │
│  └────────────┬───────────┘  └──────────▲───────────┘  │
│               │                         │              │
│               └──── (4) DB Trigger ─────┘              │
│                     (ACID Transaction)                 │
└────────────────────────────────────────────────────────┘
```

---

## 3. Các Bước Triển Khai Kỹ Thuật

### Bước 1: Khóa Chặt PostgREST - Ngăn Tuyệt Đối Frontend Truy Cập CSDL

Để ngăn chặn việc Frontend sử dụng `VITE_SUPABASE_ANON_KEY` truy vấn trái phép thông qua Supabase JS/Dart SDK, thực thi script SQL sau trên **Supabase SQL Editor**:

```sql
-- 1. Thu hồi quyền sử dụng schema public của client bên ngoài
REVOKE USAGE ON SCHEMA public FROM anon, authenticated;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon, authenticated;
REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM anon, authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;

-- 2. Đảm bảo chỉ user của Spring Boot (postgres) và backend (service_role) mới có toàn quyền
GRANT ALL ON SCHEMA public TO postgres, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres, service_role;

-- 3. Thiết lập chính sách RLS Deny-All dự phòng cho toàn bộ bảng hiện tại
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
```

---

### Bước 2: Thiết Lập Database Trigger Tự Động Đồng Bộ Người Dùng (Triệt Tiêu Dual-Write)

Tạo Trigger trên schema `auth.users` để tự động khởi tạo bản ghi trong `public.users` ngay khi tài khoản được tạo (áp dụng cho cả đăng ký mật khẩu lẫn đăng nhập Google OAuth):

```sql
-- Tạo function xử lý đồng bộ
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS trigger AS $$
DECLARE
  assigned_role user_role := 'WORKER';
  user_full_name text;
  user_phone text;
BEGIN
  -- Trích xuất metadata do client/backend gửi kèm khi đăng ký
  user_full_name := COALESCE(new.raw_user_meta_data->>'full_name', new.email);
  user_phone := new.raw_user_meta_data->>'phone';
  
  IF (new.raw_user_meta_data->>'role') = 'EVENT_ORGANIZER' THEN
    assigned_role := 'EVENT_ORGANIZER';
  END IF;

  -- Ghi nguyên tử vào public.users
  INSERT INTO public.users (
    id,
    email,
    phone,
    full_name,
    role,
    status,
    email_verified,
    created_at,
    updated_at
  ) VALUES (
    new.id,
    new.email,
    user_phone,
    user_full_name,
    assigned_role,
    'ACTIVE',
    COALESCE(new.email_confirmed_at IS NOT NULL, false),
    NOW(),
    NOW()
  )
  ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    full_name = EXCLUDED.full_name,
    email_verified = EXCLUDED.email_verified,
    updated_at = NOW();

  -- Khởi tạo profile con tương ứng nếu chưa tồn tại
  IF assigned_role = 'EVENT_ORGANIZER' THEN
    INSERT INTO public.event_organizers (user_id, company_name)
    VALUES (new.id, COALESCE(new.raw_user_meta_data->>'company_name', user_full_name))
    ON CONFLICT (user_id) DO NOTHING;
  ELSE
    INSERT INTO public.workers (user_id, full_name)
    VALUES (new.id, user_full_name)
    ON CONFLICT (user_id) DO NOTHING;
  END IF;

  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Gắn Trigger vào auth.users
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE PROCEDURE public.handle_new_auth_user();
```

---

### Bước 3: Cấu Hình Backend Kết Nối Chuẩn Qua Connection Pooler

Vì Supabase Cloud giới hạn số lượng Direct Connections, Backend Spring Boot bắt buộc phải cấu hình thông qua **Supavisor Pooler (Port 6543 hoặc Session Pooler Port 5432)** và giới hạn kích thước pool của `HikariCP` cho từng máy dev.

Cập nhật `backend/src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    # Sử dụng Connection Pooler của Supabase (thay your-project-ref và password bằng biến môi trường)
    url: ${DB_URL:jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0}
    username: ${DB_USERNAME:postgres.your-project-ref}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      # Giới hạn pool kết nối cho từng máy dev để tránh lỗi cạn kiệt connection trên Supabase Free Tier
      maximum-pool-size: 3
      minimum-idle: 1
      idle-timeout: 30000
      max-lifetime: 60000
      connection-timeout: 20000

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: public

  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: public
    default-schema: public
```

---

## 4. Kỷ Luật Phối Hợp Nhóm Khi Dùng Chung Cloud Database

Dùng chung một cơ sở dữ liệu trực tuyến đòi hỏi **tính kỷ luật cao** giữa các thành viên:

1. **Tuyệt đối không tự ý xóa/sửa dữ liệu dùng chung:**
   - Dữ liệu master data (Danh mục kỹ năng, bảng cấu hình, tài khoản admin dùng chung) không được phép can thiệp bằng tay.
   - Khi tạo dữ liệu test cá nhân, đặt tiền tố rõ ràng (ví dụ: `Test_Event_Khanh`, `user_test_a@cani.vn`).
2. **Quy trình Quản lý Migration Flyway nghiêm ngặt:**
   - **Không bao giờ sửa file migration đã merge lên `main`:** Bất kỳ thay đổi schema nào đều phải tạo file phiên bản tiếp theo (`V4__...`, `V5__...`).
   - Khi chuyển nhánh git (checkout branch), nếu thấy nhánh khác có migration mới hơn, phải thông báo cho lead trước khi chạy migration trên Cloud DB.
3. **Quy tắc Frontend:**
   - Cấm hoàn toàn việc import SDK Supabase để query bảng nghiệp vụ (`supabase.from(...)`).
   - Mọi request dữ liệu đều phải gửi qua `apiClient` của Spring Boot.
