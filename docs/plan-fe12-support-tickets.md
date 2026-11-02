# PLAN: FE-12 Support Tickets (UC-105 / UC-106 · SCR-25 / SCR-26)

> **Người phụ trách Backend:** QuyTH
> **Ngày lập:** 31/10/2026
> **Trạng thái:** Sẵn sàng code — các quyết định mặc định đã chốt (mục 7)
> **Phụ thuộc:** Table `notifications` + `NotificationService`/FCM (nếu có trong nhánh merge), `AuditLogService` (BR-70)
> **Migration kế tiếp:** `V13__support_tickets.sql` (V12 đã dùng cho dispute)

---

## 1. Tóm Tắt Nghiệp Vụ (SRS)

- **UC-105 — Nộp ticket (Worker / EO):** chọn category (`ACCOUNT`, `BILLING`, `TECHNICAL`, `GENERAL`, `OTHER`), subject + description (min **30 ký tự** — BR/MSG151), kèm screenshot tùy chọn (URL placeholder convention `https://storage.cani.vn/...` như eKYC/dispute, chờ module UC-119). Hệ thống tạo reference, tự gán **priority + SLA deadline**, thông báo đội CC.
- **BR-58 — SLA theo mức độ:** `HIGH` (payment, safety, account lock) → phản hồi trong **4h** · `MEDIUM` (verification, dispute-adjacent) → **1 ngày** · `LOW` (general queries) → **3 ngày**. Vượt SLA → cờ `slaAtRisk` trong queue (auto-escalation về Team Lead: flag + audit log trong MVP, không cần job scheduler).
- **UC-106 — Theo dõi (User):** list ticket của mình (reference, subject, status, last update), xem thread (submission + replies CC + timeline), reply khi `OPEN`/`IN_PROGRESS`, "mark resolved" khi hài lòng.
- **SCR-25 — CC Queue:** ticket mở (OPEN + IN_PROGRESS) xếp theo **rủi ro SLA → last message cũ nhất**, filter status/category, self-assign hoặc transfer (OPEN → IN_PROGRESS khi CC nhận).
- **SCR-26 — CC Ticket Detail:** thread đầy đủ, reply công khai + **internal note** (chỉ CC/Admin thấy), chuyển status `OPEN → IN_PROGRESS → CLOSED`, closed_at + resolved_by.
- **BR-70:** mọi thay đổi trạng thái → `audit_logs` (REQUIRES_NEW qua `AuditLogService`).
- **Thông báo:** 2 đầu (user ↔ CC) qua bảng `notifications`, type mới `SUPPORT_TICKET`.

---

## 2. Trạng Thái & Enums (mới)

| Enum | Giá trị | Ghi chú |
|---|---|---|
| `TicketStatus` | `OPEN`, `IN_PROGRESS`, `CLOSED` | đúng SCR-26 |
| `TicketCategory` | `ACCOUNT`, `BILLING`, `TECHNICAL`, `GENERAL`, `OTHER` | UC-105 |
| `TicketPriority` | `HIGH`, `MEDIUM`, `LOW` | BR-58 — do hệ thống gán, không do user chọn |
| `NotificationType` | + `SUPPORT_TICKET` | ALTER TYPE trong V13 |

**Mapping category → priority (hệ thống, không cho user chọn):**
`BILLING → HIGH` · `ACCOUNT → HIGH` (giả định bao gồm khóa tài khoản) · `TECHNICAL → MEDIUM` · `GENERAL → MEDIUM` · `OTHER → LOW`.
Deadline: `slaDeadline = now + (HIGH: 4h | MEDIUM: 1d | LOW: 3d)`.

---

## 3. DDL — `V13__support_tickets.sql`

Chỉ chạy PostgreSQL/Supabase (profile H2 dùng `ddl-auto` từ entity, Flyway tắt).

