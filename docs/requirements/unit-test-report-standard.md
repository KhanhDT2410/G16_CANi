---
name: se-unit-test-report
description: Chuẩn mực Kỹ nghệ Phần mềm chuyên sâu (IEEE 829 / ISO 29119) để thiết kế, tạo lập, tự động hóa và duy trì Báo cáo Kiểm thử Đơn vị (Unit Test Specification & Report) định dạng Excel. Áp dụng cho các dịch vụ Backend Spring Boot 3 (JUnit 5, Mockito), bao gồm kiến trúc sổ tính 4 tầng, ma trận truy vết (Traceability Matrix), kỹ thuật thiết kế ca kiểm thử N/A/B, quy trình quản lý khuyết tật (Defect Lifecycle) qua 3 vòng kiểm thử (Rounds 1-3), và bộ script tự động hóa bằng Python/openpyxl không gây sai lệch số liệu.
---

# SE Unit Test Report Specification & Methodology Standard (IEEE 829 / ISO 29119)

> **Mục đích tài liệu:** Cung cấp quy chuẩn Kỹ nghệ Phần mềm (Software Engineering Standards) toàn diện để toàn bộ thành viên trong nhóm (Developers, QA/Testers, Tech Leads) và AI Agents đồng bộ thực hiện việc viết, thẩm định và duy trì tài liệu Kiểm thử Đơn vị (Unit Test Report) cho tất cả các dịch vụ (Services) của dự án.

---

## 1. NGUYÊN LÝ KỸ NGHỆ PHẦN MỀM CỐT LÕI (CORE PRINCIPLES)

### 1.1. Bản chất Kim tự tháp Kiểm thử (Testing Pyramid) & Phạm vi Unit Test
1. **Kiểm thử Đơn vị là gì (Unit Test Boundary):**
   - Kiểm thử tập trung vào **từng đơn vị mã nguồn nhỏ nhất (Unit of Code)** — điển hình là một hàm/phương thức nghiệp vụ (`public method`) trong các lớp Service (ví dụ: `AuthService`, `JobService`, `EventService`).
   - Môi trường thực thi: Hoàn toàn trong bộ nhớ máy ảo JVM (`In-memory`), tốc độ mili-giây, cô lập 100% khỏi cơ sở dữ liệu vật lý (PostgreSQL, MySQL), mạng Internet, và các dịch vụ bên thứ ba (Supabase, VNPay, Firebase).
2. **Tiền điều kiện chuẩn mực (Pre-conditions / Mocking Setup):**
   - **BẮT BUỘC:** Mô tả trạng thái giả lập của các phụ thuộc qua thư viện Mocking (Mockito): `@Mock`, `@InjectMocks`, `when(repo.findById(...)).thenReturn(...)`, `SecurityContextHolder mock Authentication (Role=...)`.
   - **TUYỆT ĐỐI CẤM:** Không bao giờ ghi *"Can connect with server"*, *"Database đã bật"*, hay *"Đã đăng nhập trên UI"*. Đây là dấu hiệu vi phạm nghiêm trọng ranh giới giữa Unit Test và Integration/System Test.

### 1.2. Kỹ thuật Thiết kế Ca Kiểm thử (Test Design Techniques)
Mỗi phương thức nghiệp vụ cần được bao phủ tối thiểu theo 3 kỹ thuật:
1. **Normal (N) - Phân vùng tương đương hợp lệ (Happy Path):**
   - Đầu vào chuẩn, đầy đủ dữ liệu theo Business Rules (BR).
   - Kỳ vọng: Trả về DTO/Entity hợp lệ, gọi hàm lưu (`repository.save()`), ghi log kiểm toán.
   - **Tỷ trọng khuyến nghị:** `~35% - 40%`.
2. **Abnormal (A) - Phân vùng tương đương bất thường (Unhappy Path):**
   - Đầu vào vi phạm: Sai định dạng, thiếu trường bắt buộc, tài khoản bị khóa, thực thể không tồn tại, vi phạm quyền hạn (Access Denied).
   - Kỳ vọng: Ném ra đúng lớp ngoại lệ nghiệp vụ (`AppException`, `BadRequestException`, `ResourceNotFoundException`, `UnauthorizedException`) kèm HTTP Status (400, 401, 403, 404, 409).
   - **Tỷ trọng khuyến nghị:** `~35% - 40%`.
