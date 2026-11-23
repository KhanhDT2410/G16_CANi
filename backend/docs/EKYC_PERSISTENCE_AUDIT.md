# CANi Backend eKYC Hardening & User Data Persistence Audit

Nhánh **hung**, baseline **459d9e5**. Phạm vi kiểm tra gồm Auth, Worker, EO, Customer Care, Admin, VNPT và private storage. **Chưa xác minh dữ liệu runtime production**. Kiểm kê bên dưới phân biệt đường ghi trong mã nguồn, schema Flyway và kết quả đọc sau commit trên PostgreSQL thử nghiệm.

## 1. Current Backend Data Storage

Trong các bảng dưới đây, W và E lần lượt là `worker_ekyc_verifications` và `eo_ekyc_verifications`. Đường dẫn `/workers/...`, `/eo/...`, `/admin/...` và `/ekyc/...` có tiền tố `/api/v1`. Với eKYC, lệnh ghi đi từ `WorkerEkycController` hoặc `EventOrganizerEkycController` qua các DTO/multipart, service cùng tên, repository và PostgreSQL. Ảnh được ghi qua `EkycImageStorage`. Các mục “có đường ghi” được xác minh bằng mapper/service và DDL. Kết quả đọc lại sau commit được liệt kê riêng ở mục 6.

