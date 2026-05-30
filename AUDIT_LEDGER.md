# Production Audit Ledger

Date: 2026-05-30
Repository: Chinese Learning Platform
Scope: frontend, backend, database migrations, API contracts, security, privacy, commerce, AI, uploads, notifications, moderation, audit logs, analytics, deployment, and documentation.

## Ledger Status

This ledger was created because the user-requested root ledgers were not present in the repository:

- `REPOSITORY_AUDIT_LEDGER.md`: not found.
- `FEATURE_COMPLETION_LEDGER_2026-05-20.md`: not found.
- `AUDIT_LEDGER.md`: created in this pass.

Real code and runnable validation are treated as the source of truth. Documentation is corrected or flagged when it diverges from runtime behavior.

## Coverage Matrix

| Area                                | Coverage in this pass | Evidence                                                                                                                                                                                                                  |
| ----------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Repository structure                | Covered               | Root, frontend, backend, docs, scripts, Docker, CI, performance, and generated-output boundaries inspected.                                                                                                               |
| Frontend shared layers              | Covered               | Router, auth store, API client, env validation, demo/preview gating, types, QA harness, build/test scripts inspected.                                                                                                     |
| Backend shared layers               | Covered               | Security config, CORS, JWT/refresh auth, exception handling, validation, payment/email/AI/upload config, logging redaction, audit service, and OpenAPI export inspected.                                                  |
| Database/Flyway/JPA                 | Covered and validated | Consolidated Flyway baseline, Hibernate validate settings, entity/repository contract tests, MySQL compose, live MySQL startup, and Flyway validation script inspected and run.                                           |
| API contract                        | Covered               | Backend OpenAPI export was run through the repository quality gate; frontend API wrappers and shared API types were inspected for hand-maintained contract risk; static endpoint literals matched exported OpenAPI paths. |
| Auth/authorization                  | Covered               | Public auth throttling, lockout, role guards, default-deny security config, refresh-token flow, and negative-path tests inspected.                                                                                        |
| AI                                  | Covered               | Student AI controller, provider routing, availability gate, usage limiter, prompt sanitizer tests, admin AI report/usage surfaces, and fallback behavior inspected.                                                       |
| Commerce/payments/refunds/payouts   | Covered               | Payment provider routing, Stripe webhook handling, checkout/refund/payout services, production guards, idempotency tests, and redirect defaults inspected.                                                                |
| Uploads/downloads/files             | Covered               | Upload validation, local storage provider, rollback cleanup, private access level, download response headers, and body-size limits inspected.                                                                             |
| Notifications/moderation/audit logs | Covered               | Notification failure handling, moderation endpoints/tests, audit log entity/service/DTO, redaction behavior, and admin audit surfaces inspected.                                                                          |
| Public/student/teacher/admin roles  | Covered               | Route metadata, router guards, role-specific controllers, dashboards, learning/content/commerce flows, and role negative-path tests inspected.                                                                            |
| Build/config/deployment             | Covered               | Vite, Maven, Docker/Compose, nginx, production profiles, external E2E readiness, and CI workflow structure inspected; local MySQL compose was started successfully.                                                       |
| Documentation                       | Covered               | README/docs/runbooks searched for port and runtime drift; this ledger records findings and blockers.                                                                                                                      |

## Findings And Status

