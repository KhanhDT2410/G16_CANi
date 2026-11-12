# REVIEW DOMAIN TÌM KIẾM SỰ KIỆN / JOB RADAR — CANi
> Ngày: 12/11/2026 · Scope: backend + web + mobile · **mọi claim trọng yếu đã được verify lại trên code thật bởi reviewer trực tiếp** (không chỉ dựa vào subagent).

---

## 🔴 CRITICAL

### C1 — `requiredSkills` được set nhưng KHÔNG BAO GIỜ được đọc → worker thiếu kỹ năng vẫn được dispatch
- **Entity:** `backend/.../entity/Shift.java:88-89` · **Writer:** `EoEventService.java:201` · **Schema:** `V1__init_schema.sql:192` (`required_skills UUID[]`)
- **Đã verify:** grep toàn repo `requiredSkills|required_skills` chỉ trả về đúng 4 chỗ nêu trên — **không nơi nào đọc**. `MatchingDispatchService.passHardFilter` kiểm tra isOnline / UserStatus.ACTIVE / minReliabilityScore / minExperienceLevel / findConflictingAssignments; `WorkerJobService.applyForShift` cũng không.
- **Kịch bản:** EO đăng ca "Lễ tân VIP" chọn `requiredSkills=[Lễ tân, Ngoại ngữ]` → matching dispatch cho worker chỉ có skill "Phục vụ bàn". Ca VIP đến tay worker không đủ kỹ năng, trong khi `skill` field của worker vẫn hiện trên UI như đã được kiểm tra.
- **Fix:** 1 khối trong `passHardFilter` + `applyForShift`: `shift.getRequiredSkills() != null && length>0` → yêu cầu intersection ≥1 với skill của worker (cần chốt all-of hay any-of với sản phẩm).

### C2 — `calculateDistance` trả **3.0 km giả** khi thiếu toạ độ → worker "không rõ vị trí" đứng đầu radar
- **File:** `MatchingDispatchService.java:271-274`
```java
if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
    return 3.0; // Khoảng cách giả định nếu thiếu tọa độ
}
```
- **Đã verify chuỗi đầy đủ:** (1) `WorkerJobService.setOnlineStatus` không bắt buộc có toạ độ → `baseLat/baseLng = null`; (2) `dispatchForShift:111` truyền `worker.getBaseLat()` null → **3.0 km**; (3) `getNearbyShifts:438` — shift thiếu `venueLat` cũng ra 3.0 km; (4) `WorkerJobService:467-468` trả shift thiếu toạ độ với `locationLat = centerLat` **của worker** → **fake toạ độ giả mạo shift**.
- **Kịch bản:** Worker ở Hà Nội bật online không khai toạ độ → distance=3.0km → distanceScore ≈ 80 → được xếp ngang các worker thật sự ở gần; đồng thời ca ở TP.HCM hiện trên radar của worker Hà Nội với label "3.0 km". 50 worker không khai toạ độ = 50 proposal đồng loạt đứng đầu.
- **Fix:** trả `Double.MAX_VALUE` (để bị filter) hoặc `maxAllowedDistanceKm + 0.1`; và ở `getNearbyShifts` **loại shift thiếu toạ độ** thay vì fill bằng toạ độ worker.

---

## 🟠 HIGH

### H1 — `idleBonus = 10.0` hardcode cho MỌI worker → hằng số vô nghĩa, comment nói ngược code
`MatchingDispatchService.java:248-252`: comment ghi "nghỉ lâu (> 7 ngày)" nhưng code gán `10.0` **không điều kiện**. Mọi worker cộng đều +10 → không đổi thứ hạng, chỉ phồng điểm (max ≈ 130). `Worker` không có field `lastActiveAt` nên chưa có dữ liệu để tính idle. **Fix:** thêm field last-active, hoặc xoá hẳn khỏi công thức + comment.

