# Đặc tả: Group-aware Matching — Giao cả ca cho Worker Group (Hướng A)

Ngày: 2026-11-21 · Trạng thái: DRAFT chờ duyệt · Owner: BA + Dev

## 1. Bối cảnh & vấn đề hiện tại

Hệ thống matching hiện tại (`MatchingDispatchService`) chỉ vận hành ở cấp **cá nhân**: mỗi ca (`Shift`) được dispatch thành N `DispatchProposal` gửi tới từng `Worker`. `WorkerGroup` hiện chỉ là một tag dữ liệu: `ShiftAssignment.groupId` được set bừa bằng `attachGroupInfoIfPresent()` (lấy `activeMemberships.get(0)`), không ảnh hưởng gì đến matching.

Hậu quả thực tế:
- EO muốn "đội 5 anh em setup sân khấu làm cùng nhau" → vẫn phải nhận 5 proposal rời rạc; đội dễ bị xé vụn (3 người của đội A, 2 người lạ).
- Trưởng nhóm không có quyền nhận việc thay cả đội.
- `WorkerGroup.availability` (jsonb) và `minSize` là dữ liệu chết — không service nào dùng.
- Nhóm "chết vĩnh viễn" khi leader rời/khóa vì chưa có chuyển giao quyền.

## 2. Mục tiêu / Không phải mục tiêu

**Mục tiêu (M):**
- M1: EO bật cờ `acceptGroupMode` khi tạo ca → hệ thống gửi **1 proposal cho mỗi group phù hợp** (không phải cho từng thành viên).
- M2: Trưởng nhóm (leader) là người đại diện **nhận ca cho cả đội**: chọn chính xác những member nào đi, all-or-nothing trong 1 transaction.
- M3: Mỗi member được chọn có `ShiftAssignment` riêng (giữ nguyên luồng check-in, thanh toán, no-show, đánh giá hiện có) — tag `groupId` + `source=GROUP` ghi đúng.
- M4: Matching cá nhân chạy **song song**, không loại trừ lẫn nhau.
- M5: Hủy 1 member của ca nhóm → tự re-dispatch slot còn thiếu (group + cá nhân).

**Không phải mục tiêu (không làm lần này):**
- Member opt-in cá nhân (leader chọn, member chỉ được thông báo) — giữ flow tối giản; nâng cấp khi có complaint thực tế.
- Invite expiry / reject lời mời nhóm, xóa group, sửa UI enum PENDING/REMOVED — tách task riêng (đề xuất đưa vào backlog, không block tính năng này).
- Định giá riêng cho ca nhóm (group premium) — vẫn dùng `hourlyWage`/`allowance` hiện có.

## 3. Hướng tiếp cận (đã chọn A)

| Hướng | Ý tưởng | Kết luận |
|---|---|---|
| **A — Group là đối tượng matching** | Dispatch ra GroupProposal, leader accept thay cả đội, transaction all-or-nothing | ✅ Chọn. Đúng ý đồ "giao cả ca cho đội", ~3 service + 1 entity + UI leader |
| B — Bonus cùng nhóm trên matching cá nhân | Giữ matching cá nhân, cộng điểm cho member cùng group | ❌ UX "giao cả ca cho đội" không đạt, leader không có quyền đại diện |
| C — Rewrite module group-matching tách hẳn | Schema + service mới độc lập | ❌ Over-engineered cho nhu cầu hiện tại |

## 4. Thay đổi data model (tối thiểu)

1. **`Shift`**: thêm `Boolean acceptGroupMode` (default `false`).
2. **`DispatchProposal`**: thêm `UUID groupId` (nullable) + dùng lại cột `source` (thêm enum value `GROUP`). Cột `scoreBreakdown` (json) lưu breakdown điểm nhóm. Không tạo table mới.
3. **`AssignmentSource`**: thêm value `GROUP`.
4. **`ShiftRepository`**: thêm `incrementConfirmedWorkersAtomicBy(UUID shiftId, int n)` —
   ```sql
   UPDATE shifts SET confirmed_workers = confirmed_workers + :n,
                     remaining_workers = GREATEST(0, remaining_workers - :n)
   WHERE id = :id AND confirmed_workers + :n <= required_workers
   ```
   Trả số dòng cập nhật; `0` = đã đủ người → reject accept. (Giới hạn: dùng cho cả ca thường lẫn ca group.)
