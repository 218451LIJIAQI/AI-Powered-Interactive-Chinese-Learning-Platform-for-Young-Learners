# Testing Strategy

This document explains how MandarinSpark is validated before submission, review, or production promotion.

## Testing Goals

The test strategy focuses on five submission-critical outcomes:

- **Correctness**: student, teacher, admin, AI, payment, content, and auth flows behave as expected.
- **Security**: authentication, authorization, rate limits, file validation, payment webhooks, and sensitive logging are covered.
- **Contract stability**: backend DTO/API changes stay aligned with frontend types and route expectations.
- **Release readiness**: local quality gates match the hosted CI checks where possible.
- **Operational confidence**: production configuration, migration, backup, browser smoke, and capacity rehearsals are documented.

## Test Layers

| Layer                   | Scope                                                                | Command or location                                 |
| ----------------------- | -------------------------------------------------------------------- | --------------------------------------------------- |
| Toolchain alignment     | Java, Maven, Node versions match Maven wrapper, Docker, and CI       | `npm run quality:toolchain`                         |
| Node 22 wrapper         | Reproducible quality gate on machines with another Node major        | `npm run quality:node22`                            |
| Frontend lint           | ESLint rules for TypeScript/Vue quality                              | `npm --prefix frontend run lint`                    |
| Frontend format         | Prettier consistency for source/config/docs                          | `npm --prefix frontend run format:check`            |
| Frontend unit           | Pinia stores, API helpers, route helpers, formatting helpers         | `npm --prefix frontend run test:unit`               |
| Frontend coverage       | V8 coverage gate for API/config/plugins/stores/utils                 | `npm --prefix frontend run test:unit:coverage`      |
| Frontend typecheck      | Vue + TypeScript compile-time contract check                         | `npm --prefix frontend run typecheck`               |
| Frontend build          | Production Vite build and PWA generation                             | `npm --prefix frontend run build`                   |
| Frontend bundle budget  | Gzip-size guard for entry JS, shared CSS, and largest route chunk    | `npm --prefix frontend run budget:check`            |
| Frontend E2E            | Playwright smoke flows for public, auth, preview, and learner routes | `npm --prefix frontend run test:e2e`                |
| Backend tests           | Unit and integration tests for services/controllers/config/security  | `npm run quality:backend` or backend Maven commands |
| Backend static analysis | SpotBugs high-confidence static checks                               | backend `spotbugs:check`                            |
| Backend coverage        | JaCoCo report and configured coverage gate                           | backend `verify`                                    |
| OpenAPI contract        | Export backend API description for contract review                   | backend `-DskipOpenApiContract=false verify`        |
| Release rehearsal       | Docker, migration, backup, DAST, and capacity checks                 | `docs/RELEASE_PRECHECK_RUNBOOK.md`                  |

## Recommended Local Gate

From the repository root, run:

```powershell
npm run quality
```

If the local default `node` is not Node 22, use:

```powershell
npm run quality:node22
```

This runs:

1. Toolchain-version alignment.
2. Frontend lint.
3. Frontend format check.
4. Frontend unit tests with V8 thresholds: at least 70% statements, 72% branches, 50% functions, and 70% lines.
5. Frontend typecheck.
6. Frontend production build.
7. Frontend bundle budget check.
8. Backend `verify`, which runs tests once and enforces JaCoCo.
9. Backend SpotBugs.
10. Backend coverage gate: at least 78% instruction and 54% branch coverage.
11. Backend OpenAPI export.

For route, auth, preview-mode, or browser-only behavior changes, also run:

```powershell
npm run quality:frontend:e2e
```

For a release-candidate check, run:

```powershell
npm run quality:release
```

Use `npm run quality:release:node22` on a workstation whose default Node is
not Node 22.

The release gate additionally runs Playwright smoke, renders production and
backend Compose configuration, validates Flyway/Hibernate against a disposable
MySQL 8.4 database, performs root/frontend production dependency audits, and
runs a Trivy HIGH/CRITICAL filesystem scan. It uses a local Trivy binary when
available and otherwise falls back to the official Trivy Docker image; if both
Trivy and Docker are unavailable, record CI scan evidence rather than marking a
skipped scan as a pass.

Real AI, Stripe, and SMTP end-to-end tests require external test credentials
and callback infrastructure. Before recording live-provider E2E evidence, run:

```powershell
npm run quality:external:precheck
```

