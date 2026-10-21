# CANi Project - AI Agent & Developer Guidelines

This repository is a monorepo for **CANi - Event Staffing Platform**:
- `backend/`: Java 21, Spring Boot 3.5.1, PostgreSQL, Flyway, Maven.
- `web/`: React 18, JavaScript (JSX), Vite, React Router 7, Axios.
- `mobile/`: Flutter 3.x, Dart, Dio.
- `docs/`: Technical specifications & architectural guidelines.

---

## 1. Web Frontend Rules (`web/`)

### Architecture
- **Pattern:** Feature-Driven Architecture (Vertical Slice).
- **Location:** All business features MUST be placed in `src/features/[feature-name]/`.
- **Feature Structure:**
  - `api/`: Axios API functions only (using shared `apiClient` from `services/api`).
  - `components/`: UI components exclusive to this feature (`.jsx`).
  - `hooks/`: Business logic, state management, and side-effects (`.js`).
  - `types/`: Domain-specific constants, enums, and helpers (`.constants.js`).
  - `index.js`: Barrel export file (public interface of the feature).
- **Shared Code:** Reusable UI components belong in `src/components/common/`. Cross-feature utilities belong in `src/utils/`.

### JavaScript (JSX) & Code Standards
- **Clean React JSX:** Write clean, readable React components (`.jsx`). Use prop destructuring and meaningful variable names.
- **Component Size:** Keep components under 200 lines. Break large UI blocks into smaller subcomponents.
- **State Management:** Keep state as local as possible. Use custom hooks to isolate complex state logic from UI rendering.
- **Styling & Assets:** Follow existing CSS/styling conventions. Do not introduce ad-hoc CSS frameworks without explicit team agreement.

---

## 2. Backend Rules (`backend/`)

- **Architecture:** Standard Three-Layer Architecture (Controller -> Service -> Repository).
- **Database:** Flyway migrations must be used for all schema changes (`src/main/resources/db/migration/`). Do not modify existing applied migration scripts.
- **Security:** Spring Security conventions, proper DTO validation using Jakarta Validation (`@Valid`, `@NotNull`, etc.).
- **Responses:** Standard API response wrapper for consistency.

---

## 3. Mobile Rules (`mobile/`)

### Architecture
- **Pattern:** Feature-Driven Architecture (Vertical Slice), mirroring Web conventions.
- **Location:** All business features MUST be placed in `lib/features/[feature-name]/`.
- **Feature Structure:**
  - `data/`: Dio API clients + Repository implementations.
  - `models/`: Immutable data classes with `fromJson`/`toJson`.
  - `providers/`: Riverpod providers (state management).
  - `screens/`: Full-page screens (`ConsumerWidget`).
  - `widgets/`: Feature-specific UI widgets.
  - `[feature_name].dart`: Barrel export file (public interface of the feature).
- **Shared Code:** Reusable widgets belong in `lib/shared/widgets/`. Infrastructure code belongs in `lib/core/`.

### Dart & Code Standards
- **No `dynamic`:** Strict Dart typings are mandatory. Always type API responses and widget props.
- **Widget Size:** Keep widgets/screens under 200 lines. Extract sub-widgets when exceeded.
- **State Management:** Use Riverpod. Screens read state via `ref.watch()`, never call Dio directly.
- **Dependency Flow:** Screen → Provider → Repository → API (unidirectional, no shortcuts).
- **Styling:** Use `Theme.of(context)` for all colors and text styles. Do not hardcode values.

---

## 4. Team Consistency & Clean Architecture Skill

1. **Mandatory Skills:** Always apply the instructions defined in `.agents/skills/clean-architecture/SKILL.md`. For mobile work, also apply `.agents/skills/mobile-design/SKILL.md`.
2. **Lean Code (No AI Bloat):** Apply YAGNI & KISS. Do not write placeholder methods, speculative abstractions, or unused utility functions.
3. **Check Before Writing:** Before writing new helper functions, check `src/utils/` and `src/components/common/` (web) or `lib/core/utils/` and `lib/shared/widgets/` (mobile) to reuse existing code.
4. **Never Overwrite Configs:** Do not change build tools, tsconfig, vite plugins, pubspec.yaml, or root configs unless explicitly requested.
5. **Plan First:** Provide a concise breakdown of files to be modified/created before generating large blocks of code.

