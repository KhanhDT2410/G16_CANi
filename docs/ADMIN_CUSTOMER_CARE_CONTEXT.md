# Admin & Customer Care — Context Reconstructed (17/11/2026)

> File này reconstruct **toàn bộ** chức năng của 2 role ADMIN và CUSTOMER_CARE từ code + git + docs thực tế tại HEAD `0b98b29` (branch `test`). Mọi mục "✅" đã verify bằng đọc code / chạy test / git trong phiên 17/11/2026; mục "🟡" là chưa verify hoàn toàn hoặc có điều kiện.
> Nguồn sự thật: `backend/src/main/java/com/cani/**`, `web/src/**`, `docs/api/*.md`, `all_ucs_srs.txt` (root), `docs/reviews/REVIEW_*.md`.
> ⚠️ `docs/PROJECT_CONTEXT.md` (05/11) và các phiên HANDOFF cũ đã stale — file này thay thế chúng cho scope Admin/CC.

---

## 1. Tóm tắt kiến trúc

- **Monorepo:** `backend/` (Java 21, Spring Boot 3.5.1, PostgreSQL Supabase + H2 dev, Flyway **V1–V27**) · `web/` (React 18 + Vite, JS) · `mobile/` (Flutter/Riverpod).
- **Phân quyền:** OAuth2 Resource Server (JWT Supabase GoTrue ES256) → `UserAuthorityFilter` thay principal bằng entity `User` (4 role: `ADMIN`, `CUSTOMER_CARE`, `EVENT_ORGANIZER`, `WORKER`). Thứ tự URL rules trong `SecurityConfig`: rule CC cụ thể **trước** wildcard `/api/v1/admin/**` → `hasRole("ADMIN")`.
- **Envelope:** `ApiResponse<T>` `{success, message, data}`; error tiếng Việt qua `GlobalExceptionHandler`.
- **Convention module Admin/CC:** audit (`AuditLogService`, REQUIRES_NEW) + notification (`NotificationService`, `NotificationType`) + SLA deadline + test Mockito `@DisplayName` tiếng Việt + docs `docs/api/<name>-api.md`.

## 2. Ma trận Use Case (Admin & Customer Care)

Chú thích trạng thái: **C** = Completed (verify code+test) · **P** = Partial · **M** = Missing (chưa code) · **U** = Unverified (cần E2E/chưa chạy).

