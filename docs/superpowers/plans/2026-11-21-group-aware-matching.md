# Group-aware Matching — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Matching (thường + emergency) luôn xét cả worker group song song kênh cá nhân; leader accept k thành viên (mixed fill, all-or-nothing, atomic counter); re-dispatch + cooldown + transfer-leader; UI Flutter worker/EO.

**Architecture:** Kênh group là hàm mới `dispatchGroupsForShift` chạy bên trong `dispatchForShift`/`triggerEmergencyDispatch` (hook 1 dòng mỗi nơi). 1 `DispatchProposal`/group, lưu leader vào `worker` (cột `worker_id` NOT NULL), thêm `groupId` nullable. Accept = endpoint mới trên `WorkerGroupService` với atomic N-increment + re-check eligibility từng member. Không thêm cột trên `Shift`, không mode/flag.

**Tech Stack:** Java 17, Spring Boot (Lombok, JPA, Flyway, PostgreSQL), JUnit 5 + Mockito, Flutter + Riverpod + Dio.

**Spec:** `docs/superpowers/specs/2026-11-21-worker-group-matching-design.md` (v2)

## Global Constraints

- Không sửa hard filter kênh cá nhân (giữ `isOnline`); eligibility kênh group = eKYC APPROVED + user ACTIVE + conflict + skills + minWage + availability — **không** `isOnline`.
- `AssignmentSource.GROUP` đã có trong enum — không thêm. `Shift` không thêm cột.
- Cap: top 50 group xét, top 5 group nhận proposal. Expiry: thường 1h, emergency 30p. Cooldown no-show: 20 phút.
- `DispatchProposal.worker` NOT NULL → proposal group lưu **leader** của group vào `worker`, `groupId` = group.
- k chấp nhận: `1 ≤ k ≤ claimableSlots`, `claimableSlots = min(eligibleCount, remainingWorkers)`.
- Test chạy: `cd backend && ./gradlew test --tests "..."` (Maven/Gradle theo project; hiện dùng Maven: `mvn test -Dtest=...`).
- Migration số tiếp theo: **V42** (V41 là cuối).

## Review Focus

1. **Race slot cuối** — 2 group / group + cá nhân accept song song cùng shift: đúng 1 bên thắng atomic counter, bên kia 409, không có trạng thái `confirmed > required`. → test trong Task 2 (unit repo) + Task 3 (accept 409).
2. **Member conflict phát sinh sau dispatch** (trên đường đi nhận ca khác, user bị khóa giữa dispatch → accept): accept phải re-check từng member, 1 fail = toàn bộ reject. → Task 3 test "1 member conflict → 400, 0 assignment".
3. **Group no-show rồi ngay lập tức re-dispatch (no-show gọi `dispatchForShift`) vẫn được chọn top group** — cooldown phải lọc trước khi xếp top. → Task 4 test.
4. **Proposal group của leader cũ sau transfer-leader** — phải EXPIRED, leader mới nhận proposal mới; accept của leader cũ sau transfer → 403/404. → Task 5 test.
5. **Group không có member nào eligible** — không gửi proposal, không exception; shift thiếu người vẫn fill qua kênh cá nhân. → Task 2 test.

---

### Task 1: Migration V42 + atomic N-increment + repository group queries

**Files:**
- Create: `backend/src/main/resources/db/migration/V42__dispatch_proposal_group_id.sql`
- Modify: `backend/src/main/java/com/cani/entity/DispatchProposal.java` (+`groupId`)
- Modify: `backend/src/main/java/com/cani/repository/ShiftRepository.java` (+`incrementConfirmedWorkersAtomicBy`)
- Modify: `backend/src/main/java/com/cani/repository/DispatchProposalRepository.java` (+2 query group-based)
- Test: `backend/src/test/java/com/cani/repository/ShiftRepositoryTest.java` (tạo mới nếu chưa có; pattern: `@DataJpaTest` H2 hoặc mock-native-query test theo pattern repo hiện có)

**Interfaces:**
- Produces: `int ShiftRepository.incrementConfirmedWorkersAtomicBy(UUID shiftId, int n)`; `UUID DispatchProposal.getGroupId()`; `List<DispatchProposal> DispatchProposalRepository.findByShift_ShiftIdAndGroup_GroupIdAndStatusIn(UUID shiftId, UUID groupId, List<ProposalStatus> statuses)`; `List<DispatchProposal> DispatchProposalRepository.findByGroup_GroupIdAndStatus(UUID groupId, ProposalStatus status)`.

- [ ] **Step 1: Migration + entity**

