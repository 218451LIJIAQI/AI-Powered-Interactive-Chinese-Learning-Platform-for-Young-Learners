-- 1. Pin quiz_questions.question_type to the QuizQuestionType domain enum. The entity maps the column with
--    @Enumerated(STRING), which needs the exact constant name (earlier versions read it ignoring case and surrounding
--    spaces), so values are normalised first and the check compares case-sensitively (the column's collation ignores
--    case). It runs first: a value outside the enum fails the migration before any other statement changes the schema.
UPDATE `quiz_questions`
SET `question_type` = UPPER(TRIM(`question_type`))
WHERE `question_type` COLLATE utf8mb4_bin <> UPPER(TRIM(`question_type`));

ALTER TABLE `quiz_questions`
  ADD CONSTRAINT `chk_quiz_questions_question_type` CHECK (`question_type` COLLATE utf8mb4_bin IN ('MULTIPLE_CHOICE','TRUE_FALSE','FILL_BLANK','MATCH_WORD_MEANING','MATCH_WORD_IMAGE','LISTEN_CHOOSE','REARRANGE_SENTENCE','CHOOSE_PINYIN','CHOOSE_TONE','CHARACTER_MEANING'));

-- 2. Enforce referential integrity and uniqueness on package_items (course_package_id, item_id -> learning_modules.id).
DELETE pi
FROM `package_items` pi
LEFT JOIN `learning_modules` lm ON lm.`id` = pi.`item_id`
WHERE lm.`id` IS NULL;

DELETE dup
FROM `package_items` dup
JOIN `package_items` keep_row
  ON dup.`course_package_id` = keep_row.`course_package_id`
 AND dup.`item_id` = keep_row.`item_id`
 AND (dup.`sort_order` > keep_row.`sort_order`
   OR (dup.`sort_order` = keep_row.`sort_order` AND dup.`id` > keep_row.`id`));

ALTER TABLE `package_items`
  ADD CONSTRAINT `fk_package_items_module` FOREIGN KEY (`item_id`) REFERENCES `learning_modules` (`id`),
  ADD UNIQUE KEY `uk_package_items_package_item` (`course_package_id`, `item_id`);

-- 3. Enforce admin_invites accepted/cancelled mutual exclusion and refine its pending-queue index.
UPDATE `admin_invites`
SET `cancelled_at` = NULL
WHERE `accepted_at` IS NOT NULL AND `cancelled_at` IS NOT NULL;

SET @drop_index = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `admin_invites` DROP INDEX `idx_admin_invites_pending`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'admin_invites' AND index_name = 'idx_admin_invites_pending');
PREPARE drop_index_stmt FROM @drop_index;
EXECUTE drop_index_stmt;
DEALLOCATE PREPARE drop_index_stmt;

ALTER TABLE `admin_invites`
  ADD CONSTRAINT `chk_admin_invites_not_accepted_and_cancelled` CHECK (`accepted_at` IS NULL OR `cancelled_at` IS NULL),
  ADD KEY `idx_admin_invites_pending` (`accepted_at`, `cancelled_at`, `expires_at`);

-- 4. Add missing hot-path indexes for analytics, quiz attempts, and AI practice-check pagination.
ALTER TABLE `learn_lesson_completions`
  ADD KEY `idx_learn_lesson_completions_first_completed` (`first_completed_at`),
  ADD KEY `idx_learn_lesson_completions_last_completed` (`last_completed_at`),
  ADD KEY `idx_learn_lesson_completions_student_first` (`student_id`, `first_completed_at`);

ALTER TABLE `quiz_attempts`
  ADD KEY `idx_quiz_attempts_student_quiz_submitted` (`student_id`, `quiz_id`, `submitted`);

ALTER TABLE `ai_practice_checks`
  ADD KEY `idx_ai_practice_checks_student_deleted_created` (`student_user_id`, `deleted_at`, `created_at`, `id`);

