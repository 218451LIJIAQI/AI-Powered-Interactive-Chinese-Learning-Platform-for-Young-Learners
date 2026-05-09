# Database Notes

## 1. Main Database Tables
The Phase 2 schema is grouped into these main areas:

- Core users:
  - `users`
  - `roles`
  - `user_roles`
  - `student_profiles`
  - `teacher_profiles`
- Teacher verification:
  - `teacher_verification_documents`
  - `teacher_verification_reviews`
- Learning structure:
  - `learning_levels`
  - `learning_categories`
  - `learning_modules`
  - `lessons`
  - `lesson_activities`
- Quiz and activities:
  - `quizzes`
  - `quiz_questions`
  - `quiz_options`
  - `quiz_attempts`
  - `quiz_attempt_answers`
  - `flashcards`
  - `matching_games`
  - `matching_game_items`
  - `tracing_exercises`
  - `listening_exercises`
- Progress and rewards:
  - `student_progress`
  - `lesson_progress`
  - `video_progress`
  - `points_transactions`
  - `badges`
  - `student_badges`
  - `daily_streaks`
- AI records:
  - `ai_chat_sessions`
  - `ai_chat_messages`
  - `ai_generation_records`
  - `ai_feedback_records`
  - `ai_usage_logs`
- Commerce:
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
- Moderation and communication:
  - `reports`
  - `reviews`
  - `notifications`
  - `support_tickets`
  - `support_ticket_messages`
  - `audit_logs`
  - `content_moderation_records`
- Schedule:
  - `live_classes`
  - `live_class_enrolments`
  - `learning_tasks`
- Recorded learning:
  - `recorded_lesson_videos`
- Wishlist and uploads:
  - `saved_items`
  - `uploaded_files`

## 2. Important Enums
Phase 2 and later phases include these main enums:

- `RoleName`: `STUDENT`, `TEACHER`, `ADMIN`
- `TeacherStatus`: `PENDING`, `APPROVED`, `REJECTED`
- `ContentStatus`: `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `PUBLISHED`, `ARCHIVED`
- `LearningType`: `LIVE`, `RECORDED`, `HYBRID`
- `AccessType`: `FREE`, `PAID`
- `LiveClassStatus`: `SCHEDULED`, `ONGOING`, `COMPLETED`, `CANCELLED`, `RESCHEDULED`
- `LiveClassEnrolmentStatus`: `ENROLLED`, `CANCELLED`, `ATTENDED`, `MISSED`
- `MeetingPlatform`: `ZOOM`, `GOOGLE_MEET`, `MICROSOFT_TEAMS`, `OTHER`
- `RecordedVideoSourceType`: `UPLOADED_FILE`, `EXTERNAL_URL`
- `PaymentStatus`: `PENDING`, `PAID`, `FAILED`, `CANCELLED`, `REFUNDED`
- `RefundStatus`: `PENDING`, `APPROVED`, `REJECTED`, `REFUNDED`
- `PayoutStatus`: `PENDING`, `APPROVED`, `REJECTED`, `PAID`
- `SupportTicketStatus`: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`
- `ReportStatus`: `PENDING`, `REVIEWED`, `DISMISSED`, `ACTION_TAKEN`
- `NotificationStatus`: `UNREAD`, `READ`

## 3. Seed Data
The startup seeder inserts only essential reference data:

- Roles:
  - `STUDENT`
  - `TEACHER`
  - `ADMIN`
- Learning levels:
  - `Beginner`
  - `Intermediate`
  - `Advanced`
- Learning categories:
  - `Pinyin`
  - `Tones`
  - `Vocabulary`
  - `Chinese Characters`
  - `Listening`
  - `Reading`
  - `Writing / Tracing`
  - `Daily Conversation`
  - `Numbers`
  - `Colours`
  - `Animals`
  - `Family`
  - `School`
  - `Food`
  - `Greetings`
- Basic badge templates
- Subscription plans:
  - `Free Plan`
  - `Basic Plan`
  - `Premium Plan`
  - `Family Plan`
- System settings:
  - platform name
  - default currency = `MYR`
  - editable platform commission rate

No large lessons, quizzes, stories, vocabulary banks, or AI content are seeded by default.

## 4. How To Start MySQL
From the project root:

```powershell
docker compose up -d
```

This starts MySQL 8.4 with the database:
- `ai_chinese_learning`
- exposed on host port `3307`

## 5. How To Run Backend And Create Tables
From the `backend` folder:

```powershell
.\mvnw.cmd spring-boot:run
```

Phase 2 uses Hibernate schema update for development:
- `spring.jpa.hibernate.ddl-auto=update`

That means:
- the backend connects to MySQL
- missing tables are created automatically
- later schema refinements can still be made before production migration tooling is introduced

## 6. How Default Admin Is Created
Default admin creation is optional and controlled only by environment variables:

- `DEFAULT_ADMIN_EMAIL`
- `DEFAULT_ADMIN_PASSWORD`

If both are present:
- a default admin `users` record is created
- the admin user is linked to the `ADMIN` role through `user_roles`

If either value is missing:
- the backend logs a clear warning
- startup continues safely
- no default admin is created

No admin credentials are hard-coded in the codebase.

## 7. Notes On Scope
Phase 2 established the schema foundation. By Phase 5, these additional table groups are now actively used in implemented workflows:

- Teacher verification workflow:
  - `teacher_profiles`
  - `teacher_verification_documents`
  - `teacher_verification_reviews`
  - `uploaded_files`
- Basic moderation and communication support:
  - `notifications`
  - `audit_logs`

Phase 2 intentionally covered only:
- entities
- enums
- repositories
- seed data
- database configuration

Implemented by Phase 5:
- teacher profile updates
- teacher verification document upload and review records
- admin teacher approval and rejection
- protected uploaded file metadata storage
- notification records for verification actions
- audit logs for verification actions

Still not implemented:
- authentication
- AI features
- payment flows
- learning content CRUD workflows
- broader business workflows beyond the approved Phase 5 scope

## 8. Phase 10 Schema Notes
Phase 10 reuses the existing schedule foundation and extends it with lightweight live and recorded learning support:

- `live_classes`
  - linked to teacher, lesson, and optional package
  - stores external meeting link, platform, timing, status, and optional participant cap
- `live_class_enrolments`
  - stores student enrolment, cancellation, and attendance state
- `recorded_lesson_videos`
  - links recorded video metadata to a lesson and optional package
  - supports `UPLOADED_FILE` or `EXTERNAL_URL`
- `video_progress`
  - continues to track learner progress, now keyed to recorded lesson videos as well
- `uploaded_files`
  - stores protected metadata for uploaded recorded lesson files

Development notes:
- Meeting links are sensitive and must not be exposed in public APIs.
- Uploaded file paths are stored in the database but are not returned directly to the frontend.
- Hibernate `ddl-auto=update` remains the development strategy for this prototype.
