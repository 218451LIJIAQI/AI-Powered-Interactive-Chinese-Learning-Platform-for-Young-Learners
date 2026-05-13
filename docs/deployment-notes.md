# Deployment Notes

## Purpose
This document describes the project’s current deployment posture for demonstration and local verification. It is intentionally prototype-focused rather than production-hardened.

## Local Demonstration Deployment
- Frontend runs through Vite on `http://localhost:5173`
- Backend runs through Spring Boot on `http://localhost:8080`
- MySQL runs through Docker Compose on `localhost:3307`

## Required Services
- Docker Desktop for MySQL
- Java 21 for backend runtime
- Node.js and npm for frontend runtime

## Environment and Secrets
- All secrets must stay in backend-local environment configuration only
- Do not store JWT secrets, SMTP credentials, AI keys, Stripe secrets, or database credentials in frontend code
- Use placeholder examples in documentation only

## Demo-Friendly Fallback Behaviour
- If AI provider access is disabled, AI pages should show a friendly unavailable state
- If Stripe is disabled or not configured, payment flows should remain in documented sandbox or unavailable mode
- If SMTP is disabled, in-app notifications should continue to work
- If analytics data is sparse, dashboards should show zero values or empty states honestly

## Not Yet Included
- Production deployment hardening
- CI/CD release automation
- Managed secret storage
- Production-grade monitoring and alerting
- Production legal review
- Full external security audit

## Production Migration Checklist (before any production deployment)
- Change `spring.jpa.hibernate.ddl-auto` from `update` to `validate` and adopt a real schema migration tool (Flyway / Liquibase). The current `update` value is convenient for prototype iteration but is unsafe for production data because Hibernate may silently rewrite or drop columns when entity definitions change.
- Switch the default `DB_PASSWORD` away from `root`; the local default exists solely so the bundled `docker-compose.yml` MySQL container is reachable for FYP demos.
- Rotate the `JWT_SECRET` to a high-entropy value (at least 32 bytes) sourced from a managed secret store; the `SecurityProperties` validator already refuses to start with a missing or too-short secret.
- Set `AI_ENABLE_REAL_PROVIDER`, `PAYMENT_ENABLE_REAL_PROVIDER` and `SMTP_ENABLED` only after the corresponding real keys / credentials are configured and rate limits / quotas are understood.
- Replace the dev-only `start.ps1` ephemeral JWT generation path with a deployment pipeline that injects the secret from the secret store.

## Recommendation
For FYP demonstration, use the documented local setup rather than presenting the project as production-hosted unless a separate deployment workflow is prepared and verified.
