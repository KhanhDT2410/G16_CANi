# CANi API Testing Guide — Hướng Dẫn Kiểm Thử Trực Quan Trên Swagger UI (Springdoc OpenAPI)

> **Mục tiêu:** Hướng dẫn toàn diện quy trình chạy thử nghiệm, xác thực JWT và kiểm thử chu trình nghiệp vụ End-to-End (E2E) các API của nền tảng CANi trực tiếp trên giao diện Swagger UI.
> **Người biên soạn:** HungPD (Đội ngũ Kỹ nghệ CANi - SEP490_G16)
> **Tham chiếu chuẩn:** Cấu trúc tài liệu `docs/api/dispute-resolution-api.md` & `docs/api/auth-postman-guide.md` (QuyTH)
> **Phiên bản hệ thống:** Spring Boot 3.5.1 / Java 21 / Springdoc OpenAPI v2.8.16
> **Swagger UI URL:** `http://localhost:8080/swagger-ui/index.html` (hoặc `http://localhost:8080/swagger-ui.html`)
> **OpenAPI JSON Spec:** `http://localhost:8080/v3/api-docs`

---

## 1. Tổng Quan Kiến Trúc & Thiết Lập Swagger UI

Hệ thống CANi tích hợp thư viện **Springdoc OpenAPI v2.8.16** hỗ trợ chuẩn **OpenAPI 3.0**, cấu hình chế độ bảo mật **Bearer Authentication (JWT)** toàn cục.

```
+-----------------------------------------------------------------------------------+
|                              SWAGGER UI INTERFACE                                 |
|                         http://localhost:8080/swagger-ui/index.html               |
+-----------------------------------------------------------------------------------+
        │                                                           │
        │ 1. POST /api/auth/login                                    │ 3. Gửi Request kèm Header:
        ▼ (Không cần Token)                                         ▼ Authorization: Bearer <JWT>
+-----------------------------+                             +-----------------------+
|  Lấy Access Token từ        | ──── 2. Nhấn Authorize ───> |  Tự động gắn Token    |
|  JSON: data.token           |        (Dán chuỗi JWT)      |  vào mọi API có khóa  |
+-----------------------------+                             +-----------------------+
```

### 1.1. Cấu hình bảo mật trong Backend
- Toàn bộ đường dẫn Swagger (`/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`) đã được thiết lập `permitAll()` trong `SecurityConfig.java`.
- Filter `UserAuthorityFilter` sẽ bỏ qua các tài nguyên Swagger tĩnh, đồng thời giải mã JWT Bearer token và nạp `SecurityContext` cho các API nghiệp vụ có gắn `@PreAuthorize`.

### 1.2. Khởi động Backend
Từ thư mục gốc dự án, di chuyển vào `backend` và chạy lệnh:

```bash
# Windows PowerShell / CMD
cd D:\SEP490\Code\backend
.\mvnw.cmd spring-boot:run
```
Khi Console xuất hiện dòng thông báo:
```text
Started CaniApplication in X.XXX seconds (process running for X.XXX)
```
Hệ thống đã sẵn sàng phục vụ tại cổng mặc định `8080`. Mở trình duyệt và truy cập:
👉 **`http://localhost:8080/swagger-ui/index.html`**

---

## 2. Chuẩn Bị Dữ Liệu Mẫu (Database Seeding)

Hệ thống quản lý định danh người dùng thông qua Supabase GoTrue (`auth.users`) và đồng bộ dữ liệu nghiệp vụ sang `public.users`. 

