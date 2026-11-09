# CANi API Documentation — Cấu Hình Tham Số Hệ Thống (UC-114)

> **Mục tiêu:** Tài liệu bàn giao kỹ thuật từ Backend sang đội ngũ Frontend (Web Admin) và kiểm thử Postman cho chức năng Quản trị Cấu hình Tham số Hệ thống (UC-114: Configure System Parameters, SDS SD-30).  
> **Người phụ trách Backend:** QuyTH  
> **Phiên bản:** 1.0.0 (Spring Boot 3.5.1 / Java 21)  
> **Base URL:** `/api/v1/admin/configs`  
> **Quyền hạn bắt buộc (RBAC):** Bearer JWT Token của người dùng có Role `ADMIN`.

---

## 1. Tổng Quan Nghiệp Vụ (UC-114 / SD-30)

Chức năng **Cấu hình Tham số Toàn sàn** cho phép Quản trị viên (`ADMIN`) tra cứu và điều chỉnh linh hoạt các thông số vận hành của nền tảng CANi theo thời gian thực mà **không cần dừng hệ thống hay triển khai lại ứng dụng (zero-downtime reconfiguration)**:
1. **Truy vấn danh mục cấu hình:** Lấy toàn bộ tham số hệ thống hiện hành hoặc tra cứu chi tiết giá trị của từng tham số theo `key`.
2. **Cập nhật đơn lẻ:** Cập nhật giá trị và mô tả giải thích cho 1 tham số cấu hình cụ thể.
3. **Cập nhật hàng loạt (Bulk Update):** Cho phép Admin tinh chỉnh đồng thời nhiều tham số (VD: cập nhật tỷ lệ hoa hồng, ngưỡng thời gian phạt No-show, hạn chót đánh giá sao) trong cùng một lượt lưu, đảm bảo tính toàn vẹn (Atomic Transaction).
4. **Kiểm toán độc lập (Audit Logging):** Mọi hành động cập nhật cấu hình đều tự động ghi lại lịch sử vào bảng `audit_logs` với `action = "UPDATE_SYSTEM_CONFIG"`, lưu chi tiết JSON `old_values` và `new_values` cùng thông tin Admin thực hiện, độc lập với transaction chính (`Propagation.REQUIRES_NEW`).

---

## 2. Chi Tiết Danh Sách API

### 2.1. Lấy Danh Sách Toàn Bộ Cấu Hình Hệ Thống

* **Endpoint:** `GET /api/v1/admin/configs`
* **Method:** `GET`
* **Bảo mật:** `@PreAuthorize("hasRole('ADMIN')")`

#### Request Headers:
| Tên Header | Bắt buộc | Giá trị mẫu | Mô tả |
| :--- | :--- | :--- | :--- |
| `Authorization` | Có | `Bearer <admin_jwt_token>` | Token xác thực của Admin |

#### Ví dụ Request (cURL):
```bash
curl -X GET "http://localhost:8080/api/v1/admin/configs" \
  -H "Authorization: Bearer <admin_jwt_token>" \
  -H "Accept: application/json"
```

#### Cấu Trúc Response (200 OK):
```json
{
  "success": true,
  "message": "Lấy danh sách cấu hình hệ thống thành công",
  "data": [
    {
      "id": "7b8449c2-b529-4e5a-a38f-a9cb4e738010",
      "key": "app_version",
      "value": "1.0.0",
      "description": "Phiên bản hiện tại của ứng dụng",
      "updatedAt": "2026-10-25T14:10:00Z",
      "updatedBy": null
    },
    {
      "id": "8f121d5a-641e-450f-a982-f55cf55d1123",
      "key": "commission_rate",
      "value": "0.00",
      "description": "Tỷ lệ hoa hồng nền tảng (0% cho giai đoạn MVP)",
      "updatedAt": "2026-10-25T14:10:00Z",
      "updatedBy": null
    },
    {
      "id": "9a33bc88-9d22-482f-870d-ce019fa281b4",
      "key": "min_registration_age",
      "value": "18",
      "description": "Độ tuổi tối thiểu đăng ký làm nhân sự sự kiện",
      "updatedAt": "2026-10-25T14:10:00Z",
      "updatedBy": null
    },
    {
      "id": "6c4491de-8571-469b-8e2b-42fa6059d991",
      "key": "support_email",
      "value": "hotro@cani.vn",
      "description": "Email tiếp nhận khiếu nại và hỗ trợ",
      "updatedAt": "2026-10-25T14:10:00Z",
      "updatedBy": null
    }
  ],
  "timestamp": "2026-11-04T10:45:00.123"
}
```

---

### 2.2. Lấy Chi Tiết Một Tham Số Cấu Hình Theo Key

* **Endpoint:** `GET /api/v1/admin/configs/{key}`
* **Method:** `GET`
* **Bảo mật:** `@PreAuthorize("hasRole('ADMIN')")`
* **Path Parameters:**
  | Tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
  | :--- | :--- | :--- | :--- |
  | `key` | String | Có | Khóa định danh cấu hình (VD: `commission_rate`) |

