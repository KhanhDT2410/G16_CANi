# CANi API Documentation — Phân Hệ Phiếu Hỗ Trợ Khách Hàng (FE-12: UC-105 · UC-106 · SCR-25 · SCR-26)

> **Mục tiêu:** Tài liệu bàn giao kỹ thuật từ Backend sang đội ngũ Frontend (Web Admin / Customer Care / Mobile Worker / Event Organizer) và kiểm thử API cho chức năng Support Tickets.
> **Người phụ trách Backend:** QuyTH
> **Phiên bản:** 1.0.0 (Spring Boot 3.5.1 / Java 21)
> **Base URL:** `/api/v1/support/tickets` (User) · `/api/v1/admin/support/tickets` (CC / Admin)
> **Migration:** `V13__support_tickets.sql` (PostgreSQL/Supabase — tạo enum `ticket_status`, `ticket_category`, `ticket_priority`, bảng `support_tickets`, `support_ticket_messages`; profile H2 dùng `ddl-auto` từ entity)

---

## 1. Tổng Quan Nghiệp Vụ

Vòng đời phiếu hỗ trợ theo SRS (UC-105 / UC-106 · SCR-25 / SCR-26 · BR-58 · BR-70):

```
                     ┌────────────────── User tự markResolved (UC-106) ──────────────────┐
                     │                                                                   │
                     ▼                                                                   ▼
OPEN ──CC nhận/gán (SCR-25/26)──> IN_PROGRESS ──CC/Admin đóng kèm lý do (SCR-26)──> CLOSED
  │                                     │
  └─────── Trao đổi thread (UC-106/SCR-26) ───────┘
          (Tin nhắn công khai & Internal note)
```

**Quy tắc nghiệp vụ chính:**
- **UC-105 (Nộp ticket):** Người dùng chọn danh mục (`category`), tiêu đề và mô tả tối thiểu **30 ký tự** (BR/MSG151). Tùy chọn gửi kèm mảng URL ảnh đính kèm (`attachments`).
- **BR-58 (SLA theo mức độ ưu tiên):** Hệ thống tự động gán `priority` và tính `slaDeadline` theo danh mục:
  - `BILLING`, `ACCOUNT` $\rightarrow$ Priority `HIGH`: Hạn xử lý **4 giờ** (`now + 4h`).
  - `TECHNICAL`, `GENERAL` $\rightarrow$ Priority `MEDIUM`: Hạn xử lý **1 ngày** (`now + 1d`).
  - `OTHER` $\rightarrow$ Priority `LOW`: Hạn xử lý **3 ngày** (`now + 3d`).
- **SCR-25 (Hàng đợi CC):** Lấy các ticket trạng thái `OPEN` hoặc `IN_PROGRESS`, sắp xếp theo: (1) Nguy cơ vi phạm SLA (`slaAtRisk`: deadline trong vòng < 24 giờ hoặc đã quá hạn), (2) Thời gian tạo cũ nhất. Giới hạn tối đa 50 ticket trong queue để tối ưu hiệu năng.
- **SCR-26 (Thread & Ghi chú nội bộ):** Hỗ trợ `is_internal = true` cho phép chuyên viên CC ghi chú nội bộ mà User thường không thấy (API tự động lọc khi User xem).
- **BR-70 (Audit Log):** Mọi hành động trọng yếu (`SUBMIT_TICKET`, `REPLY_TICKET`, `ASSIGN_TICKET`, `CLOSE_TICKET`) được ghi nhận độc lập qua `AuditLogService` (`REQUIRES_NEW`).
- **Thông báo (Notifications):** Bắn thông báo 2 chiều (User $\leftrightarrow$ CC) qua bảng `notifications` với type `SUPPORT_TICKET`.

**Phân quyền (RBAC):**
| Endpoint | Quyền |
| :--- | :--- |
| `/api/v1/support/tickets/**` | Mọi user đã xác thực (Service kiểm tra quyền sở hữu IDOR) |
| `/api/v1/admin/support/tickets/**` | Role `ADMIN` hoặc `CUSTOMER_CARE` |

---

## 2. Endpoints Dành Cho Người Dùng (Worker / Event Organizer)

### 2.1. Nộp ticket hỗ trợ (UC-105)
* **Endpoint:** `POST /api/v1/support/tickets`
* **Quyền:** Đã đăng nhập (`.authenticated()`)
* **Request Body:**
```json
{
  "category": "BILLING",
  "subject": "Lỗi thanh toán ca làm việc ngày 30/10",
  "description": "Tôi đã hoàn thành ca làm việc nhưng số dư trong ví chưa được cộng tiền từ nhà tổ chức.",
  "attachments": [
    "https://storage.cani.vn/tickets/evidence-1.jpg"
  ]
}
```
| Trường | Kiểu | Bắt buộc | Ràng buộc |
| :--- | :--- | :--- | :--- |
| `category` | Enum | Có | `ACCOUNT`, `BILLING`, `TECHNICAL`, `GENERAL`, `OTHER` |
| `subject` | String | Có | Tối đa 200 ký tự |
| `description` | String | Có | **Từ 30 đến 3000 ký tự** (MSG151) |
| `attachments` | List\<String\> | Không | Danh sách URL ảnh đính kèm |

