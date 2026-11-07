# CANi — Project Context (Handoff Document)

> **Mục đích:** Chấm điểm (snapshot) toàn bộ trạng thái hệ thống để session Claude Code mới khôi phục ngữ cảnh nhanh. Đọc cùng `CLAUDE.md` (nguyên tắc) và `AGENTS.md` (chuẩn code team).
> **Đã cập nhật lần cuối:** 05/11/2026 (sau phiên fix CC 403). Mọi mục "Đã kiểm chứng" dưới đây đều được xác nhận bằng lệnh (compile / test / inspect code) tại thời điểm ghi.
>
> **Đọc trước khi code:** `HANDOFF.md` (root) — trạng thái chính xác của phiên gần nhất + việc tiếp theo. File này là snapshot tổng quan.

---

## 1. Tổng quan hệ thống

**CANi** là nền tảng event staffing (đặt việc & quản lý đội ngũ event staff) gồm 3 dịch vụ trong một monorepo:

| Dịch vụ | Thư mục | Công nghệ | Vai trò |
|---|---|---|---|
| Backend | `backend/` | Java 21, Spring Boot 3.5.1, PostgreSQL + H2, Flyway, Maven | API + nghiệp vụ |
| Web (Admin/CC/EO) | `web/` | React 18 (JS/JSX), Vite, React Router 7, Axios | Cổng quản trị + khách |
| Mobile (Worker/EO) | `mobile/` | Flutter, Riverpod, Dio | App phía người lao động |

Yêu cầu nghiệp vụ (SRS/SDS) nằm trong file `.docx` tại `docs/requirements/` (SEP490-G16 Report-3 SRS, Report-4 SDS). Mã nghiệp vụ được trích dẫn dưới dạng **UC-nn** (Use Case), **BR-nn** (Business Rule), **SCR-nn** (Screen Requirement).

---

## 2. Kiến trúc Backend

**Phân tầng:** `Controller → Service → Repository (JPA)` + Lombok + Jakarta Validation.

**Profile runtime:**
- `local` (mặc định): PostgreSQL Supabase thật, Flyway migrations (V1→V13), `ddl-auto: validate`. Bí mật đọc từ `backend/.env` (`DB_PASSWORD`… — file này **git-ignored**, không bao giờ in/commit giá trị).
- `h2`: in-memory, seed bằng `H2DataSeeder`, hỗ trợ dev-token (`dev-token-<uuid>`, chỉ chạy trên profile này).

**Xác thực:** OAuth2 Resource Server — JWT từ Supabase GoTrue (ES256, `JwtConfig`). `UserAuthorityFilter` thay principal bằng entity JPA `User` → mọi controller đọc `Authentication.getPrincipal() as User` và **không nhận userId từ body/path** (chống IDOR). Quy tắc này bắt buộc áp dụng trong mọi endpoint mới.

**Thứ tự rule trong `SecurityConfig` (quan trọng):**
1. `permitAll()`: `/api/health`, `/actuator/health`, `/api/auth/**`, `/api/v1/workers/skills/taxonomy`, `/api/v1/workers/skills/tiers/config`, `/api/v1/workers/ekyc/**`, `/api/v1/eo/ekyc/**`
2. `hasRole("ADMIN")`: `/api/v1/workers/skills/admin/**`
3. `hasAnyRole("ADMIN","CUSTOMER_CARE")`: `/api/v1/admin/disputes/**`, `/api/v1/admin/support/**`
4. `authenticated()`: `/api/v1/disputes/**`, `/api/v1/support/**`, `/api/v1/workers/skills/**`
5. **Wildcard** `hasRole("ADMIN")`: `/api/v1/admin/**` ← rule CC phải nằm **trước** wildcard này, nếu không CC bị 403.

**Wrapper phản hồi:** `ApiResponse<T>` (`success`, `message`, `data`). `GlobalExceptionHandler` — message tiếng Việt cho app exception; mọi exception chưa xử lý → 500 "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau." (gặp message này ⇒ lỗi phía server **hoặc backend chạy code cũ**, không phải lỗi UI).

**Thuật toán dùng trong module gần đây (copy khi làm module mới):** audit trail qua `AuditLogService` (REQUIRES_NEW), notification qua `NotificationRepository`/`NotificationType`, cột `sla_deadline`, test Mockito với `@DisplayName` tiếng Việt.