3. **Boundary (B) - Phân tích giá trị biên (Boundary Value Analysis - BVA):**
   - Kiểm thử tại chính xác các giá trị ngưỡng biên (`Min`, `Min-1`, `Max`, `Max+1`, `0`, `null`, rỗng `""`, chuỗi dài kịch trần, bán kính Geofence đúng 150m vs 151m, thời gian OTP hết hạn tại giây thứ 300 vs 301).
   - **Tỷ trọng khuyến nghị:** `~20% - 25%`.

### 1.3. Quy định Ngôn ngữ Tài liệu (Bilingual Consistency Rule)
- **Tiếng Anh (Toàn bộ phần kỹ thuật & mô tả):** Tất cả Tiêu đề cột, Tên Test Case, Pre-conditions, Input Parameters, Type, Round Status, Defect ID, Root Cause & Fix Notes phải viết bằng **Tiếng Anh chuyên ngành**.
- **Tiếng Việt (Thông điệp ngoại lệ hệ thống):** Các chuỗi thông báo lỗi nghiệp vụ hiển thị cho người dùng do hệ thống trả về trong `Expected Results` được **giữ nguyên Tiếng Việt** nguyên bản trong code (ví dụ: `BadRequestException("Mã OTP không hợp lệ hoặc đã hết hạn")`) để đảm bảo tính truy vết 1:1 với mã nguồn `backend`.

---

## 2. KIẾN TRÚC WORKBOOK CHUẨN 4 TẦNG (4-TIER WORKBOOK ARCHITECTURE)

Một tệp Excel Unit Test Specification hoàn chỉnh tuân thủ cấu trúc sau:

```
[Cover]                    --> Tầng 1: Quản lý phiên bản, SCM, Metadata & Lịch sử sửa đổi
   │
   ├──> [Test Cases]       --> Tầng 2: Mục lục 35+ Functions, Traceability Matrix & Môi trường JVM
   │
   ├──> [Test Statistics]  --> Tầng 3: Dashboard thống kê động (Zero-Drift Dynamic Formulas)
   │
   └──> [Detail Sheets]    --> Tầng 4: Chi tiết từng Service (AuthService, JobService, EventService...)
```

### Tầng 1: Sheet `Cover`
- **Metadata:** Project Name (`G16 - CANi`), Project Code (`SEP490_G16`), Document Code (`SEP490_G16_Report_5.1_Unit_Test_v2.0`), Issue Date, Author/Lead Tester.
- **Record of Change:** Bảng theo dõi lịch sử chỉnh sửa tài liệu qua các phiên bản (v1.0 $\rightarrow$ v2.0), có cột `Reference` trỏ tới Commit SHA trên Git.

### Tầng 2: Sheet `Test Cases` (Mục lục truy vết hệ thống)
- **Test Environment Setup:** Khai báo cấu hình JVM (OpenJDK 21), Test Runner (JUnit 5.10.2 / Surefire Plugin), Mockito Core 5.11.0, AssertJ 3.25.3.
- **Bảng Ma trận Truy vết:**
  `No` | `Feature Name` | `Class Name` | `Function Name` | `Function Code (Optional)` | `Sheet Name` | `Description` | `Pre-Condition`

### Tầng 3: Sheet `Test Statistics` (Dashboard số liệu tự động)
- **Nguyên tắc bất di bất dịch:** **TUYỆT ĐỐI KHÔNG GÕ TAY SỐ LIỆU.** Toàn bộ số liệu phải lấy động từ các Detail Sheet.
- Cột thống kê: `Passed`, `Failed`, `Pending`, `Untested`, `N (Normal)`, `A (Abnormal)`, `B (Boundary)`, `Total Test Cases`, `Success Rate`.
- **Định luật bảo toàn số học (Zero Discrepancy Law):**
  $$\text{Total Test Cases} = \text{Passed} + \text{Failed} + \text{Pending} + \text{Untested} = N + A + B$$
  Tổng ngang và tổng dọc phải khớp nhau chính xác từng đơn vị. Không để xảy ra hụt số.

---

## 3. QUY CHUẨN THIẾT KẾ DETAIL SHEET (DETAIL SHEET SPECIFICATION)

Đây là chuẩn mực áp dụng cho sheet của **mọi lớp Service** (`AuthService`, `JobService`, `EventService`, v.v.).

