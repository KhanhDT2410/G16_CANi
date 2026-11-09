# HANDOFF.md — Chuyển phiên Claude Code

> **Mục đích:** cho hội thoại mới khôi phục đúng trạng thái công việc. Đọc kèm `CLAUDE.md` (nguyên tắc), `AGENTS.md` (chuẩn code team), `docs/PROJECT_CONTEXT.md` (snapshot chi tiết).
> **Cập nhật:** 08/11/2026 (sau commit `5e5a13f`). Đã đối chiếu với code + git thực tế, có chạy test.

---

## 0. Trạng thái hiện tại (đọc đầu tiên)

- **HEAD:** `5e5a13f` "them dashboard doanh thu (UC-108), khang cao diem RS (UC-94) va job canh bao SLA" → đã push `origin/quyth-clean`. Working tree **SẠCH**.
- **2 commit mới đã gộp:** `77e021f` "FAQ" (module UC-110) + `5e5a13f` (UC-108 Revenue Dashboard + UC-94 RS Appeal + SLA monitoring + FAQ). Commit `5e5a13f` rất lớn: **73 files, +6303/−9**.
- **🔴 REVERT BẢO MẬT ĐÃ ĐƯỢC COMMIT VÀO REPO** — xem §0.1. Không còn là vấn đề "working tree" nữa, mà là **HEAD hiện chứa bản không an toàn**.
- **🔴 Full backend test suite ĐANG ĐỎ** — xem §0.3 (157 test, 1 failure + 3 errors).
- **UI eKYC/skill vẫn bị ẩn** theo yêu cầu nhóm (comment out ở `AdminPage.jsx` + `CustomerCarePage.jsx`). Feature slice nguyên vẹn → bật lại chỉ cần bỏ comment.
- **Migrations đã lên V18:** V13 support_tickets · V14 shift checklist · V15 faqs · V16 rs_appeals · V17 sla_monitoring · V18 no_show_management.
- **✅ Đã hoàn thành Module No-show (UC-88):** Backend (`NoShowService` + `AdminNoShowController` + `V18`), Frontend slice `features/no-shows/` (10 files), 14/14 tests pass, `vite build` pass, docs `docs/api/no-show-api.md`.

---

## 0.1 🔴🔴 REVERT BẢO MẬT — ĐÃ COMMIT, HEAD HIỆN KHÔNG AN TOÀN

**Đây là việc quan trọng nhất cần xử lý tiếp.**

Commit `77e021f` ("FAQ") **đã kéo theo** các thay đổi làm revert phần `dev-token` của commit `41fb598`. Commit `5e5a13f` giữ nguyên tình trạng đó. **Kiểm chứng trên HEAD hiện tại (`5e5a13f`):**

| File | Bản AN TOÀN (commit `41fb598`) | Bản ĐANG CÓ Ở HEAD (không an toàn) |
|---|---|---|
| `config/JwtConfig.java` | xóa nhánh `dev-token-*` khỏi bean `@Profile("!h2")` → chỉ profile `h2` chấp nhận | **HEAD:30 vẫn CÓ** nhánh `dev-token-*` trong bean `@Profile("!h2")` → bypass JWT ở **mọi profile** (kể cả `local`/Supabase thật) |
| `service/AuthService.java` | fallback dev-token **gate** bằng `environment.acceptsProfiles("h2")`; profile khác → `AppException` 503 | **HEAD:150 fallback vô điều kiện** → mọi profile đều nhận `dev-token-*` khi Supabase lỗi (không có `acceptsProfiles`, không có `SERVICE_UNAVAILABLE`) |
| `test/.../AuthServiceDevTokenTest.java` | 3 test (h2 cấp dev-token / profile khác 503 / không lộ account existence) | **ĐÃ BỊ XÓA** (không còn trong repo) |

**Phần IDOR thì VẪN CÒN và vẫn đúng:** `security/SecurityUtils.java` tồn tại; `WorkerEkycController` + `EventOrganizerEkycController` dùng `SecurityUtils.getCurrentUserId(authentication)`, đã bỏ `@RequestParam userId`; `EkycUserIdParamIgnoredTest` (4 test) còn & pass.

**Cách khôi phục (đã xác minh HEAD hiện tại = bản xấu, nên `git checkout` từ `41fb598` là an toàn):**
```
git checkout 41fb598 -- backend/src/main/java/com/cani/config/JwtConfig.java \
  backend/src/main/java/com/cani/service/AuthService.java \
  backend/src/test/java/com/cani/service/AuthServiceDevTokenTest.java
```
⚠️ **Kiểm tra diff `AuthService.java` trước** — commit `5e5a13f` có thể đã thêm thay đổi khác vào file này (`git diff 41fb598 5e5a13f -- .../AuthService.java`) → nếu có, chỉ lấy phần liên quan `acceptsProfiles` + `AppException`, **không** checkout đè toàn file.