SQL V42:
```sql
ALTER TABLE dispatch_proposals ADD COLUMN group_id UUID NULL;
CREATE INDEX idx_dispatch_proposals_group ON dispatch_proposals (group_id, status);
```
`DispatchProposal`: thêm trường `@Column(name = "group_id") private UUID groupId;`

- [ ] **Step 2: Atomic N-increment (native query)**

```java
@Modifying
@Query(value = "UPDATE shifts SET confirmed_workers = confirmed_workers + :n, "
     + "remaining_workers = GREATEST(0, remaining_workers - :n) "
     + "WHERE id = :shiftId AND confirmed_workers + :n <= required_workers",
     nativeQuery = true)
int incrementConfirmedWorkersAtomicBy(@Param("shiftId") UUID shiftId, @Param("n") int n);
```
Kiểm tra tên bảng/cột thật của `shifts` (cột `id` theo `Shift.java`: `shift_id`, không phải `id` — entity annotate `@Column(name = "shift_id")`) trong migration gốc trước khi commit; H2 mode PostgreSQL cho test.

- [ ] **Step 3: Repository queries** — 2 method `DispatchProposalRepository` ở trên (derived query, không cần @Query).

- [ ] **Step 4: Test** — test `ShiftRepositoryTest` (hoặc Mockito test cho N-increment nếu dùng native + `@DataJpaTest` H2): `n=3, confirmed=4, required=6 → 1 row`; `n=3, confirmed=5, required=6 → 0 row`; sau thành công `remainingWorkers = GREATEST(0, old - n)` chính xác.

- [ ] **Step 5: Commit** — `feat(db): V42 dispatch_proposals.group_id + atomic N-increment shift counters`

### Task 2: Kênh group trong dispatch (`dispatchGroupsForShift`)

**Files:**
- Modify: `backend/src/main/java/com/cani/service/MatchingDispatchService.java`
- Test: `backend/src/test/java/com/cani/service/MatchingDispatchServiceTest.java` (mở rộng test có sẵn nếu có)

**Interfaces:**
- Consumes: Task 1 (groupId, query repo), `WorkerGroupRepository.findAll()`, `WorkerGroupMembershipRepository` (query ACTIVE memberships batch theo groupIds).
- Produces: `int MatchingDispatchService.dispatchGroupsForShift(UUID shiftId, boolean emergency)` — gọi từ cuối `dispatchForShift` (emergency=false) và trong `triggerEmergencyDispatch` (emergency=true), trả số proposal group tạo; hook = 1 dòng mỗi nơi.

- [ ] **Step 1: Test trước** — trong `MatchingDispatchServiceTest` thêm 4 test (mock repo, Mockito style của project):
  - `dispatch_groups_creates_one_proposal_per_eligible_group_top5`: group có ≥1 member eligible (eKYC APPROVED, user ACTIVE, không conflict) → 1 proposal, `worker` = leader, `groupId` set, `status PENDING`, `source GROUP`, `claimableSlots` (lưu trong breakdown json) = min(eligible, remaining); top 5 group theo điểm; FCM gửi leader (mock `notificationService`).
  - `dispatch_groups_skips_group_with_zero_eligible_members`: group 4 member nhưng tất cả conflict/không eKYC → 0 proposal, không exception.
  - `dispatch_groups_ignores_is_online`: member `isOnline=false` nhưng đủ điều kiện khác → vẫn eligible.
  - `dispatch_groups_emergency_expiry_30min`: emergency=true → `expiresAt` = now+30p (thường = now+1h).

- [ ] **Step 2: Implement**

`dispatchGroupsForShift(UUID shiftId, boolean emergency)`:
- Lọc: `shift.getStatus()==ACTIVE`, `remainingWorkers>0` (không thì return 0).
- Lấy top ~50 group active (`workerGroupRepository.findAllByIsActiveTrue` — thêm derived method nếu thiếu; ponytail: `findAll()` + filter in-memory nếu <1k group, `shortcut: cap 50, dùng query rank DB khi group > hàng nghìn`).
- Exclusion: leader bị block EO (reuse `blockListService.isEitherBlocked`), group trong cooldown (Task 4, hook: `group.getLastNoShowAt()` null hoặc cách đây >20p), shift đã có proposal group PENDING/ACCEPTED → bỏ (query Task 1).
- Per group: batch memberships ACTIVE → per-member check (eKYC APPROVED, user ACTIVE, không `findConflictingAssignments`, skills containsAll nếu shift có `requiredSkills`, minWage, availability — **trừ `isOnline`**); distance trung bình eligible (Haversine static có sẵn, group không có GPS → dùng base GPS leader).
- Điểm nhóm: distance 0.35 + `group.avgRating` 0.25 + exp TB eligible 0.2 + `group.reliabilityScore` 0.1 + bonus skill verified +10 + bonus "từng làm cùng EO" +5 (không có dữ liệu mối quan hệ EO-group → bỏ bonus này, `shortcut: chờ bảng event_group_history, v1.1`).
- Top 5 → save proposal (`groupId`, `worker`=leader, `matchScore`, breakdown json chứa `eligibleMemberWorkerIds`, `claimableSlots`, `eligibleCount`), FCM leader nhãn "Lời mời cho đội [Tên] — cử 1 đến [claimableSlots] thành viên" (emergency: tiền tố "🚨 GẤP").

