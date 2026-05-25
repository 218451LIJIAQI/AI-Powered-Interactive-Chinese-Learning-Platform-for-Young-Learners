# Operations Runbook

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

## DAST

Run the GitHub Actions `DAST ZAP Baseline` workflow manually against a pre-production HTTPS URL. Add accepted false positives to `.zap/rules.tsv` with a reason and review date.

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
