# QUY TẮC XỬ LÝ DISPUTE (CC/ADMIN/AI ASSISTANT)

> Nguồn: bảng "DANH SÁCH VI PHẠM & HÌNH THỨC XỬ LÝ" (xlsx) + code hiện tại
> (`DisputeService`, `NoShowService`, BR-52/53/54/55/56/60/61/68).
> Dùng để CC ra quyết định nhất quán khi xử lý tranh chấp.

## 1. Quy tắc nền (luôn đúng, có trong code)

| # | Quy tắc | Nguồn |
|---|---------|-------|
| R1 | Cửa sổ nộp dispute: ≤ 14 ngày sau khi ca kết thúc (BR-52) | `enforceDisputeWindow` |
| R2 | Không có 2 hồ sơ OPEN/INVESTIGATING cùng 1 ca | `createDispute` |
| R3 | SLA quyết định CC: 7 ngày (BR-53) | `slaDeadline` |
| R4 | Kháng nghị lên Admin: 7 ngày sau quyết định CC (BR-54) | `escalateDispute` |
| R5 | Kết quả UNVERIFIED → không phạt bất kỳ bên nào, đóng vụ, lưu hồ sơ | `DisputeOutcome.UNVERIFIED` |
| R6 | WORKER_FAULT → tự động −10 RS (BR-55), chận trong [0,100] (BR-60/61) | `applyWorkerRsPenalty` |
| R7 | RS < 20 → khóa tài khoản (UC-95) | `NoShowService` |
| R8 | Payment: PAYMENT-dispute → DISPUTED; phán quyết WORKER_FAULT → CONFIRMED_FULL, PARTIAL → CONFIRMED_PARTIAL, EO_FAULT → giữ DISPUTED | `syncPaymentAfterDisputeDecision` |

## 2. Bảng ra quyết định theo tình huống

### 2.1. Worker vi phạm