Hook: cuối `dispatchForShift` trước `return createdCount`: `createdCount += dispatchGroupsForShift(shiftId, false);` (cẩn thận: `remainingWorkers` đã được kênh cá nhân increment chưa? — kênh cá nhân chỉ tạo proposal, chưa increment; increment xảy ra lúc accept. An toàn.) Trong `triggerEmergencyDispatch`: tương tự, `emergency=true`, expiry 30p.

- [ ] **Step 3: Chạy test xanh + commit** — `test(matching): group dispatch channel alongside individual`

### Task 3: Accept / reject / eligible / list (leader) — all-or-nothing

**Files:**
- Create: `backend/src/main/java/com/cani/dto/group/GroupProposalResponseDto.java`, `GroupProposalAcceptRequestDto.java`
- Modify: `backend/src/main/java/com/cani/service/WorkerGroupService.java`, `backend/src/main/java/com/cani/controller/WorkerGroupController.java`
- Test: `backend/src/test/java/com/cani/service/worker/WorkerGroupServiceTest.java` (mở rộng)

**Interfaces:**
- Consumes: Task 1 (`incrementConfirmedWorkersAtomicBy`, `DispatchProposal.groupId`), Task 2 (proposal shape), `DirectPaymentService` (tạo payment per member, theo cách `WorkerJobService.acceptProposal` làm cho cá nhân), `ShiftAssignmentRepository`.
- Produces (service):
  - `List<GroupProposalResponseDto> listGroupProposals(UUID callerUserId, UUID groupId)` — leader của group (hoặc ADMIN); PENDING + EXPIRED 24h.
  - `GroupProposalResponseDto getEligibleMembers(UUID callerUserId, UUID groupId, UUID proposalId)` — members ACTIVE + flag eligible + lý do (kết quả re-check, không tạo data).
  - `AcceptGroupProposalResponseDto acceptGroupProposal(UUID callerUserId, UUID groupId, UUID proposalId, List<UUID> memberWorkerIds)`
  - `void rejectGroupProposal(UUID callerUserId, UUID groupId, UUID proposalId, String reason)`
  - Endpoint controller (theo spec §7): `GET /api/v1/worker-groups/{groupId}/group-proposals`, `GET .../group-proposals/eligible?proposalId=`, `POST .../group-proposals/{id}/accept`, `POST .../group-proposals/{id}/reject`. `@PreAuthorize("hasAnyRole('WORKER','ADMIN')")`, validate caller là leader.

`GroupProposalResponseDto`: shift info (jobType, start/end, lương, còn bao nhiêu slot), `claimableSlots`, `eligibleMembers[]` (workerId, fullName, eligible, reasonCode), `expiresAt`, `matchScore`, `emergency`.

- [ ] **Step 1: Test trước** (Mockito, theo style `WorkerGroupServiceTest` có sẵn):
  - `accept_group_proposal_creates_k_assignments_source_GROUP`: k=2 member → 2 `ShiftAssignment` (`status CONFIRMED`, `source GROUP`, `groupId`, `isGroupLeader` đúng theo role membership), shift `confirmed += 2`, `remaining -= 2`, Event counter += 2, payment per member tạo, notify EO + member + leader.
  - `accept_k_exceeds_claimable_slots_rejects_400`: k > min(eligible, remaining) → `BadRequestException`, 0 assignment.
  - `accept_atomic_counter_rejects_409`: mock `incrementConfirmedWorkersAtomicBy` trả 0 → `ConflictException` (kiểm tra class exception có sẵn trong `com.cani.exception`; nếu thiếu, dùng `BadRequestException` với mã 409 — theo convention exception hiện có), không có assignment nào persisted (verify).
  - `accept_one_member_conflict_rejects_all`: 1/2 member conflict → toàn bộ fail, 0 assignment (rollback).
  - `accept_by_non_leader_rejects`: caller không phải leader group → `AccessDeniedException`/`ForbiddenException` (theo class có sẵn).
  - `reject_group_proposal_marks_expired_and_triggers_redispatch`: proposal → EXPIRED (REJECTED nếu enum có), gọi `dispatchForShift` (mock verify).
  - `list_group_proposals_leader_only_pending_and_expired_24h`.

