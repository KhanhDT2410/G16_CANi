# Kế hoạch nâng cấp bộ test CANi — từ "luôn xanh" thành "thật sự bắt được bug"

## Context

Bộ test hiện tại có **400 test / 37 file** và **luôn 100% xanh** — nhưng đó không phải vì code tốt, mà vì test được viết để *mô phỏng output của implementation* chứ không phải để *kiểm chứng hành vi đúng*. Bằng chứng:

- **77 `assertNotNull`** dùng làm assertion chính — "trả về gì đó là được".
- **Exact-string assertions** trên message tiếng Việt và trên chuỗi mask. Commit `306ee57` đổi mask CCCD từ 3→6 chữ số đầu và đổi message lỗi → **3 test đỏ** (`WorkerEkycServiceTest`, `EventOrganizerEkycServiceTest`) phải sửa tay. Đây là dấu hiệu kinh điển: mọi thay đổi *đúng* cũng làm test sập → team học cách "chỉnh test cho khớp" thay vì xem lại logic.
- **Hai lỗ hổng bảo mật tiền thật lọt qua toàn bộ 400 test**:
  - **BUG-A (billing)** — `SubscriptionService.confirmPayOSPaymentForUser` (`src/main/java/com/cani/service/SubscriptionService.java:294`): `pendingOrders` là `ConcurrentHashMap` **in-memory**; khi không tìm thấy order (server restart / multi-instance) mà PayOS gateway trả `PAID` → **cấp gói `MATCHING_PLUS` mặc định** cho bất kỳ EO nào, **không kiểm tra orderCode có thuộc EO đó không**. `orderCode` = `currentTimeMillis() % 900000000 + 100000000` → đoán được.
  - **BUG-B (auth)** — `AuthService.googleLogin` (`src/main/java/com/cani/service/AuthService.java:286`): `workerRepository.delete(w)` nằm trong `@Transactional` + `synchronized` method. `payments.worker_id` FK **không** `ON DELETE CASCADE` → xóa worker đã có Payment ⇒ FK violation ⇒ **rollback cả transaction** ⇒ googleLogin trả 500 ⇒ user kẹt. `catch (Exception e) { log.warn }` chỉ che lỗi, không cứu được transaction.
- **Toàn bộ 400 test là unit test có mock repository** → FK, UNIQUE, CASCADE, rollback **chưa bao giờ** được kiểm chứng. Migration `V25` (UNIQUE `payments.assignment_id`) không có test nào chạm tới.
- **Không có Jacoco, không có PIT** → không có cách nào chứng minh test bắt được bug.

**Kết quả mong muốn:** bộ test có failure case thật, có test chứng minh được 2 bug trên, có tầng integration chạm DB thật (FK/UNIQUE/rollback), và có cơ chế đo thay vì "cảm giác xanh".

**Phạm vi đã chốt với user:** Phase 1 + 2 + 3 (không làm Phase 4 — tooling Jacoco/PIT — trong lượt này). Cho phép 1 lần chạy online nếu cần tải dependency.

---

## Phase 1 — Red-first adversarial: 10 test chứng minh 2 bug là thật

### Nguyên tắc bắt buộc
Test **commit trước**, chạy trên HEAD hiện tại → **phải FAIL** → lưu output FAIL vào `docs/evidence/red-first-UTC-SEC.md` → **rồi mới** sửa production code → test xanh. Không có bằng chứng đỏ thì test đó không được merge.

### 1.1 BUG-A — Billing: `SubscriptionBillingSecurityTest`

**File mới:** `src/test/java/com/cani/service/eo/SubscriptionBillingSecurityTest.java`

Bám đúng house style của `SubscriptionServiceTest.java`: `@ExtendWith(MockitoExtension.class)`, `@Mock` repo + `@Mock PayOSService` + `@Mock NotificationService`, `@InjectMocks SubscriptionService`, `@Nested` + `@DisplayName("UTC-SEC-xxx: ...")` tiếng Việt, AssertJ + `verify`.

**Tái sử dụng (không viết lại):** helper `seedPendingOrder(long, UUID, PlanType)` đã có sẵn ở `SubscriptionServiceTest.java:95` — copy sang file mới, không cải tạo.

