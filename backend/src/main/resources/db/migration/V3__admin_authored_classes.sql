-- Teachers are removed (2026-11-16). Classes are themed courses the MandarinSpark team authors in the admin portal: an
-- admin publishes a lesson or a class directly, with no review step. Live sessions, learners' questions to teachers,
-- teacher feedback, teacher applications and the review history of classes go with the teachers. Applied after
-- V2__review_fixes.sql; the entities match the schema below (MySqlMigrationSmokeTests).

-- 1. Content in review (SUBMITTED) or sent back with changes requested (REJECTED) becomes a draft again, the working
--    copies of published lessons included: an admin publishes it from the editor. The status a class or lesson had
--    before it was archived follows, so a restore never brings a review status back.
UPDATE `course_packages` SET `content_status` = 'DRAFT' WHERE `content_status` IN ('SUBMITTED', 'REJECTED');
UPDATE `course_packages` SET `status_before_archive` = 'DRAFT' WHERE `status_before_archive` IN ('SUBMITTED', 'REJECTED');
UPDATE `lessons` SET `content_status` = 'DRAFT' WHERE `content_status` IN ('SUBMITTED', 'REJECTED');
UPDATE `lessons` SET `status_before_archive` = 'DRAFT' WHERE `status_before_archive` IN ('SUBMITTED', 'REJECTED');
UPDATE `learning_modules` SET `content_status` = 'DRAFT' WHERE `content_status` IN ('SUBMITTED', 'REJECTED');
UPDATE `quizzes` SET `content_status` = 'DRAFT' WHERE `content_status` IN ('SUBMITTED', 'REJECTED');
UPDATE `flashcards` SET `content_status` = 'DRAFT' WHERE `content_status` IN ('SUBMITTED', 'REJECTED');
UPDATE `tracing_exercises` SET `content_status` = 'DRAFT' WHERE `content_status` IN ('SUBMITTED', 'REJECTED');
UPDATE `matching_games` SET `content_status` = 'DRAFT' WHERE `content_status` IN ('SUBMITTED', 'REJECTED');
UPDATE `listening_exercises` SET `content_status` = 'DRAFT' WHERE `content_status` IN ('SUBMITTED', 'REJECTED');
UPDATE `drag_drop_activities` SET `content_status` = 'DRAFT' WHERE `content_status` IN ('SUBMITTED', 'REJECTED');
UPDATE `typing_exercises` SET `content_status` = 'DRAFT' WHERE `content_status` IN ('SUBMITTED', 'REJECTED');
UPDATE `practice_quizzes` SET `content_status` = 'DRAFT' WHERE `content_status` IN ('SUBMITTED', 'REJECTED');

ALTER TABLE `course_packages`
  MODIFY `content_status` enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL,
  MODIFY `status_before_archive` enum('ARCHIVED','DRAFT','PUBLISHED') DEFAULT NULL;
ALTER TABLE `lessons`
  MODIFY `content_status` enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL,
  MODIFY `status_before_archive` enum('ARCHIVED','DRAFT','PUBLISHED') DEFAULT NULL;
ALTER TABLE `learning_modules` MODIFY `content_status` enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL;
ALTER TABLE `quizzes` MODIFY `content_status` enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL;
ALTER TABLE `flashcards` MODIFY `content_status` enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL;
ALTER TABLE `tracing_exercises` MODIFY `content_status` enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL;
ALTER TABLE `matching_games` MODIFY `content_status` enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL;
ALTER TABLE `listening_exercises` MODIFY `content_status` enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL;
ALTER TABLE `drag_drop_activities` MODIFY `content_status` enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL;
ALTER TABLE `typing_exercises` MODIFY `content_status` enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL;
ALTER TABLE `practice_quizzes` MODIFY `content_status` enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL;

-- 2. Classes: when a class was archived replaces who archived it (all admins edit all classes; the audit log names who
--    did it). A class archived before V3 takes the time of its latest archive step in the class history (dropped
--    below), or its last change. The review columns (rejection reasons, submission times, pending details of a
--    published class) and the teacher go.
ALTER TABLE `course_packages` ADD COLUMN `archived_at` datetime(6) DEFAULT NULL;
UPDATE `course_packages` cp
SET cp.`archived_at` = COALESCE(
  (SELECT MAX(e.`created_at`) FROM `class_content_events` e
   WHERE e.`course_package_id` = cp.`id` AND e.`event_type` = 'CLASS_ARCHIVED'),
  cp.`updated_at`)
