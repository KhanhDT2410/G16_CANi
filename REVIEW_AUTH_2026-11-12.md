# Review chuyên sâu: LUỒNG AUTH — CANi (Backend + Web + Mobile)

> Ngày: 12/11/2026 · Phạm vi: toàn bộ luồng xác thực 3 tầng.
> Phương pháp: đọc toàn bộ code auth (backend 787 dòng + web + mobile), **boot backend thật (profile h2, port 8099) và probe endpoint bằng curl** để kiểm chứng từng nghi vấn bằng bằng chứng thực nghiệm, không phải suy đoán.
> Không sửa file nào.

---

## 0. Bảng điều hướng nhanh

| # | Mức | Vấn đề | Tầng |
|---|---|---|---|
| **A1** | 🔴 CRITICAL | Đăng nhập **không kiểm tra mật khẩu** ở profile h2 — sai mật khẩu vẫn vào được, kể cả tài khoản ADMIN | Backend |
| **A2** | 🔴 CRITICAL | Client tự chỉ định role — `targetRole` nâng WORKER → EVENT_ORGANIZER, ghi vĩnh viễn vào DB | Backend (+Web) |
| **A3** | 🔴 CRITICAL | Profile h2: JWT decoder chấp nhận **mọi token**, fallback về `worker@cani.vn` | Backend |
| **A4** | 🟠 HIGH | Chống brute-force OTP **không hoạt động** — `attempts` bị rollback do throw sau `save()` | Backend |
| **A5** | 🟠 HIGH | Đăng nhập sai mật khẩu ở profile **production** trả về 503 thay vì 401 — sai thông báo lỗi, lộ trạng thái hệ thống | Backend |
| **A6** | 🟠 HIGH | Web: refresh token lưu nhưng **không bao giờ dùng** — hết phiên = logout cứng | Web |
| **A7** | 🟠 HIGH | Mobile: một lỗi mạng thoáng qua trong lúc refresh → **xoá token vĩnh viễn** | Mobile |
| **A8** | 🟡 MED | Google OAuth: không `state`/`nonce`, token đi qua URL → rủi ro CSRF + lộ token | Web |
| **A9** | 🟡 MED | Đăng xuất không revoke token phía server | Cả 2 client |
| **A10** | 🟡 MED | `cani_user` (kèm role) nằm trong localStorage → UI gate giả mạc được | Web |
| **A11** | 🟡 MED | Không có lockout/rate-limit đăng nhập (brute-force mật khẩu) | Backend |
| **A12** | 🟢 LOW | Validate mật khẩu không nhất quán giữa các form (quên mật khẩu thiếu check in hoa) | Web + Mobile |

---

## A1 🔴 CRITICAL — Đăng nhập không kiểm tra mật khẩu (profile h2)

**File:** `backend/src/main/java/com/cani/service/AuthService.java:138-176`

**Root cause:** `login()` bọc lời gọi Supabase trong `catch (Exception e)` — bắt **cả** lỗi "sai mật khẩu" lẫn "Supabase không reachable". Khi `session == null`, code rơi vào nhánh h2 và **chỉ tra cứu user theo email, không so sánh mật khẩu**:

```java
try {
    session = supabaseAuthClient.signInWithPassword(email, request.getPassword());
} catch (Exception e) {
    log.warn("Supabase GoTrue auth failed or unreachable: {}. Falling back to local check...", e.getMessage());
}
...
} else if (environment.acceptsProfiles(Profiles.of("h2"))) {
    User localUser = userRepository.findByEmail(email)
            .orElseThrow(() -> new BadRequestException("Email hoặc mật khẩu không chính xác"));
    userId = localUser.getUserId();
    accessToken = "dev-token-" + userId;   // ← không hề kiểm tra password
```

**Đã kiểm chứng bằng probe thật** (backend boot profile h2, port 8099):

```
POST /api/auth/login {"email":"worker@cani.vn","password":"abcdef99-wrong"}
→ {"success":true,"message":"Đăng nhập thành công",
   "data":{"token":"dev-token-08d4cbd3-...","user":{"role":"WORKER",...}}}

POST /api/auth/login {"email":"admin@cani.vn","password":"zzz"}
→ {"success":true,"message":"Đăng nhập thành công",
   "data":{"token":"dev-token-00000000-0000-0000-0000-000000000001",
           "user":{"role":"ADMIN",...}}}          ← ADMIN với mật khẩu sai

POST /api/auth/login {"email":"ghost@cani.vn","password":"x"}
→ {"success":false,"message":"Email hoặc mật khẩu không chính xác"}   ← đúng
```

