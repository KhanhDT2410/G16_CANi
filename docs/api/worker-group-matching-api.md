# CANi API Documentation — Worker Group Matching & Dispatch (UC-70, UC-71 Extended)

> **Mục tiêu:** Tài liệu bàn giao kỹ thuật API cho các endpoint hỗ trợ điều phối ca làm việc theo nhóm (Group-Aware Matching & Dispatch). Bổ sung cho spec gốc UC-70 (Tạo nhóm), UC-71 (Quản lý nhóm) với kênh dispatch song song (FC-MATCH-06) và chuyển giao leadership.
> **Người phụ trách Backend:** QuyTH
> **Phiên bản:** 1.0.0 (Spring Boot 3.5.1 / Java 21)
> **Base URL:** `/api/v1/worker-groups`
> **Phân quyền RBAC:** `WORKER` (Leader nhóm), `ADMIN`

---

## 1. Tổng Quan Nghiệp Vụ (Group-Aware Matching)

### 1.1. Kiến trúc hai kênh Dispatch
Hệ thống điều phối giờ chạy **hai kênh song song** cho một ca làm việc (`Shift`):

| Kênh | Mô tả | Đối tượng nhận proposal | Logic lọc | Thời hạn phản hồi |
|------|-------|------------------------|-----------|-------------------|
| **Individual (cũ)** | Kênh matching cá nhân hiện có (FC-MATCH-01..05) | Worker độc lập (`isOnline=true`) | Hard filter: online, eKYC, RS, skill, lịch, blocklist, minWage | 1 giờ (mặc định), 30 phút (khẩn cấp) |
| **Group (mới - FC-MATCH-06)** | Kênh dispatch theo đội nhóm | **Trưởng nhóm (Leader)** của `WorkerGroup` | Chỉ xét `group_memberships` ACTIVE; **BỎ** kiểm tra `isOnline` cho member; no-show cooldown 20 phút | 1 giờ (mặc định), 30 phút (khẩn cấp) |

Khi `dispatchForShift(shiftId)` chạy: 
1. Thực hiện kênh cá nhân trước → tạo `DispatchProposal` nguồn `MATCHING/DIRECT/BULK`
2. Thực hiện kênh group → tạo `DispatchProposal` nguồn `GROUP` (gán `group_id`, `worker_id = leader`)
3. Tổng proposals = cá nhân + group

### 1.2. Quy tắc Group Dispatch (BR-GM-01..05)
1. **Leader điều khiển:** Proposal gán cho `worker = leaderWorkerId` của group; FCM gửi leader.
2. **Member eligible:** Xét `group_memberships` có `status = ACTIVE`; lọc theo `groupMemberEligibilityReason()` (giống hard filter cá nhân nhưng **không** xét `isOnline`).
3. **Claimable slots:** `min(eligibleCount, shift.remainingWorkers)`. Leader chọn 1 → claimableSlots member.
4. **No-show cooldown 20 phút:** Nếu group có member no-show trong 20 phút qua (`worker_groups.last_no_show_at`), group bị bỏ qua trong group dispatch cho đến khi hết cooldown.
5. **Top-5 groups:** Chỉ gửi proposal cho tối đa 5 group có điểm cao nhất (ranked by distance, group rating, exp, RS).
6. **Orphaned proposal expiry:** Nếu leader chuyển nhượng trong khi có proposal `PENDING` (group channel), proposal cũ tự chuyển `EXPIRED` bởi `expireStalePendingProposals()` cron (5 phút/lần).

### 1.3. Emergency Dispatch mở rộng
Khi EO kích hoạt Emergency (`triggerEmergencyDispatch`): cả hai kênh chạy với `expiresAt = now + 30 phút`, tiêu đề FCM có tiền tố `🚨 GẤP `.

---

## 2. Chi Tiết Danh Sách API Endpoints

