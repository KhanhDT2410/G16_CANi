---
name: unit-test-report
description: Unified Unit Testing & Reporting standard for CANi platform. Enforces AAA pattern, Normal/Abnormal/Boundary test design, and 100% compatibility with Report-5.1_Unit Test.xlsx template and Excel formulas.
---

# Unified Unit Test & Report Skill for CANi Platform

> **Tiêu chuẩn Kỹ nghệ Phần mềm:** Skill này định nghĩa quy trình chuẩn hóa từ thiết kế, cài đặt, thực thi Unit Test đến xuất báo cáo nghiệm thu kỹ thuật. Mọi thành viên và AI Agent khi tham gia dự án **CANi** bắt buộc phải tuân thủ nghiêm ngặt để đảm bảo chất lượng phần mềm (Software Quality Assurance) và tính tương thích 100% với file biểu mẫu `Report-5.1_Unit Test.xlsx`.

---

## 1. Nguyên Tắc Thiết Kế Unit Test (Test Design Standards)

Mỗi phương thức nghiệp vụ (`methodName`) thuộc tầng Service hoặc Business Logic bắt buộc phải được bao phủ bởi 3 nhóm kịch bản thử nghiệm:

### 1.1. Phân nhóm Kịch bản Kiểm thử (Test Types)
1. **Normal Case (`Normal`):**
   - Kiểm thử luồng chạy lý tưởng (Happy Path) với dữ liệu đầu vào hoàn toàn hợp lệ.
   - Dữ liệu trả về đúng định dạng, các mock repository/dependency được gọi đúng số lần và đúng tham số.
2. **Abnormal Case (`Abnormal`):**
   - Kiểm thử các luồng ngoại lệ, lỗi nghiệp vụ: Người dùng không đủ quyền hạn, tài nguyên không tồn tại (`ResourceNotFoundException`), dữ liệu không hợp lệ (`BadRequestException`), xung đột trạng thái (`AppException`), lỗi dịch vụ bên thứ ba (Supabase, VNPT eKYC, Gemini Vision, FCM).
   - Đảm bảo hệ thống ném ra đúng Exception Type và thông báo lỗi rõ ràng, không làm lộ stack trace nội bộ.
3. **Boundary Case (`Boundary`):**
   - Kiểm thử các giá trị biên của miền dữ liệu:
     * Số học: Min, Max, 0, số âm, số lượng nhân sự tối thiểu/tối đa trong 1 ca làm việc.
     * Chuỗi / Ký tự: Chuỗi rỗng `""`, chuỗi đạt giới hạn ký tự tối đa (ví dụ mô tả sự kiện 1000 ký tự), ký tự đặc biệt, định dạng email/CCCD biên.
     * Thời gian: Ca làm việc bắt đầu ngay tại thời điểm hiện tại (`leadTime = 0`), ca kéo dài qua đêm (23:00 - 05:00), khoảng cách địa lý sát mép bán kính radar (ví dụ đúng 20.0 km).

### 1.2. Cấu trúc Triển khai (AAA Pattern)
Mọi hàm test đều phải phân tách rõ ràng 3 giai đoạn:
- **Arrange (Given):** Chuẩn bị mock objects, dữ liệu đầu vào (DTO, Entity) và cấu hình hành vi của mock (`when(...).thenReturn(...)` hoặc `when(...).thenThrow(...)`).
- **Act (When):** Thực thi trực tiếp phương thức cần kiểm thử (SUT - System Under Test).
- **Assert (Then):** Kiểm tra tính đúng đắn của kết quả trả về (`assertEquals`, `assertNotNull`), xác minh Exception (`assertThrows`) và kiểm tra tương tác mock (`verify(repo, times(1)).save(...)`, `verify(..., never())...`).

### 1.3. Nguyên tắc Cô Lập Tuyệt Đối (Test Isolation)
- **Zero Real External Dependency:** Unit Test KHÔNG ĐƯỢC kết nối vào Database thật, không gọi API thật của VNPT eKYC, Supabase hay Firebase FCM.
- Tất cả các Repository, RestClient, External Service bắt buộc phải được thay thế bằng `@Mock` (Mockito) trong Backend hoặc Fake/Stub/Mocktail trong Mobile.
- Test chạy độc lập, không phụ thuộc vào thứ tự thực thi và có thời gian chạy dưới 50ms cho mỗi test case.

