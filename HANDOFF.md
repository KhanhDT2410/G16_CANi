# HANDOFF.md — Chuyển phiên Claude Code

> **Mục đích:** cho hội thoại mới khôi phục đúng trạng thái công việc. Đọc kèm `CLAUDE.md` (nguyên tắc), `docs/ADMIN_CUSTOMER_CARE_CONTEXT.md` (toàn bộ UC Admin/CC + mapping Controller→Service→DB), `AGENTS.md` (chuẩn code team), `run.md` (cách chạy).
> **Cập nhật:** 17/11/2026 (phiên reconstruct context). Mọi sự kiện "đã kiểm chứng" dưới đây được xác nhận bằng lệnh (test/build/inspect code/git) tại thời điểm này.
> ⚠️ `docs/PROJECT_CONTEXT.md` là snapshot cũ (05/11) — nhiều mục trong đó (dev-token, test đỏ, migration V17) **đã lỗi thời**; chỉ dùng để đọc lịch sử.

---

## 0. Trạng thái hiện tại (đọc đầu tiên)

- **Branch hiện tại:** `test` (không phải `quyth-clean` của các phiên trước). **HEAD:** `0b98b29` "feat(ai-decision): enforce direct ai approval and rejection for ekyc and skill verification, remove pending review state" (HungPD, 17/11/2026).
- **Working tree có 14 file CHƯA COMMIT (đây là việc WIP duy nhất, cần giữ nguyên):**
  - `D  web/src/features/ekyc-verification/*` (7 file) + `D  web/src/features/skill-verification/*` (5 file) — **xóa 2 FE slice hàng đợi eKYC/skill** (đã staged). Lý do nhất quán: commit `0b98b29` bỏ trạng thái PENDING_REVIEW → AI quyết APPROVED/REJECTED trực tiếp, không còn hàng đợi duyệt thủ công để UI hiển thị.
  - ` M web/src/features/admin-dashboard/components/MetricsCards.jsx` — xóa KPI section "Hàng Đợi Chờ Thẩm Định" (verifications: totalPendingCount, pendingWorkerEkycCount, pendingEoEkycCount, pendingSkillVerificationsCount).
  - ` M web/src/pages/admin/AdminPage.jsx` — xóa 3 dòng comment import eKYC/skill (không còn tab nào).
  - ✅ `npx vite build` pass (352 modules, 3.10s) trên đúng working tree này — **không có import nào dangles**. Chờ commit cùng lúc với phần backend tương ứng (nếu có) hoặc tự commit như là "gỡ UI theo 0b98b29".
- **Migrations đã lên V27** (V18 no_show_records · V19–V20 faq attachments + seed · V21 shift image + plan enums · V22 faq summary · V23 event times · V24 attendance proof · V25 payment unique · V26 payos_orders · V27 worker bank accounts).
- **🟢 P0 dev-token ĐÃ ĐƯỢC KHÔI PHỤC VÀ BẰNG CHỨNG BẰNG TEST** (commit `93d7853` "fix(auth): patch critical vulnerabilities A1-A4" của HungPD, 12/11):
  - `JwtConfig`: bean JwtDecoder nhận `dev-token-*` chỉ còn ở profile `h2` (bean `!h2` đã xóa nhánh dev-token, có comment "ponytail" giải thích).
  - `AuthService.login` (≈dòng 178–197): fallback dev-token **gate** bằng `environment.acceptsProfiles("h2")`; profile khác + Supabase lỗi → `AppException` 503 SERVICE_UNAVAILABLE.
  - Test: **không còn** `AuthServiceDevTokenTest` tách riêng — đã gộp vào `AuthServiceTest` (case `UTC009`: non-H2 → 503; `UTC013`: H2 + password seed đúng → cấp `dev-token-<userId>`). PASS trong suite 17/11.
  - → Cảnh báo "HEAD không an toàn" trong CLAUDE.md/HANDOFF cũ (phiên 08/11) **đã hết hiệu lực**.