> **Lưu ý:** Các endpoint UC-70/71 gốc (`createGroup`, `updateGroup`, `getGroupMembers`, `inviteMember`, `acceptInvite`, `removeMember`, `getGroupShifts`, `searchGroups`) đã có trong spec UC-70/UC-71. Dưới đây là **5 endpoint mới/mở rộng** cho Group Matching & Dispatch.

---

### 2.1. Lấy danh sách đề xuất ca làm việc gửi cho nhóm
* **Endpoint:** `GET /api/v1/worker-groups/{id}/group-proposals`
* **Mô tả:** Trả về danh sách `DispatchProposal` nguồn `GROUP` đang `PENDING` hoặc `EXPIRED` trong 24h qua cho group. Chỉ Leader nhóm hoặc ADMIN.
* **Phân quyền:** `@PreAuthorize("hasAnyRole('WORKER','ADMIN')")` + service-level leader check
* **Headers:**
  ```http
  Authorization: Bearer <jwt_access_token>
  Accept: application/json
  ```
* **Path Variables:**
  - `id` *(UUID, Bắt buộc)*: ID của WorkerGroup.

#### Response Success (200 OK):
```json
{
  "success": true,
  "message": "Lấy danh sách đề xuất ca cho nhóm thành công",
  "data": [
    {
      "proposalId": "c1a2b3c4-d5e6-7890-abcd-ef1234567890",
      "shiftId": "s1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "shiftName": "Phục vụ bàn",
      "eventName": "Tiệc Cưới A & B",
      "eventId": "e1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "hourlyWage": 50000,
      "startTime": "2026-11-09T10:00:00Z",
      "endTime": "2026-11-09T18:00:00Z",
      "status": "PENDING",
      "matchScore": 92.5,
      "expiresAt": "2026-11-09T11:00:00Z",
      "createdAt": "2026-11-09T10:05:00Z",
      "eligibleMemberCount": 3,
      "claimableSlots": 2,
      "eligibleMembers": [
        { "workerId": "m1...", "fullName": "Trần Thị Thợ", "reliabilityScore": 85 },
        { "workerId": "m2...", "fullName": "Lê Văn Hai", "reliabilityScore": 90 },
        { "workerId": "m3...", "fullName": "Nguyễn Văn Trưởng", "reliabilityScore": 95 }
      ],
      "scoreBreakdown": "{\"groupId\":\"...\",\"groupName\":\"Đội Phục Vụ\",\"distanceKm\":1.20,\"eligibleMemberWorkerIds\":[\"m1...\",\"m2...\",\"m3...\"],\"eligibleCount\":3,\"claimableSlots\":2,\"finalScore\":92.50}"
    },
    {
      "proposalId": "d2b3c4d5-e6f7-8901-bcde-f23456789012",
      "shiftId": "s2c3d4e5-f6a7-8901-bcde-f23456789012",
      "shiftName": "Lễ tân",
      "eventName": "Tech Expo",
      "eventId": "e2c3d4e5-f6a7-8901-bcde-f23456789012",
      "hourlyWage": 45000,
      "startTime": "2026-11-10T08:00:00Z",
      "endTime": "2026-11-10T12:00:00Z",
      "status": "EXPIRED",
      "matchScore": 88.0,
      "expiresAt": "2026-11-10T09:00:00Z",
      "createdAt": "2026-11-10T08:02:00Z",
      "eligibleMemberCount": 2,
      "claimableSlots": 1,
      "eligibleMembers": [ ... ],
      "scoreBreakdown": "..."
    }
  ]
}
```

#### Error Codes:
| HTTP Status | Message | Nguyên nhân |
|-------------|---------|-------------|
| `403 Forbidden` | `Chỉ Trưởng nhóm mới có quyền xem danh sách đề xuất ca` | Caller không phải leader của group |
| `404 Not Found` | `Không tìm thấy nhóm thợ với ID: ...` | Group ID không tồn tại |

---