| Dữ liệu | Entity/Column | API ghi | Service ghi | Nơi lưu | API đọc | Ai có quyền đọc | Chính sách xóa | Kết quả |
|---|---|---|---|---|---|---|---|---|
| 1. Họ tên | `users.full_name`, `workers.full_name`, W `full_name`, E `representative_name` | `POST /api/auth/register`, `PUT /api/auth/me`, cập nhật Worker, eKYC submit/review | `AuthService.register/updateBasicProfile`, `WorkerProfileService.updateMyProfile`, hai service eKYC | PostgreSQL | `GET /api/auth/me`, profile, status, review detail | Chủ tài khoản, Admin, tên hiển thị qua DTO công khai, reviewer xem tên OCR khi chờ duyệt | Hồ sơ tài khoản theo yêu cầu xóa/ẩn danh, OCR theo chính sách eKYC | OCR ghi vào hồ sơ xác minh. Tên tài khoản đồng bộ khi duyệt, tên tự khai qua luồng chỉnh sửa là tên hồ sơ |
| 2. Email | `users.email`, `email_otp_verifications.email` | Register, Google login, gửi OTP | `AuthService`, trigger V6, `EmailOtpService.sendOtp` | PostgreSQL, email đăng nhập cũng thuộc GoTrue | `/api/auth/me`, profile owner, Admin users. EO public có email che tùy quyền nghiệp vụ | Chủ tài khoản, Admin, auth provider | Theo vòng đời tài khoản và chính sách OTP | Có đường ghi, constraint unique trên users |
| 3. Điện thoại | `users.phone` | Register, `PUT /api/auth/me`, `PUT /eo/profile` | `AuthService`, `EventOrganizerEkycService.updateEoProfile` | PostgreSQL | `/api/auth/me`, profile owner, Admin, EO có quan hệ My Team xem số Worker | Theo quyền của API, xem mục 4 | Theo vòng đời tài khoản | Có đường ghi, constraint unique trên users |
| 4. Password hash | GoTrue `auth.users.encrypted_password`, V3 loại `public.users.password_hash` | Register, confirm reset | `SupabaseAuthClient.createUser/updatePassword` | Kho xác thực GoTrue | Auth password grant | GoTrue thực hiện xác thực | Theo vòng đời credential và chính sách provider | CANi chuyển password qua GoTrue. Test cục bộ không xác minh kho credential GoTrue |
| 5. Ngày sinh | W/E `dob` | eKYC submit OCR | Hai service eKYC finalization | PostgreSQL | Status/detail, profile owner suy ra tuổi từ hồ sơ APPROVED | Chủ hồ sơ, reviewer khi chờ xử lý | Cần quy định xóa metadata OCR | Có setter/DDL, phụ thuộc kết quả OCR |
| 6. Giới tính | W/E `gender` | eKYC submit OCR | Hai service eKYC finalization | PostgreSQL | Status/detail | Chủ hồ sơ, reviewer khi chờ xử lý | Cần quy định xóa metadata OCR | Có setter/DDL |
| 7. Địa chỉ | `worker_preferences.base_address`, `event_organizers.address`, W/E `origin_address/residence_address` | PUT profile, eKYC submit OCR | `WorkerProfileService`, `EventOrganizerEkycService`, Worker eKYC | PostgreSQL | Profile, status/detail | Địa chỉ cá nhân Worker dành cho owner, địa chỉ doanh nghiệp qua DTO EO theo quyền, OCR dành cho owner/reviewer | Tách vòng đời profile và OCR | Có đường ghi. OCR địa chỉ cư trú không được dùng điền địa chỉ EO |
| 8. Số CCCD | W/E `cccd_number_hash/cccd_number_masked`, `workers.cccd_number` legacy | eKYC submit/review | Hai service eKYC, `EkycIdentityCryptoService` | PostgreSQL với HMAC và số che, số OCR nguyên dạng trong bộ nhớ trong lần xử lý và VNPT | Status/detail chỉ thấy số che | Chủ hồ sơ, reviewer đang xử lý | Xử lý theo mục 5 và 7 | Đường ghi mới dùng HMAC. Cần kiểm kê giá trị legacy trên DB thực |
| 9. Ngày/nơi cấp | W/E `issue_date`. Chưa thấy cột nơi cấp riêng | eKYC submit OCR mặt sau | Hai service eKYC finalization | PostgreSQL nếu có ngày cấp. Nội dung nơi cấp có thể nằm trong ảnh | Status/detail và ảnh riêng tư | Chủ hồ sơ, reviewer theo quyền | Ngày cấp theo metadata, ảnh theo retention | Có đường ghi ngày cấp khi VNPT trả dữ liệu. Chưa thấy mapper lưu nơi cấp |
| 10. Ảnh mặt trước | W/E `front_image_url` | Multipart eKYC submit | Hai service eKYC, `LocalPrivateEkycImageStorage.store` | Byte ảnh trên filesystem riêng, DB lưu object key | GET `/ekyc/images/{type}/{id}/front` | Owner/Admin, CC khi PENDING_REVIEW hoặc bearer link hợp lệ | Retry, reset Worker, TTL retention và legal hold | Ghi file + key, đọc lại qua abstraction |
| 11. Ảnh mặt sau | W/E `back_image_url` | Multipart eKYC submit | Cùng cơ chế mặt trước | File riêng + object key | GET slot `back` | Cùng quyền ảnh mặt trước | Cùng chính sách mặt trước | Ghi file + key theo code |
| 12. Selfie | W/E `selfie_image_url` | Multipart eKYC submit | Cùng cơ chế mặt trước | File riêng + object key | GET slot `selfie` | Cùng quyền ảnh mặt trước | Cùng chính sách mặt trước | Ghi file + key theo code |
| 13. Trạng thái xác minh | W/E `status/routing_decision/failure_code`, `workers.kyc_status`, `event_organizers.verification_status` | Submit, review, Worker reset | Decision Engine và hai service eKYC | PostgreSQL | Status, list/detail reviewer và profile nghiệp vụ | Owner/reviewer, DTO profile có trạng thái đã xác minh theo ngữ cảnh | Metadata tuân theo mục 7, Worker reset có kiểm tra hold | Ghi các trạng thái xác minh và trạng thái profile |
| 14. OCR | W/E trường chuẩn hóa và `ocr_confidence`. W có `identification_features` legacy | Multipart submit | `VnptEkycClient.parseVnptResults`, service finalize | PostgreSQL với trường chọn lọc, JSON response chỉ trong bộ nhớ | Status/detail | Owner, reviewer khi chờ xử lý | Phải chốt TTL OCR, ảnh theo scheduler | Có đường ghi trường OCR. Confidence có thể null, đặc điểm nhận dạng Worker không ghi mới |
| 15. Face Compare | W/E `face_match_score` và `fraud_flags.faceCompareStatus` | Submit | VNPT client, Decision Engine, finalize | PostgreSQL với score nullable/trạng thái tóm tắt, VNPT nhận ảnh | Status/detail | Owner, reviewer khi chờ xử lý | Theo TTL metadata cần phê duyệt | Có đường ghi score thực trả về và trạng thái, không lưu face embedding trong CANi |
| 16. Liveness | W/E `fraud_flags.livenessStatus` khi kết quả được đánh giá | Submit | VNPT client, Decision Engine, finalize | PostgreSQL JSONB, VNPT khi gọi endpoint đã cấu hình | Status/detail | Owner, reviewer khi chờ xử lý | Theo TTL metadata | Có nhánh thực hiện HTTP khi bật và cấu hình endpoint. Chưa xác nhận endpoint thật |
| 17. Thời điểm submit/review | W/E `created_at/updated_at/reviewed_at`, E `verified_at` trên profile | Submit/review | JPA timestamps, service review | PostgreSQL | Status/detail | Owner, reviewer | Theo vòng đời bản ghi | `created_at` là lần tạo bản ghi. Retry dùng lại bản ghi, audit có thời gian từng lần finalize. `reviewed_at` có cho xét duyệt thủ công mới |
| 18. Người duyệt | W/E `reviewed_by`, `audit_logs.user_id` | `POST /admin/{ekyc hoặc eo-ekyc}/{id}/review` | Hai service admin review, `AuditLogService.logEkycAction` | PostgreSQL | Status/detail, audit Admin | Owner qua status, reviewer theo quyền, Admin qua audit | Theo chính sách audit | Lấy ID từ principal, không lấy từ body |
| 19. Phản hồi VNPT thô | Không có mapping ghi payload raw trong flow | Submit | VNPT client parse `JsonNode`, service ghi danh sách field tường minh | Bộ nhớ lúc xử lý. DB chỉ giữ provider, reference, trạng thái và field đã chọn | Không có API raw | Phân quyền VNPT và bộ nhớ tiến trình | Chính sách lưu trữ phía VNPT cần xác nhận | Chưa thấy lưu raw hoặc base64 vào DB/log ứng dụng trong flow này |
| 20. IP, thiết bị, audit | `audit_logs.ip_address/user_agent/new_values/created_at`, luồng riêng `user_device_tokens` | Các thao tác Admin/audit, đăng ký push device | `AuditLogService`, `AccountAppealService`, service device | PostgreSQL, log ứng dụng riêng | Admin audit, endpoints thiết bị của owner | Admin, owner cho luồng thiết bị của mình | Theo TTL audit/log/push cần phê duyệt | eKYC ghi actor/verification/status/decision/retry count. Cột IP và UA không được điền trong audit eKYC. Một số audit appeal ghi nhãn PUBLIC/INTERNAL |
| Avatar và profile nghiệp vụ | `users.avatar_url`, `workers`, `worker_preferences`, `event_organizers` | PUT profile | `WorkerProfileService`, `EventOrganizerEkycService` | PostgreSQL lưu URL, quy trình chứng chỉ dùng storage khác | Owner profile và public DTO theo trường | Theo DTO và quyền hồ sơ | Bỏ liên kết avatar khi ẩn danh. Xóa byte cần chính sách của nơi lưu URL | Worker lưu chuỗi URL từ request. Đường ghi này không lưu byte avatar |

