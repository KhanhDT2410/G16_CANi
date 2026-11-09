# CANi API Documentation — Phân Hệ Thẩm Định Kỹ Năng Worker (UC-47)

> **Mục tiêu:** Tài liệu bàn giao kỹ thuật từ Backend sang đội ngũ Frontend (Web Admin / Customer Care) và kiểm thử Postman.  
> **Người phụ trách Backend:** QuyTH  
> **Phiên bản:** 1.0.0 (Spring Boot 3.5.1 / Java 21)  
> **Base URL:** `/api/v1/workers/skills`

---

## 1. Tổng Quan Nghiệp Vụ (UC-47)

* **Bối cảnh:** Khi Worker tải lên chứng chỉ nghề nghiệp (Tier 2 AI-Document), Vision AI tự động quét OCR và chấm điểm độ tin cậy. Nếu điểm tin cậy ở mức trung bình (`0.60 <= score < 0.85`), hồ sơ được chuyển sang trạng thái `PENDING_REVIEW` để Admin / Customer Care đối soát thủ công.
* **Quyền hạn truy cập (RBAC):** Bắt buộc Bearer JWT Token của người dùng có Role:
  * `ADMIN`
  * `CUSTOMER_CARE`

---

## 2. Danh Sách Endpoint Cho Admin / Customer Care

### 2.1. Lấy Danh Sách Hồ Sơ Kỹ Năng Chờ Duyệt (Hàng Đợi)

* **Endpoint:** `GET /api/v1/workers/skills/admin/queue`
* **Mô tả:** Lấy danh sách toàn bộ các yêu cầu xác thực kỹ năng đang chờ xử lý (`status = PENDING_REVIEW`), sắp xếp theo thời gian nộp cũ nhất lên trước (FIFO).
* **Phân quyền:** `@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")`
* **Headers:**
  ```http
  Authorization: Bearer <jwt_access_token>
  Accept: application/json
  ```

#### Cấu Trúc Dữ Liệu Trả Về (Response 200 OK):
```json
{
  "success": true,
  "message": "Lấy danh sách hồ sơ thẩm định kỹ năng chờ duyệt thành công",
  "data": [
    {
      "id": "e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a",
      "workerId": "08d4cbd3-9d94-4307-99c5-b66484d5de84",
      "workerName": "Nguyễn Văn Worker",
      "documentUrl": "https://storage.cani.vn/documents/skills/cert_bartender_01.jpg",
      "skillId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "skillName": "Bartender / Pha chế đồ uống",
      "jobType": "Food & Beverage",
      "recipientName": "NGUYEN VAN WORKER",
      "identityNumber": "079201004567",
      "institution": "Học Viện Đào Tạo Ẩm Thực Á Âu",
      "issueDate": "15/05/2024",
      "hasSealOrSignature": true,
      "overallConfidence": 0.74,
      "routingDecision": "ADMIN_REVIEW",
      "routingReason": "Chứng chỉ hợp lệ, dấu mộc tròn rõ ràng nhưng độ nét OCR đạt 74% (ngưỡng 80%)",
      "status": "PENDING_REVIEW",
      "spAwarded": 0,
      "currentTotalSp": 50,
      "currentTier": "STARTER",
      "createdAt": "2026-10-26T12:30:00Z"
    }
  ]
}
```

---

### 2.2. Phê Duyệt / Từ Chối Hồ Sơ Kỹ Năng

* **Endpoint:** `POST /api/v1/workers/skills/admin/{id}/review`
* **Mô tả:** Admin hoặc CSKH đối soát mắt hình ảnh chứng chỉ và đưa ra quyết định duyệt hoặc từ chối.
* **Phân quyền:** `@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")`
* **Headers:**
  ```http
  Authorization: Bearer <jwt_access_token>
  ```
* **Path Variables:**
  * `id` *(UUID, Bắt buộc)*: ID của yêu cầu thẩm định (`verificationId`).
* **Query Parameters:**
  * `approved` *(boolean, Bắt buộc)*: `true` nếu duyệt, `false` nếu từ chối.
  * `reviewNotes` *(string, Tùy chọn)*: Ghi chú lý do duyệt hoặc lý do từ chối.

#### Business Rules Khi Phê Duyệt:
1. **Nếu `approved = true`:**
   * Trạng thái cập nhật thành `APPROVED`.
   * Worker được cộng ngay **`+25 SP_verify`** vào nhóm ngành `jobType`.
   * Kỹ năng được kích hoạt Tier 2 (`AI_DOCUMENT`) có thời hạn hiệu lực **24 tháng** (730 ngày theo quy tắc BR-17).
   * Lưu ID của Admin/CSKH thực hiện duyệt (`reviewedBy`) và ghi chú (`reviewNotes`).
2. **Nếu `approved = false`:**
   * Trạng thái cập nhật thành `REJECTED`.
   * Điểm cộng `spAwarded = 0`.

#### Ví Dụ Gọi API Phê Duyệt (cURL):
```bash
curl -X POST "http://localhost:8080/api/v1/workers/skills/admin/e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a/review?approved=true&reviewNotes=Đã+đối+soát+dấu+mộc+hợp+lệ" \
  -H "Authorization: Bearer <jwt_access_token>"
```

#### Response 200 OK (Khi Duyệt Thành Công):
```json
{
  "success": true,
  "message": "Đã phê duyệt kỹ năng và cộng +25 SP_verify",
  "data": {
    "id": "e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a",
    "workerId": "08d4cbd3-9d94-4307-99c5-b66484d5de84",
    "workerName": "Nguyễn Văn Worker",
    "documentUrl": "https://storage.cani.vn/documents/skills/cert_bartender_01.jpg",
    "skillName": "Bartender / Pha chế đồ uống",
    "status": "APPROVED",
    "spAwarded": 25,
    "currentTotalSp": 75,
    "currentTier": "BRONZE",
    "createdAt": "2026-10-26T12:30:00Z"
  }
}
```

---

## 3. Mã Lỗi Thường Gặp (Error Handling)

| HTTP Status | Message | Nguyên nhân & Cách khắc phục |
| :---: | :--- | :--- |
| **`400 BAD_REQUEST`** | `"Yêu cầu này đã được xử lý trước đó với trạng thái: APPROVED"` | Hồ sơ đã được duyệt hoặc từ chối trước đó, không thể duyệt lại. |
| **`403 FORBIDDEN`** | `"Access Denied"` | Token không có quyền `ADMIN` hoặc `CUSTOMER_CARE`. |
| **`404 NOT_FOUND`** | `"Không tìm thấy yêu cầu thẩm định với ID: ..."` | ID truyền trên URL không tồn tại trong hệ thống. |