**Lưu ý:** `origin/duong` **cũng đã có** bản fix sửa (comment trong file ghi "ponytail: …") → khi merge, **lấy bản của `duong`**, đừng tự viết lại.

**Việc phiên sau phải làm:** hỏi user vì sao bản an toàn bị mất (chủ ý hay nhầm khi gom commit), rồi khôi phục hoặc giữ nguyên — và nếu giữ nguyên thì **phải ghi rõ vào docs** để không ai tưởng là đã vá.

---

## 0.2 Module mới trong `5e5a13f` (chi tiết §3)

| Module | UC | Backend | FE | Migration | Test | Docs |
|---|---|---|---|---|---|---|
| **Revenue Dashboard** | UC-108 | `AdminRevenueController` + `RevenueService` + 8 DTO (`dto/admin/revenue/*`) + `PaymentRepository`/`SubscriptionRepository`/`EventRepository`/`UserRepository` mở rộng | `features/revenue-dashboard/` (10 file: `RevenueDashboardView`, `RevenueMetricsCards`, `RevenuePackageBreakdown`, `RevenueVnPaySummary`, `RevenueTransactionsTable`, `RevenueFlaggedTable`, `useRevenueDashboard`) | — | `RevenueServiceTest` + `AdminRevenueControllerTest` | `docs/api/revenue-dashboard-api.md` |
| **RS Appeal** | UC-94 | `RsAppeal` entity + `RsAppealStatus` enum + `RsAppealRepository` + `RsAppealService` + `AdminAppealController` + `WorkerAppealController` + 3 DTO + `DisputeRepository` mở rộng | `features/rs-appeals/` (10 file: `RsAppealsView`, `RsAppealsTable`, `RsAppealDetailModal`, `RsAppealReviewModal`, `RsAppealStatusBadge`) | `V16__rs_appeals.sql` | `RsAppealServiceTest` + `RsAppealControllerTest` | `docs/api/rs-appeal-api.md` |
| **SLA monitoring job** | — | `SlaMonitoringService` + `SlaAlertLog` entity + `SlaAlertLogRepository` + `SchedulingConfig` + `SlaScanReportDto` + `NotificationService` (mới) + `NotificationType` enum mở rộng | — (hiển thị qua dashboard) | `V17__sla_monitoring.sql` | `SlaMonitoringServiceTest` | `docs/api/sla-monitoring-api.md` |
| **FAQ** | UC-110 | `Faq` + 2 enum + `FaqRepository` + `FaqService` + `FaqController` + `AdminFaqController` + 5 DTO | `features/faqs/` (9 file) + tab ở Admin/CustomerCare/SupportPage | `V15__faq.sql` | `FaqServiceTest` (11) | `docs/api/faq-api.md` |

> ⚠️ **Lưu ý:** `docs/plan-fe12-support-tickets.md` §7.4 từng chốt "YAGNI — không viết scheduler" cho auto-escalation SLA, nhưng `5e5a13f` **đã viết** `SlaMonitoringService` + `SchedulingConfig`. Cần hỏi leader/team xem đây là quyết định có chủ ý (có thể đã đổi ý) → cập nhật lại `plan-fe12-support-tickets.md` cho khớp.

---

## 0.3 🔴 Full backend test suite ĐANG ĐỎ (chạy thật 08/11/2026)

```
./mvnw.cmd test   → Tests run: 157, Failures: 1, Errors: 3 | BUILD FAILURE
```
**2 class fail — KHÔNG liên quan FAQ/revenue (có vẻ là tồn đọng từ refactor eKYC "không AI" + worker job):**

| Test | Lỗi |
|---|---|
| `WorkerEkycServiceTest.shouldRejectWhenDuplicateCccdDetected:157` | `expected: "REJECTED" but was: "APPROVED"` — logic trùng CCCD đã bị đổi khi refactor eKYC bỏ AI |
| `WorkerEkycServiceTest.shouldAutoApproveEkycWhenAiReturnsHighScore` | `UnnecessaryStubbingException` (stub thừa ở :90) |
| `WorkerEkycServiceTest.shouldSetPendingReviewWhenAiConfidenceIsMedium` | `UnnecessaryStubbingException` (stub thừa ở :121) |
| `WorkerJobServiceTest.shouldAcceptProposalSuccessfully:118` | `NullPointerException` — `attendanceLogRepository` null trong `WorkerJobService.mapToAssignmentDto:254` (thiếu `@Mock` trong test) |

**Nguồn:** `git log` cho `WorkerEkycService.java` → commit `9f01b50` "feat(ekyc): refactor ekyc without AI, add CCCD QR scanner & confirm dialog, map radar & terms of service" (của nhánh khác, không phải commit của user). `WorkerJobService` → `7020ebe`.
→ **Việc phiên sau: xác nhận với user/leader các fail này có cần fix trước khi merge `test` hay để nhóm khác xử lý.** Regression set (7 class của `CLAUDE.md` §Rules 5) vẫn xanh — chỉ 2 class này đỏ.