---

## 3. Frontend Web

**Pattern Feature-Driven (Vertical Slice):** mỗi feature trong `src/features/<tên>/{api,components,hooks,types,index.js}` (chuẩn full trong `AGENTS.md` §1). `apiClient` dùng chung ở `src/services/api.js` (baseURL `${VITE_API_BASE_URL}/api`, gắn `cani_token` từ localStorage, 401 → redirect /login).

**Routes hiện có (`App.jsx`):** `/admin` (AdminPage, nhiều tab), `/customer-care` (CustomerCarePage: tickets + disputes), `/eo` (EO; worker redirect sang đây), `/support` (SupportPage — user view của support tickets), `/worker`, `/mobile-preview`, `/auth/callback`, `/login`, `/register`.

**Phân quyền UI:** `useAuth().user.role` ∈ {`ADMIN`, `CUSTOMER_CARE`, `EVENT_ORGANIZER`, `WORKER`}. Trong feature, staff = `role === 'ADMIN' || role === 'CUSTOMER_CARE'`. `useAuthGuard` redirect theo role.

**Quy ước API:** mỗi `api/<name>Api.js` trả về `response.data?.data` (unwrap envelope). Hook bắt lỗi bằng `err.response?.data?.message` rồi toast — vì message backend tiếng Việt.

**Thành phần <200 dòng;** view lớn tách subcomponent (vd `support-tickets/components/TicketModalsGroup.jsx`).

---

## 4. Database & Migrations

Flyway tại `backend/src/main/resources/db/migration/` (hiện **đến V13**). H2 bỏ qua Flyway (dùng `ddl-auto` + `h2-init.sql`). **Không bao giờ sửa migration đã áp dụng — luôn thêm số V kế tiếp.**

| Migration | Nội dung |
|---|---|
| V1 | Schema gốc (users, assignments, workers, EOs, reference data…) |
| V2 | Seed reference data |
| V3 | Auth refactor |
| V4/V5 | Worker skill verification + worker_skills |
| V6 | Single gatekeeper + sync trigger |
| V7 | Wallet & dispatch |
| V8 | Worker eKYC |
| V9 | Refactor workers & EO eKYC |
| V10 | Fix search path cho new auth user |
| V11 | `user_device_tokens` (FCM đa thiết bị) |
| V12 | `dispute_resolution` (tranh chấp: evidence, escalation, penalty) |
| V13 | `support_tickets` (ticket + messages + SLA) |

---

## 5. Các module đã HOÀN THIỆN & KÍCH THỬ

> ✅ = đã kiểm chứng bằng `mvn test` / `vite build` / đọc code tại thời điểm snapshot.

| Module (UC) | Backend | FE Web | Test | Ghi chú |
|---|---|---|---|---|
| **UC-86 Dashboard & Metrics** | ✅ `AdminDashboardController/Service` (KPI counts, recent activities, audit) | ✅ `AdminDashboardView` (tab dashboard) | ✅ `AdminDashboardServiceTest` (3) | |
| **UC-87 User Management** | ✅ `AdminUserController/Service` (list+filter, detail, lock/unlock + audit) | ✅ `UserManagementView` (tab users) | ✅ `AdminUserControllerTest` (4) | |
| **UC-47 Skill Verification** | ✅ submit (Vision AI) + queue + review (+25 SP) | ✅ tab "Hàng Đợi Kỹ Năng AI" | ✅ `SkillVerificationServiceTest`, `SkillVerificationFlowTest` | |
| **eKYC (worker + EO)** | ✅ queue + manual review (APPROVED/REJECTED) | ✅ tab "Hàng Đợi eKYC" | ✅ `WorkerEkycServiceTest`, `EventOrganizerEkycServiceTest` | |
| **UC-96→104/109 Dispute** | ✅ `DisputeService`, `DisputeController` (public), `AdminDisputeController` (queue, **queue/mine**, **customer-care-staff**, assign, decision) + `V12` | ✅ feature `disputes` (2 view: CC/Admin queue + user "của tôi", 5+1 modal) | ✅ `DisputeServiceTest` (12) | đã commit `ba29a55` |
| **UC-105/106 Support Ticket** | ✅ `SupportTicketService`, `SupportTicketController`, `AdminSupportTicketController` (queue, **queue/mine**, **customer-care-staff**, assign, reply, close) + `V13` | ✅ feature `support-tickets` (view user + staff, 6 component + `TransferTicketModal`) | ✅ `SupportTicketServiceTest` (13) | commit ở `9cb0cb1` |
| **System Config (UC-114/115)** | ✅ `SystemConfigController` (`/v1/admin/configs`, `hasRole('ADMIN')`), `SystemConfigService/Repository`, DTO | ✅ feature `system-configs` (tab "Tham Số Hệ Thống") | ✅ `SystemConfigServiceTest` (9) + `SystemConfigControllerTest` (8) | đã commit `ba29a55`; CC không truy cập (đúng design UC-114/115 chỉ Admin) |
| **CC access eKYC/Skill queue** | ✅ rule `SecurityConfig` mở CC cho ekyc/eo-ekyc/skills admin + `H2DataSeeder` seed `cc@cani.vn` | 🟡 feature slice `ekyc-verification` + `skill-verification` đã tạo nhưng **UI đang bị comment out** chờ nhóm duyệt | ✅ `CustomerCareQueueSecurityTest` (13) | commit `07a6af7` |
| **Auth + eKYC/OTP** | ✅ `AuthService`, `AuthController`, `EmailOtpService`, `VnptEkycClient` | ✅ feature `auth` | ✅ `AuthControllerTest` (3), `EmailOtpServiceTest` | |
| **Payment / Matching** | ✅ `DirectPaymentController/Service`, `MatchingDispatchService` | — | ✅ `DirectPaymentServiceTest`, `MatchingDispatchServiceTest` | |
| **Worker eKYC + Skill** | ✅ | — | ✅ | |

