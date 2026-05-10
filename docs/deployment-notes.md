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

## Recommendation
For FYP demonstration, use the documented local setup rather than presenting the project as production-hosted unless a separate deployment workflow is prepared and verified.