| # | Test method | Arrange | Contract bắt buộc |
|---|---|---|---|
| A1 | `confirmPayOSPaymentForUser_OrderNotInQueue_GatewayPaid_RejectsAndActivatesNothing` | `getPaymentStatus` → `"PAID"`, `pendingOrders` rỗng, `findActiveSubscriptions` → empty | `BadRequestException`; `verify(subscriptionRepository, never()).save(any())`; `verify(notificationService, never()).createAndSend(any(), any())` |
| A2 | `confirmPayOSPaymentForUser_OrderBelongsToOtherEo_GatewayPaid_Rejects` | seed order với `eoId = OTHER_EO`; gateway `"PAID"` | `BadRequestException`; `never().save(any())` (UTC006 hiện có đã cover — giữ, không viết lại) |
| A3 | `confirmPayOSPaymentForUser_GatewayStatusNotPaid_DoesNotActivate` | order của chính EO; gateway → `"PENDING"` / `"CANCELLED"` / `"UNKNOWN"` (`@ParameterizedTest`) | `BadRequestException`; `never().save(any())` |
| A4 | `confirmPayOSPaymentForUser_OwnerEo_GatewayPaid_ActivatesOrderPlanType` | seed order `PlanType.ENTERPRISE`; gateway `"PAID"` | `verify(subscriptionRepository).save(argThat(s -> s.getPlanType() == PlanType.ENTERPRISE))` — **test này bắt đúng default `MATCHING_PLUS`** |
| A5 | `confirmPayOSPaymentForUser_DoubleConfirm_SameOrder_ActivatesOnce` | happy path A4 chạy 2 lần, gateway vẫn `"PAID"` | `verify(subscriptionRepository, times(1)).save(any())` |
| A6 | `confirmPayOSPaymentForUser_UnknownOrder_EoAlreadyHasActivePlan_DoesNotGrantUpgrade` | order rỗng, gateway `"PAID"`, `findActiveSubscriptions` → 1 sub `FREE_TRIAL` | DTO `planType == FREE_TRIAL`; `never().save(...)` — chặn "mua gói trả phí miễn phí" |

**Fix production bắt buộc (A1–A6 chỉ xanh khi):**
1. Order phải resolve được từ **nguồn bền vững**: bảng `payos_orders` (entity `PayOSOrder` + `PayOSOrderRepository` + migration `V26__create_payos_orders.sql`, Postgres-compatible). `pendingOrders` chỉ còn là cache.
2. Không tìm thấy order → `throw BadRequestException`. **Tuyệt đối không** default `PlanType.MATCHING_PLUS`.
3. Tìm thấy order → kiểm `order.eoId == eo.getEoId()`; lệch → 400 (giữ nguyên logic hiện có).
4. `activateSubscription` chỉ được gọi với `planType` lấy từ order đã xác thực.
5. `createPayOSPaymentSession` phải **ghi order vào DB trước** khi gọi gateway.

### 1.2 BUG-B — Auth: `AuthWorkerTransitionTest`

**File mới:** `src/test/java/com/cani/service/auth/AuthWorkerTransitionTest.java`

Mock set giống `AuthServiceTest`: `UserRepository`, `WorkerRepository`, `EventOrganizerRepository`, `JwtDecoder`, `SupabaseAuthClient`, `EmailOtpService`, `Environment`.

| # | Test method | Arrange | Contract |
|---|---|---|---|
| B1 | `googleLogin_WorkerHasPaymentNoCccd_TargetEO_SucceedsAndKeepsWorkerRow` | worker `cccdNumber=null`, `totalShiftsCompleted=0`, `paymentRepository.existsByWorker_WorkerId(...)` → **true**; JWT hợp lệ | role → `EVENT_ORGANIZER`; `verify(workerRepository, never()).delete(any())`; **không** ném exception |
| B2 | `googleLogin_JunkWorkerNoPayment_TargetEO_DeletesWorkerRow` | `existsByWorker_WorkerId` → false | `verify(workerRepository).delete(argThat(w -> w.getWorkerId().equals(workerId)))` |
| B3 | `googleLogin_EstablishedWorkerWithCccd_TargetEO_KeepsWorkerRole` | `cccdNumber = "012345678"` | role vẫn `WORKER`; `never().delete(any())` |
| B4 | `googleLogin_WorkerHasPayment_TargetEO_RoleChangeIsPersisted` | như B1 | `verify(userRepository).save(argThat(u -> u.getRole() == UserRole.EVENT_ORGANIZER))` — bắt lỗi "role đổi xong bị rollback" |