### 2.2. Re-check thành viên đủ điều kiện của một đề xuất ca
* **Endpoint:** `GET /api/v1/worker-groups/{id}/group-proposals/eligible?proposalId={proposalId}`
* **Mô tả:** Thực hiện **re-check** (không ghi data) trạng thái đủ điều kiện của từng member ACTIVE cho một proposal cụ thể. Dùng trước khi Leader bấm "Nhận ca" để confirm slots.
* **Phân quyền:** `@PreAuthorize("hasAnyRole('WORKER','ADMIN')")` + service-level leader check
* **Headers:**
  ```http
  Authorization: Bearer <jwt_access_token>
  Accept: application/json
  ```
* **Path Variables:**
  - `id` *(UUID, Bắt buộc)*: ID của WorkerGroup.
* **Query Parameters:**
  - `proposalId` *(UUID, Bắt buộc)*: ID của `DispatchProposal` nguồn `GROUP`.

#### Response Success (200 OK):
```json
{
  "success": true,
  "message": "Lấy danh sách thành viên đủ điều kiện thành công",
  "data": {
    "proposalId": "c1a2b3c4-d5e6-7890-abcd-ef1234567890",
    "shiftId": "s1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "shiftName": "Phục vụ bàn",
    "claimableSlots": 2,
    "eligibleMembers": [
      { "workerId": "m1...", "fullName": "Trần Thị Thợ", "reliabilityScore": 85, "isEligible": true, "reason": null },
      { "workerId": "m2...", "fullName": "Lê Văn Hai", "reliabilityScore": 90, "isEligible": true, "reason": null },
      { "workerId": "m3...", "fullName": "Nguyễn Văn Trưởng", "reliabilityScore": 95, "isEligible": false, "reason": "SHIFT_CONFLICT" }
    ],
    "summary": { "totalActive": 3, "eligible": 2, "ineligible": 1 }
  }
}
```

#### Error Codes:
| HTTP Status | Message | Nguyên nhân |
|-------------|---------|-------------|
| `400 Bad Request` | `Đề xuất ca không thuộc về nhóm này` | Proposal không có `group_id` khớp |
| `403 Forbidden` | `Chỉ Trưởng nhóm mới có quyền xem` | Caller không phải leader |
| `404 Not Found` | `Không tìm thấy đề xuất ca với ID: ...` | Proposal ID không tồn tại |

---

### 2.3. Leader chấp nhận đề xuất ca cho nhóm (All-or-Nothing, Atomic Slots)
* **Endpoint:** `POST /api/v1/worker-groups/{id}/group-proposals/{proposalId}/accept`
* **Mô tả:** Trưởng nhóm chọn danh sách member nhận ca. Hệ thống: (1) re-check eligible, (2) atomic increment `shift.confirmed_workers` bằng `k`, (3) nếu thành công → tạo `k` `ShiftAssignment` nguồn `GROUP` + `group_id` + `isGroupLeader` theo role, (4) tạo payment, (5) FCM EO + k member + leader, (6) proposal → `ACCEPTED`. Nếu bất kỳ bước nào fail → **rollback toàn bộ** (0 assignment).
* **Phân quyền:** `@PreAuthorize("hasAnyRole('WORKER','ADMIN')")` + service-level leader check
* **Headers:**
  ```http
  Authorization: Bearer <jwt_access_token>
  Content-Type: application/json
  ```
* **Path Variables:**
  - `id` *(UUID, Bắt buộc)*: ID của WorkerGroup.
  - `proposalId` *(UUID, Bắt buộc)*: ID của `DispatchProposal` nguồn `GROUP` đang `PENDING`.
* **Request Body:**
  ```json
  {
    "memberWorkerIds": ["m1-worker-id-uuid", "m2-worker-id-uuid"]
  }
  ```
  * `memberWorkerIds` *(Array<UUID>, Bắt buộc)*: Danh sách `workerId` của các member ACTIVE được chọn nhận ca. Số lượng `k` phải ≤ `claimableSlots` của proposal.

