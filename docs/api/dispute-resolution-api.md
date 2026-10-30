# CANi API Documentation — Phân Hệ Tranh Chấp & Giải Quyết (FE-11: UC-96 → UC-104)

> **Mục tiêu:** Tài liệu bàn giao kỹ thuật từ Backend sang đội ngũ Frontend (Web Admin / Customer Care / Mobile Worker) và kiểm thử Postman cho chức năng Dispute & Resolution.
> **Người phụ trách Backend:** QuyTH
> **Phiên bản:** 1.0.0 (Spring Boot 3.5.1 / Java 21)
> **Base URL:** `/api/v1/disputes` (bên liên quan) · `/api/v1/admin/disputes` (CC / Admin)
> **Migration:** `V11__dispute_resolution.sql` (PostgreSQL/Supabase — tạo enum `dispute_status_new→dispute_status`, `dispute_type_new→dispute_type`, `dispute_outcome`, bảng `dispute_evidence`; profile H2 dùng `ddl-auto` từ entity)

---

## 1. Tổng Quan Nghiệp Vụ

Vòng đời tranh chấp theo SRS (UC-96 → UC-104, BR-52..59, 63, 68):

```
OPEN ──CC nhận/chuyển đơn (UC-99)──> INVESTIGATING ──CC quyết định (UC-100)──> RESOLVED / UNVERIFIED
                                                                              │
                                                            (UC-101, BR-54: kháng nghị trong 7 ngày)
                                                                              ▼
                                                                        ESCALATED ──Admin quyết định cuối (UC-102)──> FINAL_RESOLVED
```

**Quy tắc nghiệp vụ chính:**
- **BR-52:** Chỉ được nộp tranh chấp trong **14 ngày** kể từ thời điểm ca kết thúc; mô tả tối thiểu **50 ký tự**; không được có 2 tranh chấp mở cùng ca.
- **BR-53:** CC ra quyết định trong **7 ngày** (hệ thống tự đặt `slaDeadline = now + 7d`).
- **BR-54:** Bên thua được quyền **kháng nghị lên Admin trong 7 ngày** kể từ quyết định của CC.
- **BR-55:** Quyết định `WORKER_FAULT` → **tự động −10 điểm RS** (ghi `ReliabilityLog`, chận trong [0,100]); CC/Admin có thể **miễn penalty** kèm lý do (`penaltyWaiverReason`).
- **BR-57:** Hàng đợi ưu tiên theo: (1) rủi ro SLA (<24h đến hạn), (2) nộp cũ nhất.
- **BR-68:** Quyết định của Admin là **cuối cùng**, không kháng nghị thêm.

**Phân quyền (RBAC):**
| Endpoint | Quyền |
| :--- | :--- |
| `/v1/disputes/**` | Mọi user đã xác thực (service kiểm tra quyền sở hữu bên liên quan) |
| `/v1/admin/disputes/**` | `ADMIN` hoặc `CUSTOMER_CARE` (UC-102 yêu cầu `ADMIN` + trạng thái `ESCALATED` — service tự chặn) |

---

## 2. Endpoint — Bên Liên Quan (Worker / Event Organizer)

### 2.1. Nộp tranh chấp (UC-96)

* **Endpoint:** `POST /api/v1/disputes`
* **Request Body:**
```json
{
  "assignmentId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "type": "PAYMENT",
  "title": "Chưa nhận tiền công ca Lễ tân",
  "description": "Nhà tổ chức chưa thanh toán tiền công và phụ cấp sau khi ca kết thúc..."
}
```
| Trường | Kiểu | Bắt buộc | Ràng buộc |
| :--- | :--- | :--- | :--- |
| `assignmentId` | UUID | Có | Ca phải tồn tại; user phải là Worker hoặc EO của ca đó |
| `type` | `PAYMENT` / `WORK_QUALITY` / `ATTENDANCE` / `CONDUCT` / `OTHER` | Có | |
| `title` | String | Có | max 200 |
| `description` | String | Có | **min 50, max 3000** (BR-52) |

* **Hành phụ:** loại `PAYMENT` → bản ghi `Payment` của ca chuyển sang `DISPUTED`; 2 bên + toàn bộ đội CC nhận notification.
* **Response 200:** `DisputeDetailResponseDto` (mục 4).
* **Lỗi:**
  | HTTP | Tình huống |
  | :--- | :--- |
  | 400 | Quá cửa sổ 14 ngày · trùng tranh chấp mở cùng ca · user không phải bên liên quan |
  | 404 | Ca làm việc không tồn tại |

