# Hướng Dẫn Kiểm Thử API Xác Thực (Auth) Với Postman & Supabase

Tài liệu này hướng dẫn chi tiết quy trình nạp tài khoản mẫu vào cơ sở dữ liệu và các bước kiểm thử API phân hệ Xác thực (Authentication) của CANi Backend thông qua Postman.

---

## 1. Nạp Dữ Liệu User Mẫu Vào Cơ Sở Dữ Liệu

Do bảng `public.users(id)` liên kết khóa ngoại với bảng `auth.users(id)` của Supabase GoTrue, bạn cần chạy đoạn script SQL sau trong mục **SQL Editor** trên Supabase Dashboard để nạp đồng bộ 4 tài khoản mẫu:

```sql
-- Kích hoạt extension pgcrypto để băm mật khẩu chuẩn GoTrue
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
  v_admin_id UUID := '00000000-0000-0000-0000-000000000001';
  v_cskh_id  UUID := '00000000-0000-0000-0000-000000000002';
  v_eo_id    UUID := '00000000-0000-0000-0000-000000000003';
  v_worker_id UUID := '00000000-0000-0000-0000-000000000004';
  v_hashed_pw TEXT;
BEGIN
  -- Mật khẩu chung cho cả 4 tài khoản: Password123!
  v_hashed_pw := crypt('Password123!', gen_salt('bf'));

  -- 1. Nạp vào auth.users (Supabase GoTrue)
  INSERT INTO auth.users (
    id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, 
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) VALUES 
  (v_admin_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 
   'admin@cani.vn', v_hashed_pw, NOW(), '{"provider":"email","providers":["email"]}', '{"full_name":"Quản Trị Viên"}', NOW(), NOW()),
  (v_cskh_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 
   'cskh@cani.vn', v_hashed_pw, NOW(), '{"provider":"email","providers":["email"]}', '{"full_name":"Chuyên Viên CSKH"}', NOW(), NOW()),
  (v_eo_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 
   'eo@cani.vn', v_hashed_pw, NOW(), '{"provider":"email","providers":["email"]}', '{"full_name":"Ban Tổ Chức Á Châu"}', NOW(), NOW()),
  (v_worker_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 
   'worker@cani.vn', v_hashed_pw, NOW(), '{"provider":"email","providers":["email"]}', '{"full_name":"Nguyễn Văn Worker"}', NOW(), NOW())
  ON CONFLICT (id) DO NOTHING;

  -- 2. Nạp vào public.users (CANi Backend)
  INSERT INTO public.users (id, email, full_name, phone, role, status, email_verified) VALUES
  (v_admin_id, 'admin@cani.vn', 'Quản Trị Viên', '0901000001', 'ADMIN', 'ACTIVE', true),
  (v_cskh_id, 'cskh@cani.vn', 'Chuyên Viên CSKH', '0901000002', 'CUSTOMER_CARE', 'ACTIVE', true),
  (v_eo_id, 'eo@cani.vn', 'Ban Tổ Chức Á Châu', '0901000003', 'EVENT_ORGANIZER', 'ACTIVE', true),
  (v_worker_id, 'worker@cani.vn', 'Nguyễn Văn Worker', '0901000004', 'WORKER', 'ACTIVE', true)
  ON CONFLICT (id) DO NOTHING;

  -- 3. Nạp hồ sơ phụ thuộc tương ứng
  INSERT INTO public.event_organizers (id, user_id, company_name, verification_status)
  VALUES (gen_random_uuid(), v_eo_id, 'Công ty Sự Kiện Á Châu', 'APPROVED')
  ON CONFLICT (user_id) DO NOTHING;

  INSERT INTO public.workers (id, user_id, full_name, reliability_score, skill_tier, kyc_status)
  VALUES (gen_random_uuid(), v_worker_id, 'Nguyễn Văn Worker', 100, 'STARTER', 'APPROVED')
  ON CONFLICT (user_id) DO NOTHING;
END $$;
```

