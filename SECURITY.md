# Security Policy

## Scope

This policy covers the MandarinSpark frontend, backend API, database migrations, deployment configuration, operational scripts, and documentation in this repository.

## Reporting A Vulnerability

Do not disclose vulnerabilities, credentials, or learner data in a public issue. Report them privately through the repository security advisory channel or to the project maintainer/supervisor responsible for this deployment.

Include the affected endpoint or component, reproduction steps, impact, and any evidence with sensitive values removed. Reports involving child data, authentication, payment, file upload, or AI prompt/content safety should be treated as urgent.

## Supported Version

Security fixes are applied to the current maintained `main` branch and its active production deployment.

## Deployment Expectations

- Configure long random JWT and database secrets outside source control.
- Serve the application through HTTPS and restrict production CORS origins.
- Keep operational endpoints such as Prometheus and API documentation internal or authenticated.
- Store uploaded learner content in durable controlled storage with backups and access controls.
- Rotate compromised secrets immediately and invalidate affected sessions or provider tokens.

## Sensitive Areas

- **Authentication and sessions**: access tokens, refresh cookies, password reset tokens, role checks, lockouts, and session invalidation.
- **Child learner data**: names, contact details, progress, quiz results, AI conversations, uploaded audio/image files, and teacher feedback.
- **AI safety**: prompt injection, unsafe generated content, provider unavailability, usage quotas, and AI response reports.
- **Payments**: Stripe checkout, webhook signatures, idempotent settlement, refunds, entitlements, and teacher earnings.
- **Uploads and downloads**: file type validation, size limits, storage path handling, private media access, and safe filenames.
- **Operations**: production secrets, CORS origins, forwarded-proxy trust, CSP, logs, metrics, backups, and release evidence.

## Secure Development Expectations

- Use the shared API envelope and exception handling; do not expose raw stack traces to users.
- Do not log passwords, bearer tokens, reset tokens, provider keys, cookies, uploaded file paths, or full payment payload secrets.
- Keep provider HTTP calls outside long database transactions.
- Add targeted tests for auth, authorization, payment, AI quota/provider, upload validation, CORS, headers, and production guard changes.
- Keep preview/demo mode separate from real authenticated sessions.
- Review `docs/ARCHITECTURE.md`, `docs/TESTING_STRATEGY.md`, and `docs/RELEASE_PRECHECK_RUNBOOK.md` before release.

## Incident Response Checklist

1. Identify the affected route, role, data class, provider, and deployment environment.
2. Preserve relevant logs while redacting secrets and learner data before sharing.
3. Rotate exposed secrets, provider keys, webhook secrets, or database credentials.
4. Invalidate impacted sessions or refresh tokens when authentication material may be affected.
5. Disable risky provider integrations temporarily if AI, email, payment, or storage behavior is unsafe.
6. Patch the vulnerability with focused tests and record the fix in `CHANGELOG.md`.
7. Run the relevant quality gates and release precheck before redeploying.
