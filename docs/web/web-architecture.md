# CANi Web - Chuẩn Kiến Trúc & Cấu Trúc Dự Án (Web Architecture)

> **Mục tiêu:** Xây dựng cấu trúc mã nguồn Frontend chuẩn hóa, mở rộng tốt (Scalable), giảm thiểu tối đa xung đột khi nhiều thành viên cùng phát triển song song bằng AI. Dự án sử dụng **React 18 + Vite thuần JavaScript (JSX)**.

---

## 1. Triết Lý Kiến Trúc: Feature-Driven Architecture (Vertical Slice)

Thay vì chia thư mục theo loại file truyền thống (tất cả components dồn vào một chỗ, tất cả hooks dồn vào một chỗ), dự án áp dụng mô hình **Feature-Driven (Hướng tính năng)**:
- **Tập trung hóa theo nghiệp vụ (Colocation):** Mọi thứ thuộc về một tính năng (UI, API calls, custom hooks, constants) được đóng gói gọn trong thư mục của tính năng đó.
- **Ranh giới rõ ràng (Encapsulation):** Mỗi feature là một module độc lập. Nếu cần tái sử dụng ra ngoài, chỉ export thông qua file `index.js` (Public API) của feature.

---

## 2. Chi Tiết Cấu Trúc Thư Mục `web/src/`

```text
web/src/
├── assets/                  # Ảnh, SVG, Icons, Fonts dùng chung toàn app
├── components/              # Design System / Shared UI Components (DÙNG CHUNG)
│   ├── common/              # Các UI nguyên tử: Button, Input, Modal, Table, Badge...
│   ├── feedback/            # LoadingSpinner, ErrorBoundary, Toast...
│   └── navigation/          # Navbar, Sidebar, Breadcrumb...
│
├── config/                  # Hằng số môi trường, cấu hình toàn cục (routes, roles...)
│
├── features/                # 🌟 TRÁI TIM CỦA ỨNG DỤNG (Chia theo nghiệp vụ)
│   ├── auth/                # Xác thực (Đăng nhập, Đăng ký, Quên mật khẩu...)
│   │   ├── api/             # authApi.js (gọi API login, refresh token...)
│   │   ├── components/      # LoginForm.jsx, RegisterForm.jsx...
│   │   ├── hooks/           # useAuth.js, usePermission.js...
│   │   ├── types/           # auth.constants.js (enums, roles, hằng số)
│   │   └── index.js         # Public API: export những gì bên ngoài được dùng
│   ├── events/              # Quản lý sự kiện (Dành cho Event Organizer - EO)
│   │   ├── api/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── types/
│   │   └── index.js
│   ├── staffing/            # Quản lý tuyển dụng, ca làm, phân công nhân sự
│   ├── attendance/          # Điểm danh, check-in/out, logs ca làm
│   └── admin/               # Quản lý hệ thống, duyệt tài khoản, thống kê
│
├── hooks/                   # Custom Hooks dùng chung toàn app (useDebounce, useLocalStorage...)
├── layouts/                 # Khung sườn trang (MainLayout.jsx, AuthLayout.jsx...)
├── lib/                     # Cấu hình thư viện bên thứ 3 (axiosClient.js, dayjs.js...)
├── routes/                  # Cấu hình Router (React Router 7), ProtectedRoutes, RoleGuard
├── services/                # Các tầng service hạ tầng (socket, storage, analytics...)
├── utils/                   # Hàm tiện ích thuần túy (formatCurrency, formatDate, validation...)
├── App.jsx                  # Root component chứa Provider & AppRouter
└── main.jsx                 # Entry point khởi tạo React DOM
```

---

## 3. Quy Tắc Tổ Chức Trong Từng Feature (`src/features/[feature-name]/`)

Mỗi khi nhận làm một tính năng mới, thành viên **chỉ làm việc bên trong thư mục feature của mình**:

```text
features/events/
├── api/
│   ├── eventApi.js          # Chứa các hàm gọi axios (getEvents, createEvent...)
│   └── eventEndpoints.js    # URL paths
├── components/
│   ├── EventCard.jsx        # UI hiển thị 1 thẻ sự kiện
│   ├── EventFilter.jsx      # Bộ lọc sự kiện
│   └── EventList.jsx        # Danh sách sự kiện
├── hooks/
│   ├── useEventList.js      # Quản lý state tải danh sách, phân trang, lọc
│   └── useEventForm.js      # Quản lý validation & submit form tạo sự kiện
├── types/
│   └── event.constants.js   # Hằng số EventStatus, categories...
└── index.js                 # Export EventList, useEventList, constants
```

### 🛑 Quy Tắc Vàng Khi Viết Code:
1. **Không can thiệp file nội bộ của feature khác:** Feature `staffing` muốn lấy danh sách Event thì chỉ được import từ `@/features/events`, không đi sâu vào `@/features/events/components/EventCard.jsx`.
2. **Tách biệt UI và Logic:**
   - Component (`.jsx`): Chỉ quan tâm render giao diện, nhận props hoặc gọi hàm từ custom hook.
   - Custom Hook (`use...js`): Chịu trách nhiệm quản lý state (`useState`, `useEffect`), gọi API, bắt lỗi.
   - API Service (`api.js`): Chỉ chịu trách nhiệm kết nối mạng qua `axiosClient`, trả về dữ liệu cho hook.
3. **Giới hạn kích thước file:** Không viết file component vượt quá **200 dòng**. Nếu dài hơn, lập tức tách thành sub-components nhỏ hơn.
