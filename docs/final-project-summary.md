# Final Project Summary

## Project Title
AI-Powered Interactive Chinese Learning Platform for Young Learners

## One-Paragraph Overview
A full-stack educational platform prototype designed for Chinese learners aged 7 to 12. The system combines structured lesson delivery, interactive learning activities, AI-assisted learning tools, sandbox-friendly commerce, live and recorded class delivery, communication and moderation workflows, role-specific analytics dashboards, and multilingual user experience support across English, Bahasa Melayu, and Simplified Chinese. The project is built and operated as a final-year-project prototype, with an emphasis on clean architecture, honest fallback behaviour, role-based access control, and a polished demo-ready user experience rather than production deployment.

## Target Users
- **Students** aged 7 to 12 learning Chinese in a guided, encouraging environment.
- **Teachers** creating, delivering, and moderating educational content.
- **Admins** supervising verification, moderation, support, finances, and platform analytics.

## Roles and Access Boundaries
- `STUDENT`: lessons, activities, progress, rewards, notifications, AI tools, support tickets, payments, personal analytics.
- `TEACHER`: content management, verification workflow, live/recorded learning, reviews, feedback, moderation visibility, earnings, payouts, teacher analytics.
- `ADMIN`: teacher approvals, content approvals, support handling, content moderation, audit logs, finance oversight, platform analytics.

Role checks are enforced both at the frontend route guard layer and the Spring Security authorization layer, with private user data, upload paths, and live meeting links protected behind ownership checks in service code.

## Technology Stack
- **Frontend**: Vue 3, Vite, TypeScript, Element Plus, Vue Router, Pinia, Axios, Vue I18n, ECharts.
- **Backend**: Java 21, Spring Boot 3.5.x, Spring Security, JWT, Spring Data JPA, Bean Validation, Maven Wrapper.
- **Database**: MySQL 8.4 LTS.
- **Tooling**: Docker Compose for local MySQL, Vitest for frontend tests, JUnit 5 + Mockito + MockMvc for backend tests.

## High-Level Architecture
- A single-page Vue 3 frontend communicates with a Spring Boot REST API over JSON.
- Authentication uses stateless JWTs stored in browser local storage and forwarded by an Axios interceptor.
- The backend follows a controller/service/repository structure with focused DTOs and dedicated mapper classes.
- A global exception handler returns consistent `ApiResponse` payloads for validation, authorization, malformed JSON, missing resources, file size limits, and unexpected failures.
- AI and payment provider integrations are encapsulated behind provider client interfaces with safe fallback behaviour when real credentials are not configured.
- Notifications, audit logging, and email side effects are designed to fail safely without breaking the main business action.

## Delivered Feature Areas
- **Authentication and Profiles**: student/teacher self-registration, default admin seeding, JWT login, current-user lookup, logout, forgot/reset password placeholder with honest unavailable response.
- **Course and Content Workflow**: modules, packages, lessons, quizzes, flashcards, matching games, listening exercises, tracing exercises, content approval and moderation actions.
- **Learning Workflows**: lesson player, quiz attempts and review, wrong-answer review, rewards, progress tracking, streaks.
- **AI Tools**: tutor, vocabulary helper, sentence practice, quiz generator, story generator, pronunciation feedback, writing feedback, teacher AI assistant, with graceful fallback when no real provider is configured.
- **Live and Recorded Learning**: live class scheduling, role-aware schedules, recorded lesson uploads, hybrid learning support, protected meeting-link access.
- **Commerce**: cart, buy-now, sandbox checkout, payment history, refund requests, subscription plans, teacher earnings, payout requests, admin finance management.
- **Communication and Trust**: in-app notifications with optional SMTP delivery, reports, reviews, support tickets, moderation actions, and admin audit logs.
- **Analytics**: ECharts-based dashboards for student, teacher, and admin views, with safe empty states and loading/error states.
- **Multilingual UX**: full i18n coverage across English, Bahasa Melayu, and Simplified Chinese for demo-facing surfaces, with locale-aware date formatting.
- **Public and Legal Pages**: landing page, course discovery, FAQ, terms, privacy, child-safety, AI disclaimer, friendly 404 page.