### 2.2. Xem chi tiết (UC-97) / Lịch sử (UC-104)

* `GET /api/v1/disputes/{id}` — bên liên quan, CC, Admin được xem; user khác → **400 (MSG34)**.
* `GET /api/v1/disputes/my` — danh sách tranh chấp có mình (nộp hoặc bị đơn), mới nhất trước.

### 2.3. Nạp bằng chứng (UC-98)

* **Endpoint:** `POST /api/v1/disputes/{id}/evidence`
```json
{
  "fileType": "IMAGE",
  "fileUrl": "https://storage.cani.vn/disputes/<uuid>.jpg",
  "label": "Ảnh chứng từ thanh toán"
}
```
| Trường | Kiểu | Bắt buộc |
| :--- | :--- | :--- |
| `fileType` | `IMAGE` / `VIDEO` / `DOCUMENT` / `TRANSACTION_RECORD` | Có |
| `fileUrl` | String (max 500) | Có |
| `label` | String (max 255) | Không |

* Chỉ bên liên quan được nạp; chỉ khi đơn ở `OPEN`/`INVESTIGATING` (đã quyết định → **400 (MSG145)**).
* **Lưu ý:** `fileUrl` theo convention storage hiện hành của dự án (URL placeholder `https://storage.cani.vn/...` như eKYC) — module upload vật lý (UC-119) sẽ thay thế khi hoàn thiện.

### 2.4. Kháng nghị lên Admin (UC-101, BR-54)

* **Endpoint:** `POST /api/v1/disputes/{id}/escalate`
```json
{ "reason": "Không đồng ý với kết quả quyết định của bộ phận chăm sóc khách hàng..." }
```
* Chỉ khi đơn ở `RESOLVED`/`UNVERIFIED` và **còn trong 7 ngày** kể từ `resolvedAt` (quá hạn → **400 (MSG141)**). Đơn chuyển sang `ESCALATED`, hai bên + hệ thống được thông báo.

---

## 3. Endpoint — Customer Care / Admin

### 3.1. Hàng đợi tranh chấp (UC-109, BR-57)

* **Endpoint:** `GET /api/v1/admin/disputes/queue`
* **Response:** danh sách `DisputeQueueItemDto` (trạng thái `OPEN`/`INVESTIGATING`), sắp xếp: đơn có rủi ro SLA (`slaAtRisk = true`, còn <24h đến hạn) trước, rồi theo thời gian nộp cũ nhất.

### 3.2. Gán / chuyển đơn (UC-99)

* **Endpoint:** `POST /api/v1/admin/disputes/{id}/assign`
* **Body (tuỳ chọn):** `{ "targetCcUserId": "<uuid-cc-khác>" }` — bỏ trống = **CC tự nhận**.
* Đơn `OPEN` được nhận → tự chuyển `INVESTIGATING`. Người nhận phải là role CC/ADMIN (nếu chỉ định).
* Đơn đã quyết định / đã escalate → **400**.

### 3.3. Ra quyết định (UC-100 cho CC · UC-102 cho Admin)

* **Endpoint:** `POST /api/v1/admin/disputes/{id}/decision`
```json
{
  "outcome": "WORKER_FAULT",
  "rationale": "Qua đối chiếu nhật ký điểm danh QR và chứng từ thanh toán, xác nhận worker bỏ ca... (≥100 ký tự)",
  "penaltyWaiverReason": null
}
```
| Trường | Kiểu | Ràng buộc |
| :--- | :--- | :--- |
| `outcome` | `EO_FAULT` / `WORKER_FAULT` / `PARTIAL` / `UNVERIFIED` / `UPHOLD` | Bắt buộc |
| `rationale` | String | **min 100, max 5000** (BR-55/68) |
| `penaltyWaiverReason` | String (max 500) | Có → miễn trừ −10 RS dù `WORKER_FAULT` |

* **Quy tắc chấp nhận (service kiểm tra, trả 400 nếu vi phạm):**
  - CC/Admin ở trạng thái `INVESTIGATING` → ra quyết định thường (`RESOLVED`/`UNVERIFIED`).
  - **Chỉ Admin** ở trạng thái `ESCALATED` → quyết định cuối (`FINAL_RESOLVED`, ghi `finalDecidedBy/At`).