### 3.1. Cấu trúc Khối KPI Header (Rows 2 – 9)
- **Ô `B3` (Tổng số ca kiểm thử):**
  `=COUNTIF(A13:A500, "UTC*")`
- **Bảng Theo dõi các Vòng Kiểm thử (Rows 4 – 7):**
  - Hàng 4 (Tiêu đề): `A4: Testing Round` | `B4: Passed` | `C4: Failed` | `D4: Pending`
  - Hàng 5 (Round 1): `B5: =COUNTIF($F13:$F500,B4)` | `C5: =COUNTIF($F13:$F500,C4)` | `D5: =COUNTIF($F13:$F500,D4)`
  - Hàng 6 (Round 2): `B6: =COUNTIF($I13:$I500,B4)` | `C6: =COUNTIF($I13:$I500,C4)` | `D6: =COUNTIF($I13:$I500,D4)`
  - Hàng 7 (Round 3): `B7: =COUNTIF($L13:$L500,B4)` | `C7: =COUNTIF($L13:$L500,C4)` | `D7: =COUNTIF($L13:$L500,D4)`
- **Bảng Phân loại Kỹ thuật (Rows 8 – 9):**
  - Hàng 8 (Tiêu đề): `A8: Testing Type` | `B8: Normal` | `C8: Abnormal` | `D8: Boundary`
  - Hàng 9 (Số lượng): `B9: =COUNTIF($E13:$E500,B8)` | `C9: =COUNTIF($E13:$E500,C8)` | `D9: =COUNTIF($E13:$E500,D8)`

### 3.2. Cấu trúc Các Cột Chi tiết (Row 11: Header, Row 12+: Dữ liệu)
Mỗi sheet chi tiết phải có đầy đủ 16 cột (A đến P) và 1 vùng tra cứu tại cột T:

| Cột | Chữ | Tên Cột (Header) | Chiều rộng | Căn lề | Định dạng dữ liệu & Mô tả chi tiết |
| :---: | :---: | :--- | :---: | :---: | :--- |
| **1** | **A** | `Test Case ID` | 14 | Center | Mã định danh duy nhất: `UTC001`, `UTC002`... tăng tuần tự. |
| **2** | **B** | `Pre-conditions (Mocking Setup)` | 34 | Left | Thiết lập Mocking trong JUnit: `@Mock`, `when(...)`, `SecurityContext`. |
| **3** | **C** | `Input parameters` | 32 | Left | Tham số truyền vào hàm, DTO payload, giá trị biên. |
| **4** | **D** | `Expected Results` | 45 | Left | DTO trả về, Ngoại lệ ném ra (thông điệp TV), xác minh `verify()`. |
| **5** | **E** | `Type` | 14 | Center | Dropdown: `Normal`, `Abnormal`, `Boundary`. |
| **6** | **F** | `Round 1` | 12 | Center | **Trạng thái Round 1: KHÔNG TÔ MÀU NỀN**, Dropdown 4 giá trị. |
| **7** | **G** | `Test date` | 12 | Center | Ngày chạy Round 1 (`YYYY-MM-DD`). |
| **8** | **H** | `Tester` | 12 | Center | Tên tài khoản tester thực tế (ví dụ: `minhtb`, `hungpd`). |
| **9** | **I** | `Round 2` | 12 | Center | **Trạng thái Round 2: KHÔNG TÔ MÀU NỀN**, Dropdown 4 giá trị. |
| **10** | **J** | `Test date` | 12 | Center | Ngày chạy Round 2 (`YYYY-MM-DD`). |
| **11** | **K** | `Tester` | 12 | Center | Tên tài khoản tester chạy retest. |
| **12** | **L** | `Round 3` | 12 | Center | **Trạng thái Round 3: KHÔNG TÔ MÀU NỀN**, Dropdown 4 giá trị. |
| **13** | **M** | `Test date` | 12 | Center | Ngày nghiệm thu hồi quy (`YYYY-MM-DD`). |
| **14** | **N** | `Tester` | 12 | Center | Tên tester nghiệm thu. |
| **15** | **O** | `Defect IDs` | 16 | Center | Mã lỗi nếu Round 1 fail (`BUG-<SERVICE>-xxx`), **Font Đỏ Đậm `#C00000`**. |
| **16** | **P** | `Note & Root Cause Fix` | 45 | Left | Giải thích nguyên nhân gốc rễ và giải pháp sửa code cụ thể. |
| **20** | **T** | *(Lookup Table)* | 10 | Center | Bảng giá trị chuẩn: `T2=Passed`, `T3=Failed`, `T4=Pending`, `T5=N/A`. |

