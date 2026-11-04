# Prompt Thiết Kế OpenDesign — Luồng Đăng Ký 3 Bước & Mật Khẩu (Worker Mobile App CANi)

## Cách dùng

Copy từng khối `## PROMPT` trong SECTION 2 vào OpenDesign, thiết kế **1 màn 1 lần**. Mỗi khối tự chứa đủ ngữ cảnh (visual tokens, API, microcopy, routing), không cần đọc thêm file nào. SECTION 1 là tài liệu tham chiếu nội bộ của nhóm thiết kế, không phải prompt; các prompt chỉ "trích dẫn" nó cho người đọc, không phụ thuộc để chạy tool.

**Nguồn tham chiếu thiết kế (spec dùng để viết bộ prompt này):**

- `.agents/ScreenList/screens/A01_login_register.md` — màn login & register hiện tại
- `.agents/ScreenList/screens/A02_email_verification_otp.md` — spec màn OTP email
- `.agents/ScreenList/screens/A03_reset_password.md` — spec khôi phục mật khẩu
- `.agents/ScreenList/screens/E01_ekyc_verification_wizard.md` — spec wizard eKYC
- `web/DESIGN.md` — design tokens chung của hệ thống (màu, font, component)
- `mobile/lib/features/auth/screens/register_screen.dart` — code register 1-trang hiện tại (gộp OTP inline, sẽ thay bằng luồng 3 bước)
- `mobile/lib/features/auth/screens/login_screen.dart` — code login hiện tại
- `mobile/lib/features/ekyc/screens/ekyc_verification_screen.dart` — code wizard eKYC hiện có

---

# SECTION 1 — NGỮ CẢNH CHUNG (shared context, mọi prompt cùng nhìn vào đây)

## 1.1 Visual DNA Mobile (khác hẳn web, đọc kỹ trước khi vẽ)

- **Nền:** trắng `#FFFFFF`. Chữ chính `#0F172A`, chữ phụ `#64748B`, tiêu đề `#0B1B3D`.
- **Nút chính (Primary):** bo góc **12px**, chiều cao 52, gradient `linear-gradient(#00D2FF → #0066FF)`, chữ trắng 600, kèm icon `arrow_forward` bên phải; shadow `#0077FF @35%` blur 14.
- **Accent (link, icon prefix, checkbox):** `#0077FF`.
- **Field (ô nhập):** icon prefix màu `#0077FF` size 20, bo góc 12, nền tĩnh `#F8FAFC`, **nền khi focus trắng**, border `#E2E8F0` (border accent `#0077FF` khi focus); chữ gõ `#0F172A`, hint `#94A3B8`.
- **Error tile:** nền `#FEF2F2`, viền `#FECACA`, chữ `#DC2626`, fontSize 13, bo góc 10.
- **Success:** `#10B981` (nền nhạt `#ECFDF5`), dùng cho tick, chip, toast.
- **Warning / PENDING_REVIEW:** `#F59E0B` (nền nhạt `#FFFBEB`).
- **Logo:** `assets/cani_logo.png` (widget `CaniBrandLogo`), width 80% màn, clamp **200–340dp**.
- **Font tiếng Việt:** **Be Vietnam Pro** (tiêu đề 600–900), **Plus Jakarta Sans** (body 400–700).

## 1.2 Sơ đồ luồng 3 bước đăng ký (mục tiêu mới, thay màn register 1-trang hiện tại)

```text
LoginScreen
  --("Đăng ký ngay")-->  R-01 (Bước 1/3 · Thông tin cơ bản)
R-01 --("Tạo Tài khoản")--> R-02 (Bước 2/3 · Xác thực OTP email)
R-02 --(verify-otp OK)-->  R-03 (Bước 3/3 · Định danh eKYC)
R-03 --(submit OK, status PENDING_REVIEW)--> HomeScreen + banner "eKYC đang chờ thẩm định"
```

**Gate vào Home:**

| Trạng thái eKYC | Hành vi |
| :--- | :--- |
| `NOT_SUBMITTED` (chưa submit) | **Không cho vào Home**, giữ lại R-03 |
| `PENDING_REVIEW` | Vào Home, banner vàng "đang chờ thẩm định" |
| `APPROVED` | Vào Home, badge xanh "Đã chứng thực" |
| `REJECTED` | Vào Home, banner đỏ + lý do + CTA "Nộp lại hồ sơ eKYC" |

## 1.3 API contract (đúng như backend hiện tại, `backend/src/main/java/com/cani/controller/AuthController.java`)