- **🟢 IDOR 3 controller Worker/EO đã được vá** (kèm merge các nhánh hung/duong vào `test`): `WorkerJobController`, `DirectPaymentController`, `EoEventController` giờ lấy userId từ principal qua `SecurityUtils.getCurrentUserId` (không còn `explicitUserId` từ request) — verify 17/11. Ownership vẫn được enforce ở tầng service.
- **Kết quả build/test (chạy thật 17/11/2026):**
  - `./mvnw.cmd test` → **463 tests, 1 failure, 0 errors** (BUILD FAILURE chỉ do 1 ca này):
    - `SkillVerificationFlowTest.step3_workerSubmitsCertificate_AutoApproved` — assertion string cũ: kỳ vọng "…phê duyệt và cộng SP_verify." nhưng service (từ `0b98b29`) trả "…phê duyệt **tự động** và cộng SP_verify.". Sửa 1 dòng assertion là hết.
  - 2 class đỏ của phiên cũ (`WorkerEkycServiceTest`, `WorkerJobServiceTest`) **đã xanh** (5 + 77 tests pass).
  - `npx vite build` (web) → **PASS**.
  - **Chưa commit:** 14 file FE (mục trên).

---

## 1. Lịch sử phát triển cần biết (reconstruct từ git, 17/11/2026)

Các phiên trước (chi tiết cũ) dừng ở HEAD `5e5a13f` trên nhánh `quyth-clean` (08/11). Từ đó nhánh `test` nhận thêm ~29 commit (merge hung + duong + các fix riêng), quan trọng nhất theo thứ tự thời gian:

| Commit | Ngày | Nội dung liên quan Admin/CC |
|---|---|---|
| `93d7853` fix(auth) A1–A4 | 12/11 | **Khôi phục dev-token gate + patch bảo mật auth quan trọng khác** (EmailOtp, metadata email OAuth). `docs/reviews/REVIEW_AUTH_2026-11-12.md` (270 dòng) mô tả đầy đủ. |
| `b539e39` hung backend | 12–13/11 | "Complete HungPD backend features + EO attendance QR HMAC": thêm `AdminWageMatrixController`, `WageRecommendationController`, `InviteController`, `BlockListController`, `GeoMapController`, `NotificationController`, `EoMyTeamController`, `MyTeamService`, `WageMatrixService`…; migration V21–V24 (gồm renumber `b17b150`). |
| `78c8002` fix(payment) | 12/11 | Gate demo subscriptions, unique constraint payment (V25), chặn free-trial abuse — ảnh hưởng data của `RevenueService` (UC-108). |
| `dac7de4` fix(security) | 14–15/11 | **Secure dispute evidence** (URL placeholder chống lộ file nội bộ), cap dispatch radius, HMAC QR check-in. `DisputeService` bị sửa trực tiếp. |
| `89ebd17` feat(payment VietQR) | 16/11 | `V27__add_worker_bank_account.sql` + PayOS orders (V26) end-to-end: mobile → backend → web EO. |
| `0b98b29` feat(ai-decision) | **17/11 = HEAD** | **Bỏ PENDING_REVIEW ở eKYC + skill verification** — AI (Vision) quyết APPROVED/REJECTED thẳng. Sửa `SkillVerificationService`, `WorkerEkycService`, `SkillVerificationController`, `WorkerEkycController` + 4 mobile screen. ⚠️ Kèm theo: 1 test assertion lỗi string (mục 0), và 2 FE slice hàng đợi (bị xóa chưa commit ở working tree). |

**Kết luận trạng thái UC (17/11):**
- Backend Admin/CC **vẫn đầy đủ** như inventory trong `docs/ADMIN_CUSTOMER_CARE_CONTEXT.md`: dashboard, users, disputes, support tickets, system config, FAQ, revenue, RS appeals, no-show, skill/eKYC admin endpoints (admin skill review `adminReview`/`revoke` vẫn sống; admin eKYC queue vẫn tồn tại nhưng worker flow giờ gần như không populate PENDING_REVIEW — xem gap trong context file).
- `AdminDashboardService.getDashboardMetrics()` vẫn trả `verifications` (pendingWorkerEkycCount…) — **FE đã bỏ hiển thị** (working tree) nhưng backend chưa đổi → không lỗi, chỉ dư.