* **Response (200 OK):**
```json
{
  "success": true,
  "message": "Ticket hỗ trợ đã được tạo thành công",
  "data": {
    "id": "7fa41890-a548-4cb9-99a3-5c661ef65b90",
    "referenceNo": "TKT-20261031-1A2B",
    "userId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
    "category": "BILLING",
    "priority": "HIGH",
    "subject": "Lỗi thanh toán ca làm việc ngày 30/10",
    "description": "Tôi đã hoàn thành ca làm việc nhưng số dư trong ví chưa được cộng tiền từ nhà tổ chức.",
    "attachments": [
      "https://storage.cani.vn/tickets/evidence-1.jpg"
    ],
    "status": "OPEN",
    "assignedTo": null,
    "slaDeadline": "2026-10-31T18:52:00Z",
    "resolvedBy": null,
    "closedAt": null,
    "slaAtRisk": true,
    "canReply": true,
    "createdAt": "2026-10-31T14:52:00Z",
    "updatedAt": "2026-10-31T14:52:00Z",
    "messages": [
      {
        "id": "3c7fa68d-80f4-4e7a-9be2-e64eef655011",
        "ticketId": "7fa41890-a548-4cb9-99a3-5c661ef65b90",
        "senderId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
        "body": "Tôi đã hoàn thành ca làm việc nhưng số dư trong ví chưa được cộng tiền từ nhà tổ chức.",
        "isInternal": false,
        "attachmentUrl": null,
        "createdAt": "2026-10-31T14:52:00Z"
      }
    ]
  }
}
```

---

### 2.2. Danh sách ticket của tôi (UC-106)
* **Endpoint:** `GET /api/v1/support/tickets/my`
* **Quyền:** Đã đăng nhập
* **Response (200 OK):** Danh sách các ticket của người dùng hiện tại sắp xếp theo thời gian tạo mới nhất (`createdAt DESC`).

---

### 2.3. Xem chi tiết ticket (UC-106)
* **Endpoint:** `GET /api/v1/support/tickets/{id}`
* **Quyền:** Chủ sở hữu ticket HOẶC role `CUSTOMER_CARE`/`ADMIN` (người dùng khác bị trả về `400 Bad Request` - MSG34).
* **Ghi chú:** Với người dùng thường, các tin nhắn nội bộ (`isInternal = true`) sẽ tự động được lọc bỏ khỏi mảng `messages`.

---

### 2.4. Người dùng phản hồi ticket (UC-106)
* **Endpoint:** `POST /api/v1/support/tickets/{id}/reply`
* **Request Body:**
```json
{
  "body": "Tôi gửi thêm mã giao dịch ca làm việc: TRX-8849204.",
  "attachmentUrl": "https://storage.cani.vn/tickets/receipt.png"
}
```
| Trường | Kiểu | Bắt buộc | Ràng buộc |
| :--- | :--- | :--- | :--- |
| `body` | String | Có | Từ 5 đến 3000 ký tự |
| `attachmentUrl` | String | Không | Tối đa 500 ký tự |
| `isInternal` | Boolean | Không | Nếu gửi `true` với vai trò người dùng thường $\rightarrow$ ném lỗi 400 |

---

### 2.5. Người dùng đánh dấu đã giải quyết (UC-106)
* **Endpoint:** `POST /api/v1/support/tickets/{id}/resolve`
* **Quyền:** Chỉ người nộp ticket mới được gọi.
* **Mô tả:** Chuyển trạng thái ticket sang `CLOSED`, ghi nhận `resolvedBy` là `userId`, cập nhật `closedAt = now()`.

---

## 3. Endpoints Dành Cho Customer Care & Admin (SCR-25 / SCR-26)

### 3.1. Hàng đợi ticket cần xử lý (SCR-25)
* **Endpoint:** `GET /api/v1/admin/support/tickets/queue`
* **Quyền:** `CUSTOMER_CARE` hoặc `ADMIN`
* **Mô tả:** Trả về tối đa 50 ticket đang mở (`OPEN` hoặc `IN_PROGRESS`), sắp xếp ưu tiên theo `slaAtRisk` (deadline trong < 24h) rồi đến ticket cũ nhất.
* **Response (200 OK):**
```json
{
  "success": true,
  "message": "Lấy hàng đợi ticket thành công",
  "data": [
    {
      "id": "7fa41890-a548-4cb9-99a3-5c661ef65b90",
      "referenceNo": "TKT-20261031-1A2B3C4D",
      "category": "BILLING",
      "priority": "HIGH",
      "subject": "Lỗi thanh toán ca làm việc ngày 30/10",
      "status": "OPEN",
      "userId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
      "assignedTo": null,
      "slaDeadline": "2026-10-31T18:52:00Z",
      "lastMessageAt": "2026-10-31T14:52:00Z",
      "slaAtRisk": true,
      "createdAt": "2026-10-31T14:52:00Z"
    }
  ]
}
```

---

