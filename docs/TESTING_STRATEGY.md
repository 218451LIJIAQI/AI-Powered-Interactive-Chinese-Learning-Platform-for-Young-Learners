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
| Frontend lint           | ESLint rules for TypeScript/Vue quality                              | `npm --prefix frontend run lint`                    |
| Frontend format         | Prettier consistency for source/config/docs                          | `npm --prefix frontend run format:check`            |
| Frontend unit           | Pinia stores, utility helpers, route helpers, formatting helpers     | `npm --prefix frontend run test:unit`               |
| Frontend typecheck      | Vue + TypeScript compile-time contract check                         | `npm --prefix frontend run typecheck`               |
| Frontend build          | Production Vite build and PWA generation                             | `npm --prefix frontend run build`                   |
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

This runs:

1. Frontend lint.
2. Frontend format check.
3. Frontend unit tests.
4. Frontend typecheck.
5. Frontend production build.
6. Backend tests.
7. Backend SpotBugs.
8. Backend coverage gate.
9. Backend OpenAPI export.

For route, auth, preview-mode, or browser-only behavior changes, also run:

```powershell
npm run quality:frontend:e2e
```

For a release-candidate check, run:

```powershell
npm run quality:release
```

## Backend Validation Focus

Backend tests should cover:

- Auth login, logout, refresh, password reset, role mapping, lockout, and public auth rate limits.
- IDOR-sensitive student/teacher/admin access paths.
- DTO normalization and validation, including password byte-length and safe validation error exposure.
- AI availability, quota, provider failure, input sanitization, usage logging, and report workflows.
- Stripe webhook signature verification, supported event filtering, idempotent payment settlement, entitlements, refunds, and teacher earnings.
- File upload validation for documents, AI audio/image, and recorded videos.
- Flyway/JPA schema contract assumptions.
- Production guards for JWT, CORS, AI, SMTP, Stripe, payment URLs, and forwarded-proxy configuration.

## Frontend Validation Focus

Frontend tests and smoke checks should cover:

- Route guards for public, student, teacher, admin, unauthorized, and not-found routes.
- Session storage behavior for real login and preview login.
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

- `npm run quality` success.
- `npm run quality:frontend:e2e` success when browser flows changed.
- `docker compose --env-file .env.prod -f docker-compose.prod.yml config` success with release-candidate values.
- Flyway clean-install validation against disposable MySQL 8.4.
- Backup and restore rehearsal output.
- DAST ZAP baseline review.
- Dependency/container scan output.
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
