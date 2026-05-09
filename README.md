# AI-Powered Interactive Chinese Learning Platform for Young Learners

## Project Overview
This project is a full-stack final-year-project web application for young learners aged 7-12. It is designed as an interactive and gamified Chinese learning platform for the Malaysian context, with support for student, teacher, and admin roles.

Phase 1 established the monorepo scaffold. Phase 2 added the backend database foundation with JPA entities, repositories, seed data, and MySQL-ready configuration. Phase 3 introduced real authentication, JWT-based access control, role-protected backend routes, and frontend login and registration flows. Phase 4 added polished role-based layouts, dashboard summary APIs, and dashboard-ready UI for public, student, teacher, and admin users. Phase 5 added teacher profile management, verification document upload, admin teacher approval and rejection, basic admin user management, and approved public teacher profiles. Phase 6 added the learning content management foundation for modules, lessons, lesson activity placeholders, packages, admin review, and public discovery. Phase 7 added interactive learning activities, quiz attempts, wrong-answer review, progress tracking, and a reward system. Phase 8 added the core backend-integrated AI layer with DeepSeek-ready configuration, AI tutor workflows, teacher AI drafting tools, prototype feedback uploads, and admin AI usage visibility. Phase 9 added payment and commerce workflows with Stripe-ready backend configuration, cart and checkout flows, subscriptions, entitlements, teacher earnings, refunds, and payouts. Phase 10 added live classes, recorded lesson videos, hybrid lesson delivery, protected join-link access, video progress tracking, and student/teacher/admin schedule views. Phase 11 adds notifications, optional SMTP email, reports, reviews, teacher feedback, support tickets, content moderation actions, and audit log visibility.

## Tech Stack
- Frontend: Vue 3, Vite, TypeScript, Element Plus, Vue Router, Pinia, Axios, Vue I18n, npm
- Backend: Java 21, Spring Boot 3.5.14, Spring Security, JWT, Spring Data JPA, Bean Validation, Maven Wrapper
- Database: MySQL 8.4 LTS
- Tooling: Docker Compose

## Current Status
Completed through Phase 11 work-in-progress:
- Monorepo scaffold created
- Vue frontend created with required packages and folder structure
- Public, student, teacher, and admin layouts added and polished
- Vue Router, Pinia, Axios, Element Plus, and Vue I18n configured
- Spring Boot backend scaffolded with `com.example.aichinese`
- `/api/v1/health` endpoint added
- Database entities, repositories, enums, and seed data added
- Real student registration and teacher registration added
- Spring Security, JWT login, current-user API, and role-based backend route protection added
- Dashboard APIs and role-based dashboard pages added
- Teacher verification workflow and admin teacher approval added
- Admin user and student management added
- Public approved teacher profile added
- Phase 6 content workflow added:
  - levels and categories
  - modules, lessons, lesson placeholders, packages
  - admin content review and publish workflow
  - public course discovery and detail pages
- Phase 7 interactive learning foundation added:
  - student lesson player
  - flashcard practice
  - quiz loading, submission, scoring, and results
  - wrong-answer review
  - matching game submission
  - listening exercise submission
  - tracing completion with canvas capture
  - student progress summary
  - points, badges, and streak summary
  - teacher flashcard, quiz, matching, listening, and tracing editors
  - teacher quiz attempt viewer
- Backend integration tests added for Phase 7 learning and reward flows
- Phase 8 AI foundation added:
  - backend AI provider abstraction
  - DeepSeek-compatible backend provider client
  - AI chat tutor endpoint
  - AI vocabulary helper endpoint
  - AI sentence practice endpoint
  - AI quiz generator endpoint
  - AI story generator endpoint
  - AI learning recommendation endpoint
  - AI progress insight endpoint
  - AI teacher assistant endpoint
  - AI pronunciation feedback prototype endpoint
  - AI writing feedback prototype endpoint
  - admin AI usage logs endpoint
  - student AI pages
  - teacher AI pages
  - admin AI usage logs page
  - safe unavailable mode when no real AI key is configured
  - backend AI integration tests
- Phase 9 commerce foundation added:
  - backend payment provider abstraction with unavailable, mock, and Stripe-ready clients
  - student cart, buy now, checkout, payment history, and refund request flows
  - subscription plan management and student subscription checkout flow
  - backend payment records, order items, entitlements, and payment status verification
  - teacher earnings, commission setting, payout request flow, and admin payout management
  - admin payment and refund management views
  - public paid-content purchase entry points
  - backend commerce integration tests
- Phase 10 live and recorded learning foundation added:
  - teacher live class create, update, cancel, reschedule, and enrolment list flows
  - student live class list, detail, enrolment, and protected join-link flow
  - admin live class overview and schedule overview
  - recorded lesson video support with uploaded-file or external-URL sources
  - protected recorded video streaming through backend access checks
  - video progress save, update, resume, and completion flow
  - hybrid lesson player support for live classes plus recorded videos
  - student, teacher, and admin schedule pages
  - backend integration tests for live and recorded learning security
