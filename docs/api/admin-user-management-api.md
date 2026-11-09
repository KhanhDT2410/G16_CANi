# CANi API Documentation — Quản Trị Người Dùng (UC-87)

> **Mục tiêu:** Tài liệu bàn giao kỹ thuật từ Backend sang đội ngũ Frontend (Web Admin) và kiểm thử Postman cho chức năng Quản trị Người dùng (UC-87: User Management).  
> **Người phụ trách Backend:** QuyTH  
> **Phiên bản:** 1.0.0 (Spring Boot 3.5.1 / Java 21)  
> **Base URL:** `/api/v1/admin/users`  
> **Quyền hạn bắt buộc (RBAC):** Bắt buộc Bearer JWT Token của người dùng có Role `ADMIN`.

---

## 1. Tổng Quan Nghiệp Vụ (UC-87)

Hệ thống cho phép Quản trị viên (Admin) quản lý tập trung toàn bộ danh sách tài khoản người dùng trên nền tảng CANi:
1. **Truy vấn danh sách người dùng:** Hỗ trợ phân trang chuẩn (`Pageable`), tìm kiếm đa trường theo từ khóa (họ tên, email, số điện thoại) và bộ lọc theo vai trò (`UserRole`) cùng trạng thái hoạt động (`UserStatus`).
2. **Xem chi tiết tài khoản:** Trả về thông tin định danh người dùng kết hợp với dữ liệu hồ sơ mở rộng của Worker (eKYC, điểm uy tín, rating) hoặc Nhà tổ chức sự kiện (EO - Công ty, mã số thuế, kiểm duyệt).
3. **Cập nhật trạng thái người dùng (Khóa / Mở khóa):** Cho phép khóa (`LOCKED`) hoặc mở lại (`ACTIVE`) tài khoản người dùng kèm lý do bắt buộc. Hệ thống chặn Admin tự khóa tài khoản của chính mình và ghi nhận lịch sử vào bảng kiểm toán (`audit_logs`). Người dùng bị khóa sẽ bị thu hồi quyền truy cập API ngay lập tức (`UserAuthorityFilter`).

---

## 2. Chi Tiết Danh Sách API

### 2.1. Lấy Danh Sách Người Dùng (Có Phân Trang & Bộ Lọc)

* **Endpoint:** `GET /api/v1/admin/users`
* **Method:** `GET`
* **Bảo mật:** `@PreAuthorize("hasRole('ADMIN')")`
* **Query Parameters:**
  | Tham số | Kiểu dữ liệu | Bắt buộc | Mặc định | Mô tả |
  | :--- | :--- | :--- | :--- | :--- |
  | `search` | String | Không | null | Từ khóa tìm kiếm (họ tên, email hoặc số điện thoại) |
  | `role` | Enum | Không | null | Lọc theo vai trò: `ADMIN`, `CUSTOMER_CARE`, `EVENT_ORGANIZER`, `WORKER` |
  | `status` | Enum | Không | null | Lọc theo trạng thái: `ACTIVE`, `LOCKED`, `SUSPENDED`, `PENDING_VERIFICATION` |
  | `page` | Integer | Không | `0` | Số thứ tự trang (0-indexed) |
  | `size` | Integer | Không | `10` | Số lượng bản ghi trên một trang |
  | `sort` | String | Không | `createdAt,desc` | Trường và chiều sắp xếp (VD: `createdAt,desc`, `fullName,asc`) |

#### Ví dụ Request:
```http
GET /api/v1/admin/users?search=Nguyen&role=WORKER&status=ACTIVE&page=0&size=10&sort=createdAt,desc HTTP/1.1
Host: localhost:8080
Authorization: Bearer <admin_jwt_token>
```

#### Cấu Trúc Response (200 OK):
```json
{
  "success": true,
  "message": "Lấy danh sách người dùng thành công",
  "data": {
    "content": [
      {
        "id": "e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a",
        "email": "worker.nguyen@example.com",
        "phone": "0901234567",
        "fullName": "Nguyễn Văn Worker",
        "role": "WORKER",
        "status": "ACTIVE",
        "avatarUrl": "https://storage.cani.vn/avatars/u1.png",
        "emailVerified": true,
        "lastLoginAt": "2026-10-26T14:20:00Z",
        "createdAt": "2026-10-20T08:00:00Z",
        "updatedAt": "2026-10-26T14:20:00Z"
      }
    ],
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true,
    "empty": false
  },
  "timestamp": "2026-10-26T20:30:00.123"
}
```

---

### 2.2. Xem Chi Tiết Thông Tin Tài Khoản Người Dùng

* **Endpoint:** `GET /api/v1/admin/users/{id}`
* **Method:** `GET`
* **Path Variable:** `id` (UUID): ID của người dùng cần xem.
* **Bảo mật:** `@PreAuthorize("hasRole('ADMIN')")`

#### Ví dụ Request:
```http
GET /api/v1/admin/users/e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a HTTP/1.1
Host: localhost:8080
Authorization: Bearer <admin_jwt_token>
```

