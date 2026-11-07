# HANDOFF.md — Chuyển phiên Claude Code

> **Mục đích:** cho hội thoại mới khôi phục đúng trạng thái công việc. Đọc kèm `CLAUDE.md` (nguyên tắc), `AGENTS.md` (chuẩn code team), `docs/PROJECT_CONTEXT.md` (snapshot chi tiết).
> **Cập nhật:** 07/11/2026 (sau commit docs test-case). Đã đối chiếu với code + git thực tế.

---

## 0. Trạng thái hiện tại (đọc đầu tiên)

- **Fix CC 403 đã COMMIT và PUSH** → `07a6af7` "fix ekyc" (23 files). Backend xong + test 13 case.
- **UI eKYC/skill đang bị ẩn theo yêu cầu nhóm** (import + tab + view trong `AdminPage.jsx` và `CustomerCarePage.jsx` bị comment out: *"Tạm thời ẩn … theo yêu cầu nhóm để xem xét thêm"*). Feature slice vẫn nguyên vẹn trong `web/src/features/ekyc-verification/` + `features/skill-verification/` (đã bỏ mock fallback) → bật lại chỉ cần bỏ comment.
- **HEAD:** `cc3a75e` "Update project context and handoff documentation" → đã push `origin/quyth-clean`.
- **Bộ docs test-case đã COMMIT** (mục §3.1): 47 test case phân hệ Admin/CC + chuẩn báo cáo unit test IEEE 829 + script generator. Working tree sạch (trừ thay đổi docs lần này).
- **🔴 Đối chiếu nhánh khác (07/11/2026) — cần quyết định của nhóm:** `origin/duong` và `origin/hung` **đã fix sẵn 2/3 hạng mục P0** mà repo này còn để mở:
  - `JwtConfig`: nhánh `dev-token-*` **đã xóa** khỏi bean `@Profile("!h2")` → chỉ profile `h2` chấp nhận.
  - `AuthService.login`: fallback `dev-token` **đã gate** bằng `environment.acceptsProfiles("h2")`; profile khác khi Supabase lỗi → 503 thay vì cấp token.
  - **IDOR: đã xóa `explicitUserId` khỏi TẤT CẢ controller** (kể cả `WorkerJobController`, `DirectPaymentController`, `EoEventController` — vốn nằm ngoài phạm vi phân hệ Admin/CC), dùng class mới `com.cani.security.SecurityUtils.getCurrentUserId(authentication)`.
  → **✅ Đã áp dụng trên nhánh này (07/11/2026)** cho phần trong phạm vi Admin/CC (2 controller eKYC + JwtConfig + AuthService), **theo đúng hướng code của duong** để giảm conflict khi merge. Phần 3 controller Worker/EO deferred đến khi merge module.
  → **Khi merge vào nhánh `test`:** các thay đổi này có thể **conflict text** với bản của duong (cùng file, khác dòng context) → resolve bằng cách **lấy bản của duong** (logic tương đương, đã cover thêm 3 controller Worker/EO). **Không tự viết lại.**
- **🟡 Vấn đề migration cần nhờ leader:** commit `4278929` trên `origin/duong` *"align V12, V13 và promote checklist to V14"* — nếu V12/V13 đã apply lên Supabase thì việc sửa nội dung migration đã áp dụng sẽ vi phạm quy tắc "không sửa migration đã áp dụng" (`CLAUDE.md` §Backend). Cần hỏi leader trước khi merge.
- **🟡 Worktree vs nhánh khác:** `quyth-clean` vs `origin/duong` có **18 file overlap** từ merge-base `aff086c` (gồm `SecurityConfig.java`, `H2DataSeeder.java`, `GlobalExceptionHandler.java`, `AuthService.java`, `JwtConfig.java`, `DisputeService.java`, `SkillVerificationService.java`, `App.jsx`, `EOPage.jsx`, `pom.xml`) → merge tay có rủi ro cao, nên để leader merge `test`/`main` rồi kéo về.
- **Verify (phiên trước):** 62/62 test pass BUILD SUCCESS + `npx vite build` ✓ 1.76s.

