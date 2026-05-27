# Architecture Overview

This document describes the main runtime boundaries, data flows, and submission-relevant design decisions for MandarinSpark.

## System Context

MandarinSpark is a full-stack learning platform with three primary personas:

- **Student**: learns Mandarin through lessons, practice activities, quizzes, live/recorded classes, rewards, and SparkBot AI support.
- **Teacher**: creates learning content, manages live classes and recordings, reviews student progress, and provides feedback.
- **Administrator**: manages users, verifies teachers, moderates content and AI reports, reviews finance, and configures platform settings.

The production-style runtime is:

```text
Browser
  -> Nginx-served Vue SPA
  -> Spring Boot REST API under /api/v1
  -> MySQL 8.4 schema managed by Flyway
  -> Optional AI, Stripe, SMTP, uploads, logs, and metrics integrations
```

## Frontend Architecture

The frontend is a Vue 3 + Vite single-page application.

### Key Responsibilities

- **Routing and role boundaries**: `frontend/src/router/index.ts` defines public, student, teacher, admin, and system routes with role metadata and preview-mode support.
- **Session state**: `frontend/src/stores/auth.ts` keeps access tokens in memory only, supports HttpOnly-cookie refresh, and keeps preview role state session-scoped.
- **API access**: `frontend/src/api/http.ts` owns the Axios client, auth headers, refresh behavior, and unauthorized-session cleanup; `frontend/src/api/services.ts` exposes typed domain APIs.
- **Type contracts**: `frontend/src/types/api.ts` mirrors backend DTOs and keeps the SPA aligned with `/api/v1` JSON envelopes.
- **UX shells**: `frontend/src/layouts` and `frontend/src/pages` split public, student, teacher, and admin experiences into lazy-loaded route chunks.
- **PWA boundary**: Vite PWA caches the app shell and static assets but avoids API response caching.

### Frontend Data Flow

```text
Vue page/component
  -> Pinia store or service wrapper
  -> Axios client with bearer token
  -> ApiResponse/PageResponse unwrap
  -> component state and user feedback
```

Important frontend invariants:

- Protected routes are default-deny and require explicit role metadata.
- Preview mode must not persist bearer tokens or survive as a real authenticated session.
- External URLs are normalized before opening, and downloaded filenames are sanitized.
- `/api/**` responses must not be cached by the service worker.

## Backend Architecture

The backend is a Spring Boot 3 + Java 21 API with layered responsibilities.

### Main Layers

- **Controllers**: define REST endpoints, request validation, response envelopes, and HTTP-level cache or cookie behavior.
- **Services**: own business rules, transaction boundaries, quota checks, provider orchestration, and user-facing errors.
- **Repositories**: provide JPA persistence with entity graphs, ownership-scoped lookups, pessimistic locks for financial flows, and index-backed query contracts.
- **Entities and DTOs**: model relational data and external API contracts separately.
- **Configuration and security**: centralize CORS, JWT, forwarded-IP trust, headers, provider properties, production guards, and observability.

### Backend Request Flow

```text
HTTP request
  -> RequestIdFilter / security filters
  -> JWT authentication and RBAC
  -> Controller validation
  -> Service authorization and business rule checks
  -> Repository/database operations
  -> ApiResponse/PageResponse JSON envelope
```

Important backend invariants:

- Authentication uses short-lived JWT access tokens plus rotating HttpOnly refresh cookies.
- IDOR-sensitive reads and writes use ownership-scoped repository methods or service-level ownership checks.
- AI, email, payment, and file operations are kept outside long database transactions where possible.
- Production startup fails fast for unsafe placeholder secrets, disabled required providers, non-HTTPS callbacks, or invalid CORS/forwarded-proxy configuration.
- Uploaded files are treated as private content and pass extension, content-type, size, and magic-byte validation where applicable.

## AI Flow

The learner AI surface includes chat, vocabulary help, sentence practice, quiz generation, story generation, pronunciation feedback, writing feedback, recommendations, and report submission.

```text
Student request
  -> request validation and sanitization
  -> availability and quota checks
  -> short read-only context lookup where needed
  -> AI provider call outside long transaction
  -> persistence of usage/log/report metadata
  -> learner-friendly response or unavailable status
```

Key AI safeguards:

- Provider calls are disabled by default outside configured real-provider mode.
- Inputs are sanitized and length-capped to reduce prompt-control and cost risk.
- Per-user minute/day quotas bound provider spend.
- Provider latency is measured for operations monitoring.
- Unsafe or wrong AI responses can be reported for admin review.

## Payment Flow

The commerce flow is webhook-driven and designed for idempotency.

```text
CheckoutService
  -> OrderService creates order records
  -> PaymentService creates pending payment
  -> Stripe checkout redirect
  -> StripeWebhookService verifies signature and event shape
  -> PaymentService applies PENDING -> PAID/FAILED transition
  -> EntitlementService grants access
  -> TeacherEarningService records earning
  -> CartService clears purchased items
  -> NotificationService informs user
```

Important payment invariants:

- Stripe webhook signatures are verified before any state transition.
- Anonymous Stripe webhook bodies are capped at the Nginx edge and again before Spring request-body binding.
- Unsupported webhook events are acknowledged without state changes; malformed or forged events are rejected safely.
- Payment state transitions are idempotent and guarded by locks/versioning where needed.
- Production payment callbacks must use HTTPS and real provider configuration.

## Database and Migration Model

The backend uses MySQL 8.4 with Flyway.

- `V1__consolidated_schema.sql` is the current pre-production consolidated baseline.
- Hibernate uses `ddl-auto=validate`, so schema drift fails startup instead of silently mutating tables.
- Production sets `baseline-on-migrate=false`; an existing database must have valid Flyway history rather than being silently accepted as a baseline.
- Clean destructive Flyway operations are disabled.
- Before production freeze, accepted schema updates are folded into V1.
- After production freeze, schema changes must be new forward-only migrations.

## Deployment Model

The production-style Compose stack includes:

- MySQL 8.4 with persistent data volume.
- Spring Boot backend using the `prod` profile.
- Nginx frontend image serving the built SPA and proxying API requests.
- A fixed internal frontend proxy IP trusted by the backend for `X-Forwarded-For`.
- Persistent uploads volume for single-instance deployment.

Scaling considerations before multi-instance deployment:

- Move uploads to shared object storage.
- Move process-local authentication throttles to Redis or another shared store; AI quota counters are already database-coordinated, but their database contention must be capacity-tested.
- Use durable outbox/queue for email delivery.
- Reconfirm refresh-token/session invalidation behavior under multiple backend nodes.
- Put Prometheus/OpenAPI behind authenticated or internal-only access.

## Observability and Operations

Available operational surfaces:

- Actuator health, liveness, and readiness probes.
- Micrometer metrics, including AI provider latency.
- Request correlation IDs.
- Structured logs with sensitive data redaction.
- Release precheck, operations, API-versioning, and testing documentation under `docs/`.

Production releases should record quality-gate output, migration validation, Compose config validation, backup/restore rehearsal, browser smoke evidence, and dependency/container scan results.
