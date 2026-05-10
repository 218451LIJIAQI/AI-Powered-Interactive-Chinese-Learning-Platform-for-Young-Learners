# AI-Powered Interactive Chinese Learning Platform for Young Learners

## Project Overview
This repository contains a full-stack final-year-project prototype for young learners aged 7 to 12. The platform combines structured Chinese learning, teacher-created content, age-appropriate AI-assisted learning tools, payments in sandbox-friendly mode, live and recorded lesson delivery, communication workflows, moderation tools, and analytics dashboards for student, teacher, and admin roles.

Phase 12 focuses on final analytics, dashboard charts, UI polish, responsive improvements, i18n completion, legal and safety pages, testing, documentation, and demonstration readiness. The project remains a university prototype rather than a production system.

## Target Users
- Students learning Chinese in a guided and encouraging environment
- Teachers creating and managing educational content and feedback
- Admins supervising approvals, moderation, support, analytics, and operations

## Main Features
- JWT authentication with role-based access for `STUDENT`, `TEACHER`, and `ADMIN`
- Public landing, course discovery, FAQ, and legal/safety pages
- Student lessons, activities, rewards, progress, notifications, reports, reviews, and support tickets
- Teacher lesson/package/activity management, verification workflow, feedback, reviews, reports, moderation visibility, and earnings views
- Admin teacher approval, user management, content approvals, AI usage visibility, payments, refunds, payouts, support handling, moderation actions, and audit logs
- Phase 12 analytics dashboards with responsive charts for all three roles

## AI Features
- AI tutor
- AI vocabulary helper
- AI sentence practice
- AI quiz generator
- AI story generator
- AI learning recommendation and progress insight flows where already supported
- Teacher AI assistant and AI drafting workflows
- Prototype-friendly pronunciation and writing feedback flows
- Friendly unavailable fallback when a real AI provider is not enabled

## Learning Features
- Published lessons and structured lesson player flow
- Flashcards, quizzes, matching games, listening exercises, and tracing exercises
- Progress tracking, wrong-answer review, points, badges, and daily streaks
- Live classes, recorded lessons, hybrid delivery support, schedules, and protected meeting-link access

## Payment, Subscription, and Communication Features
- Cart, buy-now, checkout, payment history, refund requests, subscription plans, teacher earnings, payout requests
- In-app notifications with unread count and mark-as-read actions
- Optional SMTP-backed email with safe no-op fallback
- Reports, reviews, teacher feedback, support tickets, content moderation actions, and audit logs

## Analytics and Dashboard Features
- `GET /api/v1/students/analytics`
- `GET /api/v1/teachers/analytics`
- `GET /api/v1/admin/analytics`
- ECharts-based dashboard cards with safe empty states, zero states, and friendly load-failure behaviour
- Student analytics for lesson completion, activity trends, quiz trends, weak areas, and learning mode mix
- Teacher analytics for content status, recent quiz performance, earnings trend, and review distribution
- Admin analytics for registrations, teacher approvals, payment status, support status, and platform activity summaries

## Technology Stack
- Frontend: Vue 3, Vite, TypeScript, Element Plus, Vue Router, Pinia, Axios, Vue I18n, ECharts
- Backend: Java 21, Spring Boot 3.5.x, Spring Security, JWT, Spring Data JPA, Bean Validation, Maven Wrapper
- Database: MySQL 8.4 LTS
- Tooling: Docker Compose

## Monorepo Structure
```text
.
├─ frontend/   Vue 3 application
├─ backend/    Spring Boot API
├─ docs/       Project, setup, testing, and demo documentation
└─ docker-compose.yml
```

## Frontend Setup
From [frontend](./frontend):

```powershell
npm install
npm run dev
```

Useful commands:

```powershell
npm run build
npm run test
```

Default frontend URL:
- `http://localhost:5173`

## Backend Setup
From [backend](./backend):

```powershell
.\mvnw.cmd spring-boot:run
```

Useful commands:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
```

Default backend URL:
- `http://localhost:8080`
- Health endpoint: `GET /api/v1/health`

## MySQL and Docker Compose Setup
From the project root:

```powershell
docker compose up -d
docker compose config
```

Default local MySQL settings:
- Host: `localhost`
- Port: `3307`
- Database: `ai_chinese_learning`
- Username: `root`
- Password: `root`

## Environment Variables
Use placeholders only. See:
- [backend/.env.example](./backend/.env.example)
- [backend/src/main/resources/application-example.yml](./backend/src/main/resources/application-example.yml)