#### Ví dụ Request (cURL):
```bash
curl -X GET "http://localhost:8080/api/v1/admin/configs/commission_rate" \
  -H "Authorization: Bearer <admin_jwt_token>" \
  -H "Accept: application/json"
```

#### Cấu Trúc Response (200 OK):
```json
{
  "success": true,
  "message": "Lấy thông tin chi tiết cấu hình thành công",
  "data": {
    "id": "8f121d5a-641e-450f-a982-f55cf55d1123",
    "key": "commission_rate",
    "value": "0.00",
    "description": "Tỷ lệ hoa hồng nền tảng (0% cho giai đoạn MVP)",
    "updatedAt": "2026-10-25T14:10:00Z",
    "updatedBy": null
  },
  "timestamp": "2026-11-04T10:45:00.123"
}
```

#### Response Lỗi Khi Key Không Tồn Tại (404 Not Found):
```json
{
  "success": false,
  "message": "Cấu hình hệ thống không tồn tại với key: 'invalid_key'",
  "data": null,
  "timestamp": "2026-11-04T10:45:00.123"
}
```

---

### 2.3. Cập Nhật Giá Trị Một Tham Số Cấu Hình Đơn Lẻ

* **Endpoint:** `PUT /api/v1/admin/configs/{key}`
* **Method:** `PUT`
* **Bảo mật:** `@PreAuthorize("hasRole('ADMIN')")`
* **Path Parameters:**
  | Tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
  | :--- | :--- | :--- | :--- |
  | `key` | String | Có | Khóa định danh cấu hình cần cập nhật |

#### Request Body Schema (`UpdateSystemConfigRequestDto`):
| Trường dữ liệu | Kiểu | Bắt buộc | Ràng buộc validation | Mô tả |
| :--- | :--- | :--- | :--- | :--- |
| `value` | String | **Có** | `@NotBlank` | Giá trị cấu hình mới |
| `description` | String | Không | Tối đa 1000 ký tự | Mô tả/ghi chú mới (nếu muốn thay đổi) |

#### Ví dụ Request (cURL):
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/configs/commission_rate" \
  -H "Authorization: Bearer <admin_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "value": "0.05",
    "description": "Tỷ lệ hoa hồng nền tảng (5% áp dụng từ Q4/2026)"
  }'
```

#### Cấu Trúc Response (200 OK):
```json
{
  "success": true,
  "message": "Cập nhật cấu hình hệ thống thành công",
  "data": {
    "id": "8f121d5a-641e-450f-a982-f55cf55d1123",
    "key": "commission_rate",
    "value": "0.05",
    "description": "Tỷ lệ hoa hồng nền tảng (5% áp dụng từ Q4/2026)",
    "updatedAt": "2026-11-04T10:46:00Z",
    "updatedBy": "e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a"
  },
  "timestamp": "2026-11-04T10:46:00.123"
}
```

#### Response Lỗi Khi `value` Bị Trống (400 Bad Request):
```json
{
  "success": false,
  "message": "Dữ liệu đầu vào không hợp lệ",
  "data": {
    "value": "Giá trị cấu hình không được để trống"
  },
  "timestamp": "2026-11-04T10:46:00.123"
}
```

---

### 2.4. Cập Nhật Hàng Loạt Nhiều Tham Số Cấu Hình (Bulk Update)

* **Endpoint:** `PUT /api/v1/admin/configs`
* **Method:** `PUT`
* **Bảo mật:** `@PreAuthorize("hasRole('ADMIN')")`

API hỗ trợ **cả 2 định dạng dữ liệu linh hoạt** để Frontend tùy chọn:
1. **Dạng Key-Value Map:** Tiện lợi khi cập nhật nhanh giá trị.
2. **Dạng List Items:** Hỗ trợ cập nhật đồng thời cả `value` lẫn `description`.

#### Cách 1: Request Body Dạng Key-Value Map:
```json
{
  "configs": {
    "commission_rate": "0.05",
    "min_registration_age": "18",
    "support_email": "cskh@cani.vn"
  }
}
```

#### Cách 2: Request Body Dạng Danh Sách Chi Tiết:
```json
{
  "items": [
    {
      "key": "commission_rate",
      "value": "0.05",
      "description": "Hoa hồng 5% giai đoạn tăng trưởng"
    },
    {
      "key": "min_registration_age",
      "value": "18",
      "description": "Độ tuổi tối thiểu đăng ký làm nhân sự sự kiện"
    }
  ]
}
```

#### Ví dụ Request (cURL):
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/configs" \
  -H "Authorization: Bearer <admin_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "configs": {
      "commission_rate": "0.05",
      "support_email": "cskh@cani.vn"
    }
  }'
```

