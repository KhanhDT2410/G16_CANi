# CANi Platform Design System (Ocean Cyan-Blue & Native Vietnamese Typography)

> Single Source of Truth (SSOT) cho giao diện Web Portal CANi Event Staffing Platform.

---

## 1. Bản sắc Thương hiệu & Định hướng Nghệ thuật (Creative North Star)

* **Phong cách cốt lõi**: **Ocean Cyan-Blue (Công nghệ Sự kiện Trẻ trung, Uy tín & Rực rỡ)**.
* **Triết lý thị giác**:
  - Tối giản, tập trung vào trải nghiệm mượt mà của Ban Tổ Chức (EO).
  - Tận dụng hiệu ứng kính mờ (**Glassmorphism**) và ánh sáng tỏa màu Cyan băng ngọc (**Ambient Cyan Glow**).
  - Bảng màu chuyển dịch tràn đầy năng lượng từ **Cobalt Blue (`#5B86E5`)** sang **Vibrant Cyan (`#36D1DC`)**, loại bỏ hoàn toàn các tông màu tím.

---

## 2. Bảng Màu (Color Tokens)

### Primary & Gradients (Dải màu theo đúng chuẩn đề xuất)
* **Brand Gradient**: `linear-gradient(135deg, #5B86E5 0%, #36D1DC 100%)`
* **Brand Hover**: `linear-gradient(135deg, #4a75d4 0%, #2bc0cb 100%)`
* **Primary Main**: `#5B86E5` (Cobalt Royal Blue)
* **Primary Hover**: `#4a75d4`
* **Primary Subtle / Glow**: `rgba(91, 134, 229, 0.28)`
* **Primary Background**: `#eff6ff` (Blue-50)

### Feedback & Semantic Status
* **Success (Đã thanh toán / Đang diễn ra)**: `#10b981` (Nền `#ecfdf5`, viền `#a7f3d0`)
* **Warning (Bản nháp / Đang chờ duyệt)**: `#f59e0b` (Nền `#fffbeb`, viền `#fde68a`)
* **Danger (Tranh chấp / Quá hạn / Huỷ)**: `#ef4444` (Nền `#fef2f2`, viền `#fecaca`)
* **Info (Thông tin / Bản đồ)**: `#0284c7` (Nền `#f0f9ff`, viền `#bae6fd`)

### Surfaces & Backgrounds
* **Page Canvas**: `#f8fafc` kèm dải ánh sáng tỏa `radial-gradient(circle at top right, rgba(54, 209, 220, 0.12), transparent 60%)`
* **Card Surface**: `#ffffff` với viền `1px solid #e2e8f0`
* **Header / Glass Panel**: `rgba(255, 255, 255, 0.88)` + `backdrop-filter: blur(16px)`

---

## 3. Typography (Đặc trị lỗi phông dấu Tiếng Việt)

* **Phông Tiêu Đề (Display / Headlines)**: `Be Vietnam Pro` (Trọng số 600, 700, 800, 900)
  - Được thiết kế thủ công dành riêng cho tiếng Việt, hỗ trợ 100% các ký tự tổ hợp dấu đặc trưng (`ổ`, `ở`, `ữ`, `ẵ`, `ế`, `ặ`, `đ`).
  - Khắc phục triệt để hiện tượng vỡ phông / fallback font sang Times New Roman hoặc Segoe UI khi hiển thị tiếng Việt.
* **Phông Nội Dung & Dữ Liệu (Body & Labels)**: `Plus Jakarta Sans` (Trọng số 400, 500, 600, 700)
  - Cực kỳ rõ nét, cân bằng và hài hòa với `Be Vietnam Pro`.
* **Hierarchy**:
  - Page Title: `2rem`, weight 800, gradient chữ `linear-gradient(135deg, #0f172a 35%, #5B86E5 100%)`.
  - Section Headings: `1.3rem - 1.4rem`, weight 700.
  - KPI Metrics Value: `2rem`, weight 800.
  - Labels / Tabs: `0.85rem - 0.9rem`, weight 600, tracking `0.04em`.

---

## 4. Logo & Nhận diện Thương hiệu (Brandmark)