This command validates that AI, Stripe test-mode, and SMTP variables are present
and structurally safe without printing secrets. A failure here means the live
provider smoke is blocked by missing credentials or callback setup; do not
replace that evidence with mock/no-op flows.

## Backend Validation Focus

Backend tests should cover:

- Auth login, logout, refresh, password reset, role mapping, lockout, and public auth rate limits.
- IDOR-sensitive student/teacher/admin access paths.
- DTO normalization and validation, including password byte-length and safe validation error exposure.
- AI availability, quota, provider failure, input sanitization, usage logging, and report workflows.
- AI provider failures must preserve `UNAVAILABLE`/`FAILED` status and must not return fake generated content as a successful live response.
- Stripe webhook signature verification, supported event filtering, idempotent payment settlement, entitlements, refunds, and teacher earnings.
- File upload validation for documents, AI audio/image, and recorded videos.
- Flyway/JPA schema contract assumptions.
- Production guards for JWT, CORS, AI, SMTP, Stripe, payment URLs, and forwarded-proxy configuration.

## Frontend Validation Focus

Frontend tests and smoke checks should cover:

- Route guards for public, student, teacher, admin, unauthorized, and not-found routes.
- Session storage behavior for real login and preview login.
- Preview fallbacks only render when demo data, preview mode, and a preview session are all active.
- Preview AI samples must display sample/no-live-request copy and must not write live data.
- API error display and session-expiry cleanup.
- Student learning, practice, quiz, achievements, rewards, classes, profile, and AI helper flows.
- Teacher content, class, student progress, feedback, profile, and earnings flows.
- Admin user, verification, content, moderation, finance, analytics, activity library, and settings flows.
- Responsive UI behavior and absence of overflow on common desktop/tablet/mobile widths.
- No emoji/text pseudo-icon residues in polished production UI unless they are real learning content.

## Contract Testing

API contracts are enforced through a combination of:

- Backend DTO validation tests.
- OpenAPI export via Maven.
- Frontend TypeScript types in `frontend/src/types/api.ts`.
- Typed wrappers in `frontend/src/api/services.ts`.
- Playwright smoke flows for high-value user journeys.

When changing any backend response shape:

1. Update backend DTOs and tests.
2. Export or inspect the OpenAPI contract.
3. Update `frontend/src/types/api.ts` and affected API wrapper methods.
4. Add or update frontend tests for the consuming page/store.
5. Record the change in `CHANGELOG.md` if it affects external behavior.

## Release Evidence Checklist

Before final submission or production promotion, keep evidence for:

- `npm run quality` success, including Node 22 toolchain alignment, frontend V8 coverage, bundle budget, and backend JaCoCo thresholds.
- `npm run quality:frontend:e2e` success when browser flows changed.
- root and frontend `npm audit --audit-level=high --omit=dev` success.
- `docker compose --env-file .env.prod -f docker-compose.prod.yml config` success with release-candidate values.
- Flyway clean-install validation against disposable MySQL 8.4.
- Backup and restore rehearsal output.
- DAST ZAP baseline review.
- Trivy/CI HIGH/CRITICAL dependency and filesystem scan output.
- Manual smoke evidence for student, teacher, admin, AI, checkout, and password reset paths.

## High-Risk Change Rules

For these areas, add targeted tests instead of relying only on broad smoke checks:

- Authentication, refresh-token, password reset, or role changes.
- Payment, refund, entitlement, teacher earning, or webhook changes.
- AI provider, quota, prompt construction, availability, or reporting changes.
- File upload, storage, download, or external URL changes.
- Migration, repository, lock, or transaction-boundary changes.
- CORS, forwarded proxy, response header, CSP, or production guard changes.

## Manual QA Notes

Manual QA should use realistic learner/teacher/admin scenarios:

- Register or preview a learner, start a lesson, open a recommended practice activity, complete quiz flow, view rewards, and ask SparkBot.
- Register or preview a teacher, create/update learning content, schedule a class, upload or link a recording, and review student progress.
- Use admin pages to verify teacher state, review content, inspect AI reports, check finance, and update safe settings.
- Confirm logout clears sensitive session state and browser back navigation does not reveal authenticated data.

## Known Testing Boundaries

- Hosted CI does not replace a full real-provider production rehearsal.
- Local E2E smoke tests can validate routing and preview flows, but live Stripe/SMTP/AI behavior still requires test-provider credentials and callback URLs.
- PWA/service-worker behavior must be checked in a browser build/preview environment, not only through unit tests.
- Multi-instance deployment behavior requires a dedicated shared-storage/shared-rate-limit environment.
