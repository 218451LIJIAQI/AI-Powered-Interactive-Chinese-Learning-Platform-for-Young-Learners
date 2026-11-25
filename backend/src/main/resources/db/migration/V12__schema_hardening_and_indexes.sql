-- 1. Normalize and tighten the four VARCHAR enum columns in AI tables to exact uppercase constant names.
--    Hibernate maps them with @Enumerated(STRING), while V1/V4 wrote case-insensitive CHECK constraints on
--    utf8mb4_0900_ai_ci columns. Normalize any mixed-case or padded legacy values first, then defensively replace each
--    constraint using COLLATE utf8mb4_bin (matching V11's chk_quiz_questions_question_type pattern).
UPDATE `ai_chat_messages`
SET `sender_role` = UPPER(TRIM(`sender_role`))
WHERE `sender_role` COLLATE utf8mb4_bin <> UPPER(TRIM(`sender_role`));

SET @drop_check = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `ai_chat_messages` DROP CHECK `chk_ai_chat_messages_sender_role`', 'DO 0')
  FROM information_schema.table_constraints
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_chat_messages'
    AND constraint_name = 'chk_ai_chat_messages_sender_role'
    AND constraint_type = 'CHECK');
PREPARE drop_check_stmt FROM @drop_check; EXECUTE drop_check_stmt; DEALLOCATE PREPARE drop_check_stmt;

ALTER TABLE `ai_chat_messages`
  ADD CONSTRAINT `chk_ai_chat_messages_sender_role` CHECK (`sender_role` COLLATE utf8mb4_bin IN ('USER','ASSISTANT'));

UPDATE `ai_usage_logs`
SET `status` = UPPER(TRIM(`status`))
WHERE `status` COLLATE utf8mb4_bin <> UPPER(TRIM(`status`));

SET @drop_check = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `ai_usage_logs` DROP CHECK `chk_ai_usage_logs_status`', 'DO 0')
  FROM information_schema.table_constraints
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_usage_logs'
    AND constraint_name = 'chk_ai_usage_logs_status'
    AND constraint_type = 'CHECK');
PREPARE drop_check_stmt FROM @drop_check; EXECUTE drop_check_stmt; DEALLOCATE PREPARE drop_check_stmt;

ALTER TABLE `ai_usage_logs`
  ADD CONSTRAINT `chk_ai_usage_logs_status` CHECK (`status` COLLATE utf8mb4_bin IN ('SUCCESS','FAILED','UNAVAILABLE'));

UPDATE `ai_response_reports`
SET `status` = UPPER(TRIM(`status`))
WHERE `status` COLLATE utf8mb4_bin <> UPPER(TRIM(`status`));

UPDATE `ai_response_reports`
SET `reason_category` = UPPER(TRIM(`reason_category`))
WHERE `reason_category` COLLATE utf8mb4_bin <> UPPER(TRIM(`reason_category`));

SET @drop_check = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `ai_response_reports` DROP CHECK `chk_ai_response_reports_status`', 'DO 0')
  FROM information_schema.table_constraints
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_response_reports'
    AND constraint_name = 'chk_ai_response_reports_status'
    AND constraint_type = 'CHECK');
PREPARE drop_check_stmt FROM @drop_check; EXECUTE drop_check_stmt; DEALLOCATE PREPARE drop_check_stmt;

SET @drop_check = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `ai_response_reports` DROP CHECK `chk_ai_response_reports_reason_category`', 'DO 0')
  FROM information_schema.table_constraints
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_response_reports'
    AND constraint_name = 'chk_ai_response_reports_reason_category'
    AND constraint_type = 'CHECK');
PREPARE drop_check_stmt FROM @drop_check; EXECUTE drop_check_stmt; DEALLOCATE PREPARE drop_check_stmt;

SET @drop_check = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE `ai_response_reports` DROP CHECK `chk_ai_response_reports_review_consistency`', 'DO 0')
  FROM information_schema.table_constraints
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_response_reports'
    AND constraint_name = 'chk_ai_response_reports_review_consistency'
    AND constraint_type = 'CHECK');
PREPARE drop_check_stmt FROM @drop_check; EXECUTE drop_check_stmt; DEALLOCATE PREPARE drop_check_stmt;

ALTER TABLE `ai_response_reports`
  ADD CONSTRAINT `chk_ai_response_reports_status` CHECK (`status` COLLATE utf8mb4_bin IN ('PENDING_REVIEW','RESOLVED','DISMISSED')),
  ADD CONSTRAINT `chk_ai_response_reports_reason_category` CHECK (`reason_category` COLLATE utf8mb4_bin IN ('INCORRECT_CONTENT','UNCLEAR_EXPLANATION','INAPPROPRIATE_CONTENT','SCORE_SEEMS_WRONG','DOES_NOT_MATCH_INPUT','OTHER')),
  ADD CONSTRAINT `chk_ai_response_reports_review_consistency` CHECK (((`status` COLLATE utf8mb4_bin = 'PENDING_REVIEW') AND (`reviewed_at` IS NULL)) OR ((`status` COLLATE utf8mb4_bin IN ('RESOLVED','DISMISSED')) AND (`reviewed_at` IS NOT NULL)));

-- 2. Add hot-path indexes for support queue sorting, visitor-email lookup on account deletion, AI usage summary
--    aggregation, and active refresh-token session checks.
ALTER TABLE `support_requests`
  ADD KEY `idx_support_requests_status_updated` (`request_status`, `updated_at`, `id`),
  ADD KEY `idx_support_requests_status_solved` (`request_status`, `solved_at`, `id`),
  ADD KEY `idx_support_requests_requester_email` (`requester_email`);

ALTER TABLE `ai_usage_logs`
  ADD KEY `idx_ai_usage_logs_feature_status` (`feature_key`, `status`);

ALTER TABLE `refresh_tokens`
  ADD KEY `idx_refresh_tokens_family_active` (`user_id`, `family_id`, `revoked_at`, `expires_at`),
  ADD KEY `idx_refresh_tokens_user_revoked` (`user_id`, `revoked_at`);

-- 3. Defensively drop unqueried or strictly left-prefix-redundant indexes only when their covering index is present.
SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_support_requests_status_created' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_support_requests_status_updated' AND seq_in_index = 1 AND column_name = 'request_status' AND is_visible = 'YES') = 1,
    'ALTER TABLE `support_requests` DROP INDEX `idx_support_requests_status_created`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'support_requests');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_ai_usage_logs_feature_key' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_ai_usage_logs_feature_status' AND seq_in_index = 1 AND column_name = 'feature_key' AND is_visible = 'YES') = 1,
    'ALTER TABLE `ai_usage_logs` DROP INDEX `idx_ai_usage_logs_feature_key`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_usage_logs');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(
    SUM(index_name = 'idx_refresh_tokens_user_id' AND seq_in_index = 1) = 1
    AND SUM(index_name = 'idx_refresh_tokens_user_revoked' AND seq_in_index = 1 AND column_name = 'user_id' AND is_visible = 'YES') = 1,
    'ALTER TABLE `refresh_tokens` DROP INDEX `idx_refresh_tokens_user_id`', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'refresh_tokens');
PREPARE drop_index_stmt FROM @drop_index; EXECUTE drop_index_stmt; DEALLOCATE PREPARE drop_index_stmt;