```sql
CREATE TYPE ticket_status AS ENUM ('OPEN', 'IN_PROGRESS', 'CLOSED');
CREATE TYPE ticket_category AS ENUM ('ACCOUNT', 'BILLING', 'TECHNICAL', 'GENERAL', 'OTHER');
CREATE TYPE ticket_priority AS ENUM ('HIGH', 'MEDIUM', 'LOW');
ALTER TYPE notif_type ADD VALUE 'SUPPORT_TICKET';

CREATE TABLE support_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_no VARCHAR(20) NOT NULL UNIQUE,          -- VD: TKT-2026-0001
    user_id UUID NOT NULL REFERENCES users(id),        -- người nộp
    category ticket_category NOT NULL,
    priority ticket_priority NOT NULL,
    subject VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,                          -- min 30 ký tự (validate phía DTO)
    attachments JSONB,                                  -- ["url1", "url2"] (chờ UC-119)
    status ticket_status NOT NULL DEFAULT 'OPEN',
    assigned_to UUID REFERENCES users(id),              -- CC phụ trách
    sla_deadline TIMESTAMPTZ NOT NULL,
    resolved_by UUID REFERENCES users(id),
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_st_tickets_user ON support_tickets(user_id);
CREATE INDEX idx_st_tickets_status ON support_tickets(status);
CREATE INDEX idx_st_tickets_assigned ON support_tickets(assigned_to);
CREATE INDEX idx_st_tickets_sla ON support_tickets(sla_deadline);
CREATE TRIGGER trg_st_tickets_updated_at BEFORE UPDATE ON support_tickets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE support_ticket_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES support_tickets(id),
    sender_id UUID NOT NULL REFERENCES users(id),
    body TEXT NOT NULL,
    is_internal BOOLEAN NOT NULL DEFAULT FALSE,          -- internal note CC (SCR-26)
    attachment_url VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_st_messages_ticket ON support_ticket_messages(ticket_id);

-- RLS deny-all dự phòng (đồng bộ convention V6)
ALTER TABLE support_tickets ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON support_tickets;
DROP POLICY IF EXISTS "deny_authenticated" ON support_tickets;
CREATE POLICY "deny_anon" ON support_tickets FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON support_tickets FOR ALL TO authenticated USING (false);
-- (tương tự support_ticket_messages)

GRANT ALL ON support_tickets, support_ticket_messages TO postgres;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON support_tickets, support_ticket_messages TO service_role;
    END IF;
END $$;
```

> `reference_no`: sinh phía service bằng pattern `TKT-yyyyMMdd-XXXX` (counter từ count trong ngày hoặc suffix UUID ngắn — quyết định khi code, giữ UNIQUE constraint).

---

## 4. Files TẠO (13)

### Entity + Enums (5)
- `entity/enums/TicketStatus.java`
- `entity/enums/TicketCategory.java`
- `entity/enums/TicketPriority.java`
- `entity/SupportTicket.java` — `@JdbcTypeCode(NAMED_ENUM)` cho 3 enum, `attachments` JSONB `SqlTypes.JSON`, builder default `status=OPEN`
- `entity/SupportTicketMessage.java` — `isInternal` default false

### Repository (2)
- `SupportTicketRepository`:
  - `List<SupportTicket> findByUserIdOrderByCreatedAtDesc(UUID userId)` (user = user_id)
  - `List<SupportTicket> findByStatusInOrderBySlaDeadlineAsc(List<TicketStatus>)` (queue)
  - `long countByStatus(TicketStatus)` (dùng cho dashboard sau này)
- `SupportTicketMessageRepository`:
  - `List<SupportTicketMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId)`
  - `long countByTicketId(UUID)`

### DTO (5) — `dto/support/`
- `CreateTicketRequestDto` — `@NotNull category`, `@NotBlank @Size(max=200) subject`, `@NotBlank @Min(30) @Max(3000) description`, `List<String> attachments` (tùy chọn)
- `ReplyTicketRequestDto` — `@NotBlank @Min(5) body`, `Boolean isInternal` (service chặn: user thường gửi true → 400)
- `SupportTicketResponseDto` — đầy đủ + `List<MessageDto>` thread (user view: lọc `!isInternal`; CC view: đủ) + cờ `slaAtRisk` (deadline < now + 0h) + `canReply` (status != CLOSED)
- `SupportTicketQueueItemDto` — id, referenceNo, category, priority, subject, status, userId, assignedTo, slaDeadline, lastMessageAt, slaAtRisk
- `AssignTicketRequestDto` — `UUID targetCcUserId` (null = CC tự nhận)