---

## 0.4 Verify trạng thái hiện tại (chạy thật tại phiên này)

```
./mvnw.cmd test -Dtest="FaqServiceTest,EkycUserIdParamIgnoredTest"
  → FaqServiceTest: Tests run: 11, Failures: 0, Errors: 0
  → EkycUserIdParamIgnoredTest: Tests run: 4, Failures: 0, Errors: 0
  → Tests run: 15, Failures: 0, Errors: 0 | BUILD SUCCESS
```
(`AuthServiceDevTokenTest` không chạy vì file đã bị xóa.)
`npx vite build` — cần chạy lại sau commit `5e5a13f` (73 file FE mới) để xác nhận.

---

## 1. Phiên trước đã làm gì

### 1.1 Commit `5e5a13f` — UC-108 Revenue Dashboard + UC-94 RS Appeal + SLA monitoring (73 files, +6303/−9)
Chi tiết module ở **§0.2**. Đây là commit lớn nhất từ trước đến nay trên nhánh này — gộp cả 3 module SRS chưa code trước đó (ngoài FAQ đã ở `77e021f`).
Kèm: `H2DataSeeder` mở rộng (+184 dòng seed data), `application.yml` + `application-h2.yml` (cấu hình scheduler), `NotificationService` mới, `NotificationType` enum mở rộng.

### 1.2 Commit `77e021f` — module UC-110 FAQ & Help Center (39 files)
Backend: `Faq` + `FaqCategory`/`FaqStatus` + `FaqRepository` + `FaqService` + `FaqController` (`/api/v1/faqs` permitAll) + `AdminFaqController` (`/api/v1/admin/faqs` CC+Admin) + 5 DTO + `V15__faq.sql` + `FaqServiceTest` (11 test).
FE: `features/faqs/` (9 file, mọi component < 200 dòng) + tab ở `AdminPage`/`CustomerCarePage`/`SupportPage` + `ErrorBoundary` trong `App.jsx`.
Docs: `docs/api/faq-api.md`. Kèm `V14__split_shift_checklist_template_and_progress.sql` (từ `origin/duong`) + `all_ucs_srs.txt` (121 dòng trích UC từ SRS).
**⚠️ Commit này cũng kéo theo phần revert bảo mật — xem §0.1.**

### 1.3 Fix CC 403 (commit `07a6af7` — đã commit ✅)
**Phân tích đã khoanh vùng (nguyên nhân gốc):** 403 nằm ở **tầng URL rule** của `SecurityConfig`, **không** phải `@PreAuthorize`:
- `@PreAuthorize("hasAnyRole('ADMIN','CUSTOMER_CARE')")` đã có sẵn ở cả 3 controller ✅
- `UserAuthorityFilter:43` gán `ROLE_` + `user.getRole().name()` → `ROLE_CUSTOMER_CARE` ✅
- URL rule mới là chỗ chặn ❌

Endpoint bị chặn trước khi fix:
| Endpoint | Controller |
|---|---|
| `GET/POST /api/v1/admin/ekyc/...` | `WorkerEkycController:82,92` |
| `GET/POST /api/v1/admin/eo-ekyc/...` | `EventOrganizerEkycController:74,84` |
| `GET/POST /api/v1/workers/skills/admin/...` | `SkillVerificationController:131,141` |

---

## 2. Trạng thái Git

- **Branch:** `quyth-clean` · **HEAD:** `5e5a13f` "them dashboard doanh thu (UC-108), khang cao diem RS (UC-94) va job canh bao SLA" · **đã push** → `origin/quyth-clean` ✅
- **Working tree: SẠCH** ✅ (không có gì chưa commit).
- **Main:** `main` (`702a91c`). Nhánh khác: `quyth`, `duong` (ahead 6, behind 56), `hung`, `minhtb`, `test` (`5ba29a0`), `backup/quyth-with-duong-merge`.
- **Commit gần nhất:** `5e5a13f` (UC-108 + UC-94 + SLA) → `77e021f` (FAQ) → `41fb598` (fix security P0) → `ed3238b` (docs test-case) → `cc3a75e` (docs) → `07a6af7` (fix ekyc).

---

## 3. Nội dung commit `07a6af7` "fix ekyc" (23 files, +1122/−420)