### 3.3. Quy tắc Định dạng Thị giác (Visual & Styling Standards)
1. **Tiêu đề Bảng (Row 11):**
   - Nền Xanh Navy đậm `#1F4E78`, chữ trắng in đậm (`Font(color="FFFFFF", bold=True)`), căn giữa, chiều cao dòng 28pt.
2. **Dải Phân cách Phương thức (Method Section Banner):**
   - Chiều cao dòng 24pt, nền Xanh nhạt `#D9E1F2`, chữ Xanh Navy đậm `#1F4E78` in đậm.
   - Nội dung ô A: Đánh số và tên hàm đầy đủ, ví dụ: `1. register(RegisterRequest request)`.
   - **QUY TẮC SỐNG CÒN:** **TUYỆT ĐỐI KHÔNG điền chữ `Pending`, `Passed` hay bất kỳ giá trị nào vào các cột F, I, L trên dòng phân cách phương thức này.** Nếu điền, hàm `COUNTIF` sẽ đếm sai số ca kiểm thử!
3. **Màu sắc các Cột Round (F, I, L):**
   - **XÓA MÀU TOÀN BỘ (Uncolored):** Đặt `fill_type=None` (không dùng nền vàng, xanh hay đỏ).
   - Chữ màu đen tiêu chuẩn (`color="000000"`), kích thước 10pt, căn giữa.
4. **Cấu hình Dropdown (Data Validation):**
   - Cột Round (F, I, L): Danh sách `formula1='"Passed,Failed,Pending,N/A"'`. **Bắt buộc phải có `Failed`**.
   - Cột Type (E): Danh sách `formula1='"Normal,Abnormal,Boundary"'`.

---

## 4. QUY TRÌNH QUẢN LÝ VÒNG KIỂM THỬ (MULTI-ROUND LIFECYCLE)

Một báo cáo đạt chuẩn SE cấp đại học và công nghiệp phải phản ánh đúng **hành trình QA qua thời gian**, không phải một báo cáo giả lập "tất cả Passed ngay từ đầu":

```
  Sprint Baseline (Round 1)       Fix & Retest (Round 2)         Release Sign-off (Round 3)
┌───────────────────────────┐   ┌──────────────────────────┐   ┌──────────────────────────┐
│  Thực thi toàn bộ kịch bản│   │ Retest các ca bị Failed  │   │ Kiểm thử hồi quy toàn bộ │
│  Phát hiện lỗi thực tế:   │──>│ Xác nhận Dev đã fix xong │──>│ Đảm bảo hệ thống ổn định │
│  - 25 Passed, 4 Failed    │   │ - Failed -> Passed       │   │ - 100% Passed            │
│  - Ghi mã BUG-xxx & Note  │   │ - Chưa chạy: Pending     │   │ - Nghiệm thu đóng gói    │
└───────────────────────────┘   └──────────────────────────┘   └──────────────────────────┘
```

- **Quy tắc khi Dự án đang trong quá trình phát triển (In-Progress Sprints):**
  - Nếu nhóm đang ở Round 1: Round 1 điền đúng kết quả (`Passed` / `Failed`). Round 2 và Round 3 **để trạng thái `Pending`**.
  - Không điền `N/A` vào các ca kiểm thử hợp lệ; `N/A` chỉ áp dụng nếu chức năng đó bị hủy bỏ khỏi phạm vi dự án.

---

## 5. HƯỚNG DẪN TỪNG BƯỚC CHO THÀNH VIÊN TRONG NHÓM (SOP)

Khi một thành viên (hoặc Subagent) được giao làm báo cáo cho một Service mới (ví dụ: `JobService`):

### Bước 1: Khảo sát Mã nguồn Java Service
1. Mở tệp mã nguồn: `backend/src/main/java/com/cani/service/<Service>.java`.
2. Liệt kê danh sách tất cả các phương thức `public`.
3. Với mỗi phương thức, xác định:
   - DTO đầu vào, DTO đầu ra.
   - Các điều kiện ném lỗi (Validation, Business Rules, Security Role).
   - Tối thiểu 3 - 5 Test Cases cho mỗi phương thức (1 Normal, 1-2 Abnormal, 1 Boundary).

