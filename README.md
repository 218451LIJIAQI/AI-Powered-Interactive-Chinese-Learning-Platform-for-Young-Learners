# AI-Powered Interactive Chinese Learning Platform for Young Learners

## Project Overview
This project is a full-stack final-year-project web application for young learners aged 7-12. It is designed as an AI-centered, interactive, and gamified Chinese learning platform for the Malaysian context, with support for student, teacher, and admin roles.

Phase 1 established the monorepo scaffold. Phase 2 added the backend database foundation with JPA entities, repositories, seed data, and MySQL-ready configuration. Phase 3 introduced real authentication, JWT-based access control, role-protected backend routes, and frontend login and registration flows. Phase 4 added polished role-based layouts, dashboard summary APIs, and dashboard-ready UI for public, student, teacher, and admin users. Phase 5 added teacher profile management, verification document upload, admin teacher approval and rejection, basic admin user management, and approved public teacher profiles. Phase 6 now adds the learning content management foundation for modules, lessons, lesson activity placeholders, packages, admin review, and public discovery.

## Tech Stack
- Frontend: Vue 3, Vite, TypeScript, Element Plus, Vue Router, Pinia, Axios, Vue I18n, npm
- Backend: Java 21, Spring Boot 3.5.14, Spring Security, JWT, Spring Data JPA, Bean Validation, Maven Wrapper
- Database: MySQL 8.4 LTS
- Tooling: Docker Compose