* **Biểu tượng SVG**: Hình khối Squircle (`rx=10`) nền dải màu `#5B86E5 $\rightarrow$ #36D1DC` kết hợp chữ **C** cách điệu uốn lượn trắng và **Tia sáng sự kiện (Spark Beacon)** điểm nhấn màu Cyan sáng rực.
* **Tagline**: Badge `EVENT PLATFORM` bo tròn góc với viền mảnh blue tinh tế.

---

## 5. Quy tắc Thiết kế Component (Component Rules)

1. **Buttons**:
   - `eo-btn-primary`: Dùng gradient thương hiệu `#5B86E5` $\rightarrow$ `#36D1DC`, bo góc vừa vặn (`8px`), đổ bóng phát sáng xanh cyan (`rgba(91, 134, 229, 0.28)`), chuyển động nâng nhẹ (`translateY(-1px)`) khi hover.
   - `eo-btn-secondary`: Nền trắng viền xám nhạt, đổi màu hover sang màu slate tinh giản.
2. **Metric Cards (Thẻ KPI)**:
   - Bo góc `16px`, hiệu ứng hover có đường viền gradient nổi lên ở đỉnh thẻ (`top accent line`).
   - Thẻ tiến độ chốt ca tích hợp vòng tròn tiến độ SVG (`circular progress ring`) hiển thị trực quan tỷ lệ lấp đầy.
3. **Tab Switcher**:
   - Dạng thanh điều hướng dạng kén (**Pill Segmented Control**) nền `#f1f5f9`, tab đang chọn nổi bật trên nền gradient `#5B86E5` $\rightarrow$ `#36D1DC` kèm đổ bóng mềm mại.
4. **Modals & Overlays**:
   - Nền tối mờ kính `rgba(15, 23, 42, 0.65)` với `backdrop-filter: blur(8px)`.
   - Khung modal bo góc lớn (`20px`), xuất hiện với animation `scaleIn`.

---

## 6. Kiến trúc Dashboard 3 Cột (3-Column Layout System)

* **Cột 1: Navigation Sidebar (Trái - 240px)**
  - Danh mục chức năng: `Tổng quan`, `Quản lý sự kiện`, `Tuyển dụng nhân sự`, `Thanh toán & lương`, `E-KYC doanh nghiệp`, `Báo cáo & thống kê`, `Cài đặt`.
  - Item active có vệt xanh gradient biên trái và nền cyan-blue nhẹ nhàng (`rgba(91, 134, 229, 0.1)`).
  - Thẻ quảng bá ở đáy: `Tổ chức sự kiện dễ dàng cùng CANi` với nút call-to-action `Tìm hiểu thêm →`.
* **Cột 2: Main Stream (Giữa - Linh hoạt 1fr)**
  - **Hero Welcome Banner**: Lời chào cá nhân hoá kèm tóm tắt trạng thái sự kiện trong ngày và nút `+ Tạo Sự Kiện Mới`.
  - **4 Thẻ KPI**: Tổng sự kiện, Nhân sự cần tuyển, Nhân sự đã chốt ca (kèm vòng tròn tiến độ), Dự toán chi phí lương.
  - **Pill Tabs Switcher**: Chuyển đổi giữa Sự kiện, Bảng thanh toán lương (Mục 11 SRS) và eKYC.
  - **Thanh tìm kiếm & lọc**: Tìm theo tên, lọc trạng thái (`Đang diễn ra`, `Bản nháp`), sắp xếp.
  - **Thẻ Sự Kiện Chuẩn Mockup**: Ảnh bìa sân khấu live, mã sự kiện có nút copy, địa chỉ gắn pin, chip thông số nhân sự - ca làm - thù lao dự kiến, các nút hành động `📍 Bản đồ`, `+ Thêm Ca`, `🚀 Kích Hoạt Matching`, `Quản lý`.
* **Cột 3: Utility Panel (Phải - 310px)**
  - **Interactive Mini Calendar**: Xem lịch tháng 10/2026, tô sáng ngày sự kiện diễn ra.
  - **Thông báo mới nhất**: Cập nhật trạng thái ứng tuyển, phê duyệt và thanh toán realtime.
  - **Thao tác nhanh (Quick Actions 2x2)**: Tạo sự kiện mới, Tuyển nhân sự, Khai báo lương, Định danh eKYC.