**Fix production bắt buộc (B1–B4 chỉ xanh khi):**
1. `AuthService` thêm dependency `PaymentRepository`; chỉ xóa worker dummy khi `!paymentRepository.existsByWorker_WorkerId(workerId)`.
2. `PaymentRepository` thêm derived query `boolean existsByWorker_WorkerId(UUID workerId)` (1 dòng).
3. Bỏ `try/catch` nuốt lỗi quanh `delete` — để lỗi FK/DB được nhìn thấy thay vì bị log.warn rồi rollback cả tx.
4. *(Phương án thay thế được phép:* soft-deactivate `worker.isActive=false` thay vì delete — khi đó contract B2 đổi thành "không còn active". Phải ghi rõ lựa chọn trong PR description. **Khuyến nghị: FK-safe delete** để giữ hành vi hiện tại, ít rủi ro.)

---

## Phase 2 — Integration layer: FK / UNIQUE / rollback chạm DB thật

### Vì sao chọn H2 + `schema.sql` thủ công (không Testcontainers, không Flyway-trên-H2)
- **Flyway trên H2**: V1 dùng `gen_random_uuid()`, `TIMESTAMPTZ`, `JSONB`, `BIGSERIAL`, `CREATE TRIGGER ... EXECUTE FUNCTION` (plpgsql) → H2 fail ngay ở statement đầu. Phải viết lại ~25 file migration Postgres-compat.
- **Testcontainers Postgres**: Docker daemon **không chạy** và `.m2` chỉ có `testcontainers-bom`, **không có jar** → không chạy offline được, phụ thuộc máy dev.
- **H2 + schema thủ công**: deterministic, offline-safe, <1s. DDL **copy nguyên văn** từ V1 + V25 nên vẫn test đúng constraint thật.

### Files mới
| File | Nội dung |
|---|---|
| `src/test/resources/schema-integration.sql` | 7 bảng tối thiểu, DDL copy từ `V1__init_schema.sql` + constraint từ `V25`. **`payments.worker_id` KHÔNG `ON DELETE CASCADE`** (giữ đúng V1:419) |
| `src/test/resources/application-integration-test.yml` | `jdbc:h2:mem:cani_it;MODE=PostgreSQL;NON_KEYWORDS=USER,VALUE`; `ddl-auto: none`; `flyway.enabled: false`; `spring.sql.init.mode: always` → `schema-locations: classpath:schema-integration.sql` |
| `src/test/java/com/cani/support/AbstractIntegrationTest.java` | `@Tag("integration")` + `@SpringBootTest` + `@ActiveProfiles("integration-test")`; `@MockitoBean PayOSService` → **không bao giờ gọi PayOS thật** |
| `src/test/java/com/cani/service/integration/PaymentFkAndUniquenessIT.java` | I1–I3 |
| `src/test/java/com/cani/service/integration/SubscriptionActivationIT.java` | I4–I5 |

### Test cụ thể
| # | Test method | Nội dung | Assert |
|---|---|---|---|
| I1 | `deleteWorkerWithPayment_ForeignKeyViolation_Rollback` | insert user+eo+worker+event+shift+assignment+payment | `DataIntegrityViolationException`; payment vẫn còn sau rollback |
| I2 | `createSecondPaymentSameAssignment_UniqueViolation` | 2 payment cùng `assignment_id` | `DataIntegrityViolationException`; `count() == 1` |
| I3 | `confirmPayOSPaymentForUser_UnknownOrder_Rejects_NoSubscriptionCreated` | `@MockitoBean getPaymentStatus` → `"PAID"`; gọi `confirmPayOSPaymentForUser(eoUserId, 123456789L)` | `BadRequestException`; `subscriptionRepository.findAll()` rỗng → **chứng minh BUG-A ở tầng DB thật** |
| I4 | `purchaseAndConfirm_OwnerEo_ActivatesPlanWithRealDb` | `createPayOSPaymentSession` (PayOSService mock → checkoutUrl giả) → `confirmPayOSPaymentForUser` (mock → `"PAID"`) | 1 subscription, `planType == ENTERPRISE`, `status == ACTIVE` |
| I5 | `googleLogin_WorkerWithPayment_TargetEO_DoesNotFailWith500` | seed worker có payment; JWT hợp lệ; `targetRole=EVENT_ORGANIZER` | **không** ném exception; role = `EVENT_ORGANIZER`; worker row **vẫn còn** → **chứng minh BUG-B ở tầng DB thật** |

