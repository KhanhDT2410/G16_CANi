# TỔNG HỢP REVIEW 3 DOMAIN — CANi (Auth · Thanh toán · Tìm kiếm sự kiện)
> Ngày: 12/11/2026 · Đã commit `78c8002` (fix payment) trong lúc review → báo cáo tiền đã được cập nhật theo state thật.
> 3 báo cáo chi tiết: `REVIEW_AUTH_2026-11-12.md` · `REVIEW_THAN_TOAN_2026-11-12.md` · `REVIEW_TIM_KIEM_SU_KIEN_2026-11-12.md`

---

## Bức tranh tổng thể

Đã review 3 domain trọng yếu của hệ thống, mỗi domain 3 tầng (backend + web + mobile), với **probe thực nghiệm** trên backend chạy thật (profile h2) chứ không chỉ đọc code.

**Điểm mạnh thật sự của dự án** (không nên phá):
- Pipeline ứng tuyển worker **được thiết kế chặt**: atomic increment chống overbooking, conflict check chống trùng ca, payment tạo trong cùng transaction (không bao giờ có assignment mồ côi), unique constraint payments đã có.
- Tiền subscription dùng **BigDecimal đúng chuẩn**, JWT production verify bằng **JWKS ES256**, `UserAuthorityFilter` đọc role từ DB, OTP có expiry + attempt counter.
- Mobile dùng `flutter_secure_storage` (Keychain/Keystore) — đúng chuẩn hơn web.

**Một pattern lặp lại xuyên suốt 3 domain — đây mới là gốc cần vá:**
> **Backend hoặc client "tự khai" trạng thái thay vì kiểm chứng từ nguồn sự thật.**

| Domain | Biểu hiện |
|---|---|
| Auth | Client gửi `targetRole` → backend tự nâng role; h2 decoder nhận mọi token |
| Thanh toán | Nút "Đã chuyển khoản" tự khai → kích hoạt gói; webhook verify sai spec nên không ai xác nhận được |
| Tìm kiếm | `requiredSkills`/`eKYC` được khai báo ở đầu vào nhưng **không ai đọc** ở đầu ra |

Cùng một nguyên nhân: **thiếu lớp kiểm chứng giữa input và hành động**. Vá đúng cách = thêm lớp kiểm chứng ở server (record DB, verify chữ ký, đọc các field đã khai báo), **không** phải thêm check ở UI.

---

## Trạng thái sau commit `78c8002`

Đã được **vá đúng bản chất** (chặn ở server, không ở UI):
- ✅ `demo-simulate` gate theo `cani.subscription.demo-enabled` (default off, chỉ h2 bật)
- ✅ `payos-confirm` không kích hoạt gói khi demo tắt
- ✅ FREE_TRIAL: 1 lần/EO + chặn ghi đè gói đã trả tiền
- ✅ UNIQUE `payments.assignment_id` + catch `DataIntegrityViolationException`
- ✅ Lương tính bằng BigDecimal (hết money-math qua double)

Còn lại (theo mức độ):

| # | Mức | Vấn đề | File |
|---|---|---|---|
| 1 | 🔴 | **Webhook PayOS verify sai spec** (SHA256 body thay vì `x-signature` SHA512) → webhook thật 100% reject, đường tự động chết | `PayOSConfig.java:38-68` |
| 2 | 🔴 | **Auth: profile h2 không kiểm tra mật khẩu** → `admin@cani.vn` + mật khẩu sai = vào được ADMIN | `AuthService.java:142-176` |
| 3 | 🔴 | **Auth: client tự chỉ định role** → ai login Google cũng lên EVENT_ORGANIZER vĩnh viễn | `AuthService.java:253-261` |
| 4 | 🟠 | **`payos_orders` trong RAM** → restart mất đơn, webhook trả fake-ack 200, đã trả tiền không nhận gói | `SubscriptionService.java:53,284` |
| 5 | 🟠 | **Nâng cấp gói mất quota gói cũ** (không cộng dồn) | `SubscriptionService.java:331` |
| 6 | 🟠 | **Web `eoService.dispatchForShift` không tồn tại** → nút "Kích hoạt Matching" crash, chắc chắn gặp khi demo | `EORecruitmentTab.jsx:71` |
| 7 | 🟠 | **`calculateDistance` trả 3.0km giả** khi thiếu toạ độ → worker không rõ vị trí đứng đầu radar | `MatchingDispatchService.java:271` |
| 8 | 🟠 | **Radar không lọc ca đã đủ người** → hiện "Cần: 0/4" rồi bấm Apply mới báo lỗi | `WorkerJobService.java:431` |
| 9 | 🟠 | **`requiredSkills` + `eKYC` không ai đọc** → worker thiếu kỹ năng/chưa verify vẫn được dispatch | `MatchingDispatchService.java:179` |
| 10 | 🟠 | **OTP attempt counter bị rollback** (throw sau `save()`) → giới hạn 3 lần thử vô hiệu, brute-force OTP khả thi | `EmailOtpService.java:100-113` |
| 11 | 🟠 | **Web không dùng refresh token** → hết phiên = logout cứng | `services/api.js:29-38` |
| 12 | 🟡 | **`dispatch_proposals` không UNIQUE (shift,worker)** + web không có UI worker → dispatch fallback `findAll()` quét toàn bảng | `V1__init_schema.sql:248`, `MatchingDispatchService.java:99` |

---

## Ưu tiên đề xuất (theo impact ÷ effort)

**Nhóm 1 — Rẻ, sửa ngay (≈ 1-2 giờ, chặn được lỗi chắc chắn gặp trước khách):**
- #6 thêm 5 dòng vào `eoService.js` → nút Matching chạy được
- #7 đổi `return 3.0` → `Double.MAX_VALUE` (1 dòng)
- #8 thêm filter `remainingWorkers > 0` (1 dòng)
- #2 tách lỗi credential vs lỗi hạ tầng trong `login()` → profile h2 so mật khẩu thật

**Nhóm 2 — Cần thiết để luận điểnh "thanh toán thật" đứng vững (1 ngày):**
- #1 webhook theo spec PayOS + #4 persist `payos_orders` (cùng bộ, cùng chỗ code)
- #5 cộng dồn quota khi nâng cấp
- #3 xoá nhánh tự nâng role

**Nhóm 3 — Chất lượng nghiệp vụ (2 ngày):**
- #9 enforce `requiredSkills` + `eKYC` ở dispatch/apply/accept
- #10 sửa bộ đếm OTP attempt; #11 refresh path cho web

**Nếu chỉ kịp 1 ngày:** làm Nhóm 1 + #1/#4. Vì #2 và #6 là hai lỗi **chắc chắn** xảy ra khi demo (sai mật khẩu ai cũng gặp; nút Matching ai cũng bấm), còn #1 quyết định liệu câu chuyện "thanh toán qua cổng PayOS" có đứng vững trước hội đồng hay không.

---

## Điểm cần nói rõ trong báo cáo bàn giao

Ở profile **h2** (chế độ offline dùng để demo), bản chất hệ thống là **không có xác thực**: mật khẩu không được kiểm tra và mọi token đều hợp lệ. Chấp nhận được cho môi trường dev, nhưng **phải nêu rõ** và tuyệt đối không dùng profile này khi trình diễn trên máy dùng chung hoặc host công khai. Ở profile production (Supabase), các lỗi auth đó không xảy ra — nhưng #1 (webhook), #3 (nâng role), #5 (quota) vẫn là lỗi thật và cần sửa.
