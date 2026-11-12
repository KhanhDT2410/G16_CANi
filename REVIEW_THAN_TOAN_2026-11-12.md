# REVIEW PHẦN THANH TOÁN (PAYOS SUBSCRIPTION + DIRECT PAYMENT) — CANi
> Ngày: 12/11/2026 · Nguồn: subagent deep-review (đã boot backend H2 port 8099 + probe thật) **đã được verify lại từng claim quan trọng trên code thật** (mục "Đã verify" dưới mỗi finding).

---

## 🔴 CRITICAL

### C1 — Kích hoạt gói CÓ TIỀN mà KHÔNG có bằng chứng thanh toán phía server
**File:** `backend/.../service/SubscriptionService.java:255-276` (`confirmPayOSPaymentForUser`), `PayOSService.java:84-119` (fallback VietQR), `web/.../PayOSQrModal.jsx:139-154` (nút "Tôi Đã Chuyển Khoản Xong")

**Luồng:** `createPayOSPaymentSession` (line ~233) chỉ tạo `PendingOrder` trong `ConcurrentHashMap` bộ nhớ. Web show QR VietQR; người dùng **bấm "Đã Chuyển Khoản Xong"** → `POST /payos-confirm/{orderCode}` → `activateSubscription` (gói 99k/249k). **Không bước nào query PayOS** (không có `/v2/query-order`), không có record giao dịch trong DB.

**Đã verify trên code:** `confirmPayOSPaymentForUser` (line 255-276) — khi `pendingOrders.remove` trả null (reload, restart, orderCode bịa), fallback: nếu EO đã có sub ACTIVE → **trả sub đó làm "success"** (line 270-272); không có → 400. Không có bất kỳ điều kiện "PAID" nào.

**Kịch bản:** EO tạo session MATCHING_PLUS (99k) → KHÔNG chuyển khoản → bấm "Đã chuyển xong" → gói kích hoạt **miễn phí**. Lặp vô hạn (orderCode mới theo `System.currentTimeMillis()`).

**Fix (demo):** gate nhánh payos-confirm: chỉ kích hoạt khi có **record PAID trong DB** ghi bởi webhook (đã verify chữ ký), không phải memory map.

### C2 — Nút `demo-simulate` kích hoạt gói ENTERPRISE (249k) MIỄN PHÍ, mọi build
**File:** `backend/.../controller/SubscriptionController.java:103-111` (`POST /demo-simulate?planType=`)

Nút "Thử nghiệm kích hoạt" nằm sẵn trên từng card gói trong `web/.../EOSubscriptionTab.jsx:199-206` **kể cả gói paid**. Gọi `simulatePurchaseDemo` → `activateSubscription` thật. **Không có profile/env guard.**

**Probe (subagent):** `POST /demo-simulate?planType=ENTERPRISE` (dev-token EO) → HTTP 200, gói 249.000đ kích hoạt, **chưa thu 1đ**.

**Fix:** `@ConditionalOnProperty("cani.subscription.demo-enabled", havingValue="true")` (default false, chỉ bật ở h2) + web giấu nút khi flag off.

### C3 — Webhook PayOS: signature CÓ verify nhưng SAI SPEC → webhook thật 100% bị reject
**File:** `PayOSConfig.java:44-66` (`verifyWebhookSignature`), `SubscriptionService.java:301-326` (`processPayOSWebhook`)

Code hiện tại: **HMAC-SHA256** trên chuỗi `key=value&key=value` (sort A-Z) của `data`, signature nằm **trong body** (`payload.get("signature")`). Spec PayOS thật: header **`x-signature`** = Hex(**HMAC-SHA512** của **raw body**, key = checksum key).

**Hậu quả kép:** (1) Webhook **thật** từ PayOS sẽ bị reject → mọi kích hoạt tự động **chết**, kéo hệ thống về C1 (nút confirm tay) — **đây là root cause khiến C1 thành điểm yếu duy nhất sống**; (2) attacker biết checksum key (nằm `.env`, chưa rotate) có thể forge webhook theo đúng thuật toán tự-chế này.