**Bộ test chạy lại (regression set) đã pass tại snapshot (49/49, BUILD SUCCESS):**
```
DisputeServiceTest, SupportTicketServiceTest, SystemConfigServiceTest,
SystemConfigControllerTest, AdminDashboardServiceTest, AdminUserControllerTest
```
+ `vite build` ✓ (web).

---

## 6. Use Case cho Admin & Customer Care (điểm nhấn UC-86 / UC-87)

### UC-86 — Dashboard & Metrics Analytics (Admin)
- **Mục tiêu:** Admin xem tổng quan vận hành — KPI (số user, verification, operations, disputes, tickets), "recent activities" (audit log) và xu hướng.
- **Endpoint:** `GET /api/v1/admin/dashboard/...` (xem `AdminDashboardController`).
- **CC:** không truy cập (chỉ Admin — rule wildcard).
- **Audit action labels** được dịch trong `AdminDashboardService.formatActionDescription` — khi thêm module mới (dispute/ticket/config) phải bổ sung `case` ở đây để recent-activities đọc được tiếng Việt.

### UC-87 — User Management (Admin)
- **Mục tiêu:** Admin liệt kê/lọc (theo role/trạng thái/từ khóa), xem detail, **khóa/mở khóa** user + ghi audit.
- **Endpoint:** `GET/POST /api/v1/admin/users` (xem `AdminUserController`).
- **CC:** không truy cập user management.
- **Lưu ý kỹ thuật:** lock/unlock ghi `audit_logs` (BR-70) để trace nguồn.

### Phân quyền CC trong các module khác (BR-59)
- **Dispute:** CC (level-1) + Admin (final decision) cùng dùng `/v1/admin/disputes/**`; CC chỉ quyết ở `INVESTIGATING`, Admin quyết cuối ở `ESCALATED` (BR-68).
- **Support Ticket:** CC + Admin dùng `/v1/admin/support/**`; tự nhận/transfer giữa các CC (điểm "queue/mine" + "customer-care-staff").
- **eKYC/Skill queue:** CC **đã có** quyền — `SecurityConfig` mở `hasAnyRole("ADMIN","CUSTOMER_CARE")` cho `/api/v1/admin/ekyc/**`, `/api/v1/admin/eo-ekyc/**`, `/api/v1/workers/skills/admin/**` (fix đã code, **chưa commit** — xem `HANDOFF.md`). FE: 2 tab eKYC/skill trong `CustomerCarePage` dùng chung feature slice với `AdminPage`.

---

## 7. Git & Work in Progress

**Branch hiện tại:** `quyth-clean` (đã push → `origin/quyth-clean`)
**HEAD:** `cc3a75e "Update project context and handoff documentation"`
**✅ Bộ docs test-case đã commit 07/11/2026** (xem dưới)