### 3.2. Hàng đợi riêng của chuyên viên (SCR-25: Phân tầng CC)
* **Endpoint:** `GET /api/v1/admin/support/tickets/queue/mine`
* **Quyền:** `CUSTOMER_CARE` hoặc `ADMIN`
* **Mô tả:** Chỉ trả về các ticket đang mở (`OPEN` hoặc `IN_PROGRESS`) đã được phân công cho chính tài khoản đang đăng nhập (`assignedTo = currentUserId`).

---

### 3.3. Danh sách nhân sự Customer Care (SCR-25 / SCR-26)
* **Endpoint:** `GET /api/v1/admin/support/tickets/customer-care-staff`
* **Quyền:** `CUSTOMER_CARE` hoặc `ADMIN`
* **Mô tả:** Trả về danh sách chuyên viên Customer Care kèm số lượng phiếu đang xử lý (`assignedCount`) để hỗ trợ chuyển giao và cân bằng tải công việc.
* **Response (200 OK):**
```json
{
  "success": true,
  "message": "Lấy danh sách nhân sự Chăm sóc khách hàng thành công",
  "data": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "fullName": "Nguyễn Văn A",
      "email": "cskh1@cani.vn",
      "assignedCount": 3
    }
  ]
}
```

---

### 3.4. Phân công hoặc nhận ticket (SCR-25 / SCR-26)
* **Endpoint:** `POST /api/v1/admin/support/tickets/{id}/assign`
* **Quyền:** `CUSTOMER_CARE` hoặc `ADMIN`
* **Request Body (Tùy chọn):**
```json
{
  "targetCcUserId": "550e8400-e29b-41d4-a716-446655440000"
}
```
> *Ghi chú:* Nếu body rỗng hoặc `targetCcUserId = null`, hệ thống sẽ tự động gán cho chính nhân viên CC đang đăng nhập. Trạng thái tự động chuyển `OPEN` $\rightarrow$ `IN_PROGRESS`.

---

### 3.3. Customer Care phản hồi hoặc tạo ghi chú nội bộ (SCR-26)
* **Endpoint:** `POST /api/v1/admin/support/tickets/{id}/reply`
* **Quyền:** `CUSTOMER_CARE` hoặc `ADMIN`
* **Request Body:**
```json
{
  "body": "Đã kiểm tra hệ thống đối soát, chuyển khoản đang chờ ngân hàng phê duyệt.",
  "isInternal": true
}
```
> *Ghi chú:* 
> - Nếu `isInternal = true`: Chỉ lưu vào thread nội bộ cho CC/Admin xem, **không gửi thông báo cho người dùng**.
> - Nếu `isInternal = false` (hoặc null): Phản hồi công khai cho người dùng và gửi thông báo cho người nộp ticket.

---

### 3.4. Đóng ticket kèm lý do (SCR-26)
* **Endpoint:** `POST /api/v1/admin/support/tickets/{id}/close`
* **Quyền:** `CUSTOMER_CARE` hoặc `ADMIN`
* **Request Body:**
```json
{
  "rationale": "Đã kiểm tra giao dịch, hệ thống đã bù tiền vào ví thành công cho người dùng."
}
```
| Trường | Kiểu | Bắt buộc | Ràng buộc |
| :--- | :--- | :--- | :--- |
| `rationale` | String | Có | **Tối thiểu 30 ký tự, tối đa 1000 ký tự** |

* **Hành vi:**
  - Chuyển `status = CLOSED`, cập nhật `resolvedBy = actorId`, `closedAt = now()`.
  - Tự động tạo tin nhắn kết luận trong thread.
  - Gửi thông báo đến người nộp ticket kèm nội dung rationale.
  - Ghi nhận `AuditLog` với action `CLOSE_TICKET`.

---

## 4. Bảng Mã Lỗi Nghiệp Vụ

| HTTP Status | Mã lỗi / Message | Nguyên nhân |
| :--- | :--- | :--- |
| `400 Bad Request` | Mô tả yêu cầu hỗ trợ phải từ 30 đến 3000 ký tự (MSG151) | Mô tả ticket ngắn hơn 30 ký tự khi tạo mới |
| `400 Bad Request` | Bạn không có quyền xem ticket này (MSG34) | Người dùng cố tình truy cập ticket của người khác (chống IDOR) |
| `400 Bad Request` | Ticket đã đóng, không thể phản hồi | Thử reply vào ticket đã có trạng thái `CLOSED` |
| `400 Bad Request` | Người dùng thường không được tạo ghi chú nội bộ | User gửi `isInternal = true` |
| `400 Bad Request` | Người nhận phân công phải có vai trò Chăm sóc khách hàng hoặc Quản trị viên | Gán ticket cho user role khác CC/ADMIN |
| `400 Bad Request` | Lý do đóng ticket phải từ 30 đến 1000 ký tự | Rationale khi CC đóng ticket ngắn hơn 30 ký tự |
| `404 Not Found` | Ticket hỗ trợ không tìm thấy với id: ... | ID ticket không tồn tại trong CSDL |
| `403 Forbidden` | Access Denied | User không có quyền truy cập endpoint `/api/v1/admin/support/**` |