#### Cấu Trúc Response (200 OK) - Đối với Worker:
```json
{
  "success": true,
  "message": "Lấy thông tin chi tiết người dùng thành công",
  "data": {
    "id": "e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a",
    "email": "worker.nguyen@example.com",
    "phone": "0901234567",
    "fullName": "Nguyễn Văn Worker",
    "role": "WORKER",
    "status": "ACTIVE",
    "avatarUrl": "https://storage.cani.vn/avatars/u1.png",
    "emailVerified": true,
    "language": "vi",
    "fcmToken": "fcm_token_sample_abc123",
    "lastLoginAt": "2026-10-26T14:20:00Z",
    "createdAt": "2026-10-20T08:00:00Z",
    "updatedAt": "2026-10-26T14:20:00Z",
    "workerProfile": {
      "workerId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "fullName": "Nguyễn Văn Worker",
      "cccdNumber": "079201004567",
      "kycStatus": "APPROVED",
      "reliabilityScore": 98,
      "ratingAvg": 4.85,
      "ratingCount": 15,
      "totalShiftsCompleted": 20,
      "totalCancellations": 1,
      "totalNoshows": 0,
      "isOnline": true,
      "yearsExperience": 2
    },
    "eoProfile": null
  },
  "timestamp": "2026-10-26T20:30:00.123"
}
```

#### Cấu Trúc Response (200 OK) - Đối với Nhà Tổ Chức (EO):
```json
{
  "success": true,
  "message": "Lấy thông tin chi tiết người dùng thành công",
  "data": {
    "id": "f8b3d61a-8275-4cf9-b1e8-3a4c0d75922b",
    "email": "contact@eventpro.vn",
    "phone": "0912345678",
    "fullName": "Trần Thị Quản Lý",
    "role": "EVENT_ORGANIZER",
    "status": "ACTIVE",
    "avatarUrl": "https://storage.cani.vn/avatars/eo1.png",
    "emailVerified": true,
    "language": "vi",
    "fcmToken": null,
    "lastLoginAt": "2026-10-26T10:00:00Z",
    "createdAt": "2026-10-15T09:00:00Z",
    "updatedAt": "2026-10-26T10:00:00Z",
    "workerProfile": null,
    "eoProfile": {
      "eoId": "2c5a7e91-1123-4455-8899-aabbccddeeff",
      "companyName": "Công ty TNHH Tổ chức Sự kiện EventPro",
      "taxId": "0316789012",
      "address": "123 Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
      "verificationStatus": "APPROVED",
      "verifiedAt": "2026-10-16T14:30:00Z",
      "ratingAvg": 4.90,
      "ratingCount": 12,
      "totalEventsCompleted": 8
    }
  },
  "timestamp": "2026-10-26T20:30:00.123"
}
```

---

### 2.3. Cập Nhật Trạng Thái Tài Khoản (Khóa / Mở Khóa)

* **Endpoint:** `PATCH /api/v1/admin/users/{id}/status`
* **Method:** `PATCH`
* **Path Variable:** `id` (UUID): ID của người dùng cần cập nhật trạng thái.
* **Bảo mật:** `@PreAuthorize("hasRole('ADMIN')")`
* **Content-Type:** `application/json`

#### Request Body:
```json
{
  "status": "LOCKED",
  "reason": "Vi phạm quy chế cộng đồng CANi: Hủy ca đột xuất không lý do 3 lần liên tiếp."
}
```

| Trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- | :--- |
| `status` | Enum | Có | `@NotNull` | Trạng thái mới: `LOCKED`, `ACTIVE`, `SUSPENDED` |
| `reason` | String | Có | `@NotBlank`, max 500 chars | Lý do thay đổi trạng thái (bắt buộc cho việc kiểm toán) |

#### Phản Hồi Thành Công (200 OK):
```json
{
  "success": true,
  "message": "Khóa tài khoản người dùng thành công",
  "data": {
    "id": "e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a",
    "email": "worker.nguyen@example.com",
    "phone": "0901234567",
    "fullName": "Nguyễn Văn Worker",
    "role": "WORKER",
    "status": "LOCKED",
    "avatarUrl": "https://storage.cani.vn/avatars/u1.png",
    "emailVerified": true,
    "language": "vi",
    "fcmToken": "fcm_token_sample_abc123",
    "lastLoginAt": "2026-10-26T14:20:00Z",
    "createdAt": "2026-10-20T08:00:00Z",
    "updatedAt": "2026-10-26T20:45:00Z",
    "workerProfile": { ... },
    "eoProfile": null
  },
  "timestamp": "2026-10-26T20:45:00.456"
}
```

#### Xử Lý Lỗi Nghiệp Vụ:
* **400 Bad Request (Tự khóa chính mình):**
  ```json
  {
    "success": false,
    "message": "Quản trị viên không thể tự thay đổi trạng thái tài khoản của chính mình",
    "timestamp": "2026-10-26T20:45:00.456"
  }
  ```
* **400 Bad Request (Validation):**
  ```json
  {
    "success": false,
    "message": "Dữ liệu đầu vào không hợp lệ",
    "data": {
      "reason": "Lý do thay đổi trạng thái không được để trống"
    },
    "timestamp": "2026-10-26T20:45:00.456"
  }
  ```
* **404 Not Found:**
  ```json
  {
    "success": false,
    "message": "Người dùng không tồn tại với id: 'e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a'",
    "timestamp": "2026-10-26T20:45:00.456"
  }
  ```