| Tình huống | Bằng chứng cần đủ (nếu thiếu → UNVERIFIED) | Phán quyết + hành động |
|---|---|---|
| **No-show** (đi làm trễ >30', không check-in, không lý do bất khả kháng) | Log QR/GPS check-in; thời gian nhận ca; không có xác nhận EO | EO/Worker-fault → **NO_SHOW**; hệ thống (cron/EO/Admin) đã tự trừ −25 RS và cấm nhận ca 3 ngày. **CC KHÔNG trừ thêm −10 RS** → điền `penaltyWaiverReason` = "no-show đã xử lý bởi NoShowService (−25 RS đã áp)" để tránh phạt kép. |
| **Bỏ ca giữa chừng <50% thời lượng** | Log check-in/check-out; xác nhận EO | WORKER_FAULT, RS penalty −25, cấm nhận ca 3 ngày (tương đương no-show). Lần 2 trong 60 ngày: thêm khóa 7 ngày (ghi `resolution`). |
| **Bỏ ca giữa chừng ≥50%** | Như trên | WORKER_FAULT −10 RS (BR-55, auto). |
| **Không hoàn thành nhiệm vụ** (có mặt nhưng không làm) | Xác nhận EO + log hoạt động; 1 bên nói thôi → UNVERIFIED | Lần 1: cảnh cáo + −10 RS. Lần 2 (60 ngày): −15 RS + giới hạn 1 ca/tuần 14 ngày. Lần 3 (90 ngày): khóa 14 ngày. |
| **Gian lận chấm công** (quét QR hộ, ảnh QR check-in từ xa, mạo danh eKYC) | Log GPS check-in xa vị trí, trùng thiết bị, so khớp eKYC | Lần 1: hủy công ca + −25 RS + khóa 14 ngày. Mạo danh eKYC: **khóa vĩnh viễn + cấm CCCD** (đề xuất, cần Admin). |
| **Hỏng/mất tài sản** | Biên bản 2 bên, ảnh | Sơ suất + hợp tác: không trừ RS, hướng dẫn đền bù dân sự ngoài sàn. Bất cẩn nặng/không hợp tác: −10 RS + khóa tới khi thỏa thuận xong. Cố ý/trộm: khóa vĩnh viễn, chuyển cơ quan chức năng. |

### 2.2. EO vi phạm

| Tình huống | Bằng chứng | Phán quyết + hành động |
|---|---|---|
| **Quỵt thù lao** (worker hoàn thành, EO không trả) | Log check-in/check-out hợp lệ; proof chuyển khoản của worker; xác nhận nhận ca | EO_FAULT. Thông báo tối hậu thư 48h. Chưa trả sau 48h: 1 Strike + tạm khóa EO, ẩn event/shift. Mở khóa khi có ủy nhiệm chi đủ + worker xác nhận. Trốn ≥7 ngày hoặc tái phạm: khóa vĩnh viễn + blacklist. |
| **Trả thiếu thù lao** | Mức lương đã cam kết (mô tả ca) vs proof thanh toán | Lần 1: cảnh cáo + yêu cầu bù đủ 48h. Không bù: dừng quyền tạo ca 7 ngày. Tái phạm (90 ngày): 1 Strike + khóa 14 ngày. |
| **Ép việc ngoài mô tả** | Mô tả ca gốc vs lời khai worker; 2 bên trái ngược → UNVERIFIED | EO_FAULT. Worker được rời ca **mà không bị phạt no-show/bỏ ca**. Lần 1: cảnh cáo + ghi hồ sơ. Lần 2 (60 ngày): Strike + dừng duyệt ca mới 7 ngày. Lần 3 (90 ngày): khóa 30 ngày. |
| **Xúc phạm/đe dọa/quấy rối** | Tin nhắn (dispute chat), chứng nhân, video | Xúc phạm lần 1: cảnh cáo + 1 Strike. Lần 2 (90 ngày): khóa 14 ngày. Đe dọa vũ lực/bạo lực/quấy rối: **khóa vĩnh viễn ngay**. |

### 2.3. Vi phạm chung cả 2 bên

| Tình huống | Bằng chứng | Phán quyết + hành động |
|---|---|---|
| **Dispute giả / vu khống** | Hồ sơ mâu thuẫn với log hệ thống, pattern khiếu nại trước đó | REJECTED: không chấp nhận nội dung đơn; phạt ngược bên khiếu nại: Worker −15 RS + khóa quyền gửi dispute 30 ngày; EO 1 Strike + khóa quyền 30 ngày. Tái phạm (90 ngày): khóa TK 14 ngày. |
| **Làm giả bằng chứng** (cắt ghép, giả bill) | Phát hiện tại thời điểm đối chiếu (mã bill ngân hàng, EXIF, log) | Khóa vĩnh viễn **ngay lập tức**, đưa Blacklist. Không cần qua vòng kháng nghị (trừ khi Admin phúc tra). |
| **Không đủ bằng chứng** | Hai bên khai mâu thuẫn, hệ thống không có log QR/GPS, không có chứng cứ vật lý | **UNVERIFIED** (R5): đóng vụ, không chế tài, lưu hồ sơ. Cho phép mở lại trong 7 ngày nếu có chứng cứ hợp lệ mới. |

## 3. Nguyên tắc chọn outcome (DisputeOutcome)

1. Bằng chứng hệ thống (log QR, GPS, payment, eKYC) > bằng chứng vật lý 2 bên > lời khai 1 bên.
2. Chỉ 1 bên có bằng chứng, bên kia không phản hồi trong thời hạn CC → nghiêng về bên có bằng chứng (ghi rõ trong rationale).
3. Bằng chứng 2 bên đối nghịch nhau, không thể xác minh → `UNVERIFIED` (không phạt ai).
4. Worker lỗi + no-show đã tự xử lý (R7) → luôn đi kèm `penaltyWaiverReason`, không trừ thêm −10.
5. Mọi hành động ngoài hệ thống tự động (khóa 7/14/30 ngày, strike, blacklist, cấm CCCD) hiện là **thủ công do Admin** → CC ghi chính xác lệnh cần Admin thực thi vào `resolution`/`decisionRationale` theo mẫu: `CHẾ TÀI CẦN ADMIN: <hành động> | thời hạn: <ngày> | căn cứ: <dòng trong bảng xlsx>`.

## 4. Điểm code chưa có (đừng giả định hệ thống tự làm)

- Không có bộ đếm Strike / tái phạm 60–90 ngày → CC tự đếm từ `ReliabilityLog` + lịch sử dispute (kèm link evidence).
- Không có cơ chế khóa tạm thời theo ngày (chỉ có `UserStatus.LOCKED` vĩnh viễn + threshold RS<20) → mọi "khóa 7/14/30 ngày" là Admin thủ công.
- Không có "khóa quyền gửi dispute 30 ngày" cho bên vu khống.
- Không có endpoint "mở lại vụ UNVERIFIED trong 7 ngày" → bên liên quan nộp dispute mới cùng ca (nếu còn trong BR-52) hoặc tạo support ticket.
- Không có bảng Blacklist / cấm CCCD → Admin tạo mới khi cần.
- No-show tự động chỉ chạy theo cron + báo cáo thủ công (EO/CC/Admin); cấm nhận ca 3/7 ngày chưa được thực thi trong matching.

## 5. Phân loại khả thi theo code (cập nhật 2026-11-24)

- **Đã có sẵn (code tự động):** BR-52 (cửa sổ 14 ngày), BR-53 (SLA 7 ngày), BR-54 (kháng nghị 7 ngày), UNVERIFIED không phạt ai (R5), WORKER_FAULT → −10 RS auto (R6), no-show auto −25 RS + khóa TK khi RS<20, payment sync (R8), cảnh cáo/AuditLog/notification.
- **CC/ADMIN xử lý thủ công (khả thi ngay, không cần dev):** mọi "khóa 7/14/30 ngày" (Admin `PATCH /admin/users/{id}/status` → SUSPENDED/LOCKED; code KHÔNG có auto-expire → Admin tự mở khóa sau N ngày, ghi ngày hết hạn vào AuditLog), Strike + Blacklist + cấm CCCD (ghi AuditLog, chưa có bảng riêng), biên bản đền bù dân sự (ngoài sàn), mở lại vụ UNVERIFIED = nộp dispute mới cùng ca (rule chống trùng chỉ chặn OPEN/INVESTIGATING) nhưng bị ràng BR-52 (≤14 ngày sau ca kết).
- **Không thể / cần dev trước:** cấm nhận ca 3/7 ngày, giới hạn 1 ca/tuần 14 ngày, dừng quyền tạo ca mới 7 ngày (không có cờ trong matching), hạ độ ưu tiên hiển thị sự kiện, "khóa quyền gửi Dispute 30 ngày", trừ RS mức tùy ý (−15; −25 cho non-no-show — code chỉ có −10/−25 no-show/+2,−1,−2 review), bảng Strike/Blacklist, mở khóa EO tự động khi có ủy nhiệm chi + xác nhận worker.
- **Quy tắc bắt buộc khi CC phán quyết no-show đã bị hệ thống trừ −25 RS:** điền `penaltyWaiverReason` (tránh phạt kép −10 + −25 cùng sự việc).

Phiên bản xlsx kèm phân loại: `docs/BẢNG DANH SÁCH VI PHẠM VÀ HÌNH THỨC XỬ LÝ KHI GIẢI QUYẾT DISPUTE - PHÂN TÍCH KHẢ THI.xlsx` (cột F "Phân loại triển khai" + G "Ghi chú khả thi theo code thực tế").

## 6. Chốt lại phạm vi chế tài (2026-11-24, theo yêu cầu user)

Bỏ qua: cờ trong matching (cấm nhận ca, giới hạn ca/tuần, dừng tạo ca, hạ độ ưu tiên). Điểm RS / khóa / ban / strike / blacklist: **đều code được** — xem `DISPUTE_SANCTIONS_PLAN.md` (5 bước: adjust RS admin, lockUntil + auto-unlock, lịch sử vi phạm, EoStrike, Blacklist/CCCD).

Sửa sheet: "khóa quyền gửi Dispute 30 ngày" → tạm khóa TK 30 ngày; "mở lại UNVERIFIED 7 ngày" → nộp đơn mới cùng ca (ràng BR-52); "mở khóa EO khi ủy nhiệm chi + xác nhận" → Admin thủ công. File: `BẢNG DANH SÁCH VI PHẠM VÀ HÌNH THỨC XỬ LÝ KHI GIẢI QUYẾT DISPUTE - BẢN SỬA THEO CODE.xlsx` (thay cột F/G của bản trước, giữ nguyên 5 cột gốc).
