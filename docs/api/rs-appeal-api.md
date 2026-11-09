# CANi API Documentation — Kháng Cáo Điểm Uy Tín Worker (UC-94)

> **Mục tiêu:** Tài liệu bàn giao kỹ thuật API phục vụ chức năng Kháng cáo điểm phạt uy tín Worker (UC-94: RS Penalty & Appeal · SRS FE-10 Rating & Reputation).  
> **Người phụ trách Backend:** QuyTH  
> **Phiên bản:** 1.0.0 (Spring Boot 3.5.1 / Java 21)  
> **Base URL:**
> - Worker: `/api/v1/workers/appeals`
> - Admin / Customer Care: `/api/v1/admin/appeals`
> **Phân quyền RBAC:**
> - Nộp & xem đơn của mình: `WORKER` (Bảo vệ IDOR tuyệt đối qua SecurityUtils)
> - Hàng đợi & Thẩm định: `ADMIN`, `CUSTOMER_CARE`

---

## 1. Tổng Quan Nghiệp Vụ (UC-94)

### 1.1. Bối cảnh xử phạt điểm RS
Khi Worker vi phạm cam kết ca làm việc, hệ thống khấu trừ điểm uy tín RS (Reliability Score, thang 0–100):
- **Vắng mặt không báo trước (No-show):** −25 RS
- **Hủy ca muộn (Late cancel):** −20 RS
- **Hủy ca trước 24h (T-24h cancel):** −10 RS
- **Kết luận có lỗi trong giải quyết tranh chấp (Dispute WORKER_FAULT):** −10 RS (BR-55)

Mỗi lần trừ điểm đều được lưu lại trong bảng `reliability_logs` kèm lý do và số điểm trước/sau.

### 1.2. Quy tắc Kháng Cáo (Appeal Rules)
1. **Thời hạn nộp (7-day Window):** Worker chỉ có thể nộp kháng cáo trong vòng **7 ngày** tính từ thời điểm bản ghi trừ điểm được tạo (`log.created_at + 7 days`). Quá thời hạn trên, hệ thống từ chối tiếp nhận (HTTP 400).
2. **Chống lạm dụng (Unique Appeal):** Mỗi bản ghi trừ điểm (`reliability_log_id`) chỉ được phép nộp kháng cáo **duy nhất 1 lần**. Nộp trùng lặp trả về HTTP 400.
3. **Quyền sở hữu (IDOR Protection):** Worker chỉ được kháng cáo bản ghi trừ điểm của chính mình, WorkerId lấy trực tiếp từ `Authentication.getPrincipal()` thông qua `SecurityUtils.getCurrentUserId()`.
4. **Quyết định thẩm định (Final Decision):**
   - Customer Care hoặc Admin có thẩm quyền xem xét bằng chứng bất khả kháng (force majeure) và ra quyết định.
   - Khi **CHẤP THUẬN (`approved: true`)**:
     - Hệ thống khôi phục điểm RS tương ứng với số điểm đã bị phạt (`score = min(100, score + penaltyAmount)`).
     - Tự động ghi thêm một dòng `reliability_logs` mới với `change_amount > 0`, `reference_type = "RS_APPEAL"`, `reference_id = appeal.id`.
     - Ghi AuditLog `APPROVE_RS_APPEAL`.
     - Gửi thông báo đến Worker qua `NotificationService` (Type `RS_APPEAL`).
   - Khi **TỪ CHỐI (`approved: false`)**:
     - Cập nhật trạng thái `REJECTED`, giữ nguyên điểm RS.
     - Ghi AuditLog `REJECT_RS_APPEAL`.
     - Gửi thông báo nêu rõ lý do từ chối đến Worker.
   - Quyết định là cuối cùng (bất biến), không thể thay đổi lại trạng thái sau khi đã có kết luận.

---

## 2. Chi Tiết Danh Sách API

### 2.1. Worker Nộp Đơn Kháng Cáo
* **Endpoint:** `POST /api/v1/workers/appeals`
* **Method:** `POST`
* **Quyền:** `WORKER`
* **Request Body:**
```json
{
  "reliabilityLogId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "reasonCode": "FORCE_MAJEURE_MEDICAL",
  "reasonDetail": "Bị tai nạn giao thông trên đường đến ca làm việc, phải cấp cứu tại Bệnh viện Chợ Rẫy",
  "evidence": "https://storage.cani.vn/appeals/evidence-medical-report-123.pdf"
}
```

