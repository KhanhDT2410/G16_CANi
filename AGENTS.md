# CANi Project - AI Agent & Developer Guidelines

This repository is a monorepo for **CANi - Event Staffing Platform**:
- `backend/`: Java 21, Spring Boot 3.5.1, PostgreSQL, Flyway, Maven.
- `web/`: React 18, TypeScript, Vite, React Router 7, Axios.
- `mobile/`: Flutter 3.x, Dart, Dio.
- `docs/`: Technical specifications & architectural guidelines.

---

## 1. Web Frontend Rules (`web/`)

### Architecture
- **Pattern:** Feature-Driven Architecture (Vertical Slice).
- **Location:** All business features MUST be placed in `src/features/[feature-name]/`.
- **Feature Structure:**
  - `api/`: Axios API functions only (using shared `axiosClient`).
  - `components/`: UI components exclusive to this feature.
  - `hooks/`: Business logic, state management, and side-effects.
  - `types/`: Domain-specific TypeScript interfaces/types.
  - `index.ts`: Barrel export file (public interface of the feature).
- **Shared Code:** Reusable UI components belong in `src/components/common/`. Cross-feature utilities belong in `src/utils/`.

### TypeScript & Code Standards
- **No `any`:** Strict TypeScript typings are mandatory. Always type API responses and component props.
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

## 3. Team Consistency & Clean Architecture Skill

1. **Mandatory Skill:** Always apply the instructions defined in `.agents/skills/clean-architecture/SKILL.md`.
2. **Lean Code (No AI Bloat):** Apply YAGNI & KISS. Do not write placeholder methods, speculative abstractions, or unused utility functions.
3. **Check Before Writing:** Before writing new helper functions, check `src/utils/` and `src/components/common/` to reuse existing code.
4. **Never Overwrite Configs:** Do not change build tools, tsconfig, vite plugins, or root configs unless explicitly requested.
5. **Plan First:** Provide a concise breakdown of files to be modified/created before generating large blocks of code.