Nguồn kiểm tra chính gồm [AuthController](../src/main/java/com/cani/controller/AuthController.java), [AuthService](../src/main/java/com/cani/service/AuthService.java), [WorkerProfileService](../src/main/java/com/cani/service/WorkerProfileService.java), [WorkerEkycService](../src/main/java/com/cani/service/WorkerEkycService.java), [EventOrganizerEkycService](../src/main/java/com/cani/service/EventOrganizerEkycService.java), [schema V1](../src/main/resources/db/migration/V1__init_schema.sql), [auth V3](../src/main/resources/db/migration/V3__auth_refactor.sql) và [trigger V6](../src/main/resources/db/migration/V6__single_gatekeeper_and_sync_trigger.sql).

## 2. eKYC Data Flow

~~~mermaid
flowchart TD
    A[Flutter gửi 3 ảnh và dữ liệu đối chiếu] --> B[JWT và principal Backend]
    B --> C[Giới hạn kích thước, MIME, magic bytes và phần mở rộng]
    C --> D[Transaction 1 tạo hoặc chuẩn bị lần retry]
    D --> E[Ghi 3 object key vào private storage]
    E --> F[VNPT upload, OCR trước sau, Face Compare]
    F --> G[Liveness nếu cấu hình hoặc có trong kết quả]
    G --> H[Transaction 2 khóa CCCD, kiểm tra trùng và quyết định]
    H --> I[(PostgreSQL lưu trạng thái, key và audit)]
    I --> J[Commit, cập nhật profile khi APPROVED]
    J --> K[Status cho owner hoặc hàng đợi và review detail]
    H --> L[Rollback dọn ảnh mới và giữ ảnh còn tham chiếu]
    D --> M[Scheduler retention và quét file mất tham chiếu]