WHERE cp.`content_status` = 'ARCHIVED';

ALTER TABLE `course_packages`
  DROP FOREIGN KEY `FKro7vyjl7p0lepwedme1h5uqxe`,
  DROP FOREIGN KEY `fk_course_packages_pending_category`,
  DROP FOREIGN KEY `fk_course_packages_archived_by`;
ALTER TABLE `course_packages`
  DROP COLUMN `teacher_id`,
  DROP COLUMN `archived_by_user_id`,
  DROP COLUMN `rejection_reason`,
  DROP COLUMN `submitted_at`,
  DROP COLUMN `pending_title`,
  DROP COLUMN `pending_description`,
  DROP COLUMN `pending_category_id`,
  DROP COLUMN `pending_best_after_unit`,
  DROP COLUMN `revision_status`,
  DROP COLUMN `revision_rejection_reason`,
  DROP COLUMN `revision_submitted_at`;

-- 3. Units, lessons and quizzes lose their teacher and the review columns. A quiz's hint setting becomes a switch of
--    its own ("Allow SparkBot hints"): on where the stage allowed hints (any stage but INDEPENDENT, or none). A
--    question added from the AI quiz draft is marked until an admin keeps or edits it (no earlier question is).
ALTER TABLE `learning_modules` DROP FOREIGN KEY `FKngka0ebpyqfu9deb7mflm7kms`;
ALTER TABLE `learning_modules` DROP COLUMN `teacher_id`;

ALTER TABLE `lessons` DROP FOREIGN KEY `FK85pfpipvgxct5yalal134dq1w`;
ALTER TABLE `lessons`
  DROP COLUMN `teacher_id`,
  DROP COLUMN `rejection_reason`,
  DROP COLUMN `submitted_at`;

ALTER TABLE `quizzes` ADD COLUMN `hints_allowed` bit(1) NOT NULL DEFAULT b'0';
UPDATE `quizzes` SET `hints_allowed` = IF(`learning_stage` IS NULL OR `learning_stage` <> 'INDEPENDENT', b'1', b'0');
ALTER TABLE `quizzes` DROP FOREIGN KEY `FKh8m8lpcfawschgw6url6b13yy`;
ALTER TABLE `quizzes`
  DROP COLUMN `teacher_id`,
  DROP COLUMN `learning_stage`;

ALTER TABLE `quiz_questions` ADD COLUMN `ai_draft` bit(1) NOT NULL DEFAULT b'0';

-- 4. The tables of the removed features. A verification document's upload record goes with it; its stored file stays in
--    UPLOAD_DIR (a migration cannot delete files), for the operator to remove.
CREATE TEMPORARY TABLE `v3_verification_uploads` (`id` bigint NOT NULL PRIMARY KEY);
INSERT INTO `v3_verification_uploads` (`id`)
  SELECT DISTINCT `uploaded_file_id` FROM `teacher_verification_documents` WHERE `uploaded_file_id` IS NOT NULL;
DROP TABLE `teacher_verification_reviews`;
DROP TABLE `teacher_verification_documents`;
DELETE uf FROM `uploaded_files` uf JOIN `v3_verification_uploads` v ON v.`id` = uf.`id`;
DROP TEMPORARY TABLE `v3_verification_uploads`;

DROP TABLE `live_classes`;
DROP TABLE `class_questions`;
DROP TABLE `teacher_feedback`;
-- The history of a class (created, submitted, approved, changes requested, archived, restored) served the review: the
-- admin audit log records class and lesson creation, publishing, archiving, restoring and deletion.
DROP TABLE `class_content_events`;
DROP TABLE `teacher_profiles`;

-- 5. The TEACHER role. An account left without a role (a teacher account) keeps its row, because records point to it
--    (audit entries, uploads, support requests), but can no longer sign in: it is deactivated, its session version is
--    moved on (issued access tokens stop working) and its refresh tokens are deleted. Support requests sent from such
--    accounts stay in the admin queue as they were (requester role TEACHER, topic VERIFICATION).
DELETE ur FROM `user_roles` ur JOIN `roles` r ON r.`id` = ur.`role_id` WHERE r.`name` = 'TEACHER';
CREATE TEMPORARY TABLE `v3_roleless_users` (`id` bigint NOT NULL PRIMARY KEY);
INSERT INTO `v3_roleless_users` (`id`)
  SELECT u.`id` FROM `users` u WHERE NOT EXISTS (SELECT 1 FROM `user_roles` ur WHERE ur.`user_id` = u.`id`);