### Tách integration khỏi unit test (giữ `mvn test` nhanh & offline-safe)
- File integration dùng **hậu tố `*IT.java` + `@Tag("integration")`** (cả hai).
- `pom.xml`: thêm `<excludedGroups>integration</excludedGroups>` cho surefire mặc định → `mvn test` không chạy integration.
- Thêm profile `test-integration` bật `excludedGroups` rỗng + `groups=integration`.

---

## Phase 3 — Failure-case matrix + refactor assertion yếu

### 3.1 Case matrix (6 service critical) — **C** = đã có, **N** = mới

**SubscriptionService** (UTC001–UTC014 hiện có)
| Nhóm | Case | Status |
|---|---|---|
| Invalid | `orderCode <= 0` → 400 | **N** |
| Invalid | `createPayOSPaymentSession` với `planType = null` → 400 | **N** |
| State | order unknown + gateway PAID | **N** (A1) |
| State | double confirm → activate 1 lần | **N** (A5) |
| State | confirm sau khi subscription đã EXPIRED | **N** |
| Boundary | quota shifts = max-1 / max | **N** |
| Boundary | quota workers = max-1 / max | **N** |
| Boundary | `eventsUsed = null` → 0, không NPE | **N** |
| Boundary | `maxEvents = null` → 0, không NPE | **N** |
| Concurrency | 2 thread confirm cùng orderCode → 1 activation | **N** (integration) |

**DirectPaymentService** (7 test hiện có)
| Nhóm | Case | Status |
|---|---|---|
| Invalid | `assignmentId = null` → 400 | **N** |
| Invalid | `declaredAmount <= 0` → 400; lệch `agreedAmount` quá tolerance → 400 | **N** |
| State | confirm payment đã `CONFIRMED` → idempotent hoặc 400, **không** double-credit | **N** |
| State | `DECLARED` → `CONFIRMED` → `DISPUTED` (chuyển ngược) | **N** |
| Boundary | `agreedAmount = 0` → hợp lệ | **N** |
| Uniqueness | 2 payment cùng `assignment_id` → UNIQUE violation | **N** (I2) |
| Concurrency | 2 thread `confirmPayment` → 1 lần chuyển status | **N** |

**MatchingDispatchService** (4 nhóm test hiện có)
| Nhóm | Case | Status |
|---|---|---|
| Boundary | distance **= 15.0km** (đúng bán kính) → vào | **N** (hiện chỉ cover >15km) |
| Boundary | distance = 15.0km ± epsilon → 1 lượt vào, 1 lượt ra | **N** |
| Boundary | `remainingWorkers = -1` (data xấu) | **N** |
| Boundary | `requiredWorkers = 0` → không dispatch ai | **N** |
| State | shift `startsAt` đã qua | **N** |
| Invalid | `requiredSkills` rỗng → match tất cả | **N** |
| Concurrency | 2 thread dispatch cùng shift → không tạo proposal trùng | **N** (integration) |

**AuthService** (UTC001–029 hiện có)
| Nhóm | Case | Status |
|---|---|---|
| State | worker có Payment + targetRole=EO | **N** (B1) |
| State | worker rác → xóa | **N** (B2) |
| Security | `worker@cani.vn` không bao giờ leo quyền | **N** (có điều kiện trong code, chưa có test riêng) |
| Concurrency | 2 lần googleLogin cùng email | **N** |

**WorkerEkycService / EventOrganizerEkycService**
| Nhóm | Case | Status |
|---|---|---|
| Invalid | provider trả `null` → không NPE | **N** |
| Invalid | provider **throw exception** → không 500 | **N** |
| Invalid | ảnh null/rỗng → reject | **N** |
| Boundary | CCCD 5 / 6 / 9 / 12 / 13 chữ số (mask + hash) | **N** |
| Boundary | faceMatchScore = threshold ± ε (`@ParameterizedTest`) | **N** |
| State | submit lại 2 lần → 1 record active | **N** |
| State | admin approve hồ sơ đã APPROVED → idempotent | **N** |
| State | admin approve hồ sơ REJECTED → reject | **N** |
| State | admin duyệt hồ sơ không tồn tại → 404 | **N** |
| Uniqueness | CCCD trùng worker↔EO cross-table | **N** |
| Uniqueness | CCCD trùng chính worker đó → cho phép re-upload | **N** |