**Probe:** POST webhook với signature rác → 400 "Chữ ký bảo mật PayOS không hợp lệ" (verify có tồn tại — giả thuyết "ai cũng giả webhook được" là SAI ở code hiện tại, nhưng verify sai spec nên đường tự động vô dụng).

**Fix:** đọc header `x-signature`, HMAC-SHA512 raw body theo spec PayOS; test bằng webhook sample của PayOS.

---

## 🟠 HIGH

### H1 — Upgrade gói = quota cũ + thời hạn cũ bị **ghi đè** (không cộng dồn)
`activateSubscription` (line 331-364) luôn tạo sub mới `eventsUsed=0` và trỏ `eo.setSubscription(sub mới)`; quota đọc sub **mới nhất** (`findActiveSubscriptions ... ORDER BY createdAt DESC`, dùng `.get(0)`).
**Kịch bản:** EO dùng 1/5 event của MATCHING_PLUS (còn 4 credit + 25 ngày) → mua ENTERPRISE → mất 4 credit + 25 ngày. Khách trả tiền 2 gói mà mất quota gói cũ.
**Fix:** renew gộp: `expiresAt = max(cũ, now) + durationMới`, hoặc chuyển quota dư.

### H2 — `pendingOrders` (RAM) mất khi restart + orderCode có thể collision
`ConcurrentHashMap` (line 53): restart giữa "tạo session" và "webhook/confirm" → order biến mất. Webhook trả **200 "Accepted"** mà không kích hoạt (fake-ack, line 284-290) → gateway coi processed → **tiền về mà không nhận được gói**. Client confirm rơi fallback (line 270-275): có sub ACTIVE → "success" (đã trả tiền thật nhưng gói mới chưa kích hoạt), không có → 400. `orderCode = now % 900000000 + 100000000` (line 233) — 2 EO cùng tick → ghi đè, không có collision check.
**Fix:** persist bảng `payos_orders`; bỏ fake-ack.

### H3 — `payments.assignment_id` KHÔNG unique → 2 payment records / 1 ca
`DirectPaymentService.java:90-91`: `findByAssignmentId(...).orElseGet(createInitialPayment)` — check-then-act, 2 request song song cùng assignment → 2 record (double-count thù lao dashboard). `applyForShift` (WorkerJobService ~491-540): guard `existsByShift...Worker` rồi mới save → 2 thread cùng worker+shift đều qua check.
**Fix:** `@UniqueConstraint(columnNames={"assignment_id"})` + catch `DataIntegrityViolation` → 1 dòng constraint, triệt tận gốc.

### H4 — FREE_TRIAL farm được + dùng FREE để **ghi đè gói paid**
`SubscriptionService.java:222-231`: `purchase-payos {FREE_TRIAL}` (0đ) → `activateSubscription` trực tiếp, **không dedup** (probe: lặp được → 10 trial = quota không bao giờ hết). Tồi hơn: `validateAndDeductEventQuota` (line 178-190) auto-cấp trial khi hết sub; và **EO đang có MATCHING_PLUS (5 event) → bấm gói FREE → `eo.subscription` trỏ sang sub FREE (line 362) → quota 5 event trả tiền biến mất** (downgrade tự do bằng 1 click miễn phí).
**Fix:** FREE_TRIAL 1 lần/EO (unique eo+plan); `activateSubscription` không đặt `eo.subscription` sang gói thấp hơn sub paid đang chạy.

---

## 🟡 MEDIUM

