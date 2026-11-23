-- Schema changes of the project-wide review (applied after V1__consolidated_schema.sql).

-- Tracing activities: the teacher form allows longer pinyin and meanings than V1's columns held.
ALTER TABLE `tracing_exercises`
  MODIFY `pinyin` varchar(400) DEFAULT NULL,
  MODIFY `meaning` varchar(700) DEFAULT NULL;

-- A class lesson's completion time, so analytics count a lesson in the period it was completed, not each time a
-- completed lesson is revisited (which changes updated_at). Existing completed lessons get their last change.
ALTER TABLE `lesson_progress` ADD COLUMN `completed_at` datetime(6) DEFAULT NULL;
UPDATE `lesson_progress` SET `completed_at` = `updated_at` WHERE `completed` = b'1';

-- Hint steps the server gave in Learn lessons, the Mistakes review and Practice, so the per-set hint limit holds on
-- the server (item_ref names the run's set and the item, e.g. LESSON#12#345, MISTAKES#78, PRACTICE#QUIZZES#3#2).
CREATE TABLE `served_hints` (
  `step` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `item_ref` varchar(120) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_served_hints_student_item` (`student_id`,`item_ref`,`created_at`),
  CONSTRAINT `fk_served_hints_student` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`),
  CONSTRAINT `chk_served_hints_step` CHECK ((`step` between 1 and 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The interface language a visitor wrote a support request in, so support's e-mailed reply uses it (older: English).
ALTER TABLE `support_requests` ADD COLUMN `requester_language` varchar(10) DEFAULT NULL;

-- Live session reminders: when the "starts soon" reminder of the session's current start went out (once per start).
ALTER TABLE `live_classes` ADD COLUMN `reminder_sent_at` datetime(6) DEFAULT NULL;

-- Who archived a class: a teacher may restore only a class they archived themselves, never one an admin archived.
ALTER TABLE `course_packages`
  ADD COLUMN `archived_by_user_id` bigint DEFAULT NULL,
  ADD KEY `fk_course_packages_archived_by` (`archived_by_user_id`),
  ADD CONSTRAINT `fk_course_packages_archived_by` FOREIGN KEY (`archived_by_user_id`) REFERENCES `users` (`id`);
-- Columns no code maps, reads or writes (ai_practice_checks.shape_score / tone_detail, student_profiles.school_name).
-- They are dropped only where they hold no data: a database whose earlier version stored values keeps them untouched,
-- so nothing is lost (an unmapped column does not disturb Hibernate's validation).
SET @drop_practice_check_extras = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_practice_checks` DROP CHECK `chk_ai_practice_checks_scores`, DROP COLUMN `shape_score`, DROP COLUMN `tone_detail`, ADD CONSTRAINT `chk_ai_practice_checks_scores` CHECK (((`score` between 0 and 100) and ((`tones_score` is null) or (`tones_score` between 0 and 100)) and ((`clarity_score` is null) or (`clarity_score` between 0 and 100)) and ((`pace_score` is null) or (`pace_score` between 0 and 100))))',
    'DO 0')
  FROM `ai_practice_checks` WHERE `shape_score` IS NOT NULL OR `tone_detail` IS NOT NULL);
PREPARE drop_practice_check_extras FROM @drop_practice_check_extras;
EXECUTE drop_practice_check_extras;
DEALLOCATE PREPARE drop_practice_check_extras;

SET @drop_school_name = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE `student_profiles` DROP COLUMN `school_name`', 'DO 0')
  FROM `student_profiles` WHERE `school_name` IS NOT NULL);
PREPARE drop_school_name FROM @drop_school_name;
EXECUTE drop_school_name;
DEALLOCATE PREPARE drop_school_name;

-- Classes archived before the column existed: the actor of their latest CLASS_ARCHIVED event archived them.
UPDATE `course_packages` cp
SET cp.`archived_by_user_id` = (
  SELECT e.`actor_user_id` FROM `class_content_events` e
  WHERE e.`course_package_id` = cp.`id` AND e.`event_type` = 'CLASS_ARCHIVED'
  ORDER BY e.`created_at` DESC, e.`id` DESC
  LIMIT 1)
WHERE cp.`content_status` = 'ARCHIVED';

-- The teacher's "New student" notices written before they pointed at the enrolment (target CLASS + the class id,
-- message "<student> joined <class>.") point at it now, as StudentClassService writes them, so deleting the student's
-- account finds and removes them. The enrolment is the one in that class, of that teacher, whose student's name opens
-- the message exactly (byte for byte); the class title is not compared, since the class may have been renamed since.
UPDATE `notifications` n
  JOIN `course_packages` cp ON cp.`id` = n.`target_id`
  JOIN `teacher_profiles` tp ON tp.`id` = cp.`teacher_id` AND tp.`user_id` = n.`user_id`
  JOIN `class_enrolments` ce ON ce.`course_package_id` = cp.`id`
  JOIN `student_profiles` sp ON sp.`id` = ce.`student_id`
  JOIN `users` u ON u.`id` = sp.`user_id`
SET n.`target_type` = 'CLASS_ENROLMENT', n.`target_id` = ce.`id`
WHERE n.`notification_type` = 'CLASS_ENROLMENT' AND n.`target_type` = 'CLASS'
  AND LEFT(n.`message`, CHAR_LENGTH(u.`display_name`) + 8) = CONCAT(u.`display_name`, ' joined ') COLLATE utf8mb4_bin;