5. **Migration**: 1 file Flyway `Vx__add_group_matching.sql` (alter shifts, dispatch_proposals; enum assignment_source + 'GROUP').

## 5. Luồng hệ thống

### 5.1 Dispatch (EO publish ca / emergency / re-dispatch)
Mở rộng `MatchingDispatchService`:
- `dispatchForShift` & `triggerEmergencyDispatch`: sau khi chạy matching cá nhân (giữ nguyên), nếu `shift.acceptGroupMode == true` → gọi `dispatchGroupsForShift(shift, radiusKm)`.
- `dispatchGroupsForShift` (method mới, cùng service để dùng lại `passHardFilter`):
  1. Lấy các `WorkerGroup` active (limit 50 theo `createdAt`/metric; shortcut: đủ dùng, nâng cấp bằng ranking DB khi số group lớn).
  2. Lọc: `isActive`, `memberCount(active) >= shift.requiredWorkers`, `reliabilityScore >= minReliabilityScore`, không bị block (đại diện bằng leader user), skill focus trùng `requiredSkills` (nếu có) — nếu group không khai skill thì coi là khớp (giống worker).
  3. **Eligibility theo thành viên**: batch lấy active memberships của các group, chạy lại từng bước hard filter theo member (skills, conflict giờ, availability, minWage, kyc, user status). Nhóm đủ khi `eligibleCount >= requiredWorkers`.
  4. **Điểm nhóm** (cùng công thức soft score hiện có, thay đổi đầu vào):
     - `distanceScore`: trung bình khoảng cách Haversine của các member **eligible** tới điểm ca (trọng 0.35).
     - `ratingScore`: `group.avgRating` (trọng 0.25).
     - `expScore`: trung bình `totalShiftsCompleted` của eligible members (trọng 0.2).
     - `rsScore`: `group.reliabilityScore` (trọng 0.1).
     - Bonus kỹ năng xác thực +10 (cùng worker), bonus "đội cũ" (group từng hoàn thành ≥1 ca cùng sự kiện/EO) +5.
  5. Xếp hạng, lấy top `min(groupCount, max(needed, 3))` group → tạo 1 `DispatchProposal`/group với `groupId`, `source=GROUP`, expires 1h (khẩn cấp: 30 phút), FCM cho **leader** (message khác: "Lời mời cho cả đội").

### 5.2 Accept (leader, all-or-nothing) — mới
Endpoint: `POST /api/v1/worker-groups/{groupId}/group-proposals/{proposalId}/accept` body: `{ "memberWorkerIds": [UUID, ...] }`

Quy trình (1 transaction):
1. Validate: caller là leader của group (hoặc ADMIN), proposal tồn tại, `source=GROUP`, `groupId` đúng, status PENDING, chưa hết hạn.
2. Validate `memberWorkerIds`: size == `shift.requiredWorkers`, tất cả là member **ACTIVE** của group, không trùng.
3. Re-check hard filter từng member tại thời điểm accept (conflict giờ mới phát sinh, user bị khóa…) → fail 1 là reject toàn bộ (transaction roll, không half-accept).
4. `incrementConfirmedWorkersAtomicBy(shiftId, N)`; `rows == 0` → "ca vừa đủ người".
5. Tạo N `ShiftAssignment`: `status=CONFIRMED`, `source=GROUP`, `groupId`, `isGroupLeader` đúng theo role; tạo payment ban đầu qua `DirectPaymentService` (giữ nguyên); cập nhật `Event.totalConfirmedWorkers += N`.
6. Notifications: EO ("Đội X nhận ca, 5/5 thành viên: danh sách"), từng member ("Bạn được [leader] phân công ca Y — đến trước HH:mm"), leader ("Đã nhận ca cho đội").

Reject: `POST .../reject` → proposal REJECTED → re-dispatch slot còn thiếu (cá nhân + group) theo `dispatchForShift`.