### Bước 2: Cập nhật Sheet `Test Cases` (Mục lục)
- Mở sheet `Test Cases`, chèn các dòng tương ứng với các function của Service vào bảng:
  - `Feature Name`: Tên phân hệ nghiệp vụ.
  - `Class Name`: Tên lớp (ví dụ: `JobService`).
  - `Function Name`: Tên nghiệp vụ (ví dụ: `Create Job Post`).
  - `Function Code`: Mã quản lý (ví dụ: `FC-JOB-01`).
  - `Sheet Name`: Tên sheet tương ứng (ví dụ: `JobService`).

### Bước 3: Tạo Sheet Chi tiết mới
Sử dụng script Python chuẩn hóa có sẵn trong repo hoặc copy theo format mẫu:
- File script mẫu: [`docs/requirements/generate_service_unit_test_sheet.py`](generate_service_unit_test_sheet.py).
- Khai báo danh sách các method và test cases.
- Chạy lệnh (từ thư mục gốc repo):
  ```powershell
  python docs/requirements/generate_service_unit_test_sheet.py
  ```

### Bước 4: Liên kết vào Sheet `Test Statistics`
1. Mở sheet `Test Statistics`.
2. Thêm một hàng mới cho Service vừa tạo:
   - Cột C (Passed): `=JobService!B5` (Nếu tính theo Round 1) hoặc `=JobService!B7` (Nếu tính theo Round 3)
   - Cột D (Failed): `=JobService!C5` hoặc `=JobService!C7`
   - Cột E (Pending): `=JobService!D5` hoặc `=JobService!D7`
   - Cột G (Normal): `=JobService!B9`
   - Cột H (Abnormal): `=JobService!C9`
   - Cột I (Boundary): `=JobService!D9`
   - Cột J (Total): `=JobService!B3`
   - Cột K (Success Rate): `=C{row}/J{row}`

### Bước 5: Kiểm toán Tính toàn vẹn Số học (Pre-Flight Audit)
Trước khi commit code hoặc gửi cho Leader:
1. Đảm bảo không có ô nào xuất hiện lỗi `#REF!`, `#VALUE!`, `#NAME?`.
2. Kiểm tra tổng ngang: `Passed + Failed + Pending = Total = Normal + Abnormal + Boundary`.
3. Kiểm tra các dòng banner tên hàm: Tuyệt đối không chứa chữ `Pending` hay `Passed`.
4. Kiểm tra dropdown các cột Round: Bấm thử xem có xuất hiện đầy đủ 4 tùy chọn `Passed, Failed, Pending, N/A` hay không.

---

## 6. DANH MỤC CÁC SERVICE TRỌNG TÂM CẦN TRIỂN KHAI CHO DỰ ÁN CANi

Toàn bộ nhóm bám sát danh mục 6 Service cốt lõi này để phân chia công việc:

1. **`AuthService` (Đã hoàn thành mẫu chuẩn):** 8 phương thức, 29 Test Cases.
2. **`JobService` (Tuyển dụng & Tìm việc tức thì):** 7 phương thức (`createJob`, `searchJobs`, `applyJob`, `acceptApplication`, `cancelJob`, `completeJob`, `getJobDetails`).
3. **`EventService` (Quản lý Sự kiện & Địa điểm):** 6 phương thức (`createEvent`, `updateEvent`, `publishEvent`, `assignStaff`, `closeEvent`, `getEventSummary`).
4. **`AttendanceService` (Điểm danh QR & Geofence 150m):** 5 phương thức (`generateDynamicQr`, `checkInWithGeofence`, `checkOutWithGeofence`, `verifyAttendanceStatus`, `manualOverrideAttendance`).
5. **`MatchingDispatchService` (Thuật toán ghép nối WSM & Phân phối việc làm):** 5 phương thức (`calculateWsmMatchingScore`, `filterQualifiedWorkers`, `dispatchJobBatch`, `handleWorkerAcceptance`, `reDispatchOnTimeout`).
6. **`PaymentService` (Thanh toán cọc & Tự động quyết toán):** 4 phương thức (`createEscrowDeposit`, `processWorkerPayout`, `refundOrganizer`, `verifyWebhookSignature`).
