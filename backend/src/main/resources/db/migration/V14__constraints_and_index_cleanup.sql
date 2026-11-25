-- Forward-only constraint hardening and index cleanup:
-- 1. Enforce at most one working-copy revision per published lesson (MySQL UNIQUE allows multiple NULLs).
-- 2. Upgrade five single-column FK indexes to composite ordered/lookup covering indexes, then drop the superseded ones.
-- 3. Drop the six unqueried low-cardinality hsk_level secondary indexes on practice tables.
-- Uses plain PREPARE/EXECUTE guards (no stored procedures) so databases without CREATE ROUTINE privilege migrate cleanly.

-- 1. Unique working-copy revision per published lesson
SET @v14_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `lessons` ADD UNIQUE KEY `uk_lessons_revision_of` (`revision_of_lesson_id`)',
    'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'lessons' AND index_name = 'uk_lessons_revision_of');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `lessons` DROP INDEX `idx_lessons_revision_of`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'lessons' AND index_name = 'idx_lessons_revision_of');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

-- 2. Composite covering indexes (added first so FKs always retain a leftmost covering index;
--    display-order queries sort by (sort_order, created_at, id) and InnoDB appends PK id implicitly)
SET @v14_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `drag_drop_items` ADD KEY `idx_drag_drop_items_activity_order` (`drag_drop_activity_id`, `sort_order`, `created_at`)',
    'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'drag_drop_items' AND index_name = 'idx_drag_drop_items_activity_order');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `drag_drop_items` DROP INDEX `idx_drag_drop_items_activity_id`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'drag_drop_items' AND index_name = 'idx_drag_drop_items_activity_id');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `matching_game_items` ADD KEY `idx_matching_game_items_game_order` (`matching_game_id`, `sort_order`, `created_at`)',
    'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'matching_game_items' AND index_name = 'idx_matching_game_items_game_order');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `matching_game_items` DROP INDEX `idx_matching_game_items_game_id`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'matching_game_items' AND index_name = 'idx_matching_game_items_game_id');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `quiz_questions` ADD KEY `idx_quiz_questions_quiz_order` (`quiz_id`, `sort_order`, `created_at`)',
    'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'quiz_questions' AND index_name = 'idx_quiz_questions_quiz_order');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `quiz_questions` DROP INDEX `idx_quiz_questions_quiz_id`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'quiz_questions' AND index_name = 'idx_quiz_questions_quiz_id');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `quiz_options` ADD KEY `idx_quiz_options_question_order` (`question_id`, `sort_order`, `created_at`)',
    'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'quiz_options' AND index_name = 'idx_quiz_options_question_order');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `quiz_options` DROP INDEX `idx_quiz_options_question_id`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'quiz_options' AND index_name = 'idx_quiz_options_question_id');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `email_change_tokens` ADD KEY `idx_email_change_tokens_user_open` (`user_id`, `used_at`, `expires_at`)',
    'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'email_change_tokens' AND index_name = 'idx_email_change_tokens_user_open');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `email_change_tokens` DROP INDEX `idx_email_change_tokens_user_id`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'email_change_tokens' AND index_name = 'idx_email_change_tokens_user_id');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

-- 3. Drop unqueried low-cardinality hsk_level secondary indexes on practice content tables
SET @v14_sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `flashcards` DROP INDEX `idx_flashcards_hsk_level`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'flashcards' AND index_name = 'idx_flashcards_hsk_level');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `matching_games` DROP INDEX `idx_matching_games_hsk_level`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'matching_games' AND index_name = 'idx_matching_games_hsk_level');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `listening_exercises` DROP INDEX `idx_listening_exercises_hsk_level`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'listening_exercises' AND index_name = 'idx_listening_exercises_hsk_level');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `tracing_exercises` DROP INDEX `idx_tracing_exercises_hsk_level`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'tracing_exercises' AND index_name = 'idx_tracing_exercises_hsk_level');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `drag_drop_activities` DROP INDEX `idx_drag_drop_activities_hsk_level`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'drag_drop_activities' AND index_name = 'idx_drag_drop_activities_hsk_level');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SET @v14_sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `typing_exercises` DROP INDEX `idx_typing_exercises_hsk_level`', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'typing_exercises' AND index_name = 'idx_typing_exercises_hsk_level');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;
