# CANi API Documentation — Bảng Điều Khiển & Số Liệu Vận Hành Admin (UC-86)

> **Mục tiêu:** Tài liệu bàn giao kỹ thuật từ Backend sang đội ngũ Frontend (Web Admin) và kiểm thử Postman cho chức năng Bảng điều khiển & Phân tích số liệu vận hành (UC-86: Admin Dashboard & Metrics Analytics).  
> **Người phụ trách Backend:** QuyTH  
> **Phiên bản:** 1.0.0 (Spring Boot 3.5.1 / Java 21)  
> **Base URL:** `/api/v1/admin/dashboard`  
> **Quyền hạn bắt buộc (RBAC):** Bắt buộc Bearer JWT Token của người dùng có Role `ADMIN`.

---

## 1. Tổng Quan Nghiệp Vụ (UC-86)

Bảng điều khiển quản trị (Admin Dashboard) là trung tâm giám sát vận hành toàn bộ nền tảng CANi theo thời gian thực:
1. **Tổng hợp số liệu vận hành (Operational Metrics):**
   - **Thống kê người dùng (User Analytics):** Tổng số tài khoản, phân bổ theo vai trò (`WORKER`, `EVENT_ORGANIZER`, `CUSTOMER_CARE`, `ADMIN`), số tài khoản hiện đang bị khóa (`LOCKED`).
   - **Hàng đợi thẩm định chờ xử lý (Pending Verifications):** Số hồ sơ eKYC Worker chờ duyệt, số eKYC Doanh nghiệp / Nhà tổ chức sự kiện chờ duyệt, số chứng chỉ kỹ năng Worker chờ thẩm định (`PENDING_REVIEW`).
   - **Vận hành Sự kiện & Ca làm việc (Operations):** Tổng số sự kiện, số sự kiện đang hoạt động (`ACTIVE`), tổng số ca làm việc (`SHIFTS`), số ca đang mở tuyển (`ACTIVE`).
2. **Nhật ký hoạt động quản trị gần nhất (Recent Activities):**
   - Lấy 10 hoạt động quản trị viên mới nhất từ bảng `audit_logs` (thao tác khóa/mở khóa tài khoản người dùng, thẩm định chứng chỉ kỹ năng, phê duyệt hồ sơ eKYC...).
   - Tự động kết hợp (batch lookup) thông tin định danh của Admin thực hiện (họ tên, email, ảnh đại diện) để hiển thị trực quan trên giao diện Admin.
3. **Tối ưu hóa hiệu năng:**
   - Toàn bộ thống kê sử dụng trực tiếp các câu truy vấn `COUNT(*)` tại tầng Database thông qua Spring Data JPA, tuyệt đối không tải Entity lên RAM.
   - Truy vấn 10 nhật ký kiểm toán mới nhất bằng `ORDER BY created_at DESC LIMIT 10` và gom nhóm ID để nạp thông tin Admin với 1 câu truy vấn đơn nhất (loại bỏ triệt để vấn đề N+1 query).

---

## 2. Chi Tiết Danh Sách API

### 2.1. Lấy Số Liệu Thống Kê Vận Hành Nền Tảng

* **Endpoint:** `GET /api/v1/admin/dashboard/metrics`
* **Method:** `GET`
* **Bảo mật:** `@PreAuthorize("hasRole('ADMIN')")`
* **Mô tả:** Trả về toàn bộ các nhóm chỉ số phục vụ việc vẽ biểu đồ, thẻ KPI (Card Metrics) trên trang chủ Dashboard.

#### Ví dụ Request:
```http
GET /api/v1/admin/dashboard/metrics HTTP/1.1
Host: localhost:8080
Authorization: Bearer <admin_jwt_token>
```

#### Cấu Trúc Response (200 OK):
```json
{
  "success": true,
  "message": "Lấy số liệu vận hành dashboard thành công",
  "data": {
    "users": {
      "totalUsers": 215,
      "workerCount": 168,
      "eventOrganizerCount": 38,
      "customerCareCount": 6,
      "adminCount": 3,
      "lockedUsersCount": 5
    },
    "verifications": {
      "pendingWorkerEkycCount": 12,
      "pendingEoEkycCount": 4,
      "pendingSkillVerificationsCount": 15,
      "totalPendingCount": 31
    },
    "operations": {
      "totalEventsCount": 42,
      "activeEventsCount": 18,
      "totalShiftsCount": 120,
      "activeShiftsCount": 45
    }
  },
  "timestamp": "2026-10-26T22:15:00.123"
}
```

