# Contributing Guide

This guide is for anyone extending, reviewing, or running the AI-Powered
Interactive Chinese Learning Platform locally. It captures the workflow,
expectations, and review checklist used across the project.

The repository is a monorepo with two long-lived runnable applications:

- `frontend/` &mdash; Vue 3 + Vite + TypeScript single-page app served at
  `http://localhost:5173` in development.
- `backend/` &mdash; Spring Boot 3.5.x (Java 21) REST API served at
  `http://localhost:8080`.

Plus shared infrastructure:

- `docker-compose.yml` &mdash; local MySQL 8.4 container on port `3307`.
- `.github/workflows/ci.yml` &mdash; CI pipeline that runs the full backend
  + frontend test suites on every push and pull request.

## Prerequisites

| Tool | Version | Notes |
| ---- | ------- | ----- |
| **Java JDK** | 21 | Required by `backend/pom.xml`; use Temurin or any other OpenJDK distribution. |
| **Node.js** | 20.x LTS | Matches the runtime version used in CI. |
| **npm** | 10.x | Ships with Node 20. |
| **Docker Desktop** | latest | Only needed if you want MySQL locally; tests use embedded H2 instead. |
| **Maven** | _(not required)_ | The repository includes the Maven wrapper (`./mvnw`); do not install Maven manually. |
| **Git** | latest | Required for hooks and history. |

## First-time setup

```powershell
# Clone and enter the repository
git clone <repo-url> ai-chinese-learning-platform
cd "ai-chinese-learning-platform"

# Backend &mdash; create a local .env with at minimum a 32+ character JWT secret
copy backend\.env.example backend\.env
# Edit backend\.env and set JWT_SECRET to a strong random value.

# Frontend &mdash; install dependencies
cd frontend
npm install
cd ..
```

## Running the apps locally

Open three terminals.

1. **MySQL** (optional &mdash; only if you want a real database):

   ```powershell
   docker compose up -d
   ```

2. **Backend**:

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

3. **Frontend**:

   ```powershell
   cd frontend
   npm run dev
   ```

Open `http://localhost:5173` in your browser.

## Running the tests

The same commands run in CI; running them locally before pushing keeps the
green pipeline green.

```powershell
# Backend &mdash; 133 integration + unit tests against embedded H2
cd backend
.\mvnw.cmd -B -ntp test

# Frontend &mdash; 95 Vitest unit tests + production type-check / build
cd ..\frontend
npm run build
npm test
```

You can scope backend test runs:

```powershell
.\mvnw.cmd -B -ntp test -Dtest="ContentManagementIntegrationTests"
```

## Code style and review expectations

- **No unrelated changes in a single PR.** Keep refactors, bug fixes, and
  feature work in separate commits or PRs whenever possible.
- **Tests live alongside the change.** New endpoints, services, or
  filters should ship with a regression test in the appropriate
  `*IntegrationTests.java` (backend) or `*.test.ts` (frontend) file.
- **No `findAll()` + in-memory filter.** Push predicates down to the
  database via repository methods or JPA `Specification`s. The existing
  `LessonRepository` / `CoursePackageRepository` specification overrides
  with `@EntityGraph` are the reference pattern.
- **Catch narrow exceptions.** Avoid `catch (Exception ...)` blocks
  except in the small number of explicit safety nets
  (e.g. `NotificationService.safeRecord*`, `SmtpEmailService.send*`).
  Enum parsers must catch `IllegalArgumentException` and reject blank
  input explicitly.
- **No new hard-coded English strings on user-facing pages.** Add the
  key to `frontend/src/i18n/messages/{en,ms,zh}` and reference it via
  `t('namespace.key')`. The `hardcoded-strings.test.ts` test enforces a
  rolling allow-list.
- **Use the centralised storage keys.** Read / write `localStorage` only
  through `safeGetItem` / `safeSetItem` / `safeRemoveItem` with a value
  from `STORAGE_KEYS` (`frontend/src/constants/storageKeys.ts`).
- **Use the centralised event names.** Cross-component window events go
  through `dispatchAppEvent(APP_EVENTS.X)`
  (`frontend/src/constants/appEvents.ts`).
- **Reuse `TextUtils`.** Backend services normalise user-supplied text
  through `TextUtils.trimToNull` / `TextUtils.trimToOptional` /
  `TextUtils.defaultIfBlank` (`backend/.../util/TextUtils.java`) instead
  of re-implementing the same `value == null || value.isBlank() ? ... :
  value.trim()` snippet. The behaviour is pinned by `TextUtilsTests`.

## Performance and concurrency conventions

These conventions were established to keep the platform responsive as
the database grows. New code should follow them; PR reviewers should
flag regressions.

### Backend &mdash; data access

