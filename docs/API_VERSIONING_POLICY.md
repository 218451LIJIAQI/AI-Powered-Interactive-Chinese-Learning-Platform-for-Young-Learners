# API Versioning Policy

The public REST surface is versioned under `/api/v1`. Versioning applies to
request/response JSON, status codes, auth/session behaviour, pagination shape,
and webhook payloads.

## Compatible Changes

These changes may ship inside `/api/v1` without a new major route:

- Add optional response fields.
- Add optional request fields with server defaults.
- Add new enum values only when older clients safely ignore unknown values.
- Add new endpoints.
- Tighten validation only when it blocks invalid or unsafe input that should
  never have been accepted.

## Breaking Changes

These require either a new route namespace, such as `/api/v2`, or a documented
deprecation window:

- Remove or rename request/response fields.
- Change field type or meaning.
- Remove enum values.
- Change auth requirements or token/session semantics.
- Change pagination shape, sort defaults, or error envelope shape.
- Change webhook signature rules or payload schema.

## Deprecation Flow

1. Document the replacement endpoint or field in `CHANGELOG.md`.
2. Add `Deprecation: true` and `Sunset: <RFC 1123 date>` headers on the old
   endpoint for at least 90 days.
3. Add a `Link: <...>; rel="successor-version"` header when there is a direct
   replacement.
4. Keep OpenAPI for the deprecated contract until the sunset date passes.
5. Remove the deprecated route only after frontend and integration consumers
   have migrated.

## Contract Gate

CI exports the backend OpenAPI document with:

```bash
cd backend
./mvnw -DskipTests -Djacoco.skip=true -DskipOpenApiContract=false verify
```

The generated schema is written to `backend/target/openapi.json`. DTO changes
that affect frontend callers must be reflected in `frontend/src/types/api.ts`
or in a future generated-client step before merge.

## Frontend Synchronisation

When an API shape changes, update consumers in this order:

1. Backend DTO, validation, and service/controller tests.
2. OpenAPI export or manual contract inspection.
3. `frontend/src/types/api.ts`.
4. `frontend/src/api/services.ts` wrapper method.
5. Affected Pinia store, page, composable, or route helper.
6. Unit/E2E coverage for the changed user flow.

Do not leave frontend code relying on `unknown`, `any`, or ad hoc response
shape checks when a stable DTO type can be represented in `types/api.ts`.

## Error Envelope Expectations

All public JSON API errors should stay compatible with the shared response
envelope and global exception handler. User-facing messages must be concise,
safe, and free of stack traces, SQL fragments, provider secrets, bearer tokens,
reset tokens, file-system paths, and raw payment/AI provider payloads.

Validation errors may expose field names and safe validation hints, but public
auth routes should avoid account-enumeration signals.