| UC | Tên (theo docs) | Role chịu trách nhiệm | Trạng thái 17/11 | Bằng chứng chính |
|---|---|---|---|---|
| UC-47 | Thẩm định chứng chỉ kỹ năng (Vision AI) | ADMIN + CC (queue/review); Worker submit | **C** (🟡 UI admin đã xóa theo `0b98b29`; 1 test assertion fail) | `SkillVerificationController` +`Service`, `SkillVerificationServiceTest`(8), `SkillVerificationFlowTest`(9, 1 fail), `CustomerCareQueueSecurityTest`(13) |
| UC-86 | Admin Dashboard & Metrics | ADMIN only | **C** (🟡 KPI "Hàng Đợi Chờ Thẩm Định" đã gỡ khỏi FE — working tree; backend vẫn trả `verifications`) | `AdminDashboardController/Service` (`/v1/admin/dashboard/metrics`, `/recent-activities`), `AdminDashboardServiceTest`(3), `AdminDashboardControllerTest`(2) |
| UC-87 | Quản trị người dùng | ADMIN only | **C** | `AdminUserController` (GET list+filter, GET/{id} detail merge Worker/EO, PATCH /{id}/status lock/unlock + reason + audit; tự khóa mình → 400), `AdminUserService` + `UserSpecification`, `AdminUserControllerTest`(4), `AdminUserServiceTest`(6). 🟡 wart: `?adminId=` override actor |
| UC-88 | Quản lý vi phạm No-show | ADMIN + CC | **C** | `AdminNoShowController` (list/stats/detail/`/{assignmentId}/mark`/`/scan`), `NoShowService` (−25 RS, auto-lock RS<20 = UC-95, emergency dispatch, cron 10'), `V18`, `NoShowServiceTest`(8), `AdminNoShowControllerTest`(6), FE `no-shows` |
| UC-94 | Kháng cáo điểm RS Worker | Worker submit; ADMIN+CC review | **C** | `WorkerAppealController` (`/v1/workers/appeals`, principal-only), `AdminAppealController` (`/v1/admin/appeals`), `RsAppealService` (7-day window, unique-per-log, approve → hoàn RS cap 100 + log mới), `V16`, `RsAppealServiceTest`(8), `RsAppealControllerTest`(5), FE `rs-appeals`. 🟡 enum `UNDER_REVIEW` chưa dùng |
| UC-96 | Submit Dispute | Worker/EO | **C** | `DisputeController` POST `/v1/disputes` (14-day window, min 50 chars, chặn trùng assignment; PAYMENT→`DISPUTED`) |
| UC-97 | Xem chi tiết Dispute | parties / CC / Admin | **C** | GET `/v1/disputes/{id}` + role check trong `DisputeService` (khác người không phải party/CC/Admin → 400 MSG34) |
| UC-98 | Upload Evidence | parties | **C** (🟡 lưu URL placeholder chờ UC-119; `dac7de4` đã secure evidence) | POST `/v1/disputes/{id}/evidence` (chỉ OPEN/INVESTIGATING) |
| UC-99 | CC nhận/xử + assign/transfer | CC | **C** | `/queue/mine`, `/customer-care-staff`, POST `/{id}/assign` (rỗng = tự nhận; OPEN→INVESTIGATING) |
| UC-100 | CC quyết định (Level-1) | CC | **C** | POST `/v1/admin/disputes/{id}/decision`; WORKER_FAULT → −10 RS (BR-55) trừ khi `penaltyWaiverReason`; `rsPenaltyApplied` chống phạt kép |
| UC-101 | Escalate lên Admin | Worker/EO | **C** | POST `/v1/disputes/{id}/escalate` (RESOLVED/UNVERIFIED trong 7 ngày, BR-54) |
| UC-102 | Admin quyết định cuối | ADMIN | **C** (CC bị chặn code ở state ESCALATED) | decision at ESCALATED → `FINAL_RESOLVED` (finalDecidedBy/At, BR-68) |
| UC-103 | Áp dụng hình phạt RS | System | **C** (thực hiện inline trong UC-100, không có endpoint riêng) | `reliability_logs` referenceType DISPUTE |
| UC-104 | Xem lịch sử Dispute | parties / Admin | **C** | GET `/v1/disputes/my` |
| UC-105 | Submit Support Ticket | mọi user authenticated | **C** | POST `/v1/support/tickets` (SLA theo BR-58: HIGH 4h/MEDIUM 1d/LOW 3d; refNo `TKT-yyyMMdd-XXXXXXXX`; description 30–3000) |
| UC-106 | Track / xử lý Ticket | submitter / CC | **C** | `/my`, detail (internal notes ẩn với user), reply, self-resolve; CC: queue (SLA-risk first, cap 50), `/queue/mine`, staff list, assign, reply (internal note), close (rationale 30–1000) |
| UC-107 | Duyệt đăng ký EO (CC review hồ sơ EO) | CC | **P** (🟡 chưa có endpoint/flow riêng "approve EO registration" đúng nghĩa UC-107; EO eKYC queue `/admin/eo-ekyc/*` + `EventOrganizerEkycService` (bằng cấp thuế/GPKD → PENDING_REVIEW manual) đã phủ phần lớn; docs vẫn liệt UC-107 là "chưa code") | `EventOrganizerEkycController`, `EventOrganizerEkycServiceTest`(6) |
| UC-108 | Revenue Dashboard | ADMIN (SRS có Finance → map ADMIN) | **C** | `AdminRevenueController` (summary/transactions/analytics, period-over-period, VNPay summary, flagged DISPUTED/UNVERIFIED), `RevenueService` (8 DTO), `RevenueServiceTest`(7), `AdminRevenueControllerTest`(5), FE `revenue-dashboard`. Data chặt hơn sau `78c8002` |
| UC-109 | Manage Dispute Queue | CC + ADMIN | **C** | GET `/v1/admin/disputes/queue` (OPEN+INVESTIGATING+ESCALATED, SLA-at-risk <24h trước, BR-57) |
| UC-110 | FAQ & Help Center (a: quản lý, b: public, c: deflection) | ADMIN+CC quản lý; public đọc | **C** | `AdminFaqController` (CRUD + publish/archive + `upload-image` + analytics deflection rate), `FaqController` (`/v1/faqs` permitAll, viewCount), `V15/V19/V20/V22`, `FaqServiceTest`(14), FE `faqs` (tabs Admin/CC/Support) |
| UC-111 | Dashboard mở rộng (metrics disputes/assignments/RS) | ADMIN | **M** (SRS-only; chưa code) | — |
| UC-112 | Manage Users CRUD đầy đủ (reset password…) | ADMIN | **M** (UC-87 chỉ lock/unlock; SRS yêu cầu nhiều hơn) | — |
| UC-113 | RBAC / ma trận role | ADMIN | **M** (SRS-only; `UserRole` chỉ 4 giá trị, không có FINANCE) | — |
| UC-114 | Cấu hình tham số hệ thống | ADMIN only | **C** | `SystemConfigController` `/v1/admin/configs` (list/get/PUT single/bulk; 7 key mặc định; audit `UPDATE_SYSTEM_CONFIG`/key), `SystemConfigServiceTest`(9), `SystemConfigControllerTest`(9). CC bị chặn (cố ý, wildcard). 🟡 wart `?adminId=` |
| UC-115 | Cấu hình rule điểm SP | ADMIN only | **P** (thực hiện qua API key-value generic của UC-114; SRS mô tả chi tiết multiplier theo loại ca/rating — các key cụ thể chưa có) | `system_config` |
| UC-117 | Màn hình Notification (SCR-10) | mọi user | **P** — infra **C**: `NotificationController` (my/unread-count/read/read-all, principal-only), `NotificationService` (DB + FCM hook, `notifyRole`), `NotificationType`(10 giá trị), `fcm_integration_plan.md`; **M**: UI màn hình notification cho user (chưa có trong web/mobile) | — |
| UC-119 | Upload & validate file tập trung | System | **M** (mọi module dùng URL placeholder `https://storage.cani.vn/...` + convention; FAQ image upload là ngoại lệ cục bộ) | — |
| UC-121 | Trang truy vấn Audit Log độc lập | ADMIN | **P** — logging **C** (`audit_logs` ghi ở ~15 action loại: dispute, ticket, config, FAQ, appeal, no-show, user status, SLA…); **M**: endpoint + UI query audit theo actor/entity/date (hiện chỉ hiển thị top-10 trong dashboard UC-86) | — |
| (không số UC) SLA monitoring job | System (cảnh báo CC/Admin) | **C** | `SlaMonitoringService` (cron 15', ticket/dispute CC overdue + escalated→ADMIN, idempotent qua `sla_alert_logs`, không auto-close), `V17`, `SlaMonitoringServiceTest`(6), `docs/api/sla-monitoring-api.md` |
| eKYC Worker/EO (hàng đợi duyệt) | ADMIN + CC | **P** (backend C: `WorkerEkycController`/`EventOrganizerEkycController` endpoints `/admin/ekyc/queue`, `/admin/ekyc/{id}/review`, `/admin/eo-ekyc/*` + `EkycUserIdParamIgnoredTest`(4) + `CustomerCareQueueSecurityTest`(13); sau `0b98b29` worker flow AI-approve/reject thẳng → queue gần như rỗng; FE slice đã xóa) | — |

## 3. Execution flow (FE → API → Backend → DB)

### 3.1 Cổng `/admin` (ADMIN; `pages/admin/AdminPage.jsx` + `AdminSidebar`)
Tab → feature slice → endpoint (mọi call qua `apiClient` attach `cani_token`):

| Tab | Slice | Endpoints (GET/POST…) |
|---|---|---|
| dashboard | `admin-dashboard` | GET `/v1/admin/dashboard/metrics`, `/recent-activities` |
| revenue | `revenue-dashboard` | GET `/v1/admin/revenue/summary?from&to`, `/transactions?…page`, `/analytics?period=day|week|month` |
| users | `admin-users` | GET `/v1/admin/users?search&role&status&page`, `/v1/admin/users/{id}`, PATCH `/v1/admin/users/{id}/status` (body status+reason) |
| noshows | `no-shows` | GET `/v1/admin/no-shows` (filter), `/stats`, `/{id}`, POST `/{assignmentId}/mark`, `/scan` |
| appeals | `rs-appeals` | GET `/v1/admin/appeals?status&page`, `/{id}`, POST `/{id}/review` (approved+decisionNote) |
| tickets | `support-tickets` | GET `/v1/admin/support/tickets/queue`, `/queue/mine`, `/customer-care-staff`, POST `/{id}/assign`, `/{id}/reply` (internal note được), `/{id}/close` |
| disputes | `disputes` | GET `/v1/admin/disputes/queue`, `/queue/mine`, `/customer-care-staff`, POST `/{id}/assign`, `/{id}/decision` |
| faqs | `faqs` | GET/POST `/v1/admin/faqs`, GET/PUT `/{id}`, POST `/{id}/publish`, `/{id}/archive`, `/upload-image` (multipart), GET `/analytics` |
| config_* (×4) | `system-configs` | GET `/v1/admin/configs`, `/{key}`, PUT `/{key}`, PUT (bulk) |

### 3.2 Cổng `/customer-care` (CUSTOMER_CARE; ADMIN cũng browse được; `CustomerCareSidebar`)
Nhóm: support = tickets + faqs · disputes_appeals = disputes + appeals + noshows. **Không có** dashboard/revenue/users/config (đúng phân quyền backend). Feature slice **chung** với Admin (import cùng 1 thư mục `features/`).

### 3.3 Cổng public `/support` (không role)
FAQ public (`/v1/faqs`, `FaqPublicList`) + tickets user: POST `/v1/support/tickets`, GET `/my`, `/{id}`, POST `/{id}/reply`, `/{id}/resolve`.

### 3.4 Flow nghiệp vụ mẫu (Dispute UC-96→102)
```
Worker/EO: POST /v1/disputes (14d, ≥50chars, 1 dispute mở/assignment)
  → DisputeService.create: status OPEN, slaDeadline +7d (BR-57), PAYMENT→Payment DISPUTED,
     audit SUBMIT_DISPUTE, notify cả 2 parties + toàn role CUSTOMER_CARE
CC: GET /v1/admin/disputes/queue (SLA-risk first) → POST /{id}/assign (tự nhận) → OPEN→INVESTIGATING
   (audit ASSIGN_DISPUTE, notify assignee)
CC: POST /{id}/decision (outcome, rationale ≥100; WORKER_FAULT → −10 RS trừ khi waiver;
   rsPenaltyApplied guard) → RESOLVED (audit ISSUE_DISPUTE_DECISION, notify parties)
Worker/EO: POST /v1/disputes/{id}/escalate (RESOLVED/UNVERIFIED, 7d) → ESCALATED
ADMIN: POST /v1/admin/disputes/{id}/decision tại ESCALATED → FINAL_RESOLVED (finalDecidedBy/At)
SLA job: cron 15' → ticket/dispute quá hạn → notify assignee/CC (CC-overdue),
   escalated quá hạn → notify ADMIN; ghi audit SLA_BREACH_*, idempotent qua sla_alert_logs
```

### 3.5 Controller → Service → Repository → DB (bản đồ đầy đủ)

| Controller (base) | Service | Repos chính | Bảng | Migration |
|---|---|---|---|---|
| `AdminDashboardController` `/v1/admin/dashboard` | `AdminDashboardService` (KPI counts + top-10 audit + `formatActionDescription` ~30 nhãn) | `UserRepository`, `AuditLogRepository`, `WorkerEkycVerificationRepository`, `EoEkycVerificationRepository`, `SkillVerificationRequestRepository`, `EventRepository`, `ShiftRepository` | users, audit_logs, worker_ekyc_verifications, eo_ekyc_verifications, skill_verification_requests, events, shifts | V1, V8, V9, V4/V5 |
| `AdminUserController` `/v1/admin/users` | `AdminUserService` (+JPA spec `UserSpecification`) | `UserRepository`, `WorkerRepository`, `EventOrganizerRepository`, `AuditLogRepository` | users (+merge worker/eo profile) | V1/V3 |
| `DisputeController` `/v1/disputes` + `AdminDisputeController` `/v1/admin/disputes` | `DisputeService` (window 14d, SLA 7d, escalate 7d, −10 RS BR-55, clamp RS [0,100]) | `DisputeRepository`, `DisputeEvidenceRepository`, `ShiftAssignmentRepository`, `ShiftRepository`, `PaymentRepository`, `WorkerRepository`, `EventOrganizerRepository`, `UserRepository`, `ReliabilityLogRepository`, `NotificationRepository`, `AuditLogRepository` | disputes, dispute_evidence, shifts, shift_assignments, payments, reliability_logs, notifications, audit_logs | V12 |
| `SupportTicketController` `/v1/support/tickets` + `AdminSupportTicketController` `/v1/admin/support/tickets` | `SupportTicketService` (BR-58 SLA theo category; refNo TKT-…) | `SupportTicketRepository` (kèm `countByCategory`), `SupportTicketMessageRepository`, `UserRepository`, `NotificationRepository`, `AuditLogRepository` | support_tickets, support_ticket_messages | V13 |
| `SystemConfigController` `/v1/admin/configs` | `SystemConfigService` | `SystemConfigRepository` | system_config | seed + V1 |
| `FaqController` `/v1/faqs` (public) + `AdminFaqController` `/v1/admin/faqs` | `FaqService` (DRAFT→PUBLISHED→ARCHIVED; deflection = tickets trong category có FAQ PUBLISHED / tổng tickets; image ≤5MB, `uploads/faqs/`, path-traversal guard; viewCount) | `FaqRepository`, `SupportTicketRepository` (deflection) | faqs (+attachments V19, summary V22, seed V20) | V15, V19–V20, V22 |
| `WorkerAppealController` `/v1/workers/appeals` + `AdminAppealController` `/v1/admin/appeals` | `RsAppealService` (7-day, 1 appeal/log, approve→hoàn RS ≤100 + log mới RS_APPEAL) | `RsAppealRepository`, `ReliabilityLogRepository`, `DisputeRepository`, `UserRepository`, `NotificationRepository`, `AuditLogRepository` | rs_appeals, reliability_logs | V16 |
| `AdminNoShowController` `/v1/admin/no-shows` | `NoShowService` (−25 RS, lock <20, reopen slot, `MatchingDispatchService.dispatchForShift`, notify worker+EO; cron 10' + `/scan`) | `NoShowRecordRepository`, `ShiftAssignmentRepository`, `AttendanceLogRepository`, `ReliabilityLogRepository`, `UserRepository`, `EventRepository`, `NotificationRepository`, `AuditLogRepository` | no_show_records, attendance_logs | V18, V24 |
| `AdminRevenueController` `/v1/admin/revenue` | `RevenueService` (agg read-only + growth guard zero-division; VNPay summary caveat — chưa có webhook VNPay) | `PaymentRepository`, `SubscriptionRepository`, `EventRepository`, `UserRepository` | payments, subscriptions, events | V1, V25, V26 |
| `AdminWageMatrixController` `/v1/admin/wage-matrix` | `WageMatrixService` (5-level fallback lookup, floor 35k; không audit) | `WageMatrixRepository` | wage_matrix | (hung branch) |
| `SkillVerificationController` `/v1/workers/skills` (+`/admin/**` ADMIN+CC) | `SkillVerificationService` (Tier2 Vision AI: AUTO_APPROVE ≥0.80 / REJECT; identity-mismatch → REJECT 0.15; +25 SP; revoke → down SELF_DECLARED; `resolveWorkerId` admin-only override) | `SkillVerificationRequestRepository`, `WorkerSkillRepository`, `WorkerSkillPointRepository`, `SkillRepository`, `WorkerRepository` | skill_verification_requests, worker_skills, worker_skill_points, skills | V4, V5 |
| `WorkerEkycController` `/v1/workers/ekyc/*` + `/v1/admin/ekyc/*` | `WorkerEkycService` (VNPT OCR + face match, CCCD SHA-256 hash/mask NĐ13/2023, duplicate check, sau `0b98b29`: AUTO_APPROVED→APPROVED / else REJECTED, không PENDING) | `WorkerEkycVerificationRepository`, `WorkerRepository`, `UserRepository` | worker_ekyc_verifications | V8 |
| `EventOrganizerEkycController` `/v1/eo/ekyc/*` + `/v1/admin/eo-ekyc/*` | `EventOrganizerEkycService` (taxId/GPKD → forced PENDING_REVIEW manual; strict mode; review từ PENDING_REVIEW, set `EventOrganizer.verificationStatus` + FK reviewedBy) | `EoEkycVerificationRepository`, `EventOrganizerRepository`, `UserRepository` | eo_ekyc_verifications | V9 |
| `NotificationController` `/v1/notifications` | `NotificationService` (DB+FCM; `markAsRead` scoped theo userId) | `NotificationRepository` | notifications | V1 |
| SLA job (không controller) | `SlaMonitoringService` (`@Scheduled` cron 15', `SchedulingConfig`) | `SupportTicketRepository`, `DisputeRepository`, `SlaAlertLogRepository`, `NotificationRepository`, `AuditLogRepository` | sla_alert_logs | V17 |
| `HealthController` | — | — | — | — |

**Enums quan trọng (đọc trong `entity/enums/`):** DisputeStatus {OPEN, INVESTIGATING, RESOLVED, UNVERIFIED, ESCALATED, FINAL_RESOLVED} · DisputeType {PAYMENT, WORK_QUALITY, ATTENDANCE, CONDUCT, OTHER} · DisputeOutcome {EO_FAULT, WORKER_FAULT, PARTIAL, UNVERIFIED, UPHOLD} · TicketStatus {OPEN, IN_PROGRESS, CLOSED} · TicketPriority {HIGH, MEDIUM, LOW} · TicketCategory {ACCOUNT, BILLING, TECHNICAL, GENERAL, OTHER} · FaqStatus {DRAFT, PUBLISHED, ARCHIVED} · FaqCategory {ACCOUNT, SHIFT, PAYMENT, DISPUTE} · RsAppealStatus {SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED} · NoShowDetectedBy {SYSTEM_CRON, ADMIN, CUSTOMER_CARE, EVENT_ORGANIZER} · NotificationType {PROPOSAL, ASSIGNMENT, REMINDER, PAYMENT, DISPUTE, RATING, SYSTEM, GROUP, SUPPORT_TICKET, RS_APPEAL} · SkillVerificationStatus {APPROVED, PENDING_REVIEW, REJECTED} · KycStatus {PENDING, APPROVED, REJECTED, SUSPICIOUS}.

**Audit action strings (nằm trong `AdminDashboardService.formatActionDescription` + services):** SUBMIT/ASSIGN/ISSUE/FINAL dispute, SUBMIT/REPLY/ASSIGN/CLOSE ticket, UPDATE_SYSTEM_CONFIG, CREATE/UPDATE/PUBLISH/ARCHIVE_FAQ, SUBMIT/APPROVE/REJECT_RS_APPEAL, MARK_NO_SHOW, AUTO_NO_SHOW_SCAN, SLA_BREACH_TICKET / SLA_BREACH_DISPUTE_CC / SLA_BREACH_DISPUTE_ESCALATED, UPDATE_USER_STATUS, APPROVE_WORKER_EKYC / APPROVE_EO_EKYC (⚠️ nhãn tồn tại nhưng không service eKYC nào emit — gap §5). Khi thêm module mới: luôn thêm case tiếng Việt ở đây.

## 4. Lỗ hổng / việc chưa làm (gắn với SRS)

| # | Hạng mục | Trạng thái | Ghi chú |
|---|---|---|---|
| 1 | UC-107 Duyệt đăng ký EO (flow CC riêng: approve/reject hồ sơ + CCCD + resubmission/escalation) | **M** | EO eKYC queue hiện tại phủ một phần; cần leader chốt UC-107 = gì thêm |
| 2 | UC-111 Dashboard mở rộng (metrics disputes/assignments/RS, anomaly alerts) | **M** | UC-86 là baseline hiện có |
| 3 | UC-113 RBAC + role FINANCE | **M** | UserRole 4 giá trị; revenue hiện map Finance→ADMIN |
| 4 | UC-117 Màn hình notification user (SCR-10) | **M** (infra C) | API + bảng + FCM plan có sẵn; thiếu UI web/mobile |
| 5 | UC-121 Trang audit độc lập (query actor/entity/date) | **M** (logging C) | chỉ top-10 trong dashboard |
| 6 | UC-119 File upload tập trung (type/size/virus) | **M** | đang URL placeholder; FAQ image + dispute evidence + proof image chờ module này |
| 7 | UC-112 CRUD user đầy đủ (reset password…) | **M** | UC-87 chỉ lock/unlock |
| 8 | UC-115 Rule SP chi tiết (multiplier theo loại ca/rating) | **P** | chỉ key-value generic |
| 9 | Audit eKYC: nhãn APPROVE_WORKER_EKYC/EKYC tồn tại nhưng không service nào ghi audit | **gap nội tại** | xem §5.2 |
| 10 | Mobile: chưa có màn hình support-ticket / dispute / RS appeal (chờ backend — backend đã sẵn sàng) | **M** | — |

## 5. Bug / bất nhất phát hiện (17/11)

1. **`SkillVerificationFlowTest.step3` FAIL** — assertion string cũ sau `0b98b29` ("phê duyệt" vs "phê duyệt tự động"). Sửa 1 dòng. Suite 463/1 fail.
2. **eKYC worker queue gần như rỗng** — `getPendingEkycQueue()` query `PENDING_REVIEW`, nhưng worker `submitEkyc` sau `0b98b29` chỉ ghi APPROVED/REJECTED (PENDING_REVIEW chỉ còn ở EO strict-mode/direct DB). UI admin đã xóa (WIP) nên ít ảnh hưởng; nhưng endpoint `/v1/admin/ekyc/queue` + `review` vẫn tồn tại — hỏi leader giữ hay thu gọn.
3. **Wart `?adminId=`** ở `AdminUserController.PATCH status` + `SystemConfigController` PUT: param query override người ghi audit (ADMIN-only, không phải bypass nhưng trái quy tắc principal-only). Nên thay bằng `SecurityUtils.getCurrentUserId`.
4. **`RsAppealStatus.UNDER_REVIEW`** không có flow set giá trị này (chỉ SUBMITTED→APPROVED/REJECTED).
5. **Unauthenticated static-file endpoints:** `/v1/eo/events/images/**`, `/v1/workers/proof/**` (path-traversal đã guard trong service; exposure nhỏ).
6. **UC-86 FE/backend lệch nhẹ:** service dashboard vẫn trả khối `verifications` (PENDING_REVIEW counts) trong khi FE đã bỏ KPI đó — backend không lỗi, chỉ dư; cân nhắc sửa khi làm UC-111.
7. **Docs drift:** `plan-fe12-support-tickets.md` §7.4 "YAGNI không scheduler" đã lỗi thời (SLA scheduler chạy từ `5e5a13f`); `docs/PROJECT_CONTEXT.md` nói migration "đến V13/V17" trong khi thực tế V27; SRS UC-numbering (all_ucs_srs.txt) lệch UC-86/87 so với API docs (collision: SRS UC-86/87 = payment confirm/history). BR-54 bị dùng với 2 nghĩa khác nhau ở no-show-api vs dispute-api.
8. **Test catalog 47 ca** chưa bao phủ UC-88/94/108/110 + SLA job; 6 ca eKYC/skill trong catalog giờ thừa khối "queue" (UI đã xóa).

## 6. Coverage & build (verify 17/11/2026)

- **Backend suite:** `./mvnw.cmd test` → **463 tests, 1 failure** (`SkillVerificationFlowTest.step3`), 0 errors. Các class Admin/CC toàn bộ PASS: DisputeServiceTest(12), SupportTicketServiceTest(13), SystemConfigServiceTest(9)+ControllerTest(9), FaqServiceTest(14), RsAppealServiceTest(8)+ControllerTest(5), NoShowServiceTest(8)+AdminNoShowControllerTest(6), RevenueServiceTest(7)+AdminRevenueControllerTest(5), SlaMonitoringServiceTest(6), AdminDashboardServiceTest(3)+ControllerTest(2), AdminUserControllerTest(4)+AdminUserServiceTest(6), CustomerCareQueueSecurityTest(13), EkycUserIdParamIgnoredTest(4), SecurityUtilsTest(6), AuthServiceTest (gồm UTC009 dev-token 503 / UTC013 h2 fallback) PASS.
- **Chưa có test controller riêng:** AdminFaqController, AdminAppealController, AdminDisputeController, AdminSupportTicketController, WorkerAppealController (chỉ service tests + 1 số WebMvcTest chung).
- **Web:** `npx vite build` PASS trên working tree (352 modules, 3.1s, warning chunk EOPage >500kB — không blocking).
- **Working tree (14 file, CHƯA commit — giữ nguyên):** xóa `web/src/features/ekyc-verification/` + `web/src/features/skill-verification/` (staged), sửa `MetricsCards.jsx` (bỏ KPI verifications), sửa `AdminPage.jsx` (bỏ comment block). Build đã verify sạch.
- **E2E tay:** chưa chạy trong phiên này (mục 3 trong HANDOFF).

## 7. Path quan trọng (cheat-sheet)

```
backend/src/main/java/com/cani/
  config/SecurityConfig.java            ← thứ tự rule CC-trước-wildcard (đọc kỹ trước khi thêm endpoint)
  config/JwtConfig.java + H2JwtDecoder  ← dev-token chỉ h2 (P0 an toàn, có test)
  config/SchedulingConfig.java          ← @EnableScheduling (SLA cron)
  security/SecurityUtils.java           ← getCurrentUserId / isAdmin (dùng mọi endpoint mới)
  service/AuthService.java              ← gate acceptsProfiles("h2") dòng ~178-197
  controller/Admin*.java (9 file)       ← toàn bộ admin surface
  controller/{Dispute,SupportTicket,Faq,WorkerAppeal,SkillVerification,WorkerEkyc,EventOrganizerEkyc,Notification}Controller.java
  service/{AdminDashboard,AdminUser,Dispute,SupportTicket,SystemConfig,Faq,RsAppeal,NoShow,Revenue,SlaMonitoring,Notification,WorkerEkyc,EventOrganizerEkyc,SkillVerification}Service.java
  src/main/resources/db/migration/      ← V1–V27
  src/test/java/com/cani/{service/admin,controller,security}  ← regression set
web/src/
  pages/admin/AdminPage.jsx + components/AdminSidebar(.constants).jsx
  pages/customer-care/CustomerCarePage.jsx + components/CustomerCareSidebar.jsx
  features/{admin-dashboard,admin-users,disputes,support-tickets,system-configs,faqs,revenue-dashboard,rs-appeals,no-shows}/{api,components,hooks,types}
  services/api.js                       ← apiClient (cani_token, 401 redirect, public-path list)
docs/api/*.md · docs/test-cases/TEST_CASES_ADMIN_CUSTOMER_CARE.md · docs/reviews/REVIEW_*.md · all_ucs_srs.txt (root)
```

## 8. Nguồn & độ tin cậy

- **Verified (17/11):** toàn bộ mapping §3, enum, migration danh sách, test counts & suite result, dev-token/IDOR state, uncommitted changes, security rules — bằng đọc code + `git log/diff/show` + chạy `./mvnw.cmd test` + `npx vite build`.
- **Unverified:** E2E runtime (không có server đang chạy trong phiên), Supabase RLS thực tế (chỉ đọc trong migration), VNPay webhook (docs ghi "chưa có"), độ đầy đủ seed H2 cho demo CC.
- **Assumptions (đã ghi rõ, không bịa):** mapping UC ↔ module theo danh xưng trong `docs/api/*.md` + `all_ucs_srs.txt`; khi SRS docx không đọc được, số UC lấy từ extraction txt này; collision số UC-86/87 giữa SRS-txt và API-docs đã được flag (§5.7), không tự chọn bên nào.
