# Security Best Practices Review

Date: 2026-05-28
Scope: Vue/Vite frontend, Spring Boot API, Nginx production edge, Flyway production configuration, payment and live-learning state flows.

## Executive Summary

The repository already has meaningful security foundations: bearer access tokens are memory-only, refresh tokens are HttpOnly cookies, API responses are excluded from PWA runtime caching, sensitive downloads are served with restrictive headers, Stripe signatures are verified before settlement, and production startup guards cover several unsafe configurations.

This review fixed two production-boundary vulnerabilities, one business-integrity defect, and the follow-up quality-hardening pass tightened toolchain, AI fallback, preview-mode, and release-gate controls. Remaining risks are deployment and maintainability constraints that require planned architecture work rather than an unsafe broad rewrite.

## Fixed Findings

### SEC-001 High: Public Webhook Body Was Bounded Only After Binding

Location: `backend/src/main/java/com/example/aichinese/controller/PaymentController.java`, `backend/src/main/java/com/example/aichinese/service/StripeWebhookService.java`, `frontend/nginx.conf`

The unauthenticated Stripe webhook previously accepted the request body as a `String` before applying the 256 KiB byte validation. Direct backend access or the generic Nginx API limit could force substantially larger anonymous request allocation before signature rejection.

Resolution:

- Added `StripeWebhookPayloadSizeFilter`, which caps the exact webhook route before Spring body binding, including requests without a declared content length.
- Replays only accepted bounded raw bytes so HMAC verification receives an unchanged payload.
- Reuses `StripeWebhookService.MAX_WEBHOOK_PAYLOAD_BYTES` as the application invariant.
- Added an exact Nginx webhook location with `client_max_body_size 256k`.
- Added tests for declared length, unknown length, raw payload preservation, context paths, and unrelated endpoint isolation.

### SEC-002 High: Production Could Baseline an Unversioned Non-Empty Database

Location: `backend/src/main/resources/application-prod.yml`

The base configuration enables Flyway baseline-on-migrate for pre-production baseline work. Production did not override it, allowing a wrongly connected non-empty database without schema history to be accepted as baseline and bypass the intended migration validation path.

Resolution:

- Production now explicitly sets `spring.flyway.baseline-on-migrate: false`.
- Configuration tests and release documentation now pin valid Flyway history as a promotion requirement.

### INT-001 Medium: Attended or Started Live-Class Enrolments Could Be Cancelled

Location: `backend/src/main/java/com/example/aichinese/service/LiveLearningAccessService.java`, `backend/src/main/java/com/example/aichinese/service/LiveClassService.java`, `frontend/src/pages/student/StudentClassesPage.vue`

The domain model defines `ATTENDED`, `MISSED`, and `CANCELLED` as terminal reservation outcomes, but the cancellation command accepted the broader access-oriented enrolment rule. This could rewrite attendance history and corrupt learner/teacher analytics.

Resolution:

- Added a dedicated cancellation policy: only an `ENROLLED` reservation for a future `SCHEDULED` or `RESCHEDULED` class may be withdrawn.
- Aligned the student UI control with that backend invariant.
- Added tests for allowed future cancellation and rejected attended/started cases.

### AI-001 Medium: Live AI Failures Could Resemble Successful Generated Quiz Content

Location: `backend/src/main/java/com/example/aichinese/service/AiService.java`, `backend/src/test/java/com/example/aichinese/AiServicePromptTests.java`

Invalid provider JSON in quiz generation previously fell back to sample quiz questions. That was acceptable only for explicit preview/demo surfaces; for authenticated live users it risked making provider failure look like successful AI generation.

Resolution:

- Quiz provider parse failures now return `FAILED` with an empty question list and the existing `status` field rather than fake generated quiz content.
- Added a changed-path backend test proving invalid provider JSON is logged as failed usage and does not produce sample questions.
- Frontend preview samples remain allowed only in demo/preview sessions and keep no-live-request copy visible.

### SEC-003 Medium: Preview Role Cleanup Was Session-Incomplete

Location: `frontend/src/stores/auth.ts`, `frontend/src/stores/auth.test.ts`

Invalid preview roles were removed from legacy local storage but could remain in session storage until overwritten. Although they did not grant access because role validation failed, stale synthetic session state could confuse QA and weaken the "preview is explicit and ephemeral" contract.

Resolution:

- Invalid preview roles are now removed from both local and session storage.
- Added store tests for disabled preview mode, invalid persisted roles, and token-free preview sessions.

## Verified Controls

