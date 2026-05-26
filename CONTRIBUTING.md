# Contributing

## Development Flow

1. Create a focused branch and keep unrelated refactors out of functional fixes.
2. Start the local MySQL service from `backend/docker-compose.yml`.
3. Run `npm run quality` from the repository root before requesting review.
4. Run `npm run quality:frontend:e2e` when a change affects routing, auth, major UI flows, or browser-only behaviour.
5. Never commit `.env` files, API keys, JWT secrets, exported student data, or uploaded media.

## Change Scope

- Keep each change tied to a clear user-facing or operational outcome.
- Prefer small, reviewable commits for security, payment, AI, schema, and auth work.
- Do not mix broad formatting, generated files, dependency updates, and business logic changes unless the change explicitly requires it.
- Preserve existing route names, API envelope shapes, role semantics, and database contracts unless the breaking change is documented and tested.

## API And Security Changes

- Treat authentication, payment, AI usage charging, content moderation, and child data handling as review-sensitive code.
- Keep outbound AI, email, payment, and file-service network calls outside long database transactions.
- Add tests for authorization decisions, limits, schema changes, and security header changes.
- Use explicit origins, methods, and headers when CORS behavior changes.
- Update `docs/API_VERSIONING_POLICY.md` and `CHANGELOG.md` when API behavior changes.
- Ensure user-facing validation errors do not disclose secrets, reset tokens, bearer tokens, provider keys, file paths, or internal stack traces.

## Frontend Changes

- Keep authenticated tokens in session-scoped storage only; preview mode must never create or persist a real bearer token.
- Use the shared API service layer instead of ad hoc `fetch`/Axios calls unless a streaming or download endpoint requires special handling.
- Validate route query parameters through shared helpers before using them to select a flow.
- Use real icon components instead of text pseudo-icons for production UI polish.
- Run the Playwright smoke suite for route guards, preview login, and major learner/teacher/admin flow changes.

## Backend Changes

- Put imports at the top of files and keep controllers thin: validation and HTTP semantics in controllers, business rules in services.
- For IDOR-sensitive operations, use ownership-scoped repository methods or explicit service-level ownership checks.
- Use short transactions around database writes; do not keep provider HTTP calls inside long write transactions.
- Add targeted tests when changing DTO validation, provider adapters, production guards, filters, repository queries, or payment state transitions.
- Keep sensitive `toString`, logs, audit descriptions, and exception messages redacted.

## Flyway Policy

- `V1__consolidated_schema.sql` is the repository baseline.
- Before the first long-lived production deployment, accepted schema changes are folded back into this single consolidated V1 baseline.
- After the baseline is frozen against production data, every schema change must be forward-only in a new numbered migration. Never rename, edit, or delete an applied migration to make a checksum pass.
- Test a clean installation before release. After production freeze, also test an upgrade from the latest deployed schema before release.

## Documentation Updates

- Update `README.md` when setup, environment variables, quality gates, or deployment assumptions change.
- Update `docs/ARCHITECTURE.md` when a runtime boundary, data flow, provider, or scaling assumption changes.
- Update `docs/TESTING_STRATEGY.md` when adding/removing commands, test layers, or release evidence requirements.
- Update `docs/RELEASE_PRECHECK_RUNBOOK.md` when production promotion steps change.
- Update `docs/OPERATIONS_RUNBOOK.md` when backup, restore, capacity, DAST, CDN, PWA, or incident expectations change.

## Production Checklist

- Use unique production secrets and HTTPS callback URLs.
- Keep Swagger and Prometheus private unless access is authenticated or network-restricted.
- Confirm database backups and restore testing.
- Use shared object storage and shared rate limiting before scaling the backend horizontally.
- Follow `docs/RELEASE_PRECHECK_RUNBOOK.md` before production promotion.
- Record release evidence: quality gate output, migration validation, Compose config validation, E2E smoke result, backup/restore rehearsal, dependency/container scan, and manual high-risk flow checks.