- Phase 11 communication, moderation, support, and audit foundation added:
  - in-app notifications with unread count, mark-as-read, and mark-all-as-read
  - optional SMTP email service with safe no-op fallback
  - student reports with admin review and teacher revision visibility
  - student reviews with teacher visibility and admin moderation
  - teacher feedback to students with linked-student checks
  - support tickets with threaded replies, admin status updates, and priority updates
  - admin content moderation actions with teacher moderation history visibility
  - admin audit log visibility
  - backend integration tests for Phase 11 communication and moderation flows

Phase 11 known limitations:
- Stripe sandbox/test mode only
- real Stripe keys must stay in backend environment variables only
- payout is simulated by admin approval and mark-paid workflow
- refund handling is prototype-friendly when no live Stripe refund integration is configured
- no real bank transfer automation
- SMTP currently uses simple plain-text message bodies rather than a full template engine
- audit log IP addresses remain optional when request IP context is not passed into the reusable logging service

Still intentionally not implemented:
- leaderboard
- custom AI model training
- professional handwriting recognition
- professional pronunciation scoring
- professional tone scoring
- Phase 12 analytics and final polish workflows

## Key Routes
Public:
- `/`
- `/courses`
- `/courses/:id`
- `/teachers/:id`
- `/login`
- `/register`

Student:
- `/notifications`
- `/support-tickets`
- `/support-tickets/create`
- `/support-tickets/:id`
- `/student/dashboard`
- `/student/lessons`
- `/student/live-classes`
- `/student/live-classes/:id`
- `/student/schedule`
- `/student/reports`
- `/student/reports/create`
- `/student/reviews`
- `/student/feedback`
- `/student/lessons/:lessonId`
- `/student/recorded-lessons/:id`
- `/student/lessons/:lessonId/flashcards`
- `/student/quizzes/:quizId`
- `/student/quiz-attempts/:attemptId`
- `/student/matching-games/:gameId`
- `/student/listening-exercises/:exerciseId`
- `/student/tracing-exercises/:exerciseId`
- `/student/progress`
- `/student/rewards`
- `/student/wrong-answers`
- `/student/ai/tutor`
- `/student/ai/vocabulary`
- `/student/ai/sentence-practice`
- `/student/ai/quiz-generator`
- `/student/ai/story-generator`
- `/student/ai/pronunciation-feedback`
- `/student/ai/writing-feedback`

Teacher:
- `/notifications`
- `/support-tickets`
- `/support-tickets/create`
- `/support-tickets/:id`
- `/teacher/dashboard`
- `/teacher/profile`
- `/teacher/verification-status`
- `/teacher/live-classes`
- `/teacher/live-classes/create`
- `/teacher/live-classes/:id/edit`
- `/teacher/schedule`
- `/teacher/recorded-lessons`
- `/teacher/reviews`
- `/teacher/feedback`
- `/teacher/reports`
- `/teacher/moderation-records`
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
- `/teacher/ai/assistant`
- `/teacher/ai/vocabulary`
- `/teacher/ai/quiz-generator`
- `/teacher/ai/story-generator`

Admin:
- `/notifications`
- `/support-tickets`
- `/support-tickets/create`
- `/support-tickets/:id`
- `/admin/dashboard`
- `/admin/live-classes`
- `/admin/schedule`
- `/admin/users`
- `/admin/students`
- `/admin/teacher-approval`
- `/admin/content/approvals`
- `/admin/content/moderation`
- `/admin/reports`
- `/admin/reports/:id`
- `/admin/reviews`
- `/admin/notifications`
- `/admin/support-tickets`
- `/admin/support-tickets/:id`
- `/admin/audit-logs`
- `/admin/moderation`
- `/admin/ai-usage-logs`

## Phase 8 AI API Overview
General AI:
- `POST /api/v1/ai/availability`
- `POST /api/v1/ai/chat`
- `POST /api/v1/ai/vocabulary-helper`
- `POST /api/v1/ai/sentence-practice`
- `POST /api/v1/ai/quiz-generator`
- `POST /api/v1/ai/story-generator`
- `POST /api/v1/ai/learning-recommendation`
- `POST /api/v1/ai/progress-insight`
- `POST /api/v1/ai/teacher-assistant`
- `POST /api/v1/ai/pronunciation-feedback`
- `POST /api/v1/ai/writing-feedback`

Admin:
- `GET /api/v1/admin/ai/usage-logs`

## AI Configuration
The default AI provider is DeepSeek through backend environment variables only.

Example placeholders:

```env
AI_PROVIDER=deepseek
AI_API_BASE_URL=https://api.deepseek.com
AI_API_KEY=replace-with-your-deepseek-api-key
AI_MODEL_NAME=deepseek-chat
AI_TIMEOUT_SECONDS=30
AI_MAX_OUTPUT_TOKENS=1200
AI_ENABLE_REAL_PROVIDER=false
```

Important:
- Never put the real AI key in `frontend/.env`.
- Never expose the key to Vue code or API responses.
- The frontend does not receive the AI key.
- If the key is missing, the backend returns friendly unavailable responses instead of crashing.

## Payment Configuration
The default payment provider is Stripe sandbox/test mode through backend environment variables only.

Example placeholders:

```env
PAYMENT_PROVIDER=stripe
STRIPE_SECRET_KEY=replace-with-your-stripe-test-secret-key
STRIPE_PUBLIC_KEY=replace-with-your-stripe-test-public-key
STRIPE_WEBHOOK_SECRET=replace-with-your-stripe-webhook-secret
PAYMENT_SUCCESS_URL=http://localhost:5173/payment/success
PAYMENT_CANCEL_URL=http://localhost:5173/payment/cancel
PAYMENT_CURRENCY=MYR
PAYMENT_ENABLE_REAL_PROVIDER=false
PAYMENT_TIMEOUT_SECONDS=30
PAYMENT_MOCK_SUCCESS_ENABLED=true
```

Important:
- Never place the real Stripe secret key or webhook secret in frontend code.
- The frontend does not store card data.
- If Stripe keys are missing or the real provider is disabled, the backend remains runnable and returns safe unavailable or mock-mode checkout behavior.

## Live And Recorded Learning Configuration
Recorded lesson uploads use backend-only filesystem configuration:

```env
RECORDED_VIDEO_UPLOAD_DIR=./uploads/recorded-videos
MAX_VIDEO_FILE_SIZE=100MB
```

Important:
- Meeting links are never exposed in public course or lesson APIs.
- Students only receive the meeting link from the protected join endpoint after enrolment and entitlement checks pass.
- Uploaded recorded videos are served through backend-protected endpoints and do not expose private storage paths.
- Do not commit uploaded runtime video files.

## Phase 11 Email Configuration
Optional SMTP placeholders are backend-only:

```env
SMTP_ENABLED=false
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=
```

Important:
- SMTP is optional and the backend still starts when it is not configured.
- In-app notifications continue to work even when email sending is skipped.
- Never commit real SMTP credentials or expose them in frontend code.

## Phase 11 API Overview
Communication and moderation:
- `GET /api/v1/notifications`
- `GET /api/v1/notifications/unread-count`
- `PATCH /api/v1/notifications/{id}/read`
- `PATCH /api/v1/notifications/read-all`
- `POST /api/v1/reports`
- `GET /api/v1/reports/my`
- `GET /api/v1/reports/my/{id}`
- `POST /api/v1/reviews`
- `GET /api/v1/reviews/my`
- `PUT /api/v1/reviews/{id}`
- `DELETE /api/v1/reviews/{id}`
- `GET /api/v1/public/reviews`
- `GET /api/v1/students/feedback`
- `GET /api/v1/students/feedback/{id}`
- `POST /api/v1/teachers/feedback`
- `GET /api/v1/teachers/feedback`
- `PUT /api/v1/teachers/feedback/{id}`
- `GET /api/v1/teachers/feedback/candidates`
- `GET /api/v1/teachers/reviews`
- `GET /api/v1/teachers/reports`
- `GET /api/v1/teachers/reports/{id}`
- `GET /api/v1/teachers/moderation-records`
- `POST /api/v1/support-tickets`
- `GET /api/v1/support-tickets/my`
- `GET /api/v1/support-tickets/{id}`
- `POST /api/v1/support-tickets/{id}/messages`
- `GET /api/v1/admin/notifications`
- `GET /api/v1/admin/reports`
- `GET /api/v1/admin/reports/{id}`
- `PATCH /api/v1/admin/reports/{id}/review`
- `PATCH /api/v1/admin/reports/{id}/dismiss`
- `PATCH /api/v1/admin/reports/{id}/mark-action-taken`
- `PATCH /api/v1/admin/reports/{id}/request-revision`
- `GET /api/v1/admin/reviews`
- `PATCH /api/v1/admin/reviews/{id}/hide`
- `PATCH /api/v1/admin/reviews/{id}/show`
- `PATCH /api/v1/admin/reviews/{id}/flag`
- `GET /api/v1/admin/support-tickets`
- `GET /api/v1/admin/support-tickets/{id}`
- `POST /api/v1/admin/support-tickets/{id}/messages`
- `PATCH /api/v1/admin/support-tickets/{id}/status`
- `PATCH /api/v1/admin/support-tickets/{id}/priority`
- `GET /api/v1/admin/moderation-records`
- `GET /api/v1/admin/moderation-records/{id}`
- `POST /api/v1/admin/moderation/hide`
- `POST /api/v1/admin/moderation/unpublish`
- `POST /api/v1/admin/moderation/archive`
- `POST /api/v1/admin/moderation/request-revision`
- `POST /api/v1/admin/moderation/restore`
- `GET /api/v1/admin/audit-logs`
- `GET /api/v1/admin/audit-logs/{id}`
- `GET /api/v1/admin/feedback`

