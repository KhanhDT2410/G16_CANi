# CANi Kỹ Thuật — Giám Sát & Cảnh Báo Quá Hạn SLA (SLA Monitoring Job)

> **Mục tiêu:** Tài liệu đặc tả hệ thống giám sát thời gian phản hồi (SLA Monitoring & Alerting) cho Phiếu hỗ trợ (Support Tickets) và Tranh chấp (Disputes).  
> **Người phụ trách Backend:** QuyTH  
> **Phiên bản:** 1.0.0 (Spring Boot 3.5.1 / Java 21)  
> **Tác vụ nền:** `@Scheduled(cron = "${cani.sla-scan.cron:0 */15 * * * *}")`  
> **Cơ chế Idempotent:** Bảng kiểm soát độc lập `sla_alert_logs`.

---

## 1. Mục Đích & Nguyên Tắc Hoạt Động

Để đảm bảo chất lượng dịch vụ (Service Level Agreement - SLA), nền tảng CANi triển khai tác vụ kiểm soát ngầm định kỳ:
1. **Kiểm soát Ticket hỗ trợ quá hạn:**
   - Điều kiện: Phiếu hỗ trợ có trạng thái `OPEN` hoặc `IN_PROGRESS` và `sla_deadline < now`.
   - Đối tượng nhận cảnh báo: Nhân sự Customer Care được phân công (`assigned_to`) hoặc thông báo broadcast toàn bộ đội ngũ `CUSTOMER_CARE` nếu chưa có người tiếp nhận.
2. **Kiểm soát Tranh chấp cấp Customer Care:**
   - Điều kiện: Tranh chấp có trạng thái `OPEN` hoặc `INVESTIGATING` và `sla_deadline < now`.
   - Đối tượng nhận cảnh báo: Chuyên viên CC phụ trách hoặc nhóm `CUSTOMER_CARE`.
3. **Kiểm soát Tranh chấp khiếu nại cấp Admin (Escalated):**
   - Điều kiện: Tranh chấp có trạng thái `ESCALATED` và quá hạn 7 ngày làm việc hoặc `sla_deadline < now`.
   - Đối tượng nhận cảnh báo: Toàn bộ Quản trị viên `ADMIN` để đưa ra phán quyết ràng buộc cuối cùng.
4. **Nguyên tắc không can thiệp nghiệp vụ (Non-destructive):**
   - Job chạy ngầm **tuyệt đối KHÔNG tự ý đóng (close/resolve)** ticket hoặc dispute. Việc đóng/giải quyết thuộc thẩm quyền quyết định của con người.
5. **Cơ chế chống spam & Đảm bảo Idempotent 100%:**
   - Sử dụng bảng kiểm soát `sla_alert_logs` với ràng buộc `UNIQUE (entity_type, entity_id, breach_stage)`.
   - Khi chạy định kỳ mỗi 15 phút, các mục vi phạm đã được cảnh báo trong cùng giai đoạn sẽ được bỏ qua, không gây nghẽn thông báo hay spam chuông người dùng.

---

## 2. Cấu Hình Ứng Dụng (Application Properties)

Hệ thống cho phép cấu hình linh hoạt thông qua biến môi trường hoặc file `application.yml`:

```yaml
cani:
  sla-scan:
    # Bật/tắt tác vụ quét nền SLA (mặc định: true; đặt false trong môi trường test/dev in-memory)
    enabled: ${SLA_SCAN_ENABLED:true}
    # Tần suất thực thi cron (mặc định: mỗi 15 phút)
    cron: ${SLA_SCAN_CRON:0 */15 * * * *}
```

*Trong môi trường kiểm thử (Integration / Unit Tests):*
- Cấu hình `cani.sla-scan.enabled=false` được áp dụng trong `application-h2.yml` để ngăn chặn tác vụ nền chạy ngoài ý muốn.
- Các bài kiểm thử nghiệp vụ gọi trực tiếp phương thức `slaMonitoringService.scanAndAlertOverdueSla()` để kiểm thử hành vi tức thời mà không cần chờ cron.

---

## 3. Nhật Ký Kiểm Toán (Audit Trail) & Thông Báo (Notifications)

Mỗi lần phát hiện vi phạm SLA mới, hệ thống tự động:
1. **Ghi nhận AuditLog độc lập (REQUIRES_NEW):**
   - `SLA_BREACH_TICKET`: Cảnh báo quá hạn SLA phiếu hỗ trợ.
   - `SLA_BREACH_DISPUTE_CC`: Cảnh báo quá hạn SLA tranh chấp (Customer Care).
   - `SLA_BREACH_DISPUTE_ESCALATED`: Cảnh báo quá hạn SLA tranh chấp khiếu nại (Admin).
2. **Gửi Notification qua NotificationService:**
   - Type: `SUPPORT_TICKET` hoặc `DISPUTE`.
   - Nội dung tiếng Việt nêu rõ mã tham chiếu, tiêu đề và hạn chót theo SLA.

