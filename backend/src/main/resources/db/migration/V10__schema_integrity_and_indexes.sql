-- 1. Enforce at most one unaccepted invite per email without SERIALIZABLE range locks.
--    Deduplicate any historical duplicate unaccepted rows first (keep the newest row per email).
DELETE older
FROM `admin_invites` older
JOIN `admin_invites` newer
  ON older.`email` = newer.`email`
 AND older.`accepted_at` IS NULL
 AND newer.`accepted_at` IS NULL
 AND older.`id` < newer.`id`;

ALTER TABLE `admin_invites`
  ADD COLUMN `unaccepted_email` varchar(150)
    GENERATED ALWAYS AS (CASE WHEN `accepted_at` IS NULL THEN `email` ELSE NULL END) STORED,
  ADD UNIQUE KEY `uk_admin_invites_unaccepted_email` (`unaccepted_email`);

-- 2. Hot-path indexes matching live query filters and ordering.
ALTER TABLE `notifications`
  ADD KEY `idx_notifications_target` (`target_type`, `target_id`);

ALTER TABLE `practice_answers`
  ADD KEY `idx_practice_answers_student_item_created` (`student_id`, `item_ref`, `created_at`);

ALTER TABLE `practice_completions`
  ADD KEY `idx_practice_completions_student_type_activity` (`student_id`, `practice_type`, `activity_id`),
  ADD KEY `idx_practice_completions_created_at` (`created_at`);

ALTER TABLE `lesson_progress`
  ADD KEY `idx_lesson_progress_completed_at` (`completed`, `completed_at`),
  ADD KEY `idx_lesson_progress_last_accessed` (`last_accessed_at`);

ALTER TABLE `points_transactions`
  ADD KEY `idx_points_transactions_student_source_pair` (`student_id`, `source_type`, `source_id`);

ALTER TABLE `package_items`
  ADD KEY `idx_package_items_item_package` (`item_id`, `course_package_id`);

ALTER TABLE `typing_exercises`
  ADD KEY `idx_typing_exercises_status_updated` (`content_status`, `updated_at`),
  ADD KEY `idx_typing_exercises_hsk_level` (`hsk_level`);

ALTER TABLE `ai_practice_checks`
  ADD KEY `idx_ai_practice_checks_student_deleted` (`student_user_id`, `deleted_at`);

ALTER TABLE `learning_mistakes`
  ADD KEY `idx_learning_mistakes_student_review_order` (`student_id`, `cleared_at`, `sure_wrong`, `last_wrong_at`, `id`),
  ADD KEY `idx_learning_mistakes_cleared_student` (`cleared_at`, `student_id`);