**Kịch bản hỏng:** Chỉ cần biết email của admin (`admin@cani.vn` — nằm ngay trong `H2DataSeeder.java:123` và cả trong `run.md` tài liệu hướng dẫn chạy) là đăng nhập được bằng **bất kỳ mật khẩu nào**, nhận token ADMIN, và gọi được toàn bộ `/api/v1/admin/**` (quản lý user, doanh thu, tranh chấp, cấu hình hệ thống). Không cần đoán mật khẩu, không cần tấn công rate-limit.

**Fix tối thiểu:** tách 2 trường hợp — `signInWithPassword` ném `BadRequestException` khi Supabase báo sai credential (đã làm vậy ở `SupabaseAuthClient.java:104`), nên `login()` phải **re-throw** `BadRequestException` thay vì nuốt; chỉ `catch` lỗi *kết nối* (timeout/IO) mới cho phép fallback. Với profile h2, so sánh mật khẩu seed thật (hash), không cấp token chỉ vì email tồn tại.

---

## A2 🔴 CRITICAL — Client tự chỉ định role, backend nâng quyền vĩnh viễn

**File:** `backend/src/main/java/com/cani/service/AuthService.java:253-261` (và `:217-221` cho user mới)

```java
// Nếu user đã tồn tại nhưng có role là WORKER ... tự động nâng cấp sang EVENT_ORGANIZER
if ("EVENT_ORGANIZER".equalsIgnoreCase(request.getTargetRole())
        && user.getRole() == UserRole.WORKER
        && !"worker@cani.vn".equalsIgnoreCase(user.getEmail())) {
    log.info("Cập nhật vai trò người dùng {} sang EVENT_ORGANIZER theo yêu cầu từ Web Portal", ...);
    user.setRole(UserRole.EVENT_ORGANIZER);
    user = userRepository.save(user);
}
```

**Web gửi cứng giá trị này:** `web/src/pages/auth/AuthCallbackPage.jsx:29` → `targetRole: 'EVENT_ORGANIZER'`.

**Kịch bản hỏng:** Bất kỳ ai có tài khoản Google (email bất kỳ, kể cả email rác) mở web CANi → đăng nhập Google → backend **ghi role EVENT_ORGANIZER vĩnh viễn vào DB**. Không có admin duyệt, không có kiểm tra eKYC, không có email xác nhận. Đây là server-side trust bị lạm dụng — không phải vấn đề UI.

**Hệ quả nối tiếp:** role EO cho phép tạo sự kiện (`EoEventController` `@PreAuthorize("hasAnyRole('EVENT_ORGANIZER','ADMIN')"`), đăng tin tuyển dụng, quản lý offer, thanh toán. Chỉ cần một request `POST /api/auth/google` với `targetRole` tùy ý là có quyền.

**Fix tối thiểu (đúng nguyên tắc "server là nguồn sự thật"):**
1. Xoá hoàn toàn nhánh tự nâng role ở `:253-261`.
2. Chỉ áp dụng `targetRole` cho **user mới tạo** (`:217-221`) — và khi đó vẫn phải qua eKYC gate mà `EoEventService.java:81` đang áp (`verificationStatus != APPROVED` → chặn tạo event).
3. Tốt hơn: bỏ `targetRole` khỏi `GoogleLoginRequest` DTO, cho client tự chọn vai trò ở form đăng ký (như `accountType` của `RegisterRequest` đang làm) và chỉ dùng cho user mới.

---

## A3 🔴 CRITICAL — Profile h2: JWT decoder chấp nhận mọi token

**File:** `backend/src/main/java/com/cani/config/JwtConfig.java:46-123`

Decoder của profile h2 **không verify chữ ký**: token có dấu `.` được decode `alg=none`, lấy `sub` từ payload; token `dev-token-<uuid>` map thẳng tới user đó; **token rác cũng qua**, fallback về `worker@cani.vn` (hardcode ở `:56` và `:108`).

**Kịch bản hỏng:** `Authorization: Bearer x` bất kỳ → authenticated. Tổ hợp với A1, hệ thống ở profile h2 **không còn bất kỳ rào chắn xác thực nào**.

**Fix:** (a) tuyệt đối không chạy profile h2 trên host dùng chung / demo công khai — cần nêu rõ trong tài liệu bàn giao; (b) thêm cờ env `CANI_ALLOW_DEV_TOKEN=true` (mặc định **false**) để bật nhánh dev-token; (c) bỏ fallback `worker@cani.vn`; (d) luôn kiểm tra `exp` thật.

---