---

## 2. Việc tiếp theo (thứ tự khuyến nghị)

1. **Commit 14 file FE đang WIP** (xóa 2 slice ekyc/skill + MetricsCards + AdminPage) — đã verify build pass, không dangles. Commit message gợi ý: `refactor(web): remove ekyc/skill queue UI after 0b98b29 direct ai decision, drop pending-verification KPI card`. (Quyết định với leader vì đây là xóa UI.)
2. **Fix 1 dòng assertion** `SkillVerificationFlowTest:299` (string "phê duyệt tự động") → suite 463/463 xanh. Chủ commit `0b98b29` nên sửa; nếu user được phân công → sửa luôn.
3. **Đối chiếu SRS:** `AdminDashboardService.formatActionDescription` có nhãn `APPROVE_WORKER_EKYC/APPROVE_EO_EKYC` nhưng **không service eKYC nào ghi audit** (verify trong context file §gaps) — hỏi leader: thêm audit vào `WorkerEkycService`/`EventOrganizerEkycService` hay bỏ nhãn.
4. **E2E tay** (profile `h2` + web): `cc@cani.vn` → `/customer-care` (tickets, disputes, faqs, appeals, no-shows) + `admin@cani.vn` → `/admin` toàn bộ tab; xác nhận không 403; kiểm tra dashboard metrics mới (đã mất KPI verifications).
5. **Việc SRS chưa code** (ưu tiên theo SRS, chưa bắt đầu — xem context file §4): UC-107 (duyệt đăng ký EO), UC-111 (dashboard mở rộng disputes/assignments/RS), UC-113 (RBAC), UC-117 (màn hình notification — infra `NotificationService`/`notifications` + `NotificationController` đã có sẵn), UC-121 (trang audit độc lập), UC-119 (file upload tập trung — đang dùng URL placeholder), UC-112/116 (CRUD user đầy đủ, feature flags).
6. **Docs đối nội cần cập nhật khi rảnh:** `docs/plan-fe12-support-tickets.md` §7.4 vẫn ghi "YAGNI không viết scheduler" trong khi SLA scheduler đã chạy từ `5e5a13f`; `docs/PROJECT_CONTEXT.md` stale tổng thể (migration nói V13/V17 trong khi thực tế V27; dev-token cảnh báo cũ).
7. **Quy tắc vận hành:** restart backend sau mỗi sửa backend (không devtools) · `./mvnw.cmd` trên Windows · không sửa migration đã apply · không lấy userId từ request (IDOR) · không commit `.env`.

---

## 3. Lỗi / vấn đề tồn tại (verify 17/11/2026)

| # | Mức | Nội dung | Bằng chứng |
|---|---|---|---|
| 1 | 🟡 | `SkillVerificationFlowTest.step3` fail (string assertion cũ sau `0b98b29`) | surefire report 17/11 |
| 2 | 🟡 | 14 file FE xóa ekyc/skill UI **chưa commit** (staged) | `git status` |
| 3 | 🟡 | `?adminId=` param override actor (audit) ở `AdminUserController.updateUserStatus` + `SystemConfigController` PUT (ADMIN-only, không bypass nhưng đi ngược quy tắc principal-only) | đọc code 17/11 |
| 4 | 🟡 | eKYC: `getPendingEkycQueue()` query `PENDING_REVIEW` nhưng worker `submitEkyc` (sau `0b98b29`) chỉ ghi APPROVED/REJECTED → queue worker gần như rỗng (EO flow strict-mode vẫn populate) | backend audit agent |
| 5 | 🟡 | `RsAppealStatus.UNDER_REVIEW` declared nhưng không flow nào set (service chỉ SUBMITTED→APPROVED/REJECTED) | đọc enum + service |
| 6 | 🟡 | Không có controller riêng cho SLA monitoring — chỉ scheduled job (thỏa hiệp có chủ ý từ `5e5a13f`; plan doc chưa cập nhật) | `SlaMonitoringService` |
| 7 | 🟡 | `GET /api/v1/eo/events/images/**` và `GET /api/v1/workers/proof/**` là **unauthenticated static-file** (path-traversal đã guard) | `SecurityConfig` permitAll list |
| 8 | 🟡 | Test catalog 47 ca (11-11) **chưa bao phủ** UC-88/94/108/110 + SLA job; ngược lại eKYC/skill 6 ca trong catalog giờ thừa phần "queue" (UI đã xóa) | `docs/test-cases/` |
| 9 | 🟡 | UC numbering drift: SRS (all_ucs_srs.txt) gán UC-86/87 = payment confirm/history, API docs dùng UC-86/87 = dashboard/user mgmt — cần leader chốt mapping | docs audit agent |
| 10 | 🟢 | dev-token P0, IDOR 3 controller — **đã an toàn** (mục 0) | code + test 17/11 |

