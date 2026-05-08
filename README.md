# AI-Powered Interactive Chinese Learning Platform for Young Learners

## Project Overview
This project is a full-stack final-year-project web application for young learners aged 7-12. It is designed as an AI-centered, interactive, and gamified Chinese learning platform for the Malaysian context, with support for student, teacher, and admin roles.

Phase 1 established the monorepo scaffold. Phase 2 added the backend database foundation with JPA entities, repositories, seed data, and MySQL-ready configuration. Phase 3 introduced real authentication, JWT-based access control, role-protected backend routes, and frontend login and registration flows. Phase 4 now adds polished role-based layouts, dashboard summary APIs, and dashboard-ready UI for public, student, teacher, and admin users.

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
Completed through Phase 4:
- Monorepo scaffold created
- Vue frontend created with required packages and folder structure
- Public, student, teacher, and admin layouts added and polished
- Vue Router, Pinia, Axios, Element Plus, and Vue I18n configured
- Spring Boot backend scaffolded with `com.example.aichinese`
- `/api/v1/health` endpoint added
- Database entities, repositories, enums, and seed data added
- Real student registration and teacher registration added
- Teacher registration creates `PENDING` teacher profiles by default
- Seeded admin account can log in using environment variables
- JWT login, current-user API, logout placeholder, forgot-password placeholder, and reset-password placeholder added
- Spring Security and role-based backend route protection added
- Frontend auth store, Axios bearer-token interceptor, route guards, and role-based redirects added
- Protected dashboard summary APIs added:
  - `GET /api/v1/students/dashboard`
  - `GET /api/v1/teachers/dashboard`
  - `GET /api/v1/admin/dashboard`
- Student, teacher, and admin dashboards now show structured summary cards with real counts where available and safe defaults elsewhere
- Full Phase 4 route map and placeholder workspace pages added for later AI, content, payment, and operations modules

Not yet implemented:
- AI feature logic
- Payment workflows
- Learning content management workflows
- Teacher verification document workflow
- Course, package, lesson, quiz, and business feature controllers
- Live class, support, report, review, refund, payout, and notification workflows beyond dashboard placeholders and safe summaries

## Environment Setup
Backend example environment file:
- `backend/.env.example`

Important backend environment variables:
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
Authentication uses:
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

## Role-Based Layout Overview
Public layout:
- Landing
- Course Discovery
- FAQ
- Terms
- Privacy
- Child Safety
- AI Disclaimer
- Login
- Register

Student layout:
- Student Dashboard
- Learning Modules
- AI Tutor
- AI Vocabulary Helper
- AI Sentence Practice
- Pronunciation Feedback
- Writing Feedback
- Progress
- Rewards
- Cart
- Payment History
- Subscription
- Wishlist
- Notifications
- Support

Teacher layout:
- Teacher Dashboard
- Content Management
- Lesson Management
- Quiz Management
- Flashcard Management
- Matching Game Management
- AI Teacher Assistant
- AI Content Tools
- Student Progress
- Reviews
- Earnings
- Payout Requests
- Notifications
- Support

Admin layout:
- Admin Dashboard
- User Management
- Teacher Approval
- Content Approval
- AI Usage Logs
- Payment Management
- Refund Management
- Payout Management
- Subscription Plans
- Reports
- Reviews Moderation
- Notifications
- Support Tickets
- Activity Logs
- System Settings

Note:
- Most Phase 4 non-dashboard pages are intentional placeholders with polished UI, empty states, and future-phase navigation structure.

## Dashboard Summary API Overview
Student dashboard:
- `GET /api/v1/students/dashboard`
- returns authenticated student summary data with safe defaults

Teacher dashboard:
- `GET /api/v1/teachers/dashboard`
- returns teacher status, summary metrics, and safe default lists

Admin dashboard:
- `GET /api/v1/admin/dashboard`
- returns real database counts where available plus safe zero-value summaries for deferred workflows

Dashboard API behavior:
- Uses the shared `ApiResponse` wrapper
- Keeps JWT and role protection from Phase 3
- Uses DTOs instead of exposing entities
- Avoids password hash and sensitive internal data

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

Teacher Phase 4 dashboard behavior:
- `PENDING` teachers can log in and view the dashboard
- `PENDING` teachers see a warning banner and locked creation actions
- `REJECTED` teachers see a rejection warning and locked creation actions
- `APPROVED` teachers see the normal dashboard state

## How To Test Dashboards
Student Dashboard:
1. Register a student or log in as an existing student.
2. Open `/student/dashboard`.
3. Confirm the student dashboard loads with:
   - welcome message
   - current level
   - progress cards
   - quick access cards
   - placeholder empty states where later features are not yet implemented

Teacher Dashboard:
1. Register a teacher or log in as an existing teacher.
2. Open `/teacher/dashboard`.
3. Confirm:
   - teacher status is shown
   - `PENDING` state displays a clear approval message
   - create-content actions are visually locked until approval

Admin Dashboard:
1. Seed the admin through environment variables.
2. Log in with the seeded admin account.
3. Open `/admin/dashboard`.
4. Confirm summary cards and recent lists load with real counts where available.

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
- frontend redirects unauthenticated protected access to `/login`
- frontend redirects authenticated users away from protected areas of other roles

## Additional Documentation
- Setup guide: [docs/setup.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/docs/setup.md)
- Database notes: [docs/database-notes.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/docs/database-notes.md)