Nếu cơ sở dữ liệu chưa có sẵn tài khoản mẫu, thực thi đoạn script SQL sau trong **SQL Editor** trên [Supabase Dashboard](https://supabase.com/dashboard):

```sql
-- Kích hoạt extension pgcrypto để băm mật khẩu chuẩn GoTrue
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
  v_admin_id  UUID := '00000000-0000-0000-0000-000000000001';
  v_cskh_id   UUID := '00000000-0000-0000-0000-000000000002';
  v_eo_id     UUID := '00000000-0000-0000-0000-000000000003';
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

  -- 3. Nạp hồ sơ liên kết tương ứng
  INSERT INTO public.event_organizers (id, user_id, company_name, verification_status)
  VALUES (gen_random_uuid(), v_eo_id, 'Công ty Sự Kiện Á Châu', 'APPROVED')
  ON CONFLICT (user_id) DO NOTHING;

  INSERT INTO public.workers (id, user_id, full_name, reliability_score, skill_tier, kyc_status, is_online)
  VALUES (gen_random_uuid(), v_worker_id, 'Nguyễn Văn Worker', 100, 'STARTER', 'APPROVED', true)
  ON CONFLICT (user_id) DO NOTHING;
END $$;
```

### Danh Sách 4 Tài Khoản Kiểm Thử Chuẩn:
| Email | Mật khẩu mặc định | Role (`vai trò`) | Phạm vi phân quyền API |
| :--- | :--- | :--- | :--- |
| **`eo@cani.vn`** | `Password123!` | `EVENT_ORGANIZER` | Quản lý sự kiện, tạo ca làm, xuất bản, duyệt công, đánh giá Worker |
| **`worker@cani.vn`** | `Password123!` | `WORKER` | Bật Online, nhận đề xuất, ứng tuyển, QR Check-in/Check-out, xem thu nhập |
| **`cskh@cani.vn`** | `Password123!` | `CUSTOMER_CARE` | Hàng đợi tranh chấp, tiếp nhận, điều tra & ra phán quyết hòa giải |
| **`admin@cani.vn`** | `Password123!` | `ADMIN` | Toàn quyền cấu hình hệ thống, phán quyết kháng nghị tối cao (UC-102) |

---

## 3. Quy Trình Xác Thực JWT Trên Swagger UI (Authorize)

### Bước 1: Gọi API Đăng nhập trên Swagger
1. Tìm đến nhóm thẻ **`auth-controller`**.
2. Chọn endpoint: `POST /api/auth/login`.
3. Bấm nút **Try it out**.
4. Nhập Payload đăng nhập theo tài khoản bạn muốn kiểm thử:
```json
{
  "email": "worker@cani.vn",
  "password": "Password123!"
}
```
5. Bấm nút **Execute**.
6. Ở khung **Server response (HTTP 200)**, tìm trường `token` nằm trong đối tượng `data`:
```json
{
  "success": true,
  "message": "Đăng nhập thành công",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ...",
    "refreshToken": "...",
    "expiresIn": 3600,
    "user": {
      "id": "00000000-0000-0000-0000-000000000004",
      "email": "worker@cani.vn",
      "role": "WORKER"
    }
  }
}
```
7. Bôi đen và copy toàn bộ chuỗi ký tự trong dấu ngoặc kép của `"token"`.

### Bước 2: Kích hoạt Authorization toàn cục
1. Cuộn lên đầu trang Swagger UI hoặc tìm nút **Authorize 🔓** (có biểu tượng ổ khóa màu xanh lá ở góc phải phía trên).
2. Một cửa sổ pop-up hiện ra mang tên **Available authorizations**:
   - Tên Scheme: `Bearer Authentication (http, Bearer)`.
   - Ô nhập: `Value`.
3. **Dán trực tiếp chuỗi token** vừa copy vào ô `Value`.
   > ⚠️ **LƯU Ý QUAN TRỌNG:** KHÔNG cần gõ thêm từ `Bearer ` phía trước. Cấu hình OpenAPI đã tự động thêm tiền tố này vào Request Header.
4. Bấm nút **Authorize** -> Biểu tượng ổ khóa sẽ chuyển sang trạng thái đã khóa 🔒.
5. Bấm nút **Close**.
6. Từ thời điểm này, mọi request gửi từ Swagger UI sẽ tự động đính kèm Header:
   `Authorization: Bearer <chuỗi_token_của_bạn>`.

### Bước 3: Đổi Role / Chuyển tài khoản kiểm thử
Khi muốn chuyển sang kiểm thử vai trò khác (ví dụ từ `WORKER` sang `EVENT_ORGANIZER`):
1. Gọi lại `POST /api/auth/login` với email của EO (`eo@cani.vn`).
2. Copy `token` mới.
3. Bấm vào nút **Authorize 🔒**, bấm **Logout**, sau đó dán token mới vào và bấm **Authorize**.

---

## 4. Kịch Bản Kiểm Thử Chu Trình Nghiệp Vụ Thực Tế (E2E Test Flow)

Chu trình phối hợp giữa Nhà tổ chức sự kiện (EO) và Lao động tự do (Worker):

```
[EO] Tạo Sự Kiện (Draft) ──> Thêm Ca Làm (Shift) ──> Xuất Bản Sự Kiện (Publish & Auto-Match)
                                                                 │
                                                                 ▼
[Worker] Quét Tìm Ca (Job Radar) HOẶC Nhận Đề Xuất (Proposal) ──> Chấp Nhận Ca (Accept)
                                                                 │
                                                                 ▼
[EO] Lấy Mã QR Điểm Danh (Shift QR) <───────────────────────────┘
                               │
                               ▼
[Worker] Quét QR Check-in (Kèm tọa độ GPS trong bán kính 200m)
                               │
                               ▼
[Worker] Thực hiện & Cập nhật Tiến độ Checklist công việc
                               │
                               ▼
[Worker] Quét QR Check-out (Sau tối thiểu 30 phút làm việc)
                               │
                               ▼
[EO] Xem Bảng Điểm Danh Trực Tiếp ──> Duyệt Công Cho Worker (Verify Attendance)
                               │
                               ▼
[Cả 2 Bên] Đánh Giá & Chấm Điểm 1-5★ Lẫn Nhau (Review & Rating)
```

---

### Kịch bản 1: Nhà tổ chức tạo & xuất bản Sự kiện (Role: EVENT_ORGANIZER)

*Đăng nhập bằng tài khoản `eo@cani.vn` và Authorize token.*

#### 1. Tạo sự kiện mới (UC-10, FE-08)
- **Endpoint:** `POST /api/v1/eo/events`
- **Request Body:**
```json
{
  "name": "Triển Lãm Công Nghệ TechExpo 2026",
  "description": "Sự kiện công nghệ thường niên quy mô lớn tại Hà Nội",
  "venueAddress": "Trung tâm Hội nghị Quốc gia, Mễ Trì, Nam Từ Liêm, Hà Nội",
  "venueLat": 21.0068,
  "venueLng": 105.7876,
  "eventDate": "2026-12-25",
  "mode": "MATCHING"
}
```
- **Kỳ vọng:** Response 200 OK. Lưu lại trường `data.id` (ví dụ: `v_event_id`).

#### 2. Thêm ca làm việc vào sự kiện (UC-17, FE-08)
- **Endpoint:** `POST /api/v1/eo/events/{eventId}/shifts` (Thay `{eventId}` bằng `v_event_id`)
- **Request Body:**
```json
{
  "jobType": "Lễ tân & Điều phối khách mời",
  "description": "Đón tiếp đại biểu và hỗ trợ check-in khách tham quan",
  "startTime": "2026-12-25T08:00:00Z",
  "endTime": "2026-12-25T12:00:00Z",
  "requiredWorkers": 3,
  "hourlyWage": 50000,
  "allowance": 20000,
  "locationAddress": "Sảnh A, Trung tâm Hội nghị Quốc gia, Hà Nội",
  "locationLat": 21.0068,
  "locationLng": 105.7876,
  "minReliabilityScore": 60,
  "maxDistanceKm": 20.0,
  "minExperienceLevel": "BEGINNER",
  "dressCode": "Áo sơ mi trắng, quần âu tối màu",
  "checklist": "[\"Đón khách tại cổng\",\"Phát tài liệu hội thảo\",\"Hướng dẫn lối vào hội trường\"]"
}
```
- **Kỳ vọng:** Response 200 OK. Lưu lại trường `data.id` của ca làm việc (ví dụ: `v_shift_id`).

#### 3. Xuất bản sự kiện & kích hoạt điều phối (UC-20)
- **Endpoint:** `POST /api/v1/eo/events/{eventId}/publish`
- **Kỳ vọng:** Trạng thái sự kiện chuyển sang `PUBLISHED`. Thuật toán Matching tự động quét Worker phù hợp và tạo `Proposal`.

---

### Kịch bản 2: Worker Tìm kiếm & Nhận việc (Role: WORKER)

*Đăng nhập bằng tài khoản `worker@cani.vn` và Authorize token mới.*

#### 1. Bật trạng thái Online sẵn sàng nhận việc (UC-14, FE-02)
- **Endpoint:** `POST /api/v1/workers/status`
- **Request Body:**
```json
{
  "isOnline": true,
  "currentLat": 21.0065,
  "currentLng": 105.7870
}
```
- **Kỳ vọng:** `data: true`, thông báo bật chế độ kiếm việc thành công.

#### 2. Quét tìm ca làm xung quanh qua Job Radar (UC-15, FE-03)
- **Endpoint:** `GET /api/v1/workers/shifts/nearby`
- **Query Params:**
  - `lat`: `21.0065`
  - `lng`: `105.7870`
  - `radiusKm`: `15.0`
- **Kỳ vọng:** Danh sách các ca làm việc gần Worker kèm khoảng cách tính theo công thức Haversine.

#### 3. Ứng tuyển trực tiếp vào ca làm (UC-16, FE-03)
- **Endpoint:** `POST /api/v1/workers/shifts/{shiftId}/apply` (Điền `v_shift_id`)
- **Kỳ vọng:** Response 200 OK, tạo mới một `assignment`. Lưu lại `data.id` (ví dụ: `v_assignment_id`).

*(Hoặc nếu nhận đề xuất tự động từ hệ thống: Gọi `GET /api/v1/workers/proposals` -> Lấy `proposalId` -> Gọi `POST /api/v1/workers/proposals/{proposalId}/accept`)*.

---

### Kịch bản 3: Điểm danh vào ca & Điểm danh hoàn thành (QR Check-in & Check-out)

*Hệ thống áp dụng cơ chế xác thực kép: Đúng nội dung QR của Ca + Tọa độ GPS nằm trong bán kính 200m so với địa điểm tổ chức.*

#### 1. EO lấy nội dung mã QR của ca làm việc
- *Authorize token của EO (`eo@cani.vn`)*
- **Endpoint:** `GET /api/v1/eo/events/shifts/{shiftId}/qr` (Điền `v_shift_id`)
- **Response JSON:**
```json
{
  "success": true,
  "data": {
    "shiftId": "...",
    "checkinQrContent": "CANI_CHECKIN:<shiftId>",
    "checkoutQrContent": "CANI_CHECKOUT:<shiftId>"
  }
}
```

#### 2. Worker quét QR Check-in điểm danh vào ca (UC-37, FE-05)
- *Authorize token của Worker (`worker@cani.vn`)*
- **Endpoint:** `POST /api/v1/workers/assignments/{assignmentId}/checkin` (Điền `v_assignment_id`)
- **Request Body:**
```json
{
  "qrContent": "CANI_CHECKIN:<v_shift_id>",
  "lat": 21.0068,
  "lng": 105.7876
}
```
- **Quy tắc kiểm thử:**
  - Nếu tọa độ nằm cách địa điểm > 200m: Hệ thống chặn với lỗi `400 Bad Request` (*"Bạn đang ở cách địa điểm làm việc ...m, vượt quá bán kính cho phép (200m)"*).
  - Nếu ca đã bị hủy hoặc chưa được xác nhận: Chặn với lỗi `400 Bad Request`.
  - Nếu đúng QR và trong phạm vi: Chuyển trạng thái sang `CHECKED_IN`, ghi nhận `ON_TIME` hoặc `LATE`.

#### 3. Worker xem & tích hoàn thành Checklist (UC-45, FE-05)
- **Lấy Checklist:** `GET /api/v1/workers/assignments/{assignmentId}/checklist`
- **Cập nhật Checklist:** `PUT /api/v1/workers/assignments/{assignmentId}/checklist`
```json
{
  "items": [
    { "task": "Đón khách tại cổng", "completed": true },
    { "task": "Phát tài liệu hội thảo", "completed": true },
    { "task": "Hướng dẫn lối vào hội trường", "completed": false }
  ]
}
```

#### 4. Worker quét QR Check-out hoàn thành ca (UC-38, FE-05)
- **Endpoint:** `POST /api/v1/workers/assignments/{assignmentId}/checkout` (Điền `v_assignment_id`)
- **Request Body:**
```json
{
  "qrContent": "CANI_CHECKOUT:<v_shift_id>",
  "lat": 21.0068,
  "lng": 105.7876
}
```
- **Quy tắc kiểm thử:**
  - Bắt buộc phải ở trạng thái `CHECKED_IN` mới được checkout.
  - Thời lượng làm việc tối thiểu được tính là 30 phút theo quy định nền tảng.
  - Sau khi check-out thành công, trạng thái ca chuyển sang `COMPLETED`.

---

### Kịch bản 4: Ban Tổ Chức Duyệt Công & Đánh Giá (Role: EVENT_ORGANIZER)

*Authorize token của EO (`eo@cani.vn`).*

#### 1. Theo dõi bảng điểm danh trực tiếp (UC-48, FE-09)
- **Endpoint:** `GET /api/v1/eo/events/shifts/{shiftId}/attendance`
- **Kỳ vọng:** Trả về danh sách nhân sự đã check-in, giờ vào, giờ ra, số phút làm thực tế và trạng thái duyệt công.

#### 2. Xác nhận duyệt công & thanh toán (UC-49, FE-09)
- **Endpoint:** `POST /api/v1/eo/events/assignments/{assignmentId}/verify` (Điền `v_assignment_id`)
- **Kỳ vọng:** Xác nhận hoàn thành ca, chuyển trạng thái duyệt sang `VERIFIED`.

#### 3. EO chấm điểm Worker (UC-69, FE-10)
- **Endpoint:** `POST /api/v1/eo/events/assignments/{assignmentId}/reviews`
- **Request Body:**
```json
{
  "rating": 5,
  "comment": "Worker làm việc rất chuyên nghiệp, đúng giờ và nhiệt tình hỗ trợ khách mời!"
}
```
- **Kỳ vọng:** Hệ thống cập nhật điểm đánh giá trung bình và cộng điểm uy tín cho Worker.

---

### Kịch bản 5: Worker Xem Thống Kê Thu Nhập (Role: WORKER)

*Authorize token của Worker (`worker@cani.vn`).*

- **Endpoint:** `GET /api/v1/workers/income-summary`
- **Kỳ vọng Response (HTTP 200 OK):**
```json
{
  "success": true,
  "message": "Lấy thống kê thu nhập thành công",
  "data": {
    "totalEarned": 220000,
    "pendingPayment": 0,
    "availableBalance": 220000,
    "completedJobsCount": 1,
    "recentPayments": [
      {
        "amount": 220000,
        "status": "COMPLETED",
        "paidAt": "..."
      }
    ]
  }
}
```

---

### Kịch bản 6: Nộp & Xử Lý Tranh Chấp Khiếu Nại (FE-11: UC-96 → UC-102)

*Áp dụng khi phát sinh sự cố về tiền công hoặc chất lượng công việc.*

#### 1. Worker gửi đơn khiếu nại (Role: WORKER)
- **Endpoint:** `POST /api/v1/disputes`
- **Request Body:**
```json
{
  "assignmentId": "<v_assignment_id>",
  "type": "PAYMENT",
  "title": "Chưa nhận đủ phụ cấp ăn trưa theo thỏa thuận",
  "description": "Ban tổ chức thông báo có phụ cấp 20,000 VND tiền ăn trưa nhưng quyết toán chưa bao gồm khoản tiền này..."
}
```
- **Lưu ý nghiệp vụ (BR-52):** Mô tả (`description`) bắt buộc **tối thiểu 50 ký tự** và nộp trong vòng **14 ngày** kể từ khi ca kết thúc. Lưu lại `data.id` (ví dụ: `v_dispute_id`).

#### 2. Nộp tài liệu bằng chứng
- **Endpoint:** `POST /api/v1/disputes/{id}/evidence`
```json
{
  "fileType": "IMAGE",
  "fileUrl": "https://storage.cani.vn/disputes/bien-ban-xac-nhan.jpg",
  "label": "Ảnh chụp biên bản xác nhận phụ cấp tại quầy sự kiện"
}
```

#### 3. Chuyên viên CSKH tiếp nhận & xử lý (Role: CUSTOMER_CARE)
*Authorize token của `cskh@cani.vn`.*
- **Xem hàng đợi xử lý ưu tiên (UC-86):** `GET /api/v1/admin/disputes/queue`
- **Nhận quyền xử lý đơn (UC-99):** `POST /api/v1/admin/disputes/{id}/assign`
- **Ra quyết định phán quyết (UC-100):** `POST /api/v1/admin/disputes/{id}/decision`
```json
{
  "outcome": "EO_FAULT",
  "rationale": "Căn cứ theo bản mô tả ca làm việc có ghi rõ phụ cấp 20,000 VND và bằng chứng Worker cung cấp là hợp lệ. Yêu cầu EO thanh toán bổ sung cho Worker. (Độ dài giải trình tối thiểu 100 ký tự theo quy định)",
  "penaltyWaiverReason": null
}
```
*(Ghi chú: Trường `rationale` bắt buộc từ **100 ký tự** trở lên theo nghiệp vụ BR-55)*.

---

## 5. Bảng Đối Chiếu Mã Lỗi HTTP Thường Gặp (Error Reference)

Mọi phản hồi lỗi từ Backend đều tuân thủ định dạng chuẩn `ApiResponse`:
```json
{
  "success": false,
  "message": "Chi tiết nguyên nhân lỗi bằng tiếng Việt có dấu",
  "data": null,
  "timestamp": "2026-11-05T21:30:00Z"
}
```

| HTTP Status | Nguyên nhân phổ biến trên Swagger | Cách khắc phục |
| :--- | :--- | :--- |
| **`400 Bad Request`** | Dữ liệu Body không thỏa mãn `@Valid` (thiếu trường bắt buộc, chuỗi quá ngắn, lương < 20,000đ); vi phạm Geofence 200m; quét QR sai format; nộp tranh chấp quá 14 ngày. | Đọc thông điệp trong trường `message` của Response để sửa lại đúng trường dữ liệu hoặc tọa độ GPS. |
| **`401 Unauthorized`** | Chưa bấm **Authorize** trên Swagger; token đã hết hạn sau 1 giờ (`expiresIn: 3600`); copy thiếu ký tự token. | Gọi lại `POST /api/auth/login`, copy trọn vẹn chuỗi `data.token` và dán vào nút **Authorize 🔓**. |
| **`403 Forbidden`** | Đăng nhập bằng tài khoản không có quyền gọi endpoint (ví dụ: dùng token `WORKER` để gọi API của `EVENT_ORGANIZER` hoặc `ADMIN`). | Đổi tài khoản phù hợp với bảng phân quyền RBAC ở Mục 2. |
| **`404 Not Found`** | ID ca làm, sự kiện, hoặc assignment truyền vào URL không tồn tại trong cơ sở dữ liệu. | Kiểm tra lại UUID đã copy từ các bước tạo trước đó. |
| **`500 Internal Error`**| Cơ sở dữ liệu Supabase bị gián đoạn kết nối hoặc lỗi chưa bắt ngoại lệ. | Kiểm tra terminal chạy `./mvnw.cmd spring-boot:run` để xem stack trace chi tiết. |

---

## 6. Mẹo & Thủ Thuật Khi Test Trên Swagger UI (Pro-Tips)

1. **Giữ tab Swagger mở:** Khi bạn bấm Refresh trình duyệt, Swagger UI có thể xóa token khỏi bộ nhớ tạm. Hãy lưu sẵn token ra file Notepad để dán lại nhanh chóng nếu cần.
2. **Kiểm tra nhanh User đang đăng nhập:** Gọi `GET /api/auth/me` để kiểm tra xem hệ thống đang nhận diện bạn là ai, thuộc role nào và ID là gì.
3. **Mô phỏng tọa độ GPS chính xác:** 
   - Địa điểm tổ chức: `lat: 21.0068, lng: 105.7876` (Trung tâm Hội nghị Quốc gia).
   - Tọa độ Worker hợp lệ (< 200m): `lat: 21.0069, lng: 105.7877` (~15 mét cách tâm).
   - Tọa độ Worker bị chặn (> 200m): `lat: 21.0150, lng: 105.7876` (~910 mét cách tâm).
4. **Chuỗi QR chuẩn:**
   - Check-in: `CANI_CHECKIN:<UUID_CỦA_SHIFT>`
   - Check-out: `CANI_CHECKOUT:<UUID_CỦA_SHIFT>`

---
*Tài liệu được cập nhật tự động và đồng bộ với nhánh `hung` của dự án CANi SEP490.*
