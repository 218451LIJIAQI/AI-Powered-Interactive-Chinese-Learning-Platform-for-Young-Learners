# Database Notes

## Main Table Groups

### User and Role Tables
- `users`
- `roles`
- `user_roles`
- `student_profiles`
- `teacher_profiles`
- `teacher_verification_documents`
- `teacher_verification_reviews`

### Learning Tables
- `learning_levels`
- `learning_categories`
- `learning_modules`
- `lessons`
- `lesson_activities`
- `quizzes`
- `quiz_questions`
- `quiz_options`
- `quiz_attempts`
- `quiz_attempt_answers`
- `flashcards`
- `matching_games`
- `matching_game_items`
- `listening_exercises`
- `tracing_exercises`
- `lesson_progress`
- `student_progress`
- `points_transactions`
- `badges`
- `student_badges`
- `daily_streaks`
- `saved_items`

### AI Tables
- `ai_chat_sessions`
- `ai_chat_messages`
- `ai_generation_records`
- `ai_feedback_records`
- `ai_usage_logs`

### Payment and Commerce Tables
- `course_packages`
- `package_items`
- `carts`
- `cart_items`
- `orders`
- `order_items`
- `payment_records`
- `subscription_plans`
- `student_subscriptions`
- `refund_requests`
- `teacher_earnings`
- `payout_requests`
- `system_settings`

### Live, Recorded, and Schedule Tables
- `live_classes`
- `live_class_enrolments`
- `recorded_lesson_videos`
- `video_progress`
- `learning_tasks`

### Support, Moderation, and Audit Tables
- `notifications`
- `reports`
- `reviews`
- `support_tickets`
- `support_ticket_messages`
- `content_moderation_records`
- `audit_logs`
- `teacher_feedback`

### Upload Tables
- `uploaded_files`

## Important Enums
- `RoleName`: `STUDENT`, `TEACHER`, `ADMIN`
- `TeacherStatus`: `PENDING`, `APPROVED`, `REJECTED`
- `ContentStatus`: `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `PUBLISHED`, `ARCHIVED`
- `LearningType`: `LIVE`, `RECORDED`, `HYBRID`
- `AccessType`: `FREE`, `PAID`
- `LiveClassStatus`: `SCHEDULED`, `ONGOING`, `COMPLETED`, `CANCELLED`, `RESCHEDULED`
- `LiveClassEnrolmentStatus`: `ENROLLED`, `CANCELLED`, `ATTENDED`, `MISSED`
- `PaymentStatus`: `PENDING`, `PAID`, `FAILED`, `CANCELLED`, `REFUNDED`
- `RefundStatus`: `PENDING`, `APPROVED`, `REJECTED`, `REFUNDED`
- `PayoutStatus`: `PENDING`, `APPROVED`, `REJECTED`, `PAID`
- `SupportTicketStatus`: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`
- `SupportTicketPriority`: `LOW`, `MEDIUM`, `HIGH`, `URGENT`
- `ReportStatus`: `PENDING`, `REVIEWED`, `DISMISSED`, `ACTION_TAKEN`
- `NotificationStatus`: `UNREAD`, `READ`
- `ReviewStatus`: `VISIBLE`, `HIDDEN`, `FLAGGED`
- `ContentModerationAction`: `HIDE`, `UNPUBLISH`, `ARCHIVE`, `REQUEST_REVISION`, `RESTORE`

## Analytics Source Notes
Phase 12 analytics is summary-oriented and intentionally reuses existing production-like records instead of introducing a separate analytics warehouse.

Student analytics sources:
- `lesson_progress`
- `quiz_attempts`
- `video_progress`
- `live_class_enrolments`
- `student_progress`
- `notifications`

Teacher analytics sources:
- `learning_modules`
- `lessons`
- `course_packages`
- `quiz_attempts`
- `teacher_earnings`
- `live_classes`
- `live_class_enrolments`
- `reviews`
- `ai_usage_logs`

Admin analytics sources:
- `users`
- `teacher_profiles`
- `learning_modules`
- `lessons`
- `course_packages`
- `payment_records`
- `refund_requests`
- `payout_requests`
- `reports`
- `reviews`
- `support_tickets`
- `content_moderation_records`
- `audit_logs`
- `ai_usage_logs`
- `notifications`

The dashboards should show honest empty states or zero values when these tables have limited demo data.

## Seed Data
The project seeds only essential reference data:
- roles
- learning levels
- learning categories
- badge templates
- subscription plans
- basic system settings

The project does not seed large fake production datasets. This is intentional for Phase 12 demo honesty.

## Development Schema Notes
- Development uses Hibernate `ddl-auto=update`
- Table evolution is kept lightweight and phase-friendly
- Existing entities should be reused rather than duplicated
- Enums should remain `STRING`-based in the database where already modelled that way

## Default Admin Notes
Default admin creation is optional and controlled by:
- `DEFAULT_ADMIN_EMAIL`
- `DEFAULT_ADMIN_PASSWORD`

No admin credentials are hard-coded in source files.

## Security Notes
- Uploaded file paths should not be exposed directly to frontend clients
- Notification, report, support, and feedback records remain ownership-protected
- SMTP, AI, Stripe, JWT, and database secrets must not be stored in frontend code