---

## 2. Quy Tắc Tương Thích Tuyệt Đối Với Excel Template (`Report-5.1_Unit Test.xlsx`)

Để đảm bảo các công thức tự động trong Excel (`COUNTIF`, `SUM`, tỉ lệ %) hoạt động chính xác khi copy dữ liệu vào file Excel:

### 2.1. Cấu Trúc Cột (16 Cột Chuẩn Bắt Buộc)
| Cột | Tên Cột Excel | Định Dạng & Quy Tắc Giá Trị |
|---|---|---|
| **A** | **Test Case ID** | **Dòng phân cách hàm:** Điền `methodName()` (Cột B..P để trống).<br>**Dòng test case:** Điền `UTC001`, `UTC002`, `UTC003`... tăng dần liên tục trong toàn bộ Sheet. Tiền tố `UTC` là bắt buộc để ô `B3` (`=COUNTIF(A13:A1005,"UTC*")`) đếm chính xác. |
| **B** | **Pre-conditions** | Điều kiện tiên quyết và trạng thái mock (ví dụ: `User authenticated as EO; ShiftRepository mocked`). Nếu không có ghi `N/A`. |
| **C** | **Input parameters** | Tham số truyền vào dạng cặp key-value: `userId=...; dto.title="Event A"; dto.maxWorkers=5`. Nếu không có ghi `N/A` hoặc `none`. |
| **D** | **Expected Results** | **BẮT BUỘC** viết đúng 3 thành phần (xuống dòng bằng `<br>` trong bảng Markdown):<br>`Return: <giá trị hoặc kiểu DTO trả về / null>`<br>`Exception: <Tên Exception Class hoặc N/A>`<br>`Log Message: <Nội dung thông báo lỗi hoặc N/A>` |
| **E** | **Type** | Chỉ nhận 1 trong 3 giá trị chính xác (phân biệt hoa thường): `Normal`, `Abnormal`, hoặc `Boundary` (để khớp với công thức `=COUNTIF($E12:$E1003,B8)` tại dòng 9 của Excel). |
| **F** | **Round 1** | Trạng thái thực thi Vòng 1: `Passed`, `Failed`, hoặc `Pending`. |
| **G** | **Test date** | Ngày thực thi kiểm thử (`YYYY-MM-DD`). |
| **H** | **Tester** | Tên hoặc User code của tester / developer. |
| **I** | **Round 2** | Trạng thái Vòng 2: `Passed`, `Failed`, `Pending`, hoặc `N/A`. |
| **J** | **Test date** | Ngày thực thi Vòng 2 (nếu có). |
| **K** | **Tester** | Tester Vòng 2. |
| **L** | **Round 3** | Trạng thái Vòng 3 (Final Round): `Passed`, `Failed`, `Pending`, hoặc `N/A`. |
| **M** | **Test date** | Ngày thực thi Vòng 3 (nếu có). |
| **N** | **Tester** | Tester Vòng 3. |
| **O** | **Defect IDs** | Mã lỗi ghi nhận trong Jira/Issue Tracker (ví dụ: `BUG-102`). Nếu không có lỗi ghi để trống. |
| **P** | **Note** | Ghi chú thêm nếu có. |

### 2.2. Điểm Lưu Ý Sống Còn Về Công Thức `Test Statistics`
> **CẢNH BÁO KỸ THUẬT TỪ SENIOR QA:**
> Trong file Excel mẫu `Report-5.1_Unit Test.xlsx`, ô `C12` của sheet `Test Statistics` lấy dữ liệu từ công thức `=ModuleName1!B7` (tương ứng với hàng **Round 3 Passed**).
> - Nếu một Test Case đã đạt `Passed` ngay từ **Round 1** và không có lỗi: Khi ghi nhận kết quả, cần điền `Passed` cho cả Round 2 và Round 3 (hoặc điền `Passed` tại Round 3 với tư cách là Final Acceptance) để công thức tổng hợp tại sheet `Test Statistics` không bị ghi nhận là 0 Passed!
> - Nếu kịch bản có bug ở Round 1: Round 1 ghi `Failed`, sau khi fix code ở Round 2 ghi `Passed`, Round 3 ghi `Passed`.

