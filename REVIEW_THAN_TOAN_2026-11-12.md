# REVIEW PHẦN THANH TOÁN (PAYOS SUBSCRIPTION + DIRECT PAYMENT) — CANi
> Ngày: 12/11/2026 · Nguồn: subagent deep-review (đã boot backend H2 port 8099 + probe thật) **đã được verify lại từng claim quan trọng trên code thật** (mục "Đã verify" dưới mỗi finding).
>
> ⚠️ **CẬP NHẬT 12/11 sau commit `78c8002`** ("gate demo subscriptions, enforce payment unique constraint and prevent free trial abuse"): các mục đánh dấu ✅ ĐÃ SỬA dưới đây **đã được vá trong code hiện tại**. Mục không đánh dấu vẫn còn nguyên.

---

## 🔴 CRITICAL

### ✅ C1 — ĐÃ SỬA một phần — kích hoạt gói có tiền giờ **bị gate** khi demo tắt
**File:** `backend/.../service/SubscriptionService.java` (`confirmPayOSPaymentForUser`)

**Đã verify state hiện tại (sau `78c8002`):**
```java
if (demoEnabled) {
    return activateSubscription(eo, order.planType(), "PAYOS_VIETQR_DEMO", "PAYOS_" + orderCode, "POS_" + orderCode);
}
throw new BadRequestException("Giao dịch đang chờ xác thánh toán từ cổng PayOS. Vui lòng hoàn tất chuyển khoản và chờ trong giây lát.");
```
→ Khi `cani.subscription.demo-enabled=false` (mặc định trong `application.yml:102`), nút confirm **không kích hoạt được gói nữa**. ✅

**Còn lại (chưa sửa):** nhánh fallback khi `pendingOrders.remove` trả null vẫn trả sub ACTIVE hiện tại làm "success" — tức **trong profile h2** (`demo-enabled=true`, `application-h2.yml:42`) đường này vẫn mở. Và bản chất vẫn là **không có bằng chứng thanh toán**: không query PayOS (`/v2/query-order`), không record giao dịch trong DB.
**Fix còn lại:** persist bảng `payos_orders` với trạng thái PAID từ webhook; nút confirm chỉ kích hoạt khi có record đó.

### ✅ C2 — ĐÃ SỬA — `demo-simulate` giờ có gate theo cờ
**Đã verify:** `SubscriptionService.java:129` — `if (!demoEnabled) throw new AppException("Chức năng mô ph�ơng thanh toán bị vô hiệu hóa trên môi trường này.", HttpStatus.FORBIDDEN);`
Cờ: `application.yml:102` `demo-enabled: ${SUBSCRIPTION_DEMO_ENABLED:false}` (mặc định **off**) · `application-h2.yml:42` `demo-enabled: true` (chỉ profile demo). Web đã ẩn nút khi flag off.
✅ **Đã đóng ở môi trường production-like.** Chỉ còn mở ở profile h2 — tức đúng chỗ demo cần.

### 🔴 C3 — **CÒN NGUYÊN** — Webhook PayOS verify SAI SPEC → webhook thật 100% bị reject
**File:** `PayOSConfig.java:38,64,68` (`hmacSha256`), `SubscriptionService.processPayOSWebhook`

**Đã verify lại sau `78c8002`:** `grep -n "x-signature|SHA512|hmacSha" PayOSConfig.java` → **vẫn chỉ `hmacSha256`**, không có SHA512, không đọc header `x-signature`. Code vẫn tính **HMAC-SHA256** trên chuỗi `key=value&key=value` (sort A-Z) của `data`, signature lấy từ **body** (`payload.get("signature")`).
Spec PayOS thật: header **`x-signature`** = Hex(**HMAC-SHA512** của **raw body**, key = checksum key).