### Commit gần nhất
| Commit | Nội dung |
|---|---|
| `cc3a75e` Update project context and handoff documentation | `HANDOFF.md` + `docs/PROJECT_CONTEXT.md` + `AGENTS.md` (sửa typo dòng tiêu đề) |
| `07a6af7` fix ekyc | **Fix CC 403** eKYC/skill queue: `SecurityConfig` mở CC cho ekyc/eo-ekyc/skills admin · `H2DataSeeder` seed `cc@cani.vn` · `CustomerCareQueueSecurityTest` (13 test) · 2 feature slice FE `ekyc-verification` + `skill-verification` (bỏ mock fallback) · xóa `AdminEkycTab`/`AdminSkillsTab` · +2 tab CC (**đang comment out chờ nhóm duyệt**) · `App.jsx` route `/`,`/worker` → `/login`, thêm `/cskh` → `/customer-care` · docs |
| `df3ab8d` skill CLI | `HANDOFF.md` + `docs/PROJECT_CONTEXT.md` |
| `ba29a55` Cấu hình hệ thống | System Config module (UC-114/115) + dispute `queue/mine` & `customer-care-staff` backend + global 403 handler + FE `features/system-configs/` + docs |
| `9cb0cb1` Chỉnh sửa ticket support | FE-12: `/queue/mine` + `/customer-care-staff` cho support, `TransferTicketModal`, route `/support`, docs |

### ✅ Bộ docs test-case (đã commit 07/11/2026 — không có source code)
```
docs/requirements/Report-5.1_Unit Test.xlsx            báo cáo unit test (binary 320 KB)
docs/requirements/unit-test-report-standard.md         chuẩn SE unit test report (IEEE 829 / ISO 29119) — đổi tên từ "SKILL (1).md"
docs/requirements/generate_service_unit_test_sheet.py script openpyxl sinh sheet unit test theo service
docs/test-cases/TEST_CASES_ADMIN_CUSTOMER_CARE.md      47 test case phân hệ Admin/CC
docs/test-cases/TEST_CASES_ADMIN_CUSTOMER_CARE.csv     bản Google Sheets
docs/test-cases/TEST_CASES_ADMIN_CUSTOMER_CARE.tsv     bản copy-paste
```
- 3 file test-case do agent sinh ra theo yêu cầu điền test case vào RP5.1.
- Đã sửa kèm: đường dẫn `tools/...` → `docs/requirements/...` (trong md + docstring script), `run.md` `cd Code/*` → `cd *`, `dispute-resolution-api.md` dòng 146 thiếu `/api`.

### 🟡 So sánh với nhánh khác (07/11/2026) — chưa merge
| So sánh | Ahead | Behind |
|---|---|---|
| `quyth-clean` vs `origin/main` | 27 | 0 |
| `quyth-clean` vs `origin/duong` / `origin/hung` | 21 | **71** |
| `quyth-clean` vs `origin/minhtb` | 21 | 61 |
| `quyth-clean` vs `origin/test` | 0 | 39 |

> **`origin/duong` + `origin/hung` đã fix sẵn 2/3 hạng mục P0** (dev-token bypass + IDOR toàn bộ controller) — xem `HANDOFF.md` §0. Overlap 18 file với nhánh này. **Vấn đề migration V12/V13 cần hỏi leader** (commit `4278929`).

> Chi tiết đầy đủ: `HANDOFF.md` §0–§3.

---

## 8. Known Issues & Unfinished Tasks

