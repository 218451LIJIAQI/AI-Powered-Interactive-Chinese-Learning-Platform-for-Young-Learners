# Testing Checklist

## Automated Commands

Frontend:

- `cd frontend`
- `npm run build`

Backend:

- `cd backend`
- `.\mvnw.cmd test`
- `.\mvnw.cmd -DskipTests compile`

Infrastructure:

- `docker compose config`
- verify MySQL is healthy on `localhost:3307`
- start backend locally and verify `GET /api/v1/health`

## Phase 11 Manual Checks

### Student

1. Log in as a student.
2. Open `/notifications` and verify unread count and mark-as-read behavior.
3. Open `/student/reports/create`, submit a report, then verify it appears in `/student/reports`.
4. Open `/student/reviews`, create a review, and verify it appears in the personal list.
5. Open `/student/feedback` and verify owned teacher feedback is visible only to the student.
6. Open `/support-tickets/create`, create a ticket, open `/support-tickets/{id}`, and add a reply.

### Teacher

1. Log in as a teacher.
2. Open `/notifications` and verify teacher-facing notifications load.
3. Open `/teacher/reviews` and confirm only owned-content reviews are shown.
4. Open `/teacher/feedback`, create feedback for a linked student, and confirm the save succeeds.
5. Open `/teacher/reports` and verify owned-content report visibility.
6. Open `/teacher/moderation-records` and verify related moderation records load.
7. Open shared support ticket pages and verify teacher-owned ticket access works.

### Admin

1. Log in as an admin.
2. Open `/admin/notifications`.
3. Open `/admin/reports` and `/admin/reports/{id}` and perform review, dismiss, or revision actions.
4. Open `/admin/reviews` and hide/show a review.
5. Open `/admin/support-tickets` and `/admin/support-tickets/{id}` and verify reply, status, and priority updates.
6. Open `/admin/moderation` and create at least one moderation action.
7. Open `/admin/audit-logs` and verify audit records appear.

## Security and Static Checks

- confirm no `javax.persistence` imports remain
- confirm guest access to protected endpoints returns `401`
- confirm wrong-role access to admin routes returns `403`
- confirm users cannot access another user’s notifications
- confirm users cannot access another user’s support tickets
- confirm students cannot access another student’s feedback or reports
- confirm teachers cannot access unrelated private records
- confirm no password hashes are exposed in Phase 11 DTOs or APIs
- confirm no SMTP credentials are committed or exposed in frontend files
- confirm no AI provider keys, Stripe secrets, or JWT secrets are committed
- confirm `frontend/dist`, `backend/target`, uploads, logs, and temp/runtime files are not staged
- confirm `frontend/src/i18n/messages/zh.ts` remains clean UTF-8

## SMTP No-Op Verification

1. Leave `SMTP_ENABLED=false` or leave `SMTP_HOST` empty.
2. Start the backend.
3. Trigger a workflow that sends email, such as an admin support reply.
4. Confirm the main workflow succeeds and the backend does not crash.
5. Confirm in-app notifications continue to work.
