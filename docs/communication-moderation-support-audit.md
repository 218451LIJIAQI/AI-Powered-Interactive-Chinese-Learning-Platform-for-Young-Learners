# Communication, Moderation, Support, and Audit

## Overview
Phase 11 adds the platform communication and governance layer:

- in-app notifications for students, teachers, and admins
- optional SMTP email delivery with safe no-op fallback
- student reports and admin report handling
- student reviews with teacher/admin visibility and moderation
- teacher feedback to students
- support tickets with threaded replies
- admin content moderation actions and teacher moderation visibility
- admin audit log visibility

The system remains runnable when SMTP is not configured. In-app notifications continue to work even when email sending is disabled or unavailable.

## Notifications

### In-app notifications
Supported notification types include:

- `SYSTEM`
- `TEACHER_FEEDBACK`
- `QUIZ_RESULT`
- `PROGRESS_UPDATE`
- `REWARD_EARNED`
- `PAYMENT_STATUS`
- `REFUND_STATUS`
- `PAYOUT_STATUS`
- `LIVE_CLASS_REMINDER`
- `TEACHER_APPROVAL`
- `CONTENT_REVIEW`
- `REPORT_CREATED`
- `REVIEW_RECEIVED`
- `SUPPORT_TICKET`
- `MODERATION_ACTION`
- `ADMIN_TASK`

Main notification APIs:

- `GET /api/v1/notifications`
- `GET /api/v1/notifications/unread-count`
- `PATCH /api/v1/notifications/{id}/read`
- `PATCH /api/v1/notifications/read-all`
- `GET /api/v1/admin/notifications`

Rules:

- users can only view and update their own notifications
- unread counts are private to the authenticated user
- notification creation failures are logged safely and do not fail the main business action where possible

### Frontend notification pages

- common notification center: `/notifications`
- admin notification route: `/admin/notifications`
- unread badge is shown in student, teacher, and admin layouts

## SMTP Email

### Environment variables

- `SMTP_ENABLED`
- `SMTP_HOST`
- `SMTP_PORT`
- `SMTP_USERNAME`
- `SMTP_PASSWORD`
- `SMTP_FROM`

### Behaviour

- if `SMTP_ENABLED=false`, email sending is skipped safely
- if `SMTP_HOST` is missing, email sending is skipped safely
- the backend still starts even when SMTP is not configured
- email failures do not crash the main workflow
- SMTP credentials must stay in backend-only environment configuration

### Current email usage

- teacher approval notifications
- teacher rejection notifications
- support ticket replies

## Reports

### Student flow

- create a report against a lesson, package, review, teacher, technical issue, or other target
- view personal report history
- view personal report detail

Student APIs:

- `POST /api/v1/reports`
- `GET /api/v1/reports/my`
- `GET /api/v1/reports/my/{id}`

### Admin flow

- view all reports
- inspect report detail
- mark as reviewed
- dismiss
- mark action taken
- request teacher revision

Admin APIs:

- `GET /api/v1/admin/reports`
- `GET /api/v1/admin/reports/{id}`
- `PATCH /api/v1/admin/reports/{id}/review`
- `PATCH /api/v1/admin/reports/{id}/dismiss`
- `PATCH /api/v1/admin/reports/{id}/mark-action-taken`
- `PATCH /api/v1/admin/reports/{id}/request-revision`

### Teacher flow

- view reports related to owned content
- view revision requests without seeing unrelated private reporter data

Teacher APIs:

- `GET /api/v1/teachers/reports`
- `GET /api/v1/teachers/reports/{id}`

## Reviews and Ratings

### Student flow

- create a review for accessible content
- edit own review
- hide own review
- view personal review history

Student APIs:

- `POST /api/v1/reviews`
- `GET /api/v1/reviews/my`
- `PUT /api/v1/reviews/{id}`
- `DELETE /api/v1/reviews/{id}`

### Public flow

- only `VISIBLE` reviews are returned publicly

Public API:

- `GET /api/v1/public/reviews?targetType=&targetId=`

### Teacher flow

- view reviews for owned content

Teacher API:

- `GET /api/v1/teachers/reviews`

### Admin moderation

- hide review
- restore review
- flag review

Admin APIs:

- `GET /api/v1/admin/reviews`
- `PATCH /api/v1/admin/reviews/{id}/hide`
- `PATCH /api/v1/admin/reviews/{id}/show`
- `PATCH /api/v1/admin/reviews/{id}/flag`

## Teacher Feedback

Teacher feedback records can be linked to:

- a student
- an optional lesson
- an optional quiz attempt

Teacher APIs:

- `POST /api/v1/teachers/feedback`
- `GET /api/v1/teachers/feedback`
- `PUT /api/v1/teachers/feedback/{id}`
- `GET /api/v1/teachers/feedback/candidates`

Student APIs:

- `GET /api/v1/students/feedback`
- `GET /api/v1/students/feedback/{id}`

Admin API:

- `GET /api/v1/admin/feedback`

Rules:

- only teachers can create feedback
- feedback is limited to students linked to the teacher's own content or activity records
- students can only see their own feedback

## Support Tickets

### Shared authenticated flow

- create ticket
- view own ticket list
- view own ticket detail
- reply while ticket is open

Shared APIs:

- `POST /api/v1/support-tickets`
- `GET /api/v1/support-tickets/my`
- `GET /api/v1/support-tickets/{id}`
- `POST /api/v1/support-tickets/{id}/messages`

### Admin flow

- view all tickets
- view ticket detail
- reply
- update status
- update priority

Admin APIs:

- `GET /api/v1/admin/support-tickets`
- `GET /api/v1/admin/support-tickets/{id}`
- `POST /api/v1/admin/support-tickets/{id}/messages`
- `PATCH /api/v1/admin/support-tickets/{id}/status`
- `PATCH /api/v1/admin/support-tickets/{id}/priority`

Rules:

- users cannot read another user's ticket
- closed tickets reject normal requester replies until reopened

## Content Moderation

Supported moderation actions:

- `HIDE`
- `UNPUBLISH`
- `ARCHIVE`
- `REQUEST_REVISION`
- `RESTORE`

Admin APIs:

- `GET /api/v1/admin/moderation-records`
- `GET /api/v1/admin/moderation-records/{id}`
- `POST /api/v1/admin/moderation/hide`
- `POST /api/v1/admin/moderation/unpublish`
- `POST /api/v1/admin/moderation/archive`
- `POST /api/v1/admin/moderation/request-revision`
- `POST /api/v1/admin/moderation/restore`

Teacher API:

- `GET /api/v1/teachers/moderation-records`

## Audit Logs

Admin audit log APIs:

- `GET /api/v1/admin/audit-logs`
- `GET /api/v1/admin/audit-logs/{id}`

Important recorded action groups include:

- report actions
- review moderation actions
- teacher feedback creation
- support ticket admin actions
- content moderation actions

Audit log failures are handled safely and should not break the main business action.

## Frontend Routes

### Shared authenticated routes

- `/notifications`
- `/support-tickets`
- `/support-tickets/create`
- `/support-tickets/:id`

### Student routes

- `/student/reports`
- `/student/reports/create`
- `/student/reviews`
- `/student/feedback`

### Teacher routes

- `/teacher/reviews`
- `/teacher/feedback`
- `/teacher/reports`
- `/teacher/moderation-records`

### Admin routes

- `/admin/reports`
- `/admin/reports/:id`
- `/admin/reviews`
- `/admin/support-tickets`
- `/admin/support-tickets/:id`
- `/admin/audit-logs`
- `/admin/moderation`
- `/admin/notifications`

## Role-Based Access Rules

- backend access control is enforced through Spring Security and role-protected controller mappings
- frontend route guards are convenience only and do not replace backend checks
- non-admin users cannot access admin audit or moderation APIs
- teachers cannot access unrelated student-private feedback or reports
- students cannot access another student's notifications, reports, feedback, or support tickets

## Manual Testing Guide

1. Log in as a student and open `/notifications`, `/student/reports`, `/student/reviews`, `/student/feedback`, and `/support-tickets`.
2. Submit a report and confirm it appears in the student report history.
3. Create a review and confirm it appears in student history and teacher review visibility.
4. Create a support ticket, reply to it, and confirm the thread updates.
5. Log in as a teacher and open `/notifications`, `/teacher/reviews`, `/teacher/reports`, `/teacher/feedback`, and `/teacher/moderation-records`.
6. Confirm teacher review/report visibility is limited to owned content.
7. Log in as an admin and open `/admin/reports`, `/admin/reviews`, `/admin/support-tickets`, `/admin/moderation`, `/admin/audit-logs`, and `/admin/notifications`.
8. Confirm admin pages show the expected Phase 11 records and that protected endpoints reject unauthorized access.

## Known Limitations

- SMTP is optional and currently uses simple plain-text messages instead of a full template system.
- audit log IP addresses remain optional because the current reusable logging service is designed to tolerate missing request context.
- Live browser verification can show temporary empty tables if a page snapshot is taken before the async fetch completes; the backend data still resolves correctly after the page settles.