### H2 — `distanceScore` chia cho `MAX_DISTANCE_KM=15` cứng, trái bán kính thật của shift
`MatchingDispatchService.java:41,92-94,235`: đã tính `maxAllowedDistanceKm` từ `shift.getMaxDistanceKm()` nhưng `calculateSoftScore` vẫn chia 15. Shift bán kính 5km → worker 4.9km có score ≈ 1 (gần bằng bị filter); shift bán kính 2km → worker 1.9km score 87, ngang worker 1km (93) → trọng số 0.4 mất ý nghĩa. **Fix:** truyền `maxAllowedDistanceKm` vào `calculateSoftScore`.

### H3 — `dispatchForShift` giữ `@Transactional` trong khi gọi FCM blocking cho từng worker
`MatchingDispatchService.java:71,133-168` + `FcmService.java:118` (`sendEachForMulticast` — HTTP blocking tới Firebase). Ca cần 5 người → dispatch tới 13 worker × ~200ms = **~2.6s giữ transaction mở**; `publishEvent` lặp theo từng shift của event → nhân lên. Web demo bấm "Kích hoạt matching" cho 4-5 ca → thread pool bão hoà, mọi request radar/apply chờ lock. **Fix:** tách FCM ra khỏi tx (sau commit / `@Async` / event).

### H4 — Radar load TOÀN BỘ shift ACTIVE, Haversine trong Java → O(N) mỗi lần mở màn hình
`WorkerJobService.java:431` (`findByStatus(ACTIVE)`, không phân trang) + `:438` (Haversine từng shift) + truy `s.getEvent()` (lazy load → N+1). 1000 shift ACTIVE = 1000 lần Haversine + 1000 lần query event mỗi lần scan. **Fix:** lọc trong SQL `WHERE status='ACTIVE' AND start_time >= now` + `LIMIT`; index `idx_shifts_status_time` (V7) đã có sẵn.

### H5 — Radar KHÔNG lọc ca đã đủ người → hiện ca FULLY_STAFFED rồi bấm Apply mới báo lỗi
`getNearbyShifts` chỉ filter ACTIVE, không đọc `remainingWorkers`. `incrementConfirmedWorkersAtomic` set `status=FULLY_STAFFED` khi đủ, nhưng radar không lọc theo status → worker thấy "Cần: 0/4", bấm Apply → chặn. **Fix:** 1 dòng filter `remainingWorkers > 0`.

### H6 — Không đường nào kiểm tra eKYC của worker (dispatch / apply / accept)
`Worker.kycStatus` default `PENDING`; grep `kycStatus` trong `MatchingDispatchService` + `WorkerJobService` = **0 kết quả**. Worker mới đăng ký, chưa verify CCCD → bật online → nhận proposal → apply → check-in ca. **Fix:** chặn `kycStatus != VERIFIED` trong `passHardFilter` + `applyForShift` + `acceptProposal` (cần chốt với sản phẩm: PENDING được apply nhưng không được dispatch?).

### H7 — `dispatch_proposals` KHÔNG có UNIQUE (shift_id, worker_id) → trùng proposal
`V1__init_schema.sql:248-259` không có unique; `V7` chỉ tạo **index thường** (`idx_dispatch_proposals_shift_worker`) — index không chặn duplicate. `MatchingDispatchService:136` check-then-create → 2 lần publish song song (web + mobile, hoặc retry) → 2 proposal cùng worker. Worker accept cái 1 (atomic increment chặn double-booking ✅) nhưng proposal 2 treo PENDING. **Fix:** migration `ADD CONSTRAINT uq_dp_shift_worker UNIQUE (shift_id, worker_id)`.

### H8 — Web không có UI worker → `findByIsOnlineTrue()` luôn rỗng → dispatch fallback load TOÀN BỘ bảng worker
`web/src/App.jsx:59` (`/worker` → redirect `/login`); grep `workerService` trong web: **không page nào import**. Vì web không có worker UI, không ai bật `isOnline` → `MatchingDispatchService:97` rỗng → **fallback `findAll()` (line 99)** = load toàn bộ bảng workers vào RAM mỗi lần publish, rồi score từng người. **Fix:** hoặc tạo trang Worker Dashboard web, hoặc (bắt buộc trước demo) **bỏ fallback `findAll()`** để không bao giờ quét toàn bộ bảng.