**Hậu quả kép (chưa giảm):** (1) Webhook **thật** từ PayOS bị reject → kích hoạt tự động **chết** → mọi đường dồn về nút tay; (2) attacker biết checksum key (`.env`, chưa rotate) forge được theo đúng thuật toán tự-chế này.
**Probe:** POST webhook với signature rác → 400 "Chữ ký bảo mật PayOS không hợp lệ" (verify có tồn tại; giả thuyết "ai cũng giả webhook được" là **sai** ở code hiện tại — nhưng verify sai spec nên đường tự động vô dụng).
**Fix:** đọc header `x-signature`, HMAC-SHA512 raw body theo spec PayOS; test bằng webhook sample của PayOS.

---

## 🟠 HIGH

### 🔴 H1 — **CÒN NGUYÊN** — Upgrade gói = quota cũ + thời hạn cũ bị **ghi đè** (không cộng dồn)
`activateSubscription` luôn tạo sub mới `eventsUsed=0` và trỏ `eo.setSubscription(sub mới)`; quota đọc sub **mới nhất** (`findActiveSubscriptions ... ORDER BY createdAt DESC`, `.get(0)`).
**Kịch bản:** EO dùng 1/5 event của MATCHING_PLUS (còn 4 credit + 25 ngày) → mua ENTERPRISE → mất 4 credit + 25 ngày. Khách trả tiền 2 gói mà mất quota gói cũ. **Không nằm trong commit `78c8002`.**
**Fix:** renew gộp: `expiresAt = max(cũ, now) + durationMới`, hoặc chuyển quota dư; tối thiểu ghi rõ rule trong SRS.

### 🔴 H2 — **CÒN NGUYÊN** — `pendingOrders` (RAM) mất khi restart + orderCode có thể collision
`ConcurrentHashMap`: restart giữa "tạo session" và "webhook/confirm" → order biến mất. Webhook trả **200 "Accepted"** mà không kích hoạt (fake-ack) → gateway coi processed → **tiền về mà không nhận được gói**. `orderCode = now % 900000000 + 100000000` — 2 EO cùng tick → ghi đè, không có collision check.
**Fix:** persist bảng `payos_orders`; bỏ fake-ack.

### ✅ H3 — ĐÃ SỬA — unique constraint `payments.assignment_id`
**Đã verify lại:** `Payment.java:16-21` khai báo `uniqueConstraints = @UniqueConstraint(name="uk_payments_assignment_id", columnNames={"assignment_id"})` + migration `V25__add_unique_constraint_to_payments_assignment_id.sql` + `DirectPaymentService.java:93-99` catch `DataIntegrityViolationException` → tìm lại record. ✅ **Mục này tôi đã ghi SAI trong bản đầu (chưa đọc tới V25) — đã sửa lại ở đây.**
*Còn lại (nhỏ):* `applyForShift` vẫn check-then-act cho assignment (`existsByShift...Worker` rồi mới save) — nên thêm UNIQUE `(shift_id, worker_id)` trên `shift_assignments` cho chắc.

### ✅ H4 — ĐÃ SỬA — FREE_TRIAL không farm được + không ghi đè gói paid
**Đã verify sau `78c8002`:** `SubscriptionService.java:227-243` — thêm 2 guard: (1) nếu EO đang có gói **trả phí** ACTIVE → `throw BadRequestException("Tài khoản đang có gói dịch vụ trả phí hoạt động, không thể chuyển sang gói dùng thử.")`; (2) nếu đã từng có FREE_TRIAL → `throw BadRequestException("Mỗi Nhà tổ chức sự kiện chỉ được sử dụng Gói Trải Nghiệm 1 lần duy nhất.")`. ✅ Cả 2 kịch bản (farm + downgrade tự do) đã bị chặn.

---

## 🟡 MEDIUM