* **Hành phụ (BR-55):** `WORKER_FAULT` (không được miễn) → `reliabilityScore` của Worker bị đơn −10, ghi `ReliabilityLog` (referenceType `DISPUTE`), đánh dấu `rsPenaltyApplied` tránh trừ 2 lần ở bước Admin.

---

## 4. Cấu Trúc Response Chung

### 4.1. `DisputeDetailResponseDto`
```json
{
  "success": true,
  "message": "Tranh chấp đã được tạo và chuyển đến bộ phận Chăm sóc khách hàng",
  "data": {
    "id": "e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a",
    "assignmentId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "complainantId": "08d4cbd3-9d94-4307-99c5-b66484d5de84",
    "respondentId": "f8b3d61a-8275-4cf9-b1e8-3a4c0d75922b",
    "type": "PAYMENT",
    "title": "Chưa nhận tiền công ca Lễ tân",
    "description": "...",
    "status": "OPEN",
    "outcome": null,
    "slaDeadline": "2026-11-04T12:30:00Z",
    "resolution": null,
    "decisionRationale": null,
    "escalationReason": null,
    "escalatedAt": null,
    "finalDecisionRationale": null,
    "finalDecidedAt": null,
    "rsPenaltyApplied": false,
    "createdAt": "2026-10-28T12:30:00Z",
    "updatedAt": "2026-10-28T12:30:00Z",
    "evidences": [
      {
        "id": "c3d4e5f6-...",
        "party": "08d4cbd3-9d94-4307-99c5-b66484d5de84",
        "fileType": "IMAGE",
        "fileUrl": "https://storage.cani.vn/disputes/xxx.jpg",
        "label": "Ảnh chứng từ",
        "createdAt": "2026-10-28T13:00:00Z"
      }
    ]
  },
  "timestamp": "..."
}
```

### 4.2. `DisputeQueueItemDto`
| Trường | Kiểu | Mô tả |
| :--- | :--- | :--- |
| `id` | UUID | ID tranh chấp |
| `type` / `title` / `status` | enum / string | danh mục, tiêu đề, trạng thái hiện tại |
| `complainantId` / `respondentId` | UUID | hai bên (frontend batch lookup tên như UC-86 recent activities) |
| `assignedTo` | UUID | CC đang phụ trách (null = chưa gán) |
| `slaDeadline` | Instant (ISO 8601) | hạn quyết định (BR-53) |
| `escalated` | boolean | cờ dự phòng (hiện tại queue chỉ gồm đơn mở, luôn `false`) |
| `slaAtRisk` | boolean | còn < 24h đến hạn SLA |

---

## 5. Xử Lý Lỗi (Error Handling)

Mọi lỗi đều trả `ApiResponse` chuẩn của dự án:
```json
{ "success": false, "message": "Lý do cụ thể (tiếng Việt)", "timestamp": "..." }
```
| HTTP | Ví dụ message |
| :--- | :--- |
| **400** | `Đã quá thời hạn tranh chấp 14 ngày kể từ khi ca kết thúc (BR-52...)` · `Đã có tranh chấp chưa được xử lý cho ca làm việc này` · `Tranh chấp đang ở trạng thái RESOLVED, không chấp nhận quyết định...` |
| **401** | `Full authentication is required to access this resource` |
| **403** | Token không có role `ADMIN`/`CUSTOMER_CARE` (trên `/v1/admin/disputes/**`) |
| **404** | `Ca làm việc không tồn tại với id: '...'` / `Tranh chấp không tồn tại với id: '...'` |

---

## 6. Ghi Chép Kiểm Toán (BR-70)

Mọi thay đổi trạng thái sinh `audit_logs` (REQUIRES_NEW, không rollback nghiệp vụ):
`SUBMIT_DISPUTE` · `ADD_DISPUTE_EVIDENCE` · `ASSIGN_DISPUTE` · `ISSUE_DISPUTE_DECISION` · `ESCALATE_DISPUTE` · `DISPUTE_FINAL_DECISION` — entity type `DISPUTE`, có `oldValues`/`newValues` JSON, hiển thị trên UC-86 recent activities (nhan tiếng Việt).