#### Chi Tiết Ý Nghĩa Các Trường Dữ Liệu:
| Đối tượng | Trường | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- | :--- |
| **users** | `totalUsers` | Long | Tổng số lượng người dùng đã đăng ký trên hệ thống |
| | `workerCount` | Long | Số người dùng là Worker (Người lao động tự do) |
| | `eventOrganizerCount` | Long | Số người dùng là Nhà tổ chức sự kiện (Doanh nghiệp/Cá nhân) |
| | `customerCareCount` | Long | Số nhân sự Chăm sóc khách hàng |
| | `adminCount` | Long | Số quản trị viên hệ thống |
| | `lockedUsersCount` | Long | Số tài khoản hiện đang bị khóa tạm thời hoặc vĩnh viễn |
| **verifications** | `pendingWorkerEkycCount` | Long | Số hồ sơ xác minh danh tính (eKYC) của Worker đang chờ duyệt |
| | `pendingEoEkycCount` | Long | Số hồ sơ eKYC Doanh nghiệp / Nhà tổ chức sự kiện đang chờ duyệt |
| | `pendingSkillVerificationsCount` | Long | Số yêu cầu thẩm định chứng chỉ kỹ năng Worker đang chờ duyệt |
| | `totalPendingCount` | Long | Tổng các hồ sơ / yêu cầu đang tồn đọng chờ thẩm định |
| **operations** | `totalEventsCount` | Long | Tổng số sự kiện được tạo trên nền tảng |
| | `activeEventsCount` | Long | Số sự kiện đang ở trạng thái hoạt động (`ACTIVE`) |
| | `totalShiftsCount` | Long | Tổng số ca làm việc được tạo trong mọi sự kiện |
| | `activeShiftsCount` | Long | Số ca làm việc đang mở đăng ký / tuyển dụng (`ACTIVE`) |

---

### 2.2. Lấy Danh Sách Hoạt Động Quản Trị Gần Nhất (Recent Activities)

* **Endpoint:** `GET /api/v1/admin/dashboard/recent-activities`
* **Method:** `GET`
* **Bảo mật:** `@PreAuthorize("hasRole('ADMIN')")`
* **Mô tả:** Lấy danh sách tối đa 10 hành động can thiệp quản trị gần đây nhất từ bảng `audit_logs` (khóa tài khoản, duyệt kỹ năng, duyệt eKYC, cấu hình hệ thống...).

#### Ví dụ Request:
```http
GET /api/v1/admin/dashboard/recent-activities HTTP/1.1
Host: localhost:8080
Authorization: Bearer <admin_jwt_token>
```

#### Cấu Trúc Response (200 OK):
```json
{
  "success": true,
  "message": "Lấy danh sách hoạt động gần nhất thành công",
  "data": [
    {
      "id": "7f000001-8b7a-40a8-8f81-5a1d7f113a01",
      "userId": "e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a",
      "adminName": "Nguyễn Quản Trị",
      "adminEmail": "admin@cani.vn",
      "adminAvatarUrl": "https://storage.cani.vn/avatars/admin1.png",
      "action": "UPDATE_USER_STATUS",
      "actionDescription": "Cập nhật trạng thái người dùng",
      "entityType": "USER",
      "entityId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "oldValues": "{\"status\":\"ACTIVE\"}",
      "newValues": "{\"status\":\"LOCKED\",\"reason\":\"Vi phạm điều khoản hủy ca 3 lần\"}",
      "ipAddress": "192.168.1.10",
      "createdAt": "2026-10-26T21:45:10Z"
    },
    {
      "id": "7f000001-8b7a-40a8-8f81-5a1d7f113a02",
      "userId": "e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a",
      "adminName": "Nguyễn Quản Trị",
      "adminEmail": "admin@cani.vn",
      "adminAvatarUrl": "https://storage.cani.vn/avatars/admin1.png",
      "action": "APPROVE_SKILL_VERIFICATION",
      "actionDescription": "Thẩm định chứng chỉ kỹ năng",
      "entityType": "SKILL_VERIFICATION",
      "entityId": "d4e5f6a7-b8c9-0d1e-2f3a-4b5c6d7e8f9a",
      "oldValues": null,
      "newValues": "{\"status\":\"APPROVED\",\"spAwarded\":25}",
      "ipAddress": "192.168.1.10",
      "createdAt": "2026-10-26T20:30:00Z"
    },
    {
      "id": "7f000001-8b7a-40a8-8f81-5a1d7f113a03",
      "userId": null,
      "adminName": "Hệ thống",
      "adminEmail": null,
      "adminAvatarUrl": null,
      "action": "AUTO_APPROVE_WORKER_EKYC",
      "actionDescription": "Xử lý hồ sơ eKYC",
      "entityType": "WORKER_EKYC",
      "entityId": "c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f",
      "oldValues": null,
      "newValues": "{\"status\":\"APPROVED\",\"confidence\":98.5}",
      "ipAddress": "127.0.0.1",
      "createdAt": "2026-10-26T19:15:00Z"
    }
  ],
  "timestamp": "2026-10-26T22:15:00.123"
}
```

