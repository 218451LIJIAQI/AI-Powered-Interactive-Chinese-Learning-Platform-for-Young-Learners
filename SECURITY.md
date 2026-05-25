# Security Policy

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
