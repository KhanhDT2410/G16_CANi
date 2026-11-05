# CANi — Project Context (Handoff Document)

> **Mục đích:** Chấm điểm (snapshot) toàn bộ trạng thái hệ thống để session Claude Code mới khôi phục ngữ cảnh nhanh. Đọc cùng `CLAUDE.md` (nguyên tắc) và `AGENTS.md` (chuẩn code team).
> **Đã cập nhật lần cuối:** 05/11/2026. Mọi mục "Đã kiểm chứng" dưới đây đều được xác nhận bằng lệnh (compile / test / inspect code) tại thời điểm ghi.
>
> **⚠️ Đọc trước khi code (Critical):** Commit hiện tại (HEAD) **CHƯA compile được** vì thiếu một số file. Chi tiết trong mục "Git & Work in Progress" và "Known Issues — #1".

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
| **UC-96→104/109 Dispute** | ✅ `DisputeService`, `DisputeController` (public), `AdminDisputeController` (queue, **queue/mine**, **customer-care-staff**, assign, decision) + `V12` | ✅ feature `disputes` (2 view: CC/Admin queue + user "của tôi", 5+1 modal) | ✅ `DisputeServiceTest` (12) | **Lưu ý:** `queue/mine` + `customer-care-staff` hiện chỉ có trong **working tree**, chưa commit (thiếu `DisputeRepository`/`DisputeService`) — xem Known Issue #1 |
| **UC-105/106 Support Ticket** | ✅ `SupportTicketService`, `SupportTicketController`, `AdminSupportTicketController` (queue, **queue/mine**, **customer-care-staff**, assign, reply, close) + `V13` | ✅ feature `support-tickets` (view user + staff, 6 component + `TransferTicketModal`) | ✅ `SupportTicketServiceTest` (13) | commit ở `9cb0cb1` |
| **System Config** | ✅ `SystemConfigController` (`/v1/admin/configs`, `hasRole('ADMIN')`), `SystemConfigService/Repository`, DTO | ✅ feature `system-configs` (tab "Tham Số Hệ Thống") | ✅ `SystemConfigServiceTest` (9) + `SystemConfigControllerTest` (8) | **Untracked** (chưa commit); security dùng wildcard ADMIN — CC không truy cập (đúng design UC-114/115 chỉ Admin) |
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
- **eKYC/Skill queue:** hiện CC chưa có rule riêng trong `SecurityConfig` (chỉ disputes + support) → **CC bị 403** khi xem eKYC/skill queue. Xem Known Issue #3.

---

## 7. Git & Work in Progress

**Branch hiện tại:** `quyth-clean`
**HEAD:** `9cb0cb1 "Chỉnh sửa ticket support"`

### Commit gần nhất (đã commit, sạch)
| Commit | Nội dung |
|---|---|
| `9cb0cb1` Chỉnh sửa ticket support | FE-12: `/queue/mine` + `/customer-care-staff` cho support, `TransferTicketModal`, route `/support`, tab configs (untracked), docs |
| `f786a46` Fix Escalate Dispute | FE dispute: `queue/mine` + `customer-care-staff`, `TransferDisputeModal`, tab "Được giao tôi" + ESCALATED, docs, test +3 |
| `24cb802` Front End Ticket Support | Feature `support-tickets` (FE-12) |
| `b002727` chỉnh sửa Admin Dashboard | W-38 dashboard |
| `8f9e8c8` chỉnh sửa giao diện eKYC Admin | |
| `14102ec` Hoàn thiện backend FE11-Ticket | Backend support ticket + V13 |

### ⚠️ WORKING TREE CHƯA COMMIT (git status)
```
 M backend/.../exception/GlobalExceptionHandler.java   # thêm AccessDenied → 403
 M backend/.../repository/DisputeRepository.java       # + findByStatusInAndAssignedTo(+IsNull)
 M backend/.../service/DisputeService.java              # + QUEUE_STATUSES, getDisputeQueueForCc, getUnassignedQueue, toQueueItems
 M web/src/pages/admin/AdminPage.jsx                    # tab "Tham Số Hệ Thống"
?? backend/.../controller/SystemConfigController.java
?? backend/.../dto/admin/config/
?? backend/.../repository/SystemConfigRepository.java
?? backend/.../service/SystemConfigService.java
?? backend/.../test/.../SystemConfig{Service,Controller}Test.java
?? docs/api/system-config-api.md
?? web/src/features/system-configs/
```

