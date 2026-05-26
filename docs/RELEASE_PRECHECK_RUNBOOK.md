# Release Precheck Runbook

Run this checklist before promoting a build to production or a long-lived
pre-production environment. The GitHub Actions quality gate covers hosted-safe
checks; the remaining commands exercise local Docker, browser, database, and
operational paths that need an explicit release rehearsal.

## Release Candidate Inputs

Prepare the following before running the checklist:

- target Git commit SHA and release label
- public frontend HTTPS URL
- production or pre-production CORS origin list
- AI provider, model name, quota, and test prompt
- Stripe mode, webhook endpoint, and callback URLs
- SMTP relay, from address, and password-reset URL
- database backup location and retention policy
- rollback owner and communication channel

## Local Quality Gate

From the repository root:

```bash
npm run quality
```

This runs frontend linting, formatting, unit tests, type checking, production
build, backend tests, SpotBugs, coverage verification, and OpenAPI export.

Run the browser smoke suite after installing Playwright browsers:

```bash
cd frontend
npx playwright install chromium
npm run test:e2e
```

Required evidence:

- command exit code
- test summary
- target commit SHA
- any skipped tests and reason

## Database Migration Gate

Validate the consolidated Flyway baseline against a disposable MySQL 8.4
container:

```powershell
cd backend
.\scripts\validate-flyway-mysql.ps1
```

Record the generated database name, Flyway validation result, and repair-path
result in the release notes.

Do not promote if:

- Hibernate validation fails against the migrated schema
- Flyway reports pending, failed, or checksum-mismatched migrations
- a production-applied migration was edited to force a checksum match
- a destructive migration step has no backup/restore rehearsal

## Production Compose Dry Run

Create a throwaway environment file from `.env.prod.example`, fill non-secret
placeholders with release-candidate values, and validate the rendered Compose
configuration:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml config >/dev/null
```

Do not promote if any production guard requires `localhost`, test payment keys,
disabled SMTP, disabled real AI provider mode, or missing HTTPS callback URLs.

Also confirm:

- `FRONTEND_PROXY_IP` belongs to `APP_NETWORK_SUBNET`
- `APP_SECURITY_FORWARDED_TRUSTED_PROXIES` contains only trusted proxy IPs/CIDRs
- `APP_CORS_ALLOWED_ORIGINS` contains explicit HTTPS origins only
- `MANAGEMENT_EXPOSED_ENDPOINTS` does not expose Prometheus publicly
- `JWT_SECRET`, database passwords, Stripe secrets, AI keys, and SMTP credentials are not committed

## Operational Rehearsals

- Run a backup and restore rehearsal using `docs/OPERATIONS_RUNBOOK.md`.
- Run the k6 smoke-capacity script against pre-production and record P95
  latency, error rate, AI 429 rate, Hikari pool utilisation, CPU, memory, and
  provider latency.
- Run the manual `DAST ZAP Baseline` workflow against a pre-production HTTPS
  URL and review `.zap/rules.tsv` suppressions.
- Confirm object storage, shared rate limiting, and durable email/outbox
  strategy before horizontal backend scaling.

## Functional Smoke Checklist

Run at least one happy-path smoke for each area:

- public landing page, catalogue, sign-in, forgot-password, and reset-password link rendering
- student home, learn, practice, quiz, classes, achievements, rewards, AI helper, profile, and logout
- teacher dashboard, content editing, class management, student progress, feedback, profile, and earnings
- admin dashboard, users, teacher verification, content review, moderation, finance, analytics, activity library, and settings
- AI availability, one text-generation flow, and one AI report submission
- checkout redirect creation, webhook settlement in test mode, entitlement visibility, and refund request
- uploaded document/media validation for accepted and rejected files

## Rollback Readiness

Before promotion, document:

- previous deployable image/tag/commit
- database backup timestamp and restore command
- whether the release contains a schema change
- how to disable real AI provider calls temporarily
- how to disable Stripe checkout temporarily
- how to disable SMTP sending temporarily
- who approves rollback and who communicates user-facing impact

## Final Go/No-Go Summary

Record the result in the release notes:

| Gate                           | Result  | Evidence                  |
| ------------------------------ | ------- | ------------------------- |
| Root quality gate              | Pending | command output            |
| Browser E2E smoke              | Pending | Playwright output         |
| Flyway/MySQL validation        | Pending | validation log            |
| Compose config render          | Pending | command output            |
| Backup/restore rehearsal       | Pending | backup file + restore log |
| DAST/dependency/container scan | Pending | scan summary              |
| Manual high-risk smoke         | Pending | tester notes              |
| Rollback plan                  | Pending | owner + timestamp         |
