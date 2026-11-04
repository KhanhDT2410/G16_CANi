# Hướng dẫn chạy Hệ thống CANi (Terminal Quickstart)

> Mở mỗi dịch vụ trên một tab Terminal (PowerShell hoặc Command Prompt) riêng biệt.

---

## 1. Backend (Spring Boot - Port 8080)

```powershell
cd Code/backend
```

- **Cách 1: Chạy Local Offline (H2 Database - Khuyên dùng để test)**
  *Tự động seed dữ liệu mẫu, không cần kết nối mạng hoặc cloud database.*
  ```powershell
  .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"
  ```

- **Cách 2: Chạy kết nối Cloud (Supabase PostgreSQL)**
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```

*Tài khoản demo có sẵn (Dùng chung mật khẩu: `Password123!`):*
- **Worker (Mobile):** `worker@cani.vn` | Mật khẩu: `Password123!`
- **Event Organizer (Web):** `eo@cani.vn` | Mật khẩu: `Password123!`
- **Customer Care (Web):** `cskh@cani.vn` | Mật khẩu: `Password123!`
- **Administrator (Web):** `admin@cani.vn` | Mật khẩu: `Password123!`

---

## 2. Frontend Web (React / Vite - Port 5173)

```powershell
cd Code/web
npm install   # Chỉ cần chạy lần đầu
npm run dev
```
Truy cập: [http://localhost:5173](http://localhost:5173)

> **Lưu ý cấu hình biến môi trường (`web/.env`):**
> - `VITE_DEMO=true`: Bật chế độ Demo Mock Data (chạy offline độc lập không cần backend).
> - `VITE_DEMO=false` (mặc định): Gọi trực tiếp Backend REST API thực tế.

---

## 3. Mobile App (Flutter)

```powershell
cd Code/mobile
```

- **Chạy trực tiếp trên Windows (Tỷ lệ khung điện thoại 420x860):**
  ```powershell
  D:\Code\flutter\bin\flutter.bat run -d windows
  ```
  *(Hoặc `flutter run -d windows` nếu đã cấu hình biến môi trường PATH)*

- **Chạy trên trình duyệt Web (Chrome):**
  ```powershell
  D:\Code\flutter\bin\flutter.bat run -d chrome
  ```

- **Chạy trên thiết bị thật Android (Cắm cáp USB):**
  1. Sử dụng cáp truyền dữ liệu (Data cable), cắm điện thoại vào máy tính và đảm bảo đã bật **Gỡ lỗi qua USB (USB Debugging)** & **Cài đặt qua USB (Install via USB)** trong *Tùy chọn nhà phát triển (Developer Options)*.
  2. Mở cổng kết nối đường hầm mạng tới Backend Spring Boot trên máy tính:
     ```powershell
     & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8080 tcp:8080
     ```
     *(Lệnh này giúp ứng dụng trên điện thoại gọi `http://localhost:8080` đi thẳng vào Backend trên PC mà không cần chung mạng Wi-Fi hay lo tường lửa)*
  3. Kiểm tra thiết bị đã sẵn sàng:
     ```powershell
     flutter devices
     ```
  4. Khởi chạy ứng dụng lên điện thoại:
     ```powershell
     flutter run -d android
     ```
     *(Nếu có nhiều thiết bị, chỉ định mã cụ thể: `flutter run -d <device_id>`)*

---

### Phím tắt hữu ích khi đang chạy Flutter:
- Nhấn `r`: Hot Reload (cập nhật code giao diện/logic tức thì trong ~1s mà không mất dữ liệu/state).
- Nhấn `R`: Hot Restart (khởi động lại toàn bộ app và reset state).
- Nhấn `d`: Detach (ngắt kết nối terminal để giải phóng máy tính nhưng ứng dụng vẫn tiếp tục chạy trên điện thoại).
- Nhấn `q`: Thoát ứng dụng.