#### Response Success (200 OK):
```json
{
  "success": true,
  "message": "Đội của bạn đã nhận ca làm việc thành công!",
  "data": {
    "shiftId": "s1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "proposalId": "c1a2b3c4-d5e6-7890-abcd-ef1234567890",
    "assignmentIds": ["a1...", "a2..."],
    "memberCount": 2,
    "assignments": [
      {
        "assignmentId": "a1...",
        "workerId": "m1...",
        "workerName": "Trần Thị Thợ",
        "status": "CONFIRMED",
        "source": "GROUP",
        "groupId": "g1...",
        "isGroupLeader": false,
        "roleInGroup": "MEMBER"
      },
      {
        "assignmentId": "a2...",
        "workerId": "m2...",
        "workerName": "Lê Văn Hai",
        "status": "CONFIRMED",
        "source": "GROUP",
        "groupId": "g1...",
        "isGroupLeader": false,
        "roleInGroup": "MEMBER"
      }
    ]
  }
}
```

#### Error Codes:
| HTTP Status | Message | Ý Nghĩa / Nguyên Nhân |
|-------------|---------|----------------------|
| `400 Bad Request` | `Số thành viên chọn (3) vượt quá slot còn trống (2)` | `k > claimableSlots` |
| `400 Bad Request` | `Thành viên ... không đủ điều kiện: SHIFT_CONFLICT` | Re-check fail (trùng lịch, kyc, skill, v.v.) |
| `400 Bad Request` | `Đề xuất ca đã hết hạn hoặc không còn PENDING` | Proposal không ở trạng thái `PENDING` |
| `403 Forbidden` | `Chỉ Trưởng nhóm mới có quyền chấp nhận đề xuất ca cho nhóm` | Caller không phải leader |
| `404 Not Found` | `Không tìm thấy nhóm thợ / đề xuất ca` | ID không tồn tại |
| `409 Conflict` | `Ca làm việc vừa được người khác nhận đủ số lượng` | Atomic counter `incrementConfirmedWorkersAtomicBy` trả 0 (race condition) |

---

### 2.4. Leader từ chối đề xuất ca cho nhóm (Re-dispatch slot còn thiếu)
* **Endpoint:** `POST /api/v1/worker-groups/{id}/group-proposals/{proposalId}/reject`
* **Mô tả:** Trưởng nhóm từ chối proposal. Hệ thống: proposal → `REJECTED`, gọi `matchingDispatchService.dispatchForShift(shiftId)` để fill lại slot còn thiếu bằng các kênh khác (cá nhân + group khác).
* **Phân quyền:** `@PreAuthorize("hasAnyRole('WORKER','ADMIN')")` + service-level leader check
* **Headers:**
  ```http
  Authorization: Bearer <jwt_access_token>
  Content-Type: application/json
  ```
* **Path Variables:**
  - `id` *(UUID, Bắt buộc)*: ID của WorkerGroup.
  - `proposalId` *(UUID, Bắt buộc)*: ID của `DispatchProposal` nguồn `GROUP`.
* **Request Body:**
  ```json
  {
    "reason": "Ca trùng với lịch đội khác"
  }
  ```
  * `reason` *(String, Bắt buộc)*: Lý do từ chối.

#### Response Success (200 OK):
```json
{
  "success": true,
  "message": "Đã từ chối đề xuất ca cho nhóm",
  "data": null
}
```

#### Error Codes:
| HTTP Status | Message | Ý Nghĩa |
|-------------|---------|---------|
| `400 Bad Request` | `Đề xuất ca đã được xử lý` | Proposal không ở `PENDING` |
| `403 Forbidden` | `Chỉ Trưởng nhóm mới có quyền từ chối đề xuất ca cho nhóm` | Caller không phải leader |
| `404 Not Found` | `Không tìm thấy đề xuất ca với ID: ...` | Proposal ID không tồn tại |

---