## Phase 7 API Overview
Student learning:
- `GET /api/v1/students/lessons/{lessonId}`
- `GET /api/v1/students/lessons/{lessonId}/activities`
- `GET /api/v1/students/lessons/{lessonId}/flashcards`
- `POST /api/v1/students/flashcards/{flashcardId}/practice`
- `GET /api/v1/students/quizzes/{quizId}`
- `POST /api/v1/students/quizzes/{quizId}/attempts`
- `GET /api/v1/students/quiz-attempts/{attemptId}`
- `GET /api/v1/students/wrong-answers`
- `GET /api/v1/students/quiz-attempts/{attemptId}/wrong-answers`
- `GET /api/v1/students/matching-games/{gameId}`
- `POST /api/v1/students/matching-games/{gameId}/submit`
- `GET /api/v1/students/listening-exercises/{exerciseId}`
- `POST /api/v1/students/listening-exercises/{exerciseId}/submit`
- `GET /api/v1/students/tracing-exercises/{exerciseId}`
- `POST /api/v1/students/tracing-exercises/{exerciseId}/complete`
- `GET /api/v1/students/progress`
- `GET /api/v1/students/rewards`
- `GET /api/v1/students/rewards/badges`
- `GET /api/v1/students/rewards/points-history`
- `GET /api/v1/students/rewards/streak`

Teacher activity management:
- `GET/POST /api/v1/teachers/lessons/{lessonId}/flashcards`
- `PUT/DELETE /api/v1/teachers/flashcards/{flashcardId}`
- `GET/POST /api/v1/teachers/lessons/{lessonId}/quizzes`
- `PUT/DELETE /api/v1/teachers/quizzes/{quizId}`
- `POST /api/v1/teachers/quizzes/{quizId}/questions`
- `PUT/DELETE /api/v1/teachers/quiz-questions/{questionId}`
- `GET /api/v1/teachers/quizzes/{quizId}/attempts`
- `GET/POST /api/v1/teachers/lessons/{lessonId}/matching-games`
- `PUT/DELETE /api/v1/teachers/matching-games/{gameId}`
- `GET/POST /api/v1/teachers/lessons/{lessonId}/listening-exercises`
- `PUT/DELETE /api/v1/teachers/listening-exercises/{exerciseId}`
- `GET/POST /api/v1/teachers/lessons/{lessonId}/tracing-exercises`
- `PUT/DELETE /api/v1/teachers/tracing-exercises/{exerciseId}`

## Learning And Reward Rules
- Students can only access `PUBLISHED` lessons and activities.
- Paid content remains visible but locked. No checkout or unlock logic exists in Phase 7.
- Teachers must still be `APPROVED` to create or edit activities.
- Teachers can only manage activities for their own lessons.
- Quiz scores are calculated on the backend.
- Correct answers are not exposed before student submission.
- Wrong answers are only visible to the owning student.
- Reward values currently used:
  - flashcard set completion: `5`
  - matching game completion: `10`
  - listening exercise completion: `10`
  - tracing exercise completion: `10`
  - quiz completion: `20`
  - quiz score at least `80%`: `10` bonus
  - lesson completion: `25`

## Local Setup
Start MySQL:

```powershell
docker compose up -d
```

Run backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Run frontend:

```powershell
cd frontend
npm install
npm run dev
```

## Documentation
- Setup guide: [docs/setup.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/setup.md)
- API overview: [docs/api-overview.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/api-overview.md)
- Database notes: [docs/database-notes.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/database-notes.md)
- Testing checklist: [docs/testing-checklist.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/testing-checklist.md)
- Teacher verification workflow: [docs/teacher-verification-workflow.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/teacher-verification-workflow.md)
- Content management: [docs/content-management.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/content-management.md)
- Interactive learning: [docs/interactive-learning.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/interactive-learning.md)
- AI features: [docs/ai-features.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/ai-features.md)
- Payment and commerce: [docs/payment-commerce.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/payment-commerce.md)
- Live, recorded, and hybrid learning: [docs/live-recorded-hybrid-learning.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/live-recorded-hybrid-learning.md)
- Communication, moderation, support, and audit: [docs/communication-moderation-support-audit.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/communication-moderation-support-audit.md)
