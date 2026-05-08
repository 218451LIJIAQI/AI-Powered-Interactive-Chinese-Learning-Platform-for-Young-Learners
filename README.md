# AI-Powered Interactive Chinese Learning Platform for Young Learners

## Project Overview
This project is a full-stack final-year-project web application for young learners aged 7-12. It is designed as an AI-centered, interactive, and gamified Chinese learning platform for the Malaysian context, with support for student, teacher, and admin roles.

Phase 1 established the monorepo scaffold. Phase 2 added the backend database foundation with JPA entities, repositories, seed data, and MySQL-ready configuration. Phase 3 introduced real authentication, JWT-based access control, role-protected backend routes, and frontend login and registration flows. Phase 4 added polished role-based layouts, dashboard summary APIs, and dashboard-ready UI for public, student, teacher, and admin users. Phase 5 now adds teacher profile management, verification document upload, admin teacher approval and rejection, basic admin user management, and approved public teacher profiles.

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
Completed through Phase 5:
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
- Teacher profile API and UI added for authenticated teachers
- Teacher verification status, document upload, protected document access, and resubmission flow added
- Admin teacher approval list, teacher verification detail view, approve, reject, and protected document access added
- Admin user list and student list APIs and pages added
- Public approved teacher profile API and frontend page added
- Notification and audit log creation added for teacher verification actions
- Backend file upload handling added using `UPLOAD_DIR` and `MAX_FILE_SIZE`
- Phase 5 backend integration tests added for teacher verification, admin teacher management, public teacher profile visibility, and access control

Not yet implemented:
- AI feature logic
- Payment workflows
- Learning content management workflows
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
- `UPLOAD_DIR`
- `MAX_FILE_SIZE`

Safe example values are already documented in:
- [backend/.env.example](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/backend/.env.example)
- [backend/src/main/resources/application-example.yml](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/backend/src/main/resources/application-example.yml)

JWT notes:
- `JWT_SECRET` should be a strong plain-text secret with at least 32 characters/bytes.
- If you want to provide a Base64-encoded JWT secret, prefix it with `base64:`.
- Short secrets now fail clearly at startup instead of causing a runtime login `500`.

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
- My Profile
- Verification Status
- Upload Verification Documents
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
- Students
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
- Many Phase 4 and Phase 5 non-core workflow pages are still intentional placeholders with polished UI, empty states, and future-phase navigation structure.

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

Teacher verification and profile APIs:
- `GET /api/v1/teachers/me`
- `PUT /api/v1/teachers/me/profile`
- `GET /api/v1/teachers/verification-status`
- `POST /api/v1/teachers/verification-documents`
- `POST /api/v1/teachers/resubmit-verification`
- `GET /api/v1/teachers/verification-documents/{id}`

Admin teacher management APIs:
- `GET /api/v1/admin/teachers`
- `GET /api/v1/admin/teachers/{id}`
- `GET /api/v1/admin/teachers/pending`
- `POST /api/v1/admin/teachers/{id}/approve`
- `POST /api/v1/admin/teachers/{id}/reject`
- `GET /api/v1/admin/teacher-documents/{id}`

Admin user management APIs:
- `GET /api/v1/admin/users`
- `GET /api/v1/admin/students`

Public teacher profile API:
- `GET /api/v1/public/teachers/{id}`

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

Phase 5 teacher verification workflow:
- teachers can update their own profile from `/teacher/profile`
- teachers can upload private verification documents from `/teacher/verification-documents`
- teachers can review their status from `/teacher/verification-status`
- rejected teachers can upload new documents and resubmit verification
- admins can approve or reject teachers from `/admin/teacher-approval`
- only `APPROVED` teachers become publicly visible at `/teachers/:id`
- verification documents are protected and are only accessible to the owner teacher or an admin

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

## How To Test Teacher Verification And Admin Management
Teacher verification:
1. Register a teacher account.
2. Log in and open `/teacher/profile`.
3. Update the profile and save it.
4. Open `/teacher/verification-documents`.
5. Upload a PDF, JPG, JPEG, or PNG verification document.
6. Confirm the latest document list and verification status update render correctly.

Admin teacher approval:
1. Log in with the seeded admin account.
2. Open `/admin/teacher-approval`.
3. Review a pending teacher.
4. Open the teacher detail page.
5. View the protected verification document.
6. Approve or reject the teacher and confirm the status changes.

Public teacher profile:
1. Approve a teacher through the admin teacher approval page.
2. Open `/teachers/{teacherId}`.
3. Confirm the approved teacher profile loads with verified badge, bio, and placeholder course/review sections.
4. Confirm pending or rejected teachers do not load publicly.

## Additional Documentation
- Setup guide: [docs/setup.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/docs/setup.md)
- Database notes: [docs/database-notes.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/docs/database-notes.md)
- Teacher verification workflow: [docs/teacher-verification-workflow.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/docs/teacher-verification-workflow.md)
