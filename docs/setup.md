# Setup Guide

## Overview
This document explains how to run the project locally through Phase 5.

## Prerequisites
- Node.js and npm
- Java 21
- Docker Desktop with Docker Compose

Note:
- Maven does not need to be installed globally because the backend includes the Maven Wrapper.

## 1. Start MySQL
From the project root:

```powershell
docker compose up -d
```

This starts:
- MySQL 8.4
- Database name: `ai_chinese_learning`
- Host port: `3307`

## 2. Configure Backend Environment Variables
Use:
- [backend/.env.example](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/backend/.env.example)
- [backend/src/main/resources/application-example.yml](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/backend/src/main/resources/application-example.yml)

Important variables:
- `JWT_SECRET`
- `JWT_EXPIRATION`
- `DEFAULT_ADMIN_EMAIL`
- `DEFAULT_ADMIN_PASSWORD`
- `UPLOAD_DIR`
- `MAX_FILE_SIZE`

Example:

```env
DB_HOST=localhost
DB_PORT=3307
DB_NAME=ai_chinese_learning
DB_USERNAME=root
DB_PASSWORD=root
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
JWT_SECRET=replace-with-a-strong-plain-text-secret-at-least-32-characters-long
JWT_EXPIRATION=3600000
DEFAULT_ADMIN_EMAIL=admin@example.com
DEFAULT_ADMIN_PASSWORD=SuperSecure123!
UPLOAD_DIR=./uploads
MAX_FILE_SIZE=5MB
```

Notes:
- Do not use real production secrets in local demo setup files.
- `JWT_SECRET` should be a strong plain-text secret with at least 32 characters/bytes.
- If you need to provide a Base64-encoded JWT secret, use the prefix `base64:`.
- Invalid or short JWT secrets now fail clearly during backend startup instead of producing a runtime login `500`.
- If `DEFAULT_ADMIN_EMAIL` or `DEFAULT_ADMIN_PASSWORD` is missing, the backend still starts safely and skips admin creation.

## 3. Run the Backend
From the `backend` folder:

```powershell
.\mvnw.cmd spring-boot:run
```

Health check:

```text
GET http://localhost:8080/api/v1/health
```

## 4. Run the Frontend
From the `frontend` folder:

```powershell
npm install
npm run dev
```

Default frontend URL:
- `http://localhost:5173`

