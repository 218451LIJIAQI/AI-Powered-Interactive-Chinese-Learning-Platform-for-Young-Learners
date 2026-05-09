# AI-Powered Interactive Chinese Learning Platform for Young Learners

## Project Overview
This project is a full-stack final-year-project web application for young learners aged 7-12. It is designed as an interactive and gamified Chinese learning platform for the Malaysian context, with support for student, teacher, and admin roles.

Phase 1 established the monorepo scaffold. Phase 2 added the backend database foundation with JPA entities, repositories, seed data, and MySQL-ready configuration. Phase 3 introduced real authentication, JWT-based access control, role-protected backend routes, and frontend login and registration flows. Phase 4 added polished role-based layouts, dashboard summary APIs, and dashboard-ready UI for public, student, teacher, and admin users. Phase 5 added teacher profile management, verification document upload, admin teacher approval and rejection, basic admin user management, and approved public teacher profiles. Phase 6 added the learning content management foundation for modules, lessons, lesson activity placeholders, packages, admin review, and public discovery. Phase 7 now adds interactive learning activities, quiz attempts, wrong-answer review, progress tracking, and a reward system.

## Tech Stack
- Frontend: Vue 3, Vite, TypeScript, Element Plus, Vue Router, Pinia, Axios, Vue I18n, npm
- Backend: Java 21, Spring Boot 3.5.14, Spring Security, JWT, Spring Data JPA, Bean Validation, Maven Wrapper
- Database: MySQL 8.4 LTS
- Tooling: Docker Compose

## Current Status
Completed through Phase 7:
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

Intentionally not implemented in Phase 7:
- AI features
- payment, subscription, cart, refund, payout, or entitlement logic
- leaderboard
- live class scheduling workflows
- recorded video upload or player workflows
- professional handwriting recognition
- professional pronunciation scoring
- Phase 8 or later workflows

## Key Routes
Public:
- `/`
- `/courses`
- `/courses/:id`
- `/teachers/:id`
- `/login`
- `/register`

Student:
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

Teacher:
- `/teacher/dashboard`
- `/teacher/profile`
- `/teacher/verification-status`
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

Admin:
- `/admin/dashboard`
- `/admin/users`
- `/admin/students`
- `/admin/teacher-approval`
- `/admin/content/approvals`
- `/admin/content/moderation`

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
- Database notes: [docs/database-notes.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/database-notes.md)
- Teacher verification workflow: [docs/teacher-verification-workflow.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/teacher-verification-workflow.md)
- Content management: [docs/content-management.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/content-management.md)
- Interactive learning: [docs/interactive-learning.md](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/docs/interactive-learning.md)
