# CANi Mobile - Chuẩn Kiến Trúc & Cấu Trúc Dự Án (Mobile Architecture)

> **Mục tiêu:** Xây dựng cấu trúc mã nguồn Flutter chuẩn hóa, mở rộng tốt (Scalable), giảm thiểu xung đột khi nhiều thành viên cùng phát triển song song, đồng bộ triết lý kiến trúc với Web Frontend.

---

## 1. Triết Lý Kiến Trúc: Feature-Driven Architecture (Vertical Slice)

Tương tự phần Web, dự án Mobile áp dụng mô hình **Feature-Driven (Hướng tính năng)**:
- **Tập trung hóa theo nghiệp vụ (Colocation):** Mọi thứ thuộc về một tính năng (UI screens, data sources, models, state management) được đóng gói gọn trong thư mục của tính năng đó.
- **Ranh giới rõ ràng (Encapsulation):** Mỗi feature là một module độc lập. Nếu cần tái sử dụng ra ngoài, chỉ export thông qua file barrel (`index.dart` hoặc `feature_name.dart`).
- **State Management:** Sử dụng **Riverpod** làm giải pháp quản lý trạng thái xuyên suốt dự án (type-safe, testable, compile-time safe).

---

## 2. Chi Tiết Cấu Trúc Thư Mục `mobile/lib/`

```text
mobile/lib/
├── app/                        # Cấu hình gốc ứng dụng
│   ├── app.dart                # Root Widget (MaterialApp, ProviderScope, Router)
│   ├── router.dart             # Cấu hình GoRouter / route declarations
│   └── theme.dart              # ThemeData, ColorScheme, TextTheme toàn app
│
├── core/                       # Hạ tầng dùng chung toàn app (KHÔNG chứa UI)
│   ├── constants/              # Hằng số: API URLs, app-wide enums, config keys
│   ├── network/                # Dio client, interceptors (auth, logging, retry)
│   │   ├── dio_client.dart     # Singleton Dio instance cấu hình base URL, timeout
│   │   └── auth_interceptor.dart
│   ├── storage/                # Local storage wrappers (SharedPreferences, SecureStorage)
│   └── utils/                  # Hàm tiện ích thuần túy (formatDate, formatCurrency, validators)
│
├── shared/                     # UI Components & Widgets dùng chung (Design System)
│   ├── widgets/                # Atomic widgets: AppButton, AppTextField, AppCard, LoadingIndicator
│   ├── layouts/                # Scaffold wrappers, responsive layouts
│   └── extensions/             # Dart extensions (BuildContext, String, DateTime)
│
├── features/                   # 🌟 TRÁI TIM CỦA ỨNG DỤNG (Chia theo nghiệp vụ)
│   ├── auth/                   # Xác thực (Đăng nhập, Đăng ký, Quên mật khẩu)
│   │   ├── data/               # Data sources & Repository implementations
│   │   │   ├── auth_api.dart         # Gọi Dio (login, register, refresh token)
│   │   │   └── auth_repository.dart  # Repository pattern (abstract → impl)
│   │   ├── models/             # Data classes / DTOs (LoginRequest, UserProfile)
│   │   ├── providers/          # Riverpod providers (authProvider, userProvider)
│   │   ├── screens/            # Full-page screens (LoginScreen, RegisterScreen)
│   │   ├── widgets/            # UI widgets nội bộ feature (LoginForm, OtpInput)
│   │   └── auth.dart           # Barrel export (Public API của feature)
│   │
│   ├── events/                 # Quản lý sự kiện
│   │   ├── data/
│   │   ├── models/
│   │   ├── providers/
│   │   ├── screens/
│   │   ├── widgets/
│   │   └── events.dart
│   │
│   ├── staffing/               # Quản lý ca làm, phân công nhân sự
│   ├── attendance/             # Điểm danh, check-in/out (QR, GPS)
│   └── profile/                # Hồ sơ cá nhân, cài đặt
│
├── main.dart                   # Entry point: runApp() + ProviderScope
└── l10n/                       # Localization (nếu cần đa ngôn ngữ)
```

---

## 3. Quy Tắc Tổ Chức Trong Từng Feature (`lib/features/[feature-name]/`)

Mỗi khi phát triển một tính năng mới, thành viên **chỉ làm việc bên trong thư mục feature của mình**:

```text
features/events/
├── data/
│   ├── event_api.dart          # Gọi Dio API (getEvents, createEvent...)
│   └── event_repository.dart   # Repository: xử lý mapping, caching, error
├── models/
│   ├── event_item.dart         # Data class (freezed hoặc manual)
│   └── create_event_dto.dart   # DTO cho request tạo sự kiện
├── providers/
│   ├── event_list_provider.dart    # AsyncNotifierProvider quản lý danh sách
│   └── event_detail_provider.dart  # FutureProvider cho chi tiết
├── screens/
│   ├── event_list_screen.dart  # Full-page: danh sách sự kiện
│   └── event_detail_screen.dart
├── widgets/
│   ├── event_card.dart         # Widget hiển thị 1 card sự kiện
│   └── event_filter_bar.dart   # Widget bộ lọc
└── events.dart                 # Barrel export
```

### 🛑 Quy Tắc Vàng Khi Viết Code:
1. **Không can thiệp file nội bộ của feature khác:** Feature `staffing` muốn lấy dữ liệu Event thì chỉ được import từ `package:cani/features/events/events.dart`, không đi sâu vào `features/events/widgets/event_card.dart`.
2. **Tách biệt UI và Logic (Strict Separation):**
   - **Screen (`.dart`):** Chỉ tổ hợp (compose) các widgets và đọc state từ Riverpod providers. Không gọi Dio trực tiếp.
   - **Provider (`providers/`):** Chịu trách nhiệm quản lý state, gọi repository, xử lý loading/error.
   - **Repository (`data/`):** Giao tiếp với backend API qua Dio client dùng chung, trả về domain models.
   - **Model (`models/`):** Immutable data classes với `fromJson`/`toJson`. Không chứa business logic.
3. **Giới hạn kích thước file:** Không viết file widget/screen vượt quá **200 dòng**. Nếu dài hơn, tách thành sub-widgets.
4. **Naming Conventions (Dart/Flutter):**
   - File: `snake_case.dart` (ví dụ: `event_card.dart`, `auth_repository.dart`).
   - Class: `PascalCase` (ví dụ: `EventCard`, `AuthRepository`).
   - Provider: `camelCase` (ví dụ: `eventListProvider`, `authStateProvider`).
   - Private members: prefix `_` (ví dụ: `_fetchEvents()`).

---

## 4. Stack Công Nghệ Chuẩn

| Thành phần | Công nghệ | Ghi chú |
|:---|:---|:---|
| Framework | Flutter 3.x + Dart | Material 3, Material Design |
| State Management | Riverpod (flutter_riverpod) | Type-safe, testable |
| HTTP Client | Dio | Interceptors, retry, cancel token |
| Routing | GoRouter (go_router) | Declarative, deep linking |
| Local Storage | SharedPreferences / flutter_secure_storage | Token, preferences |
| JSON Serialization | json_serializable + json_annotation | Code generation |
| Immutable Models | freezed (tùy chọn) | Copyable, equatable |
| Image Loading | cached_network_image | Cache + placeholder |

---

## 5. Luồng Phụ Thuộc Một Chiều (Unidirectional Dependency Flow)

```
┌─────────────────────────────────────────────────────────┐
│  Presentation Layer: Screens + Widgets (.dart)          │
│  - Compose widgets, đọc state từ Riverpod providers     │
│  - CẤM: Không gọi Dio hoặc Repository trực tiếp        │
└───────────────────────────┬─────────────────────────────┘
                            │ watch / read
┌───────────────────────────▼─────────────────────────────┐
│  State Layer: Riverpod Providers                        │
│  - Quản lý state (AsyncValue, loading, error, data)     │
│  - Điều phối gọi Repository, xử lý side-effects        │
└───────────────────────────┬─────────────────────────────┘
                            │ sử dụng
┌───────────────────────────▼─────────────────────────────┐
│  Data Layer: Repositories + API Services                │
│  - Sử dụng Dio client dùng chung (core/network/)       │
│  - Map JSON → Domain Models, xử lý error               │
└───────────────────────────┬─────────────────────────────┘
                            │ sử dụng
┌───────────────────────────▼─────────────────────────────┐
│  Domain / Contract Layer: Models & DTOs                 │
│  - Immutable data classes (fromJson / toJson)           │
│  - CẤM TUYỆT ĐỐI kiểu `dynamic` thay cho typed models │
└─────────────────────────────────────────────────────────┘
```