-- 5. Defensively drop strictly left-prefix-redundant and unqueried indexes only when their covering index is present.
SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_package_items_package_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'uk_package_items_package_item' AND seq_in_index = 1 AND column_name = 'course_package_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `package_items` DROP INDEX `idx_package_items_package_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'package_items');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_quiz_attempts_student_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_quiz_attempts_student_quiz_submitted' AND seq_in_index = 1 AND column_name = 'student_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `quiz_attempts` DROP INDEX `idx_quiz_attempts_student_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'quiz_attempts');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_ai_practice_checks_student_deleted' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_ai_practice_checks_student_deleted_created' AND seq_in_index = 1 AND column_name = 'student_user_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `ai_practice_checks` DROP INDEX `idx_ai_practice_checks_student_deleted`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_practice_checks');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_ai_practice_checks_student_created' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_ai_practice_checks_student_deleted_created' AND seq_in_index = 1 AND column_name = 'student_user_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `ai_practice_checks` DROP INDEX `idx_ai_practice_checks_student_created`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_practice_checks');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `ai_practice_checks` DROP INDEX `idx_ai_practice_checks_deleted`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_practice_checks' AND index_name = 'idx_ai_practice_checks_deleted');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_lesson_progress_student_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'uk_lesson_progress_student_lesson' AND seq_in_index = 1 AND column_name = 'student_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `lesson_progress` DROP INDEX `idx_lesson_progress_student_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'lesson_progress');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_points_transactions_student_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'uk_points_transactions_student_source' AND seq_in_index = 1 AND column_name = 'student_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `points_transactions` DROP INDEX `idx_points_transactions_student_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'points_transactions');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_quiz_attempt_answers_attempt_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'uk_quiz_attempt_answers_attempt_question' AND seq_in_index = 1 AND column_name = 'quiz_attempt_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `quiz_attempt_answers` DROP INDEX `idx_quiz_attempt_answers_attempt_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'quiz_attempt_answers');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_student_badges_student_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'uk_student_badges_student_badge' AND seq_in_index = 1 AND column_name = 'student_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `student_badges` DROP INDEX `idx_student_badges_student_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'student_badges');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_video_progress_student_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'uk_video_progress_student_video' AND seq_in_index = 1 AND column_name = 'student_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `video_progress` DROP INDEX `idx_video_progress_student_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'video_progress');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_video_progress_lesson_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_video_progress_lesson_last_watched' AND seq_in_index = 1 AND column_name = 'lesson_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `video_progress` DROP INDEX `idx_video_progress_lesson_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'video_progress');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_ai_chat_messages_session_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_ai_chat_messages_session_role_id' AND seq_in_index = 1 AND column_name = 'session_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `ai_chat_messages` DROP INDEX `idx_ai_chat_messages_session_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_chat_messages');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_ai_chat_sessions_user_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_ai_chat_sessions_user_feature_created' AND seq_in_index = 1 AND column_name = 'user_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `ai_chat_sessions` DROP INDEX `idx_ai_chat_sessions_user_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_chat_sessions');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_ai_response_reports_student_user_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_ai_response_reports_student_created' AND seq_in_index = 1 AND column_name = 'student_user_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `ai_response_reports` DROP INDEX `idx_ai_response_reports_student_user_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_response_reports');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_ai_response_reports_status' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_ai_response_reports_status_created_at' AND seq_in_index = 1 AND column_name = 'status' AND is_visible = 'YES') = 1,
    'ALTER TABLE `ai_response_reports` DROP INDEX `idx_ai_response_reports_status`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_response_reports');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_ai_usage_logs_user_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_ai_usage_logs_user_feature_created' AND seq_in_index = 1 AND column_name = 'user_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `ai_usage_logs` DROP INDEX `idx_ai_usage_logs_user_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_usage_logs');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_audit_logs_actor_user_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_audit_logs_actor_created' AND seq_in_index = 1 AND column_name = 'actor_user_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `audit_logs` DROP INDEX `idx_audit_logs_actor_user_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'audit_logs');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_audit_logs_action_type' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_audit_logs_action_created_at' AND seq_in_index = 1 AND column_name = 'action_type' AND is_visible = 'YES') = 1,
    'ALTER TABLE `audit_logs` DROP INDEX `idx_audit_logs_action_type`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'audit_logs');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_lessons_module_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_lessons_module_position' AND seq_in_index = 1 AND column_name = 'module_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `lessons` DROP INDEX `idx_lessons_module_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'lessons');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_notifications_user_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_notifications_user_created_id' AND seq_in_index = 1 AND column_name = 'user_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `notifications` DROP INDEX `idx_notifications_user_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'notifications');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `notifications` DROP INDEX `idx_notifications_status`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'notifications' AND index_name = 'idx_notifications_status');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `notifications` DROP INDEX `idx_notifications_type`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'notifications' AND index_name = 'idx_notifications_type');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_recorded_videos_lesson_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_recorded_videos_lesson_active_sort' AND seq_in_index = 1 AND column_name = 'lesson_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `recorded_lesson_videos` DROP INDEX `idx_recorded_videos_lesson_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'recorded_lesson_videos');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `recorded_lesson_videos` DROP INDEX `idx_recorded_videos_active`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'recorded_lesson_videos' AND index_name = 'idx_recorded_videos_active');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_learning_mistakes_student_waiting' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_learning_mistakes_student_review_order' AND seq_in_index = 1 AND column_name = 'student_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `learning_mistakes` DROP INDEX `idx_learning_mistakes_student_waiting`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'learning_mistakes');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `xiaomei_memory_notes` DROP INDEX `idx_xiaomei_memory_notes_forgotten`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'xiaomei_memory_notes' AND index_name = 'idx_xiaomei_memory_notes_forgotten');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

