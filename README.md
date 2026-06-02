# AI-Powered Interactive Chinese Learning Platform

MandarinSpark is a Final Year Project full-stack web application for young Chinese learners in Malaysia. It provides role-based learning experiences for students, content and class management for teachers, and basic platform management for administrators.

## Main Features

| Role | Features |
| --- | --- |
| Student | Course browsing, learning dashboard, practice activities, quizzes, rewards, profile, AI learning helper |
| Teacher | Dashboard, class management, content management, student progress, feedback, profile |
| Admin | User management, teacher verification, content review, activity library, finance/settings overview |

## Tech Stack

| Layer | Technology |
| --- | --- |
| Frontend | Vue 3, TypeScript, Pinia, Vue Router, Vue I18n, Vite |
| Backend | Java 21, Spring Boot 3, Spring Security, Spring Data JPA |
| Database | MySQL 8.4, Flyway |
| AI | Configurable OpenAI-compatible/DeepSeek provider with local no-real-provider mode |

## Project Structure

```text
.
├── backend/      Spring Boot API, database migration, backend tests
├── frontend/     Vue application, frontend tests, static assets
└── README.md     FYP setup and run guide
```

## Prerequisites

- Java 21
- Node.js 22
- Docker Desktop, or an installed MySQL 8 database

## Backend Setup

For quick FYP demonstration without Docker/MySQL, run the backend with the in-memory demo database:

```powershell
cd backend
$env:DEFAULT_ADMIN_EMAIL = "admin@example.com"
$env:DEFAULT_ADMIN_PASSWORD = "Admin12345"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

This creates a temporary H2 database every time the backend starts. Use it for presentation and UI testing, not for saving long-term data.

For normal local development with MySQL, use Docker Compose.

Make sure Docker Desktop is running before starting the local database.

Start the local MySQL database:

```powershell
cd backend
docker compose up -d
```

Run the Spring Boot backend:

```powershell
cd backend
$env:DB_USERNAME = "aichinese_app"
$env:DB_PASSWORD = "replace-with-a-strong-application-db-password"
$env:JWT_SECRET = "local-development-secret-with-at-least-32-characters"
$env:DEFAULT_ADMIN_EMAIL = "admin@example.com"
$env:DEFAULT_ADMIN_PASSWORD = "Admin12345"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Backend local URLs:

| Purpose | URL |
| --- | --- |
| API base | `http://localhost:8080/api/v1` |
| Health check | `http://localhost:8080/api/v1/health` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |

## Frontend Setup

Install dependencies and start the Vite dev server:

```powershell
cd frontend
npm ci
npm run dev
```

Frontend URL:

```text
http://localhost:5174
```

The frontend dev server proxies `/api/v1` requests to the backend at `http://127.0.0.1:8080`, so no frontend `.env.local` file is required for normal local development.

## FYP Demo Mode

If you need to demonstrate the interface without starting the backend/database, enable the frontend read-only preview mode:

```powershell
cd frontend
Copy-Item .env.example .env.local
```

Then edit `frontend/.env.local`:

```text
VITE_USE_DEMO_DATA=true
VITE_ENABLE_PREVIEW_MODE=true
```

Run `npm run dev`, open `http://localhost:5174/auth`, and use the Preview Student, Preview Teacher, or Preview Admin buttons.

## Useful Commands

Frontend:

```powershell
cd frontend
npm run lint
npm run test:unit
npm run typecheck
npm run build
```

Backend:

```powershell
cd backend
.\mvnw.cmd clean test
```

## Environment Notes

- `backend/.env.example` contains the main local environment variable template.
- Real AI, Stripe, and SMTP credentials are optional for FYP demonstration.
- By default, the backend can run with no real AI provider enabled.
- Do not commit personal `.env.local`, upload, build, cache, or dependency folders.