### Backend — modified
| File | Nội dung |
|---|---|
| `config/SecurityConfig.java` | `:51` skills admin → `hasAnyRole("ADMIN","CUSTOMER_CARE")`; **thêm rule mới** `"/api/v1/admin/ekyc/**", "/api/v1/admin/eo-ekyc/**"` → `hasAnyRole("ADMIN","CUSTOMER_CARE")`, đặt **trước** wildcard `/api/v1/admin/**` (dòng ~61). Comment tiếng Việt theo style rule CC sẵn có. Rule System Config **không** đụng (UC-114/115 cố ý ADMIN-only). |
| `config/H2DataSeeder.java` | Seed user `cc@cani.vn` / id `11111111-1111-1111-1111-111111111111` / role `CUSTOMER_CARE` / `ACTIVE` (chỉ profile `h2`). Dùng `userRepository.existsByEmail(...)` guard, giống block seed Worker. |

### Backend — new
- `src/test/java/com/cani/controller/CustomerCareQueueSecurityTest.java` — `@WebMvcTest` 3 controller + `@Import(SecurityConfig…)`, mock `JwtDecoder`/`UserRepository`/3 service. **13 test**: CC 200 (GET queue + POST review, cả worker/eo-ekyc/skill), WORKER 403, EVENT_ORGANIZER 403, anonymous 401. `@DisplayName` tiếng Việt.

### Web — modified
| File | Nội dung |
|---|---|
| `pages/admin/AdminPage.jsx` | Import + 2 tab + 2 view eKYC/skill **đã bị comment out** (xem §0). Đồng thời đổi mô tả header → "Giám sát vận hành, quản trị người dùng và cấu hình hệ thống". |
| `pages/customer-care/CustomerCarePage.jsx` | 2 tab "Hàng Đợi eKYC" / "Hàng Đợi Kỹ Năng AI" **đã bị comment out** (xem §0). Giữ `flexWrap: 'wrap'` cho thanh tab. |
| `features/disputes/components/DisputeCenterView.jsx` | Xóa dead code `escalatedCount` (useMemo không còn dùng; `:154` đã tự filter). `grep escalatedCount` toàn repo → 0 match. |
| `App.jsx` | Route index `/` và `/worker`: `<Navigate to="/eo">` → `<Navigate to="/login">`. Thêm route fallback `/cskh` → `/customer-care` (`App.jsx:49-50`). |

### Web — deleted
- `pages/admin/components/AdminEkycTab.jsx` (175 dòng) — chuyển thành feature slice.
- `pages/admin/components/AdminSkillsTab.jsx` (147 dòng) — chuyển thành feature slice.
- → Thư mục `web/src/pages/admin/components/` **đã bị xóa hoàn toàn** (không còn file nào).

### Web — new (2 feature slice đầy đủ theo `AGENTS.md` §1) — ĐÃ TẠO NHƯNG CHƯA DÙNG (bị comment out)
```
features/ekyc-verification/
  api/ekycVerificationApi.js          getWorkerEkycQueue, reviewWorkerEkyc, getEoEkycQueue, reviewEoEkyc
  components/EkycVerificationView.jsx  (96 dòng)
  components/EkycVerification.css
  hooks/useEkycVerification.js         (60 dòng)
  types/ekycVerification.constants.js  EKYC_DECISION
  index.js
features/skill-verification/
  api/skillVerificationApi.js          getPendingSkillQueue, reviewSkill (params: approved, reviewNotes)
  components/SkillVerificationView.jsx (103 dòng)
  components/SkillVerification.css
  hooks/useSkillVerification.js        (59 dòng)
  types/skill-verification.constants.js
  index.js
```
→ Code trong 2 slice này là bản **đã bỏ mock fallback** (đã verify: `grep -rn "Mock Fallback\|fallback = \[\|mockData" web/src/` → 0 match).

### 3.1 — ✅ Bộ docs test-case ĐÃ COMMIT (07/11/2026 — toàn bộ là docs, không có source code)
```
A  docs/requirements/Report-5.1_Unit Test.xlsx            (320 KB, binary — báo cáo unit test)
A  docs/requirements/unit-test-report-standard.md         (208 dòng — chuẩn SE unit test report, IEEE 829 / ISO 29119)
A  docs/requirements/generate_service_unit_test_sheet.py  (279 dòng — script openpyxl sinh sheet unit test theo service)
A  docs/test-cases/TEST_CASES_ADMIN_CUSTOMER_CARE.md      (47 ca kiểm thử phân hệ Admin/CC: UC-86/87/114/115/96→109/105/106 + eKYC/skill)
A  docs/test-cases/TEST_CASES_ADMIN_CUSTOMER_CARE.csv     (bản Google Sheets)
A  docs/test-cases/TEST_CASES_ADMIN_CUSTOMER_CARE.tsv     (bản copy-paste)
```
- **Nguồn gốc:** 3 file test-case do agent sinh ra theo yêu cầu điền test case vào RP5.1 (Report 5.1 Unit Test).
- **Đã xử lý trong lần commit này:**
  - `SKILL (1).md` → **đổi tên** thành `docs/requirements/unit-test-report-standard.md` (bỏ khoảng trắng + "(1)" là dấu file tải về).
  - Sửa đường dẫn sai trong `unit-test-report-standard.md` §5 Bước 3: `tools/generate_service_unit_test_sheet.py` (trỏ về `C:/Users/ASUS/Downloads/...` của máy khác) → `docs/requirements/generate_service_unit_test_sheet.py`.
  - Sửa docstring `generate_service_unit_test_sheet.py`: `Module:`/`Usage:` → `docs/requirements/...`.
  - Sửa `run.md`: `cd Code/backend|web|mobile` → `cd backend|web|mobile` (đường dẫn tương đối trong repo).
  - Sửa `docs/api/dispute-resolution-api.md`: dòng 146 thiếu `/api` trong `GET /v1/admin/disputes/customer-care-staff` → `/api/v1/...`.