1. **🔴 UI eKYC/skill đang bị ẩn** — import + tab + view trong `AdminPage.jsx` và `CustomerCarePage.jsx` bị comment out ("Tạm thời ẩn … theo yêu cầu nhóm để xem xét thêm"). Backend đã sẵn sàng + 13 test pass, nhưng người dùng chưa thấy màn hình. Bật lại: bỏ comment 4 chỗ ở `AdminPage.jsx` + 2 khối ở `CustomerCarePage.jsx`.
2. **✅ 6 file test-case/unit-test docs đã commit** (07/11/2026) — `SKILL (1).md` đã đổi tên `unit-test-report-standard.md`, đường dẫn `tools/...` đã hiệu chỉnh (md + docstring script), kèm sửa `run.md` + `dispute-resolution-api.md`.
3. **🟡 E2E tay chưa chạy** cho fix CC 403 (queue eKYC/skill từ API thật, duyệt/từ chối 1 hồ sơ).
4. **🟡 CSS orphan** trong `web/src/pages/admin/AdminPage.css` (`.admin-verif-card`, `.admin-card-top`, `.admin-img-box`, `.admin-action-row`, `.admin-card-list`) sau khi xóa 2 tab cũ — dọn được nhưng **giữ `.btn-admin-approve`** (còn `UserStatusModal.jsx` dùng).
5. **🟡 `dev-token` bypass ngoài profile `h2`** — ⚠️ **ĐÃ CÓ FIX TRÊN `origin/duong`/`origin/hung`, chưa merge.** Nhánh này vẫn còn: `JwtConfig.java:30-40` chấp nhận `dev-token-*` trên mọi profile → bypass JWT trên `local`; `AuthService.login:147-152` fallback không verify password. Bản fix nhóm: xóa nhánh `dev-token` khỏi bean `!h2` + gate `AuthService` bằng `environment.acceptsProfiles("h2")` (profile khác → 503). → **Merge + verify, không tự code lại.**
6. **🟡 IDOR còn sót ở controller cũ** — ⚠️ **ĐÃ CÓ FIX TRÊN `origin/duong`/`origin/hung`, chưa merge.** Nhánh này vẫn còn `resolveUserId(authentication, explicitUserId)` ưu tiên `userId` query-param: `WorkerEkycController:104`, `EventOrganizerEkycController:96`, `WorkerJobController:153`, `DirectPaymentController:102`, `EoEventController:120`. Bản fix nhóm: xóa `explicitUserId` toàn bộ, dùng `com.cani.security.SecurityUtils.getCurrentUserId(authentication)`. Mobile không cần sửa (`ekyc_repository.dart:42,56` vẫn gửi param, Spring bỏ qua).
7. **🟡 Support ticket `reference_no` suffix 8 ký tự** (đã fix từ 4 → 8), vẫn UNIQUE constraint — rủi ro va chạm thấp.
8. **🟡 `App.jsx` route `/`,`/worker` → `/login`** — đã commit, chưa verify E2E (user chưa đăng nhập vào `/` giờ ra `/login` thay vì `/eo`).
9. **🟡 Test class còn thiếu trong scope:** `EoEventServiceTest` (chưa có); `AuthServiceTest` (chưa có dù SKILL standard mô tả mẫu 29 ca — kiểm tra scope trước).
10. **Unfinished (SRS, chưa code):** UC-74/54/56 RS Appeals + No-show; UC-108 Revenue Dashboard (FE mock, chưa backend); UC-110 FAQ (thiếu entity); UC-111 dashboard mở rộng (metrics disputes/assignments/RS); UC-113 RBAC; UC-107 duyệt đăng ký EO; UC-117 màn hình notification (SCR-10); UC-121 trang truy vấn audit độc lập. Mobile chưa có feature support-ticket/dispute (đang chờ backend).
    > Auto-escalation SLA job **không tính là thiếu** — `plan-fe12-support-tickets.md` §7.4 chốt "YAGNI, không viết scheduler"; cờ `slaAtRisk` đã đủ cho MVP.
11. **Backend không có devtools:** mọi lần sửa backend phải **restart** (đã xảy ra 2 lần "lỗi hệ thống" do chạy code cũ). Kiểm tra bằng `curl /api/health` + grep mapping trong startup log.

---

## 9. Bước làm tiếp được khuyến nghị (thứ tự)

> Chi tiết bổ sung trong `HANDOFF.md` (nguồn chính cho việc tiếp theo).

1. ~~**Xử lý 6 file test-case/unit-test docs đang staged**~~ → ✅ xong 07/11/2026 (đã commit).
2. **Hỏi leader 2 việc:** ① merge vào `test` hay `main`? ② commit `4278929` (duong) "align V12/V13 + promote V14" — V12/V13 đã apply Supabase chưa? (sửa migration đã áp dụng là vi phạm quy tắc repo).
3. **Sau khi leader merge → pull/rebase về `quyth-clean`**, chạy regression + `curl /api/health`, rồi **verify P0** (không code lại): profile `local` → `dev-token-*` phải 401; profile `h2` → vẫn 200; `GET .../status?userId=<khác>` → dữ liệu của chính caller.
4. **Bật lại UI eKYC/skill** khi nhóm duyệt (bỏ comment ở `AdminPage.jsx` + `CustomerCarePage.jsx`) → `npx vite build`.
5. **E2E tay** profile `h2` + web: đăng nhập `cc@cani.vn` (password bất kỳ — fallback h2 không verify) → `/customer-care` → tab eKYC/skill → xác nhận queue từ API thật → duyệt/từ chối 1 hồ sơ.
6. **Dọn CSS orphan** trong `AdminPage.css` (giữ `.btn-admin-approve`).
7. **Unfinished (theo priority SRS):** Revenue Dashboard (UC-108) → FAQ (UC-110) → RS Appeals/No-show (UC-74) → duyệt đăng ký EO (UC-107) → dashboard mở rộng (UC-111).

