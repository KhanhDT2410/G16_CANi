# CANi API Documentation — Quản Lý Vi Phạm Vắng Mặt No-Show (UC-88)

> **Mục tiêu:** Tài liệu bàn giao kỹ thuật API phục vụ chức năng Quản lý vi phạm vắng mặt không báo trước (No-show - UC-88 · SRS FE-09 Attendance & Payment).  
> **Người phụ trách:** QuyTH  
> **Phiên bản:** 1.0.0 (Spring Boot 3.5.1 / Java 21)  
> **Base URL:** `/api/v1/admin/no-shows`  
> **Phân quyền RBAC:** `ADMIN`, `CUSTOMER_CARE`  

---

## 1. Tổng Quan Nghiệp Vụ (UC-88)

### 1.1. Quy tắc phát hiện & Chế tài No-show (BR-54 & BR-70)
1. **Thời hạn điểm danh (30-minute Window):** Sau 30 phút tính từ thời điểm bắt đầu ca làm việc (`shift.startTime + 30m`), nếu Worker chưa quét mã QR điểm danh (trạng thái điểm danh vẫn là `NOT_CHECKED_IN` hoặc chưa có bản ghi `checkin_time`), ca trực sẽ bị đánh dấu vi phạm `NO_SHOW`.
2. **Khấu trừ điểm uy tín (RS Penalty):**
   - Trừ ngay lập tức **25 điểm RS** của Worker (`worker.reliability_score = max(0, score - 25)`).
   - Tự động ghi nhận một bản ghi phạt trong bảng `reliability_logs` với `change_amount = -25`, `reference_type = "NO_SHOW"`, `reference_id = shiftAssignment.id`.
3. **Tự động khóa tài khoản (Account Lockout - UC-95):**
   - Nếu điểm RS của Worker sau khi trừ giảm xuống dưới ngưỡng **20 điểm**, hệ thống tự động cập nhật trạng thái tài khoản `user.status = LOCKED`.
4. **Điều phối khẩn cấp (Emergency Dispatch - UC-59):**
   - Vị trí thiếu hụt trong ca trực được mở lại (`shift.remaining_workers = remaining_workers + 1`).
   - Hệ thống tự động kích hoạt thuật toán Matching & Dispatch mở rộng để gửi đề xuất ngay lập tức cho các Worker tiềm năng kế tiếp.
5. **Thông báo đa kênh (Notification):**
   - Gửi cảnh báo đến Worker thông báo mức phạt 25 RS và thời hạn 7 ngày để nộp đơn kháng cáo (UC-94).
   - Gửi thông báo đến Nhà tổ chức sự kiện (EO) về tình trạng nhân sự vắng mặt và tiến độ điều phối người thay thế.
6. **Cơ chế kích hoạt:**
   - **Tự động (Background Cronjob):** Chạy định kỳ mỗi 10 phút quét toàn bộ các ca vi phạm quá hạn 30 phút.
   - **Thủ công (Manual Mark / Scan Now):** Admin và Customer Care có thể xác nhận No-show ngay lập tức cho 1 ca hoặc kích hoạt quét toàn hệ thống phục vụ vận hành và kiểm thử.

---

## 2. Chi Tiết Danh Sách API Endpoints

### 2.1. Lấy danh sách vi phạm No-show (Phân trang & Bộ lọc)
* **Endpoint:** `GET /api/v1/admin/no-shows`
* **Quyền:** `ADMIN`, `CUSTOMER_CARE`
* **Query Parameters:**
  - `keyword` (String, optional): Tìm kiếm theo tên Worker, email, tên sự kiện, vị trí công việc.
  - `workerId` (UUID, optional): Lọc theo ID nhân sự.
  - `eventId` (UUID, optional): Lọc theo ID sự kiện.
  - `fromDate` (ISO-8601, optional): Từ ngày ghi nhận.
  - `toDate` (ISO-8601, optional): Đến ngày ghi nhận.
  - `page` (Integer, default: 0): Số trang.
  - `size` (Integer, default: 20): Số bản ghi mỗi trang.