- **Ghi chú:** 3 file test-case **không trùng** với nhóm — `origin/duong` chỉ sửa `docs/UNIT_TEST_REPORT_EXPORT.md` (file khác) và không có thư mục `docs/test-cases/` → không conflict khi merge.

### 3.2 — ✅ 13 file MODIFIED (chưa commit) → **ĐÃ COMMIT** trong `77e021f`/`5e5a13f` (08/11/2026)
```
M backend/.../config/H2DataSeeder.java         + seed admin@cani.vn (role ADMIN, id 00000000-...-0001)
M backend/.../config/JwtConfig.java            ⚠️ REVERT: nhánh dev-token-* quay lại bean @Profile("!h2")
M backend/.../config/SecurityConfig.java        + rule /api/v1/admin/faqs/** (CC) + /api/v1/faqs/** (permitAll) — ĐÚNG
M backend/.../repository/SupportTicketRepository.java  + countByCategory(TicketCategory)
M backend/.../service/AdminDashboardService.java  + 4 nhãn audit CREATE/UPDATE/PUBLISH/ARCHIVE_FAQ — ĐÚNG
M backend/.../service/AuthService.java          ⚠️ REVERT: bỏ gate environment.acceptsProfiles("h2") + bỏ AppException 503
D backend/src/test/.../AuthServiceDevTokenTest.java   ⚠️ BỊ XÓA (3 test)
M web/src/App.jsx                               + ErrorBoundary class component (bắt lỗi UI, nút "Tải lại trang" / "Xóa Cache & Đăng nhập lại")
M web/src/pages/admin/AdminPage.jsx             + tab "Câu Hỏi Thường Gặp" (FaqView)
M web/src/pages/customer-care/CustomerCarePage.jsx + tab "Câu Hỏi Thường Gặp (FAQ)"
M web/src/pages/support/SupportPage.jsx         + 2 tab: FAQ (mặc định) + Tickets; dùng FaqPublicList
M docs/PROJECT_CONTEXT.md, HANDOFF.md           (docs)
```
→ **3 file an toàn bị revert: `JwtConfig.java`, `AuthService.java`, `AuthServiceDevTokenTest.java` (deleted) — và đã được COMMIT vào HEAD.** Xem bảng so sánh ở **§0.1**.

### 3.3 — ✅ 14 file UNTRACKED (chưa commit) → **ĐÃ COMMIT** trong `77e021f` (module UC-110 FAQ)
```
?? backend/src/main/java/com/cani/entity/Faq.java
?? backend/src/main/java/com/cani/entity/enums/FaqCategory.java      (ACCOUNT, SHIFT, PAYMENT, DISPUTE)
?? backend/src/main/java/com/cani/entity/enums/FaqStatus.java        (DRAFT, PUBLISHED, ARCHIVED)
?? backend/src/main/java/com/cani/repository/FaqRepository.java
?? backend/src/main/java/com/cani/service/FaqService.java
?? backend/src/main/java/com/cani/controller/FaqController.java       @RequestMapping /api/v1/faqs (GET list, GET /{id})
?? backend/src/main/java/com/cani/controller/AdminFaqController.java  @RequestMapping /api/v1/admin/faqs (GET, GET/{id}, POST, PUT/{id}, POST /{id}/publish, POST /{id}/archive, GET /analytics)
?? backend/src/main/java/com/cani/dto/faq/                           5 DTO: Create/Update/FaqResponse/FaqPublicResponse/FaqAnalytics
?? backend/src/test/java/com/cani/service/FaqServiceTest.java         11 test — PASS
?? backend/src/main/resources/db/migration/V15__faq.sql              bảng faqs + 2 enum + RLS deny-all + trigger updated_at
?? backend/src/main/resources/db/migration/V14__split_shift_checklist_template_and_progress.sql  (đã checkout từ origin/duong)
?? docs/api/faq-api.md
?? web/src/features/faqs/     api/faqApi.js (66) · components/FaqView.jsx (196) · FaqCard.jsx (103) · FaqEditorModal.jsx (143) · FaqPublicList.jsx (127) · Faq.css (653) · hooks/useFaqs.js (183) · types/faq.constants.js (30) · index.js
?? all_ucs_srs.txt            121 dòng — trích xuất toàn bộ UC từ SRS (UC-01…UC-121?)
```
→ Tất cả component FE < 200 dòng ✅ (trừ `Faq.css` là file CSS, không tính). Verify tại phiên trước: 65/65 test pass + `vite build` 2.54s + 6 curl thực tế trên profile `h2`.