~~~

Backend bỏ qua ID từ query/body không thuộc hợp đồng chính chủ và lấy đối tượng từ principal. QR/nhập tay dùng để đối chiếu. Provider chỉ lưu trạng thái đã chuẩn hóa vào hồ sơ xác minh. OCR trước khi approve có thể trả về ở status của chủ hồ sơ, nhưng chưa ghi đè tên tài khoản. Timeout hoặc lỗi dịch vụ là `TECHNICAL_FAILURE`, cho phép retry. `NOMATCH` hay Liveness fail rõ ràng bị từ chối. Thiếu dữ liệu OCR, ngày hết hạn hoặc evidence Liveness khi policy yêu cầu chuyển `PENDING_REVIEW`. Lỗi parse Face Compare chuyển lỗi kỹ thuật.

VnptEkycClient xử lý hợp đồng hiện có của repo với envelope success, `object.msg` và `object.prob`. `MATCH` cần số hữu hạn và không âm. Ngưỡng chỉ bật khi đã cấu hình cùng cận trên hợp lệ. Kết quả kiểm tra thẻ riêng lẻ không đủ để xác nhận selfie. Tài liệu công khai xác nhận các nhóm chức năng, còn field, thang điểm và endpoint chính thức của gói CANi cần đối chiếu với VNPT. [VNPT eKYC](https://ekyc.vnpt.vn/vi/technology)

## 3. Security Findings

| Mức | Bằng chứng và tác động | Trạng thái |
|---|---|---|
| P0 | `EmailService.sendOtpEmail` từng ghi mã OTP vào log kèm email. Mã còn hiệu lực có thể bị dùng sai mục đích ở luồng đổi mật khẩu | Đã bỏ ghi mã và đưa mã ra khỏi tiêu đề email ở phần thay đổi trước trên nhánh |
| P1 | `WorkerProfileService.getWorkerProfileForEo` từng gắn email, điện thoại, địa chỉ và tuổi vào API hồ sơ cho mọi tài khoản đăng nhập | Đã giới hạn field theo owner và quan hệ My Team |
| P1 | `recordAudit` của hai service dùng `Map.toString()` cho cột JSONB. PostgreSQL từ chối khi ghi audit, review và audit có thể lệch nếu chạy độc lập | Đã chuyển JSON hợp lệ và lưu trong transaction quyết định |
| P1 | `EkycImageController` trước hardening cho CC đọc hồ sơ rộng, signed URL chưa gắn với lần lưu ảnh. Response review có thể tự cấp thêm URL | CC chỉ xem `PENDING_REVIEW`. Link gắn với key/trạng thái, response review tối thiểu |
| P1 | `finalizeSubmission` có thể lệch DB/file khi retry, commit lỗi hoặc process dừng giữa chừng | Xóa ảnh cũ sau commit, dọn ảnh mới khi rollback rõ ràng, grace scanner xử lý file mồ côi |
| P1 | `EkycIdentityCryptoService` từng fallback SHA-256 cho CCCD, dữ liệu có miền giá trị nhỏ. Rủi ro còn trên bản ghi legacy | Yêu cầu pepper khi submit mới. Tra cứu hash legacy trong giai đoạn chuyển tiếp |
| P1 | `AuthService.register` trước đây có thể tạo user local khi GoTrue lỗi và không có credential hoạt động | Giới hạn fallback cho profile H2 |
| P1 | `EmailOtpService.verifyOtp` lưu OTP plaintext và có luồng chạy trong transaction caller, lỗi có thể rollback bộ đếm. Thiếu cơ chế dọn OTP hết hạn ở service | Còn mở, cần thống nhất cách giữ cơ chế retry khi GoTrue lỗi và làm migration OTP |
| P1 | Flyway `V50__skill_system_v3_unified_skills_and_requirements.sql:122` tham chiếu `sa.checkout_time` trên schema cài mới thiếu cột. Full migration chặn startup PostgreSQL | Tái hiện trên DB thử nghiệm, kiểm thử eKYC pin V49 |
| P2 | `LocalPrivateEkycImageStorage` cần persistent volume, ACL và encryption at rest. Scheduler ảnh cần policy được duyệt và rollout theo deployment | Có tùy chọn vận hành, cần kiểm tra môi trường thực |
| P1 | **AuthService.googleLogin** có nhánh đổi vai trò Worker mới sang EO, xóa profile Worker khi chưa có payment. Cần xét cả hồ sơ đang xử lý, legal hold và quan hệ Assignment trước một lần xóa thật | Còn mở, cần test với dữ liệu nghiệp vụ và chốt quy tắc chuyển vai trò |
| P2 | **AdminUserService.getUserById** trả CCCD legacy và FCM token trong chi tiết Admin. Danh sách phân trang dùng DTO tóm tắt riêng | Cần loại các trường nếu công cụ Admin không sử dụng |
| P2 | Queue eKYC trả danh sách toàn bộ hồ sơ pending. Nhánh `getMyProfile` còn nạp nhiều tập dữ liệu phụ để dựng profile | Eager-load liên kết cần cho queue/ảnh, Admin users search có phân trang và batch load. Queue eKYC cần phân trang khi tải thực tế tăng |
| P2 | Endpoint đổi tên hồ sơ lưu display name. Review notes là text tự do, có thể chứa PII nhập tay | Cần quy tắc nghiệp vụ nếu muốn khóa tên pháp lý hoặc buộc reason code thay cho ghi chú |

## 4. Changes Implemented

- `VnptEkycClient`, `EkycDecisionEngine`, `EkycIdentityCryptoService` và `EkycCccdLockManager` kiểm tra kết quả sinh trắc, scale/threshold, khóa trùng CCCD và từ chối ghi mới khi thiếu pepper. Profile cập nhật tên khi APPROVED.
- `WorkerEkycService` và `EventOrganizerEkycService` kiểm tra attempt khi finalize hoặc đánh dấu lỗi. `AuditLogService` ghi JSONB cùng transaction với hồ sơ. Hai controller thêm GET chi tiết chờ duyệt cho Admin/CC. Manual approval yêu cầu hồ sơ có blind index CCCD để kiểm tra trùng. Hai POST review trả trạng thái và thẩm định viên, không cấp link ảnh mới.
- `EkycImageController` và `EkycImageAccessTokenService` kiểm tra role, owner, thời hạn, chữ ký và phiên bản ảnh. GET ảnh trả `Cache-Control: private, no-store` và `nosniff`. Signed URL dành cho owner/Admin hoặc CC trong lúc hồ sơ pending, ảnh vẫn cần qua backend và TTL.
- `LocalPrivateEkycImageStorage` ghi key ngẫu nhiên, dùng create-new, chặn đường dẫn không hợp lệ/symlink. `EkycImageValidator` đối chiếu định dạng thật từ header với Content-Type, tên tệp và dung lượng. `EkycImageRetentionService` giữ legal hold và thử lại các lần unlink lỗi. `EkycOrphanImageCleanup` chỉ xét tệp do hệ thống sinh, quá grace, không thuộc lượt xử lý và không có DB reference.
- `WorkerProfileController` và `WorkerProfileService` phân quyền field cá nhân tại điểm tạo DTO. Public Worker giữ thông tin nghề nghiệp. Owner đọc email, phone, địa chỉ và tuổi tính từ hồ sơ APPROVED khi có ngày sinh. EO đọc số Worker khi có quan hệ My Team. Địa chỉ thường trú OCR không được dùng làm địa chỉ doanh nghiệp.
- `AuthService` và `SupabaseAuthClient` giới hạn fallback tài khoản giả lập cho H2 và bỏ ghép body lỗi từ GoTrue vào phản hồi.
- `application.yml` và `.env.example` để thời hạn ảnh theo trạng thái bằng 0 khi chưa chọn chính sách. `EKYC_RETENTION_CLEANUP_ENABLED=true` bật job xử lý deadline đã ghi. `EKYC_RETENTION_ORPHAN_GRACE_HOURS` dương bật dọn file mồ côi. Đặt `EKYC_STORAGE_DIR` tới volume riêng đã mã hóa, bền qua redeploy và chỉ cho tiến trình backend đọc/ghi. Ở môi trường nhiều replica, mọi replica phục vụ hồ sơ phải đọc cùng dữ liệu storage và dùng cùng cấu hình signer từ secret manager.

## 5. Database Changes

[V49](../src/main/resources/db/migration/V49__ekyc_review_and_retention.sql) đã có trên `hung`. Migration thêm `legal_hold DEFAULT false` và `reviewed_at NULL` ở W/E, bật RLS và thu quyền `anon/authenticated/PUBLIC`. ORM cần role backend phù hợp với RLS. PostgreSQL thử nghiệm dùng owner cho JPA và định nghĩa các role Supabase cần bởi migration lịch sử. Không sửa migration đã có. Sửa audit dùng chính schema `audit_logs.new_values jsonb` hiện tại.

Trước rollout, kiểm tra số lượng dòng thiếu `worker_id/eo_id` hoặc FK bị đứt, các `cccd_number_hash` lặp trên hồ sơ APPROVED của tài khoản khác nhau, số CCCD legacy còn dạng số đầy đủ, deadline cũ và key không tồn tại. Xuất số đếm tổng hợp, vì danh sách giá trị CCCD chứa PII. `reviewed_at` cũ giữ null trừ khi có audit tin cậy và đối chiếu được quyết định tương ứng. `updated_at` không đại diện chính xác cho thời điểm duyệt. Backfill hash cần đọc CCCD đã xác minh từ nguồn được phép, tính HMAC bằng secret ngoài DB rồi kiểm tra trùng trước khi ghi. Hash SHA-256 cũ không thể chuyển trực tiếp thành HMAC. Giữ khả năng đối chiếu trong giai đoạn chuyển đổi và xem xét xác minh lại hồ sơ thiếu nguồn tin cậy trước khi bỏ hash cũ. Làm sạch bản sao legacy chỉ sau khi đối soát sử dụng và xác nhận policy.

## 6. Test Results

Môi trường Java 21.0.8, Maven Wrapper, H2 cho các regression test và PostgreSQL 16.6 riêng trên loopback cho persistence eKYC. Bộ test eKYC dùng ảnh/định danh tổng hợp và mock ở ranh giới VNPT. Lệnh Maven bên dưới nhận `CANI_AUDIT_PG_PORT` và `CANI_AUDIT_PG_PASSWORD` để kết nối database thử nghiệm có tên `cani_audit` với role `postgres`. Các migration lịch sử yêu cầu `anon`, `authenticated` và `service_role`.

~~~powershell
$env:SPRING_PROFILES_ACTIVE = 'h2'
$env:VNPT_EKYC_ENABLED = 'false'
$env:FIREBASE_ENABLED = 'false'
.\mvnw.cmd '-Dtest=*,!SkillVisionAiClientLiveTest' test
~~~

| Kiểm tra | Kết quả |
|---|---|
| PostgreSQL V1–V49 | PASS. Flyway ghi success V49. Cả hai bảng eKYC bật RLS, `anon` và `authenticated` đều thiếu quyền SELECT |
| PostgreSQL clean install hết migration | FAIL tại V50 vì thiếu `shift_assignments.checkout_time`. Schema đặt cột tương ứng ở `attendance_logs.checkout_time` |
| Rà soát FK và blind index ở DB thử nghiệm | PASS. 0 bản ghi mồ côi, 0 hồ sơ APPROVED thiếu hash, 0 nhóm hash APPROVED trùng giữa các tài khoản tại thời điểm đo |
| VNPT, Supabase và DB production | BLOCKED. Cần fixture theo hợp đồng thật, môi trường provider, secrets và quyền truy cập có kiểm soát |

| Kết quả kiểm thử | Lệnh hoặc suite | Trạng thái |
|---|---|---|
| Biên dịch và kiểm thử hồi quy | Lệnh Maven ở trên, bao gồm `AuthServiceTest`, `AuthWorkerTransitionTest`, `WorkerProfileServiceTest`, `MatchingDispatchServiceTest`, `ProposalStatusUpdaterTest`, `WorkerShiftHistoryAuditTest` và `WorkerJobServiceTest` | **650/652 PASS** ở lượt toàn bộ. Hai lỗi còn lại do fixture Mockito của ca rollback chạy ngoài transaction và ảnh hưởng test kế tiếp. Sau khi sửa fixture, cả hai suite liên quan PASS khi chạy lại |
| Luồng Worker/EO và phân quyền | `WorkerEkycServiceTest`, `EventOrganizerEkycServiceTest`, `EkycImageSecurityTest`, `EkycUserIdParamIgnoredTest` | **28 PASS** trong lượt toàn bộ, gồm duplicate CCCD, retry, ownership, hạn chế CC/EO, chữ ký sai và upload sai định dạng |
| Quyết định VNPT | `VnptEkycClientTest` | **15 PASS** trong lượt toàn bộ, gồm timeout, thiếu Face Compare, JSON lỗi, HTTP 429, Liveness thiếu/sai/hợp lệ và giấy tờ không hợp lệ |
| PostgreSQL eKYC và file lifecycle | `.\mvnw.cmd '-Dtest=EkycPostgresPersistenceTest,EkycStorageLifecycleTest' test` với biến môi trường ở trên | **6/6 PASS**, `BUILD SUCCESS`. Đọc User và Worker profile từ giao dịch khác, Worker retry giữ cùng verification ID, đọc lại tệp, EO duyệt và ghi JSONB, rollback giữ tệp cũ, xóa tệp mới, legal hold, orphan cleanup và URL hết hạn |

Regression còn bao gồm `DirectPaymentServiceTest` và `WorkerShiftHistoryAuditTest`. Lớp audit lịch sử đối soát Assignment, Attendance, Payment và Review. Test gọi Gemini live được loại khỏi lượt cuối. Run mới nhất cho hai suite sửa fixture đọc lại trực tiếp PostgreSQL với schema V49.

## 7. Remaining Risks

- Cần hợp đồng gói VNPT cho trạng thái MATCH/NOMATCH, score, ngưỡng, endpoint và ý nghĩa Liveness, cùng cam kết retention của provider. Tham số thử nghiệm là fixture và không phải xác nhận hợp đồng.
- Đặt `EKYC_HASH_PEPPER` ổn định và secret ký ảnh riêng với entropy đủ mạnh trong secret manager. Đổi pepper cần chiến lược hai khóa/backfill để duy trì phát hiện trùng. Tránh ghi query `sig` vào access logs hoặc telemetry. Audit này chưa xác minh reverse proxy, log cũ, encryption/ACL của volume, volume sau redeploy, bản sao DB hay backup production.
- Hạn lưu ảnh và metadata, legal hold, backup expiry và thời hạn audit cần chủ dữ liệu/pháp lý phê duyệt. Hold hiện là cờ DB, chưa có API vận hành. Job orphan cần grace dài hơn thời gian xử lý tối đa. Hồ sơ `PROCESSING` tồn đọng sau crash cần được vận hành rà soát khi job bảo toàn tệp. Bản ghi có ảnh bị mất do lỗi ổ đĩa cần phục hồi từ bản sao đã được duyệt hoặc yêu cầu nộp lại.
- Xóa tài khoản hiện có thao tác đổi trạng thái Admin và Worker reset eKYC. Luồng xử lý yêu cầu xóa nên thu hồi phiên, gỡ avatar/dữ liệu liên hệ, gỡ push token và tệp eKYC được phép xóa, giữ hành động dưới mã vụ việc ít dữ liệu. Với dispute đang mở hoặc nghĩa vụ lưu, đặt hold trước. Giữ tham chiếu Assignment, Attendance, Payment và Dispute, dùng định danh giả khi được phép. Backups cần lịch hết hạn, hạn chế quyền restore và áp lại yêu cầu xóa sau khôi phục. FK `public.users → auth.users` V3 có cascade khi schema auth tồn tại. Xóa GoTrue trước khi đánh giá FK và nghĩa vụ có thể làm mất dữ liệu nghiệp vụ. Chưa triển khai endpoint xóa hàng loạt.
- Trường OCR phục vụ màn hình hiện có vẫn được lưu với ACL cấp ứng dụng, gồm ngày sinh, địa chỉ giấy tờ, ngày cấp và giới tính. Scheduler hiện tập trung vào file ảnh. Chính sách thu gọn OCR theo trạng thái, thời hạn, dispute và nhu cầu đọc lại của mobile cần được triển khai sau khi chốt hợp đồng lưu giữ. Điểm Face Compare đã chuẩn hóa vẫn là metadata nhạy cảm.
- Quy trình tạo tài khoản thật, credential GoTrue, hoạt động gửi email và các migration trên DB production chưa được xác minh. `V50` cần sửa ở phạm vi migration skill trước khi nhận kết quả full-schema clean install.

## 8. Final Data Storage Matrix

| Dữ liệu | Nơi lưu và bảo vệ | Quyền đọc | Điều kiện xóa |
|---|---|---|---|
| User ID, role, trạng thái | PostgreSQL `users`, JWT GoTrue cho phiên | Owner và Admin, DTO nghiệp vụ có phần định danh cần thiết | Giữ định danh kỹ thuật của lịch sử nghiệp vụ, thu hồi phiên theo quy trình xóa |
| Họ tên | `users.full_name/workers.full_name`, tên OCR trong W/E | Tên hồ sơ có thể xuất hiện công khai. Tên OCR giới hạn owner/reviewer | Ẩn danh hồ sơ theo policy, xét TTL OCR độc lập |
| Email | `users.email`, GoTrue và bản ghi OTP | Owner/Admin. Public EO có trường che theo nghiệp vụ đang dùng | Theo vòng đời account và OTP |
| Điện thoại | `users.phone` với unique constraint | Owner, Admin, EO xem Worker trong My Team. Public EO có masking theo nghiệp vụ | Gỡ khi ẩn danh nếu không có nghĩa vụ giữ |
| Password | GoTrue quản lý password hash | GoTrue xác thực | Theo vòng đời auth account sau kiểm tra FK và nghĩa vụ |
| Ngày sinh, giới tính | W/E, ACL backend và RLS. Chưa có mã hóa cột ở tầng ứng dụng | Owner và reviewer pending, tuổi chỉ trong owner Worker profile | Cần TTL metadata có phê duyệt |
| Địa chỉ | Địa chỉ hồ sơ trong PostgreSQL theo mục đích nghiệp vụ, OCR nằm trong W/E | Địa chỉ cá nhân theo owner/reviewer, địa chỉ EO theo DTO | Gỡ copy OCR và account theo policy riêng |
| CCCD | HMAC-SHA256 cho tra cứu, suffix 4 số che. Số đầy đủ chỉ trong quá trình xử lý mới, có thể còn ở legacy | API status/review chỉ hiển thị số che. API Admin user detail có thể đọc cột legacy. Backend giữ HMAC | Theo thời hạn dedup/hold được duyệt, thực hiện kế hoạch legacy |
| Ngày/nơi cấp | `issue_date` khi có. Nơi cấp có thể nằm trong ảnh | Owner/reviewer | Metadata theo policy, ảnh theo retention |
| Ảnh trước và ảnh sau | File volume riêng, key ngẫu nhiên trong W/E, encryption at rest do volume | Owner/Admin, CC pending, bearer URL ngắn hạn | Xóa qua retry sau commit, reset Worker không hold, scheduler nếu có deadline |
| Selfie | File volume riêng, cùng cơ chế với ảnh CCCD | Cùng quyền ảnh CCCD | Cùng chính sách ảnh CCCD |
| Trạng thái OCR và giấy tờ | Field chuẩn hóa, mã lỗi và flags JSONB trong W/E | Owner/reviewer, trạng thái hồ sơ tổng hợp ở profile | TTL metadata, có thể giữ kết quả tối thiểu cho trạng thái nghiệp vụ |
| Face Compare và Liveness | Score/trạng thái quan sát được, flags W/E | Owner/reviewer. Danh sách queue chỉ cần trạng thái hồ sơ | TTL metadata cần phê duyệt |
| Trạng thái xác minh | W/E và trạng thái profile Worker/EO | Owner, Admin, CC pending, trạng thái công khai theo DTO | Có thể giữ trạng thái tối thiểu, xử lý hold trước khi xóa evidence |
| Thời điểm và người duyệt | W/E và audit JSONB với actor/time | Owner qua status, Admin audit và reviewer theo quyền | Theo chính sách audit, giữ khi có dispute |
| VNPT raw | Bộ nhớ xử lý và hệ thống VNPT bên ngoài | Runtime backend và nhà cung cấp | Theo vòng đời request và chính sách VNPT |
| IP, thiết bị, audit | Cột audit tùy use case, bảng device riêng, logs hạ tầng. eKYC không điền IP/UA | Admin, quyền dịch vụ, owner với thiết bị của mình | TTL audit/log, thu hồi device token và áp dụng hold |
| Avatar, Worker/EO nghiệp vụ | URL avatar và trường profile PostgreSQL, byte thuộc hệ thống lưu URL nếu có | Theo DTO và quyền API | Gỡ avatar/PII đủ điều kiện, duy trì liên kết lịch sử cần giữ |