## A4 🟠 HIGH — Chống brute-force OTP không hoạt động

**File:** `backend/src/main/java/com/cani/service/EmailOtpService.java:100-113`

**Root cause:** `verifyOtp` là `@Transactional`. Khi mã sai, code là `verification.setAttempts(currentAttempts + 1); otpRepository.save(verification);` rồi **`throw new BadRequestException(...)`**. Vì `AppException extends RuntimeException` (unchecked), **transaction bị rollback** → lần `save()` bị hủy → `attempts` **không bao giờ được ghi xuống DB**.

**Đã kiểm chứng bằng probe thật** — 10 lần gọi liên tiếp với mã sai:

```
Lần 1 : "Mã xác thực OTP không chính xác. Bạn còn 2 lần thử."
Lần 2 : "Mã xác thực OTP không chính xác. Bạn còn 2 lần thử."   ← không giảm
Lần 3 : "Mã xác thực OTP không chính xác. Bạn còn 2 lần thử."
...
Lần 10: "Mã xác thực OTP không chính xác. Bạn còn 2 lần thử."   ← vẫn "còn 2 lần"
```

Không bao giờ đạt tới ngưỡng 3 lần → **giới hạn 3 lần thử là vô hiệu**. Mã OTP 6 chữ số = 10⁶ khả năng, với cooldown gửi 60s nhưng **không giới hạn số lần thử** → brute-force khả thi.

**Fix:** `save()` rồi `flush()`, và **không rollback** phần ghi attempts — dùng `REQUIRES_NEW` cho phần đếm attempt, hoặc bỏ `@Transactional` khỏi method và tự quản lý transaction cho phần verify thành công.

*(Lưu ý: probe thứ 2 cho thấy message "đã hết hạn" vì OTP đã qua 5 phút — cooldown 60s hoạt động đúng, chỉ có bộ đếm attempt là hỏng.)*

---

## A5 🟠 HIGH — Sai mật khẩu ở production trả 503 thay vì 401

**File:** `backend/src/main/java/com/cani/service/AuthService.java:142-172`

Ở profile **không phải h2** (tức production / local chạy Supabase), khi Supabase báo sai mật khẩu, `signInWithPassword` ném `BadRequestException` → bị `catch (Exception e)` nuốt → `session == null` → **không rơi vào nhánh h2** → rơi vào nhánh `else` và ném 503 *"Dịch vụ xác thực tạm thời không khả dụng"*.

**Kịch bản hỏng:** Người dùng gõ sai mật khẩu → UI báo "Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau" → tưởng hệ thống sập, thử lại liên tục. Đồng thời lộ trạng thái hệ thống cho kẻ tấn công (đây là lỗi trong *thông báo*, không phải trong quyền truy cập — nhưng `AuthService.java:174-177` vẫn tra `userRepository` và chặn LOCKED/SUSPENDED, nên **tài khoản bị khoá vẫn bị lộ qua đường này**).

**Fix:** cùng fix với A1 — phân biệt lỗi credential (4xx từ Supabase) với lỗi hạ tầng (timeout/5xx). Chỉ lỗi hạ tầng mới trả 503.

---

## A6 🟠 HIGH — Web: refresh token lưu nhưng không bao giờ dùng

- Ghi: `web/src/features/auth/hooks/useLogin.js:62`, `web/src/pages/auth/AuthCallbackPage.jsx:43`
- Xoá: `web/src/features/auth/api/authApi.js:103`
- **Không nơi nào gửi `cani_refresh_token` đi.** Backend **đã có** endpoint `/api/auth/refresh` (`AuthController.java:76`) và nó hoạt động — chỉ là web không gọi.
- Interceptor 401 (`web/src/services/api.js:29-38`) chỉ xoá localStorage + `window.location.href = '/login'`.

**Kịch bản hỏng:** access token của Supabase hết hạn (~1h) trong lúc user đang làm việc → mất phiên ngay lập tức, dù refresh token còn hợp lệ.

**Fix:** trong interceptor 401, đọc `cani_refresh_token` → `POST /api/auth/refresh` → cập nhật `cani_token` → retry request 1 lần. (Mobile đã làm đúng việc này — xem A7 — nên copy pattern đó.)

---

## A7 🟠 HIGH — Mobile: lỗi mạng thoáng qua xoá token vĩnh viễn

**File:** `mobile/lib/core/network/auth_interceptor.dart:83-106`

Trong block xử lý 401, `catch (_) { inMemoryToken = null; }` bắt **mọi** exception — kể cả timeout mạng. Một lần mạng chập chờn trong lúc refresh → token bị xoá → user bị đẩy về màn login, dù phiên vẫn hợp lệ.