- [ ] **Step 2: Implement**

`acceptGroupProposal` (1 transaction `@Transactional`):
1. Leader check (membership LEADER của caller trong group, hoặc ADMIN).
2. Proposal: PENDING, đúng group, `expiresAt` chưa qua, shift ACTIVE, chưa FULLY_STAFFED.
3. k = `memberWorkerIds.size()`; dedup; mọi id phải là membership ACTIVE của group; `1 ≤ k ≤ claimableSlots` (tính lại `remainingWorkers` DB thật).
4. Re-check eligibility từng member (hàm riêng dùng lại logic Task 2 — extract thành `isMemberEligibleForShift(Worker, Shift, boolean requireOnline=false)` để cả kênh group dispatch + accept dùng chung; cá nhân tiếp tục dùng requireOnline=true qua hard filter có sẵn).
5. `incrementConfirmedWorkersAtomicBy(shiftId, k)`; rows==0 → conflict exception (409), rollback.
6. k × (ShiftAssignment + DirectPayment + counter event + FCM).

**Ponytail note:** re-dispatch sau reject/cancel đã có sẵn ở `WorkerJobService.cancelAssignment:913` và `NoShowService.markNoShow:147` (cả hai gọi `dispatchForShift`) — Task này chỉ cần `rejectGroupProposal` gọi `dispatchForShift(shiftId)` 1 dòng.

- [ ] **Step 3: Chạy test xanh + commit** — `feat(groups): leader accept/reject group shift proposals (all-or-nothing, atomic slots)`

### Task 4: Cooldown no-show 20p + invalidation scheduler

**Files:**
- Modify: `backend/src/main/java/com/cani/entity/WorkerGroup.java` (+`lastNoShowAt`), migration (bổ sung vào file V42 nếu Task 1 chưa commit, ngược lại tạo **V43__worker_group_last_no_show_at.sql**: `ALTER TABLE worker_groups ADD COLUMN last_no_show_at TIMESTAMPTZ NULL`)
- Modify: `backend/src/main/java/com/cani/service/NoShowService.java` (line ~147, sau khi mark no-show: nếu assignment có `groupId` → `group.setLastNoShowAt(now)`)
- Modify: `backend/src/main/java/com/cani/service/MatchingDispatchService.java` (filter cooldown trong Task 2 + extend `expireStalePendingProposals`: proposal group mà leader đã đổi / member eligible list thay đổi → EXPIRED; tối giản: chỉ EXPIRED khi leader không còn LEADER hoặc shift không ACTIVE)
- Test: mở rộng `MatchingDispatchServiceTest`: `dispatch_groups_skips_group_in_no_show_cooldown_20min` (group `lastNoShowAt` = now−19p → bị loại; now−21p → vào).

- [ ] Steps theo TDD: test → implement (2 dòng trong NoShowService, 1 filter trong Task 2) → chạy xanh → commit `feat(groups): 20-min no-show cooldown + stale group proposal invalidation`

### Task 5: Transfer leader

**Files:**
- Modify: `WorkerGroupService.java`, `WorkerGroupController.java`
- Create: `backend/src/main/java/com/cani/dto/group/TransferLeaderRequestDto.java` (`newLeaderWorkerId`)
- Test: mở rộng `WorkerGroupServiceTest`

**Interfaces (Produces):** `void transferLeader(UUID callerUserId, UUID groupId, UUID newLeaderWorkerId)`; endpoint `POST /api/v1/worker-groups/{id}/transfer-leader` (leader hiện tại hoặc ADMIN).

- [ ] **Step 1: Test** — `transfer_leader_swaps_roles_and_expires_old_proposals`: membership cũ LEADER→MEMBER, membership mới MEMBER→LEADER, `group.leaderWorkerId` đổi; mọi `DispatchProposal` PENDING của group (leader cũ, query `findByGroup_GroupIdAndStatus`) → EXPIRED; FCM leader mới. + `transfer_leader_new_member_must_be_active`: new leader không phải membership ACTIVE → BadRequest.
- [ ] **Step 2: Implement + commit** — `feat(groups): transfer group leadership`

### Task 6: Sửa bug `attachGroupInfoIfPresent` (P10)

