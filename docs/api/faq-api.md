# CANi API Documentation — Phân Hệ Quản Lý Câu Hỏi Thường Gặp & Help Center (SRS FE-12: UC-110)

> **Mục tiêu:** Tài liệu bàn giao kỹ thuật từ Backend sang đội ngũ Frontend (Web Admin / Customer Care / Mobile Worker / Event Organizer) và kiểm thử API cho chức năng Quản lý FAQ (UC-110a), Help Center công khai (UC-110b) và Thống kê hiệu quả giảm tải (UC-110c).
> **Người phụ trách Backend:** QuyTH
> **Phiên bản:** 1.0.0 (Spring Boot 3.5.1 / Java 21)
> **Base URL:** `/api/v1/faqs` (Help Center công khai) · `/api/v1/admin/faqs` (Customer Care & Admin)
> **Migration:** `V15__faq.sql` (PostgreSQL/Supabase — enum `faq_category`, `faq_status`, bảng `faqs`, trigger `trg_faqs_updated_at`, RLS deny-all dự phòng)

---

## 1. Tổng Quan Nghiệp Vụ

### 1.1. Phạm vi nghiệp vụ (SRS §4.2 UC-110, FE-12 Customer Care)
> *"Admin or Customer Care agent creates, edits, archives, or publishes FAQ articles organized by category (Account, Shift, Payment, Dispute). Published FAQs are surfaced in the platform's help center accessible to all users. Analytics on FAQ view counts and ticket deflection rates are available to identify gaps in self-service content."*

### 1.2. Vòng đời câu hỏi thường gặp (FAQ Lifecycle)
```
       Tạo mới (CC/Admin)
             │
             ▼
        [ DRAFT ] ────────── Chỉnh sửa nội dung
             │
      Xuất bản (publish)
             │
             ▼
      [ PUBLISHED ] ─────── Hiển thị trên Help Center (/api/v1/faqs)
             │              Tự động tăng view_count khi người dùng xem
       Lưu trữ (archive)
             │
             ▼
       [ ARCHIVED ] ──────── Ngừng hiển thị, không cho phép chỉnh sửa nội dung
```

### 1.3. Phân quyền truy cập (RBAC)
| Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- |
| `/api/v1/faqs/**` | `permitAll()` | Help Center công khai cho toàn bộ người dùng và khách truy cập |
| `/api/v1/admin/faqs/**` | `ADMIN`, `CUSTOMER_CARE` | Phân hệ quản trị dành riêng cho Ban quản trị & Nhân viên CSKH |

---

## 2. Công Thức & Cách Tính Ticket Deflection Rate (UC-110c)

### 2.1. Ý nghĩa nghiệp vụ
**Ticket Deflection Rate** (Tỷ lệ giảm tải phiếu hỗ trợ) đo lường mức độ câu hỏi thường gặp (FAQ) tự phục vụ giúp giải quyết các thắc mắc mà người dùng thường phải tạo ticket gửi đến bộ phận CSKH.

### 2.2. Công thức tính toán
$$\text{Ticket Deflection Rate (\%)} = \frac{\text{Số ticket thuộc các danh mục đã có FAQ xuất bản (PUBLISHED)}}{\text{Tổng số ticket hỗ trợ trên toàn hệ thống}} \times 100$$

- **Quy tắc ánh xạ danh mục giữa FAQ và Support Ticket:**
  - `FaqCategory.ACCOUNT` $\leftrightarrow$ `TicketCategory.ACCOUNT`
  - `FaqCategory.PAYMENT` $\leftrightarrow$ `TicketCategory.BILLING`
  - Các danh mục `SHIFT`, `DISPUTE` hiện chưa có nhóm ticket hỗ trợ trực tiếp.
- Nếu tổng số ticket trong hệ thống = 0: `deflectionRate = 0.0%`.
- Kết quả được làm tròn đến 2 chữ số thập phân.

---

## 3. Danh Sách Endpoints Chi Tiết

### 3.1. Help Center Công Khai (`/api/v1/faqs`)

#### [GET] `/api/v1/faqs` — Lấy danh sách FAQ công khai đã xuất bản
- **Quyền:** `permitAll()`
- **Query params:**
  - `category` (tùy chọn): `ACCOUNT`, `SHIFT`, `PAYMENT`, `DISPUTE`