---

## 10. Quy tắc làm tính năng mới (đề cương nhanh)

**Module backend (checklist):**
1. Flyway migration (số V kế tiếp, kèm RLS deny-all + GRANT convention, trigger `updated_at`).
2. Enums + entities (`@JdbcTypeCode(NAMED_ENUM)`, JSON cho `jsonb`).
3. Repositories (derived query names).
4. DTOs (Jakarta Validation, message tiếng Việt).
5. Service: class-level `@Transactional(readOnly=true)`; ghi dùng `@Transactional` từng method; audit + notification; anti-IDOR (không lấy userId từ request).
6. 2 controller khi cần (public `/v1/…` + admin `/v1/admin/…` nếu CC dùng chung).
7. `SecurityConfig` — rule CC đặt **trước** wildcard `/v1/admin/**`.
8. `AdminDashboardService.formatActionDescription` — thêm nhãn audit mới.
9. Test Mockito service (Vietnamese `@DisplayName`).
10. `docs/api/<name>-api.md` (cấu trúc như `dispute-resolution-api.md` / `support-ticket-api.md`).

**Module FE:**
1. Slice `features/<name>/` (api/components/hooks/types/index).
2. Endpoint khớp đúng API doc (đối chiếu URL + payload + enum).
3. Branch UI staff vs user bằng `role === 'ADMIN' || 'CUSTOMER_CARE'`.
4. Gắn vào `AdminPage` và/hoặc `CustomerCarePage`.

**Luôn chạy sau khi sửa:**
- Backend: `./mvnw test -Dtest="<NewTest>,DisputeServiceTest,SupportTicketServiceTest,AdminDashboardServiceTest,AdminUserControllerTest"` (regression).
- Web: `npx vite build`.
- **Restart backend** sau mỗi thay đổi backend (không có devtools).

---

## 11. Tham chiếu nhanh

| Chủ đề | Đường dẫn |
|---|---|
| Nguyên tắc + kiến trúc (tóm tắt) | `CLAUDE.md` (gốc repo) |
| Chuẩn code team | `AGENTS.md` |
| Chạy hệ thống (3 service) | `run.md` |
| SRS / SDS (yêu cầu nghiệp vụ) | `docs/requirements/SEP490-G16-Report-{3,4}_*.docx` |
| API Dispute | `docs/api/dispute-resolution-api.md` |
| API Support Ticket | `docs/api/support-ticket-api.md` |
| API System Config | `docs/api/system-config-api.md` |
| API Dashboard (UC-86) | `docs/api/admin-dashboard-api.md` |
| API User Mgmt (UC-87) | `docs/api/admin-user-management-api.md` |
| API Skill / Auth | `docs/api/skill-verification-api.md`, `docs/api/auth-postman-guide.md` |
| Plan FE-12 | `docs/plan-fe12-support-tickets.md` |
| Test case Admin/CC (47 ca) | `docs/test-cases/TEST_CASES_ADMIN_CUSTOMER_CARE.md` (+ `.csv`, `.tsv`) |
| Chuẩn báo cáo unit test | `docs/requirements/unit-test-report-standard.md` · script `docs/requirements/generate_service_unit_test_sheet.py` |
| DB design | `docs/database/DBDesign.txt` |
| Migration | `backend/src/main/resources/db/migration/` (đến V13) |

> **Nguyên tắc của document này:** chỉ đánh dấu "đã hoàn thiện" khi có bằng chứng (test pass / build / đọc code). Các mục chưa kiểm chứng được đánh dấu 🟡/🔴 trong "Known Issues". Bí mật/`.env` không bao giờ được đưa vào.
