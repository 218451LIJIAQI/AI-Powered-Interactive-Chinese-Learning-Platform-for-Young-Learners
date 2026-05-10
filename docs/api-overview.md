# API Overview

## Base Path
All backend endpoints remain under:

- `/api/v1`

## Common Response Format
Most controllers return the shared `ApiResponse` structure:

```json
{
  "success": true,
  "message": "Human-readable summary",
  "data": {},
  "errors": []
}
```

## Authorization Header
Protected endpoints require:

```text
Authorization: Bearer <jwt-token>
```

## Public APIs
- `GET /api/v1/health`
- `GET /api/v1/public/teachers/{id}`
- `GET /api/v1/public/levels`
- `GET /api/v1/public/categories`
- `GET /api/v1/public/courses`
- `GET /api/v1/public/courses/{id}`
- `GET /api/v1/public/reviews`
- `GET /api/v1/subscription-plans`

## Authentication APIs
- `POST /api/v1/auth/register/student`
- `POST /api/v1/auth/register/teacher`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`

## Student APIs
Core student flows include:
- `GET /api/v1/students/dashboard`
- `GET /api/v1/students/analytics`
- `GET /api/v1/students/lessons/{lessonId}`
- `GET /api/v1/students/lessons/{lessonId}/activities`
- `GET /api/v1/students/lessons/{lessonId}/flashcards`
- `GET /api/v1/students/quizzes/{quizId}`
- `POST /api/v1/students/quizzes/{quizId}/attempts`
- `GET /api/v1/students/quiz-attempts/{attemptId}`
- `GET /api/v1/students/wrong-answers`
- `GET /api/v1/students/progress`
- `GET /api/v1/students/rewards`
- `GET /api/v1/students/live-classes`
- `GET /api/v1/students/live-classes/{id}`
- `POST /api/v1/students/live-classes/{id}/enrol`
- `GET /api/v1/students/live-classes/{id}/join`
- `GET /api/v1/students/schedule`
- `GET /api/v1/students/recorded-videos/{videoId}/stream`

## Teacher APIs
Core teacher flows include:
- `GET /api/v1/teachers/dashboard`
- `GET /api/v1/teachers/analytics`
- `GET /api/v1/teachers/me`
- `PUT /api/v1/teachers/me/profile`
- `GET /api/v1/teachers/verification-status`
- `POST /api/v1/teachers/verification-documents`
- `POST /api/v1/teachers/resubmit-verification`
- `GET /api/v1/teachers/modules`
- `POST /api/v1/teachers/modules`
- `GET /api/v1/teachers/lessons`
- `POST /api/v1/teachers/lessons`
- `GET /api/v1/teachers/packages`
- `POST /api/v1/teachers/packages`
- `GET /api/v1/teachers/live-classes`
- `POST /api/v1/teachers/live-classes`
- `GET /api/v1/teachers/schedule`
- `GET /api/v1/teachers/quizzes/{quizId}/attempts`

## Admin APIs
Core admin flows include:
- `GET /api/v1/admin/dashboard`
- `GET /api/v1/admin/analytics`
- `GET /api/v1/admin/users`
- `GET /api/v1/admin/students`
- `GET /api/v1/admin/teachers`
- `GET /api/v1/admin/teachers/pending`
- `POST /api/v1/admin/teachers/{id}/approve`
- `POST /api/v1/admin/teachers/{id}/reject`
- `GET /api/v1/admin/content/submissions`
- `GET /api/v1/admin/content/submissions/{contentType}/{id}`
- `POST /api/v1/admin/content/{contentType}/{id}/approve`
- `POST /api/v1/admin/content/{contentType}/{id}/reject`
- `POST /api/v1/admin/content/{contentType}/{id}/publish`
- `PATCH /api/v1/admin/content/{contentType}/{id}/archive`
- `GET /api/v1/admin/ai/usage-logs`
- `GET /api/v1/admin/live-classes`
- `GET /api/v1/admin/schedule`

## AI APIs
- `POST /api/v1/ai/availability`
- `POST /api/v1/ai/chat`
- `POST /api/v1/ai/vocabulary-helper`
- `POST /api/v1/ai/sentence-practice`
- `POST /api/v1/ai/quiz-generator`
- `POST /api/v1/ai/story-generator`
- `POST /api/v1/ai/learning-recommendation`
- `POST /api/v1/ai/progress-insight`
- `POST /api/v1/ai/teacher-assistant`
- `POST /api/v1/ai/pronunciation-feedback`
- `POST /api/v1/ai/writing-feedback`

## Payment APIs
Representative payment and commerce flows include:
- `POST /api/v1/payments/checkout`
- `POST /api/v1/payments/buy-now`
- `GET /api/v1/payments/history`
- `POST /api/v1/refunds`
- `GET /api/v1/refunds/my`
- `GET /api/v1/teachers/earnings`
- `POST /api/v1/teachers/payout-requests`
- `GET /api/v1/admin/payments`
- `GET /api/v1/admin/refunds`
- `GET /api/v1/admin/payouts`

## Live, Recorded, and Schedule APIs
- `GET /api/v1/students/live-classes`
- `GET /api/v1/students/live-classes/{id}`
- `POST /api/v1/students/live-classes/{id}/enrol`
- `GET /api/v1/students/live-classes/{id}/join`
- `GET /api/v1/students/schedule`
- `GET /api/v1/teachers/live-classes`
- `POST /api/v1/teachers/live-classes`
- `PUT /api/v1/teachers/live-classes/{id}`
- `PATCH /api/v1/teachers/live-classes/{id}/cancel`
- `PATCH /api/v1/teachers/live-classes/{id}/reschedule`
- `GET /api/v1/admin/live-classes`
- `GET /api/v1/admin/schedule`

## Support, Review, Moderation, and Audit APIs
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
- `POST /api/v1/teachers/feedback`
- `GET /api/v1/teachers/feedback`
- `GET /api/v1/teachers/reviews`
- `GET /api/v1/teachers/reports`
- `GET /api/v1/teachers/moderation-records`
- `POST /api/v1/support-tickets`
- `GET /api/v1/support-tickets/my`
- `GET /api/v1/support-tickets/{id}`
- `POST /api/v1/support-tickets/{id}/messages`
- `GET /api/v1/admin/reports`
- `PATCH /api/v1/admin/reports/{id}/dismiss`
- `PATCH /api/v1/admin/reports/{id}/mark-action-taken`
- `PATCH /api/v1/admin/reports/{id}/request-revision`
- `GET /api/v1/admin/reviews`
- `PATCH /api/v1/admin/reviews/{id}/hide`
- `PATCH /api/v1/admin/reviews/{id}/show`
- `PATCH /api/v1/admin/reviews/{id}/flag`
- `GET /api/v1/admin/support-tickets`
- `POST /api/v1/admin/support-tickets/{id}/messages`
- `PATCH /api/v1/admin/support-tickets/{id}/status`
- `PATCH /api/v1/admin/support-tickets/{id}/priority`
- `GET /api/v1/admin/moderation-records`
- `POST /api/v1/admin/moderation/hide`
- `POST /api/v1/admin/moderation/unpublish`
- `POST /api/v1/admin/moderation/archive`
- `POST /api/v1/admin/moderation/request-revision`
- `POST /api/v1/admin/moderation/restore`
- `GET /api/v1/admin/audit-logs`

## Analytics APIs
- `GET /api/v1/students/analytics`
- `GET /api/v1/teachers/analytics`
- `GET /api/v1/admin/analytics`

Role rules:
- student analytics returns only the current student’s own data
- teacher analytics returns only teacher-owned or teacher-related aggregates
- admin analytics returns platform-level summaries

## Security Notes
- Guest access to protected endpoints should return `401`
- Wrong-role access should return `403`
- Ownership checks should prevent cross-user access to notifications, reports, support tickets, feedback, and similar private records
- Backend secrets must stay in backend-only configuration