**SẴN (đã có, được phép wire ngay):**

| Endpoint | Body | Ghi chú |
| :--- | :--- | :--- |
| `POST /api/auth/register` | `{email, password, fullName, otpCode, phone?, accountType:"WORKER"}` | → `UserDto` |
| `POST /api/auth/send-otp` | `{email, otpType}` | `otpType` ∈ `REGISTER` / `RESET_PASSWORD` |
| `POST /api/auth/verify-otp` | `{email, token}` | |
| `POST /api/auth/reset-password` | `{email}` | SẴN (gửi email khôi phục) |

**CHƯA CÓ (đánh dấu _CHỜ API_ trong thiết kế, backend chưa implement):**

| Proposed endpoint | Body | Ghi chú |
| :--- | :--- | :--- |
| `POST /api/auth/change-password` | `{currentPassword, newPassword}` | Đổi mật khẩu trong app khi đã đăng nhập |
| `POST /api/auth/confirm-reset-password` | `{email, otp, newPassword}` | Bước xác nhận của luồng quên mật khẩu (spec A-03) |

**eKYC (dựa trên `mobile/lib/features/ekyc/models/ekyc_models.dart` + `ekyc_verification_screen.dart`):**

- `POST /api/v1/worker/ekyc/submit` — multipart: `frontImage`, `backImage`, `selfieImage`, optional `cccdQrData` / `fullName` / `dob` / `gender`.
- `GET /api/v1/worker/ekyc/status` → `NOT_SUBMITTED` / `PENDING_REVIEW` / `APPROVED` / `REJECTED`.

---

# SECTION 2 — 5 KHỐI PROMPT (copy từng khối vào OpenDesign)

## PROMPT 1 — R-01 "Đăng ký Step 1/3: Thông tin cơ bản"

**Bối cảnh tự chứa cho tool thiết kế:** Ứng dụng Flutter tên **CANi Worker** (nền tảng sự kiện, staff). Thiết kế 1 màn hình mobile **R-01** trong luồng đăng ký 3 bước mới. Mục đích màn này: **thu thập danh tính (họ tên, email, SĐT, mật khẩu)** và xác thực email **ở bước 2** — nghĩa là màn R-01 **KHÔNG có ô nhập OTP** (khác `register_screen.dart` hiện tại đang gộp OTP inline). Token: nền trắng; nút chính bo góc 12px, gradient `linear-gradient(#00D2FF → #0066FF)`, shadow `#0077FF @35%` blur 14, chữ trắng 600; accent `#0077FF`; field bo góc 12, icon prefix `#0077FF` size 20, nền tĩnh `#F8FAFC`, focus trắng, border `#E2E8F0`; error tile nền `#FEF2F2` viền `#FECACA` chữ `#DC2626`; logo `assets/cani_logo.png` (widget `CaniBrandLogo`) width 80% màn clamp 200–340dp; font Be Vietnam Pro (tiêu đề 600–900), Plus Jakarta Sans (body).

**UI tree (vẽ đúng thứ tự):**

```text
Scaffold (nền trắng)
└── SafeArea
    └── SingleChildScrollView (padding: 24)
        ├── 1. Top bar: nút back tròn 40x40 (nền #F0F7FF, icon arrow_back_ios_new)
        ├── 2. CaniBrandLogo (80% màn, clamp 200–340dp)
        ├── 3. Title "Tạo Tài Khoản Worker" (Be Vietnam Pro 21/800, #0B1B3D)
        ├── 4. Subtitle "Bước 1/3 · Nhập thông tin cơ bản" (13, #64748B)
        ├── 5. Step indicator 3 điểm + nhãn: [1] Thông tin · [2] Xác thực · [3] Định danh
        │    (điểm 1 active: nền #0077FF chữ trắng; điểm 2-3: nền #F1F5F9 chữ #94A3B8; có gạch nối giữa)
        ├── 6. Google fast-track: OutlinedButton "Đăng ký nhanh với Google" (nền trắng, viền #E2E8F0, bo góc 12, logo Google)
        ├── 7. Divider: "— Hoặc điền thông tin bên dưới —" (đường kẻ #E2E8F0 + chữ #94A3B8 12)
        ├── 8. Field Họ và tên (icon person_outline #0077FF)
        ├── 9. Field Email (icon email_outlined #0077FF)
        ├── 10. Field Số điện thoại (icon phone_outlined #0077FF, hint "0912 345 678")
        ├── 11. Field Mật khẩu (icon lock_outline #0077FF, suffix toggle visibility)
        ├── 12. Field Xác nhận mật khẩu (icon lock_outline #0077FF, suffix toggle)
        ├── 13. Checkbox điều khoản: ô vuông 20x20 (tích xanh #0077FF) + chữ "Tôi đồng ý với Điều khoản dịch vụ & Chính sách bảo mật" (2 phần link underline màu #0077FF)
        ├── 14. Nút chính "Tạo Tài khoản →" (gradient #00D2FF→#0066FF, bo góc 12, cao 52, disabled: xám #E2E8F0 chữ #94A3B8)
        └── 15. Footer: "Bạn đã có tài khoản? " + link "Đăng nhập ngay" (#0077FF, 600)
```