- **Sort at the database, never in JVM.** Repositories expose
  `findAll(Sort)` overloads with `@EntityGraph`. Services should call
  `repo.findAll(Sort.by(DESC, "createdAt"))` instead of
  `repo.findAll().stream().sorted(...)`. Reference: `ReportService` /
  `SupportTicketService` admin listings.
- **Use `groupByXxxStatus()` projections for breakdowns.** The shared
  `StatusCountProjection` interface
  (`backend/.../repository/projection/StatusCountProjection.java`) is
  consumed by `groupByReportStatus`, `groupByReviewStatus`,
  `groupByTicketStatus`, `groupByPaymentStatus`, `groupByAiStatus`,
  `groupByContentStatus` (× 3 for module / lesson / package). Reference:
  `AnalyticsService.getAdminAnalytics`.
- **Use scoped `countByXxx` / `countByXxxIn` queries instead of
  `findAll().stream().filter(...).count()`.** Examples: `ReportRepository
  .countByReportStatus(PENDING)`, `SupportTicketRepository
  .countByTicketStatusIn(Set.of(OPEN, IN_PROGRESS))`,
  `AiUsageLogRepository.countAll()`.
- **Override `findAll()` to add `@EntityGraph` when admin / mapper code
  paths need related entities.** Reference:
  `RefundRequestRepository.findAll()`. Without the override, the JPA
  default returns lazy-fetched entities and the mapper triggers an N+1
  on `student.user` / `order` accesses.
- **Avoid `lc.relation.field` in JPQL `WHERE`** clauses when the
  relation is nullable. Hibernate generates an implicit INNER JOIN
  there even inside `is null OR ...` short-circuits, which silently
  excludes rows whose FK is non-null. Use explicit `LEFT JOIN` instead.
  Reference: `LiveClassRepository.findStudentVisibleClasses(...)`.
- **Bulk-fetch via `IN (...)` instead of N round-trips.** When a
  service has to fetch rows for "every owned X", first collect the ids
  in one query, then query the dependent table with `... where id in
  :ids`. Reference: `ReviewService.getTeacherReviews` (lesson + package
  ids, then `findByTargetTypeAndTargetIdInOrderByCreatedAtDesc`).

### Frontend &mdash; lifecycle

- **Catch initial-load errors per resource, never let them cascade.**
  In Vue pages that load multiple independent resources (e.g. filter
  options + main list), each loader should own its own
  `try { ... } catch (error) { errorMessage.value = ... } finally {
  loading.value = false }`. Run them in parallel from `onMounted` via
  `void loadX()`. Reference: `CourseDiscoveryPage.vue` and
  `StudentLessonsPage.vue`.
- **Guard paginated / filtered loads with a monotonic request token.**
  When the user can re-trigger a load (Apply filters / change page /
  fast-clicking), the slower in-flight request can resolve last and
  overwrite the newer correct result. Use the shared
  `useRequestToken()` composable
  (`frontend/src/composables/useRequestToken.ts`): call `start()` at
  the beginning of each load, and check `isCurrent(token)` before
  committing success **and** before surfacing errors so a stale
  rejection does not clobber the newer request's state. The canonical
  race scenario is pinned down by `useRequestToken.test.ts`. Reference
  consumers: `CourseDiscoveryPage.vue`, `StudentLessonsPage.vue` and
  `AdminAiUsageLogsPage.vue`. New paginated pages must use this
  composable rather than rolling their own inline token variable.

## CI pipeline

`.github/workflows/ci.yml` runs two jobs in parallel on every push and PR
to `main`:

1. **Backend tests** &mdash; sets up Temurin 21, runs `./mvnw -B -ntp
   test`, and uploads Surefire reports on failure.
2. **Frontend type-check, build & tests** &mdash; sets up Node 20, runs
   `npm ci`, `npm run build`, and `npm test`, and uploads the production
   bundle as an artifact.

Both jobs use the `concurrency` setting to cancel in-progress runs when
a new commit arrives on the same branch.

## Security notes

- **Never commit a real `JWT_SECRET`, Stripe key, or AI provider key.**
  The `.gitignore` excludes `backend/.env` and `frontend/.env*` for this
  reason.
- **`JWT_SECRET` must be at least 32 bytes.** The
  `SecurityProperties` validator fails the boot if it is missing or
  too short.
- **AI / Stripe / SMTP integrations are sandbox-only.** Real-provider
  flags (`AI_ENABLE_REAL_PROVIDER`, `PAYMENT_ENABLE_REAL_PROVIDER`,
  `SMTP_ENABLED`) default to `false`. Production hardening is intentionally
  out of scope for this prototype.

## Reporting an issue

Include:

- The exact command you ran (PowerShell or bash).
- The expected behaviour and the observed behaviour.
- Backend log output around the failure (when relevant), with secrets
  redacted.
- Browser console output (when relevant).
