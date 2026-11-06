# HANDOFF.md — Chuyển phiên Claude Code

> **Mục đích:** cho hội thoại mới khôi phục đúng trạng thái công việc. Đọc kèm `CLAUDE.md` (nguyên tắc), `AGENTS.md` (chuẩn code team), `docs/PROJECT_CONTEXT.md` (snapshot chi tiết).
> **Cập nhật:** 05/11/2026 (sau commit `07a6af7`). Đã đối chiếu với code + git thực tế, có chạy test/build.

---

## 0. Trạng thái hiện tại (đọc đầu tiên)

- **Toàn bộ fix CC 403 đã được COMMIT và PUSH** → `07a6af7` "fix ekyc" (23 files). Working tree **SẠCH**.
- **Phía BACKEND đã xong và đã test**: CC có quyền eKYC/skill.
- **Phía FRONTEND bị ẩn theo yêu cầu nhóm**: import + tab + view eKYC/skill trong `AdminPage.jsx` và `CustomerCarePage.jsx` đều bị **comment out** ("Tạm thời ẩn tính năng eKYC và Thẩm định kỹ năng AI theo yêu cầu nhóm để xem xét thêm"). → **UI chưa hiển thị dù backend đã sẵn sàng.** Feature slice vẫn còn nguyên trong `web/src/features/ekyc-verification/` và `features/skill-verification/`.
- **Verify lại tại phiên này:** 62/62 test pass (BUILD SUCCESS) + `npx vite build` ✓ built in 1.76s.

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

- **Branch:** `quyth-clean` · **HEAD:** `07a6af7` "fix ekyc" · **đã push** → `origin/quyth-clean` ✅
- **Working tree: SẠCH** (không có thay đổi chưa commit).
- **Main:** `main` (`702a91c`). Nhánh khác: `quyth`, `duong`, `hung`, `minhtb`, `test`, `backup/quyth-with-duong-merge`.
- Commit `07a6af7` gộp **toàn bộ** backend + FE + docs + `HANDOFF.md`/`PROJECT_CONTEXT.md` trong **1** commit (23 files, +1122/−420) — không tách 2 commit như dự kiến.

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
5. 🟡 **`dev-token` bypass ngoài profile `h2`** (chưa sửa, cần quyết định): `JwtConfig.java:30-40` — bean `@Profile("!h2")` vẫn chấp nhận token `dev-token-*`, tự dựng JWT `alg=none`, claim email cố định `worker@cani.vn` → bypass được xác thực JWT trên cả profile `local` (Supabase thật). Nên gỡ nhánh này hoặc giới hạn theo property/env **không phụ thuộc profile**.
   - Lưu ý liên quan: `AuthService.login:147-152` fallback khi Supabase lỗi → tìm user theo email, **không verify password**, trả `dev-token-<uuid>`. Hệ quả: profile `h2`, đăng nhập `cc@cani.vn` với **bất kỳ password nào** cũng vào được → thuận tiện cho E2E.
6. 🟡 **IDOR còn sót ở controller cũ:** `resolveUserId(authentication, explicitUserId)` ưu tiên `userId` từ query-param — `WorkerEkycController:104`, `EventOrganizerEkycController:96`, `WorkerJobController:40`, `DirectPaymentController:102`, `EoEventController:120`. `CLAUDE.md` quy định "không lấy userId từ request" → nên bỏ tham số `explicitUserId`. Module mới (dispute/ticket/config) đã tuân thủ đúng.
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

1. **Hỏi nhóm/nhận chỉ thị bật lại UI eKYC/skill** (mục §6.1) — nếu bật: bỏ comment 4 chỗ ở `AdminPage.jsx` + 2 khỗi ở `CustomerCarePage.jsx`, chạy `npx vite build`.
2. **E2E tay** profile `h2` + web: đăng nhập `cc@cani.vn` → `/customer-care` → tab eKYC/skill → queue từ API thật → duyệt/từ chối 1 hồ sơ → xác nhận không 403.
3. **Dọn CSS orphan** `AdminPage.css` (mục §6.2) — giữ `.btn-admin-approve`.
4. **Cập nhật `docs/PROJECT_CONTEXT.md`** §7 (working tree → sạch, HEAD `07a6af7`) + §8 Known Issue (CC 403 đã fix & commit) + ghi nhận UI đang bị ẩn.
5. **SRS chưa code, theo priority:** UC-108 Revenue Dashboard → UC-110 FAQ → UC-74/54/56 RS Appeals/No-show → UC-111 dashboard mở rộng → auto-escalation SLA job.
6. **Quyết định còn treo:** `dev-token` bypass (mục §6.5) và IDOR controller cũ (mục §6.6).

---

## 9. Module đã hoàn thành (không làm lại)

✅ **Đã commit:** Dispute UC-96→104/109 (`f786a46`, `ba29a55`) · Support Ticket UC-105/106 (`9cb0cb1`) · System Config UC-114/115 (`ba29a55`) · Dashboard UC-86 · User Mgmt UC-87 · Skill Verification UC-47 · eKYC worker + EO · Auth + OTP · Payment/Matching.
✅ **Đã commit `07a6af7`:** CC access eKYC/skill queue — **backend đã xong + test 13 case**; FE có feature slice nhưng **UI đang bị ẩn** chờ nhóm duyệt.
❌ **Chưa bắt đầu:** UC-108, UC-110, UC-74/54/56, UC-111, auto-escalation SLA; mobile chưa có feature support-ticket/dispute.

---

## 10. Quy tắc khi làm việc

Không commit `.env`/token · restart backend sau mỗi sửa backend (không devtools — `curl /api/health` + grep mapping startup log) · component web <200 dòng · message lỗi tiếng Việt · không sửa migration đã áp dụng · không lấy `userId` từ request (IDOR) · trên Windows dùng `./mvnw.cmd` (chi tiết trong `CLAUDE.md`).
