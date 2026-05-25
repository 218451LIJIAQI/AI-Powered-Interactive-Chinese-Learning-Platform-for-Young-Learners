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