| Severity | Finding                                                                                                                                                                     | Files/objects                                                                                                                                                                                                                       | Status              | Evidence                                                                                                                                                                                                        |
| -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| High     | Local frontend URL contract drift sent payment and password-reset flows to `localhost:5173` while Vite now runs on strict `5174`.                                           | `backend/src/main/resources/application.yml`, `backend/src/main/resources/application-example.yml`, `backend/.env.example`, `backend/.env.smtp.local.example`, `PaymentProperties`, `EmailProperties`, related payment/email tests. | Fixed in this pass. | `rg "localhost:5173"` showed callback/default drift before the fix; CORS entries remain intentional.                                                                                                            |
| Medium   | User-requested audit ledgers were absent, leaving no canonical evidence trail for this quality gate.                                                                        | Repository root.                                                                                                                                                                                                                    | Fixed in this pass. | `rg --files` found no root audit ledger before this file was created.                                                                                                                                           |
| Medium   | Release-gate Flyway repair validation depended on nested `ConvertTo-SecureString`, which failed in this Windows no-profile validation path.                                 | `backend/scripts/validate-flyway-mysql.ps1`, `Invoke-RepairScript`.                                                                                                                                                                 | Fixed in this pass. | `npm run quality:release` exposed the failure during Flyway checksum repair validation; replacing the nested command with direct .NET `SecureString` construction allowed the full release gate to pass.        |
| Medium   | Frontend endpoint wrappers previously relied on manual OpenAPI comparison, and one finite moderation action wrapper hid five concrete backend routes behind a dynamic path. | `frontend/src/api/services.ts`, `scripts/check-frontend-openapi-contract.mjs`, `package.json`, backend OpenAPI export.                                                                                                              | Fixed in this pass. | `npm run quality:contracts` now checks frontend endpoint signatures against exported OpenAPI; it passes with 189 frontend signatures against 220 OpenAPI signatures after moderation routes were made explicit. |
| Low      | Browser QA harness comment contradicted the actual `5174` default.                                                                                                          | `frontend/scripts/run-browser-qa-flows.mjs`.                                                                                                                                                                                        | Fixed in this pass. | Comment now states that `5174` matches local Vite and the harness walks forward when busy.                                                                                                                      |
| Low      | Student AI controller Javadoc incorrectly described admin AI endpoints as part of the same controller.                                                                      | `backend/src/main/java/com/example/aichinese/controller/AiController.java`.                                                                                                                                                         | Fixed in this pass. | Comment now points admin AI surfaces to admin controllers.                                                                                                                                                      |

## Implemented Improvements

- Aligned local payment and password-reset defaults to `http://localhost:5174` while preserving the deliberate CORS local-port range.
- Updated affected backend payment/email tests to expect the same local frontend default as the Vite dev server.
- Hardened the Flyway validation script so checksum-repair dry-run/execute checks build the nested secure password without relying on `ConvertTo-SecureString` module autoloading.
- Added `npm run quality:contracts`, wired it into the root quality gate, and made the admin moderation API wrapper enumerate its five concrete backend routes.
- Corrected misleading QA and AI-controller documentation comments.
- Added this root audit ledger so future audits have a stable place to record code/doc drift, verification evidence, and blockers.

## Verification Evidence

Baseline validation before source changes:

- `npm --prefix frontend run lint`: passed.
- `npm --prefix frontend run security:check`: passed.
- `npm --prefix frontend run format:check`: passed.
- `npm --prefix frontend run test:unit:coverage`: passed, 103 tests.
- `npm --prefix frontend run typecheck`: passed.
- `npm --prefix frontend run build`: passed.
- `npm --prefix frontend run budget:check`: passed.
- `.\mvnw.cmd test`: passed, 519 tests.
- `.\mvnw.cmd -DskipTests package`: passed.
- `.\mvnw.cmd -DskipTests spotbugs:check`: passed, zero findings.
- `npm run quality:backend`: passed, including backend coverage gate, SpotBugs, and dynamic-port OpenAPI export.

Post-change validation:

- `npm run quality:format`: passed after formatting this ledger.
- `npm --prefix frontend run lint`: passed.
- `npm --prefix frontend run security:check`: passed.
- `npm --prefix frontend run format:check`: passed.
- `npm --prefix frontend run test:unit:coverage`: passed, 23 test files and 103 tests.
- `npm --prefix frontend run typecheck`: passed.
- `npm --prefix frontend run build`: passed.
- `npm --prefix frontend run budget:check`: passed; latest release-gate run reported entry JS 75.78 KiB gzip / 86.00 KiB, CSS 51.15 KiB gzip / 58.00 KiB, largest route chunk 14.52 KiB gzip / 17.00 KiB.
- `npm run quality:frontend:e2e`: passed, 6 Chromium smoke tests.
- `docker compose up -d` from `backend/`: passed after Docker Desktop became available; `ai-chinese-learning-mysql` is healthy on `127.0.0.1:3307`.
- `.\scripts\validate-flyway-mysql.ps1`: passed; fresh MySQL schema, Hibernate context startup, Flyway repair validation, and folded cleanup compatibility checks succeeded.
- Direct MySQL inspection: `flyway_schema_history` version `1` has `success=1`; schema has 62 tables; audit, notification, and AI-chat hot-path indexes are present.
- `.\mvnw.cmd test`: passed, 519 tests.
- `.\mvnw.cmd -DskipTests package`: passed.
- `npm run quality:backend`: passed, including coverage thresholds, 519 tests, SpotBugs zero findings, and dynamic-port OpenAPI export.
- Representative local route/API probes passed for health, OpenAPI, public settings/courses, guest auth, student/teacher/admin protected routes, AI availability protection, Stripe webhook fail-closed behavior, uploads, notifications, moderation, audit logs, and analytics.
- Static frontend endpoint contract spot check: 84 static endpoint literals in `frontend/src/api/services.ts` produced zero exact misses against the exported OpenAPI path set.
- `npm run quality:contracts`: passed; checked 189 frontend endpoint signatures against 220 OpenAPI signatures.
- `npm audit --omit=dev`: passed, 0 vulnerabilities.
- `npm --prefix frontend audit --omit=dev`: passed, 0 vulnerabilities.
- `npm run quality:release`: passed after the Flyway validation and contract hardening; this included frontend lint/security/coverage/typecheck/build/budget, backend 519-test coverage gate, SpotBugs zero findings, dynamic OpenAPI export, `quality:contracts`, Playwright smoke, dependency audits, Compose config checks, Flyway MySQL validation, and Dockerized Trivy filesystem scan.
- `npm run quality:external:precheck`: failed because live AI, Stripe, and SMTP credentials/callback variables are not configured; this is tracked as a blocker below.

## Remaining Risks And Blockers

| Priority | Risk/blocker                                                                          | Area                                                 | Impact                                                                                                                                              | Needed to clear                                                                                             |
| -------- | ------------------------------------------------------------------------------------- | ---------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| High     | Live external-provider E2E is not proven without credentials and reachable providers. | AI, Stripe, SMTP, object storage.                    | Real provider regressions can remain hidden behind local tests.                                                                                     | Provide test credentials/callback endpoints and run external E2E readiness plus live flows.                 |
| Medium   | Multi-instance production behavior depends on deployment architecture.                | Upload storage, rate limiting, async email dispatch. | Local disk uploads, process-local throttles, and in-process email dispatch need external infrastructure for horizontally scaled production.         | Add shared object storage, shared rate-limit store, and durable outbox/queue before multi-instance rollout. |
| Low      | Live API/database probe with authenticated seeded users was not run.                  | Full authenticated live API plus DB reconciliation.  | Unit/integration tests and unauthenticated route probes passed, but a live seeded-user reconciliation pass would give stronger end-to-end evidence. | Seed or provide known QA credentials, then run `frontend/scripts/live-api-db-integration-probe.mjs`.        |

## Fresh-Pass Checklist

- Re-run targeted `rg` checks for `localhost:5173` callback drift.
- Re-run frontend typecheck/build, backend tests/package, and OpenAPI contract guard.
- MySQL/Flyway validation passed both directly and inside `npm run quality:release`.
- Repository quality gates passed where local services and credentials permitted; the final release gate exited with code 0 after contract automation was added.
- Remaining unverified areas are explicitly listed as blockers above.

## Final Assessment

The repository is signed off for the current local/repository quality gate context. It is not signed off for live production launch until external-provider E2E credentials, callback endpoints, and multi-instance infrastructure decisions are available and verified.

Overall residual risk is low for in-repository build/test/security/migration correctness, and medium for full production readiness because real AI, Stripe, SMTP, object storage, and scaled deployment behavior remain environment-dependent.