### Danh Sách Tài Khoản Mẫu:
| Email | Mật khẩu | Vai trò (`role`) | Nền tảng sử dụng |
|:---|:---|:---|:---|
| `admin@cani.vn` | `Password123!` | `ADMIN` | Web Admin Portal (`/admin`) |
| `cskh@cani.vn` | `Password123!` | `CUSTOMER_CARE` | Web Support Desk (`/customer-care`) |
| `eo@cani.vn` | `Password123!` | `EVENT_ORGANIZER` | Web Organizer Portal (`/eo`) |
| `worker@cani.vn` | `Password123!` | `WORKER` | Mobile Flutter App |

---

## 2. Các Bước Kiểm Thử Bằng Postman

Tạo một Collection mới trong Postman mang tên `CANi Auth API`.

### Request 1: Đăng nhập (Sign In)
* **Method:** `POST`
* **Endpoint:** `http://localhost:8080/api/auth/login`
* **Headers:** `Content-Type: application/json`
* **Body (raw JSON):**
  ```json
  {
    "email": "admin@cani.vn",
    "password": "Password123!"
  }
  ```
* **Kỳ vọng Response (HTTP 200 OK):**
  ```json
  {
    "success": true,
    "message": "Đăng nhập thành công",
    "data": {
      "token": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9...",
      "refreshToken": "...",
      "expiresIn": 3600,
      "user": {
        "id": "00000000-0000-0000-0000-000000000001",
        "email": "admin@cani.vn",
        "fullName": "Quản Trị Viên",
        "role": "ADMIN",
        "status": "ACTIVE"
      }
    }
  }
  ```
* 💡 **Script tự động lưu Token (Post-response / Tests tab):**
  ```javascript
  if (pm.response.code === 200) {
      const json = pm.response.json();
      pm.collectionVariables.set("access_token", json.data.token);
      pm.collectionVariables.set("refresh_token", json.data.refreshToken);
  }
  ```

---

### Request 2: Lấy Thông Tin Người Dùng Hiện Tại (Get Current User Profile)
* **Method:** `GET`
* **Endpoint:** `http://localhost:8080/api/auth/me`
* **Headers:** 
  * `Authorization`: `Bearer {{access_token}}`
* **Kỳ vọng Response (HTTP 200 OK):**
  ```json
  {
    "success": true,
    "message": "Lấy thông tin người dùng thành công",
    "data": {
      "id": "00000000-0000-0000-0000-000000000001",
      "email": "admin@cani.vn",
      "fullName": "Quản Trị Viên",
      "role": "ADMIN",
      "status": "ACTIVE",
      "emailVerified": true
    }
  }
  ```
* **Negative Test (Bảo mật):** Thử bỏ header `Authorization` hoặc truyền token không hợp lệ ➜ Nhận `401 Unauthorized` dạng JSON:
  ```json
  {
    "success": false,
    "message": "Xác thực không thành công hoặc token không hợp lệ",
    "data": null
  }
  ```

---

### Request 3: Đăng Ký Người Dùng Mới (Register New Account)
* **Method:** `POST`
* **Endpoint:** `http://localhost:8080/api/auth/register`
* **Headers:** `Content-Type: application/json`
* **Body (raw JSON):**
  ```json
  {
    "email": "new_organizer@cani.vn",
    "password": "Password123!",
    "fullName": "Trần Thị Ánh",
    "phone": "0987112233",
    "companyName": "Công ty Sự Kiện Ánh Dương",
    "accountType": "EVENT_ORGANIZER"
  }
  ```
* **Kỳ vọng Response (HTTP 201 Created):**
  Trả về thông tin `UserDto` và tự động đồng bộ sang GoTrue cùng hồ sơ `event_organizers`.

---

### Request 4: Làm Mới Phiên Đăng Nhập (Refresh Token)
* **Method:** `POST`
* **Endpoint:** `http://localhost:8080/api/auth/refresh`
* **Headers:** `Content-Type: application/json`
* **Body (raw JSON):**
  ```json
  {
    "refreshToken": "{{refresh_token}}"
  }
  ```
* **Kỳ vọng Response (HTTP 200 OK):** Nhận access token mới.