#### Cấu Trúc Response (200 OK):
```json
{
  "success": true,
  "message": "Cập nhật hàng loạt cấu hình hệ thống thành công",
  "data": [
    {
      "id": "8f121d5a-641e-450f-a982-f55cf55d1123",
      "key": "commission_rate",
      "value": "0.05",
      "description": "Tỷ lệ hoa hồng nền tảng",
      "updatedAt": "2026-11-04T10:48:00Z",
      "updatedBy": "e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a"
    },
    {
      "id": "6c4491de-8571-469b-8e2b-42fa6059d991",
      "key": "support_email",
      "value": "cskh@cani.vn",
      "description": "Email tiếp nhận khiếu nại và hỗ trợ",
      "updatedAt": "2026-11-04T10:48:00Z",
      "updatedBy": "e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a"
    }
  ],
  "timestamp": "2026-11-04T10:48:00.123"
}
```

---

## 3. Đặc Tả Kiểm Toán Hệ Thống (Audit Log Specification)

Mỗi lần có thao tác cập nhật cấu hình hệ thống, Backend tự động kích hoạt `AuditLogService.logAction(AuditLog)` để ghi nhận lịch sử vào bảng `audit_logs`:

| Trường trong `audit_logs` | Kiểu dữ liệu | Giá trị ghi nhận | Ý nghĩa |
| :--- | :--- | :--- | :--- |
| `user_id` | UUID | `e4a2c58e-...` | UUID của Admin thực hiện thao tác |
| `action` | VARCHAR(100) | `"UPDATE_SYSTEM_CONFIG"` | Mã định danh hành động |
| `entity_type` | VARCHAR(100) | `"SYSTEM_CONFIG"` | Đối tượng chịu tác động |
| `entity_id` | UUID | UUID của bản ghi cấu hình | ID bản ghi trong bảng `system_config` |
| `old_values` | JSONB | `{"key": "commission_rate", "value": "0.00", ...}` | Giá trị cấu hình trước khi thay đổi |
| `new_values` | JSONB | `{"key": "commission_rate", "value": "0.05", ...}` | Giá trị cấu hình sau khi cập nhật |
| `created_at` | TIMESTAMPTZ | Thời điểm ghi log | Thời gian giao dịch |

---

## 4. Danh Mục Tham Số Cấu Hình Hệ Thống Mặc Định

Dưới đây là bảng tổng hợp các tham số cấu hình chính đang được quản lý trên sàn CANi:

| STT | Khóa định danh (`key`) | Giá trị mặc định | Đơn vị / Kiểu | Mô tả nghiệp vụ |
| :---: | :--- | :--- | :--- | :--- |
| 1 | `commission_rate` | `0.00` | Số thập phân (`0.05` = 5%) | Tỷ lệ phần trăm hoa hồng nền tảng thu trên tổng giá trị ca làm. |
| 2 | `app_version` | `1.0.0` | Semver string | Phiên bản ứng dụng Client tối thiểu được phép kết nối. |
| 3 | `support_email` | `hotro@cani.vn` | Email RFC 5322 | Email nhận ticket/khiếu nại chính thức của Customer Care. |
| 4 | `min_registration_age`| `18` | Số nguyên (năm) | Độ tuổi tối thiểu theo luật lao động để được tạo tài khoản Worker. |
| 5 | `noshow_penalty_hours`| `2` | Số nguyên (giờ) | Khoảng thời gian trước ca làm nếu hủy ca sẽ bị tính lỗi No-show. |
| 6 | `rating_deadline_days`| `7` | Số nguyên (ngày) | Hạn chót để Nhà tổ chức và Nhân sự hoàn tất đánh giá/chấm điểm sao sau khi kết thúc sự kiện. |
| 7 | `direct_payment_hold_hours` | `24` | Số nguyên (giờ) | Thời gian hệ thống giữ tiền tạm giữ trước khi giải ngân cho Worker nếu không có tranh chấp. |

---

## 5. Hướng Dẫn Tích Hợp Frontend (Web Admin)

1. **Hiển thị Form Quản trị Cấu hình:**
   - Sử dụng API `GET /api/v1/admin/configs` khi khởi tạo trang Admin System Settings để load toàn bộ danh sách tham số.
   - Nhóm các tham số theo tiền tố hoặc danh mục (VD: Tài chính, Vận hành ca làm, Tài khoản người dùng).
2. **Cập nhật từng dòng (Inline Edit):**
   - Sử dụng `PUT /api/v1/admin/configs/{key}` khi người dùng bấm nút lưu trên từng hàng tham số.
3. **Cập nhật đồng loạt (Batch Save):**
   - Khi Admin thay đổi nhiều ô input và nhấn nút **"Lưu thay đổi tất cả"**, gửi payload dạng Map đến `PUT /api/v1/admin/configs`.
4. **Xử lý lỗi phân quyền:**
   - Nếu nhận HTTP `403 Forbidden` (`message: "Bạn không có quyền truy cập tài nguyên này"`), điều hướng người dùng hoặc hiển thị thông báo lỗi yêu cầu tài khoản có quyền `ADMIN`.