### 3.4 — ✅ UC-108 + UC-94 + SLA (commit `5e5a13f`, 08/11/2026)
Chi tiết module đã mô tả ở **§0.2** + §1.1. Không cần liệt kê lại toàn bộ 73 file — xem `git show --stat 5e5a13f`.

---

## 4. Điểm quan trọng: mock fallback đã gỡ (fix ẩn bug 403)

`AdminEkycTab`/`AdminSkillsTab` cũ có **mock fallback data** khi API lỗi và **nuốt lỗi** review → báo "thành công" giả. Đây là lý do bug 403 bị che khi demo.

Đã sửa trong 2 hook mới: `catch (err)` → `showToast(err.response?.data?.message || '…', 'error')` + `setQueue([])`.
Verify: `grep -rn "Mock Fallback\|fallback = \[\|mockData" web/src/` → **0 match**.

---

## 5. Kết quả kiểm chứng (chạy thật, không diễn giải)

**Phiên 08/11/2026 (sau `5e5a13f`):**
```
./mvnw.cmd test                                     → Tests run: 157, Failures: 1, Errors: 3 | BUILD FAILURE
   ✗ WorkerEkycServiceTest (1 fail + 2 errors)     → xem §0.3
   ✗ WorkerJobServiceTest (1 error, NPE)            → xem §0.3
./mvnw.cmd test -Dtest="FaqServiceTest,EkycUserIdParamIgnoredTest"
                                                     → Tests run: 15, Failures: 0, Errors: 0 | BUILD SUCCESS
   FaqServiceTest: 11 · EkycUserIdParamIgnoredTest: 4
npx vite build (web/)                                → ⚠️ CHƯA chạy lại sau 73 file FE mới → xem §8.3
```

**Phiên trước (07/11/2026, trước `5e5a13f`):**
```
./mvnw.cmd test -Dtest="CustomerCareQueueSecurityTest"          → Tests run: 13, Failures: 0, Errors: 0 | BUILD SUCCESS
./mvnw.cmd test -Dtest="<7 class regression set>"                 → Tests run: 62, Failures: 0, Errors: 0 | BUILD SUCCESS
npx vite build (web/)                                              → ✓ built in 1.65s
```

> ⚠️ **Lưu ý Windows:** phải dùng `./mvnw.cmd` (không phải `./mvnw`) — shell POSIX không parse được `.mvn/jvm.config` (`-Xmx2048m`) → `ClassNotFoundException: "-Xmx64m"`.

**Chưa làm: E2E tay** (bật backend profile `h2` + web, đăng nhập `cc@cani.vn`, xem queue load từ API thật, duyệt/từ chối 1 hồ sơ; cũng nên kiểm tra 3 module mới: Revenue Dashboard, RS Appeal, FAQ).

---

## 6. Lỗi / việc còn tồn tại