---

## 3. Quy Trình Thực Thi 4 Bước Cho AI & Thành Viên Nhóm

Khi được giao nhiệm vụ viết Unit Test và báo cáo cho một Module:

```
[BƯỚC 1: Phân Tích Logic] ──> [BƯỚC 2: Viết Code Test] ──> [BƯỚC 3: Chạy Test CLI] ──> [BƯỚC 4: Xuất Markdown & TSV]
```

### Bước 1: Phân Tích Logic Nghiệp Vụ Của Method
1. Đọc mã nguồn phương thức trong Service (`backend/src/main/java/com/cani/service/...`).
2. Xác định các trường hợp:
   - Happy Path (`Normal`).
   - Các nhánh validate, check null, check quyền, check trạng thái (`Abnormal`).
   - Các điểm biên của giá trị (`Boundary`).

### Bước 2: Viết Mã Nguồn Unit Test
1. Tạo hoặc cập nhật file test tương ứng trong `backend/src/test/java/com/cani/service/...`.
2. Sử dụng `@ExtendWith(MockitoExtension.class)`, `@InjectMocks`, `@Mock`.
3. Đặt tên phương thức test rõ ràng theo quy ước:
   `should<DoSomething>_When<Condition>()`
4. Viết assertions chặt chẽ: Kiểm tra từng trường dữ liệu quan trọng của DTO trả về, không chỉ `assertNotNull`.

### Bước 3: Chạy Lệnh Kiểm Thử Trên Terminal
1. Chạy lệnh:
   - Backend: `./mvnw test -Dtest=TenClassTest` (hoặc `.\mvnw.cmd test -Dtest=TenClassTest` trên Windows).
   - Mobile: `flutter test test/...`
2. Kiểm tra log đảm bảo `BUILD SUCCESS` và `Failures: 0, Errors: 0`.
3. Ghi lại ngày test thực tế và kết quả `Passed`.

### Bước 4: Xuất Báo Cáo Markdown Khớp Excel & Khối TSV
Tạo hoặc cập nhật file `docs/UNIT_TEST_REPORT_EXPORT.md` với đầy đủ cấu trúc:
1. Sheet `Cover`
2. Sheet `Test Cases` (Môi trường & Danh mục Sheet)
3. Sheet `Test Statistics` (Bảng thống kê tỷ lệ % khớp công thức)
4. Từng Sheet `<ModuleName>` gồm:
   - Header thống kê module
   - Bảng Markdown 16 cột
   - **Khối TSV (Tab-Separated Values):** Đặt trong `<details><summary>Copy nhanh vào Excel (TSV)</summary>` để người dùng copy trực tiếp 1 lần và dán thẳng vào ô `A12` của sheet trong file Excel mà không bị lỗi cột hay vỡ dòng.

---

## 4. Quy Cách Định Dạng Khối TSV Copy-Paste Excel

Trong khối TSV:
- Giữa các cột phân cách bằng đúng ký tự `\t` (Tab).
- Nội dung ô `Expected Results` có nhiều dòng (Return, Exception, Log Message) phải được bọc trong dấu ngoặc kép `"` và xuống dòng bằng `\n` thực tế. Khi paste vào Excel, toàn bộ nội dung sẽ nằm gọn gàng trong 1 ô mà không bị nhảy dòng sang các row khác.

Ví dụ định dạng dòng TSV chuẩn:
```tsv
createShift()	N/A	N/A	N/A	N/A	N/A	N/A	N/A	N/A	N/A	N/A	N/A	N/A	N/A	N/A	N/A
UTC001	User authenticated as EO	userId=u1; dto.title=""	"Return: null
Exception: BadRequestException
Log Message: Title is required"	Abnormal	Passed	2026-11-03	CANi_Tester	Passed	2026-11-03	CANi_Tester	Passed	2026-11-03	CANi_Tester		
```

