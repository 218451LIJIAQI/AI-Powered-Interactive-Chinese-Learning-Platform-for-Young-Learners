-- Each counter already has one unique index for its owner. Databases baselined from Hibernate can lack
-- the duplicate index. Only remove V1's single-column copy when its matching unique index is present.
SET @drop_index = (
  SELECT IF(SUM(column_name = 'user_id') = 2 AND MAX(seq_in_index) = 1,
    'ALTER TABLE ai_usage_quota_counters DROP INDEX idx_ai_usage_quota_counters_user_id', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'ai_usage_quota_counters'
    AND ((index_name = 'idx_ai_usage_quota_counters_user_id' AND non_unique = 1)
      OR (index_name = 'uk_ai_usage_quota_counters_user' AND non_unique = 0 AND is_visible = 'YES')));
PREPARE drop_index_stmt FROM @drop_index;
EXECUTE drop_index_stmt;
DEALLOCATE PREPARE drop_index_stmt;

SET @drop_index = (
  SELECT IF(SUM(column_name = 'student_id') = 2 AND MAX(seq_in_index) = 1,
    'ALTER TABLE daily_streaks DROP INDEX idx_daily_streaks_student_id', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'daily_streaks'
    AND ((index_name = 'idx_daily_streaks_student_id' AND non_unique = 1)
      OR (index_name = 'uk_daily_streaks_student' AND non_unique = 0 AND is_visible = 'YES')));
PREPARE drop_index_stmt FROM @drop_index;
EXECUTE drop_index_stmt;
DEALLOCATE PREPARE drop_index_stmt;