**Trạng thái:** code **đã compile + 49/49 test pass + build xanh** trên working tree. Nhưng nếu commit hiện tại (HEAD) được checkout lại, nó **thiếu** `DisputeService`/`DisputeRepository` nên **không compile được** (mà FE `f786a46` đã gọi các method đó). → Cần commit working tree để làm cho commit hợp lệ (Known Issue #1).

---

## 8. Known Issues & Unfinished Tasks

1. **🔴 HEAD không compile — cần commit working tree.** `f786a46` (FE dispute) tham chiếu `getDisputeQueueForCc` / `customer-care-staff` (backend) nhưng backend đó **chưa commit** (ở `DisputeService.java`/`DisputeRepository.java` — hiện là modified, chưa push). **Hành động:** commit toàn bộ working tree (mục 7) thành 1–2 commit; sau đó `git checkout f786a46` vẫn cần backend mới. *Đã kiểm chứng:* `git show 9cb0cb1:…/DisputeService.java` = 0 matches `getDisputeQueueForCc`.
2. **🟡 CC bị 403 trên eKYC/Skill queue (SRS yêu cầu CC được xem).** `SecurityConfig` chỉ mở CC cho `disputes` + `support`; eKYC (`/v1/admin/ekyc/**`, `/v1/admin/eo-ekyc/**`) và skill (`/v1/workers/skills/admin/**`) vẫn `hasRole("ADMIN")` duy nhất → CC 403. *Lưu ý:* đây là issue từ trước, chưa phải việc của FE-12.
3. **🟡 `AdminPage.jsx` (UC-86/87 view) 893 dòng + eKYC/skill queue inline** — vượt chuẩn <200 dòng (AGENTS.md §1). Các tab queue hiện mới tách thành component; eKYC/skill vẫn dính trong `AdminPage.jsx` → nên tách `AdminEkycTab`/`AdminSkillsTab` (đã tồn tại trong tree).
4. **🟡 Support ticket `reference_no` suffix 8 ký tự** (đã fix từ 4 → 8 trong `SupportTicketService`), vẫn UNIQUE constraint — rủi ro va chạm thấp nhưng không loại trừ. Ghi chú: hiện tại dùng `UUID.randomUUID().substring(0,8).toUpperCase()`.
5. **Unfinished (SRS, chưa code):** UC-74/54/56 RS Appeals + No-show; UC-108 Revenue Dashboard (FE mock, chưa backend); UC-110 FAQ (thiếu entity); UC-111 dashboard mở rộng (metrics disputes/assignments/RS); auto-escalation SLA job (inventory SRS). Mobile chưa có feature support-ticket/dispute (đang chờ backend).
6. **Backend không có devtools:** mọi lần sửa backend phải **restart** (đã xảy ra 2 lần "lỗi hệ thống" do chạy code cũ). Kiểm tra bằng `curl /api/health` + grep mapping trong startup log.

---

## 9. Bước làm tiếp được khuyến nghị (thứ tự)

1. **Commit working tree ngay** (giải Known Issue #1) — tách 2 commit rõ ràng:
   - `backend: add dispute queue/mine + customer-care-staff & global 403 handler` (3 file `M` + test)
   - `backend: system config module + web configs tab` (SystemConfig* + AdminPage + docs)
2. **Fix Known Issue #2** (2 dòng `SecurityConfig`): thêm
   ```java
   .requestMatchers("/api/v1/admin/ekyc/**", "/api/v1/admin/eo-ekyc/**").hasAnyRole("ADMIN","CUSTOMER_CARE")
   ```
   đặt trước wildcard; kèm test. (CC 403 fix — SRS UC-47 yêu cầu.)
3. **Kiểm tra lại đầu cuối bằng E2E thật** (bật backend + frontend, đăng nhập tài khoản `admin@`/`cskh@`/`worker@` trên profile h2, click qua: tạo ticket → CC queue → nhận/transfer → đóng; nộp dispute → CC → escalate → Admin final).
4. **Tách `AdminPage.jsx`** (Known Issue #3) — rút eKYC/skill queue ra component riêng, chuẩn <200 dòng.
5. **Unfinished (theo priority SRS):** Revenue Dashboard (UC-108) → FAQ (UC-110) → RS Appeals/No-show (UC-74) → dashboard mở rộng (UC-111).

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
| DB design | `docs/database/DBDesign.txt` |
| Migration | `backend/src/main/resources/db/migration/` (đến V13) |

> **Nguyên tắc của document này:** chỉ đánh dấu "đã hoàn thiện" khi có bằng chứng (test pass / build / đọc code). Các mục chưa kiểm chứng được đánh dấu 🟡/🔴 trong "Known Issues". Bí mật/`.env` không bao giờ được đưa vào.
