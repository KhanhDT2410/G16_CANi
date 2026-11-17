# BẰNG CHỨNG RED-FIRST TEST CHO 2 LỖ HỔNG BẢO MẬT (UTC-SEC)

> **Mục tiêu:** Chứng minh lỗ hổng **BUG-A (Billing)** và **BUG-B (Auth)** thực sự tồn tại trong mã nguồn hiện tại của CANi trước khi áp dụng bản vá production.
> **Thời điểm chạy:** 2026-11-13 01:13:07
> **Lệnh thực thi:** `./mvnw.cmd test "-Dtest=SubscriptionBillingSecurityTest,AuthWorkerTransitionTest"`

---

## 1. TỔNG HỢP KẾT QUẢ THỰC THI (HEAD HIỆN TẠI - TRƯỚC KHI VÁ)

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.cani.service.auth.AuthWorkerTransitionTest
[ERROR] Tests run: 4, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 1.114 s <<< FAILURE! -- in com.cani.service.auth.AuthWorkerTransitionTest
[INFO] Running com.cani.service.eo.SubscriptionBillingSecurityTest
[ERROR] Tests run: 8, Failures: 3, Errors: 0, Skipped: 0, Time elapsed: 0.173 s <<< FAILURE! -- in com.cani.service.eo.SubscriptionBillingSecurityTest

[INFO] Results:
[ERROR] Failures: 
[ERROR]   AuthWorkerTransitionTest.googleLogin_WorkerHasPaymentNoCccd_TargetEO_SucceedsAndKeepsWorkerRow:114 
workerRepository.delete(
    <any com.cani.entity.Worker>
);
Never wanted here:
-> at com.cani.service.auth.AuthWorkerTransitionTest.googleLogin_WorkerHasPaymentNoCccd_TargetEO_SucceedsAndKeepsWorkerRow(AuthWorkerTransitionTest.java:114)
But invoked here:
-> at com.cani.service.AuthService.lambda$12(AuthService.java:286) with arguments: [com.cani.entity.Worker@75a0c890]

[ERROR]   AuthWorkerTransitionTest.googleLogin_WorkerHasPayment_TargetEO_RoleChangeIsPersisted:253 
workerRepository.delete(
    <any com.cani.entity.Worker>
);
Never wanted here:
-> at com.cani.service.auth.AuthWorkerTransitionTest.googleLogin_WorkerHasPayment_TargetEO_RoleChangeIsPersisted(AuthWorkerTransitionTest.java:253)
But invoked here:
-> at com.cani.service.AuthService.lambda$12(AuthService.java:286) with arguments: [com.cani.entity.Worker@53abfc07]

[ERROR]   SubscriptionBillingSecurityTest.confirmPayOSPaymentForUser_DoubleConfirm_SameOrder_ActivatesOnce:199 
expected: ENTERPRISE
 but was: MATCHING_PLUS

[ERROR]   SubscriptionBillingSecurityTest.confirmPayOSPaymentForUser_OrderNotInQueue_GatewayPaid_RejectsAndActivatesNothing:113 
Expecting actual throwable to be an instance of:
  com.cani.exception.BadRequestException
but was:
  java.lang.NullPointerException: Cannot invoke "com.cani.entity.Subscription.getMaxEvents()" because "s" is null
	at com.cani.service.SubscriptionService.mapToDto(SubscriptionService.java:436)
	at com.cani.service.SubscriptionService.activateSubscription(SubscriptionService.java:420)
	at com.cani.service.SubscriptionService.confirmPayOSPaymentForUser(SubscriptionService.java:296)

[ERROR]   SubscriptionBillingSecurityTest.confirmPayOSPaymentForUser_UnknownOrder_EoAlreadyHasActivePlan_DoesNotGrantUpgrade:213 
Expecting actual throwable to be an instance of:
  com.cani.exception.BadRequestException
but was:
  java.lang.NullPointerException: Cannot invoke "com.cani.entity.Subscription.getMaxEvents()" because "s" is null
	at com.cani.service.SubscriptionService.mapToDto(SubscriptionService.java:436)
	at com.cani.service.SubscriptionService.activateSubscription(SubscriptionService.java:420)
	at com.cani.service.SubscriptionService.confirmPayOSPaymentForUser(SubscriptionService.java:296)

[INFO] 
[ERROR] Tests run: 12, Failures: 5, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

---

## 2. PHÂN TÍCH CHI TIẾT NGUYÊN NHÂN LỖI (ROOT CAUSE)

### 2.1 BUG-A (Billing: Lỗ hổng cướp gói & default MATCHING_PLUS)
* **Kịch bản:** Khi một orderCode không tồn tại trong hàng đợi in-memory (`pendingOrders` rỗng do restart server hoặc orderCode lạ từ attacker), nếu PayOS Gateway trả về trạng thái `PAID`:
  * Mã nguồn hiện tại tại dòng 294: `PlanType planType = order != null ? order.planType() : PlanType.MATCHING_PLUS;`
  * Dẫn đến việc **tự động cấp gói `MATCHING_PLUS` cho bất kỳ ai gọi API**, đồng thời bỏ qua hoàn toàn bước kiểm tra quyền sở hữu `order.eoId`.
  * Khi gọi xác nhận 2 lần (double confirm), lần thứ 2 order đã bị xóa khỏi `pendingOrders`, hệ thống lại tự động kích hoạt đè gói `MATCHING_PLUS` thay vì giữ nguyên gói `ENTERPRISE` đã mua.
* **Chứng minh:** `confirmPayOSPaymentForUser_OrderNotInQueue_GatewayPaid_RejectsAndActivatesNothing` và `confirmPayOSPaymentForUser_DoubleConfirm_SameOrder_ActivatesOnce` FAIL đỏ 100%.

### 2.2 BUG-B (Auth: Xóa nhầm Worker đã có dữ liệu tài chính gây vỡ Transaction)
* **Kịch bản:** Khi tài khoản Worker (chưa cập nhật CCCD hoặc 0 ca hoàn thành) nhưng **đã có bản ghi Payment** đăng nhập qua Google OAuth2 với `targetRole=EVENT_ORGANIZER`:
  * Mã nguồn hiện tại tại dòng 286: `workerRepository.delete(w)` được gọi vô điều kiện.
  * Vì `payments.worker_id` không có ràng buộc `ON DELETE CASCADE`, thao tác này sẽ kích hoạt ngoại lệ Foreign Key Violation ở tầng Database, khiến Spring Transaction bị đánh dấu `rollback-only`.
  * Khối `try/catch` nuốt exception không thể cứu được transaction, dẫn đến `UnexpectedRollbackException` (HTTP 500) khi commit.
* **Chứng minh:** `googleLogin_WorkerHasPaymentNoCccd_TargetEO_SucceedsAndKeepsWorkerRow` bắt được việc `workerRepository.delete(w)` bị gọi trái phép.