**Kịch bản hỏng:** User đang ở giữa form đặt lịch, mạng chập chờn 1 giây, request 401 → refresh timeout → token null → mất phiên, mất dữ liệu form.

**Fix:** chỉ clear token khi lỗi **xác thực** (401/403 từ chính endpoint refresh); lỗi mạng thì `handler.next(err)` và giữ token. Ngoài ra block này tạo 2 `Dio()` mới mỗi lần (`auth_interceptor.dart:72,102`) không bao giờ `close()` — nên dùng lại instance có sẵn.

---

## A8 🟡 MED — Google OAuth: không `state`/`nonce`, token đi qua URL

**File:** `web/src/pages/auth/AuthCallbackPage.jsx`, `web/src/features/auth/api/supabaseClient.js`

- `supabaseClient.js` không set `flowType` → supabase-js v2 mặc định là **implicit flow** → token nằm trong **fragment** `#access_token=...&refresh_token=...` của URL.
- Không có `state`/`nonce` → callback **không chống CSRF**.
- `AuthCallbackPage.jsx:65` lại đọc `?code=` (pattern của PKCE) — không khớp với implicit flow.

**Kịch bản hỏng:** Token lộ trong address bar (copy-paste, lịch sử trình duyệt, `document.referrer` sang domain khác). Không có `state` → kẻ tấn công có thể chèn URL callback giả để ép session của họ cho nạn nhân (login CSRF).

**Fix:** set `flowType: 'pkce'` trong `createClient`, thêm `state` random + kiểm tra khi callback, và dọn fragment khỏi URL ngay (`history.replaceState`) sau khi đọc.

---

## A9 🟡 MED — Đăng xuất không revoke token

- Web `authApi.js:96-105`: `supabase.auth.signOut()` + xoá 3 key localStorage. Không gọi backend.
- Mobile `auth_repository.dart:~326`: `Supabase...signOut()` + xoá key local. Không gọi backend.

**Kịch bản hỏng:** User A đăng xuất, nhưng refresh token Supabase chưa bị revoke. Nếu attacker có access token cũ (từ log, screenshot, máy công cộng) → gọi `/api/auth/refresh` → lấy token mới → vẫn truy cập được.

**Fix:** thêm `POST /api/auth/logout` ở backend (gọi GoTrue `signOut`/`revoke`), gọi **trước khi** xoá local. Hoặc dùng `signOut(scope: 'global')` để thu hồi toàn bộ session.

---

## A10 🟡 MED — `cani_user` (kèm role) nằm trong localStorage

**File:** `web/src/features/auth/context/AuthContext.jsx:49`, `useAuthGuard.js:18`, `RequireRole.jsx:19`

`useAuthGuard` và `RequireRole` quyết định điều hướng dựa trên `user.role` đọc từ `localStorage['cani_user']`. Sửa role trong DevTools → UI render trang admin; `AuthCallbackPage.jsx:37` còn **từ chối đăng nhập** nếu role là WORKER (logic ở client).

**Ranh giới quan trọng:** Đây **chỉ là UI gate**. Server chặn thật bằng `hasRole("ADMIN")` + `UserAuthorityFilter` đọc role từ DB → mọi API call vẫn 403. **Không escalate được.** Nhưng nó làm UI hiển thị sai (user thấy trang admin rỗng) và làm logic "chặn Worker vào Web" không đáng tin.

**Fix:** đọc user từ `/api/auth/me` (đã có `refreshUser`), không lưu role trong localStorage; nếu giữ localStorage thì coi đó là **cache hiển thị**, không phải nguồn quyết định.

---

## A11 🟡 MED — Không có lockout/rate-limit đăng nhập

Grep toàn backend: không có bộ đếm `failedLogin`/`lockout` nào. `AuthController` không có rate-limit. Endpoint `/api/auth/login` là `permitAll` (`SecurityConfig.java:45`).

Supabase GoTrue có rate-limit theo IP, nhưng **không có lockout theo tài khoản** → attacker dùng proxy xoay IP để brute-force 1 tài khoản. Ở profile h2, A1 đã khiến việc này vô nghĩa (không cần mật khẩu đúng).

**Fix:** bộ đếm fail theo email trong DB, khoá sau 5 lần trong 15 phút. (Mẫu có sẵn: `EmailOtpVerification.attempts`.)

---

## A12 🟢 LOW — Validate mật khẩu không nhất quán

