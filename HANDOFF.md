# HANDOFF.md — Chuyển phiên Claude Code

> **Mục đích:** cho hội thoại mới khôi phục đúng trạng thái công việc. Đọc kèm `CLAUDE.md` (nguyên tắc) và `docs/PROJECT_CONTEXT.md` (bản snapshot đầy đủ, **chưa commit**). File này chỉ ghi phần *không* nằm trong hai file trên.
> **Cập nhật:** 05/11/2026. Đã đối chiếu với code thực tế (grep/read + `git log`), không suy đoán.

---

## 1. Phiên hiện tại — đã làm và chưa làm

- **Đã làm trong phiên này:** định hướng lại (đọc `CLAUDE.md`, `docs/PROJECT_CONTEXT.md`, `AGENTS.md`), đối chiếu từng mục trong PROJECT_CONTEXT với code (SecurityConfig, migrations, controllers, tests, AdminPage). **Không sửa source code, không commit.**
- **Không có thay đổi code mới trong phiên này.** Toàn bộ thay đổi code của giai đoạn FE-12/UC-114-115 đã được commit ở `ba29a55` (xem §2).

## 2. Trạng thái git (đã kiểm chứng bằng lệnh)

- **Branch:** `quyth-clean` · **HEAD:** `ba29a55` "Cấu hình hệ thống" · **Main:** `main`.
- **Working tree:** chỉ còn `?? docs/PROJECT_CONTEXT.md` (file snapshot chưa commit; nên commit cùng lần thay đổi kế tiếp).
- **Các thay đổi đã commit ở `ba29a55` (module System Config, UC-114/115):**
  - Backend (new): `SystemConfigController` (`/api/v1/admin/configs`: GET list, GET `{key}`, PUT `{key}`, PUT bulk), `SystemConfigService`, `SystemConfigRepository`, DTO `dto/admin/config/*`.
  - Backend (modified): `DisputeRepository` + `DisputeService` (thêm `queue/mine`, `customer-care-staff` — giải Known Issue #1 cũ trong PROJECT_CONTEXT), `GlobalExceptionHandler` (thêm `AccessDeniedException` → 403 có structure).
  - Web: `web/src/features/system-configs/` (full slice: api/components/hooks/types/index) + tab "Tham Số Hệ Thống" trong `AdminPage.jsx`.
  - Tests (new): `SystemConfigServiceTest`, `SystemConfigControllerTest`.
  - Docs: `docs/api/system-config-api.md`.
- **Commit liền trước:** `9cb0cb1` (support ticket queue/mine + CC staff + FE), `f786a46` (dispute queue/mine FE).

## 3. Tiến độ chức năng

| Việc | Trạng thái | Bằng chứng |
|---|---|---|
| System Config (UC-114/115) | ✅ Hoàn thiện, đã commit `ba29a55` | controller/service/test có trong tree; test nằm trong regression set |
| Dispute `queue/mine` + `customer-care-staff` backend | ✅ Đã commit trong `ba29a55` | `AdminDisputeController:45,58` |
| Support ticket `queue/mine` + `customer-care-staff` | ✅ Đã commit trước đó | `AdminSupportTicketController:42,55` |
| CC xem eKYC / skill verification queue (Known Issue #2) | ❌ **Chưa làm — việc tiếp theo** | `SecurityConfig:51,54,57`: `workers/skills/admin/**` và `admin/ekyc/**`, `admin/eo-ekyc/**` vẫn chỉ ADMIN |
| UC-108 Revenue Dashboard, UC-110 FAQ, UC-74/54/56, UC-111, auto-escalation SLA | ❌ Chưa bắt đầu (theo priority §9 PROJECT_CONTEXT) | — |

## 4. Lỗi / vấn đề

**Đã xử lý (trước `ba29a55`):** HEAD-không-compile (thiếu backend dispute queue) · `AdminPage.jsx` quá lớn (nay 86 dòng, eKYC/skill tách ra `AdminEkycTab` 174 dòng / `AdminSkillsTab` 147 dòng) · 500 "lỗi hệ thống" do AccessDenied chưa có handler (nay trả 403 chuẩn).

**Chưa giải quyết:**
1. **🔴 Việc kế tiếp — CC bị 403 trên eKYC/skill queue.** Endpoint thực tế (đã xác minh trong code, khác URL đã ghi trong PROJECT_CONTEXT §8.2):
   - `GET /api/v1/admin/ekyc/queue` + `POST /api/v1/admin/ekyc/{verificationId}/review` (`WorkerEkycController`)
   - `GET /api/v1/admin/eo-ekyc/queue` + `POST /api/v1/admin/eo-ekyc/{verificationId}/review` (`EventOrganizerEkycController`)
   - `POST /api/v1/admin/skills/...` dưới `/api/v1/workers/skills/admin/**`
   → Chỉnh `SecurityConfig`: đặt rule `hasAnyRole("ADMIN","CUSTOMER_CARE")` cho hai matcher ekyc **trước** wildcard `/api/v1/admin/**`; matcher skills cần tách (đường dẫn khác pattern `admin/**` nên thêm rule riêng trước đó). Kèm test (regression set + `./mvnw test`).
2. 🟡 `reference_no` ticket: suffix 8 ký tự, UNIQUE constraint vẫn còn rủi ro va chạm thấp (PROJECT_CONTEXT §8.4).
3. 🟡 Việc SRS chưa code (mục §3, dòng cuối) + E2E tay (tạo ticket → CC nhận/transfer/đóng; dispute → escalate → Admin final).
4. 🟡 **Lệch với `CLAUDE.md` (mục "Rules" #4) — `dev-token` vẫn sống ngoài profile `h2`.** `JwtConfig.java:30-40` (bean `@Profile("!h2")`) vẫn chấp nhận token `dev-token-*` và tự dựng JWT `alg=none`, claim email cố định `worker@cani.vn`. Tức là trên profile `local` (Supabase thật) vẫn bypass được xác thực JWT → nên gỡ nhánh này hoặc giới hạn theo property/env, không phụ thuộc profile. **Chưa sửa** (ngoài phạm vi phiên này) — cần quyết định của bạn về cách chặn.
5. 🟡 **IDOR còn sót ở các controller cũ:** `resolveUserId(authentication, explicitUserId)` ưu tiên `userId` từ query-param — có ở `WorkerEkycController:104`, `EventOrganizerEkycController:96`, `WorkerJobController:40`, `DirectPaymentController:102`, `EoEventController:120`. `CLAUDE.md` quy định "không lấy userId từ request" → các endpoint này nên bỏ tham số `explicitUserId`. Module mới (dispute/ticket/config) đã tuân thủ đúng; các controller cũ thì chưa.

## 5. Quyết định kỹ thuật (để phiên sau không làm lại)

- **System config không tạo migration mới:** bảng `system_config` đã có từ `V1` (seed ở `V2`) → module chỉ thêm controller/service/repo/DTO, đúng convention.
- **Security:** System Config dùng wildcard `hasRole("ADMIN")` — **cố ý**, UC-114/115 chỉ Admin (CC không truy cập). Không đưa config vào nhóm rule CC.
- **Audit:** các action config đã có nhãn tiếng Việt trong `AdminDashboardService.formatActionDescription` (kiểm tra trước khi thêm module mới — quy tắc này ở CLAUDE.md).
- **Không sửa source theo yêu cầu của phiên này:** mọi thay đổi thực tế cần được giao việc riêng trong phiên kế tiếp.

## 6. Việc tiếp theo (thứ tự)

1. Commit `docs/PROJECT_CONTEXT.md` (đang untracked) cùng commit fix kế tiếp.
2. **Fix Known Issue #2** (mục §4.1): 2–3 dòng rule trong `SecurityConfig` + test + chạy regression set (`./mvnw test -Dtest="DisputeServiceTest,SupportTicketServiceTest,SystemConfigServiceTest,SystemConfigControllerTest,AdminDashboardServiceTest,AdminUserControllerTest"`).
3. E2E bằng thật (backend profile `h2` + web; tài khoản admin/ccs/worker như PROJECT_CONTEXT §9.3).
4. Unfinished SRS theo priority: UC-108 → UC-110 → UC-74/54/56 → UC-111.

> Quy tắc khi làm việc: không commit `.env`/token; restart backend sau mỗi sửa backend; component web <200 dòng; message error tiếng Việt. (Chi tiết trong `CLAUDE.md`.)
