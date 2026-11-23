-- Talk with Xiaomei remembers a few short notes per learner (S7.14, S7.15, 2026-11-17), and a deleted practice check can
-- be brought back for a moment (the Undo of Delete). Applied after V4__plan_practice_notifications.sql; the entities
-- match the schema below (MySqlMigrationSmokeTests).

-- 1. What Xiaomei remembers about a learner, used only in that learner's own chats: the goal of their learning plan
--    (plan_reason set; note_text is the plan's goal title, null for a plan built from rules) or a short fact they shared
--    in a chat (note_text only, at most 80 characters). "Forget" sets forgotten_at and its Undo clears it. A forgotten
--    chat note is deleted for good once the Undo window has passed; a forgotten plan goal stays forgotten until the goal
--    changes. Account deletion removes every note.
CREATE TABLE `xiaomei_memory_notes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `student_user_id` bigint NOT NULL,
  `note_text` varchar(80) DEFAULT NULL,
  `plan_reason` enum('EVERYDAY','JUST_FOR_ME','OTHER','STUDY','TRAVEL','WORK') DEFAULT NULL,
  `forgotten_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_xiaomei_memory_notes_student` (`student_user_id`,`forgotten_at`),
  KEY `idx_xiaomei_memory_notes_forgotten` (`forgotten_at`),
  CONSTRAINT `fk_xiaomei_memory_notes_student_user` FOREIGN KEY (`student_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_xiaomei_memory_notes_content` CHECK (((`note_text` is not null) or (`plan_reason` is not null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. Delete in Recent checks takes a practice check away at once: deleted_at is set, the check leaves the learner's
--    list, and its Undo clears it again. The check is deleted for good once the Undo window has passed.
ALTER TABLE `ai_practice_checks` ADD COLUMN `deleted_at` datetime(6) DEFAULT NULL,
  ADD KEY `idx_ai_practice_checks_deleted` (`deleted_at`);