* **Response Success (200 OK):**
```json
{
  "success": true,
  "message": "Lấy danh sách vi phạm No-show thành công",
  "data": {
    "content": [
      {
        "id": "c1a2b3c4-d5e6-7890-abcd-ef1234567890",
        "assignmentId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "workerId": "b1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "workerUserId": "u1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "workerName": "Nguyễn Văn A",
        "workerEmail": "worker@cani.vn",
        "workerPhone": "0987654321",
        "workerAvatar": null,
        "shiftId": "s1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "shiftRole": "Nhân viên an ninh",
        "eventId": "e1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "eventName": "Lễ Hội Âm Nhạc CANi",
        "shiftStartTime": "2026-11-08T08:00:00Z",
        "shiftEndTime": "2026-11-08T16:00:00Z",
        "detectedBy": "SYSTEM_CRON",
        "detectedAt": "2026-11-08T08:31:00Z",
        "penaltyAmount": 25,
        "reliabilityScoreBefore": 85,
        "reliabilityScoreAfter": 60,
        "isAccountLocked": false,
        "emergencyDispatched": true,
        "notes": "Hệ thống tự động phát hiện vắng mặt sau 30 phút bắt đầu ca",
        "appealId": "f1a2b3c4-d5e6-7890-abcd-ef1234567890",
        "appealStatus": "SUBMITTED",
        "createdAt": "2026-11-08T08:31:00Z"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

---

### 2.2. Lấy thống kê tổng quan No-show
* **Endpoint:** `GET /api/v1/admin/no-shows/stats`
* **Quyền:** `ADMIN`, `CUSTOMER_CARE`
* **Response Success (200 OK):**
```json
{
  "success": true,
  "message": "Lấy thống kê No-show thành công",
  "data": {
    "totalNoShows": 48,
    "noShowsToday": 3,
    "noShowsThisMonth": 22,
    "totalRsDeducted": 1200,
    "totalAccountsLocked": 5
  }
}
```

---

### 2.3. Xem chi tiết hồ sơ vi phạm No-show
* **Endpoint:** `GET /api/v1/admin/no-shows/{id}`
* **Quyền:** `ADMIN`, `CUSTOMER_CARE`
* **Response Success (200 OK):**
```json
{
  "success": true,
  "message": "Lấy chi tiết vi phạm No-show thành công",
  "data": {
    "id": "c1a2b3c4-d5e6-7890-abcd-ef1234567890",
    "assignmentId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "workerName": "Nguyễn Văn A",
    "workerEmail": "worker@cani.vn",
    "eventName": "Lễ Hội Âm Nhạc CANi",
    "shiftRole": "Nhân viên an ninh",
    "shiftStartTime": "2026-11-08T08:00:00Z",
    "detectedBy": "SYSTEM_CRON",
    "detectedAt": "2026-11-08T08:31:00Z",
    "penaltyAmount": 25,
    "reliabilityScoreBefore": 85,
    "reliabilityScoreAfter": 60,
    "isAccountLocked": false,
    "emergencyDispatched": true,
    "notes": "Hệ thống tự động phát hiện vắng mặt sau 30 phút bắt đầu ca",
    "appealId": null,
    "appealStatus": null
  }
}
```

---

### 2.4. Đánh dấu No-show thủ công cho ca làm việc
* **Endpoint:** `POST /api/v1/admin/no-shows/{assignmentId}/mark`
* **Quyền:** `ADMIN`, `CUSTOMER_CARE`
* **Request Body:**
```json
{
  "notes": "Nhân sự không có mặt tại địa điểm sự kiện lúc 08:35, EO đã xác nhận vắng mặt"
}
```
* **Response Success (200 OK):**
```json
{
  "success": true,
  "message": "Xác nhận nhân sự vắng mặt (No-show) thành công",
  "data": {
    "id": "c1a2b3c4-d5e6-7890-abcd-ef1234567890",
    "assignmentId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "workerName": "Nguyễn Văn A",
    "penaltyAmount": 25,
    "reliabilityScoreBefore": 85,
    "reliabilityScoreAfter": 60,
    "isAccountLocked": false,
    "emergencyDispatched": true
  }
}
```

---

### 2.5. Kích hoạt quét No-show toàn hệ thống tức thì
* **Endpoint:** `POST /api/v1/admin/no-shows/scan`
* **Quyền:** `ADMIN`, `CUSTOMER_CARE`
* **Response Success (200 OK):**
```json
{
  "success": true,
  "message": "Đã quét 4 ca tiềm năng, phát hiện và xử lý 2 ca No-show.",
  "data": {
    "totalCandidatesScanned": 4,
    "totalMarkedNoShow": 2,
    "totalEmergencyDispatched": 2,
    "totalAccountsLocked": 1,
    "scannedAt": "2026-11-08T16:30:00Z",
    "message": "Đã quét 4 ca tiềm năng, phát hiện và xử lý 2 ca No-show."
  }
}
```

---

## 3. Bảng Mã Lỗi (Error Codes)

| HTTP Status | Message Tiếng Việt | Ý Nghĩa / Nguyên Nhân |
|---|---|---|
| `400 Bad Request` | `Ca làm việc này đã được ghi nhận No-show trước đó` | Ca trực đã bị đánh dấu No-show từ trước, không xử lý trùng lặp. |
| `400 Bad Request` | `Không thể đánh dấu No-show cho ca làm việc đã hoàn thành hoặc đã bị hủy` | Trạng thái `COMPLETED` hoặc `CANCELLED` không hợp lệ để phạt No-show. |
| `400 Bad Request` | `Nhân sự đã thực hiện quét mã QR điểm danh, không thể đánh dấu No-show` | Đã có log điểm danh `checkin_time != null`. |
| `403 Forbidden` | `Access Denied` | Worker hoặc EO cố tình gọi API của Admin/Customer Care. |
| `404 Not Found` | `Không tìm thấy Phân công ca làm việc với id ...` | `assignmentId` không tồn tại trong hệ thống. |

