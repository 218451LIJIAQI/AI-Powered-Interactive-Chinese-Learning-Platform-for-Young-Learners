# Setup Guide

## Overview
This document explains how to run the project locally through Phase 7.

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
- [../backend/.env.example](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/backend/.env.example)
- [../backend/src/main/resources/application-example.yml](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/backend/src/main/resources/application-example.yml)

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
- `JWT_SECRET` should be a strong plain-text secret with at least 32 characters or bytes.
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

## 6. Main Frontend Routes
Public routes:
- `/`
- `/courses`
- `/courses/:id`
- `/faq`
- `/terms`
- `/privacy`
- `/child-safety`
- `/ai-disclaimer`
- `/login`
- `/register`
- `/teachers/:id`

Student routes:
- `/student/dashboard`
- `/student/lessons`
- `/student/lessons/:lessonId`
- `/student/lessons/:lessonId/flashcards`
- `/student/quizzes/:quizId`
- `/student/quiz-attempts/:attemptId`
- `/student/matching-games/:gameId`
- `/student/listening-exercises/:exerciseId`
- `/student/tracing-exercises/:exerciseId`
- `/student/progress`
- `/student/rewards`
- `/student/wrong-answers`

Teacher routes:
- `/teacher/dashboard`
- `/teacher/profile`
- `/teacher/verification-status`
- `/teacher/verification-documents`
- `/teacher/modules`
- `/teacher/lessons`
- `/teacher/lessons/new`
- `/teacher/lessons/:id/edit`
- `/teacher/packages`
- `/teacher/packages/new`
- `/teacher/packages/:id/edit`
- `/teacher/flashcards`
- `/teacher/quizzes`
- `/teacher/matching-games`
- `/teacher/listening-exercises`
- `/teacher/tracing-exercises`
- `/teacher/quiz-results`

Admin routes:
- `/admin/dashboard`
- `/admin/users`
- `/admin/students`
- `/admin/teacher-approval`
- `/admin/teacher-approval/:id`
- `/admin/content/approvals`
- `/admin/content/approvals/:type/:id`
- `/admin/content/moderation`

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

## 8. Teacher Verification And Admin Management APIs
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

## 9. Phase 6 Content Management APIs
Public:
- `GET /api/v1/public/levels`
- `GET /api/v1/public/categories`
- `GET /api/v1/public/courses`
- `GET /api/v1/public/courses/{id}`

Teacher:
- `GET /api/v1/teachers/modules`
- `POST /api/v1/teachers/modules`
- `GET /api/v1/teachers/modules/{id}`
- `PUT /api/v1/teachers/modules/{id}`
- `PATCH /api/v1/teachers/modules/{id}/archive`
- `POST /api/v1/teachers/modules/{id}/submit`
- `GET /api/v1/teachers/lessons`
- `POST /api/v1/teachers/lessons`
- `GET /api/v1/teachers/lessons/{id}`
- `PUT /api/v1/teachers/lessons/{id}`
- `PATCH /api/v1/teachers/lessons/{id}/archive`
- `POST /api/v1/teachers/lessons/{id}/submit`
- `GET /api/v1/teachers/lessons/{lessonId}/activities`
- `POST /api/v1/teachers/lessons/{lessonId}/activities`
- `PUT /api/v1/teachers/lesson-activities/{activityId}`
- `PATCH /api/v1/teachers/lesson-activities/{activityId}/archive`
- `GET /api/v1/teachers/packages`
- `POST /api/v1/teachers/packages`
- `GET /api/v1/teachers/packages/{id}`
- `PUT /api/v1/teachers/packages/{id}`
- `PATCH /api/v1/teachers/packages/{id}/archive`
- `POST /api/v1/teachers/packages/{id}/submit`
- `POST /api/v1/teachers/packages/{id}/items`
- `DELETE /api/v1/teachers/packages/{packageId}/items/{itemId}`

Admin:
- `GET /api/v1/admin/levels`
- `GET /api/v1/admin/categories`
- `GET /api/v1/admin/content/submissions`
- `GET /api/v1/admin/content/submissions/{contentType}/{id}`
- `POST /api/v1/admin/content/{contentType}/{id}/approve`
- `POST /api/v1/admin/content/{contentType}/{id}/reject`
- `POST /api/v1/admin/content/{contentType}/{id}/publish`
- `POST /api/v1/admin/content/{contentType}/{id}/archive`

## 10. Manual Phase 6 and Phase 7 Testing Guide
Teacher content creation:
- Log in as an approved teacher
- Open `/teacher/modules` and create a module
- Open `/teacher/lessons` and create a lesson
- Add at least one lesson activity placeholder
- Open `/teacher/packages` and create a package
- Add one module or lesson to the package
- Submit the content for review

Admin content moderation:
- Log in as an admin
- Open `/admin/content/approvals`
- Review submitted content
- Approve or reject content with a reason
- Publish approved content
- Open `/admin/content/moderation` to confirm status visibility

Public discovery:
- Open `/courses`
- Filter by level, category, access type, or learning type
- Confirm only published content appears
- Open `/courses/{id}` and confirm paid content remains locked

Phase 7 interactive learning:
- log in as an approved teacher
- add flashcards to an owned draft or rejected lesson
- add a quiz and at least one question
- add matching, listening, and tracing activities
- submit and publish the lesson through the existing Phase 6 workflow
- log in as a student
- open `/student/lessons`
- open the published lesson player
- practise flashcards
- submit a quiz and review the result
- open wrong-answer review
- submit matching and listening activities
- complete tracing
- open `/student/progress`
- open `/student/rewards`

## 11. What Exists Through Phase 7
- Frontend app scaffold
- Role-based layouts and navigation
- Real login page
- Real student and teacher registration page
- Auth store, JWT handling, and route guards
- Student, teacher, and admin dashboards with real API-backed summary structure
- Teacher profile and verification workflow
- Admin teacher approval workflow
- Public approved teacher profile page
- Teacher module, lesson, and package management pages
- Admin content approval and moderation pages
- Public course discovery and course detail pages
- Student lesson player, flashcard practice, quiz pages, wrong-answer review, progress, and rewards pages
- Teacher flashcard, quiz, matching, listening, tracing, and quiz-results pages
- Spring Boot backend
- `/api/v1/health`
- Database foundation and seed data
- Spring Security and JWT authentication
- Current user API
- Role-based backend protection
- Dashboard summary APIs
- Content management workflow APIs
- Interactive learning APIs, scoring, progress tracking, and rewards
- Backend integration tests for content workflow, security, and interactive learning

## 12. What Is Intentionally Deferred
- AI integration logic
- Payment and subscription workflows
- Checkout and cart logic
- Advanced AI tutor and AI explanations
- Commerce unlock logic
- Leaderboard
- Professional handwriting recognition
- Professional pronunciation scoring
- Live class scheduling
- Recorded lesson player
- Reviews, reports, support tickets, refunds, payouts, and advanced notifications
