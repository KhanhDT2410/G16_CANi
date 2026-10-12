# CANi - Event Staffing Platform

Monorepo for the CANi capstone project - a short-term event staffing and workforce coordination platform.

## Project Structure

```
G16_CANi/
├── backend/          # Java 21 + Spring Boot (Three-Layer Architecture)
├── web/              # React + TypeScript + Vite
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
- **TypeScript**
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

Access at: `http://localhost:3000`

**Environment configuration:** Copy `.env.example` to `.env` and adjust `VITE_API_BASE_URL`.

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

> **Note:** This repository is in the initial scaffolding phase. No business features have been implemented yet.

- Backend: Spring Boot application initialized with minimal security config and health endpoint
- Web: React + TypeScript app with placeholder routes (`/eo`, `/admin`)
- Mobile: Flutter app with placeholder home screen

## Architecture

### Backend (Three-Layer Architecture)
```
Controller → Service → Repository
```

### API Communication
- Web and Mobile clients communicate with Backend via REST APIs
- No direct database access from clients
- Future authentication via Spring Security (JWT-based)

## Documentation

Project documentation is located in the `docs/` directory:
- `api/` - API specifications
- `architecture/` - Architecture decisions
- `database/` - Database design
- `diagrams/` - System diagrams
- `requirements/` - Project requirements