### 2.5. Chuyển giao Trưởng nhóm (Transfer Leadership)
* **Endpoint:** `POST /api/v1/worker-groups/{id}/transfer-leader`
* **Mô tả:** Trưởng nhóm hiện tại (hoặc ADMIN) chuyển quyền Leader sang một member `ACTIVE` khác. Thao tác **atomic** trong 1 transaction: (1) swap `role_in_group` của 2 membership (old→MEMBER, new→LEADER), (2) cập nhật `worker_groups.leader_worker_id`, (3) **tất cả proposal `PENDING` nguồn `GROUP` của nhóm này → `EXPIRED`** (orphaned), (4) FCM gửi user của leader mới.
* **Phân quyền:** `@PreAuthorize("hasAnyRole('WORKER','ADMIN')")` + service-level leader check
* **Headers:**
  ```http
  Authorization: Bearer <jwt_access_token>
  Content-Type: application/json
  ```
* **Path Variables:**
  - `id` *(UUID, Bắt buộc)*: ID của WorkerGroup.
* **Request Body:**
  ```json
  {
    "newLeaderWorkerId": "m2-worker-id-uuid"
  }
  ```
  * `newLeaderWorkerId` *(UUID, Bắt buộc)*: `workerId` của member sẽ trở thành Leader mới. Phải là membership `ACTIVE` hiện tại của group.

#### Response Success (200 OK):
```json
{
  "success": true,
  "message": "Đã chuyển giao Trưởng nhóm thành công",
  "data": null
}
```

#### Error Codes:
| HTTP Status | Message | Ý Nghĩa |
|-------------|---------|---------|
| `400 Bad Request` | `Người tiếp quản phải là thành viên ACTIVE của nhóm` | Membership không phải `ACTIVE` (PENDING/REMOVED) |
| `400 Bad Request` | `Không thể chuyển giao cho chính mình` | `newLeaderWorkerId` == leader hiện tại |
| `403 Forbidden` | `Chỉ Trưởng nhóm hiện tại mới có quyền chuyển giao` | Caller không phải leader (ADMIN được phép) |
| `404 Not Found` | `Không tìm thấy nhóm thợ / Worker` | ID không tồn tại |

---

## 3. Bảng Mã Lỗi (Error Codes) — Tổng Hợp

| HTTP Status | Message Tiếng Việt | Endpoint(s) | Ý Nghĩa / Nguyên Nhân |
|---|---|---|---|
| `400 Bad Request` | `Số thành viên chọn (...) vượt quá slot còn trống (...)` | acceptGroupProposal | `k > claimableSlots` |
| `400 Bad Request` | `Thành viên ... không đủ điều kiện: <REASON>` | acceptGroupProposal | Re-check fail: `KYC_UNAPPROVED`, `USER_INACTIVE`, `LOW_RELIABILITY`, `LOW_EXPERIENCE`, `MISSING_SKILLS`, `SHIFT_CONFLICT`, `WAGE_BELOW_MIN`, `UNAVAILABLE` |
| `400 Bad Request` | `Đề xuất ca đã hết hạn hoặc không còn PENDING` | acceptGroupProposal, rejectGroupProposal | Proposal status != `PENDING` |
| `400 Bad Request` | `Đề xuất ca không thuộc về nhóm này` | getGroupProposalEligible | Proposal.group_id != path groupId |
| `400 Bad Request` | `Người tiếp quản phải là thành viên ACTIVE của nhóm` | transferLeader | Membership status != ACTIVE |
| `400 Bad Request` | `Không thể chuyển giao cho chính mình` | transferLeader | newLeader == currentLeader |
| `403 Forbidden` | `Chỉ Trưởng nhóm hiện tại mới có quyền xem/phê duyệt/chuyển giao` | Tất cả | Caller không phải leader (ADMIN bypass) |
| `404 Not Found` | `Không tìm thấy nhóm thợ / đề xuất ca / Worker với ID: ...` | Tất cả | Resource ID không tồn tại |
| `409 Conflict` | `Ca làm việc vừa được người khác nhận đủ số lượng` | acceptGroupProposal | Atomic slot counter race condition (counter trả 0) |

