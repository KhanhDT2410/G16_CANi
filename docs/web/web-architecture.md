# CANi Web - Chuẩn Kiến Trúc & Cấu Trúc Dự Án (Web Architecture)

> **Mục tiêu:** Xây dựng cấu trúc mã nguồn Frontend chuẩn hóa, mở rộng tốt (Scalable), giảm thiểu tối đa xung đột khi nhiều thành viên cùng phát triển song song bằng AI.

---

## 1. Triết Lý Kiến Trúc: Feature-Driven Architecture (Vertical Slice)

Thay vì chia thư mục theo loại file truyền thống (tất cả components dồn vào một chỗ, tất cả hooks dồn vào một chỗ), dự án áp dụng mô hình **Feature-Driven (Hướng tính năng)**:
- **Tập trung hóa theo nghiệp vụ (Colocation):** Mọi thứ thuộc về một tính năng (UI, API calls, custom hooks, types) được đóng gói gọn trong thư mục của tính năng đó.
- **Ranh giới rõ ràng (Encapsulation):** Mỗi feature là một module độc lập. Nếu cần tái sử dụng ra ngoài, chỉ export thông qua file `index.ts` (Public API) của feature.

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
│   │   ├── api/             # authApi.ts (gọi API login, refresh token...)
│   │   ├── components/      # LoginForm.tsx, RegisterForm.tsx...
│   │   ├── hooks/           # useAuth.ts, usePermission.ts...
│   │   ├── types/           # auth.types.ts
│   │   └── index.ts         # Public API: export những gì bên ngoài được dùng
│   ├── events/              # Quản lý sự kiện (Dành cho Event Organizer - EO)
│   │   ├── api/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── types/
│   │   └── index.ts
│   ├── staffing/            # Quản lý tuyển dụng, ca làm, phân công nhân sự
│   ├── attendance/          # Điểm danh, check-in/out, logs ca làm
│   └── admin/               # Quản lý hệ thống, duyệt tài khoản, thống kê
│
├── hooks/                   # Custom Hooks dùng chung toàn app (useDebounce, useLocalStorage...)
├── layouts/                 # Khung sườn trang (MainLayout, AuthLayout, DashboardLayout...)
├── lib/                     # Cấu hình thư viện bên thứ 3 (axiosClient.ts, dayjs.ts...)
├── routes/                  # Cấu hình Router (React Router 7), ProtectedRoutes, RoleGuard
├── services/                # Các tầng service hạ tầng (socket, storage, analytics...)
├── types/                   # Type definitions dùng chung toàn hệ thống (ApiResponse, Paginated...)
├── utils/                   # Hàm tiện ích thuần túy (formatCurrency, formatDate, validation...)
├── App.tsx                  # Root component chứa Provider & AppRouter
└── main.tsx                 # Entry point khởi tạo React DOM
```

---

## 3. Quy Tắc Tổ Chức Trong Từng Feature (`src/features/[feature-name]/`)

Mỗi khi nhận làm một tính năng mới, thành viên **chỉ làm việc bên trong thư mục feature của mình**:

```text
features/events/
├── api/
│   ├── eventApi.ts          # Chứa các hàm gọi axios (getEvents, createEvent...)
│   └── eventEndpoints.ts    # URL paths
├── components/
│   ├── EventCard.tsx        # UI hiển thị 1 thẻ sự kiện
│   ├── EventFilter.tsx      # Bộ lọc sự kiện
│   └── EventList.tsx        # Danh sách sự kiện
├── hooks/
│   ├── useEventList.ts      # Quản lý state tải danh sách, phân trang, lọc
│   └── useEventForm.ts      # Quản lý validation & submit form tạo sự kiện
├── types/
│   └── index.ts             # Interface Event, EventStatus, CreateEventDto...
└── index.ts                 # Export EventList, useEventList, Event interface
```

### 🛑 Quy Tắc Vàng Khi Viết Code:
1. **Không can thiệp file nội bộ của feature khác:** Feature `staffing` muốn lấy danh sách Event thì chỉ được import từ `@/features/events`, không đi sâu vào `@/features/events/components/EventCard.tsx`.
2. **Tách biệt UI và Logic:**
   - Component (`.tsx`): Chỉ quan tâm render giao diện, nhận props hoặc gọi hàm từ custom hook.
   - Custom Hook (`use...ts`): Chịu trách nhiệm quản lý state (`useState`, `useEffect`), gọi API, bắt lỗi.
   - API Service (`api.ts`): Chỉ chịu trách nhiệm kết nối mạng qua `axiosClient`, trả về dữ liệu chuẩn TypeScript.
3. **Giới hạn kích thước file:** Không viết file component vượt quá **200 dòng**. Nếu dài hơn, lập tức tách thành sub-components nhỏ hơn.