### H9 — 🔥 `EORecruitmentTab` gọi `eoService.dispatchForShift` — hàm KHÔNG TỒN TẠI → nút "Kích hoạt Matching" crash
**Đã verify trực tiếp:** `web/src/pages/eo/components/EORecruitmentTab.jsx:71` → `await eoService.dispatchForShift(shift.shiftId)`; grep `dispatchForShift` trong `web/src/services/eoService.js` → **không tồn tại** (file chỉ có `triggerEmergencyBroadcast` gọi `/emergency-dispatch`). → `TypeError: eoService.dispatchForShift is not a function` → catch → toast "Lễ khi kích hoạt Matching". **Fix (5 dòng):**
```js
dispatchForShift: async (shiftId) => {
  const response = await apiClient.post(`/v1/eo/events/shifts/${shiftId}/publish`);
  return response.data?.data;
},
```

### H10 — Web fallback demo data khi API lỗi → hiển thị dữ liệu giả giống thật trước khách
`web/src/services/workerService.js:10,74` + `web/src/pages/eo/hooks/useEvents.js:34` (`DEMO_EVENTS`). Backend lỗi (401 hết hạn token, CORS, mạng) → UI tự hiện sự kiện/lương/proposal mẫu. **Fix:** bỏ fallback, hoặc chỉ fallback khi `import.meta.env.DEV` **và** không phải lỗi 401/403.

### H11 — Mobile `JobRadarState.copyWith` không có sentinel → không reset được field về null
`mobile/.../job_radar_provider.dart:36-53`: `selectedShift`, `errorMessage`, `locationName` copy thẳng giá trị cũ → `copyWith(errorMessage: null)` **giữ error cũ** → lỗi cũ không bao giờ xoá được, hiển thị lại sau mỗi lần scan. **Fix:** dùng sentinel `Object?` cho 3 field đó.

### H12 — Mobile `applyForShift` demo mode nuốt mọi lỗi (silent success)
`mobile/.../job_radar_repository.dart:75` (`if (demoMode) return;`) → cả lỗi thật ("ca đã đủ người") cũng bị nuốt, UI báo thành công. Kèm `job_radar_models.dart:41-50`: backend trả `locationLat=null` → mobile tự fill `10.7769/106.7009` → che dấu "shift không có toạ độ" (cùng gốc C2). **Fix:** demoMode chỉ nuốt lỗi network/5xx; giữ `null` toạ độ và hiển thị "không xác định vị trí".

---

## 🟡 MEDIUM

- **M1 — Proposal PENDING hết hạn không ai dọn:** `@Scheduled` toàn repo chỉ có `NoShowService:184` + `SlaMonitoringService:55` (đã verify). `ProposalStatusUpdater` chỉ set EXPIRED **khi worker accept sau hạn** (`WorkerJobService:115-117`) → proposal quá hạn mà không ai bấm accept nằm vĩnh viễn trong DB. **Fix:** scheduled job 15 phút expire PENDING quá hạn.
- **M2 — `event.totalConfirmedWorkers` cập nhật kiểu read-then-`+1` → lost update.** `WorkerJobService:169,544,667` đọc field rồi cộng, **không atomic** (khác hẳn `incrementConfirmedWorkersAtomic` của shift). 2 worker apply cùng event song song → mất 1 lượt đếm. **Fix:** thêm `@Modifying` query `incrementTotalConfirmedWorkers(eventId)`.
- **M3 — Mobile radar không có pull-to-refresh, không poll:** grep `RefreshIndicator|onRefresh|poll` trong `job_radar_screen.dart` = không có. Worker đang mở radar không thấy ca vừa publish → phải thoát vào lại app. **Fix:** bọc list bằng `RefreshIndicator` gọi `scanNearbyShifts()`.
- **M4 — EO không có nút "Từ chối" ứng viên:** `EoEventController` chỉ có `POST /assignments/{id}/verify` (duyệt hoàn thành), **không có endpoint reject assignment**. Worker apply từ radar → assignment `CONFIRMED`, `eoConfirmed=false` → EO không có cách loại worker ngoài hệ thống. **Fix:** thêm endpoint `rejectAssignment` + nút trong RecruitmentTab/EventDetailModal.
- **M5 — Mobile demoMode không guard `kDebugMode`:** `job_radar_repository.dart:17` dùng `bool.fromEnvironment('DEMO_MODE', defaultValue:false)` — build release với define là bật demo. **Fix:** `kDebugMode && bool.fromEnvironment('DEMO_MODE')`.

