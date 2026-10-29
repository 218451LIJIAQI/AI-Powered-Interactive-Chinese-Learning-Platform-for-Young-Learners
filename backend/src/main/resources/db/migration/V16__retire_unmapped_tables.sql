-- =====================================================================
-- V16 - Retire tables no code maps any more (MySQL 8.4).
--
--   1. content_moderation_records: the moderation log of the retired admin
--      communication tools. Admin decisions on classes, lessons, units and
--      practice sets are recorded in audit_logs, and reported AI replies in
--      ai_response_reports.
--   2. student_quiz_progress: a per-quiz summary of attempts. Lesson
--      progress, mastery and weak areas are computed from quiz_attempts and
--      lesson_progress, so nothing read the summary.
--
-- No other table references either one; their own foreign keys and indexes
-- go with them. V1-V15 are never edited.
-- =====================================================================

drop table if exists content_moderation_records;
drop table if exists student_quiz_progress;