-- Single-column content_status indexes shadowed by (content_status, updated_at).
SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_course_packages_status' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_course_packages_status_updated' AND seq_in_index = 1 AND column_name = 'content_status' AND is_visible = 'YES') = 1,
    'ALTER TABLE `course_packages` DROP INDEX `idx_course_packages_status`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'course_packages');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_drag_drop_activities_status' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_drag_drop_activities_status_updated' AND seq_in_index = 1 AND column_name = 'content_status' AND is_visible = 'YES') = 1,
    'ALTER TABLE `drag_drop_activities` DROP INDEX `idx_drag_drop_activities_status`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'drag_drop_activities');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_flashcards_status' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_flashcards_status_updated' AND seq_in_index = 1 AND column_name = 'content_status' AND is_visible = 'YES') = 1,
    'ALTER TABLE `flashcards` DROP INDEX `idx_flashcards_status`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'flashcards');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_learning_modules_status' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_learning_modules_status_updated' AND seq_in_index = 1 AND column_name = 'content_status' AND is_visible = 'YES') = 1,
    'ALTER TABLE `learning_modules` DROP INDEX `idx_learning_modules_status`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'learning_modules');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_lessons_status' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_lessons_status_updated' AND seq_in_index = 1 AND column_name = 'content_status' AND is_visible = 'YES') = 1,
    'ALTER TABLE `lessons` DROP INDEX `idx_lessons_status`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'lessons');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_listening_exercises_status' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_listening_exercises_status_updated' AND seq_in_index = 1 AND column_name = 'content_status' AND is_visible = 'YES') = 1,
    'ALTER TABLE `listening_exercises` DROP INDEX `idx_listening_exercises_status`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'listening_exercises');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_matching_games_status' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_matching_games_status_updated' AND seq_in_index = 1 AND column_name = 'content_status' AND is_visible = 'YES') = 1,
    'ALTER TABLE `matching_games` DROP INDEX `idx_matching_games_status`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'matching_games');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_quizzes_status' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_quizzes_status_updated' AND seq_in_index = 1 AND column_name = 'content_status' AND is_visible = 'YES') = 1,
    'ALTER TABLE `quizzes` DROP INDEX `idx_quizzes_status`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'quizzes');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_tracing_exercises_status' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_tracing_exercises_status_updated' AND seq_in_index = 1 AND column_name = 'content_status' AND is_visible = 'YES') = 1,
    'ALTER TABLE `tracing_exercises` DROP INDEX `idx_tracing_exercises_status`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'tracing_exercises');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_typing_exercises_status' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_typing_exercises_status_updated' AND seq_in_index = 1 AND column_name = 'content_status' AND is_visible = 'YES') = 1,
    'ALTER TABLE `typing_exercises` DROP INDEX `idx_typing_exercises_status`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'typing_exercises');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;