## 5. Authentication and Route Protection
Implemented auth endpoints:
- `POST /api/v1/auth/register/student`
- `POST /api/v1/auth/register/teacher`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`

Frontend auth behavior:
- JWT token is stored in local storage
- Axios automatically sends `Authorization: Bearer <token>`
- Vue Router guards protect role pages
- Role redirects after login:
  - `STUDENT -> /student/dashboard`
  - `TEACHER -> /teacher/dashboard`
  - `ADMIN -> /admin/dashboard`

## 6. Phase 4 Dashboard Routes
Public routes:
- `/`
- `/courses`
- `/faq`
- `/terms`
- `/privacy`
- `/child-safety`
- `/ai-disclaimer`
- `/login`
- `/register`

Student routes:
- `/student/dashboard`
- `/student/modules`
- `/student/ai-tutor`
- `/student/ai-vocabulary`
- `/student/ai-sentence-practice`
- `/student/pronunciation-feedback`
- `/student/writing-feedback`
- `/student/progress`
- `/student/rewards`
- `/student/cart`
- `/student/payment-history`
- `/student/subscription`
- `/student/wishlist`
- `/student/notifications`
- `/student/support`

Teacher routes:
- `/teacher/dashboard`
- `/teacher/profile`
- `/teacher/verification-status`
- `/teacher/verification-documents`
- `/teacher/content`
- `/teacher/lessons`
- `/teacher/quizzes`
- `/teacher/flashcards`
- `/teacher/matching-games`
- `/teacher/ai-assistant`
- `/teacher/ai-content-tools`
- `/teacher/student-progress`
- `/teacher/reviews`
- `/teacher/earnings`
- `/teacher/payouts`
- `/teacher/notifications`
- `/teacher/support`

Admin routes:
- `/admin/dashboard`
- `/admin/users`
- `/admin/students`
- `/admin/teacher-approval`
- `/admin/teacher-approval/:id`
- `/admin/content-approval`
- `/admin/ai-usage-logs`
- `/admin/payments`
- `/admin/refunds`
- `/admin/payouts`
- `/admin/subscription-plans`
- `/admin/reports`
- `/admin/reviews`
- `/admin/notifications`
- `/admin/support-tickets`
- `/admin/activity-logs`
- `/admin/settings`

Public teacher profile route:
- `/teachers/:id`

## 7. Dashboard Summary APIs
Student dashboard:
- `GET /api/v1/students/dashboard`
- Requires `STUDENT`

Teacher dashboard:
- `GET /api/v1/teachers/dashboard`
- Requires `TEACHER`

Admin dashboard:
- `GET /api/v1/admin/dashboard`
- Requires `ADMIN`

Behavior:
- Returns real counts where the database already contains relevant records
- Returns safe defaults or empty arrays for later-phase workflows
- Uses the shared `ApiResponse` response format

## 8. Phase 5 Teacher Verification And Admin Management APIs
Teacher APIs:
- `GET /api/v1/teachers/me`
- `PUT /api/v1/teachers/me/profile`
- `GET /api/v1/teachers/verification-status`
- `POST /api/v1/teachers/verification-documents`
- `POST /api/v1/teachers/resubmit-verification`
- `GET /api/v1/teachers/verification-documents/{id}`

Admin APIs:
- `GET /api/v1/admin/teachers`
- `GET /api/v1/admin/teachers/{id}`
- `GET /api/v1/admin/teachers/pending`
- `POST /api/v1/admin/teachers/{id}/approve`
- `POST /api/v1/admin/teachers/{id}/reject`
- `GET /api/v1/admin/teacher-documents/{id}`
- `GET /api/v1/admin/users`
- `GET /api/v1/admin/students`

Public API:
- `GET /api/v1/public/teachers/{id}`

## 9. Teacher Verification Workflow
- Teacher registers publicly and starts with `PENDING` status.
- Teacher updates their profile at `/teacher/profile`.
- Teacher uploads private verification documents at `/teacher/verification-documents`.
- Admin reviews pending teachers at `/admin/teacher-approval`.
- Admin approves or rejects the teacher.
- Rejected teachers can see the rejection reason and resubmit verification.
- Only approved teachers are visible on the public teacher profile route.

## 10. Manual Phase 5 Testing Guide
Teacher profile and upload:
- Register and log in as a teacher
- Open `/teacher/profile`
- Save profile changes
- Open `/teacher/verification-documents`
- Upload a PDF or PNG verification file
- Confirm the document list and status card update

Admin approval:
- Log in as the seeded admin
- Open `/admin/teacher-approval`
- Review a pending teacher
- Open the teacher detail page
- View the protected document
- Approve or reject the teacher

Public teacher profile:
- After approval, open `/teachers/{teacherId}`
- Confirm the verified badge and safe public info appear
- Confirm rejected or pending teachers are not publicly visible
## 11. Dashboard Testing Guide
### Student dashboard
- Register or log in as a student
- Visit `/student/dashboard`
- Confirm:
  - welcome message uses the authenticated user
  - progress cards render
  - quick access cards render
  - empty states appear instead of broken widgets

### Teacher dashboard
- Register or log in as a teacher
- Visit `/teacher/dashboard`
- Confirm:
  - teacher status is shown
  - `PENDING` or `REJECTED` displays a clear alert
  - creation-related actions are disabled or visually locked

### Admin dashboard
- Log in with the seeded admin account
- Visit `/admin/dashboard`
- Confirm:
  - total students, teachers, and pending approvals render
  - recent user activity, recent reports, and recent audit logs render safely
  - payment and AI cards show safe summary values

## 12. What Exists Through Phase 5
- Frontend app scaffold
- Role-based layouts and navigation
- Real login page
- Real student and teacher registration page
- Auth store, JWT handling, and route guards
- Student, teacher, and admin dashboards with real API-backed summary structure
- Placeholder workspace pages for future modules
- Spring Boot backend
- `/api/v1/health`
- Database foundation and seed data
- Spring Security and JWT authentication
- Current user API
- Role-based backend protection
- Dashboard summary APIs
- Backend auth and dashboard integration tests
- Teacher profile management
- Teacher verification upload and resubmission flow
- Admin teacher approval and rejection flow
- Admin user and student list pages
- Public approved teacher profile page
- Phase 5 backend integration tests
## 13. What Is Intentionally Deferred
- AI integration logic
- Payment and subscription workflows
- Learning content management
- Course, package, lesson, quiz, and business feature workflows
- Full support, report, review, notification, refund, and payout workflows
