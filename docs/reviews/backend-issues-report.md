# Báo cáo vấn đề phía Backend — từ góc nhìn Frontend Mobile
> Người lập: Frontend Mobile · Ngày: 2026-10-28 · Nguồn: verify trực tiếp trên source (file:line kèm)
> Cách đọc: mỗi mục = Vấn đề → Bằng chứng → Tác động → Giải quyết (ai sửa).
> Ưu tiên: P0 = đang làm mobile gọi API lỗi · P1 = an ninh · P2 = tính năng thiếu · P3 = perf/robust.

## P0 — Lỗi hợp đồng API đang ảnh hưởng trực tiếp mobile

### 1. Mobile `checkin`/`checkout` thiếu prefix `/v1` → 404
- **Vấn đề:** Mobile gọi `POST /workers/assignments/{id}/checkin` (không `v1`), backend mapping `@RequestMapping("/api/v1/workers")` → URL thực `http://localhost:8080/api/workers/...` → 404.
- **Bằng chứng:** `mobile/lib/features/job_proposals/data/proposal_repository.dart:158,187` (so các endpoint đúng cùng file `:27,76,88,102`); backend `WorkerJobController.java:24`.
- **Tác động:** Điểm danh QR không chạy với backend thật (UI không báo lỗi do fallback mục 8).
- **Giải quyết:** Mobile (tôi sửa): thêm `/v1`. Backend: xác nhận contract chuẩn nếu prefix cũ là chủ ý.

### 2. `confirmPayment`: mobile gửi `CONFIRMED_FULL`, backend chỉ nhận `RECEIVED_FULL` → 400
- **Vấn đề:** Worker bấm "Đã nhận đủ lương" → POST `confirmStatus: "CONFIRMED_FULL"`; backend danh sách cứng `RECEIVED_FULL/RECEIVED_PARTIAL/NOT_RECEIVED`, khác → `IllegalArgumentException`.
- **Bằng chứng:** `backend .../service/DirectPaymentService.java:115-123`; test `DirectPaymentServiceTest.java:137,158`. Mobile: `shift_assignment_card.dart:249`, `my_assignments_screen.dart:110-112`, `job_proposals_screen.dart:107`.
- **Tác động:** Confirm lương thất bại 100% trên backend thật; UI vẫn "thành công" (fallback mục 8) → dữ liệu sai.
- **Giải quyết:** Backend: accept alias `CONFIRMED_FULL` → map `RECEIVED_FULL` (1 dòng) HOẶC công bố enum chuẩn trong spec. Mobile: đổi hằng theo spec.

### 8. Mobile fallback `return true` / demo data che mọi lỗi backend
- **Vấn đề:** `acceptProposal/rejectProposal/confirmPayment` catch → `return true`; `fetchProposals/fetchAssignments` trả demo khi lỗi → các bug P0 trên im lặng.
- **Bằng chứng:** `proposal_repository.dart:79,94,142` (catch→true), `:35-68,110-127` (demo fallback).
- **Giải quyết:** Mobile (sau khi backend ổn): bỏ fallback, propagate error. Backend: lỗi trả về envelope `ApiResponse` + mã rõ để UI hiện nguyên nhân.

## P1 — An ninh

### 3. `resolveUserId(?userId=)` impersonate bất kỳ user
- **Vấn đề:** Tham số `userId` optional dùng thẳng, bỏ qua JWT principal → user A hành động thay user B.
- **Bằng chứng:** `DirectPaymentController.java:102-114` (`if (explicitUserId != null) return explicitUserId;`); cùng pattern `WorkerJobController.java:37`, `EoEventController` (nhiều endpoint).
- **Giải quyết (backend):** Xóa `userId` param ở endpoint worker/eo; lấy userId từ JWT. Nếu giữ cho ADMIN: `hasRole('ADMIN')` + audit log.

### 4. `/api/v1/notifications/**` + `/api/v1/devices/**` là `permitAll`
- **Vấn đề:** Route anonymous + `resolveUserId` (mục 3) → chưa đăng nhập vẫn đọc notification, đăng ký/hủy device token của user khác.
- **Bằng chứng:** `SecurityConfig.java:50-51`; `NotificationController` + `DeviceTokenController` (resolveUserId fallback); `DELETE /devices/tokens?fcmToken=` xóa token user khác.
- **Giải quyết (backend):** notifications → `authenticated()` + userId từ JWT; devices → validate ownership trước unregister.