---

## 1. Phiên trước đã làm gì

**Fix CC 403 trên hàng đợi eKYC / Skill Verification** (Known Issue #2 cũ) — gồm backend lẫn frontend. **Đã commit `07a6af7`.**

**Phân tích đã khoanh vùng trước khi code (nguyên nhân gốc):**
403 nằm ở **tầng URL rule** của `SecurityConfig`, **không** phải `@PreAuthorize`:
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

- **Branch:** `quyth-clean` · **HEAD:** `cc3a75e` "Update project context and handoff documentation" · **đã push** → `origin/quyth-clean` ✅
- **✅ Bộ docs test-case đã commit** (chi tiết §3.1). Không có file source code nào bị sửa trong đợt này.
- **Main:** `main` (`702a91c`). Nhánh khác: `quyth`, `duong`, `hung`, `minhtb`, `test`, `backup/quyth-with-duong-merge`.
- Commit `07a6af7` gộp **toàn bộ** backend + FE + docs trong **1** commit (23 files, +1122/−420). Commit `cc3a75e` chỉ cập nhật 3 file docs.
- **🟡 So sánh nhánh (07/11/2026):** `quyth-clean` **ahead 27** / **behind 0** so với `origin/main`; **ahead 21 / behind 71** so với `origin/duong` và `origin/hung`; **ahead 0 / behind 39** so với `origin/test`. `origin/test` có vẻ là nhánh tổng hợp mới nhất (3 merge `quyth-clean` gần đây).

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

---

## 4. Điểm quan trọng: mock fallback đã gỡ (fix ẩn bug 403)

`AdminEkycTab`/`AdminSkillsTab` cũ có **mock fallback data** khi API lỗi và **nuốt lỗi** review → báo "thành công" giả. Đây là lý do bug 403 bị che khi demo.

Đã sửa trong 2 hook mới: `catch (err)` → `showToast(err.response?.data?.message || '…', 'error')` + `setQueue([])`.
Verify: `grep -rn "Mock Fallback\|fallback = \[\|mockData" web/src/` → **0 match**.

---

## 5. Kết quả kiểm chứng (chạy thật, không diễn giải)

```
./mvnw.cmd test -Dtest="CustomerCareQueueSecurityTest"          → Tests run: 13, Failures: 0, Errors: 0 | BUILD SUCCESS
./mvnw.cmd test -Dtest="<7 class regression set>"                 → Tests run: 62, Failures: 0, Errors: 0 | BUILD SUCCESS
npx vite build (web/)                                              → ✓ built in 1.65s
```

> ⚠️ **Lưu ý Windows:** phải dùng `./mvnw.cmd` (không phải `./mvnw`) — shell POSIX không parse được `.mvn/jvm.config` (`-Xmx2048m`) → `ClassNotFoundException: "-Xmx64m"`.

**Chưa làm: E2E tay** (bật backend profile `h2` + web, đăng nhập `cc@cani.vn`, xem queue load từ API thật, duyệt/từ chối 1 hồ sơ).

---

## 6. Lỗi / việc còn tồn tại

1. 🔴 **UI eKYC/skill bị ẩn theo yêu cầu nhóm** — trong `AdminPage.jsx` và `CustomerCarePage.jsx`, import + tab + view đều bị comment out ("Tạm thời ẩn … theo yêu cầu nhóm để xem xét thêm"). Backend đã sẵn sàng + test pass nhưng **người dùng chưa thấy được màn hình**. Khi nhóm đồng ý bật: bỏ comment ở 4 chỗ (2 import, 2 khối render) + 2 khối tab trong `CustomerCarePage.jsx` → rồi chạy lại `npx vite build`.
2. 🟡 **CSS orphan trong `web/src/pages/admin/AdminPage.css`** — sau khi xóa 2 tab cũ, các class `.admin-verif-card` (:86), `.admin-card-top` (:94), `.admin-img-box` (:108, :116), `.admin-action-row` (:131), `.admin-card-list` (:80) **không còn component nào dùng**. Riêng `.btn-admin-approve` (:138) **vẫn được** `features/admin-users/components/UserStatusModal.jsx:93` dùng lại → **đừng xóa** class này.
3. 🟡 **E2E tay chưa chạy** — bật backend profile `h2` + web, đăng nhập `cc@cani.vn`, xác nhận queue eKYC/skill load từ API thật, duyệt/từ chối 1 hồ sơ, xác nhận request có quyền (không 403). Lưu ý: UI đang bị ẩn nên **phải bật lại tab trước** (mục 1) hoặc test bằng `curl`.
4. 🟡 `reference_no` ticket suffix 8 ký tự — vẫn còn rủi ro va chạm UNIQUE (thấp).
5. ✅ **`dev-token` bypass ngoài profile `h2`** — **ĐÃ VÁ 07/11/2026 trên nhánh này.**
   - `JwtConfig.java` — xóa nhánh `dev-token` khỏi bean `@Profile("!h2")`; chỉ bean `@Profile("h2")` còn chấp nhận `dev-token-*`.
   - `AuthService.login:149-162` — fallback "Supabase lỗi → dev-token" **chỉ chạy khi `environment.acceptsProfiles("h2")`**; profile khác → ném `AppException` **503** ("Dịch vụ xác thực tạm thời không khả dụng"), không cấp token và không lộ ra việc email tồn tại hay không.
   - Hành vi profile `h2` **không đổi**: đăng nhập `cc@cani.vn` bằng bất kỳ password nào vẫn vào được → thuận tiện cho E2E.
   - Test: `AuthServiceDevTokenTest` (3 test).
6. ✅ **IDOR ở 2 controller eKYC** — **ĐÃ VÁ 07/11/2026 trên nhánh này (phạm vi phân hệ Admin/CC).**
   - Thêm `com.cani.security.SecurityUtils.getCurrentUserId(Authentication)` — chỉ đọc `principal instanceof User`, không nhận userId từ request (đúng quy tắc `CLAUDE.md`).
   - `WorkerEkycController` + `EventOrganizerEkycController` — xóa `@RequestParam userId` ở 6 endpoint (submit/status/review ×2) và helper `resolveUserId(authentication, explicitUserId)`.
   - Endpoint review: `adminUserId` giờ = người CC/Admin đang đăng nhập → không thể giả mạo reviewer.
   - Mobile **không cần sửa**: `mobile/lib/features/ekyc/data/ekyc_repository.dart:42,56` vẫn gửi `userId` nhưng Spring bỏ qua param không khai báo.
   - Test: `EkycUserIdParamIgnoredTest` (4 test).
   - **🟡 Còn lại 3 controller Worker/EO ngoài phạm vi** — `WorkerJobController` (7 endpoint), `DirectPaymentController` (4 endpoint), `EoEventController` (6 endpoint) vẫn còn `explicitUserId` → xử lý khi merge module (bản fix tương ứng đã có trên `origin/duong`).
7. 🟡 `App.jsx` đổi route `/eo`,`/worker` → `/login` + thêm route `/cskh` → `/customer-care` — **đã commit** nhưng chưa verify E2E (ảnh hưởng: user chưa đăng nhập vào `/` sẽ ra `/login` thay vì `/eo`).

---

## 7. Quyết định kỹ thuật đã thống nhất (đừng làm lại)

- **Tách feature slice dùng chung** thay vì import chéo giữa `pages/admin/components/` và `pages/customer-care/` → 2 slice `ekyc-verification` + `skill-verification`, `AdminPage` và `CustomerCarePage` cùng dùng (hiện đang comment out chờ nhóm duyệt).
- **Thứ tự rule `SecurityConfig` là quyết định** — matcher đầu tiên thắng; rule CC **phải** trước wildcard `/api/v1/admin/**`.
- **Không mở quyền CC cho System Config** — UC-114/115 chỉ Admin, cố ý dùng wildcard ADMIN.
- **Ưu tiên hiển thị lỗi thật thay vì mock** — không được bật mock fallback mặc định trong các queue dùng cho nghiệp vụ thật.
- **Không cần migration** cho fix này (chỉ đổi SecurityConfig + seeder + FE).
- **Audit:** fix này không thêm action audit mới → `AdminDashboardService.formatActionDescription` không cần sửa.
- **Nhóm quyết ẩn UI eKYC/skill để xem xét** → giữ nguyên feature slice trong repo, chỉ comment ở tầng page; khi bật lại không cần viết lại code.

---

## 8. Việc tiếp theo (thứ tự)

1. ~~**Xử lý 6 file docs đang staged (chưa commit)**~~ → ✅ **Đã xong 07/11/2026** (mục §3.1): restore 3 file test-case từ git objects, đổi tên `SKILL (1).md`, sửa đường dẫn sai trong `unit-test-report-standard.md` / `generate_service_unit_test_sheet.py` / `run.md` / `dispute-resolution-api.md`.
2. **Hỏi leader 2 việc** (mục §0): ① merge vào đâu — `test` hay `main`? ② commit `4278929` của duong "align V12/V13 + promote V14" có hợp lệ không, tức V12/V13 đã apply Supabase chưa?
3. **Sau khi leader merge → pull/rebase về `quyth-clean`**, chạy regression set + `curl /api/health`, rồi **verify P0** (thay vì code lại): profile `local` → `dev-token-*` phải 401; profile `h2` → vẫn 200; `GET .../status?userId=<khác>` → trả dữ liệu của chính caller.
4. **Hỏi nhóm/nhận chỉ thị bật lại UI eKYC/skill** (mục §6.1) — nếu bật: bỏ comment 4 chỗ ở `AdminPage.jsx` + 2 khối ở `CustomerCarePage.jsx`, chạy `npx vite build`.
5. **E2E tay** profile `h2` + web: đăng nhập `cc@cani.vn` → `/customer-care` → tab eKYC/skill → queue từ API thật → duyệt/từ chối 1 hồ sơ → xác nhận không 403.
6. **Dọn CSS orphan** `AdminPage.css` (mục §6.2) — giữ `.btn-admin-approve`.
7. **Test class còn thiếu trong scope:** `EoEventServiceTest` (chưa có); `AuthServiceTest` (chưa có dù SKILL standard mô tả mẫu 29 ca — kiểm tra scope trước).
8. **SRS chưa code, theo priority:** UC-108 Revenue Dashboard → UC-110 FAQ → UC-74/54/56 RS Appeals/No-show → UC-111 dashboard mở rộng. (Auto-escalation SLA job: `plan-fe12-support-tickets.md` §7.4 ghi rõ "YAGNI — không viết scheduler", cờ `slaAtRisk` đã đủ cho MVP.)

---

## 9. Module đã hoàn thành (không làm lại)

✅ **Đã commit:** Dispute UC-96→104/109 (`f786a46`, `ba29a55`) · Support Ticket UC-105/106 (`9cb0cb1`) · System Config UC-114/115 (`ba29a55`) · Dashboard UC-86 · User Mgmt UC-87 · Skill Verification UC-47 · eKYC worker + EO · Auth + OTP · Payment/Matching.
✅ **Đã commit `07a6af7`:** CC access eKYC/skill queue — **backend đã xong + test 13 case**; FE có feature slice nhưng **UI đang bị ẩn** chờ nhóm duyệt.
❌ **Chưa bắt đầu:** UC-108, UC-110, UC-74/54/56, UC-111, auto-escalation SLA; mobile chưa có feature support-ticket/dispute.

---

## 10. Quy tắc khi làm việc

Không commit `.env`/token · restart backend sau mỗi sửa backend (không devtools — `curl /api/health` + grep mapping startup log) · component web <200 dòng · message lỗi tiếng Việt · không sửa migration đã áp dụng · không lấy `userId` từ request (IDOR) · trên Windows dùng `./mvnw.cmd` (chi tiết trong `CLAUDE.md`).