| Nơi | Kiểm tra |
|---|---|
| Backend `RegisterRequest.java:38-42` | ≥8 ký tự + ít nhất 1 chữ in hoa ✅ |
| Backend `ConfirmResetPasswordRequest.java` | ≥8 ký tự, **không** yêu cầu in hoa ⚠️ |
| Web `useRegister.js` | ≥8 + in hoa ✅ |
| Web `ForgotPasswordModal.jsx:94-97` | ≥8, **không** check in hoa ⚠️ |
| Mobile `register_screen.dart` | đủ ✅ |
| Mobile `forgot_password_screen.dart:398-400` | **chỉ check empty** ⚠️ |

**Kịch bản hỏng:** Mobile user đặt mật khẩu mới 3 ký tự → submit → server trả lỗi generic, UI không báo rõ nguyên nhân (mobile parse `data['message']` nên có báo, nhưng không validate trước → UX kém).

**Fix:** thêm `@Pattern(regexp = "^(?=.*[A-Z]).+$")` vào `ConfirmResetPasswordRequest` (sửa 1 chỗ, áp dụng cho cả web lẫn mobile) + validate phía client cho trải nghiệm.

---

## Phần ĐÃ kiểm tra và SẠCH (không phải vấn đề)

| Hạng mục | Kết quả |
|---|---|
| Impersonation qua `?userId=` | **Đã vá** — `SecurityUtils.getCurrentUserId()` lấy từ JWT; có regression test `EkycUserIdParamIgnoredTest.java` |
| `/notifications`, `/devices` permitAll | **Đã vá** — `SecurityConfig.java:82-88` → `.authenticated()`; probe không token → 401 ✅ |
| Token có bị log ra console | Web: grep sạch. Mobile: chỉ `debugPrint` FCM device token (không phải auth secret) ✅ |
| Cooldown gửi OTP | **Hoạt động đúng** — probe 3 lần liên tiếp → lần 2,3 bị chặn "Vui lòng đợi 60 giây" ✅ |
| Token lưu ở mobile | `flutter_secure_storage` (Keychain/Keystore) — đúng, tốt hơn web ✅ |
| Mobile có dùng refresh token | **CÓ** — `auth_interceptor.dart:72-91` POST `/api/auth/refresh` với `cani_refresh_token` ✅ (web thì không — A6) |
| Supabase JWT verify ở production | JWKS ES256, đúng chuẩn, không phải HMAC ✅ |
| `UserAuthorityFilter` đọc role từ DB | ✅ Không trust role từ client |
| OTP hết hạn 5 phút | ✅ Đã kiểm chứng qua probe |
| eKYC gate khi tạo event | ✅ Có — `EoEventService.java:81` chặn nếu `verificationStatus != APPROVED` |
| Locked/Suspended chặn login | ✅ `AuthService.java:175,263` |
| Secret trong git history | ✅ Sạch — `git log --all` không có |

---

## Đề xuất thứ tự xử lý

**Trước demo (ưu tiên tuyệt đối):**
1. **A1** — không kiểm tra mật khẩu ở h2. Đây là lỗ hổng lớn nhất, và nó **nằm trên đường demo offline** (chính là profile h2 mà giảng viên/ban đánh giá sẽ chạy).
2. **A2** — xoá nhánh tự nâng role.
3. **A4** — sửa bộ đếm OTP (rollback nuốt `attempts`).
4. **A3** — cờ `CANI_ALLOW_DEV_TOKEN` mặc định off + bỏ fallback `worker@cani.vn`; ghi rõ cảnh báo profile h2 trong tài liệu bàn giao.

**Sau demo:**
5. A5 (phân biệt lỗi credential vs hạ tầng) · A6 (web refresh) · A7 (mobile refresh) — nên gộp A5+A6+A7 thành một PR "chuẩn hoá luồng refresh token 3 tầng".
6. A8 (PKCE + state) · A9 (revoke on logout) · A10 (bỏ role khỏi localStorage) · A11 (lockout) · A12 (validate mật khẩu đồng bộ).

---

## Ghi chú cho hội đồng bảo vệ

Ở profile **h2** (chế độ offline dùng để demo — đúng theo `run.md`), bản chất hệ thống là **không có xác thực**: mật khẩu không được kiểm tra (A1) và mọi token đều hợp lệ (A3). Điều này **chấp nhận được cho môi trường dev** nhưng **phải nêu rõ** trong báo cáo bàn giao, và tuyệt đối không dùng profile này khi trình diễn trên máy dùng chung hoặc host công khai. Ở profile production (Supabase), A1 không xảy ra — nhưng A2 (nâng role) và A5 (sai thông báo lỗi) vẫn là lỗi thật và cần sửa.
