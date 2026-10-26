# Hướng dẫn chạy Hệ thống CANi (Terminal Quickstart)

> Mở mỗi dịch vụ trên một tab Terminal (PowerShell hoặc Command Prompt) riêng biệt.

---

## 1. Backend (Spring Boot - Port 8080)

```powershell
cd backend
```

- **Cách 1: Chạy Local Offline (H2 Database - Khuyên dùng để test)**
  *Tự động seed dữ liệu mẫu, không cần kết nối mạng hoặc cloud database.*
  ```powershell
  # Đảm bảo dùng Java 21+ (Ví dụ nếu máy đang mặc định Java 17):
  # $env:JAVA_HOME = "C:\Users\DELL\.jdks\corretto-24.0.2"
  .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"
  ```

- **Cách 2: Chạy kết nối Cloud (Supabase PostgreSQL)**
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```

*Tài khoản Worker có sẵn: `worker@cani.vn` | Mật khẩu: `123456`*

---

## 2. Frontend Web (React / Vite - Port 5173)

```powershell
cd web
npm install   # Chỉ cần chạy lần đầu
npm run dev
```
Truy cập: [http://localhost:5173](http://localhost:5173)

---

## 3. Mobile App (Flutter)

```powershell
cd mobile
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
