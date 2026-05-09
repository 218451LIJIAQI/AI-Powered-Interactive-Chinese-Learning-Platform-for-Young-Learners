# API Overview

## Base Path
All backend APIs remain under:

- `/api/v1`

## Public APIs

- `GET /api/v1/health`
- `GET /api/v1/public/teachers/{id}`
- `GET /api/v1/public/levels`
- `GET /api/v1/public/categories`
- `GET /api/v1/public/courses`
- `GET /api/v1/public/courses/{id}`
- `GET /api/v1/public/reviews?targetType=&targetId=`

## Authentication APIs

- `POST /api/v1/auth/register/student`
- `POST /api/v1/auth/register/teacher`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`

## Student APIs

Learning and commerce examples:

- `GET /api/v1/students/dashboard`
- `GET /api/v1/students/lessons/{lessonId}`
- `GET /api/v1/students/progress`
- `GET /api/v1/students/rewards`
- `GET /api/v1/students/live-classes`
- `GET /api/v1/students/schedule`
- `GET /api/v1/students/recorded-lessons/{id}`
- `POST /api/v1/payments/checkout`

Phase 11 communication APIs:

- `GET /api/v1/notifications`
- `GET /api/v1/notifications/unread-count`
- `PATCH /api/v1/notifications/{id}/read`
- `PATCH /api/v1/notifications/read-all`
- `POST /api/v1/reports`
- `GET /api/v1/reports/my`
- `GET /api/v1/reports/my/{id}`
- `POST /api/v1/reviews`
- `GET /api/v1/reviews/my`
- `PUT /api/v1/reviews/{id}`
- `DELETE /api/v1/reviews/{id}`
- `GET /api/v1/students/feedback`
- `GET /api/v1/students/feedback/{id}`
- `POST /api/v1/support-tickets`
- `GET /api/v1/support-tickets/my`
- `GET /api/v1/support-tickets/{id}`
- `POST /api/v1/support-tickets/{id}/messages`

## Teacher APIs

Phase 11 communication APIs:

- `GET /api/v1/teachers/reviews`
- `GET /api/v1/teachers/reports`
- `GET /api/v1/teachers/reports/{id}`
- `GET /api/v1/teachers/moderation-records`
- `POST /api/v1/teachers/feedback`
- `GET /api/v1/teachers/feedback`
- `PUT /api/v1/teachers/feedback/{id}`
- `GET /api/v1/teachers/feedback/candidates`

Existing teaching APIs remain available for lessons, modules, packages, activity editors, live classes, recorded lessons, payout requests, and dashboard summaries.

## Admin APIs

Phase 11 communication, moderation, and audit APIs:

- `GET /api/v1/admin/notifications`
- `GET /api/v1/admin/reports`
- `GET /api/v1/admin/reports/{id}`
- `PATCH /api/v1/admin/reports/{id}/review`
- `PATCH /api/v1/admin/reports/{id}/dismiss`
- `PATCH /api/v1/admin/reports/{id}/mark-action-taken`
- `PATCH /api/v1/admin/reports/{id}/request-revision`
- `GET /api/v1/admin/reviews`
- `PATCH /api/v1/admin/reviews/{id}/hide`
- `PATCH /api/v1/admin/reviews/{id}/show`
- `PATCH /api/v1/admin/reviews/{id}/flag`
- `GET /api/v1/admin/support-tickets`
- `GET /api/v1/admin/support-tickets/{id}`
- `POST /api/v1/admin/support-tickets/{id}/messages`
- `PATCH /api/v1/admin/support-tickets/{id}/status`
- `PATCH /api/v1/admin/support-tickets/{id}/priority`
- `GET /api/v1/admin/moderation-records`
- `GET /api/v1/admin/moderation-records/{id}`
- `POST /api/v1/admin/moderation/hide`
- `POST /api/v1/admin/moderation/unpublish`
- `POST /api/v1/admin/moderation/archive`
- `POST /api/v1/admin/moderation/request-revision`
- `POST /api/v1/admin/moderation/restore`
- `GET /api/v1/admin/audit-logs`
- `GET /api/v1/admin/audit-logs/{id}`
- `GET /api/v1/admin/feedback`

Existing admin APIs remain available for teacher approval, user management, content approvals, AI usage logs, payments, refunds, payouts, subscription plans, and live class oversight.

## Security Notes

- backend role checks enforce access independently from the frontend
- guest access to protected endpoints returns `401`
- wrong-role access returns `403`
- ownership checks protect notifications, support tickets, reports, and feedback
- public reviews only return `VISIBLE` reviews
- secrets such as JWT, SMTP, AI provider, and Stripe values must stay in backend-only environment configuration
