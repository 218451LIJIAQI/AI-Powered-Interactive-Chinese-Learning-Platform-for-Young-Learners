# AI-Powered Interactive Chinese Learning Platform for Young Learners

## Project Overview
This project is a full-stack final-year-project web application for young learners aged 7-12. It is designed as an AI-centered, interactive, and gamified Chinese learning platform for the Malaysian context, with support for student, teacher, and admin roles.

Phase 1 established the monorepo scaffold. Phase 2 added the backend database foundation with JPA entities, repositories, seed data, and MySQL-ready configuration. Phase 3 now adds real authentication, JWT-based access control, role-protected backend routes, and frontend login and registration flows.

## Tech Stack
- Frontend: Vue 3, Vite, TypeScript, Element Plus, Vue Router, Pinia, Axios, Vue I18n, npm
- Backend: Java 21, Spring Boot 3.5.14, Spring Security, JWT, Spring Data JPA, Bean Validation, Maven Wrapper
- Database: MySQL 8.4 LTS
- Tooling: Docker Compose

## Monorepo Structure
```text
ai-chinese-learning-platform/
├── frontend/
├── backend/
├── docs/
├── docker-compose.yml
├── README.md
└── .gitignore
```

## Current Status
Completed through Phase 3:
- Monorepo scaffold created
- Vue frontend created with required packages and folder structure
- Public, student, teacher, and admin layouts added
- Vue Router, Pinia, Axios, Element Plus, and Vue I18n configured
- Spring Boot backend scaffolded with `com.example.aichinese`
- `/api/v1/health` endpoint added
- Database entities, repositories, enums, and seed data added
- Real student registration and teacher registration added
- Teacher registration now creates `PENDING` teacher profiles by default
- Seeded admin account can log in using environment variables
- JWT login, current-user API, logout placeholder, forgot-password placeholder, and reset-password placeholder added
- Spring Security and role-based backend route protection added
- Frontend auth store, Axios bearer-token interceptor, route guards, and role-based redirects added
- Student, teacher, and admin dashboard placeholders now show authenticated user details and teacher status messaging

Not yet implemented:
- AI integration
- Payment integration
- Learning content workflows
- Teacher verification document workflow
- Course, package, lesson, quiz, and business feature controllers

## Environment Setup
Backend example environment file:
- `backend/.env.example`

Important Phase 3 backend environment variables:
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `JWT_EXPIRATION`
- `DEFAULT_ADMIN_EMAIL`
- `DEFAULT_ADMIN_PASSWORD`
- `APP_CORS_ALLOWED_ORIGINS`

Safe example values are already documented in:
- [backend/.env.example](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/backend/.env.example)
- [backend/src/main/resources/application-example.yml](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/backend/src/main/resources/application-example.yml)

## How To Start MySQL
From the project root:

```powershell
docker compose up -d
```

To stop MySQL:

```powershell
docker compose down
```

MySQL notes:
- Database name: `ai_chinese_learning`
- Host port: `3307`

## How To Run Backend
From the `backend` folder:

```powershell
.\mvnw.cmd spring-boot:run
```

Backend default URL:
- `http://localhost:8080`

Health endpoint:
- `GET http://localhost:8080/api/v1/health`

## How To Run Frontend
From the `frontend` folder:

```powershell
npm install
npm run dev
```

Frontend default URL:
- `http://localhost:5173`

## Authentication Overview
Phase 3 authentication uses:
- Spring Security
- BCrypt password hashing
- JWT access tokens
- Backend role-based route protection
- Frontend Pinia auth store with bearer-token Axios interceptor

Implemented auth APIs:
- `POST /api/v1/auth/register/student`
- `POST /api/v1/auth/register/teacher`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`

Backend route protection:
- Public:
  - `/api/v1/health`
  - `/api/v1/auth/register/student`
  - `/api/v1/auth/register/teacher`
  - `/api/v1/auth/login`
  - `/api/v1/auth/forgot-password`
  - `/api/v1/auth/reset-password`
- Authenticated:
  - `/api/v1/auth/me`
  - `/api/v1/auth/logout`
- Role protected:
  - `/api/v1/students/**` requires `STUDENT`
  - `/api/v1/teachers/**` requires `TEACHER`
  - `/api/v1/admin/**` requires `ADMIN`

Frontend redirect rules after login:
- `STUDENT -> /student/dashboard`
- `TEACHER -> /teacher/dashboard`
- `ADMIN -> /admin/dashboard`

## Default Admin Login
Admin cannot register publicly.

Create the default admin by setting:
- `DEFAULT_ADMIN_EMAIL`
- `DEFAULT_ADMIN_PASSWORD`

If these variables are missing:
- backend still starts safely
- a clear warning is logged
- no admin user is created automatically

## Student and Teacher Registration
Student registration:
- public
- creates a user with `STUDENT` role
- creates a student profile

Teacher registration:
- public
- creates a user with `TEACHER` role
- creates a teacher profile
- default teacher status is `PENDING`

Teacher Phase 3 behavior:
- can log in
- can access teacher dashboard placeholder
- sees current approval status
- content creation workflows are intentionally deferred

## How To Test Protected Routes
1. Register a student or teacher, or log in with the seeded admin account.
2. Use the returned JWT token in:

```text
Authorization: Bearer <token>
```

3. Test:
- `GET /api/v1/auth/me`
- `GET /api/v1/students/dashboard`
- `GET /api/v1/teachers/dashboard`
- `GET /api/v1/admin/dashboard`

Expected examples:
- no token on a protected route returns `401`
- wrong role on a protected route returns `403`

## Additional Documentation
- Setup guide: [docs/setup.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/docs/setup.md)
- Database notes: [docs/database-notes.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/docs/database-notes.md)