## 🟢 LOW
- `workerService.getMyProposals` demo data có `matchScore`/`scoreBreakdown` dạng string trong khi backend trả `matchScore` là JSON number → lệch contract nếu ai đó bật.
- `job_radar_repository.dart:57-59`: `setOnlineStatus` khi lat/lng null → hardcode `10.7769/106.7009` (Q7) → worker ở Hà Nội vẫn "ở" Q7 → góp vào C2.

---

## Đã kiểm tra và SẠCH

| Hạng mục | Kết quả (có bằng chứng) |
|---|---|
| **Chống overbooking** | `incrementConfirmedWorkersAtomic` (`ShiftRepository:24`) UPDATE atomic `WHERE confirmedWorkers < requiredWorkers`, tự set FULLY_STAFFED — **đúng** |
| **Chống worker 2 ca trùng giờ** | `findConflictingAssignments` ở cả dispatch, apply, accept — **đúng** |
| **Payment luôn đi kèm assignment** | `applyForShift` + `acceptProposal` gọi `createInitialPayment` **trong cùng transaction** → payment lỗi thì assignment rollback. **Không có assignment mà không có payment** |
| **Unique payments.assignment_id** | ✅ **ĐÃ CÓ** — `Payment.java:16-21` khai báo `uniqueConstraints` + migration `V25__add_unique_constraint_to_payments_assignment_id.sql` + catch `DataIntegrityViolationException` trong `DirectPaymentService:93-99` |
| **EO có quyền duyệt** | `verifyAttendance` (`EoEventService:335-362`) set `eoConfirmed=true` + COMPLETED, chống duyệt 2 lần; assignment bắt đầu `CONFIRMED, eoConfirmed=false` → EO **có** quyền kiểm soát (thiếu nút "từ chối" là vấn đề UX, xem M4) |
| **Proposal status update** | `ProposalStatusUpdater` REQUIRES_NEW (commit độc lập) — chống lost update |

## 3 việc CHẶN DEMO

1. **H9** — nút "Kích hoạt Matching Radar" crash (`dispatchForShift is not a function`). Thêm 5 dòng vào `eoService.js` là hết. **Đây là lỗi chắc chắn gặp khi demo.**
2. **C2 + H5** — worker không toạ độ bị coi là "gần 3km" + radar hiện ca đã đủ người. Sửa 1 dòng `calculateDistance` + 1 dòng filter `remainingWorkers > 0`. Sửa 2 dòng, giảm ngay 2 lỗi hiển thị sai trước khách.
3. **H8** — web không có worker UI nên `findByIsOnlineTrue()` rỗng → `findAll()` quét toàn bảng worker mỗi lần publish. Demo chỉ dùng mobile cho worker thì **nên xoá fallback `findAll()`** để không bao giờ quét full table.

## Điểm sáng

Pipeline "worker apply → assignment + payment record + conflict check" được thiết kế **chặt và đúng**: atomic increment chống overbooking, conflict check chống trùng ca, payment tạo trong cùng transaction (không bao giờ có assignment mồ côi), unique constraint payments đã có, `verifyAttendance` chống duyệt trùng. Nền tảng tốt — thiếu là **"đầu vào"** (skill/eKYC/hard filter) và **"đầu ra"** (expire + reject của EO).