### 5.3 Hủy 1 member trong ca nhóm
Dùng nguyên `cancelAssignment` hiện có (phạt điểm uy tín, `decrementConfirmedWorkersAtomic`). Bổ sung: sau khi decrement, nếu `remainingWorkers > 0` → `dispatchForShift` + (nếu `acceptGroupMode`) `dispatchGroupsForShift` — re-dispatch tự động. Leader được notify "1 thành viên hủy ca".

### 5.4 Xem proposal nhóm (leader)
- `GET /api/v1/worker-groups/{groupId}/group-proposals` (PENDING + EXPIRED gần) — chỉ leader.
- Mobile: tab mới trong `worker_group_detail_screen`: "Lời mời ca làm cho đội" → sheet chọn member (checkbox, đúng bằng số cần) → accept.

## 6. Vấn đề sẽ gặp + hướng giải (đăng ký sẵn)

| # | Vấn đề | Giải pháp đã thiết kế |
|---|---|---|
| P1 | Race: 2 nhóm cùng accept 1 ca, hoặc accept group + accept cá nhân cùng lúc → overbooking | Atomic `UPDATE ... WHERE confirmed + n <= required` (mục 4.4) cho **cả** cá nhân lẫn nhóm; `rows==0` → từ chối |
| P2 | Leader accept mà member không có mặt (bận/vắng) | Penalties hiện có xử lý (no-show → reliability; tự hủy → trừ điểm, hủy sớm <12h = -20). Leader là người chịu trách nhiệm chọn member → hành vi tự nhiên là chỉ chọn người đã xác nhận bằng tin nhắn. Nếu member no-show: leader báo EO → EO dùng re-dispatch (M5) thay member |
| P3 | Conflict giờ phát sinh giữa lúc proposal tạo → leader accept | Re-check conflict (`findConflictingAssignments`) từng member trong transaction accept (mục 5.2.3); fail → toàn bộ reject, leader chọn lại hoặc reject proposal |
| P4 | Leader bị khóa/rời giữa chừng, proposal chết; nhóm "vô chủ" | (a) Proposal có `expiresAt` + scheduler EXPIRED hiện có — không treo. (b) Thêm endpoint **chuyển giao leader** (`POST /worker-groups/{id}/transfer-leader`) — scope nhỏ, kèm trong task này vì là tiền đề sống còn của nhóm: đổi `leaderWorkerId`, đổi membership role, giữ metrics |
| P5 | 1 member nhận 2 ca nhóm cùng giờ (thuộc 2 nhóm) | Re-check conflict ở bước accept (P3) chặn cứng; matching cá nhân cũng đã có filter này |
| P6 | Worker nhận được cả proposal cá nhân lẫn proposal nhóm cùng ca →混乱 | Chấp nhận: 2 kênh song song (M4). Accept kênh nào, kênh kia tự mất hiệu lực qua conflict check + atomic counter. Notification phân biệt rõ nhãn "[NHÓM]" |
| P7 | N+1: group nhiều × member nhiều × filter | Batch: 1 query lấy active memberships của các candidate group, map member→Worker, reuse cached worker list trong service. Cap 50 group × 50 member = 2500 worker xét — cùng bậc với `findByIsOnlineTrue` hiện có. Nếu hot, cache worker trong 1 request |
| P8 | `avgRating`/`reliabilityScore` của group chỉ refresh khi join/leave — score cũ khi member mới được review | Chấp nhận: refresh hiện có + thêm gọi `recalculateGroupMetrics` khi complete ca (1 dòng trong luồng checkout) — rẻ, sai số giảm dần |
| P9 | Emergency dispatch: EO cần người gấp, chờ leader phản hồi 1h? | Emergency: group proposal expires 30 phút (theo design), cap top 3 group; cá nhân vẫn chạy song song nên không block hoàn toàn |
| P10 | `attachGroupInfoIfPresent` ghi group sai (bug hiện có, issue #3) | Sửa: chỉ set `groupId` khi `source == GROUP` (từ proposal), không bừa `get(0)`. Với source cá nhân để null |
| P11 | Shift `requiredWorkers` > mọi group khả năng (group nhỏ) → dispatch group vô nghĩa | Lọc `eligibleCount >= requiredWorkers` (mục 5.1.3): không đủ → không gửi proposal group, log ra; cá nhân bù |
| P12 | Thanh toán: ca nhóm nhưng payment vẫn per-member — leader có muốn nhận "tiền cả đội" 1 lần? | **Ngoài scope v1** (mục 2): giữ payment per-member như cá nhân; leader tự chia trong nhóm. Ghi rõ trong acceptance criteria |

## 7. API contract (tóm tắt)

```
POST /worker-groups/{groupId}/group-proposals/{proposalId}/accept
  Body: { "memberWorkerIds": ["uuid", ...] }   // size bắt buộc == shift.requiredWorkers
  200 -> GroupProposalAcceptResponseDto { assignmentIds[], shiftId, memberCount }
  409 -> "Ca vừa đủ người trước khi bạn xác nhận"
  400 -> "Member X có ca trùng / không đủ thành viên / không phải thành viên ACTIVE"

POST /worker-groups/{groupId}/group-proposals/{proposalId}/reject
  Body: { "reason": "..." }

GET  /worker-groups/{groupId}/group-proposals  // chỉ leader; PENDING + EXPIRED 24h
GET  /worker-groups/{groupId}/group-proposals/members-eligible?proposalId=..
  -> danh sách member ACTIVE + eligible/not-eligible + lý do (để UI checklist)

POST /worker-groups/{groupId}/transfer-leader
  Body: { "newLeaderWorkerId": "uuid" }
```

DTO mới: `GroupProposalResponseDto` (shift info, memberCount cần, điểm nhóm, breakdown, expiresAt, danh sách eligible members).

## 8. Acceptance criteria (test)

1. Ca `requiredWorkers=3, acceptGroupMode=true`, group G (leader L, 5 member, 4 eligible) → đúng 1 GroupProposal tới L; 1 member không eligible không nhận proposal đại diện.
2. L accept 3 member → `confirmed_workers=3`, 3 assignments `source=GROUP`, `groupId=G`; EO + 3 member nhận notification; payment records tạo.
3. Accept khi ca còn 2 slot → 409, không data nửa vời (rollback kiểm chứng: `confirmed_workers` không đổi).
4. 1 member hủy sau accept → slot re-dispatch (cá nhân + group), leader được báo.
5. 2 group cùng accept cùng ca song song (kịch bản race) → đúng 1 bên thắng atomic, bên kia 409.
6. Member có ca trùng giờ khác → accept bị 400 toàn bộ (không half-accept).
7. Transfer leader: proposal group cũ EXPIRED đúng hạn; leader mới nhận được proposal mới; `leaderWorkerId` đổi, role membership đổi.
8. Ca `acceptGroupMode=false` → hành vi matching cá nhân 100% như trước (regression).

## 9. Scope code (file cần chạm)

- BE: `Shift`, `DispatchProposal`, `AssignmentSource` (enum), `ShiftRepository` (+1 method), `MatchingDispatchService` (+`dispatchGroupsForShift`), `WorkerGroupService` (accept/reject/transfer + endpoints mới trong `WorkerGroupController`), `WorkerJobService` (re-dispatch on cancel, sửa `attachGroupInfoIfPresent`), 1 migration SQL, ~3 test class.
- FE (Flutter): model `GroupProposalModel` + provider methods (accept/reject/get eligible), 1 tab mới trong `worker_group_detail_screen`, sheet chọn member; toggle `acceptGroupMode` trong form tạo ca (app EO — `mobile/lib/features/event*`).
- UI enum status member (issue #8) — **ngoài scope v1**, backlog.

## 10. Thứ tự build đề xuất (gợi ý plan)

1. Migration + entity/enum + atomic method (thử nghiệm unit riêng) → verify: test 3, 5
2. `dispatchGroupsForShift` + DTO + endpoint list/get → verify: test 1
3. Accept/reject flow + notifications + payments → verify: test 2, 4, 6
4. Re-dispatch on cancel + emergency → verify: test 4, 9
5. Transfer leader → verify: test 7
6. Flutter: leader UI + EO toggle → verify: manual smoke
7. Regression matching cá nhân → verify: test 8
