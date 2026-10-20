---
name: clean-architecture
description: Enforce Clean Architecture, Lean Code (YAGNI, KISS), strict formatting, and layer separation across Web and Backend. Use whenever creating, modifying, or refactoring features to eliminate AI bloat, avoid overengineering, and maintain uniform codebase conventions.
---

# Clean Architecture & Lean Code Skill for AI Agents

> **Mục tiêu tối thượng:** Hướng dẫn AI Agent luôn viết code tinh gọn (Lean), chuẩn phân tầng (Clean Architecture), tuân thủ định dạng nghiêm ngặt và **triệt tiêu hoàn toàn code thừa (AI Bloat / Overengineering)**.

---

## 1. Nguyên Tắc Cốt Lõi: Chống "AI Bloat" & Overengineering

Trước khi viết bất kỳ dòng mã nào, AI phải tuân thủ 3 luật thép:
1. **YAGNI (You Aren't Gonna Need It):**
   - Chỉ giải quyết đúng yêu cầu hiện tại của bài toán.
   - **CẤM:** Không tự ý tạo các hàm "phòng hờ cho tương lai", không tự ý viết các generic abstraction phức tạp khi chưa có ít nhất 3 use-case cụ thể.
   - **CẤM:** Không tự tiện thêm thư viện mới vào `package.json` hoặc `pom.xml` nếu chưa được yêu cầu.
2. **KISS (Keep It Simple, Stupid):**
   - Đoạn code tốt nhất là đoạn code ngắn nhất, dễ đọc nhất và làm tròn bổn phận nhất.
   - Ưu tiên các pattern chuẩn của framework (React hooks, Spring services), không lạm dụng Design Pattern phức tạp khi giải pháp đơn giản đã đủ đáp ứng.
3. **DRY & Reuse First:**
   - Luôn kiểm tra các hàm tiện ích (`utils/`), components chung (`components/common/`), API client (`lib/axiosClient`) đã có trong dự án trước khi viết mới.

---

## 2. Chuẩn Phân Tầng Frontend (Clean Feature Architecture)

Mọi chức năng Frontend trong `src/features/[feature-name]/` phải tuân theo luồng phụ thuộc một chiều (Unidirectional Dependency Flow):

```
┌────────────────────────────────────────────────────────┐
│  Presentation Layer: UI Components (.tsx)              │
│  - Chỉ nhận props, hiển thị UI và emit events          │
│  - CẤM: Không gọi axios trực tiếp trong component     │
└───────────────────────────┬────────────────────────────┘
                            │ sử dụng
┌───────────────────────────▼────────────────────────────┐
│  Application / Logic Layer: Custom Hooks (.ts)         │
│  - Quản lý state (loading, error, data)                │
│  - Điều phối gọi API và xử lý dữ liệu                  │
└───────────────────────────┬────────────────────────────┘
                            │ sử dụng
┌───────────────────────────▼────────────────────────────┐
│  Infrastructure Layer: API Services (.ts)              │
│  - Sử dụng instance chung (axiosClient)                │
│  - Gắn type chuẩn vào request & response               │
└───────────────────────────┬────────────────────────────┘
                            │ sử dụng
┌───────────────────────────▼────────────────────────────┐
│  Domain / Contract Layer: Types & Interfaces (.ts)     │
│  - Định nghĩa DTO, Model, Enum nghiệp vụ              │
│  - CẤM TUYỆT ĐỐI kiểu `any`                            │
└────────────────────────────────────────────────────────┘
```

### Tiêu chuẩn Code Frontend:
- **Kích thước file:** Tối đa 150 - 200 dòng cho 1 component. Nếu dài hơn, bóc tách ngay thành các sub-components cùng thư mục.
- **Đặt tên (Naming Conventions):**
  - Component: `PascalCase.tsx` (ví dụ: `EventCard.tsx`, `ShiftAssignmentModal.tsx`).
  - Custom Hook: `camelCase.ts` bắt đầu bằng `use` (ví dụ: `useEventList.ts`).
  - Service / API: `camelCase.ts` (ví dụ: `eventApi.ts`).
  - Type / Interface: `PascalCase` (ví dụ: `EventItem`, `CreateEventRequest`).
- **Clean Code:**
  - Không viết comment sáo rỗng (ví dụ: `// Function to fetch data`). Tên hàm và tên biến phải tự giải thích (`fetchActiveEvents()`, `isSubmitting`).

---

## 3. Chuẩn Phân Tầng Backend (Spring Boot Clean 3-Layer)

Mọi chức năng Backend trong `backend/src/main/java/...` phải đảm bảo:

1. **Controller (Presentation Layer):**
   - Chỉ đảm nhận: Map HTTP Route, parse request body, validate đầu vào bằng annotation (`@Valid`, `@NotNull`, `@Min`...), trả về `ResponseEntity<ApiResponse<T>>`.
   - Cấm: Không viết logic nghiệp vụ (if/else xử lý tính toán) trong Controller.
2. **Service (Business / Use Case Layer):**
   - Đảm nhận toàn bộ nghiệp vụ, kiểm tra ràng buộc (Business Invariants), quản lý Transaction (`@Transactional`).
   - Cấm: Không nhận `HttpServletRequest` hay trả về Entity trực tiếp ra API; luôn map qua DTO (Data Transfer Object).
3. **Repository (Data Access Layer):**
   - Giao tiếp với Database qua Spring Data JPA.
   - Cấm: Không đưa logic nghiệp vụ vào Repository.
4. **Entity & DTO (Domain / Contract):**
   - Tách bạch Entity (ánh xạ bảng DB) và Request/Response DTOs. Không làm lộ mật khẩu, trường nhạy cảm hoặc cấu trúc DB thô ra ngoài.

---

## 4. Chuẩn Phân Tầng Mobile Flutter (Clean Feature Architecture)

Mọi chức năng Mobile trong `lib/features/[feature-name]/` phải tuân theo luồng phụ thuộc một chiều:

```
┌────────────────────────────────────────────────────────┐
│  Presentation Layer: Screens + Widgets (.dart)         │
│  - Compose widgets, đọc state từ Riverpod providers    │
│  - CẤM: Không gọi Dio hoặc Repository trực tiếp       │
└───────────────────────────┬────────────────────────────┘
                            │ watch / read
┌───────────────────────────▼────────────────────────────┐
│  State Layer: Riverpod Providers                       │
│  - Quản lý state (AsyncValue: loading, error, data)    │
│  - Điều phối gọi Repository, xử lý side-effects       │
└───────────────────────────┬────────────────────────────┘
                            │ sử dụng
┌───────────────────────────▼────────────────────────────┐
│  Data Layer: Repositories + API Services               │
│  - Sử dụng Dio client dùng chung (core/network/)      │
│  - Map JSON → typed Models, xử lý DioException        │
└───────────────────────────┬────────────────────────────┘
                            │ sử dụng
┌───────────────────────────▼────────────────────────────┐
│  Domain / Contract Layer: Models & DTOs                │
│  - Immutable data classes (fromJson / toJson)          │
│  - CẤM TUYỆT ĐỐI kiểu `dynamic`                      │
└────────────────────────────────────────────────────────┘
```

### Tiêu chuẩn Code Mobile:
- **Kích thước file:** Tối đa 200 dòng cho 1 widget/screen. Nếu dài hơn, bóc tách ngay thành sub-widgets.
- **Đặt tên (Naming Conventions):**
  - File: `snake_case.dart` (ví dụ: `event_card.dart`, `auth_repository.dart`).
  - Class / Widget: `PascalCase` (ví dụ: `EventCard`, `AuthRepository`).
  - Provider: `camelCase` (ví dụ: `eventListProvider`, `authStateProvider`).
  - Enum: `PascalCase` class + `camelCase` values (ví dụ: `EventStatus.draft`).
- **Clean Code:**
  - Dùng `const` constructor cho mọi widget khi có thể.
  - Dùng `Theme.of(context)` cho colors/text styles, không hardcode.
  - Import theo package path (`package:cani/...`), không dùng relative imports.

---

## 5. Checklist Bắt Buộc Trước Khi Bàn Giao Code (Verification Gate)

Trước khi gửi code cho người dùng, AI Agent phải tự rà soát theo checklist này:
- [ ] **Không code thừa:** Đã bỏ hết biến không dùng, import không dùng (`unused imports`), hàm rác?
- [ ] **Không `any`:** Toàn bộ dữ liệu truyền vào/ra đã có Type/Interface tường minh?
- [ ] **Đúng tầng:** Component có bị gọi API trực tiếp không? Controller có bị dính logic nghiệp vụ không?
- [ ] **Tái sử dụng:** Đã dùng Button/Input/Modal trong `@/components/common` thay vì viết thẻ HTML/CSS lạ chưa?
- [ ] **Format sạch:** Thụt lề chuẩn, không có khoảng trắng thừa, code tuân thủ ESLint/Prettier?