- **Sắp xếp:** Sắp xếp theo `view_count` giảm dần, sau đó theo `published_at` mới nhất.
- **Response mẫu (200 OK):**
```json
{
  "success": true,
  "message": "Lấy danh sách câu hỏi thường gặp thành công",
  "data": [
    {
      "id": "7a356c36-69a4-4f81-9877-66db0d368e7b",
      "category": "ACCOUNT",
      "question": "Làm sao để đặt lại mật khẩu tài khoản?",
      "answer": "Vào màn hình Đăng nhập, bấm Quên mật khẩu và nhập email để nhận mã OTP khôi phục.",
      "viewCount": 42
    }
  ]
}
```

#### [GET] `/api/v1/faqs/{id}` — Xem chi tiết FAQ và tăng lượt xem
- **Quyền:** `permitAll()`
- **Hành vi:** Kiểm tra FAQ tồn tại và có trạng thái `PUBLISHED`. Tự động tăng `view_count = view_count + 1`.
- **Response mẫu (200 OK):**
```json
{
  "success": true,
  "message": "Lấy thông tin chi tiết câu hỏi thường gặp thành công",
  "data": {
    "id": "7a356c36-69a4-4f81-9877-66db0d368e7b",
    "category": "ACCOUNT",
    "question": "Làm sao để đặt lại mật khẩu tài khoản?",
    "answer": "Vào màn hình Đăng nhập, bấm Quên mật khẩu và nhập email để nhận mã OTP khôi phục.",
    "viewCount": 43
  }
}
```

---

### 3.2. Quản Trị FAQ Dành Cho CC & Admin (`/api/v1/admin/faqs`)

#### [GET] `/api/v1/admin/faqs` — Danh sách quản lý FAQ (hỗ trợ lọc & phân trang)
- **Quyền:** `hasAnyRole('ADMIN', 'CUSTOMER_CARE')`
- **Query params:**
  - `status`: `DRAFT`, `PUBLISHED`, `ARCHIVED` (tùy chọn)
  - `category`: `ACCOUNT`, `SHIFT`, `PAYMENT`, `DISPUTE` (tùy chọn)
  - `page`: số trang (bắt đầu từ 0)
  - `size`: số bản ghi / trang (mặc định 20)
- **Response mẫu (200 OK):**
```json
{
  "success": true,
  "message": "Lấy danh sách quản lý FAQ thành công",
  "data": {
    "content": [
      {
        "id": "7a356c36-69a4-4f81-9877-66db0d368e7b",
        "category": "ACCOUNT",
        "question": "Làm sao để đặt lại mật khẩu tài khoản?",
        "answer": "Vào màn hình Đăng nhập, bấm Quên mật khẩu và nhập email để nhận mã OTP khôi phục.",
        "status": "PUBLISHED",
        "authorId": "11111111-1111-1111-1111-111111111111",
        "authorName": "Nhân viên CSKH",
        "publishedAt": "2026-11-07T07:40:00Z",
        "archivedAt": null,
        "viewCount": 43,
        "createdAt": "2026-11-07T07:30:00Z",
        "updatedAt": "2026-11-07T07:45:00Z"
      }
    ],
    "page": {
      "size": 20,
      "number": 0,
      "totalElements": 1,
      "totalPages": 1
    }
  }
}
```

#### [POST] `/api/v1/admin/faqs` — Tạo mới câu hỏi thường gặp
- **Quyền:** `hasAnyRole('ADMIN', 'CUSTOMER_CARE')`
- **Request Body:**
```json
{
  "category": "ACCOUNT",
  "question": "Làm sao để đặt lại mật khẩu tài khoản?",
  "answer": "Vào màn hình Đăng nhập, bấm Quên mật khẩu và nhập email để nhận mã OTP khôi phục."
}
```
- **Ràng buộc kiểm tra tính hợp lệ (Validation):**
  - `category`: Bắt buộc (`@NotNull`).
  - `question`: Không được để trống, độ dài từ **10 đến 500 ký tự**.
  - `answer`: Không được để trống, độ dài tối thiểu **20 ký tự**.