**Files:**
- Modify: `backend/src/main/java/com/cani/service/WorkerJobService.java` (line ~1647 + 2 call site: 193, 683)

- [ ] **Step 1: Test** — trong test service hiện có (tạo mới nếu thiếu): assignment sinh từ proposal `source GROUP` → `groupId` = groupId của proposal, `isGroupLeader` đúng; assignment cá nhân (source MATCHING/DIRECT) → `groupId` = null (trước đây bug: lấy `activeMemberships.get(0)` bừa).
- [ ] **Step 2: Implement** — đổi hàm nhận thêm parameter `source` (hoặc đọc từ proposal/assignment): chỉ set khi `source == GROUP`. Call site 193/683 truyền source tương ứng. **Không đổi behavior khác.**
- [ ] **Step 3: Chạy test + commit** — `fix(worker-job): group info only attached for GROUP-source assignments`

### Task 7: Flutter worker app — "Lời mời ca cho đội" + transfer

**Files:**
- Create: `mobile/lib/features/worker_groups/models/group_proposal_models.dart` (`GroupProposalModel`, `GroupEligibleMemberModel`, parse theo `GroupProposalResponseDto`)
- Modify: `mobile/lib/features/worker_groups/providers/worker_group_provider.dart` (+state `groupProposals`, methods `loadGroupProposals(groupId)`, `acceptGroupProposal(groupId, proposalId, memberWorkerIds)`, `rejectGroupProposal(...)`, `transferLeader(groupId, newLeaderWorkerId)`)
- Modify: `mobile/lib/features/worker_groups/screens/worker_group_detail_screen.dart` (+section "Lời mời ca cho đội" chỉ hiện với leader: card proposal (sự kiện, jobType, giờ, lương, còn m slot, đếm ngược expires, nhãn 🚨 nếu emergency) + sheet chọn member (checkbox, counter "còn m slot — cử 1..m", disable nếu 0; button "Xác nhận cho đội" / "Từ chối") + button "Chuyển giao trưởng nhóm" (bottom sheet chọn member ACTIVE, không phải leader))

**Interfaces (Consumes):** endpoints Task 3/5 (đường dẫn prefix `/v1/` như provider hiện có).

- [ ] **Step 1: Model + provider** (mirror pattern có sẵn trong provider: try/catch, parse `body['data']`, SnackBar qua return bool).
- [ ] **Step 2: UI trong detail screen** — theo spec §5.6; member-pick sheet dùng `currentGroupMembers` + `eligibleMembers` từ endpoint eligible (gray + lý do nếu not eligible).
- [ ] **Step 3: Verify** — `flutter analyze` sạch + chạy app, smoke: leader thấy proposal, chọn 2/3 member, accept → snackbar, members thấy assignment mới; transfer leader → role đổi. (Không có widget test hiện cho feature này — ponytail: chấp nhận manual smoke, thêm widget test khi UI ổn định.)

### Task 8: EO app — hiển thị nguồn fill ca

**Files:**
- Modify: màn hình theo dõi ca của EO (tìm trong `mobile/lib/features/` — event/shift detail, nơi list assignments) — thêm badge "Nhóm: [tên]" (từ `groupId`/`source` của assignment) trong danh sách nhân sự ca.

- [ ] **Step 1:** xác định đúng file (grep `ShiftAssignment` / source trong EO screens), render badge — không đổi API.
- [ ] **Step 2: Smoke + commit** — `feat(mobile-eo): show group fill source on shift staffing list`

### Task 9: Regression + docs

**Files:**
- Test: chạy **toàn bộ** test matching cá nhân có sẵn + `WorkerJobServiceTest`, `NoShowServiceTest` (nếu có) → xanh.
- Create: `docs/api/worker-group-matching-api.md` — 4 endpoints mới + transfer + response DTO + mã lỗi (409 slot, 400 k/conflict), theo format các file trong `docs/api/`.
- Modify: `docs/database/` (log migration V42/V43: cột mới `group_id`, `last_no_show_at`) — chỉ 1 file ghi chú ngắn.

- [ ] **Step 1:** `cd backend && mvn test` (toàn bộ) → xanh (AC8 spec: matching cá nhân y hệt).
- [ ] **Step 2:** viết `docs/api/worker-group-matching-api.md` + ghi chú migration.
- [ ] **Step 3:** commit — `test(docs): group-aware matching regression + API doc`

---

## Order & Dependencies

T1 → T2 → T3 → (T4, T5 song song) → T6 (độc lập, làm được bất cứ lúc nào) → T7 → T8 → T9. T9 là gate cuối (bắt buộc trước merge).