---

## 4. Response DTO Reference

### 4.1. GroupProposalResponseDto
| Field | Type | Mô tả |
|-------|------|-------|
| proposalId | UUID | ID đề xuất |
| shiftId | UUID | ID ca làm việc |
| shiftName | String | Tên vị trí (jobType) |
| eventName | String | Tên sự kiện |
| eventId | UUID | ID sự kiện |
| hourlyWage | Long | Lương giờ (VND) |
| startTime | ISO-8601 | Thời gian bắt đầu ca |
| endTime | ISO-8601 | Thời gian kết thúc ca |
| status | String | `PENDING` / `EXPIRED` |
| matchScore | Decimal | Điểm matching nhóm (0-100+) |
| expiresAt | ISO-8601 | Hạn phản hồi |
| createdAt | ISO-8601 | Thời điểm tạo proposal |
| eligibleMemberCount | Integer | Số member ACTIVE đủ điều kiện |
| claimableSlots | Integer | Số slot group có thể nhận (min(eligible, remaining)) |
| eligibleMembers | Array<MemberSummary> | Danh sách member tóm tắt |
| scoreBreakdown | String | JSON breakdown điểm nhóm |

### 4.2. AcceptGroupProposalResponseDto
| Field | Type | Mô tả |
|-------|------|-------|
| shiftId | UUID | ID ca làm việc |
| proposalId | UUID | ID proposal đã accept |
| assignmentIds | Array<UUID> | Danh sách assignment IDs vừa tạo |
| memberCount | Integer | Số member nhận ca thành công |
| assignments | Array<AssignmentDetail> | Chi tiết từng assignment |

### 4.3. AssignmentDetail (nested)
| Field | Type | Mô tả |
|-------|------|-------|
| assignmentId | UUID | ID phân công |
| workerId | UUID | Worker ID |
| workerName | String | Tên worker |
| status | String | `CONFIRMED` |
| source | String | `GROUP` |
| groupId | UUID | Group ID |
| isGroupLeader | Boolean | True nếu member là LEADER trong group |
| roleInGroup | String | `LEADER` / `MEMBER` |

---

## 5. Ví Dụ Gọi API (cURL)

### 5.1. Xem danh sách đề xuất ca cho nhóm
```bash
curl -X GET "http://localhost:8080/api/v1/worker-groups/GROUP_ID/group-proposals" \
  -H "Authorization: Bearer <jwt_access_token>"
```

### 5.2. Re-check thành viên đủ điều kiện
```bash
curl -X GET "http://localhost:8080/api/v1/worker-groups/GROUP_ID/group-proposals/eligible?proposalId=PROPOSAL_ID" \
  -H "Authorization: Bearer <jwt_access_token>"
```

### 5.3. Leader chấp nhận đề xuất ca cho 2 member
```bash
curl -X POST "http://localhost:8080/api/v1/worker-groups/GROUP_ID/group-proposals/PROPOSAL_ID/accept" \
  -H "Authorization: Bearer <jwt_access_token>" \
  -H "Content-Type: application/json" \
  -d '{"memberWorkerIds": ["WORKER_ID_1", "WORKER_ID_2"]}'
```

### 5.4. Leader từ chối đề xuất ca
```bash
curl -X POST "http://localhost:8080/api/v1/worker-groups/GROUP_ID/group-proposals/PROPOSAL_ID/reject" \
  -H "Authorization: Bearer <jwt_access_token>" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Trùng lịch đội khác"}'
```

### 5.5. Chuyển giao Trưởng nhóm
```bash
curl -X POST "http://localhost:8080/api/v1/worker-groups/GROUP_ID/transfer-leader" \
  -H "Authorization: Bearer <jwt_access_token>" \
  -H "Content-Type: application/json" \
  -d '{"newLeaderWorkerId": "NEW_LEADER_WORKER_ID"}'
```