1. 🔴 **UI eKYC/skill bị ẩn theo yêu cầu nhóm** — trong `AdminPage.jsx` và `CustomerCarePage.jsx`, import + tab + view đều bị comment out ("Tạm thời ẩn … theo yêu cầu nhóm để xem xét thêm"). Backend đã sẵn sàng + test pass nhưng **người dùng chưa thấy được màn hình**. Khi nhóm đồng ý bật: bỏ comment ở 4 chỗ (2 import, 2 khối render) + 2 khối tab trong `CustomerCarePage.jsx` → rồi chạy lại `npx vite build`.
2. 🟡 **CSS orphan trong `web/src/pages/admin/AdminPage.css`** — sau khi xóa 2 tab cũ, các class `.admin-verif-card` (:86), `.admin-card-top` (:94), `.admin-img-box` (:108, :116), `.admin-action-row` (:131), `.admin-card-list` (:80) **không còn component nào dùng**. Riêng `.btn-admin-approve` (:138) **vẫn được** `features/admin-users/components/UserStatusModal.jsx:93` dùng lại → **đừng xóa** class này.
3. 🟡 **E2E tay chưa chạy** — bật backend profile `h2` + web, đăng nhập `cc@cani.vn`, xác nhận queue eKYC/skill load từ API thật, duyệt/từ chối 1 hồ sơ, xác nhận request có quyền (không 403). Lưu ý: UI đang bị ẩn nên **phải bật lại tab trước** (mục 1) hoặc test bằng `curl`.
4. 🟡 `reference_no` ticket suffix 8 ký tự — vẫn còn rủi ro va chạm UNIQUE (thấp).
5. ✅ **Số V Migration:** chuỗi Flyway trên nhánh hiện tại **V12 → V13 → V14 → V15 → V16 → V17** (support_tickets · shift checklist · faqs · rs_appeals · sla_monitoring), đã commit đầy đủ, không đứt gãy khi merge.
6. 🔴🔴 **`dev-token` bypass ngoài profile `h2`** — **ĐÃ COMMIT BẢN KHÔNG AN TOÀN VÀO REPO** (qua `77e021f`). HEAD `5e5a13f` vẫn có nhánh `dev-token-*` trong bean `@Profile("!h2")` + `AuthService` không gate. Chi tiết + lệnh khôi phục ở **§0.1**.
7. 🔴 **Full backend test suite ĐỎ** — 157 test, 1 failure + 3 errors ở `WorkerEkycServiceTest` + `WorkerJobServiceTest` (không liên quan module mới; nguồn: commit `9f01b50` refactor eKYC bỏ AI + `7020ebe`). Chi tiết ở **§0.3**.
8. ✅ **IDOR ở 2 controller eKYC** — **ĐÃ VÁ 07/11/2026 — VẪN CÒN NGUYÊN trên HEAD (KHÔNG bị revert).**
   - Thêm `com.cani.security.SecurityUtils.getCurrentUserId(Authentication)` — chỉ đọc `principal instanceof User`, không nhận userId từ request (đúng quy tắc `CLAUDE.md`).
   - `WorkerEkycController` + `EventOrganizerEkycController` — xóa `@RequestParam userId` ở 6 endpoint (submit/status/review ×2) và helper `resolveUserId(authentication, explicitUserId)`.
   - Endpoint review: `adminUserId` giờ = người CC/Admin đang đăng nhập → không thể giả mạo reviewer.
   - Mobile **không cần sửa**: `mobile/lib/features/ekyc/data/ekyc_repository.dart:42,56` vẫn gửi `userId` nhưng Spring bỏ qua param không khai báo.
   - Test: `EkycUserIdParamIgnoredTest` (4 test) — vẫn pass.
   - **🟡 Còn lại 3 controller Worker/EO ngoài phạm vi** — `WorkerJobController` (7 endpoint), `DirectPaymentController` (4 endpoint), `EoEventController` (6 endpoint) vẫn còn `explicitUserId` → xử lý khi merge module (bản fix tương ứng đã có trên `origin/duong`).
9. 🟡 `App.jsx` đổi route `/eo`,`/worker` → `/login` + thêm route `/cskh` → `/customer-care` — **đã commit** nhưng chưa verify E2E (ảnh hưởng: user chưa đăng nhập vào `/` sẽ ra `/login` thay vì `/eo`).

---

## 7. Quyết định kỹ thuật đã thống nhất (đừng làm lại)

- **Tách feature slice dùng chung** thay vì import chéo giữa `pages/admin/components/` và `pages/customer-care/` → 2 slice `ekyc-verification` + `skill-verification`, `AdminPage` và `CustomerCarePage` cùng dùng (hiện đang comment out chờ nhóm duyệt).
- **Thứ tự rule `SecurityConfig` là quyết định** — matcher đầu tiên thắng; rule CC **phải** trước wildcard `/api/v1/admin/**`.
- **Không mở quyền CC cho System Config** — UC-114/115 chỉ Admin, cố ý dùng wildcard ADMIN.
- **Ưu tiên hiển thị lỗi thật thay vì mock** — không được bật mock fallback mặc định trong các queue dùng cho nghiệp vụ thật.
- **Audit:** fix này không thêm action audit mới → `AdminDashboardService.formatActionDescription` không cần sửa.
- **Nhóm quyết ẩn UI eKYC/skill để xem xét** → giữ nguyên feature slice trong repo, chỉ comment ở tầng page; khi bật lại không cần viết lại code.
- **Module FAQ dùng mô hình 2 controller** — public `/api/v1/faqs` (`permitAll()`, đọc-only cho user) + admin `/api/v1/admin/faqs` (CC + Admin). Cùng pattern cho RS Appeal: `WorkerAppealController` (worker tự nộp) + `AdminAppealController` (CC/Admin duyệt).

---

## 8. Việc tiếp theo (thứ tự)