UPDATE `users` u JOIN `v3_roleless_users` v ON v.`id` = u.`id`
SET u.`active` = b'0', u.`session_version` = u.`session_version` + 1;
DELETE rt FROM `refresh_tokens` rt JOIN `v3_roleless_users` v ON v.`id` = rt.`user_id`;
DROP TEMPORARY TABLE `v3_roleless_users`;
DELETE FROM `roles` WHERE `name` = 'TEACHER';
ALTER TABLE `roles` MODIFY `name` enum('ADMIN','STUDENT') NOT NULL;

-- 6. Notifications of the removed features, and the learners' support notices, which opened the removed Help & support
--    pages (support now answers learners by e-mail).
DELETE FROM `notifications`
WHERE `notification_type` IN (
  'TEACHER_FEEDBACK', 'LIVE_CLASS_REMINDER', 'TEACHER_APPROVAL', 'CONTENT_REVIEW', 'MODERATION_ACTION',
  'CLASS_ENROLMENT', 'CLASS_QUESTION')
  OR (`notification_type` = 'SUPPORT_REQUEST' AND (`target_path` LIKE '/student/support%' OR `target_path` LIKE '/teacher/support%'));
ALTER TABLE `notifications`
  MODIFY `notification_type` enum('ADMIN_TASK','AI_REPORT_REVIEW','SUPPORT_REQUEST','SYSTEM') NOT NULL;

-- 7. The AI quiz draft of the lesson editor is the admins' now: its feature key TEACHER_DRAFT becomes QUIZ_DRAFT.
ALTER TABLE `ai_usage_logs` MODIFY `feature_key`
  enum('CHAT_TUTOR','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_DRAFT','QUIZ_GENERATOR','STORY_GENERATOR','TEACHER_DRAFT','XIAOMEI_CONVERSATION') NOT NULL;
ALTER TABLE `ai_response_reports` MODIFY `feature_key`
  enum('CHAT_TUTOR','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_DRAFT','QUIZ_GENERATOR','STORY_GENERATOR','TEACHER_DRAFT','XIAOMEI_CONVERSATION') NOT NULL;
ALTER TABLE `ai_chat_sessions` MODIFY `feature_key`
  enum('CHAT_TUTOR','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_DRAFT','QUIZ_GENERATOR','STORY_GENERATOR','TEACHER_DRAFT','XIAOMEI_CONVERSATION') NOT NULL;
UPDATE `ai_usage_logs` SET `feature_key` = 'QUIZ_DRAFT' WHERE `feature_key` = 'TEACHER_DRAFT';
UPDATE `ai_response_reports` SET `feature_key` = 'QUIZ_DRAFT' WHERE `feature_key` = 'TEACHER_DRAFT';
UPDATE `ai_chat_sessions` SET `feature_key` = 'QUIZ_DRAFT' WHERE `feature_key` = 'TEACHER_DRAFT';
ALTER TABLE `ai_usage_logs` MODIFY `feature_key`
  enum('CHAT_TUTOR','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_DRAFT','QUIZ_GENERATOR','STORY_GENERATOR','XIAOMEI_CONVERSATION') NOT NULL;
ALTER TABLE `ai_response_reports` MODIFY `feature_key`
  enum('CHAT_TUTOR','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_DRAFT','QUIZ_GENERATOR','STORY_GENERATOR','XIAOMEI_CONVERSATION') NOT NULL;
ALTER TABLE `ai_chat_sessions` MODIFY `feature_key`
  enum('CHAT_TUTOR','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_DRAFT','QUIZ_GENERATOR','STORY_GENERATOR','XIAOMEI_CONVERSATION') NOT NULL;

-- 8. E-mail switches: live session reminders, teacher feedback and content reviews go. The admins' daily e-mail
--    (notify_review_queues, "Daily summary") stays, about the queues that remain: AI reports and support requests.
ALTER TABLE `users`
  DROP COLUMN `notify_live_session_reminders`,
  DROP COLUMN `notify_feedback`,
  DROP COLUMN `notify_content_reviews`;