#### Chi Tiết Ý Nghĩa Các Trường:
| Trường | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| `id` | UUID | ID của bản ghi kiểm toán trong bảng `audit_logs` |
| `userId` | UUID | ID của Quản trị viên (Admin/Customer Care) thực hiện thao tác (null nếu là tác vụ ngầm của hệ thống) |
| `adminName` | String | Họ và tên của Admin (hoặc `"Hệ thống"` nếu là tiến trình nền tự động) |
| `adminEmail` | String | Email của người thực hiện |
| `adminAvatarUrl` | String | Đường dẫn ảnh đại diện của Admin |
| `action` | String | Mã định danh kỹ thuật của thao tác (VD: `UPDATE_USER_STATUS`, `APPROVE_SKILL_VERIFICATION`...) |
| `actionDescription` | String | Mô tả hành động thân thiện với người dùng (tiếng Việt) |
| `entityType` | String | Đối tượng bị tác động (`USER`, `SKILL_VERIFICATION`, `WORKER_EKYC`, `EO_EKYC`...) |
| `entityId` | UUID | Khóa chính của bản ghi bị tác động |
| `oldValues` | String (JSON) | Trạng thái / thuộc tính trước khi thay đổi |
| `newValues` | String (JSON) | Trạng thái / thuộc tính sau khi thay đổi (kèm lý do nếu có) |
| `ipAddress` | String | Địa chỉ IP của Admin khi gửi request |
| `createdAt` | Instant (ISO 8601) | Thời điểm hành động diễn ra |

---

## 3. Kiến Trúc Kỹ Thuật & Tối Ưu Hóa

### 3.1. Phân Tầng Kiến Trúc (Three-Layer Architecture)
```
AdminDashboardController (REST API, RBAC PreAuthorize)
          │
          ▼
AdminDashboardService (Tổng hợp số liệu, Batch lookup Admin info)
          │
          ├──> UserRepository (count(), countByRole(), countByStatus(), findAllById())
          ├──> WorkerEkycVerificationRepository (countByStatus("PENDING_REVIEW"))
          ├──> EoEkycVerificationRepository (countByStatus("PENDING_REVIEW"))
          ├──> SkillVerificationRequestRepository (countByStatus(PENDING_REVIEW))
          ├──> EventRepository (count(), countByStatus(ACTIVE))
          ├──> ShiftRepository (count(), countByStatus(ACTIVE))
          └──> AuditLogRepository (findTop10ByOrderByCreatedAtDesc())
```

### 3.2. Bảo Mật & Phân Quyền (RBAC)
- Toàn bộ Controller được bảo vệ bởi:
  ```java
  @PreAuthorize("hasRole('ADMIN')")
  ```
- Nếu Token không hợp lệ hoặc hết hạn: Trả về mã lỗi HTTP `401 Unauthorized`.
- Nếu Token thuộc về Worker hoặc Event Organizer: Trả về mã lỗi HTTP `403 Forbidden`.

---

## 4. Xử Lý Lỗi (Error Handling)

### 4.1. Không có quyền truy cập (403 Forbidden)
```json
{
  "success": false,
  "message": "Access Denied",
  "timestamp": "2026-10-26T22:15:00.123"
}
```

### 4.2. Chưa đăng nhập (401 Unauthorized)
```json
{
  "success": false,
  "message": "Full authentication is required to access this resource",
  "timestamp": "2026-10-26T22:15:00.123"
}
```