Important environment groups:
- Database: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- CORS and JWT: `APP_CORS_ALLOWED_ORIGINS`, `JWT_SECRET`, `JWT_EXPIRATION`
- Default admin: `DEFAULT_ADMIN_EMAIL`, `DEFAULT_ADMIN_PASSWORD`
- Uploads: `UPLOAD_DIR`, `MAX_FILE_SIZE`, `RECORDED_VIDEO_UPLOAD_DIR`, `MAX_VIDEO_FILE_SIZE`
- AI provider: `AI_PROVIDER`, `AI_API_BASE_URL`, `AI_API_KEY`, `AI_MODEL_NAME`, `AI_TIMEOUT_SECONDS`, `AI_MAX_OUTPUT_TOKENS`, `AI_ENABLE_REAL_PROVIDER`
- Stripe/payment: `PAYMENT_PROVIDER`, `STRIPE_SECRET_KEY`, `STRIPE_PUBLIC_KEY`, `STRIPE_WEBHOOK_SECRET`, `PAYMENT_SUCCESS_URL`, `PAYMENT_CANCEL_URL`, `PAYMENT_CURRENCY`, `PAYMENT_ENABLE_REAL_PROVIDER`, `PAYMENT_TIMEOUT_SECONDS`, `PAYMENT_MOCK_SUCCESS_ENABLED`
- SMTP: `SMTP_ENABLED`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`

## Default Admin Setup
If `DEFAULT_ADMIN_EMAIL` and `DEFAULT_ADMIN_PASSWORD` are set, the backend creates a default admin account during startup. If they are omitted, startup continues safely and admin creation is skipped.

## AI Provider Configuration
The backend is DeepSeek-ready, but real AI access is optional.

Example placeholder values:

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
- Do not place real AI keys in frontend code
- Do not commit real AI keys
- If AI is unavailable, the frontend should still show a friendly fallback

## Stripe Sandbox Configuration
Payments remain sandbox-friendly and prototype-oriented.

Example placeholder values:

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
- Stripe secret values must stay backend-only
- Test mode and mock-friendly behaviour are acceptable for demos
- Do not claim live production payment support

## SMTP Configuration
SMTP is optional.

Example placeholder values:

```env
SMTP_ENABLED=false
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=
```

Important:
- The system still works without SMTP
- In-app notifications still work when SMTP is disabled or unavailable
- Do not commit real SMTP credentials

## File Upload Configuration
Prototype uploads use backend-managed directories only.

Example:

```env
UPLOAD_DIR=./uploads
MAX_FILE_SIZE=5MB
RECORDED_VIDEO_UPLOAD_DIR=./uploads/recorded-videos
MAX_VIDEO_FILE_SIZE=100MB
```

Do not commit:
- uploaded runtime files
- backend `target/`
- frontend `dist/`
- logs
- temp files

## How To Run
Frontend:

```powershell
cd frontend
npm install
npm run dev
```

Backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

MySQL:

```powershell
docker compose up -d
```

## How To Test
Frontend:

```powershell
cd frontend
npm run build
npm run test
```

Backend:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
```

Infrastructure:

```powershell
docker compose config
```

## User Roles and Access Overview
- `STUDENT`: learning, progress, rewards, AI tools, notifications, reports, reviews, support, payments, and personal analytics
- `TEACHER`: content creation, verification, live/recorded learning management, reviews, feedback, moderation visibility, earnings, payouts, and teacher analytics
- `ADMIN`: teacher approvals, content approvals, support, moderation, audit logs, AI usage visibility, finance oversight, and platform analytics

Role checks are enforced both in frontend routing and backend authorization.

## Main Workflows
- Student registration, login, dashboard, lesson practice, rewards, and AI learning support
- Teacher registration, verification, content creation, live/recorded learning delivery, feedback, and earnings
- Admin approval, moderation, payments, support handling, analytics review, and audit visibility
- Communication and trust workflows through notifications, reports, reviews, support tickets, moderation actions, and audit logs

## Database Notes
See [docs/database-notes.md](./docs/database-notes.md) for table groups, enum notes, analytics data sources, and schema guidance.

## Known Limitations
- External AI provider integration only; no custom model training
- Stripe sandbox/test-mode only
- SMTP is optional and may remain in no-op mode
- External meeting links only; no internal video conferencing stack
- Pronunciation and writing feedback remain prototype-friendly rather than production-grade scoring
- No `Parent` role
- No production legal review
- No production deployment hardening
- No complete production-grade security audit

## Future Enhancements
- More complete analytics filtering and export flows
- Broader frontend test coverage
- Production-grade email templates and delivery tracking
- Richer moderation tooling and reporting
- Stronger observability and deployment automation

## Documentation
- Setup guide: [docs/setup.md](./docs/setup.md)
- API overview: [docs/api-overview.md](./docs/api-overview.md)
- Database notes: [docs/database-notes.md](./docs/database-notes.md)
- Testing checklist: [docs/testing-checklist.md](./docs/testing-checklist.md)
- Demo guide: [docs/demo-script.md](./docs/demo-script.md)
- Communication, moderation, support, and audit reference: [docs/communication-moderation-support-audit.md](./docs/communication-moderation-support-audit.md)