### 11. 2 endpoint đọc payment thiếu `@PreAuthorize`
- **Bằng chứng:** `DirectPaymentController.java:71-75` (`/shifts/{shiftId}`), `:96-100` (`/assignments/{assignmentId}`) — không annotation, trong khi `:80-81` có.
- **Giải quyết (backend):** `@PreAuthorize` theo role + ownership check.

## P2 — Backend chưa hoàn chỉnh (mobile đang chờ)

### 5. FCM pipeline chết — `createAndSend` không có caller
- **Bằng chứng:** grep `createAndSend` trong `backend/src/main` → chỉ 1 match (định nghĩa `NotificationService.java:31`).
- **Giải quyết (backend):** gọi từ business events: tạo `DispatchProposal`, `publishEvent`, `declarePayment`, eKYC review.

### 10. Check-in QR: backend chỉ LƯU, không KIỂM TRA
- **Vấn đề:** GPS không so với venue (geo-fence chưa có); QR hash không đối chiếu QR kỳ vọng; `AttendanceLog.qrGeneratedAt/qrExpiresAt` không ai set.
- **Bằng chứng:** `WorkerJobService.java:318-320`.
- **Giải quyết (backend):** geo-check tolerance 50–100m; QR dynamic do backend ký (TTL) + verify.

### 12. `/verify-otp` + `/reset-password` mồ côi, semantics sai
- **Vấn đề:** không UI nào gọi; `/verify-otp` forward OTP 6-số sang GoTrue `/auth/v1/verify` (đòi token khác loại) → endpoint không bao giờ verify được OTP của `/send-otp`.
- **Giải quyết (backend):** (1) verify theo bảng `email_otp_verifications` cục bộ + thêm forgot-password qua OTP, HOẶC (2) giữ GoTrue, đổi tên rõ + mobile xây UI sau.

## P3 — Performance & Robustness

### 6. Matching bỏ qua ràng buộc shift
- **Bằng chứng:** `MatchingDispatchService` hằng `MAX_DISTANCE_KM = 15.0`; `passHardFilter` không check `shift.maxDistanceKm/requiredSkills/minExperienceLevel` (`Shift.java:88-102`).
- **Giải quyết (backend):** đọc thuộc tính shift vào filter.

### 7. `workerRepository.findAll()` per shift
- **Bằng chứng:** `MatchingDispatchService.java:87`, gọi từ `EoEventService.publishEvent` per-shift → O(N), giữ transaction lâu.
- **Giải quyết (backend):** query có điều kiện + limit; cân nhắc dispatch async.

### 13. `createInitialPayment` trả null → NPE risk
- **Bằng chứng:** `DirectPaymentService.java:47-49` (null), `:85` (dùng trực tiếp).
- **Giải quyết (backend):** `Optional` + `orElseThrow(BadRequest)`.

### 14. Cooldown OTP: UI 30s vs backend 60s
- **Bằng chứng:** `register_screen.dart` (`_countdown = 30`); `EmailOtpService` (`COOLDOWN_SECONDS = 60`) → bấm phút 30–60 ăn 400.
- **Giải quyết:** UI mobile lên 60s (tôi sửa) hoặc backend trả cooldown trong response.

## Ghi chú 2 hướng (mobile tự xử lý — M)
| # | Việc | File mobile |
|---|---|---|
| M1 | Thêm `/v1` cho checkin/checkout (mục 1) | `proposal_repository.dart:158,187` |
| M2 | Đổi hằng `CONFIRMED_FULL` theo spec backend (mục 2) | `shift_assignment_card.dart:249` + 2 screens |
| M3 | Bỏ demo fallback, propagate error (mục 8) | `proposal_repository.dart` |
| M4 | Hardcode `http://localhost:8080/api` trong refresh — break device thật | `auth_interceptor.dart:75` |
| M5 | Radar "Ứng tuyển nhanh" stub SnackBar, không gọi API | `job_radar_screen.dart:528-535` |

## Tóm tắt theo owner
- **Backend bắt buộc (P0/P1):** 1 (chốt contract), 2 (alias/enum), 3, 4, 11.
- **Backend roadmap (P2):** 5, 10, 12.
- **Backend kỹ thuật (P3):** 6, 7, 13, 14.
- **Mobile (tôi):** M1–M5.
