# Changelog

All notable changes are documented here following the Keep a Changelog structure.

## Unreleased

### Added

- Production Dockerfiles, Nginx SPA configuration, and a full-stack production Compose file.
- Root-level quality-gate scripts and a release precheck runbook that align local validation with CI and production rehearsal.
- Architecture and testing-strategy documentation covering runtime boundaries, critical data flows, validation layers, contract checks, release evidence, and known scaling limits.
- Caffeine caching for frequently read platform, commerce, and public learning-catalog settings.
- AI per-user provider-call rate limiter and provider latency metrics.
- Bounded asynchronous post-commit dispatcher for password-reset and teacher-verification emails.
- Root onboarding, contribution, security, and production-audit documentation.

### Changed

- README now provides a fuller submission-ready project overview, role capability matrix, local run steps, quality gates, deployment boundaries, environment-variable summary, and extension limitations.
- Security and operations documentation now include sensitive-area handling, incident response, provider operations, backup/restore expectations, and horizontal-scaling readiness.
- AI text-generation calls now run outside long-lived database transactions.
- AI feedback upload file copies now run outside database transactions, retaining only short metadata writes.
- Production startup now rejects disabled real-AI mode, disabled SMTP, or placeholder JWT signing secrets, preventing unavailable AI from silently shipping, undeliverable password resets, and predictable bearer-token signing.
- Login lockouts remain protected under fresh-key spray, and anonymous auth rate-limit windows reset correctly at the exact boundary.
- Production Nginx now replaces client-supplied forwarding headers and is explicitly trusted through a pinned internal Compose address, preserving non-spoofable per-client auth throttling.
- Security headers now include CSP and CORS accepts explicit methods and request headers only.
- Hikari sizing and graceful shutdown timeout are externally configurable.
- Login and anonymous auth rate limiters now use configurable bounded Caffeine state rather than scan-based eviction.
- Frontend build outputs separate framework, networking, and icon chunks.
- Contribution guidance now reflects the single consolidated Flyway V1 baseline until production freeze.