> Chi tiết UC-by-UC + mapping đầy đủ: `docs/ADMIN_CUSTOMER_CARE_CONTEXT.md`.

---

## 4. Quyết định kỹ thuật đã thống nhất (đừng làm lại)

- **Thứ tự rule `SecurityConfig`** — rule CC phải trước wildcard `/v1/admin/**`; `@PreAuthorize` method-level không thay thế được URL rule.
- **System Config + Revenue + Wage Matrix = ADMIN-only** (cố ý; SRS "Finance" chưa có role riêng → map vào ADMIN).
- **Không mock fallback trên queue nghiệp vụ** (bài học 403 bị che).
- **2-controller pattern cho module CC-chia-xu:** public `/v1/…` (user) + admin `/v1/admin/…` (CC+Admin), cùng 1 feature slice FE.
- **Từ `0b98b29`: eKYC/skill verification là AI-decided** — không còn PENDING_REVIEW manual state; UI hàng đợi đã xóa (working tree), admin vẫn giữ `/v1/workers/skills/admin/queue` + `review`/`revoke` cho override.
- **Audit + notification là convention của mọi module Admin/CC** (xem danh sách action strings trong context file).

---

## 5. Chấm điểm nhanh các module (verify 17/11/2026)

| Module | Backend | FE Web | Test | Ghi chú |
|---|---|---|---|---|
| UC-86 Dashboard | ✅ | ✅ (KPI verifications đã gỡ khỏi UI) | ✅ (3) | |
| UC-87 User Mgmt | ✅ | ✅ | ✅ (4+6) | còn `?adminId=` wart |
| UC-96→104/109 Dispute | ✅ (secure evidence `dac7de4`) | ✅ | ✅ (12) | |
| UC-105/106 Ticket | ✅ | ✅ | ✅ (13) | SLA job cảnh báo |
| UC-114/115 System Config | ✅ | ✅ | ✅ (9+9) | |
| UC-110 FAQ | ✅ (V15–V20, V22) | ✅ (upload-image, analytics) | ✅ (14) | deflection rate |
| UC-108 Revenue | ✅ | ✅ | ✅ (7+5) | data payment đã chặt hơn sau `78c8002` |
| UC-94 RS Appeal | ✅ | ✅ | ✅ (8+5) | |
| UC-88 No-Show | ✅ (V18) | ✅ | ✅ (8+6) | cron 10' |
| UC-47 Skill verify | ✅ (AI direct decision) | 🟡 **UI đã xóa (WIP)** | 🟡 1 assertion fail | xem #1, #4 §3 |
| eKYC worker/EO | ✅ (VNPT 100%, Gemini OCR) | 🟡 admin queue UI đã xóa | ✅ (5+6) | xem #4 §3 |
| SLA monitoring | ✅ (cron 15', V17) | — (alert qua notification) | ✅ (6) | |
| Wage Matrix / Invite / BlockList / GeoMap / MyTeam / Notification / Subscription + PayOS | ✅ (hung) | 🟡 (EO side) | ✅ | ngoài phạm vi Admin/CC, không sâu |
