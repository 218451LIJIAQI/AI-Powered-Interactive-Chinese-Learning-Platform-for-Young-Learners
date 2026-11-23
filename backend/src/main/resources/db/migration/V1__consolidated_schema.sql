-- MandarinSpark MySQL schema (Flyway baseline).
--
-- The complete schema in one migration: every table with its keys, foreign keys, CHECK constraints and indexes,
-- plus the reference rows the application expects on a fresh database. Hibernate validates the entities against it
-- (spring.jpa.hibernate.ddl-auto=validate). The demo and test profiles let Hibernate build their own databases.
--
-- Tables reference each other in both directions, so foreign-key checks are off while they are created.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `admin_invites` (
  `accepted_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `email` varchar(150) NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_invites_token_hash` (`token_hash`),
  KEY `idx_admin_invites_email` (`email`),
  KEY `idx_admin_invites_pending` (`accepted_at`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_chat_messages` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `sender_role` varchar(30) NOT NULL,
  `message_text` longtext NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_chat_messages_session_id` (`session_id`),
  KEY `idx_ai_chat_messages_session_role_id` (`session_id`,`sender_role`,`id`),
  KEY `idx_ai_chat_messages_session_created` (`session_id`,`created_at`),
  CONSTRAINT `FKncfaarj6gwd37j1fi98bmpmcl` FOREIGN KEY (`session_id`) REFERENCES `ai_chat_sessions` (`id`),
  CONSTRAINT `chk_ai_chat_messages_sender_role` CHECK ((`sender_role` in (_utf8mb4'USER',_utf8mb4'ASSISTANT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_chat_sessions` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `feature_key` enum('CHAT_TUTOR','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_GENERATOR','STORY_GENERATOR','TEACHER_DRAFT','XIAOMEI_CONVERSATION') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_chat_sessions_user_id` (`user_id`),
  KEY `idx_ai_chat_sessions_feature_key` (`feature_key`),
  KEY `idx_ai_chat_sessions_user_feature_created` (`user_id`,`feature_key`,`created_at`),
  CONSTRAINT `FK6mxk23rx514hrkne46l8bblp4` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_practice_checks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `student_user_id` bigint NOT NULL,
  `lesson_id` bigint DEFAULT NULL,
  `check_type` enum('HANDWRITING','RECORDING','TONES') NOT NULL,
  `target_text` varchar(100) NOT NULL,
  `score` int NOT NULL,
  `tones_score` int DEFAULT NULL,
  `clarity_score` int DEFAULT NULL,
  `pace_score` int DEFAULT NULL,
  `headline` varchar(200) NOT NULL,
  `feedback` varchar(1000) DEFAULT NULL,
  `tips` varchar(2000) DEFAULT NULL,
  `shape_score` int DEFAULT NULL,
  `tone_detail` longtext,
  PRIMARY KEY (`id`),
  KEY `idx_ai_practice_checks_student_created` (`student_user_id`,`created_at`),
  KEY `idx_ai_practice_checks_lesson_id` (`lesson_id`),
  CONSTRAINT `fk_ai_practice_checks_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`),
  CONSTRAINT `fk_ai_practice_checks_student_user` FOREIGN KEY (`student_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_ai_practice_checks_scores` CHECK (((`score` between 0 and 100) and ((`tones_score` is null) or (`tones_score` between 0 and 100)) and ((`clarity_score` is null) or (`clarity_score` between 0 and 100)) and ((`pace_score` is null) or (`pace_score` between 0 and 100)) and ((`shape_score` is null) or (`shape_score` between 0 and 100))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_response_reports` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `student_user_id` bigint NOT NULL,
  `reviewer_user_id` bigint DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `status` varchar(30) NOT NULL,
  `reason_category` varchar(40) NOT NULL,
  `feature_key` enum('CHAT_TUTOR','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_GENERATOR','STORY_GENERATOR','TEACHER_DRAFT','XIAOMEI_CONVERSATION') NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `reviewer_note` varchar(1000) DEFAULT NULL,
  `ai_response_snapshot` longtext,
  `prompt_snapshot` longtext,
  PRIMARY KEY (`id`),
  KEY `idx_ai_response_reports_student_user_id` (`student_user_id`),
  KEY `idx_ai_response_reports_status` (`status`),
  KEY `idx_ai_response_reports_feature_key` (`feature_key`),
  KEY `fk_ai_response_reports_reviewer_user_id` (`reviewer_user_id`),
  KEY `idx_ai_response_reports_status_created_at` (`status`,`created_at`),
  KEY `idx_ai_response_reports_student_created` (`student_user_id`,`created_at`),
  CONSTRAINT `fk_ai_response_reports_reviewer_user_id` FOREIGN KEY (`reviewer_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_ai_response_reports_student_user_id` FOREIGN KEY (`student_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_ai_response_reports_reason_category` CHECK ((`reason_category` in (_utf8mb4'INCORRECT_CONTENT',_utf8mb4'UNCLEAR_EXPLANATION',_utf8mb4'INAPPROPRIATE_CONTENT',_utf8mb4'SCORE_SEEMS_WRONG',_utf8mb4'OTHER'))),
  CONSTRAINT `chk_ai_response_reports_review_consistency` CHECK ((((`status` = _utf8mb4'PENDING_REVIEW') and (`reviewed_at` is null)) or ((`status` in (_utf8mb4'RESOLVED',_utf8mb4'DISMISSED')) and (`reviewed_at` is not null)))),
  CONSTRAINT `chk_ai_response_reports_snapshot_source` CHECK ((`ai_response_snapshot` is not null)),
  CONSTRAINT `chk_ai_response_reports_status` CHECK ((`status` in (_utf8mb4'PENDING_REVIEW',_utf8mb4'RESOLVED',_utf8mb4'DISMISSED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_usage_logs` (
  `token_count` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `language_preference` varchar(20) DEFAULT NULL,
  `status` varchar(50) NOT NULL,
  `user_role` varchar(50) DEFAULT NULL,
  `feature_key` enum('CHAT_TUTOR','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_GENERATOR','STORY_GENERATOR','TEACHER_DRAFT','XIAOMEI_CONVERSATION') NOT NULL,
  `provider_name` varchar(100) DEFAULT NULL,
  `model_name` varchar(120) DEFAULT NULL,
  `input_summary` varchar(500) DEFAULT NULL,
  `output_summary` varchar(500) DEFAULT NULL,
  `error_summary` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_usage_logs_user_id` (`user_id`),
  KEY `idx_ai_usage_logs_feature_key` (`feature_key`),
  KEY `idx_ai_usage_logs_created_at` (`created_at`),
  KEY `idx_ai_usage_logs_status_created_at` (`status`,`created_at`),
  KEY `idx_ai_usage_logs_user_feature_created` (`user_id`,`feature_key`,`created_at`),
  CONSTRAINT `FKs6ew5u0i9jag515tvn9ms1t2w` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_ai_usage_logs_status` CHECK ((`status` in (_utf8mb4'SUCCESS',_utf8mb4'FAILED',_utf8mb4'UNAVAILABLE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_usage_quota_counters` (
  `created_at` datetime(6) NOT NULL,
  `day_count` int NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `minute_count` int NOT NULL,
  `minute_started_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `usage_day` date NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_usage_quota_counters_user` (`user_id`),
  KEY `idx_ai_usage_quota_counters_user_id` (`user_id`),
  CONSTRAINT `fk_ai_usage_quota_counters_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_ai_usage_quota_counters_day_count` CHECK ((`day_count` >= 0)),
  CONSTRAINT `chk_ai_usage_quota_counters_minute_count` CHECK ((`minute_count` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_logs` (
  `actor_user_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `target_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `action_type` varchar(100) NOT NULL,
  `target_type` varchar(100) DEFAULT NULL,
  `actor_email` varchar(150) DEFAULT NULL,
  `description` longtext,
  `request_id` varchar(120) DEFAULT NULL,
  `target_label` varchar(255) DEFAULT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_logs_actor_user_id` (`actor_user_id`),
  KEY `idx_audit_logs_action_type` (`action_type`),
  KEY `idx_audit_logs_created_at` (`created_at`),
  KEY `idx_audit_logs_action_created_at` (`action_type`,`created_at`),
  KEY `idx_audit_logs_request_id` (`request_id`),
  KEY `idx_audit_logs_target_created` (`target_type`,`target_id`,`created_at`),
  KEY `idx_audit_logs_actor_created` (`actor_user_id`,`created_at`),
  KEY `idx_audit_logs_actor_email_created` (`actor_email`,`created_at`),
  CONSTRAINT `FK17vn8rhj6qver0naebk935vkk` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `badges` (
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `icon_key` varchar(100) DEFAULT NULL,
  `name` varchar(120) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_badges_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `badges` (`active`, `created_at`, `updated_at`, `icon_key`, `name`, `description`) VALUES
  (1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'first-steps', 'First Steps', 'Earned the first time you submit a quiz or complete a practice activity.'),
  (1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'quiz-star', 'Quiz Star', 'Earned the first time you score at least 80% on a quiz.'),
  (1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'lesson-finisher', 'Lesson Finisher', 'Earned the first time you complete a lesson.'),
  (1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'streak-explorer', 'Streak Explorer', 'Earned when you learn on 3 days in a row (submit a quiz or complete a practice activity each day).'),
  (1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'xp-starter', 'XP Starter', 'Earned when you collect at least 50 learning points.');

CREATE TABLE `class_activity_completions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `student_id` bigint NOT NULL,
  `lesson_id` bigint NOT NULL,
  `activity_type` enum('FLASHCARDS','MATCHING','TRACING') NOT NULL,
  `activity_id` bigint NOT NULL,
  `completed_at` datetime(6) NOT NULL,
  `times_completed` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_activity_completions_student_activity` (`student_id`,`activity_type`,`activity_id`),
  KEY `idx_class_activity_completions_lesson` (`lesson_id`,`student_id`),
  CONSTRAINT `fk_class_activity_completions_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`),
  CONSTRAINT `fk_class_activity_completions_student` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `class_content_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `course_package_id` bigint NOT NULL,
  `lesson_id` bigint DEFAULT NULL,
  `actor_user_id` bigint DEFAULT NULL,
  `event_type` enum('CLASS_APPROVED','CLASS_ARCHIVED','CLASS_CHANGES_REQUESTED','CLASS_CREATED','CLASS_DETAILS_APPROVED','CLASS_DETAILS_CHANGES_REQUESTED','CLASS_DETAILS_SUBMITTED','CLASS_RESTORED','CLASS_SUBMITTED','LESSON_APPROVED','LESSON_ARCHIVED','LESSON_CHANGES_REQUESTED','LESSON_CREATED','LESSON_RESTORED','LESSON_SUBMITTED','REVISION_APPROVED','REVISION_CHANGES_REQUESTED','REVISION_SUBMITTED') NOT NULL,
  `subject_title` varchar(200) NOT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `changes` longtext,
  PRIMARY KEY (`id`),
  KEY `fk_class_content_events_actor` (`actor_user_id`),
  KEY `idx_class_content_events_class` (`course_package_id`,`created_at`),
  KEY `idx_class_content_events_lesson` (`lesson_id`,`created_at`),
  CONSTRAINT `fk_class_content_events_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_class_content_events_class` FOREIGN KEY (`course_package_id`) REFERENCES `course_packages` (`id`),
  CONSTRAINT `fk_class_content_events_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `class_enrolments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `student_id` bigint NOT NULL,
  `course_package_id` bigint NOT NULL,
  `joined_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_enrolments_student_class` (`student_id`,`course_package_id`),
  KEY `idx_class_enrolments_class_id` (`course_package_id`),
  CONSTRAINT `fk_class_enrolments_class` FOREIGN KEY (`course_package_id`) REFERENCES `course_packages` (`id`),
  CONSTRAINT `fk_class_enrolments_student` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `class_questions` (
  `answered_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `answered_by_teacher_id` bigint DEFAULT NULL,
  `course_package_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lesson_id` bigint DEFAULT NULL,
  `student_id` bigint NOT NULL,
  `question_text` varchar(500) NOT NULL,
  `answer_text` varchar(2000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_class_questions_lesson` (`lesson_id`),
  KEY `fk_class_questions_answered_by` (`answered_by_teacher_id`),
  KEY `idx_class_questions_class_student` (`course_package_id`,`student_id`),
  KEY `idx_class_questions_student` (`student_id`,`created_at`),
  CONSTRAINT `fk_class_questions_answered_by` FOREIGN KEY (`answered_by_teacher_id`) REFERENCES `teacher_profiles` (`id`),
  CONSTRAINT `fk_class_questions_class` FOREIGN KEY (`course_package_id`) REFERENCES `course_packages` (`id`),
  CONSTRAINT `fk_class_questions_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`),
  CONSTRAINT `fk_class_questions_student` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `course_packages` (
  `category_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(200) NOT NULL,
  `rejection_reason` varchar(1000) DEFAULT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `content_status` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') NOT NULL,
  `status_before_archive` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') DEFAULT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `pending_title` varchar(200) DEFAULT NULL,
  `pending_description` varchar(2000) DEFAULT NULL,
  `pending_category_id` bigint DEFAULT NULL,
  `revision_status` enum('DRAFT','REJECTED','SUBMITTED') DEFAULT NULL,
  `revision_rejection_reason` varchar(1000) DEFAULT NULL,
  `revision_submitted_at` datetime(6) DEFAULT NULL,
  `best_after_unit` int DEFAULT NULL,
  `pending_best_after_unit` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_course_packages_teacher_id` (`teacher_id`),
  KEY `idx_course_packages_status` (`content_status`),
  KEY `FKrpijdtkt0sen2sf1anb4phqym` (`category_id`),
  KEY `idx_course_packages_status_created_at` (`content_status`,`created_at`),
  KEY `idx_course_packages_status_updated` (`content_status`,`updated_at`),
  KEY `fk_course_packages_pending_category` (`pending_category_id`),
  KEY `idx_course_packages_revision_status` (`revision_status`),
  CONSTRAINT `fk_course_packages_pending_category` FOREIGN KEY (`pending_category_id`) REFERENCES `learning_categories` (`id`),
  CONSTRAINT `FKro7vyjl7p0lepwedme1h5uqxe` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profiles` (`id`),
  CONSTRAINT `FKrpijdtkt0sen2sf1anb4phqym` FOREIGN KEY (`category_id`) REFERENCES `learning_categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `daily_streaks` (
  `best_streak` int NOT NULL,
  `current_streak` int NOT NULL,
  `last_learning_date` date DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_streaks_student` (`student_id`),
  KEY `idx_daily_streaks_student_id` (`student_id`),
  CONSTRAINT `FKs76gc3on5gbgotqkw7si0s2ha` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`),
  CONSTRAINT `chk_daily_streaks_best_not_less_current` CHECK ((`best_streak` >= `current_streak`)),
  CONSTRAINT `chk_daily_streaks_non_negative` CHECK (((`current_streak` >= 0) and (`best_streak` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `drag_drop_activities` (
  `hsk_level` int DEFAULT NULL,
  `xp_reward` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content_status` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') NOT NULL,
  `learning_stage` enum('GUIDED','INDEPENDENT','SCAFFOLDED') DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_drag_drop_activities_status` (`content_status`),
  KEY `idx_drag_drop_activities_hsk_level` (`hsk_level`),
  KEY `idx_drag_drop_activities_created_by_user_id` (`created_by_user_id`),
  KEY `idx_drag_drop_activities_status_updated` (`content_status`,`updated_at`),
  CONSTRAINT `FKei5kgr46vhjn8qfk9a6d7a5yl` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_drag_drop_activities_hsk_level_range` CHECK (((`hsk_level` is null) or (`hsk_level` between 1 and 6))),
  CONSTRAINT `chk_drag_drop_activities_xp_reward_range` CHECK ((`xp_reward` between 0 and 1000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `drag_drop_items` (
  `sort_order` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `drag_drop_activity_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `target_zone` varchar(200) NOT NULL,
  `drag_text` varchar(500) NOT NULL,
  `pinyin` varchar(255) DEFAULT NULL,
  `distractors` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_drag_drop_items_activity_id` (`drag_drop_activity_id`),
  CONSTRAINT `FKa1elfd3nl4s21kyps00ur44sn` FOREIGN KEY (`drag_drop_activity_id`) REFERENCES `drag_drop_activities` (`id`),
  CONSTRAINT `chk_drag_drop_items_sort_order_range` CHECK ((`sort_order` between 0 and 10000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `email_change_tokens` (
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `new_email` varchar(150) NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_change_tokens_token_hash` (`token_hash`),
  KEY `idx_email_change_tokens_user_id` (`user_id`),
  CONSTRAINT `fk_email_change_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `email_confirmation_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `email` varchar(150) NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_confirmation_tokens_hash` (`token_hash`),
  KEY `idx_email_confirmation_tokens_user` (`user_id`,`created_at`),
  CONSTRAINT `fk_email_confirmation_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flashcard_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `flashcard_id` bigint NOT NULL,
  `sort_order` int NOT NULL,
  `hanzi` varchar(100) NOT NULL,
  `pinyin` varchar(200) DEFAULT NULL,
  `meaning` varchar(200) NOT NULL,
  `example` varchar(200) DEFAULT NULL,
  `example_meaning` varchar(300) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_flashcard_items_flashcard` (`flashcard_id`,`sort_order`),
  CONSTRAINT `fk_flashcard_items_flashcard` FOREIGN KEY (`flashcard_id`) REFERENCES `flashcards` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flashcards` (
  `hsk_level` int DEFAULT NULL,
  `xp_reward` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(200) DEFAULT NULL,
  `content_status` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') NOT NULL,
  `learning_stage` enum('GUIDED','INDEPENDENT','SCAFFOLDED') DEFAULT NULL,
  `lesson_id` bigint DEFAULT NULL,
  `lesson_position` int DEFAULT NULL,
  `revision_source_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_flashcards_status` (`content_status`),
  KEY `idx_flashcards_hsk_level` (`hsk_level`),
  KEY `idx_flashcards_created_by_user_id` (`created_by_user_id`),
  KEY `idx_flashcards_status_updated` (`content_status`,`updated_at`),
  KEY `fk_flashcards_revision_source` (`revision_source_id`),
  KEY `idx_flashcards_lesson` (`lesson_id`),
  CONSTRAINT `fk_flashcards_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`),
  CONSTRAINT `fk_flashcards_revision_source` FOREIGN KEY (`revision_source_id`) REFERENCES `flashcards` (`id`),
  CONSTRAINT `FKmss52266jmxyysm5uijhf63ie` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_flashcards_hsk_level_range` CHECK (((`hsk_level` is null) or (`hsk_level` between 1 and 6))),
  CONSTRAINT `chk_flashcards_xp_reward_range` CHECK ((`xp_reward` between 0 and 1000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learn_lesson_completions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `student_id` bigint NOT NULL,
  `lesson_id` bigint NOT NULL,
  `best_accuracy` int NOT NULL,
  `attempts` int NOT NULL,
  `first_completed_at` datetime(6) NOT NULL,
  `last_completed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learn_lesson_completions_student_lesson` (`student_id`,`lesson_id`),
  KEY `idx_learn_lesson_completions_lesson` (`lesson_id`),
  CONSTRAINT `fk_learn_lesson_completions_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `learn_lessons` (`id`),
  CONSTRAINT `fk_learn_lesson_completions_student` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learn_lessons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `unit_id` bigint NOT NULL,
  `position` int NOT NULL,
  `title` varchar(120) NOT NULL,
  `lesson_kind` enum('BONUS','CHECKPOINT','LESSON','REVIEW') NOT NULL,
  `minutes` int NOT NULL,
  `xp_reward` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learn_lessons_unit_position` (`unit_id`,`position`),
  CONSTRAINT `fk_learn_lessons_unit` FOREIGN KEY (`unit_id`) REFERENCES `learn_units` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learn_questions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `lesson_id` bigint NOT NULL,
  `position` int NOT NULL,
  `question_kind` enum('HANZI','LISTEN_BUILD','LISTEN_CHOOSE','MEANING','PINYIN','TRANSLATE_BUILD') NOT NULL,
  `hanzi` varchar(200) DEFAULT NULL,
  `pinyin` varchar(300) DEFAULT NULL,
  `meaning` varchar(300) DEFAULT NULL,
  `audio_text` varchar(200) DEFAULT NULL,
  `choices` text NOT NULL,
  `answer` varchar(300) NOT NULL,
  `explanation` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_learn_questions_lesson` (`lesson_id`,`position`),
  CONSTRAINT `fk_learn_questions_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `learn_lessons` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learn_unit_phrases` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `unit_id` bigint NOT NULL,
  `position` int NOT NULL,
  `hanzi` varchar(100) NOT NULL,
  `pinyin` varchar(200) NOT NULL,
  `meaning` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_learn_unit_phrases_unit` (`unit_id`,`position`),
  CONSTRAINT `fk_learn_unit_phrases_unit` FOREIGN KEY (`unit_id`) REFERENCES `learn_units` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learn_units` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `position` int NOT NULL,
  `path_level` int NOT NULL,
  `title` varchar(120) NOT NULL,
  `title_zh` varchar(40) NOT NULL,
  `hsk_level` int NOT NULL,
  `icon_key` varchar(40) NOT NULL,
  `publish_status` enum('DRAFT','PUBLISHED') NOT NULL,
  `guidebook_tips` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learn_units_position` (`position`),
  KEY `idx_learn_units_status` (`publish_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learning_categories` (
  `sort_order` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(120) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_learning_categories_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learning_mistakes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `student_id` bigint NOT NULL,
  `mistake_source` enum('LEARN','PRACTICE') NOT NULL,
  `item_ref` varchar(120) NOT NULL,
  `source_title` varchar(200) NOT NULL,
  `answer_kind` enum('BUILD','CHOICE') NOT NULL,
  `question_kind` enum('HANZI','LISTEN_BUILD','LISTEN_CHOOSE','MEANING','PINYIN','TRANSLATE_BUILD') DEFAULT NULL,
  `prompt` varchar(500) DEFAULT NULL,
  `hanzi` varchar(200) DEFAULT NULL,
  `pinyin` varchar(300) DEFAULT NULL,
  `meaning` varchar(300) DEFAULT NULL,
  `audio_text` varchar(200) DEFAULT NULL,
  `choices` text NOT NULL,
  `answer` varchar(300) NOT NULL,
  `explanation` varchar(500) DEFAULT NULL,
  `your_answer` varchar(300) DEFAULT NULL,
  `wrong_count` int NOT NULL,
  `last_wrong_at` datetime(6) NOT NULL,
  `cleared_at` datetime(6) DEFAULT NULL,
  `sure_wrong` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learning_mistakes_student_item` (`student_id`,`item_ref`),
  KEY `idx_learning_mistakes_student_waiting` (`student_id`,`cleared_at`,`last_wrong_at`),
  CONSTRAINT `fk_learning_mistakes_student` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learning_modules` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content_status` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_learning_modules_teacher_id` (`teacher_id`),
  KEY `idx_learning_modules_status` (`content_status`),
  KEY `idx_learning_modules_status_created_at` (`content_status`,`created_at`),
  KEY `idx_learning_modules_status_updated` (`content_status`,`updated_at`),
  CONSTRAINT `FKngka0ebpyqfu9deb7mflm7kms` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learning_plan_steps` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `plan_id` bigint NOT NULL,
  `position` int NOT NULL,
  `step_kind` enum('CLASS','LEARN_UNIT') NOT NULL,
  `ref_id` bigint NOT NULL,
  `title` varchar(200) NOT NULL,
  `reason` varchar(300) DEFAULT NULL,
  `goal_picks` varchar(400) NOT NULL,
  `quick_review` bit(1) NOT NULL,
  `week_from` int NOT NULL,
  `week_to` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learning_plan_steps_plan_position` (`plan_id`,`position`),
  CONSTRAINT `fk_learning_plan_steps_plan` FOREIGN KEY (`plan_id`) REFERENCES `learning_plans` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learning_plans` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `student_id` bigint NOT NULL,
  `version` int NOT NULL,
  `active` bit(1) NOT NULL,
  `plan_reason` enum('EVERYDAY','JUST_FOR_ME','OTHER','STUDY','TRAVEL','WORK') NOT NULL,
  `reason_text` varchar(120) DEFAULT NULL,
  `goal_picks` varchar(400) NOT NULL,
  `goal_text` varchar(200) DEFAULT NULL,
  `deadline` date DEFAULT NULL,
  `plan_pace` enum('INTENSE','LIGHT','REGULAR','SERIOUS') NOT NULL,
  `focus_skills` varchar(60) NOT NULL,
  `self_level` enum('A_LITTLE','NEW_TO_MANDARIN','NOT_SURE','THE_BASICS') NOT NULL,
  `placed_level` enum('BEGINNER_1','BEGINNER_2','NEW_TO_MANDARIN') NOT NULL,
  `head_start` bit(1) NOT NULL,
  `known_skills` varchar(200) NOT NULL,
  `new_skills` varchar(200) NOT NULL,
  `level_checked_at` datetime(6) DEFAULT NULL,
  `level_check_answered` int NOT NULL,
  `target_level` enum('BEGINNER_1','BEGINNER_2','NEW_TO_MANDARIN') NOT NULL,
  `ai_assisted` bit(1) NOT NULL,
  `goal_title` varchar(80) DEFAULT NULL,
  `goal_summary` varchar(500) DEFAULT NULL,
  `work_on` text,
  `outcomes` text,
  `milestone_week` int DEFAULT NULL,
  `milestone_text` varchar(400) DEFAULT NULL,
  `starts_on` date NOT NULL,
  `total_weeks` int NOT NULL,
  `content_weeks` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learning_plans_student_version` (`student_id`,`version`),
  KEY `idx_learning_plans_student_active` (`student_id`,`active`),
  CONSTRAINT `fk_learning_plans_student` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `lesson_key_phrases` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `lesson_id` bigint NOT NULL,
  `chinese` varchar(100) NOT NULL,
  `pinyin` varchar(200) NOT NULL,
  `meaning` varchar(300) NOT NULL,
  `sort_order` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lesson_key_phrases_lesson` (`lesson_id`,`sort_order`),
  CONSTRAINT `fk_lesson_key_phrases_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `lesson_progress` (
  `completed` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_accessed_at` datetime(6) DEFAULT NULL,
  `lesson_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lesson_progress_student_lesson` (`student_id`,`lesson_id`),
  KEY `idx_lesson_progress_student_id` (`student_id`),
  KEY `idx_lesson_progress_lesson_id` (`lesson_id`),
  KEY `idx_lesson_progress_student_updated` (`student_id`,`updated_at`),
  CONSTRAINT `FKq75k0u8fs6niodnxid01mpy57` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`),
  CONSTRAINT `FKqwr70bkn0j6gok1y4op9jns8y` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `lessons` (
  `estimated_duration_minutes` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `module_id` bigint DEFAULT NULL,
  `teacher_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(200) NOT NULL,
  `rejection_reason` varchar(1000) DEFAULT NULL,
  `summary` varchar(1000) DEFAULT NULL,
  `content_status` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') NOT NULL,
  `status_before_archive` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') DEFAULT NULL,
  `unit_position` int NOT NULL DEFAULT '0',
  `submitted_at` datetime(6) DEFAULT NULL,
  `revision_of_lesson_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lessons_module_id` (`module_id`),
  KEY `idx_lessons_teacher_id` (`teacher_id`),
  KEY `idx_lessons_status` (`content_status`),
  KEY `idx_lessons_status_created_at` (`content_status`,`created_at`),
  KEY `idx_lessons_status_updated` (`content_status`,`updated_at`),
  KEY `idx_lessons_revision_of` (`revision_of_lesson_id`),
  KEY `idx_lessons_module_position` (`module_id`,`unit_position`),
  CONSTRAINT `FK85pfpipvgxct5yalal134dq1w` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profiles` (`id`),
  CONSTRAINT `fk_lessons_revision_of` FOREIGN KEY (`revision_of_lesson_id`) REFERENCES `lessons` (`id`),
  CONSTRAINT `FKbioqstqvvgu46entuqgajuklb` FOREIGN KEY (`module_id`) REFERENCES `learning_modules` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `level_check_options` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `question_id` bigint NOT NULL,
  `position` int NOT NULL,
  `option_text` varchar(200) NOT NULL,
  `correct` bit(1) NOT NULL,
  `explanation` varchar(300) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_level_check_options_question` (`question_id`,`position`),
  CONSTRAINT `fk_level_check_options_question` FOREIGN KEY (`question_id`) REFERENCES `level_check_questions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `level_check_questions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `prompt` varchar(300) NOT NULL,
  `hanzi` varchar(100) DEFAULT NULL,
  `pinyin` varchar(200) DEFAULT NULL,
  `audio_text` varchar(200) DEFAULT NULL,
  `skill` enum('CHARACTERS','GRAMMAR','IDIOMS','LISTENING','NUMBERS','PINYIN','SENTENCES','VOCABULARY') NOT NULL,
  `difficulty` enum('EASY','HARD','MEDIUM') NOT NULL,
  `mandarin_level` enum('BEGINNER_1','BEGINNER_2') NOT NULL,
  `publish_status` enum('DRAFT','PUBLISHED') NOT NULL,
  `answered_count` int NOT NULL,
  `correct_count` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_level_check_questions_pick` (`publish_status`,`mandarin_level`,`difficulty`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `listening_exercises` (
  `hsk_level` int DEFAULT NULL,
  `xp_reward` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content_status` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') NOT NULL,
  `question_payload` longtext,
  `learning_stage` enum('GUIDED','INDEPENDENT','SCAFFOLDED') DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_listening_exercises_status` (`content_status`),
  KEY `idx_listening_exercises_hsk_level` (`hsk_level`),
  KEY `idx_listening_exercises_created_by_user_id` (`created_by_user_id`),
  KEY `idx_listening_exercises_status_updated` (`content_status`,`updated_at`),
  CONSTRAINT `FKlo1t6skrjponktm3nck9suoww` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_listening_exercises_hsk_level_range` CHECK (((`hsk_level` is null) or (`hsk_level` between 1 and 6))),
  CONSTRAINT `chk_listening_exercises_xp_reward_range` CHECK ((`xp_reward` between 0 and 1000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `live_classes` (
  `course_package_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scheduled_end_at` datetime(6) DEFAULT NULL,
  `scheduled_start_at` datetime(6) DEFAULT NULL,
  `teacher_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(200) NOT NULL,
  `time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Singapore',
  `meeting_link` varchar(500) DEFAULT NULL,
  `live_class_status` enum('CANCELLED','RESCHEDULED','SCHEDULED') NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `recording_url` varchar(500) DEFAULT NULL,
  `rescheduled_from_start_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_live_classes_teacher_id` (`teacher_id`),
  KEY `idx_live_classes_status` (`live_class_status`),
  KEY `idx_live_classes_scheduled_start_at` (`scheduled_start_at`),
  KEY `FKqg3u5ofl9ucg96mshrjh1jdas` (`course_package_id`),
  KEY `idx_live_classes_teacher_status_start` (`teacher_id`,`live_class_status`,`scheduled_start_at`),
  CONSTRAINT `FKqg3u5ofl9ucg96mshrjh1jdas` FOREIGN KEY (`course_package_id`) REFERENCES `course_packages` (`id`),
  CONSTRAINT `FKt3xuqfrdocxy59x5l4nrsvmb8` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profiles` (`id`),
  CONSTRAINT `chk_live_classes_linked_content` CHECK ((`course_package_id` is not null)),
  CONSTRAINT `chk_live_classes_time_order` CHECK (((`scheduled_start_at` is null) or (`scheduled_end_at` is null) or (`scheduled_end_at` > `scheduled_start_at`))),
  CONSTRAINT `chk_live_classes_time_zone_not_blank` CHECK ((`time_zone` <> _utf8mb4''))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `matching_game_items` (
  `sort_order` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `matching_game_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `left_text` varchar(500) NOT NULL,
  `right_text` varchar(500) NOT NULL,
  `pinyin` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_matching_game_items_game_id` (`matching_game_id`),
  CONSTRAINT `FK65586qwsgs79q383doebg3p87` FOREIGN KEY (`matching_game_id`) REFERENCES `matching_games` (`id`),
  CONSTRAINT `chk_matching_game_items_sort_order_range` CHECK ((`sort_order` between 0 and 10000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `matching_games` (
  `hsk_level` int DEFAULT NULL,
  `xp_reward` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content_status` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') NOT NULL,
  `learning_stage` enum('GUIDED','INDEPENDENT','SCAFFOLDED') DEFAULT NULL,
  `lesson_id` bigint DEFAULT NULL,
  `lesson_position` int DEFAULT NULL,
  `revision_source_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_matching_games_status` (`content_status`),
  KEY `idx_matching_games_hsk_level` (`hsk_level`),
  KEY `idx_matching_games_created_by_user_id` (`created_by_user_id`),
  KEY `idx_matching_games_status_updated` (`content_status`,`updated_at`),
  KEY `fk_matching_games_revision_source` (`revision_source_id`),
  KEY `idx_matching_games_lesson` (`lesson_id`),
  CONSTRAINT `FK9qopobo92p8ga0io07cq6xbcr` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_matching_games_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`),
  CONSTRAINT `fk_matching_games_revision_source` FOREIGN KEY (`revision_source_id`) REFERENCES `matching_games` (`id`),
  CONSTRAINT `chk_matching_games_hsk_level_range` CHECK (((`hsk_level` is null) or (`hsk_level` between 1 and 6))),
  CONSTRAINT `chk_matching_games_xp_reward_range` CHECK ((`xp_reward` between 0 and 1000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notifications` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `read_at` datetime(6) DEFAULT NULL,
  `target_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `target_type` varchar(50) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `target_path` varchar(255) DEFAULT NULL,
  `message` longtext NOT NULL,
  `notification_status` enum('READ','UNREAD') NOT NULL,
  `notification_type` enum('ADMIN_TASK','AI_REPORT_REVIEW','CLASS_ENROLMENT','CLASS_QUESTION','CONTENT_REVIEW','LIVE_CLASS_REMINDER','MODERATION_ACTION','SUPPORT_REQUEST','SYSTEM','TEACHER_APPROVAL','TEACHER_FEEDBACK') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_user_id` (`user_id`),
  KEY `idx_notifications_status` (`notification_status`),
  KEY `idx_notifications_type` (`notification_type`),
  KEY `idx_notifications_user_created_id` (`user_id`,`created_at`,`id`),
  KEY `idx_notifications_user_status_created_id` (`user_id`,`notification_status`,`created_at`,`id`),
  CONSTRAINT `FK9y21adhxn0ayjhfocscqox7bh` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_notifications_read_state` CHECK ((((`notification_status` = _utf8mb4'UNREAD') and (`read_at` is null)) or ((`notification_status` = _utf8mb4'READ') and (`read_at` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `package_items` (
  `sort_order` int NOT NULL,
  `course_package_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_package_items_package_id` (`course_package_id`),
  CONSTRAINT `FK3y1vt7pu35xvx6ip4hsopcc6d` FOREIGN KEY (`course_package_id`) REFERENCES `course_packages` (`id`),
  CONSTRAINT `chk_package_items_sort_order_non_negative` CHECK ((`sort_order` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `password_reset_tokens` (
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `token` varchar(128) NOT NULL,
  `active_token_user_id` bigint GENERATED ALWAYS AS ((case when (`used_at` is null) then `user_id` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_password_reset_tokens_token` (`token`),
  UNIQUE KEY `uk_password_reset_tokens_active_user` (`active_token_user_id`),
  KEY `idx_password_reset_tokens_user_id` (`user_id`),
  CONSTRAINT `FKk3ndxg5xp6v7wd4gjyusp15gq` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_password_reset_tokens_used_after_created` CHECK (((`used_at` is null) or (`used_at` >= `created_at`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `points_transactions` (
  `points_delta` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_id` bigint DEFAULT NULL,
  `student_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `source_type` varchar(100) DEFAULT NULL,
  `reason` varchar(255) NOT NULL,
  `source_key` varchar(220) GENERATED ALWAYS AS ((case when ((`source_type` is not null) and (`source_id` is not null)) then concat(`source_type`,_utf8mb4'#',`source_id`) else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_transactions_student_source` (`student_id`,`source_key`),
  KEY `idx_points_transactions_student_id` (`student_id`),
  KEY `idx_points_transactions_created_at` (`created_at`),
  KEY `idx_points_transactions_student_created` (`student_id`,`created_at`),
  CONSTRAINT `FK1m9uhs8ghtik7hl37hllyp3bn` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`),
  CONSTRAINT `chk_points_transactions_source_pair` CHECK ((((`source_type` is null) and (`source_id` is null)) or ((`source_type` is not null) and (`source_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `practice_answers` (
  `correct` bit(1) NOT NULL,
  `hint_steps` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `item_ref` varchar(120) NOT NULL,
  `confidence` enum('GUESS','SURE') DEFAULT NULL,
  `practice_type` enum('DRAG_DROP','FLASHCARDS','LISTENING','MATCHING','QUIZZES','TRACING','TYPING') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_practice_answers_student_created` (`student_id`,`created_at`),
  CONSTRAINT `fk_practice_answers_student` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`),
  CONSTRAINT `chk_practice_answers_hint_steps` CHECK ((`hint_steps` between 0 and 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `practice_completions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `student_id` bigint NOT NULL,
  `practice_type` enum('DRAG_DROP','FLASHCARDS','LISTENING','MATCHING','QUIZZES','TRACING','TYPING') NOT NULL,
  `activity_id` bigint NOT NULL,
  `correct_count` int NOT NULL,
  `item_count` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_practice_completions_student_created` (`student_id`,`created_at`),
  KEY `idx_practice_completions_set` (`practice_type`,`activity_id`),
  CONSTRAINT `fk_practice_completions_student` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `practice_quiz_questions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `practice_quiz_id` bigint NOT NULL,
  `sort_order` int NOT NULL,
  `prompt` varchar(300) NOT NULL,
  `hanzi` varchar(100) DEFAULT NULL,
  `pinyin` varchar(200) DEFAULT NULL,
  `options` text NOT NULL,
  `correct_option_index` int NOT NULL,
  `explanation` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_practice_quiz_questions_quiz` (`practice_quiz_id`,`sort_order`),
  CONSTRAINT `fk_practice_quiz_questions_quiz` FOREIGN KEY (`practice_quiz_id`) REFERENCES `practice_quizzes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `practice_quizzes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content_status` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `learning_stage` enum('GUIDED','INDEPENDENT','SCAFFOLDED') DEFAULT NULL,
  `hsk_level` int DEFAULT NULL,
  `xp_reward` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_practice_quizzes_status_updated` (`content_status`,`updated_at`),
  KEY `idx_practice_quizzes_created_by_user_id` (`created_by_user_id`),
  CONSTRAINT `fk_practice_quizzes_created_by` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `quiz_attempt_answers` (
  `correct` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL,
  `quiz_attempt_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `answer_text` longtext,
  `selected_option_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_attempt_answers_attempt_question` (`quiz_attempt_id`,`question_id`),
  KEY `idx_quiz_attempt_answers_attempt_id` (`quiz_attempt_id`),
  KEY `idx_quiz_attempt_answers_question_id` (`question_id`),
  KEY `idx_quiz_attempt_answers_selected_option_id` (`selected_option_id`),
  CONSTRAINT `FK59s9075rs7i5520p8awj6ri1` FOREIGN KEY (`question_id`) REFERENCES `quiz_questions` (`id`),
  CONSTRAINT `fk_quiz_attempt_answers_selected_option` FOREIGN KEY (`selected_option_id`) REFERENCES `quiz_options` (`id`),
  CONSTRAINT `FKm8571omdvyu3q35r8ansqqqi2` FOREIGN KEY (`quiz_attempt_id`) REFERENCES `quiz_attempts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `quiz_attempts` (
  `max_score` int DEFAULT NULL,
  `score` int DEFAULT NULL,
  `submitted` bit(1) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quiz_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_quiz_attempts_quiz_id` (`quiz_id`),
  KEY `idx_quiz_attempts_student_id` (`student_id`),
  KEY `idx_quiz_attempts_completed_at` (`completed_at`),
  CONSTRAINT `FKfwipvfipnnwsoacoyv5k7fbxc` FOREIGN KEY (`quiz_id`) REFERENCES `quizzes` (`id`),
  CONSTRAINT `FKo1dvlihe37kkv2ugmj8l4oica` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`),
  CONSTRAINT `chk_quiz_attempts_completion_state` CHECK ((((`submitted` = 0x00) and (`completed_at` is null)) or ((`submitted` = 0x01) and (`completed_at` is not null)))),
  CONSTRAINT `chk_quiz_attempts_score_bounds` CHECK ((((`score` is null) and (`max_score` is null)) or ((`score` is not null) and (`max_score` is not null) and (`score` >= 0) and (`max_score` >= 0) and (`score` <= `max_score`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `quiz_options` (
  `correct_option` bit(1) NOT NULL,
  `sort_order` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `option_text` longtext NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_quiz_options_question_id` (`question_id`),
  CONSTRAINT `FKhkuvmd7qk1lmtq3hy9htpinkr` FOREIGN KEY (`question_id`) REFERENCES `quiz_questions` (`id`),
  CONSTRAINT `chk_quiz_options_sort_order_non_negative` CHECK ((`sort_order` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `quiz_questions` (
  `points` int NOT NULL,
  `sort_order` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quiz_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `question_type` varchar(50) NOT NULL,
  `correct_answer_text` varchar(1000) DEFAULT NULL,
  `explanation` varchar(2000) DEFAULT NULL,
  `question_text` longtext NOT NULL,
  `hint` varchar(1000) DEFAULT NULL,
  `audio_url` varchar(500) DEFAULT NULL,
  `image_url` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_quiz_questions_quiz_id` (`quiz_id`),
  CONSTRAINT `FKanfmgf6ksbdnv7ojb0pfve54q` FOREIGN KEY (`quiz_id`) REFERENCES `quizzes` (`id`),
  CONSTRAINT `chk_quiz_questions_points_positive` CHECK ((`points` > 0)),
  CONSTRAINT `chk_quiz_questions_sort_order_non_negative` CHECK ((`sort_order` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `quizzes` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lesson_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content_status` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') NOT NULL,
  `learning_stage` enum('GUIDED','INDEPENDENT','SCAFFOLDED') DEFAULT NULL,
  `revision_source_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_quizzes_lesson_id` (`lesson_id`),
  KEY `idx_quizzes_status` (`content_status`),
  KEY `FKh8m8lpcfawschgw6url6b13yy` (`teacher_id`),
  KEY `idx_quizzes_status_updated` (`content_status`,`updated_at`),
  KEY `fk_quizzes_revision_source` (`revision_source_id`),
  CONSTRAINT `fk_quizzes_revision_source` FOREIGN KEY (`revision_source_id`) REFERENCES `quizzes` (`id`),
  CONSTRAINT `FKbdv8uggpsin6pnkx0d80ryqey` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`),
  CONSTRAINT `FKh8m8lpcfawschgw6url6b13yy` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recorded_lesson_videos` (
  `active` bit(1) NOT NULL,
  `duration_seconds` int DEFAULT NULL,
  `sort_order` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lesson_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `uploaded_file_id` bigint DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `external_video_url` varchar(500) DEFAULT NULL,
  `source_type` enum('EXTERNAL_URL','UPLOADED_FILE') NOT NULL,
  `revision_source_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_recorded_videos_lesson_id` (`lesson_id`),
  KEY `idx_recorded_videos_uploaded_file_id` (`uploaded_file_id`),
  KEY `idx_recorded_videos_active` (`active`),
  KEY `idx_recorded_videos_lesson_active_sort` (`lesson_id`,`active`,`sort_order`),
  KEY `fk_recorded_videos_revision_source` (`revision_source_id`),
  CONSTRAINT `fk_recorded_videos_revision_source` FOREIGN KEY (`revision_source_id`) REFERENCES `recorded_lesson_videos` (`id`),
  CONSTRAINT `FKcqo8auork0arjxayvfqxhbsx0` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`),
  CONSTRAINT `FKkt22yac011o76sonbnlcoounr` FOREIGN KEY (`uploaded_file_id`) REFERENCES `uploaded_files` (`id`),
  CONSTRAINT `chk_recorded_videos_duration_non_negative` CHECK (((`duration_seconds` is null) or (`duration_seconds` >= 0))),
  CONSTRAINT `chk_recorded_videos_sort_order_range` CHECK ((`sort_order` between 0 and 10000)),
  CONSTRAINT `chk_recorded_videos_source_reference` CHECK ((((`source_type` = _utf8mb4'UPLOADED_FILE') and (`uploaded_file_id` is not null) and (`external_video_url` is null)) or ((`source_type` = _utf8mb4'EXTERNAL_URL') and (`uploaded_file_id` is null) and (`external_video_url` is not null) and (char_length(trim(`external_video_url`)) > 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `refresh_tokens` (
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `revoked_at` datetime(6) DEFAULT NULL,
  `rotated_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `created_by_ip` varchar(64) DEFAULT NULL,
  `family_id` varchar(36) NOT NULL,
  `replaced_by_token_hash` varchar(64) DEFAULT NULL,
  `token_hash` varchar(64) NOT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `persistent` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_tokens_token_hash` (`token_hash`),
  KEY `idx_refresh_tokens_user_id` (`user_id`),
  KEY `idx_refresh_tokens_family_id` (`family_id`),
  KEY `idx_refresh_tokens_expires_at` (`expires_at`),
  CONSTRAINT `fk_refresh_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_refresh_tokens_expiry_after_created` CHECK ((`expires_at` > `created_at`)),
  CONSTRAINT `chk_refresh_tokens_replacement_on_rotation` CHECK ((((`rotated_at` is null) and (`replaced_by_token_hash` is null)) or ((`rotated_at` is not null) and (`replaced_by_token_hash` is not null)))),
  CONSTRAINT `chk_refresh_tokens_revoked_after_created` CHECK (((`revoked_at` is null) or (`revoked_at` >= `created_at`))),
  CONSTRAINT `chk_refresh_tokens_rotated_after_created` CHECK (((`rotated_at` is null) or (`rotated_at` >= `created_at`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `roles` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `name` enum('ADMIN','STUDENT','TEACHER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_roles_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_badges` (
  `awarded_at` datetime(6) NOT NULL,
  `badge_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_badges_student_badge` (`student_id`,`badge_id`),
  KEY `idx_student_badges_student_id` (`student_id`),
  KEY `idx_student_badges_badge_id` (`badge_id`),
  KEY `idx_student_badges_student_awarded` (`student_id`,`awarded_at`),
  CONSTRAINT `FKdogjuytddp97f0p6ivt3r8ufp` FOREIGN KEY (`badge_id`) REFERENCES `badges` (`id`),
  CONSTRAINT `FKfgx9txqmkltpu2d9y9xgx0pr4` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_profiles` (
  `total_points` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `school_name` varchar(150) DEFAULT NULL,
  `daily_goal_xp` int NOT NULL DEFAULT '20',
  `plan_setup_state` enum('COMPLETED','NOT_STARTED','SKIPPED') NOT NULL DEFAULT 'NOT_STARTED',
  `hint_mode` enum('AUTOMATIC','OFF','ON_REQUEST') NOT NULL DEFAULT 'AUTOMATIC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_student_profiles_user_id` (`user_id`),
  CONSTRAINT `FK32koy3tgqtaujxhfsn0b9pel2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_student_profiles_daily_goal_xp` CHECK ((`daily_goal_xp` in (10,20,30)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `support_messages` (
  `attachment_file_id` bigint DEFAULT NULL,
  `author_user_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `from_support` bit(1) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `body` varchar(4000) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_support_messages_request_created` (`request_id`,`created_at`),
  KEY `fk_support_messages_author` (`author_user_id`),
  KEY `fk_support_messages_attachment` (`attachment_file_id`),
  CONSTRAINT `fk_support_messages_attachment` FOREIGN KEY (`attachment_file_id`) REFERENCES `uploaded_files` (`id`),
  CONSTRAINT `fk_support_messages_author` FOREIGN KEY (`author_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_support_messages_request` FOREIGN KEY (`request_id`) REFERENCES `support_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `support_request_numbers` (
  `id` int NOT NULL,
  `next_number` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `support_request_numbers` (`id`, `next_number`) VALUES (1,1000);

CREATE TABLE `support_requests` (
  `closes_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_support_reply_at` datetime(6) DEFAULT NULL,
  `request_number` int NOT NULL,
  `requester_user_id` bigint DEFAULT NULL,
  `solved_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `requester_email` varchar(150) NOT NULL,
  `subject` varchar(150) NOT NULL,
  `requester_role` enum('STUDENT','TEACHER','VISITOR') NOT NULL,
  `request_topic` enum('ACCOUNT','AI_HELPER','CLASSES','LEARNING','OTHER','SAFETY','VERIFICATION') NOT NULL,
  `request_status` enum('ANSWERED','OPEN','SOLVED') NOT NULL,
  `closed_by` enum('AUTO','REQUESTER','SUPPORT') DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_support_requests_number` (`request_number`),
  KEY `idx_support_requests_requester_created` (`requester_user_id`,`created_at`),
  KEY `idx_support_requests_status_created` (`request_status`,`created_at`),
  KEY `idx_support_requests_status_closes` (`request_status`,`closes_at`),
  CONSTRAINT `fk_support_requests_requester` FOREIGN KEY (`requester_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `system_settings` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `setting_key` varchar(100) NOT NULL,
  `setting_value` varchar(2000) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_system_settings_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `teacher_feedback` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lesson_id` bigint DEFAULT NULL,
  `quiz_attempt_id` bigint DEFAULT NULL,
  `student_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `feedback_message` longtext NOT NULL,
  `course_package_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_teacher_feedback_teacher_id` (`teacher_id`),
  KEY `idx_teacher_feedback_student_id` (`student_id`),
  KEY `idx_teacher_feedback_lesson_id` (`lesson_id`),
  KEY `idx_teacher_feedback_quiz_attempt_id` (`quiz_attempt_id`),
  KEY `idx_teacher_feedback_class` (`course_package_id`),
  CONSTRAINT `FK20dqiw55c8g861irt3ss17gei` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`),
  CONSTRAINT `FK9hwlmq73lsoic18x7vl3hlinj` FOREIGN KEY (`quiz_attempt_id`) REFERENCES `quiz_attempts` (`id`),
  CONSTRAINT `fk_teacher_feedback_class` FOREIGN KEY (`course_package_id`) REFERENCES `course_packages` (`id`),
  CONSTRAINT `FKfncou1297ai4q4x7a3w7pk4m1` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`),
  CONSTRAINT `FKo45jtk4pyheie3uoeig54rnar` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `teacher_profiles` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `rejection_reason` varchar(1000) DEFAULT NULL,
  `bio` varchar(2000) DEFAULT NULL,
  `teacher_status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  `verification_submitted_at` datetime(6) DEFAULT NULL,
  `standards_accepted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_teacher_profiles_user_id` (`user_id`),
  KEY `idx_teacher_profiles_status` (`teacher_status`),
  KEY `idx_teacher_profiles_status_created` (`teacher_status`,`created_at`),
  KEY `idx_teacher_profiles_status_submitted` (`teacher_status`,`verification_submitted_at`),
  CONSTRAINT `FK38hlnhfrimybow0wdfwnsfhew` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `teacher_verification_documents` (
  `current_version` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `submitted_at` datetime(6) DEFAULT NULL,
  `teacher_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `uploaded_file_id` bigint NOT NULL,
  `document_type` varchar(100) NOT NULL,
  `current_document_key` varchar(160) GENERATED ALWAYS AS ((case when (`current_version` = 1) then concat(`teacher_id`,_utf8mb4'#',`document_type`) else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_verification_documents_current_type` (`current_document_key`),
  KEY `idx_teacher_verification_documents_teacher_id` (`teacher_id`),
  KEY `idx_teacher_verification_documents_uploaded_file_id` (`uploaded_file_id`),
  KEY `idx_teacher_verification_documents_teacher_current_submitted` (`teacher_id`,`current_version`,`submitted_at`,`id`),
  KEY `idx_teacher_verification_documents_latest` (`teacher_id`,`submitted_at`,`id`),
  CONSTRAINT `FKcv0uukr5souwo3d23ml3xwtpl` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profiles` (`id`),
  CONSTRAINT `FKs9i8og0090d99nx3qat7k2p75` FOREIGN KEY (`uploaded_file_id`) REFERENCES `uploaded_files` (`id`),
  CONSTRAINT `chk_teacher_verification_documents_submitted_at` CHECK ((`submitted_at` is not null)),
  CONSTRAINT `chk_teacher_verification_documents_type` CHECK ((`document_type` in (_utf8mb4'CERTIFICATE',_utf8mb4'QUALIFICATION',_utf8mb4'IDENTITY')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `teacher_verification_reviews` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewer_user_id` bigint DEFAULT NULL,
  `teacher_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `review_note` varchar(1000) DEFAULT NULL,
  `decision_status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_teacher_verification_reviews_teacher_id` (`teacher_id`),
  KEY `idx_teacher_verification_reviews_reviewer_user_id` (`reviewer_user_id`),
  KEY `idx_teacher_verification_reviews_latest` (`teacher_id`,`reviewed_at`,`id`),
  CONSTRAINT `FKbop7rx4hlfrk7rc9qhs1xwd0r` FOREIGN KEY (`reviewer_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKecrytd9lvfoe64ksxxgvrgk0n` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profiles` (`id`),
  CONSTRAINT `chk_teacher_verification_reviews_final_decision` CHECK ((`decision_status` in (_utf8mb4'APPROVED',_utf8mb4'REJECTED'))),
  CONSTRAINT `chk_teacher_verification_reviews_reviewed_at` CHECK ((`reviewed_at` is not null))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tracing_exercises` (
  `hsk_level` int DEFAULT NULL,
  `xp_reward` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `target_character` varchar(100) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `meaning` varchar(500) DEFAULT NULL,
  `pinyin` varchar(255) DEFAULT NULL,
  `content_status` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') NOT NULL,
  `learning_stage` enum('GUIDED','INDEPENDENT','SCAFFOLDED') DEFAULT NULL,
  `stroke_count` int DEFAULT NULL,
  `lesson_id` bigint DEFAULT NULL,
  `lesson_position` int DEFAULT NULL,
  `revision_source_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tracing_exercises_status` (`content_status`),
  KEY `idx_tracing_exercises_hsk_level` (`hsk_level`),
  KEY `idx_tracing_exercises_created_by_user_id` (`created_by_user_id`),
  KEY `idx_tracing_exercises_status_updated` (`content_status`,`updated_at`),
  KEY `fk_tracing_exercises_revision_source` (`revision_source_id`),
  KEY `idx_tracing_exercises_lesson` (`lesson_id`),
  CONSTRAINT `FK87jluuqqc6mam1kxbthcy4qvm` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_tracing_exercises_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`),
  CONSTRAINT `fk_tracing_exercises_revision_source` FOREIGN KEY (`revision_source_id`) REFERENCES `tracing_exercises` (`id`),
  CONSTRAINT `chk_tracing_exercises_hsk_level_range` CHECK (((`hsk_level` is null) or (`hsk_level` between 1 and 6))),
  CONSTRAINT `chk_tracing_exercises_xp_reward_range` CHECK ((`xp_reward` between 0 and 1000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `typing_exercises` (
  `hsk_level` int DEFAULT NULL,
  `xp_reward` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content_status` enum('ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') NOT NULL,
  `learning_stage` enum('GUIDED','INDEPENDENT','SCAFFOLDED') DEFAULT NULL,
  `word_payload` longtext,
  PRIMARY KEY (`id`),
  KEY `idx_typing_exercises_status` (`content_status`),
  KEY `idx_typing_exercises_created_by_user_id` (`created_by_user_id`),
  CONSTRAINT `fk_typing_exercises_created_by` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_typing_exercises_hsk_level_range` CHECK (((`hsk_level` is null) or (`hsk_level` between 1 and 6))),
  CONSTRAINT `chk_typing_exercises_xp_reward_range` CHECK ((`xp_reward` between 0 and 1000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `uploaded_files` (
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `file_size` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_user_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `content_type` varchar(120) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `stored_file_name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uploaded_files_stored_file_name` (`stored_file_name`),
  KEY `idx_uploaded_files_owner_user_id` (`owner_user_id`),
  KEY `idx_uploaded_files_created_at` (`created_at`),
  CONSTRAINT `FKfg64d98iapftr6cur53s2kbuv` FOREIGN KEY (`owner_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_uploaded_files_file_size_non_negative` CHECK ((`file_size` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_roles` (
  `role_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `FKh8ciramu9cc9q3qcqiv4ue8a6` (`role_id`),
  CONSTRAINT `FKh8ciramu9cc9q3qcqiv4ue8a6` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `FKhfh9dx7w3ubf1co1vdev94g3f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `preferred_language` varchar(10) DEFAULT NULL,
  `display_name` varchar(120) NOT NULL,
  `email` varchar(150) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `session_version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(6) DEFAULT NULL,
  `notify_live_session_reminders` bit(1) NOT NULL DEFAULT b'1',
  `notify_feedback` bit(1) NOT NULL DEFAULT b'1',
  `notify_content_reviews` bit(1) NOT NULL DEFAULT b'1',
  `notify_review_queues` bit(1) NOT NULL DEFAULT b'1',
  `email_verified_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_users_email` (`email`),
  KEY `idx_users_created_at` (`created_at`),
  CONSTRAINT `chk_users_session_version_non_negative` CHECK ((`session_version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `video_progress` (
  `completed` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_watched_at` datetime(6) DEFAULT NULL,
  `lesson_id` bigint NOT NULL,
  `recorded_video_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_video_progress_student_video` (`student_id`,`recorded_video_id`),
  KEY `idx_video_progress_student_id` (`student_id`),
  KEY `idx_video_progress_lesson_id` (`lesson_id`),
  KEY `idx_video_progress_recorded_video_id` (`recorded_video_id`),
  KEY `idx_video_progress_lesson_last_watched` (`lesson_id`,`last_watched_at`),
  CONSTRAINT `FK5uinmc3mesxfs52gcr3a076bf` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`),
  CONSTRAINT `FKdjbxt2h00nn3l4ckqkc0f92mo` FOREIGN KEY (`recorded_video_id`) REFERENCES `recorded_lesson_videos` (`id`),
  CONSTRAINT `FKfhbi17gox8advjbaqxqyf0b5w` FOREIGN KEY (`student_id`) REFERENCES `student_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