**Bảng trường + validation + lỗi (tiếng Việt):**

| Trường | Bắt buộc | Validation | Lỗi hiển thị (dưới field, 13, #DC2626) |
| :--- | :--- | :--- | :--- |
| Họ và tên | Yes | 2–100 ký tự, không ký tự đặc biệt | "Vui lòng nhập họ và tên đầy đủ" |
| Email | Yes | Định dạng email hợp lệ | "Vui lòng nhập email hợp lệ" |
| Số điện thoại | No | Regex VN `^(0\|84)[3\|5\|7\|8\|9][0-9]{8}$` | "Số điện thoại không hợp lệ" |
| Mật khẩu | Yes | ≥8 ký tự, ≥1 chữ in hoa | "Mật khẩu tối thiểu 8 ký tự và cần ít nhất 1 chữ in hoa" |
| Xác nhận mật khẩu | Yes | Khớp 100% với Mật khẩu | "Mật khẩu xác nhận không khớp" |
| Điều khoản | Yes | Phải tick | "Bạn cần đồng ý điều khoản để tiếp tục" |

**Trạng thái:** (a) Default: form rỗng, nút "Tạo Tài khoản" **disabled** (xám) — enable khi đủ 5 field hợp lệ + tick điều khoản; (b) Nêu lỗi: field sai có border `#DC2626` + message đỏ dưới field, không hiện error tile to; (c) Submitting: nút chuyển sang spinner tròn trắng + chữ "Đang tạo tài khoản..."; (d) Thành công: **chuyển màn R-02, giữ nguyên email đã nhập** (data context, không cần user nhập lại).

**Microcopy:** title "Tạo Tài Khoản Worker"; subtitle "Bước 1/3 · Nhập thông tin cơ bản"; hint SĐT "0912 345 678"; hint mật khẩu "Tối thiểu 8 ký tự, có 1 chữ in hoa"; checkbox "Tôi đồng ý với Điều khoản dịch vụ & Chính sách bảo mật"; nút "Tạo Tài khoản →"; footer "Bạn đã có tài khoản? Đăng nhập ngay".

**Điều hướng:** back button → LoginScreen; footer link "Đăng nhập ngay" → LoginScreen; submit OK → R-02 (truyền `email` sang); Google fast-track → đăng ký SSO (quá trình riêng, giữ nguyên hành vi hiện tại).

---

## PROMPT 2 — R-02 "Đăng ký Step 2/3: Xác thực OTP email"

**Bối cảnh tự chứa:** Màn **R-02** (bước 2/3) của luồng đăng ký CANi Worker, thiết kế theo spec `A-02_email_verification_otp.md`. Mục đích: xác minh quyền sở hữu email sau khi bước 1 hoàn tất; OTP gửi qua `POST /api/auth/send-otp` và verify qua `POST /api/auth/verify-otp` (cả 2 **SẴN**). Token: nền trắng; 6 ô OTP vuông bo góc 12, cao ~52, kích thước mỗi ô ~48, chữ 20/700 trung tâm; ô active có border `#0077FF` 2px + shadow nhẹ, ô thường border `#E2E8F0` nền `#F8FAFC`; success `#10B981` (nền `#ECFDF5`); error tile nền `#FEF2F2` viền `#FECACA` chữ `#DC2626`; nút chính gradient `linear-gradient(#00D2FF → #0066FF)` bo góc 12 shadow `#0077FF @35%` blur 14; accent `#0077FF`; font Be Vietnam Pro (tiêu đề) + Plus Jakarta Sans (body).

**UI tree:**

```text
Scaffold (nền trắng)
├── Top bar: back → R-01 (bảo toàn form bước 1)
├── Illustration icon: hộp thư có khóa (SVG 80x80, tông #E0F2FE)
├── Header "Nhập mã xác thực" (Be Vietnam Pro 22/800, #0B1B3D)
├── RichText "Mã 6 số đã được gửi tới **{email}**" (email in đậm #0077FF, phần còn lại 14 #64748B)
├── Step indicator: [1] Thông tin ✓ (xanh #10B981) · [2] Xác thực (active) · [3] Định danh (chờ)
├── Hàng 6 ô OTP (square input, auto-focus ô 1, bàn phím số, auto-advance, auto-paste clipboard 6 số)
├── Countdown + resend pill: countdown 30s chữ "Gửi lại (28s)" xám #94A3B8, pill button "Gửi lại mã OTP" (nền #0077FF chữ trắng bo tròn, disable đếm ngược)
├── Ghi chú "Đã gửi tới email ✓" (12, #10B981, icon check)
├── Nút "XÁC NHẬN & TIẾP TỤC" (gradient #00D2FF→#0066FF, bo góc 12, cao 52, disable khi chưa đủ 6 số)
└── Footer "Không nhận được mã? Kiểm tra thư rác hoặc liên hệ CSKH" (12, #64748B, "CSKH" link #0077FF)
```

**Bảng trường + validation + lỗi:**

| Trường | Validation | Lỗi / Ghi chú |
| :--- | :--- | :--- |
| OTP (6 ô) | `^\d{6}$` | "Vui lòng nhập mã 6 chữ số" (hiện khi chạm submit khi thiếu số) |
| Countdown | 30s, pill disable khi >0 | "Gửi lại (28s)" → hết 30s hiện "Gửi lại mã OTP" |
| TTL mã | 10 phút | Lỗi khi sai/quá hạn: "Mã xác thực không đúng hoặc đã hết hạn (tối đa 10 phút)" |

**Trạng thái:** (a) Default: ô 1 auto-focus; (b) Loading: verify-otp → spinner tròn trong nút + nút khóa; (c) Success: tick xanh `#10B981` (nền `#ECFDF5`, "Xác thực email thành công!") rồi **tự chuyển R-03**; (d) Error: 6 ô **shake** (rung 300ms) + error tile "Mã xác thực không đúng hoặc đã hết hạn (tối đa 10 phút)" hiện trên hàng ô.

**Microcopy:** title "Nhập mã xác thực"; rich-text "Mã 6 số đã được gửi tới {email}"; pill "Gửi lại mã OTP" / "Gửi lại (28s)"; label "Đã gửi tới email ✓"; nút "XÁC NHẬN & TIẾP TỤC"; footer "Không nhận được mã? Kiểm tra thư rác hoặc liên hệ CSKH".

**Điều hướng:** back → R-01; verify OK (tick xanh, ~600ms) → R-03; "Gửi lại mã OTP" gọi `send-otp` (otpType REGISTER) và reset countdown 30s.

---

## PROMPT 3 — R-03 "Đăng ký Step 3/3: Định danh eKYC (CCCD)"

**Bối cảnh tự chứa:** Màn **R-03** (bước 3/3, màn cuối của luồng đăng ký) của CANi Worker, thiết kế theo spec `E-01` + `ekyc_verification_screen.dart` hiện có. Mục đích: submit hồ sơ eKYC CCCD (2 ảnh thẻ + selfie) để qua **gate vào Home** — chỉ vào Home khi eKYC đã submit (PENDING_REVIEW hoặc APPROVED). API (SẴN): `POST /api/v1/worker/ekyc/submit` (multipart: frontImage, backImage, selfieImage, optional cccdQrData/fullName/dob/gender) và `GET /api/v1/worker/ekyc/status` (trạng thái NOT_SUBMITTED / PENDING_REVIEW / APPROVED / REJECTED). Token: nền trắng; 3 capture box bo góc 12, mỗi box cao ~140, có preview hoặc placeholder khung chấm nét; status chip: PENDING_REVIEW vàng `#F59E0B` (nền `#FFFBEB`), APPROVED xanh `#10B981` (nền `#ECFDF5`), REJECTED đỏ `#DC2626` (nền `#FEF2F2`); nút chính gradient `linear-gradient(#00D2FF → #0066FF)` bo góc 12 shadow `#0077FF @35%` blur 14; accent `#0077FF`; error tile nền `#FEF2F2` viền `#FECACA` chữ `#DC2626`; banner Home: PENDING vàng nền `#FFFBEB` viền `#F59E0B` chữ `#92400E`, REJECTED nền `#FEF2F2` viền `#FCA5A5` chữ `#DC2626`, APPROVED nền `#ECFDF5` chữ `#10B981`; logo `assets/cani_logo.png` (widget `CaniBrandLogo`) 80% màn clamp 200–340dp; font Be Vietnam Pro + Plus Jakarta Sans.

**UI tree:**

```text
Scaffold (nền trắng)
├── Top bar: back → R-02 (bảo toàn OTP đã verify)
├── Header "Định danh CCCD" (Be Vietnam Pro 21/800, #0B1B3D)
├── Subtitle "Bước 3/3 · Chụp & xác minh giấy tờ để vào ứng dụng" (13, #64748B)
├── Step indicator: [1] ✓ · [2] ✓ · [3] active
├── 3 Capture Boxes (đánh dấu "Bước 1/2/3" theo E-01):
│    ├── Box 1 "Mặt trước CCCD" (placeholder khung chấm nét hoặc preview ảnh)
│    ├── Box 2 "Mặt sau CCCD" (idem)
│    └── Box 3 "Selfie" (khung oval căn khuôn mặt)
│    Mỗi box có icon camera + nút "Chọn ảnh" mở BOTTOM SHEET 3 nguồn:
│       (a) "Quét mã QR góc thẻ CCCD" (100% dữ liệu, không AI)
│       (b) "Chụp bằng Camera" (khung căn chỉnh, flash, nút shutter)
│       (c) "Chọn từ thư viện"
├── Banner gợi ý (nền #EFF6FF viền #BFDBFE chữ #1E40AF): "Mẹo: Quét Mã QR CCCD / Sửa Thông Tin nhanh hơn & chính xác hơn so với chụp thủ công"
├── Card "Thông tin CCCD đã xác nhận" (bo góc 12, nền #F8FAFC, hiện khi quét QR/chụp OCR thành công):
│    Số CCCD (mã hóa 079*****123) · Họ tên · Ngày sinh · Giới tính · Nơi thường trú · Ngày cấp
│    + Nút "Sửa" (icon edit, #0077FF, mở form chỉnh tay từng field)
├── Nút chính "Gửi Hồ Sơ Xác Thực eKYC" (gradient #00D2FF→#0066FF, bo góc 12, cao 52, disabled khi thiếu 1 trong 3 ảnh)
└── Result card (hiện sau submit, thay thế 3 box):
     ├── Status chip: PENDING_REVIEW (vàng) / APPROVED (xanh) / REJECTED (đỏ + lý do routingReason)
     ├── Metric "Độ khớp khuôn mặt: 92%" (bảng đồ vòng tròn, ≥85% đạt, màu #10B981; dưới 85% màu #F59E0B)
     └── Metric "Độ tin cậy OCR: 95%" (idem)
```

**Bảng trường + validation + lỗi:**

| Dữ liệu | Validation | Lỗi / Ghi chú |
| :--- | :--- | :--- |
| frontImage / backImage / selfieImage | Có đủ 3 ảnh | Nút submit disable + hint "Vui lòng nạp đủ 3 ảnh trước khi gửi" |
| CCCD QR (nếu dùng nguồn a) | Parse mã thành công | "Không đọc được mã QR, vui lòng thử lại hoặc chụp bằng Camera" |
| OCR/face match | Face match ≥85% | Lỗi: "Ảnh selfie không khớp với CCCD (yêu cầu ≥85%)" |
| Submit error | HTTP 4xx/5xx | Error tile: "Không thể gửi hồ sơ, vui lòng kiểm tra kết nối và thử lại" |

**Trạng thái:** (a) Default: 3 box rỗng + submit disable; (b) Loading: spinner trong nút "Đang gửi hồ sơ..."; (c) Success: result card + status chip; PENDING_REVIEW → chuyển Home + banner vàng; APPROVED → Home + badge xanh; (d) REJECTED: result card đỏ + routingReason + CTA "Nộp lại hồ sơ eKYC" (về lại 3 box, giữ dữ liệu đã OCR).

**Microcopy:** title "Định danh CCCD"; subtitle "Bước 3/3 · Chụp & xác minh giấy tờ để vào ứng dụng"; 3 box labels "Mặt trước CCCD" / "Mặt sau CCCD" / "Selfie"; bottom sheet labels "Quét mã QR CCCD (100% dữ liệu, không AI)" / "Chụp bằng Camera" / "Chọn từ thư viện"; banner "Mẹo: Quét Mã QR CCCD / Sửa Thông Tin nhanh hơn & chính xác hơn so với chụp thủ công"; card title "Thông tin CCCD đã xác nhận"; nút "Sửa"; nút submit "Gửi Hồ Sơ Xác Thực eKYC"; metric labels "Độ khớp khuôn mặt" / "Độ tin cậy OCR"; CTA "Nộp lại hồ sơ eKYC".

**KÈM THEO — Banner eKYC trên HomeScreen (phải thiết kế luôn trong prompt này, vì gate vào Home):**

```text
HomeScreen (nền trắng, tab bar 4 tab)
├── Top banner (theo GET /api/v1/worker/ekyc/status, bo góc 12, chiều cao ~72, đặt dưới app bar):
│    ├── PENDING_REVIEW → nền #FFFBEB, viền #F59E0B, icon hourglass:
│         "Hồ sơ định danh đang chờ thẩm định (15–30 phút)" + phụ đề "Bạn đã có thể xem ca, hệ thống sẽ thông báo khi duyệt xong"
│    ├── REJECTED → nền #FEF2F2, viền #FCA5A5, icon cancel:
│         "Hồ sơ eKYC bị từ chối: {routingReason}" + CTA nút "Nộp lại hồ sơ" (#DC2626, bo tròn) → R-03
│    └── APPROVED → không có banner, thay bằng badge xanh nhỏ góc avatar:
│         chip "Đã chứng thực ✓" (nền #ECFDF5, chữ #10B981, bo tròn, 12/600)
```

**Điều hướng:** back → R-02; submit PENDING/APPROVED → HomeScreen (+ banner/badge tương ứng); REJECTED → CTA "Nộp lại hồ sơ eKYC" giữ trong R-03; Home → CTA "Nộp lại hồ sơ" (banner đỏ) → R-03.

---

## PROMPT 4 — P-01 "Quên mật khẩu (2 bước)"

**Bối cảnh tự chứa:** Luồng **P-01** "Quên mật khẩu" của CANi Worker, thiết kế theo spec `A-03_reset_password.md`, 2 bước trong 1 màn (PageView swipe-disable + StepController). Mục đích: khôi phục truy cập khi quên mật khẩu. **Entry-point (yêu cầu thiết kế thêm, hiện login_screen.dart KHÔNG có):** link "Quên mật khẩu?" đặt **bên dưới trường mật khẩu** trên LoginScreen. API: Step 1 gọi `POST /api/auth/reset-password` (body `{email}`) — **SẴN**; Step 2 xác nhận mới **CHỜ API** `POST /api/auth/confirm-reset-password` (body `{email, otp, newPassword}`, backend chưa implement — phải đánh dấu rõ trong thiết kế). Token: nền trắng; field bo góc 12, icon prefix `#0077FF` size 20, nền `#F8FAFC`, focus trắng, border `#E2E8F0`; 6 ô OTP vuông bo góc 12 (active border `#0077FF` 2px); nút chính gradient `linear-gradient(#00D2FF → #0066FF)` bo góc 12 cao 52 shadow `#0077FF @35%` blur 14; success `#10B981` (nền `#ECFDF5`); error tile nền `#FEF2F2` viền `#FECACA` chữ `#DC2626`; checklist độ mạnh: 4 dòng checkbox vuông 16 (tích `#10B981` khi đạt, chưa đạt màu `#94A3B8`); accent `#0077FF`; font Be Vietnam Pro + Plus Jakarta Sans.

**UI tree:**

```text
(a) TRÊN LOGIN SCREEN (thiết kế thêm vào login_screen.dart):
    Field Mật khẩu (icon lock_outline)
    └── Dưới field, căn trái: link "Quên mật khẩu?" (#0077FF, 13/600, chạm → mở P-01)

P-01 (Scaffold, PageView 2 trang, swipe vô hiệu, có top bar back → LoginScreen)
├── STEP 1 "Nhập email":
│    ├── Icon khóa mở (80x80, nền #E0F2FE bo tròn 20, icon #0077FF)
│    ├── Title "Quên mật khẩu?" (Be Vietnam Pro 22/800, #0B1B3D)
│    ├── Subtitle "Nhập email đã đăng ký, chúng tôi sẽ gửi mã khôi phục" (13, #64748B)
│    ├── Field Email (icon email_outlined #0077FF, hint "worker@cani.vn")
│    ├── Nút "GỬI MÃ XÁC NHẬN" (gradient #00D2FF→#0066FF, bo góc 12, cao 52)
│    └── State sent: thay nút bằng tile xanh "Đã gửi mã khôi phục, vui lòng kiểm tra hộp thư của bạn (cả thư rác)" (nền #ECFDF5, chữ #10B981, bo góc 12)
└── STEP 2 "Thiết lập mật khẩu mới":
     ├── Title "Thiết lập mật khẩu mới" (Be Vietnam Pro 21/800, #0B1B3D)
     ├── 6 ô OTP (mã từ email, auto-advance, paste, bàn phím số)
     ├── Field "Mật khẩu mới" (obscure + toggle visibility, icon lock_outline)
     ├── Field "Xác nhận mật khẩu mới" (idem)
     ├── Checklist độ mạnh 4 dòng (tích dần khi gõ):
     │     □ ≥ 8 ký tự · □ Có chữ in hoa · □ Có chữ số · □ Có ký tự đặc biệt (!@#$%...)
     ├── Ghi chú nhỏ (12, #94A3B8, icon info): "Bước này chờ API: POST /api/auth/confirm-reset-password chưa có trên backend"
     └── Nút "ĐỔI MẬT KHẨU & ĐĂNG NHẬP" (gradient, disable khi checklist chưa đủ 4/4 hoặc OTP chưa đủ)
```

**Bảng trường + validation + lỗi:**

| Trường (bước) | Validation | Lỗi (13, #DC2626) |
| :--- | :--- | :--- |
| email (S1) | Định dạng email hợp lệ | "Vui lòng nhập email hợp lệ" |
| otp (S2) | `^\d{6}$` | "Vui lòng nhập mã 6 chữ số" |
| newPassword (S2) | ≥8, ≥1 hoa, ≥1 số, ≥1 đặc biệt (4/4) | "Mật khẩu chưa đủ độ mạnh yêu cầu" (kèm checklist chưa tích) |
| confirm (S2) | Khớp 100% newPassword | "Mật khẩu xác nhận không trùng khớp" |

**Trạng thái:** (a) S1 default: field rỗng + nút enable; (b) S1 loading: spinner trong nút "Đang gửi mã..."; (c) S1 sent: tile xanh "Kiểm tra hộp thư của bạn" + nút "Tôi đã nhận mã" (→ S2); (d) S2 loading: spinner "Đang đổi mật khẩu..."; (e) S2 success: toast "Đổi mật khẩu thành công!" (~2s) rồi **tự về LoginScreen** (prefill email của S1, focus vào ô mật khẩu); (f) S2 error: error tile "Không thể đổi mật khẩu, vui lòng kiểm tra kết nối và thử lại".

**Microcopy:** S1 title "Quên mật khẩu?"; S1 subtitle "Nhập email đã đăng ký, chúng tôi sẽ gửi mã khôi phục"; S1 nút "GỬI MÃ XÁC NHẬN"; S1 sent "Đã gửi mã khôi phục, vui lòng kiểm tra hộp thư của bạn (cả thư rác)"; S2 title "Thiết lập mật khẩu mới"; S2 nút "ĐỔI MẬT KHẨU & ĐĂNG NHẬP"; toast "Đổi mật khẩu thành công!".

**Điều hướng:** Login → link "Quên mật khẩu?" → P-01 S1; back top-bar → LoginScreen; S1 "Tôi đã nhận mã" → S2; S2 submit OK → toast + về LoginScreen (prefill email, focus ô mật khẩu).

---

## PROMPT 5 — P-02 "Đổi mật khẩu (trong app, đã đăng nhập)"

**Bối cảnh tự chứa:** Màn **P-02** "Đổi mật khẩu" của CANi Worker, chỉ dùng khi worker **đã đăng nhập**. Entry-point: tab **Tài khoản** → block **"Bảo mật"** (hiện `profile_block_security.dart` có row "Đổi mật khẩu" nhưng ghi nhãn "Sắp ra mắt" — yêu cầu thiết kế màn đích P-02 này để thay thế nhãn đó, bỏ "Sắp ra mắt"). Mục đích: worker chủ động đổi mật khẩu trong app. **⚠ CHỜ API: backend chưa có endpoint — proposed `POST /api/auth/change-password` với body `{currentPassword, newPassword}`** (đánh dấu rõ bằng gạch đầu dòng "CHỜ API" trong thiết kế; UI phải hoàn chỉnh sẵn để wire ngay khi API có). Token: nền trắng; field bo góc 12, icon prefix `#0077FF` size 20, nền `#F8FAFC`, focus trắng, border `#E2E8F0`; nút chính gradient `linear-gradient(#00D2FF → #0066FF)` bo góc 12 cao 52 shadow `#0077FF @35%` blur 14; success toast nền `#ECFDF5` chữ `#10B981`; error tile nền `#FEF2F2` viền `#FECACA` chữ `#DC2626`; checklist độ mạnh: 4 dòng checkbox vuông 16 (tích `#10B981` khi đạt, chưa đạt `#94A3B8`); accent `#0077FF`; font Be Vietnam Pro + Plus Jakarta Sans.

**UI tree:**

```text
ProfileScreen (tab Tài khoản)
└── Block "Bảo mật" (bo góc 12, nền #F8FAFC)
     ├── Row 1: icon shield_outline + "Đăng xuất" (14/600, #0F172A, chevron →)
     └── Row 2: icon key_outline + "Đổi mật khẩu" (14/600, #0F172A)
            ├── Bỏ nhãn "Sắp ra mắt" (gạch đầu dòng "CHỜ API: backend chưa có endpoint")
            └── Chạm → P-02

P-02 (Scaffold, top bar back → Profile, title "Đổi mật khẩu")
└── SingleChildScrollView (padding 24)
     ├── Subtitle "Nhập mật khẩu hiện tại và mật khẩu mới để tăng cường bảo mật tài khoản" (13, #64748B)
     ├── Field "Mật khẩu hiện tại" (icon lock_outline #0077FF, obscure + toggle)
     ├── Field "Mật khẩu mới" (idem)
     ├── Field "Xác nhận mật khẩu mới" (idem)
     ├── Checklist độ mạnh 4 dòng (tích dần khi gõ, chỉ dùng với "Mật khẩu mới"):
     │     □ ≥ 8 ký tự · □ Có chữ in hoa · □ Có chữ số · □ Có ký tự đặc biệt (!@#$%...)
     ├── Ghi chú (12, #94A3B8, icon info, gạch đầu dòng "CHỜ API"): "Endpoint POST /api/auth/change-password chưa có trên backend, UI thiết kế sẵn để wire khi API có"
     └── Nút "Lưu mật khẩu mới" (gradient #00D2FF→#0066FF, bo góc 12, cao 52, disabled khi 3 field sai hoặc checklist 4/4 chưa đủ)
```

**Bảng trường + validation + lỗi:**

| Trường | Validation | Lỗi (13, #DC2626) |
| :--- | :--- | :--- |
| currentPassword | Không trống | "Vui lòng nhập mật khẩu hiện tại" |
| newPassword | ≥8, ≥1 hoa, ≥1 số, ≥1 đặc biệt | "Mật khẩu mới chưa đủ độ mạnh yêu cầu" (kèm checklist) |
| confirmPassword | Khớp 100% newPassword | "Xác nhận mật khẩu không khớp" |

**Trạng thái:** (a) Default: form rỗng, nút disabled; (b) Loading: spinner trong nút "Đang lưu..."; (c) Success: toast "Mật khẩu đã được cập nhật thành công!" (nền `#ECFDF5`, chữ `#10B981`, ~2s) rồi back về Profile (row "Đổi mật khẩu" giữ nhãn CHỜ API / "Sẽ kết nối khi backend có endpoint"); (d) Error (API 401/400): error tile "Mật khẩu hiện tại không đúng, vui lòng nhập lại" — chỉ hiện khi API ready (chưa wire thì ghi chú "CHỜ API").

**Microcopy:** subtitle "Nhập mật khẩu hiện tại và mật khẩu mới để tăng cường bảo mật tài khoản"; field labels "Mật khẩu hiện tại" / "Mật khẩu mới" / "Xác nhận mật khẩu mới"; checklist "≥ 8 ký tự" / "Có chữ in hoa" / "Có chữ số" / "Có ký tự đặc biệt (!@#$%...)"; nút "Lưu mật khẩu mới"; toast "Mật khẩu đã được cập nhật thành công!".

**Điều hướng:** tab Tài khoản → block "Bảo mật" → row "Đổi mật khẩu" (bỏ "Sắp ra mắt", gắn nhãn "CHỜ API") → P-02; back top-bar → Profile; submit OK → toast + về Profile (row "Đổi mật khẩu" giữ nhãn CHỜ API / "Sẽ kết nối khi backend có endpoint").
