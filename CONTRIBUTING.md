# Contributing

## Development Flow

1. Create a focused branch and keep unrelated refactors out of functional fixes.
2. Start the local MySQL service from `backend/docker-compose.yml`.
3. Run `backend\mvnw.cmd test` and `frontend\npm run build` before requesting review.
4. Never commit `.env` files, API keys, JWT secrets, exported student data, or uploaded media.

## API And Security Changes

- Treat authentication, payment, AI usage charging, content moderation, and child data handling as review-sensitive code.
- Keep outbound AI, email, payment, and file-service network calls outside long database transactions.
- Add tests for authorization decisions, limits, schema changes, and security header changes.
- Use explicit origins, methods, and headers when CORS behavior changes.

## Flyway Policy

- `V1__consolidated_schema.sql` is the repository baseline.
- `V14__final_database_contract_hardening.sql` is an applied follow-up migration and must remain immutable after deployment.
- Add new migrations starting at `V15`; never rename, edit, or delete an applied migration to make a checksum pass.
- Test both a clean installation and an upgrade from the latest deployed schema before release.

## Production Checklist

- Use unique production secrets and HTTPS callback URLs.
- Keep Swagger and Prometheus private unless access is authenticated or network-restricted.
- Confirm database backups and restore testing.
- Use shared object storage and shared rate limiting before scaling the backend horizontally.