1. 🔴 **HỎI USER VỀ REVERT BẢO MẬT** (mục §0.1 + §6.6) — bản không an toàn **đã lên HEAD**. Hỏi: chủ ý hay nhầm khi gom commit? → nếu nhầm, khôi phục theo lệnh trong §0.1 (lưu ý kiểm tra diff `AuthService` giữa `41fb598` và `5e5a13f` trước).
2. 🔴 **Xử lý 2 test class đang đỏ** (mục §0.3) — `WorkerEkycServiceTest` (1 fail + 2 UnnecessaryStubbing) và `WorkerJobServiceTest` (NPE thiếu `@Mock AttendanceLogRepository`). Cần xác nhận với user/leader: fix ở nhánh này hay để nhóm khác xử lý (nguồn: commit của nhánh khác `9f01b50`, `7020ebe`).
3. **Chạy `npx vite build`** sau commit `5e5a13f` (73 file FE mới: revenue-dashboard, rs-appeals, faqs) — chưa verify build tại phiên này.
4. **Hỏi leader 2 việc:** ① merge vào đâu — `test` hay `main`? ② commit `4278929` của duong "align V12/V13 + promote V14" có hợp lệ không, tức V12/V13 đã apply Supabase chưa?
5. **Hỏi về SLA scheduler** (mục §0.2) — `docs/plan-fe12-support-tickets.md` §7.4 chốt "YAGNI — không viết scheduler" nhưng `5e5a13f` đã viết `SlaMonitoringService` + `SchedulingConfig` → cập nhật plan doc cho khớp quyết định thực tế.
6. **Sau khi leader merge → pull/rebase về `quyth-clean`**, chạy regression set + `curl /api/health`, rồi **verify P0** (thay vì code lại): profile `local` → `dev-token-*` phải 401; profile `h2` → vẫn 200; `GET .../status?userId=<khác>` → trả dữ liệu của chính caller.
7. **Hỏi nhóm/nhận chỉ thị bật lại UI eKYC/skill** (mục §6.1) — nếu bật: bỏ comment 4 chỗ ở `AdminPage.jsx` + 2 khối ở `CustomerCarePage.jsx`, chạy `npx vite build`.
8. **E2E tay** profile `h2` + web: đăng nhập `cc@cani.vn` → `/customer-care` → tab eKYC/skill + FAQ → queue từ API thật → duyệt/từ chối 1 hồ sơ → xác nhận không 403.
9. **Dọn CSS orphan** `AdminPage.css` (mục §6.2) — giữ `.btn-admin-approve`.
10. **SRS chưa code:** UC-111 dashboard mở rộng (metrics disputes/assignments/RS) → UC-74/54/56 RS Appeals cũ (UC-94 mới là appeal RS khác) → UC-113 RBAC → UC-107 duyệt đăng ký EO → UC-117 notification → UC-121 audit. Mobile chưa có feature support-ticket/dispute.

---

## 9. Module đã hoàn thành (không làm lại)

✅ **Đã commit:** Dispute UC-96→104/109 (`f786a46`, `ba29a55`) · Support Ticket UC-105/106 (`9cb0cb1`) · System Config UC-114/115 (`ba29a55`) · Dashboard UC-86 · User Mgmt UC-87 · Skill Verification UC-47 · eKYC worker + EO · Auth + OTP · Payment/Matching.
✅ **Đã commit `07a6af7`:** CC access eKYC/skill queue — **backend đã xong + test 13 case**; FE có feature slice nhưng **UI đang bị ẩn** chờ nhóm duyệt.
✅ **Đã commit `ed3238b`:** bộ docs test-case 47 ca + chuẩn báo cáo unit test IEEE 829 (`unit-test-report-standard.md`) + script generator.
✅ **Đã commit `41fb598`:** fix bảo mật P0 — IDOR 2 controller eKYC (`SecurityUtils`) + chặn dev-token ngoài profile h2. **⚠️ Phần dev-token đã bị revert & commit — xem §0.1; phần IDOR vẫn nguyên.**
✅ **Đã commit `77e021f`:** UC-110 Quản lý FAQ & Help Center — backend + FE + V15 + 11 tests + `docs/api/faq-api.md`.
✅ **Đã commit `5e5a13f`:** UC-108 Revenue Dashboard (8 DTO + `RevenueService` + `AdminRevenueController` + FE 10 file) · UC-94 RS Appeal (entity + 2 controller + FE 10 file + V16) · SLA monitoring job (`SlaMonitoringService` + `SchedulingConfig` + V17 + `NotificationService` mới).
❌ **Chưa bắt đầu:** UC-111, UC-74/54/56, UC-113, UC-107, UC-117, UC-121; mobile chưa có feature support-ticket/dispute.
🟡 **Còn 3 controller Worker/EO vẫn có IDOR** (`WorkerJobController`, `DirectPaymentController`, `EoEventController`) — ngoài phạm vi Admin/CC, bản fix đã có trên `origin/duong`, xử lý khi merge module.

---

## 10. Quy tắc khi làm việc

Không commit `.env`/token · restart backend sau mỗi sửa backend (không devtools — `curl /api/health` + grep mapping startup log) · component web <200 dòng · message lỗi tiếng Việt · không sửa migration đã áp dụng · không lấy `userId` từ request (IDOR) · trên Windows dùng `./mvnw.cmd` (chi tiết trong `CLAUDE.md`).
