# CANi - Event Staffing Platform

Monorepo for the CANi capstone project - a short-term event staffing and workforce coordination platform.

## Project Structure

```
G16_CANi/
├── backend/          # Java 21 + Spring Boot (Three-Layer Architecture)
├── web/              # React + JavaScript (JSX) + Vite
├── mobile/           # Flutter + Dart
└── docs/             # Project documentation
```

## Technology Stack

### Backend
- **Java 21**
- **Spring Boot 3.5.1**
- **Maven** (with Maven Wrapper)
- **PostgreSQL** (via environment variables)
- **Flyway** for database migrations
- **Spring Security** (initialized, minimal config)

### Web Frontend
- **React 18**
- **JavaScript (JSX)**
- **Vite**
- **React Router 7**
- **Axios**

### Mobile App
- **Flutter 3.44.6**
- **Dart 3.12.2**
- **Dio** for HTTP client

## Prerequisites

- Java 21 or higher
- Node.js 18+ (for web development)
- Flutter SDK 3.x (for mobile development)
- PostgreSQL database (for backend)
- Maven 3.9+ (or use included Maven Wrapper)

## Running the Applications

### Backend

```bash
cd backend

# Download dependencies and run
./mvnw spring-boot:run

# Or compile first
./mvnw compile
./mvnw spring-boot:run
```

**Health endpoint:** `GET http://localhost:8080/api/health`

**Environment variables:**
- `DB_URL` - PostgreSQL connection URL (default: `jdbc:postgresql://localhost:5432/cani`)
- `DB_USERNAME` - Database username (default: `postgres`)
- `DB_PASSWORD` - Database password (default: `postgres`)
- `SERVER_PORT` - Server port (default: `8080`)

### Web Frontend

```bash
cd web

# Install dependencies
npm install

# Run development server
npm run dev
```

Access at: `http://localhost:5173`

**Environment configuration:** Copy `.env.example` to `.env` and adjust `VITE_API_BASE_URL`. Set `VITE_DEMO=true` to enable offline mock mode.

### Mobile App

```bash
cd mobile

# Get dependencies
flutter pub get

# Run on connected device/emulator
flutter run
```

**Android Emulator:** The app uses `http://10.0.2.2:8080` to connect to the host machine's backend.

**Physical Device:** Update `lib/services/api_service.dart` with your host machine's LAN IP address.

## Validation Commands

### Backend
```bash
cd backend
./mvnw --version                    # Check Maven Wrapper
./mvnw compile                      # Compile the project
./mvnw test                         # Run tests
```

### Web
```bash
cd web
npm install                          # Install dependencies
npm run build                       # Production build
```

### Mobile
```bash
cd mobile
flutter pub get                     # Get dependencies
flutter analyze                    # Analyze code
flutter test                       # Run tests
```

## Current Status

> **Note:** Core architecture and business modules have been fully implemented across all 3 platforms.

- **Backend (Spring Boot 3.5.1 / Java 21):**
  - Authentication & RBAC (Supabase GoTrue integration + ES256 JWKS verification, offline H2 dev profile)
  - Event & Shift Management (Event lifecycle, shift configuration, wage matrix recommendations)
  - Matching & Dispatch Engine (Fairness boost, priority queue, emergency replacement)
  - Attendance & Check-in/out (Dynamic vector QR generation, real-time live attendance monitoring)
  - Direct Payment Settlement (Non-Escrow direct payment declaration, worker confirmation, dispute queue)
  - Skill Verification & Rating (Skill taxonomy, AI eKYC integration, mutual 5-criteria rating)
- **Web Portal (React 18 / Vite 5):**
  - Event Organizer Portal (`/eo`): Event & shift orchestration, live attendance, QR modal, direct wage settlement
  - Customer Care Desk (`/customer-care`): Dispute investigation & resolution, user support
  - Admin Console (`/admin`): User CRUD, system parameters, audit log
  - Route security guarded by `<RequireRole>` (`useAuthGuard`)
- **Mobile Partner App (Flutter 3.x / Riverpod):**
  - Job Radar & interactive venue map
  - Shift assignment tracking, dynamic QR attendance check-in/out, checklist progress
  - Weekly recurring & specific date availability configuration
  - Working preferences & operating radius slider
  - Non-escrow payment confirmation & dispute submission
  - 4-tab clean bottom navigation (Bản đồ, Lịch ca, Kỹ năng, Hồ sơ & Cài đặt)

## Architecture

### Backend (Three-Layer Architecture)
```
Controller → Service → Repository
```

### API Communication
- Web and Mobile clients communicate with Backend via REST APIs
- No direct database access from clients
- Authentication via Spring Security & Supabase JWT (ES256 JWKS)

## Documentation

Project documentation is located in the `docs/` directory:
- `docs/api/` - REST API specifications & Postman auth guide (`auth-postman-guide.md`)
- `docs/architecture/` - Architecture decisions & system layers guide
- `docs/database/` - Database design & schema DDL (`DBDesign.txt`)
- `docs/diagrams/` - System diagrams & Use Case models
- `docs/requirements/` - Project requirements & SRS index
- `docs/web/` - Web architecture & feature golden template guide (`feature-template-guide.md`)
- `docs/ai-team-standards.md` - AI Engineering constitution & code quality rules