- **Audit Action:** `CREATE_FAQ`

#### [PUT] `/api/v1/admin/faqs/{id}` — Cập nhật câu hỏi thường gặp
- **Quyền:** `hasAnyRole('ADMIN', 'CUSTOMER_CARE')`
- **Ràng buộc:** Chỉ chỉnh sửa khi status là `DRAFT` hoặc `PUBLISHED`. Nếu FAQ đã `ARCHIVED` sẽ trả về lỗi `400 Bad Request`.
- **Audit Action:** `UPDATE_FAQ`

#### [POST] `/api/v1/admin/faqs/{id}/publish` — Xuất bản câu hỏi lên Help Center
- **Quyền:** `hasAnyRole('ADMIN', 'CUSTOMER_CARE')`
- **Hành vi:**
  - Chuyển `status = PUBLISHED`, gán `published_at = now()`.
  - Nếu đã `PUBLISHED`: Đảm bảo tính idempotent, trả về bản ghi hiện tại.
  - Nếu đã `ARCHIVED`: Trả về `400 Bad Request`.
- **Audit Action:** `PUBLISH_FAQ`

#### [POST] `/api/v1/admin/faqs/{id}/archive` — Lưu trữ câu hỏi thường gặp
- **Quyền:** `hasAnyRole('ADMIN', 'CUSTOMER_CARE')`
- **Hành vi:** Chuyển `status = ARCHIVED`, gán `archived_at = now()`. Ngừng hiển thị trên Help Center công khai. Không xoá vật lý để lưu vết lịch sử.
- **Audit Action:** `ARCHIVE_FAQ`

#### [GET] `/api/v1/admin/faqs/analytics` — Thống kê hiệu quả FAQ (UC-110c)
- **Quyền:** `hasAnyRole('ADMIN', 'CUSTOMER_CARE')`
- **Response mẫu (200 OK):**
```json
{
  "success": true,
  "message": "Lấy thống kê FAQ thành công",
  "data": {
    "totalViews": 1420,
    "viewsByCategory": {
      "ACCOUNT": 650,
      "SHIFT": 420,
      "PAYMENT": 280,
      "DISPUTE": 70
    },
    "topFaqs": [
      {
        "id": "7a356c36-69a4-4f81-9877-66db0d368e7b",
        "category": "ACCOUNT",
        "question": "Làm sao để đặt lại mật khẩu tài khoản?",
        "viewCount": 350
      }
    ],
    "ticketDeflectionRate": 48.75,
    "calculationFormula": "deflectionRate = (số ticket có category tương ứng FAQ đã publish) / (tổng số ticket) * 100"
  }
}
```

---

## 4. Bảng Mã Lỗi (Tiếng Việt)

| Mã HTTP | Tình huống | Thông điệp lỗi mẫu |
| :--- | :--- | :--- |
| `400 Bad Request` | DTO không hợp lệ | "Câu hỏi phải có từ 10 đến 500 ký tự" |
| `400 Bad Request` | DTO không hợp lệ | "Câu trả lời phải có ít nhất 20 ký tự" |
| `400 Bad Request` | Sửa FAQ đã lưu trữ | "Không thể chỉnh sửa câu hỏi đã lưu trữ" |
| `400 Bad Request` | Xuất bản FAQ đã lưu trữ | "Không thể xuất bản câu hỏi đã lưu trữ" |
| `403 Forbidden` | Worker / Role khác truy cập API admin | "Access Denied" |
| `404 Not Found` | Không tìm thấy ID FAQ | "Không tìm thấy Câu hỏi thường gặp với id: ..." |

---

## 5. Audit Log Actions (Ghi Nhận Nhật Ký Vận Hành)

| Action Code | Diễn giải (`AdminDashboardService`) | Entity Type |
| :--- | :--- | :--- |
| `CREATE_FAQ` | Tạo câu hỏi thường gặp | `FAQ` |
| `UPDATE_FAQ` | Cập nhật câu hỏi thường gặp | `FAQ` |
| `PUBLISH_FAQ` | Xuất bản câu hỏi thường gặp | `FAQ` |
| `ARCHIVE_FAQ` | Lưu trữ câu hỏi thường gặp | `FAQ` |