- **M1 — Direct payment không có state machine:** `confirmPayment` (line 136-167) không check `payment.status` → worker "confirm nhận đủ" ngay cả khi EO **chưa declare** (NOT_DECLARED, declaredAmount=null) = xác nhận khoản tiền chưa tồn tại; dispute đặt lên mọi trạng thái; re-declare sau khi worker đã chốt cũng không chặn. (Lưu ý: hệ non-escrow, tiền không qua nền tảng — confirm chỉ là **đối soát**, đúng SRS UC-11/12 — rủi ro là dữ liệu đối soát sai, không phải mất tiền.)
- **M2 — Rounding lương qua double:** `DirectPaymentService.java:63-66` `double hours = minutes/60.0` → `hourlyWage.multiply(BigDecimal.valueOf(hours))` → ca 7h20ph = 7.33333... VND lẻ. Đổi sang `BigDecimal.valueOf(minutes).divide(60, 2, HALF_UP)`.
- **M3 — eKYC gate có, quota-gate MỞ (strict=false):** `EoEventService.java:79-82` chặn create event khi `verificationStatus != APPROVED` (tốt, nhưng ở create-DRAFT, chưa phải publish). Kèm `cani.subscription.strict:false` (`application.yml:101`) + `publishEvent` (line 455-461) **nuốt mọi exception không phải BadRequest** của SubscriptionService → hết quota mà non-strict vẫn publish được. Reviewer hỏi "quota có enforce không" → câu trả lời thật: **chỉ khi set env `SUBSCRIPTION_STRICT=true`**.
- **M4 — Web `?payos=success` KHÔNG phải lỗ (đã verify):** `web/.../useSubscription.js:67-73` chỉ showToast + `GET /current` từ backend; kích hoạt thật qua `payos-confirm` (C1) hoặc webhook (chết, C3). Không ai "gõ URL là có gói". Lỗi thật: toast khẳng định "thành công" khi chưa verify gì + `?payos=cancel` **không xử lý**.
- **M5 — Mobile confirmPayment sạch (đã verify):** gửi `RECEIVED_FULL/PARTIAL` (map từ UI `CONFIRMED_*`, `proposal_repository.dart:177-181`), backend accept alias (`DirectPaymentService.java:146-153`) — khớp với mục #2 report cũ. Catch của confirm **throw** thật (không `return true` demo). Lỗi nhỏ: gửi enum sai → 500 (payment-lookup trước status-check) nên nên đổi order: validate status trước → 400.

## 🟢 LOW

- Số tài khoản MBBank cá nhân + `return/cancel-url=localhost` hardcode (`PayOSService.java:84,109`; `application.yml:107-108`) — build prod quên env là user không bao giờ quay về app.
- `.env` chứa PayOS secret (đã verify gitignore, không leak); checksum key dùng cả request lẫn webhook, chưa rotate.

---

## 3 việc CHẶN DEMO (tiền)

1. **Gỡ nút "Đã Chuyển Khoản Xong" trong demo / gate `payos-confirm`**: demo H2 chỉ dùng `demo-simulate` (sandbox, có gate C2); nút confirm chỉ sống khi có record PAID từ webhook đã verify (tương tác C1+C3).
2. **Gate `demo-simulate` + FREE_TRIAL theo flag** (C2, H4): `cani.subscription.demo-enabled` default false; FREE_TRIAL 1 lần/EO + không ghi đè gói paid.
3. **Sửa webhook theo spec PayOS (x-signature, HMAC-SHA512 raw body) + persist pending order ra DB** (C3, H2): bỏ fake-ack.

## Điểm sáng (worth keeping)

Anti-cross-tenant có thật (`confirmPayOSPaymentForUser` check `order.eoId() == eo.eoId()`, log cảnh báo IDOR); phân quyền declare/confirm rõ (EO declare / Worker confirm / ADMIN, `DirectPaymentController.java:35,51,68,88,103`); giá subscription dùng **BigDecimal đúng chuẩn** (không float); dev-token chỉ còn sống ở profile h2 (đã cleanup từ trước); mobile payment không dính demo-fallback.
