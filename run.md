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

---

### Phím tắt hữu ích khi đang chạy Flutter:
- Nhấn `r`: Hot Reload (cập nhật code giao diện tức thì).
- Nhấn `R`: Hot Restart (khởi động lại ứng dụng).
- Nhấn `q`: Thoát ứng dụng.
