# BỘ TEST CASE CHI TIẾT — PHÂN HỆ ADMIN & CUSTOMER CARE (CANi PLATFORM)

> **Mục tiêu:** Tài liệu danh mục Test Case hoàn chỉnh phục vụ viết Unit Test (JUnit 5 + MockMvc/Service) và nghiệm thu chức năng trên Google Sheets cho 2 phân hệ Quản trị viên (Admin) và Chăm sóc khách hàng (Customer Care).  
> **Tổng số Test Case:** 47 ca kiểm thử  
> **File CSV tương thích Google Sheets:** `docs/test-cases/TEST_CASES_ADMIN_CUSTOMER_CARE.csv`  
> **File TSV (Copy-Paste trực tiếp vào Sheet):** `docs/test-cases/TEST_CASES_ADMIN_CUSTOMER_CARE.tsv`  

---

## 1. TỔNG QUAN PHÂN BỔ TEST CASE

| STT | Phân hệ (Module) | Mã Use Case / SRS | Số lượng Test Case | Phạm vi chính |
|:---:|---|---|:---:|---|
| 1 | **Admin Dashboard** | UC-86 | 5 | Metrics KPIs, Recent Activities (Audit batch lookup), Phân quyền RBAC |
| 2 | **Admin User Management** | UC-87 | 9 | Phân trang, Search đa trường, Profile Worker/EO, Khóa/Mở khóa, Chặn tự khóa |
| 3 | **Cấu hình hệ thống (System Config)** | UC-114, UC-115 | 5 | Lấy danh sách, tra cứu key, cập nhật config, chặn Customer Care (Admin only) |
| 4 | **Tranh chấp & Khiếu nại (Disputes)** | UC-96 → UC-104/109 | 12 | Queue SLA Risk, Queue cá nhân CC, Cân bằng tải CC, Gán/Chuyển đơn, Quyết định CC, Trừ 10 RS, Miễn phạt, Phán quyết cuối Admin, RBAC |
| 5 | **Phiếu hỗ trợ (Support Tickets)** | UC-105, UC-106, SCR-25/26 | 10 | Queue SLA Risk, Queue cá nhân, Nhận phiếu, Phản hồi khách hàng, Ghi chú nội bộ (`is_internal`), Đóng ticket (rationale >= 30 ký tự), RBAC |
| 6 | **Hàng đợi eKYC & Kỹ năng AI** | UC-47, Worker/EO eKYC | 6 | Quyền CC duyệt eKYC Worker, eKYC EO, thẩm định kỹ năng AI (+25 SP), Chặn Worker/EO |

---

## 2. HƯỚNG DẪN 2 CÁCH ĐƯA LÊN GOOGLE SHEETS TRONG 10 GIÂY

### Cách 1: Copy & Paste trực tiếp (Nhanh nhất - 5 giây)
1. Mở file [`TEST_CASES_ADMIN_CUSTOMER_CARE.tsv`](file:///c:/Users/quytr/Documents/GitHub/G16_CANi/docs/test-cases/TEST_CASES_ADMIN_CUSTOMER_CARE.tsv) bằng VS Code hoặc Notepad.
2. Nhấn `Ctrl + A` $\rightarrow$ `Ctrl + C` để sao chép toàn bộ nội dung.
3. Mở một trang tính Google Sheet mới $\rightarrow$ Click vào ô **A1** $\rightarrow$ Nhấn `Ctrl + V`. Toàn bộ 47 dòng và 12 cột sẽ tự động nhảy vào đúng từng ô rất đẹp!

### Cách 2: Import file CSV
1. Mở trang tính Google Sheet của bạn.
2. Chọn **Tệp (File) > Nhập (Import) > Tải lên (Upload)**.
3. Kéo thả file [`TEST_CASES_ADMIN_CUSTOMER_CARE.csv`](file:///c:/Users/quytr/Documents/GitHub/G16_CANi/docs/test-cases/TEST_CASES_ADMIN_CUSTOMER_CARE.csv) vào.
4. Chọn loại dấu phân cách là **Dấu phẩy (Comma)** $\rightarrow$ Bấm **Nhập dữ liệu**.