> Naming `lastMessageAt`: không có cột, tính từ `createdAt` message mới nhất (1 query theo ticket trong queue — queue giới hạn TOP 50 để tránh N+1; chấp nhận trade-off, ghi chú trong docs).

### Service (1)
`service/SupportTicketService.java` — convention UC-87/Dispute (`@Transactional(readOnly=true)` class-level, `@Transactional` method ghi, `ApiResponse` message tiếng Việt, audit + notification):

| Method | Hành vi |
|---|---|
| `createTicket(userId, req)` | map priority theo category; `reference_no`; `slaDeadline`; save; notify đội CC (`UserRepository.findByRole(CUSTOMER_CARE)`); audit `SUBMIT_TICKET`; response đầy đủ |
| `getMyTickets(userId)` | list ticket của user (desc) |
| `getTicketDetail(ticketId, requesterId)` | user nộp / CC / Admin được xem; user khác → 400 (MSG34). User view thread không chứa internal note |
| `reply(ticketId, userId, req)` | ticket phải `OPEN`/`IN_PROGRESS` (CLOSED → 400); user phải là người nộp (CC/Admin được tất cả); `isInternal=true` chỉ chấp nhận khi role CC/ADMIN; notify phía bên kia; audit `REPLY_TICKET` |
| `markResolved(userId, ticketId)` | user nộp tự mark → status `CLOSED`, `resolvedBy=userId`, `closedAt` (ticket do user chốt; CC vẫn có thể close) |
| `closeTicket(ccId, ticketId, req)` | CC/ADMIN: lý do close (`@NotBlank rationale` — re-use DTO nhỏ hoặc dùng `ReplyTicketRequestDto` + cờ close; **quyết định: dùng endpoint `/close` body `{rationale}` min 30 ký tự**) → `CLOSED`, `resolvedBy=ccId` |
| `assignTicket(actorId, ticketId, target)` | đúng mẫu `assignDispute`: OPEN→IN_PROGRESS; target phải role CC/ADMIN; notify người nhận |
| `getQueue()` | `OPEN + IN_PROGRESS`, sort: (1) `slaAtRisk` (deadline trong <24h) trước, (2) cũ nhất trước (theo `createdAt`) |

**Notification (2 đầu):** title/body tiếng Việt, `type=SUPPORT_TICKET`, `referenceType="SUPPORT_TICKET"`, `referenceId=ticketId`.

### Controllers (2)
| Controller | URL | Endpoints | Bảo mật |
|---|---|---|---|
| `SupportTicketController` | `/api/v1/support/tickets` | `POST /` · `GET /my` · `GET /{id}` · `POST /{id}/reply` · `POST /{id}/resolve` | `.authenticated()` + service kiểm tra quyền sở hữu (đúng mẫu dispute: **không** dùng query param ghi đè userId — chống IDOR) |
| `AdminSupportTicketController` | `/api/v1/admin/support/tickets` | `GET /queue` · `POST /{id}/assign` · `POST /{id}/reply` · `POST /{id}/close` | `@PreAuthorize("hasAnyRole('ADMIN','CUSTOMER_CARE')")` |

### Test + Docs (2)
- `test/.../service/SupportTicketServiceTest.java` (Mockito, đúng mẫu `DisputeServiceTest`) — ~10 test:
  1. submit BILLING → priority HIGH, deadline = +4h, reference khác rỗng
  2. submit OTHER → LOW, deadline = +3d
  3. description < 30 ký tự → bị validation (test DTO + 400 ở controller, hoặc mock bypass ở service — test service: tự kiểm tra lại nếu muốn layer kép, **chỉ test qua @Valid ở controller test nếu làm**)
  4. reply user thường với `isInternal=true` → 400
  5. reply khi ticket CLOSED → 400
  6. user không phải người nộp reply → 400
  7. assign OPEN → IN_PROGRESS + assignedTo; assign cho role không CC/ADMIN → 400
  8. queue: ticket SLA risk xếp trước ticket cũ
  9. close bằng CC → CLOSED + resolvedBy + closedAt
  10. markResolved bằng user → CLOSED + resolvedBy = user