- **M1 — Direct payment không có state machine:** `confirmPayment` (line 136-167) không check `payment.status` → worker "confirm nhận đủ" ngay cả khi EO **chưa declare** (NOT_DECLARED, declaredAmount=null) = xác nhận khoản tiền chưa tồn tại; dispute đặt lên mọi trạng thái; re-declare sau khi worker đã chốt cũng không chặn. (Lưu ý: hệ non-escrow, tiền không qua nền tảng — confirm chỉ là **đối soát**, đúng SRS UC-11/12 — rủi ro là dữ liệu đối soát sai, không phải mất tiền.)
- ✅ **M2 — ĐÃ SỬA — Rounding lương qua double:** commit `78c8002` đổi `DirectPaymentService.java` sang `BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)` + `Math.max(60, minutes)`. Không còn money math qua double.
- **M3 — eKYC gate có, quota-gate MỞ (strict=false):** `EoEventService.java:79-82` chặn create event khi `verificationStatus != APPROVED` (tốt, nhưng ở create-DRAFT, chưa phải publish). Kèm `cani.subscription.strict:false` (`application.yml:101`) + `publishEvent` (line 455-461) **nuốt mọi exception không phải BadRequest** của SubscriptionService → hết quota mà non-strict vẫn publish được. Reviewer hỏi "quota có enforce không" → câu trả lời thật: **chỉ khi set env `SUBSCRIPTION_STRICT=true`**.
- **M4 — Web `?payos=success` KHÔNG phải lỗ (đã verify):** `web/.../useSubscription.js:67-73` chỉ showToast + `GET /current` từ backend; kích hoạt thật qua `payos-confirm` (C1) hoặc webhook (chết, C3). Không ai "gõ URL là có gói". Lỗi thật: toast khẳng định "thành công" khi chưa verify gì + `?payos=cancel` **không xử lý**.
- **M5 — Mobile confirmPayment sạch (đã verify):** gửi `RECEIVED_FULL/PARTIAL` (map từ UI `CONFIRMED_*`, `proposal_repository.dart:177-181`), backend accept alias (`DirectPaymentService.java:146-153`) — khớp với mục #2 report cũ. Catch của confirm **throw** thật (không `return true` demo). Lỗi nhỏ: gửi enum sai → 500 (payment-lookup trước status-check) nên nên đổi order: validate status trước → 400.

## 🟢 LOW

- Số tài khoản MBBank cá nhân + `return/cancel-url=localhost` hardcode (`PayOSService.java:84,109`; `application.yml:107-108`) — build prod quên env là user không bao giờ quay về app.
- `.env` chứa PayOS secret (đã verify gitignore, không leak); checksum key dùng cả request lẫn webhook, chưa rotate.

---

## 3 việc CHẶN DEMO (tiền) — cập nhật sau `78c8002`

1. ✅ **Xong** — gate `demo-simulate` + FREE_TRIAL + `payos-confirm` theo cờ `cani.subscription.demo-enabled` (default off, h2 = on). *(C2, H4, C1 một phần)*
2. 🔴 **CÒN** — **Sửa webhook theo spec PayOS**: đọc header `x-signature`, HMAC-**SHA512** raw body (hiện vẫn SHA256 trên body-`signature`). Không sửa thì luồng tự động chết và mọi đường dồn về nút tay. *(C3)*
3. 🔴 **CÒN** — **Persist `payos_orders` ra DB** + bỏ fake-ack webhook 200: kích hoạt gói cần bằng chứng "PAID", restart không mất đơn, hết cảnh "đã trả tiền nhưng không nhận gói". *(C1, H2)*
4. 🟠 **CÒN** — **Cộng dồn quota khi nâng cấp gói** (H1): mua gói mới hiện làm mất quota + hạn dùng gói cũ.

## Điểm sáng (worth keeping)

Anti-cross-tenant có thật (`confirmPayOSPaymentForUser` check `order.eoId() == eo.eoId()`, log cảnh báo IDOR); phân quyền declare/confirm rõ (EO declare / Worker confirm / ADMIN, `DirectPaymentController.java:35,51,68,88,103`); giá subscription dùng **BigDecimal đúng chuẩn** (không float); dev-token chỉ còn sống ở profile h2 (đã cleanup từ trước); mobile payment không dính demo-fallback.