## Monorepo Structure
```text
ai-chinese-learning-platform/
|- frontend/
|- backend/
|- docs/
|- docker-compose.yml
|- README.md
`- .gitignore
```

## Current Status
Completed through Phase 6:
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
- Teacher profile API and UI added for authenticated teachers
- Teacher verification status, document upload, protected document access, and resubmission flow added
- Admin teacher approval list, teacher verification detail view, approve, reject, and protected document access added
- Admin user list and student list APIs and pages added
- Public approved teacher profile API and frontend page added
- Phase 6 content management foundation added:
  - public levels and categories APIs
  - teacher module management
  - teacher lesson management
  - lesson activity placeholder management
  - teacher package management
  - admin content submissions, approval, rejection, publish, and archive workflow
  - public course and package discovery
  - public course and package detail browsing
  - teacher and admin content management pages
- Backend content workflow tests added for teacher access, admin moderation, and public visibility rules

Not yet implemented:
- AI feature logic
- Payment workflows
- Quiz gameplay
- Flashcard gameplay
- Matching game gameplay
- Character tracing gameplay
- Student progress tracking workflows
- Cart and subscription business logic
- Live class scheduling workflows
- Recorded lesson video player workflow
- Support, report, review, refund, payout, and advanced notification workflows

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
- [backend/.env.example](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/backend/.env.example)
- [backend/src/main/resources/application-example.yml](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/backend/src/main/resources/application-example.yml)

JWT notes:
- `JWT_SECRET` should be a strong plain-text secret with at least 32 characters or bytes.
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
- Course Detail
- FAQ
- Terms
- Privacy
- Child Safety
- AI Disclaimer
- Login
- Register
- Approved Teacher Profile

Student layout:
- Student Dashboard
- Learning Modules placeholder
- AI Tutor placeholder
- AI Vocabulary Helper placeholder
- AI Sentence Practice placeholder
- Pronunciation Feedback placeholder
- Writing Feedback placeholder
- Progress placeholder
- Rewards placeholder
- Cart placeholder
- Payment History placeholder
- Subscription placeholder
- Wishlist placeholder
- Notifications placeholder
- Support placeholder

Teacher layout:
- Teacher Dashboard
- My Profile
- Verification Status
- Upload Verification Documents
- Module Management
- Lesson Management
- Package Management
- Quiz Management placeholder
- Flashcard Management placeholder
- Matching Game Management placeholder
- AI Teacher Assistant placeholder
- AI Content Tools placeholder
- Student Progress placeholder
- Reviews placeholder
- Earnings placeholder
- Payout Requests placeholder
- Notifications placeholder
- Support placeholder

Admin layout:
- Admin Dashboard
- User Management
- Students
- Teacher Approval
- Content Approval
- Content Moderation
- AI Usage Logs placeholder
- Payment Management placeholder
- Refund Management placeholder
- Payout Management placeholder
- Subscription Plans placeholder
- Reports placeholder
- Reviews Moderation placeholder
- Notifications placeholder
- Support Tickets placeholder
- Activity Logs placeholder
- System Settings placeholder

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

## Teacher Verification And Admin Management APIs
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

## Phase 6 Content Management APIs
Public catalog:
- `GET /api/v1/public/levels`
- `GET /api/v1/public/categories`
- `GET /api/v1/public/courses`
- `GET /api/v1/public/courses/{id}`

Teacher content APIs:
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

Admin content moderation:
- `GET /api/v1/admin/levels`
- `GET /api/v1/admin/categories`
- `GET /api/v1/admin/content/submissions`
- `GET /api/v1/admin/content/submissions/{contentType}/{id}`
- `POST /api/v1/admin/content/{contentType}/{id}/approve`
- `POST /api/v1/admin/content/{contentType}/{id}/reject`
- `POST /api/v1/admin/content/{contentType}/{id}/publish`
- `POST /api/v1/admin/content/{contentType}/{id}/archive`

## Phase 6 Workflow Summary
Content structure:
- `Level -> Module -> Lesson -> Activity -> Quiz`

Status workflow:
- Teacher creates content as `DRAFT`
- Teacher submits `DRAFT` or `REJECTED` content as `SUBMITTED`
- Admin approves `SUBMITTED` content into `APPROVED`
- Admin rejects `SUBMITTED` content into `REJECTED`
- Admin publishes `APPROVED` content into `PUBLISHED`
- Admin archives content into `ARCHIVED`
- Public discovery only returns `PUBLISHED` content

Teacher access rules:
- Only `APPROVED` teachers can create, edit, or submit content
- `PENDING` and `REJECTED` teachers can still log in, but content creation stays locked
- Teachers can only manage their own content

Public discovery rules:
- Only published lessons and packages are visible
- Paid content appears locked
- No payment or checkout logic is implemented

## How To Test Dashboards
Student Dashboard:
1. Register a student or log in as an existing student.
2. Open `/student/dashboard`.
3. Confirm the student dashboard loads with:
   - welcome message
   - progress cards
   - quick access cards
   - safe empty states where later features are not yet implemented

Teacher Dashboard:
1. Register a teacher or log in as an existing teacher.
2. Open `/teacher/dashboard`.
3. Confirm:
   - teacher status is shown
   - `PENDING` state displays a clear approval message
   - creation-related actions are visually locked until approval

Admin Dashboard:
1. Seed the admin through environment variables.
2. Log in with the seeded admin account.
3. Open `/admin/dashboard`.
4. Confirm summary cards and recent lists load with real counts where available.

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
3. Confirm the approved teacher profile loads with verified badge, bio, and placeholder course or review sections.
4. Confirm pending or rejected teachers do not load publicly.

## How To Test Phase 6 Content Management
Teacher content flow:
1. Log in as an `APPROVED` teacher.
2. Open `/teacher/modules` and create a module draft.
3. Open `/teacher/lessons` and create a lesson draft.
4. Add one or more activity placeholders to the lesson.
5. Open `/teacher/packages` and create a package draft.
6. Add owned modules or lessons as package items.
7. Submit the module, lesson, or package for review.

Admin review flow:
1. Log in as an admin.
2. Open `/admin/content/approvals`.
3. Filter submissions by type or status.
4. Open a content detail page.
5. Approve or reject submitted content.
6. Publish approved content.
7. Confirm archived content no longer appears publicly.

Public discovery flow:
1. Open `/courses`.
2. Search or filter by level, category, access type, or learning type.
3. Open a published course or package detail page.
4. Confirm paid content remains locked and metadata-only.

## Additional Documentation
- Setup guide: [docs/setup.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/setup.md)
- Database notes: [docs/database-notes.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/database-notes.md)
- Teacher verification workflow: [docs/teacher-verification-workflow.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/teacher-verification-workflow.md)
- Content management: [docs/content-management.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/content-management.md)