- `docs/api/support-ticket-api.md` — đúng cấu trúc mẫu `dispute-resolution-api.md` (tổng quan, bảng endpoint, response JSON, mã lỗi, audit actions)

---

## 5. Files SỬA (2)

1. **`config/SecurityConfig.java`** — thêm **trước** rule wildcard `/api/v1/admin/**` (đúng mẫu disputes):
   ```java
   .requestMatchers("/api/v1/admin/support/**").hasAnyRole("ADMIN", "CUSTOMER_CARE")
   .requestMatchers("/api/v1/support/**").authenticated()
   ```
   (kèm comment SRS UC-105/106 + BR-59 như block disputes)
2. **`service/AdminDashboardService.java`** — `formatActionDescription` thêm 4 nhãn:
   `SUBMIT_TICKET` → "Nộp ticket hỗ trợ" · `REPLY_TICKET` → "Trả lời ticket hỗ trợ" · `ASSIGN_TICKET` → "Phân công ticket hỗ trợ" · `CLOSE_TICKET` → "Đóng ticket hỗ trợ" (để UC-86 recent-activities đọc được)

---

## 6. Thứ Tự Code (mỗi bước compile/verify được)

| Bước | Việc | Verify |
|---|---|---|
| 1 | V13 migration + 3 enum + 2 entity | `mvnw compile` (H2 ddl-auto tạo bảng; PG migrate V13 — chạy profile supabase 1 lần) |
| 2 | 2 repository + 5 DTO | `mvnw compile` |
| 3 | `SupportTicketService` (cả 8 method + notification + audit) | `mvnw compile` |
| 4 | 2 controllers + SecurityConfig + dashboard labels | `mvnw compile` |
| 5 | `SupportTicketServiceTest` (10 test) | `mvnw test -Dtest="SupportTicketServiceTest,DisputeServiceTest,AdminDashboardServiceTest,AdminUserControllerTest"` — mục tiêu xanh, không vỡ test cũ |
| 6 | `docs/api/support-ticket-api.md` | đọc lại + Postman (tài khoản `worker@cani.vn` submit → `cskh@cani.vn` queue/close; dùng `dev-token-` **chỉ trên profile h2**) |

---

## 7. Quyết Định Đã Chốt (mặc định nếu không phản hồi)

1. **Priority mapping:** user chỉ chọn category; hệ thống map BILLING/ACCOUNT→HIGH, TECHNICAL/GENERAL→MEDIUM, OTHER→LOW (SLA 4h/1d/3d).
2. **URL CC:** `/v1/admin/support/tickets/**` (đồng bộ disputes/eKYC — 1 chỗ bảo vệ, CC không bị 403).
3. **Internal note:** cột `is_internal` trong `support_ticket_messages` (1 bảng); user view trả thread đã lọc, CC view trả đủ.
4. **SLA breach (auto-escalate Team Lead):** MVP chỉ cờ `slaAtRisk` trong queue + audit log khi close muộn; không viết scheduler (YAGNI — SRS để dành job inventory).
5. **Reference number:** `TKT-` + ngày + suffix ngắn duy nhất (hết khi code bước 1).

---

## 8. Ngoài Scope (ghi nhận, không làm trong plan này)

- UC-110 FAQ (cần entity + CRUD riêng)
- Upload file thật (UC-119) — ticket dùng URL placeholder như eKYC
- Dashboard metric ticket (nối vào UC-86/111 — sau khi có `countByStatus`)
- Auto-escalation job khi quá SLA (background job inventory SRS)
- FE Web tab "Support Tickets" cho CC (gọi đúng các endpoint mục 4.4)
