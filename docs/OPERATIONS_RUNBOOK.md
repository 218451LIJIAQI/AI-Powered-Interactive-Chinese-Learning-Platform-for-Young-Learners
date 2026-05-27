# Operations Runbook

This runbook captures operational procedures that should be rehearsed before production promotion and kept current when deployment assumptions change.

## Database Backup And Restore

Use `scripts/backup-db.sh` for self-managed MySQL:

```bash
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=ai_chinese_learning \
DB_USERNAME=aichinese_app DB_PASSWORD=... BACKUP_DIR=./backups \
./scripts/backup-db.sh
```

Run a restore rehearsal in a disposable database before every production release:

```bash
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=ai_chinese_learning_restore \
DB_USERNAME=aichinese_app DB_PASSWORD=... \
./scripts/restore-db.sh ./backups/ai_chinese_learning-YYYYMMDDTHHMMSSZ.sql.gz
```

Acceptance evidence to record:

- backup file name, size, and SHA-256 file
- restore target database
- Flyway validation result after restore
- smoke test result against the restored database

For managed MySQL, enable provider-native automated backups, at least 7 days retention, and one documented point-in-time restore rehearsal before launch.

Operational expectations:

- store backups outside the application container and outside the primary database host
- protect backup files with the same sensitivity as production data
- restrict restore permissions to trusted operators
- rehearse restore into a disposable database before relying on a backup strategy
- verify the restored schema with Flyway and run a smoke test against restored data

## Capacity Baseline

Install k6 and run:

```bash
BASE_URL=https://preprod.example.com \
TEST_STUDENT_EMAIL=student@example.com \
TEST_STUDENT_PASSWORD='...' \
K6_TARGET_VUS=25 \
k6 run performance/k6/smoke-capacity.js
```

Record P95 latency, error rate, AI 429 rate, Hikari pool utilisation, CPU, memory, and provider latency for the release checklist.

Recommended interpretation:

- rising AI 429 is acceptable only when it matches the configured quota behavior
- sustained Hikari saturation indicates database pool or query pressure
- high provider latency should be separated from backend processing latency
- payment/webhook paths should remain low-volume but highly reliable
- capacity results are only valid for the tested data volume and provider mode

## DAST

Run the GitHub Actions `DAST ZAP Baseline` workflow manually against a pre-production HTTPS URL. Add accepted false positives to `.zap/rules.tsv` with a reason and review date.

Do not run DAST against production student data unless the target, rate, and authorization scope are explicitly approved.

## CDN And Static Asset Caching

Serve the built frontend (`frontend/dist`) through a CDN in production.

Recommended cache policy:

- `assets/*.{js,css,woff2,svg,png,jpg,jpeg,webp,avif}`: `Cache-Control: public, max-age=31536000, immutable`
- `index.html`, `manifest.webmanifest`, service worker files: `Cache-Control: no-cache`
- authenticated `/api/**`: never cache at CDN
- private media downloads/streams: use the backend access-control endpoint or signed URLs; do not expose raw object keys publicly

Purge strategy:

- purge `index.html`, manifest, and service worker files on every frontend release
- keep hashed asset files immutable
- purge media only when replacing or revoking a public/signed asset

## PWA Offline Boundary

The service worker caches only the application shell and same-origin static
assets. It deliberately does not cache `/api/**` responses. Logout and auth
invalidation clear the app's offline caches so no learner can inherit another
learner's cached shell state on a shared device.

Future offline course packs must add explicit per-user authorization checks,
cache names scoped by user/session, and a logout purge test before being
enabled.

## Provider Operations

### AI Provider

- keep `AI_ENABLE_REAL_PROVIDER=true` only when a valid provider key and quota are configured
- monitor provider latency and user-facing unavailable responses
- rotate provider keys immediately if exposed
- temporarily disable real provider calls if content safety or quota behavior becomes unsafe
- do not ship fake generated content as if it came from a real AI provider

### Stripe

- verify webhook signatures and event shape before settlement
- keep webhook secrets out of source control and rotate them after exposure
- monitor failed webhook delivery and replay attempts
- confirm checkout success/cancel URLs are HTTPS and match the frontend domain
- reconcile paid orders, entitlements, refunds, and teacher earnings after incident recovery

### SMTP

- keep `SMTP_ENABLED=true` in production only with a tested relay
- use HTTPS password-reset links
- monitor delivery failures for password reset and teacher verification flows
- move to durable outbox/queue if delivery must survive process restarts

## Incident Triage

When an operational incident is suspected:

1. Identify impacted persona, route, provider, deployment version, and time window.
2. Capture request IDs, logs, health status, metrics, and provider dashboard evidence.
3. Redact secrets, tokens, learner data, and payment details before sharing evidence.
4. Decide whether to disable AI, Stripe checkout, SMTP, or uploads temporarily.
5. If data integrity may be affected, stop writes where possible and take a fresh backup before repair.
6. Patch with targeted tests and record the operational finding in release notes.
7. Run the relevant smoke checks before restoring normal service.

## Horizontal Scaling Readiness

Before running multiple backend instances:

- move uploads from local volume to shared object storage or a controlled shared file service
- move process-local authentication throttles to Redis or another shared store; AI quota counters are database-coordinated and must be load-tested for contention
- confirm refresh-token/session invalidation semantics across nodes
- replace in-process email dispatch with durable outbox/queue where required
- ensure logs and metrics include instance identity
- rehearse rolling deploy, rollback, and database migration sequencing

## Regular Maintenance Checklist

Run periodically or before important demonstrations:

- review `.env.prod.example` for outdated placeholder domains or missing variables
- confirm dependency and container scans have no high/critical findings
- confirm OpenAPI docs are not publicly exposed unless intentionally protected
- verify backup retention and restore evidence are current
- check that generated outputs such as `frontend/dist`, `backend/target`, and Playwright reports are not committed
- run `npm run quality` after dependency, config, security, payment, or AI changes