- Frontend auth stores access tokens in memory and uses scoped HttpOnly refresh cookies.
- Node.js tooling is locked to major version 22 across `.nvmrc`, `.node-version`, root/frontend `engines`, Docker, and CI; the local toolchain gate fails on Node 26+ by design.
- Frontend coverage gates are raised to 70% statements, 72% branches, 50% functions, and 70% lines.
- Backend JaCoCo gates are raised to 78% instruction and 54% branch.
- Release gate runs Trivy HIGH/CRITICAL filesystem scanning through a local Trivy binary or the official Trivy Docker image instead of silently skipping vulnerability scanning.
- PWA runtime caching excludes `/api/**` traffic.
- External URL opening and downloaded filenames are normalized/sanitized.
- Production Nginx applies CSP and browser security headers.
- Backend authorization is default-deny with role and ownership checks on protected surfaces.
- Private file responses use centralized restrictive cache and content-disposition handling.
- Payment settlement remains signature-verified and idempotency-oriented.
- Workstations outside the required Node 22 line can run the reproducible `quality:node22` / `quality:release:node22` wrappers, so local Node 26 does not block evidence collection.
- `quality:external:precheck` now fails closed when real AI, Stripe test-mode, or SMTP E2E credentials/callbacks are missing, preventing mock/no-op paths from being reported as live-provider validation.
- `npm audit --audit-level=high` reported zero vulnerabilities for frontend production/dev dependencies and root production dependencies during this review.

## Residual Risks And Required Governance

### RISK-001 Medium: Horizontal Scaling Has Explicit Single-Instance Dependencies

Uploads use local storage, authentication throttles remain process-local, and email delivery is not a durable outbox. AI quota counters are already database-coordinated, so documentation was corrected to avoid unnecessary redesign, but contention must be capacity-tested.

Required before multi-instance production: object storage, shared authentication throttling, durable delivery where notification loss is unacceptable, rolling-deploy rehearsal, and AI quota contention evidence.

### RISK-002 Conditional Medium: Database Transport Depends On Internal-Network Deployment

The default JDBC URL sets `useSSL=false`. That is only acceptable while MySQL is isolated on the controlled Compose network. Any managed database or cross-host topology must use an environment-specific TLS JDBC URL and certificate-verification policy before promotion.

### RISK-003 Low/Medium: CSP Still Permits Inline Styles

The frontend CSP correctly blocks inline scripts but retains `style-src 'unsafe-inline'`, reflecting extensive component styling patterns. No unsafe HTML/script DOM sink was identified in the reviewed production frontend sources. Long-term hardening should migrate inline styling patterns toward classes or controlled CSS variables so the CSP can be narrowed without a disruptive rewrite.

### ENG-001 Maintainability: Oversized Frontend Feature Units Raise Change Risk

Multiple Vue pages exceed 1,000 lines, and the central stylesheet is substantially larger. These units increase review cost, merge contention, and the chance that authorization-visible UI state drifts from API contracts. Extract feature-scoped composables/components and CSS modules incrementally, guarded by route and contract tests.

The production build observed during this review generated a shared CSS asset of 293.51 kB (52.57 kB gzip) and an entry JavaScript asset of 251.16 kB (77.10 kB gzip). Treat bundle budgets and route-level extraction as measurable follow-up work.

### ENG-002 Data Contract: Live-Class Times Are Zone-Free

Live-class requests use `LocalDateTime` values without an offset or named timezone. Preserving a teacher-selected wall-clock time avoids an unintended UTC conversion, but it does not define behavior for teachers and learners in different timezones. Before multi-region or international production use, introduce an explicit canonical timezone or offset-bearing API contract, migrate persisted values deliberately, and add cross-timezone display/join tests.

### BLOCK-001 External Provider E2E Requires Test Credentials

The repository can structurally validate AI, Stripe test-mode, and SMTP readiness, but the current local environment does not expose `AI_API_KEY`, `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, SMTP credentials, or provider callback/forwarding values. Live-provider E2E must remain blocked until `npm run quality:external:precheck` passes and the corresponding smoke flows are executed against sandbox/test services.

## Validation Evidence

- Baseline before changes: `npm run quality` succeeded, including frontend lint/format/unit/type/build and backend tests, SpotBugs, coverage, and OpenAPI export.
- Targeted changed-path verification: backend tests for migration configuration, webhook pre-binding limits, and live-learning access rules passed (`22` tests, `0` failures).
- Frontend verification after the UI invariant change: Prettier check and TypeScript typecheck passed.
- Full post-change release gate passed under Node 22: frontend lint/format/`102` unit tests/typecheck/build/bundle budget, backend tests (`512` tests), SpotBugs (`0` findings), JaCoCo verification, OpenAPI contract export, Playwright smoke (`6` tests), npm production audits (`0` vulnerabilities), Compose config rendering, Flyway MySQL validation, and Trivy filesystem scan (`0` findings).
- A concurrent teacher-page update was normalized with the repository Prettier rule; the current frontend snapshot then passed lint/format/`71` unit tests/typecheck/build again.
- Playwright browser smoke passed on the final formatted frontend snapshot (`4` tests, `0` failures).
- Production and backend Docker Compose configuration parsed successfully using CI-equivalent non-secret placeholder values; unfilled required production variables fail closed during interpolation.
- Auth-session regression tests now cover successful refresh hydration, failed `/auth/me` after refresh, failed logout cleanup, token-free preview sessions, and legacy storage cleanup.
- External provider readiness check intentionally fails without sandbox/test credentials, making the remaining live AI/Stripe/SMTP E2E blocker explicit instead of allowing fake completion.