## Security and Safety Design Choices
- Passwords are hashed with `PasswordEncoder`; the password hash is never serialized in any API response, and integration tests assert this for both registration and current-user responses.
- JWT secret, AI keys, Stripe keys, and SMTP credentials are loaded from environment variables only and are never committed.
- A global exception handler returns clean error responses for validation errors (400), malformed JSON (400), authorization failures (403), missing resources (404), file size limits (400), and unexpected errors (500), without exposing stack traces or internal class names.
- Role-based and ownership-based checks are enforced on private endpoints, with integration tests covering missing-token (401) and wrong-role (403) cases.
- Real external services (DeepSeek, Stripe, SMTP) are not contacted from automated tests.

## Quality Practices
- 101 backend integration and unit tests covering authentication, role separation (student/teacher/admin 401 and 403 paths), forgot-password account-enumeration safety, default-deny on unmapped routes, auth response shape, dashboards, AI fallback, commerce, content management, communication and moderation, interactive learning, live/recorded learning, analytics, JWT service, default admin seeder, teacher verification workflow, and the global exception handler's null/blank-message safety net for `IllegalArgumentException` and `AccessDeniedException`.
- 77 frontend tests covering the i18n message coverage for demo-facing keys, the absence of hard-coded English strings on demo pages, the analytics utility helpers, the API client error/redirect contract and numeric-parameter guard, the auth store, the role-based-path / route-param / enum-label / idle-prefetch composables, the application event constants, the file-download / API-error / safe-storage / money / datetime utilities, and the in-process app event dispatcher.
- A consistent `ApiResponse` envelope across the backend, with the frontend Axios interceptor surfacing a single, friendly error path.
- Clean separation between layouts, pages, components, and composables in the frontend, and between controllers, services, repositories, mappers, and providers in the backend.
- Default-deny security posture: any request path that is not explicitly listed in `SecurityConfig` requires authentication, so a future endpoint added without an explicit rule cannot be accidentally exposed to the public.

## Prototype Boundaries (Honest Limitations)
- AI features rely on third-party providers (DeepSeek-ready) only; no in-house model training.
- Payments operate in Stripe sandbox or mock mode only; no live financial processing or PCI scope.
- SMTP is optional; in-app notifications continue to work when SMTP is disabled.
- Live classes use external meeting platforms (Google Meet, Zoom links); there is no internal video conferencing stack.
- Pronunciation and writing scoring are demo-friendly heuristics, not production-grade evaluation.
- No dedicated `Parent` role.
- No formal production security audit, accessibility certification, or legal review has been performed.
- Deployment hardening, observability tooling, and production scaling are out of scope for the prototype.

## FYP Value
The project demonstrates:
- End-to-end full-stack integration across a non-trivial educational domain.
- Role-based platform design with consistent enforcement at both routing and service layers.
- Realistic handling of optional external services (AI, payment, email) with friendly fallback.
- A multilingual user experience appropriate for a Chinese-learning platform aimed at young learners in a Malaysian context.
- A polished, examiner-ready demo experience with consistent UI states, friendly error handling, and meaningful analytics dashboards.
- Disciplined use of validation, authorization, exception handling, and automated tests for the most critical workflows.

## Suggested Demonstration Flow
1. Start MySQL with `docker compose up -d`, then start the backend and frontend.
2. Show the landing page, course discovery, and the language switcher (English, Bahasa Melayu, Simplified Chinese).
3. Register a new student, log in, and tour the student dashboard, lessons, AI tools, cart, and notifications.
4. Log in as a seeded teacher to demonstrate verification status, content management, live/recorded learning, reviews, and earnings.
5. Log in as the admin to demonstrate teacher approvals, content moderation, payments, support tickets, audit logs, and analytics.
6. Optionally show the friendly 404 page, the malformed-JSON 400 response, and a wrong-role 403 response to highlight defensive design.