**EoEventService**
| Nhóm | Case | Status |
|---|---|---|
| Invalid | lat/lng ngoài [-90,90] / [-180,180] | **N** |
| Invalid | `endTime <= startTime` | **N** |
| Invalid | `requiredWorkers = 0` hoặc âm | **N** |
| Quota | createEvent khi EO hết quota event (strict) | **N** |

**Tổng Phase 3: ~45 test mới.**

### 3.2 Refactor assertion yếu — quy tắc chuẩn hoá

Đưa vào `docs/TESTING_RULES.md`:

| Mã | Sai (hiện tại) | Đúng (sau) |
|---|---|---|
| **R1** | `assertNotNull(result)` là assertion chính | Luôn ≥2 assert, ≥1 assert trên **giá trị nghiệp vụ** (`getPlanType()`, `getRole()`), không phải null-check |
| **R2** | `assertThat(ex.getMessage()).contains("Giao dịch không thuộc về...")` | `isInstanceOf(BadRequestException.class)` + assert mã lỗi ổn định. **Không assert text tiếng Việt** |
| **R3** | Assert chính xác chuỗi mask (`"***456"`) | Assert theo thuộc tính: `hasSize(11).endsWith("456").doesNotContain("123")` |
| **R4** | `isEqualTo(BigDecimal.valueOf(99000))` | `isEqualByComparingTo(...)` (scale-insensitive) |
| **R5** | `verify(repo).save(any())` | `verify(repo).save(argThat(s -> s.getPlanType() == PlanType.ENTERPRISE))` |
| **R6** | Happy path chỉ assert "không ném exception" | ≥1 assert output + ≥1 verify side-effect |

**3 file làm mẫu trong lượt này:**
1. `src/test/java/com/cani/service/auth/AuthServiceTest.java` — 13 assert message tiếng Việt → `isInstanceOf(...)`; `assertNotNull` → assert field cụ thể.
2. `src/test/java/com/cani/service/eo/EventOrganizerEkycServiceTest.java` — assert `status`/`verifiedBy`/`verifiedAt` cụ thể.
3. `src/test/java/com/cani/service/worker/WorkerEkycServiceTest.java` — assert score/threshold theo giá trị; thêm 3 case boundary từ matrix.

**Backlog (không làm lượt này), thứ tự ưu tiên:** `MatchingDispatchServiceTest` → `WorkerJobServiceTest` (67 test) → `EoEventServiceTest` → `AdminUserServiceTest` → phần còn lại. Mỗi PR refactor ≤3 file để review được.

---

## Acceptance gates

| Gate | Tiêu chí | Lệnh kiểm chứng |
|---|---|---|
| **G1** | Có **bằng chứng đỏ**: 10 test UTC-SEC đã FAIL trên HEAD trước khi fix, lưu tại `docs/evidence/red-first-UTC-SEC.md` | Chạy `mvn -o -Dtest=SubscriptionBillingSecurityTest,AuthWorkerTransitionTest test` trên commit trước fix → ≥5 FAIL |
| **G2** | Sau fix: 10/10 UTC-SEC xanh, **không** test nào `@Disabled`, tổng suite ≥ 455 test | `mvn -o test` → `Failures: 0, Errors: 0` |
| **G3** | Integration layer chạm DB thật: I1 throw FK, I2 throw UNIQUE, I3/I4/I5 xanh | `mvn -o -Ptest-integration test` |
| **G4** | `mvn -o test` mặc định **không** chạy integration và vẫn xanh | `mvn -o test` + kiểm log không có `*IT` |
| **G5** | 3 file refactor không còn assert message tiếng Việt, `assertNotNull` không còn là assertion chính | `grep -rn "getMessage()).contains(\"[A-Z]" src/test/java/com/cani/service/{auth,eo,worker}` → rỗng |
| **G6** | Không test nào gọi PayOS thật | grep `new PayOSService` trong src/test → rỗng |
| **G7** | Build offline vẫn chạy được | `mvn -o test` |

---

## Lệnh thực thi