#### Response (200 OK):
```json
{
  "success": true,
  "message": "Nộp đơn kháng cáo điểm uy tín thành công",
  "data": {
    "id": "e99148d2-45e0-4786-905a-f1190a421b01",
    "workerId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "workerName": "Nguyễn Văn A",
    "workerEmail": "worker@cani.vn",
    "currentReliabilityScore": 75,
    "reliabilityLogId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "penaltyAmount": 25,
    "deductedAt": "2026-11-05T08:00:00Z",
    "deductionReason": "Vắng mặt không báo trước (No-show)",
    "reasonCode": "FORCE_MAJEURE_MEDICAL",
    "reasonDetail": "Bị tai nạn giao thông trên đường đến ca làm việc...",
    "evidence": "https://storage.cani.vn/appeals/evidence-medical-report-123.pdf",
    "status": "SUBMITTED",
    "createdAt": "2026-11-08T09:00:00Z"
  }
}
```

---

### 2.2. Worker Lấy Danh Sách Đơn Kháng Cáo Của Mình
* **Endpoint:** `GET /api/v1/workers/appeals`
* **Method:** `GET`
* **Quyền:** `WORKER`

#### Response (200 OK):
```json
{
  "success": true,
  "message": "Lấy danh sách kháng cáo điểm uy tín thành công",
  "data": [
    {
      "id": "e99148d2-45e0-4786-905a-f1190a421b01",
      "penaltyAmount": 25,
      "reasonCode": "FORCE_MAJEURE_MEDICAL",
      "status": "SUBMITTED",
      "createdAt": "2026-11-08T09:00:00Z"
    }
  ]
}
```

---

### 2.3. Hàng Đợi Kháng Cáo (Admin & Customer Care)
* **Endpoint:** `GET /api/v1/admin/appeals`
* **Method:** `GET`
* **Quyền:** `ADMIN`, `CUSTOMER_CARE`
* **Query Parameters:**
  - `status` *(tùy chọn)*: `SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`
  - `page`, `size`, `sort`

#### Response (200 OK):
```json
{
  "success": true,
  "message": "Lấy danh sách kháng cáo điểm uy tín thành công",
  "data": {
    "content": [
      {
        "id": "e99148d2-45e0-4786-905a-f1190a421b01",
        "workerName": "Nguyễn Văn A",
        "penaltyAmount": 25,
        "reasonCode": "FORCE_MAJEURE_MEDICAL",
        "status": "SUBMITTED",
        "createdAt": "2026-11-08T09:00:00Z"
      }
    ],
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### 2.4. Xem Chi Tiết Đơn Kháng Cáo
* **Endpoint:** `GET /api/v1/admin/appeals/{id}`
* **Method:** `GET`
* **Quyền:** `ADMIN`, `CUSTOMER_CARE`

---

### 2.5. Ra Quyết Định Phê Duyệt / Từ Chối Kháng Cáo
* **Endpoint:** `POST /api/v1/admin/appeals/{id}/review`
* **Method:** `POST`
* **Quyền:** `ADMIN`, `CUSTOMER_CARE`
* **Request Body:**
```json
{
  "approved": true,
  "decisionNote": "Bằng chứng bệnh viện hợp lệ và dấu mộc xác nhận tai nạn bất khả kháng. Phê duyệt khôi phục 25 điểm RS."
}
```

#### Response (200 OK):
```json
{
  "success": true,
  "message": "Chấp thuận kháng cáo và khôi phục điểm uy tín thành công",
  "data": {
    "id": "e99148d2-45e0-4786-905a-f1190a421b01",
    "workerId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "workerName": "Nguyễn Văn A",
    "currentReliabilityScore": 100,
    "penaltyAmount": 25,
    "status": "APPROVED",
    "reviewerId": "11111111-1111-1111-1111-111111111111",
    "reviewerName": "Chuyên viên Hỗ trợ",
    "decisionNote": "Bằng chứng bệnh viện hợp lệ và dấu mộc xác nhận tai nạn bất khả kháng. Phê duyệt khôi phục 25 điểm RS.",
    "decidedAt": "2026-11-08T10:00:00Z"
  }
}
```

