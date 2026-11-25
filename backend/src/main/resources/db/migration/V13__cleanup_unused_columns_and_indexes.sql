-- Forward-only cleanup of write-only columns, retired TONES practice checks, and unqueried non-FK secondary indexes.
-- Helper procedures avoid repeating dynamic information_schema guards and are dropped before the static DDL below.

DELIMITER $$

CREATE PROCEDURE `v13_add_index_if_missing`(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(128))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index
  ) THEN
    SET @v13_sql = CONCAT('ALTER TABLE `', p_table, '` ADD KEY `', p_index, '` (', p_cols, ')');
    PREPARE v13_stmt FROM @v13_sql;
    EXECUTE v13_stmt;
    DEALLOCATE PREPARE v13_stmt;
  END IF;
END$$

CREATE PROCEDURE `v13_drop_index_if_exists`(IN p_table VARCHAR(64), IN p_index VARCHAR(64))
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index
  ) THEN
    SET @v13_sql = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_index, '`');
    PREPARE v13_stmt FROM @v13_sql;
    EXECUTE v13_stmt;
    DEALLOCATE PREPARE v13_stmt;
  END IF;
END$$

CREATE PROCEDURE `v13_drop_fk_for_column`(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
BEGIN
  DECLARE v_fk_name VARCHAR(64);
  SELECT constraint_name INTO v_fk_name
  FROM information_schema.key_column_usage
  WHERE table_schema = DATABASE()
    AND table_name = p_table
    AND column_name = p_column
    AND referenced_table_name IS NOT NULL
  LIMIT 1;
  IF v_fk_name IS NOT NULL THEN
    SET @v13_sql = CONCAT('ALTER TABLE `', p_table, '` DROP FOREIGN KEY `', v_fk_name, '`');
    PREPARE v13_stmt FROM @v13_sql;
    EXECUTE v13_stmt;
    DEALLOCATE PREPARE v13_stmt;
  END IF;
END$$

DELIMITER ;

-- Preserve a plain user_id index on ai_chat_sessions for FK enforcement and account deletion before dropping feature_key.
CALL `v13_add_index_if_missing`('ai_chat_sessions', 'idx_ai_chat_sessions_user_id', '`user_id`');

-- Drop the denormalized video_progress.lesson_id FK defensively (V1 hash or Hibernate-generated name).
CALL `v13_drop_fk_for_column`('video_progress', 'lesson_id');

-- Drop unqueried or superseded non-FK secondary indexes (all remaining FKs retain covering leftmost indexes).
CALL `v13_drop_index_if_exists`('refresh_tokens', 'idx_refresh_tokens_family_id');
CALL `v13_drop_index_if_exists`('refresh_tokens', 'idx_refresh_tokens_expires_at');
CALL `v13_drop_index_if_exists`('ai_chat_sessions', 'idx_ai_chat_sessions_feature_key');
CALL `v13_drop_index_if_exists`('ai_chat_sessions', 'idx_ai_chat_sessions_user_feature_created');
CALL `v13_drop_index_if_exists`('ai_response_reports', 'idx_ai_response_reports_feature_key');
CALL `v13_drop_index_if_exists`('audit_logs', 'idx_audit_logs_request_id');
CALL `v13_drop_index_if_exists`('audit_logs', 'idx_audit_logs_actor_email_created');
CALL `v13_drop_index_if_exists`('uploaded_files', 'idx_uploaded_files_created_at');
CALL `v13_drop_index_if_exists`('student_badges', 'idx_student_badges_student_awarded');
CALL `v13_drop_index_if_exists`('practice_completions', 'idx_practice_completions_student_created');
CALL `v13_drop_index_if_exists`('learning_modules', 'idx_learning_modules_status_created_at');
CALL `v13_drop_index_if_exists`('learning_modules', 'idx_learning_modules_status_updated');
CALL `v13_drop_index_if_exists`('lessons', 'idx_lessons_status_created_at');
CALL `v13_drop_index_if_exists`('quizzes', 'idx_quizzes_status_updated');
CALL `v13_drop_index_if_exists`('video_progress', 'idx_video_progress_lesson_last_watched');

DROP PROCEDURE `v13_add_index_if_missing`;
DROP PROCEDURE `v13_drop_index_if_exists`;
DROP PROCEDURE `v13_drop_fk_for_column`;

-- Drop write-only and denormalized columns (coalesced per table).
ALTER TABLE `learning_modules`
  DROP COLUMN `content_status`;

ALTER TABLE `ai_chat_sessions`
  DROP COLUMN `feature_key`;

ALTER TABLE `uploaded_files`
  DROP COLUMN `active`;

ALTER TABLE `video_progress`
  DROP COLUMN `last_watched_at`,
  DROP COLUMN `lesson_id`;

ALTER TABLE `refresh_tokens`
  DROP COLUMN `created_by_ip`,
  DROP COLUMN `user_agent`;

-- Remove retired TONES practice check rows and tighten check_type to active values only.
DELETE FROM `ai_practice_checks`
WHERE `check_type` = 'TONES';

ALTER TABLE `ai_practice_checks`
  MODIFY COLUMN `check_type` ENUM('HANDWRITING', 'RECORDING') NOT NULL;