```bash
MVN=/c/Users/HP/.m2/wrapper/dists/apache-maven-3.9.9-bin/33b4b2b4/apache-maven-3.9.9/bin/mvn
cd /d/SEP490/Code/backend

# RED — viết test trước, chạy trên HEAD, phải FAIL
"$MVN" -o -Dtest=SubscriptionBillingSecurityTest+AuthWorkerTransitionTest test

# GREEN — sau khi vá production code
"$MVN" -o -Dtest=SubscriptionBillingSecurityTest+AuthWorkerTransitionTest test

# Toàn suite
"$MVN" -o test

# Integration (tách riêng)
"$MVN" -o -Ptest-integration test

# Đếm assertion yếu (phải giảm dần)
grep -rn "assertNotNull" src/test/java | wc -l
```

> **Offline caveat:** `mvn -o` dùng `~/.m2` local. Repo chưa dùng `jacoco`/`pitest` trong lượt này. Nếu sau này thêm tooling: cần **1 lần online** để tải plugin, sau đó offline chạy bình thường (user đã đồng ý).

---

## Files cần sửa

**Production (Phase 1):**
- `src/main/java/com/cani/service/SubscriptionService.java` — bỏ default `MATCHING_PLUS` (L294); resolve order từ nguồn bền vững
- `src/main/java/com/cani/service/AuthService.java` — FK-safe guard trước `delete` (L286); bỏ try/catch nuốt lỗi
- `src/main/java/com/cani/repository/PaymentRepository.java` — thêm `existsByWorker_WorkerId`
- `src/main/java/com/cani/entity/PayOSOrder.java` — **mới**
- `src/main/java/com/cani/repository/PayOSOrderRepository.java` — **mới**
- `src/main/resources/db/migration/V26__create_payos_orders.sql` — **mới**

**Test (Phase 1):**
- `src/test/java/com/cani/service/eo/SubscriptionBillingSecurityTest.java` — **mới**
- `src/test/java/com/cani/service/auth/AuthWorkerTransitionTest.java` — **mới**

**Test (Phase 2):**
- `src/test/resources/schema-integration.sql` — **mới**
- `src/test/resources/application-integration-test.yml` — **mới**
- `src/test/java/com/cani/support/AbstractIntegrationTest.java` — **mới**
- `src/test/java/com/cani/service/integration/PaymentFkAndUniquenessIT.java` — **mới**
- `src/test/java/com/cani/service/integration/SubscriptionActivationIT.java` — **mới**

**Test (Phase 3):**
- `src/test/java/com/cani/service/eo/SubscriptionServiceTest.java` — bổ sung ~10 case
- `src/test/java/com/cani/service/admin/DirectPaymentServiceTest.java` — bổ sung ~7 case
- `src/test/java/com/cani/service/matching/MatchingDispatchServiceTest.java` — bổ sung ~7 case
- `src/test/java/com/cani/service/auth/AuthServiceTest.java` — refactor assertion + ~2 case
- `src/test/java/com/cani/service/worker/WorkerEkycServiceTest.java` — refactor + ~6 case
- `src/test/java/com/cani/service/eo/EventOrganizerEkycServiceTest.java` — refactor + ~6 case
- `src/test/java/com/cani/service/eo/EoEventServiceTest.java` — bổ sung ~4 case

**Build:**
- `pom.xml` — profile `test-integration` + surefire `excludedGroups=integration`

**Docs:**
- `docs/TESTING_RULES.md` — **mới** (quy tắc R1–R6)
- `docs/evidence/red-first-UTC-SEC.md` — **mới** (bằng chứng đỏ)

---

## Thứ tự & song song hoá

| Phase | PR | Nội dung | Chạy song song với |
|---|---|---|---|
| 1 | PR#1 | 10 test adversarial + fix 2 bug + migration V26 | — (đụng file hot `SubscriptionService`, `AuthService`) |
| 2 | PR#2 | Integration layer (5 file test + 1 pom profile) | **PR#3** (P2 chỉ đụng `src/test/resources`, file `*IT`; P3 đụng unit test khác) |
| 3 | PR#3 | ~45 test matrix + refactor 3 file + `docs/TESTING_RULES.md` | **PR#2** |
| 4 | PR#4 | *(deferred)* Jacoco + PIT | sau P3 để mutation score đo suite mới |

**Ước lượng:** Phase 1 ≈ 1–2 ngày · Phase 2 ≈ 1 ngày · Phase 3 ≈ 2–3 ngày · tổng ≈ 60–70 test mới, suite lên ~465–475 test.
