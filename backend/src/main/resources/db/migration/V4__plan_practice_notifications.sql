-- Learning plan, Practice and learner notifications (2026-11-17). Applied after V3__admin_authored_classes.sql; the
-- entities match the schema below (MySqlMigrationSmokeTests).

-- 1. "Time I can give each day" is one of the four bands of daily study time of Shen & Wu (2026). The old paces were all
--    30 minutes a day or less: Light (10), Regular (15) and Serious (20) become "Under 30 min", Intense (30) becomes
--    "30–60 min". Nothing else is recomputed: older plans keep their weeks. The setup's plan is a draft until the learner
--    starts it ("Start my path"); every plan saved before V4 was started.
ALTER TABLE `learning_plans` MODIFY `plan_pace`
  enum('HOURS_1_TO_2','HOURS_2_PLUS','INTENSE','LIGHT','MINUTES_30_TO_60','REGULAR','SERIOUS','UNDER_30_MIN') NOT NULL;
UPDATE `learning_plans` SET `plan_pace` = 'UNDER_30_MIN' WHERE `plan_pace` IN ('LIGHT', 'REGULAR', 'SERIOUS');
UPDATE `learning_plans` SET `plan_pace` = 'MINUTES_30_TO_60' WHERE `plan_pace` = 'INTENSE';
ALTER TABLE `learning_plans` MODIFY `plan_pace`
  enum('HOURS_1_TO_2','HOURS_2_PLUS','MINUTES_30_TO_60','UNDER_30_MIN') NOT NULL;
ALTER TABLE `learning_plans` ADD COLUMN `confirmed` bit(1) NOT NULL DEFAULT b'1';

-- 2. Practice has no Quizzes type any more (AI Helper's Quick quiz and the class lesson quizzes stay). The quiz sets go
--    with their finished runs, logged answers, waiting mistakes and served hints; the XP learners earned with them
--    (points_transactions) and the audit entries stay.
DELETE FROM `practice_completions` WHERE `practice_type` = 'QUIZZES';
DELETE FROM `practice_answers` WHERE `practice_type` = 'QUIZZES';
DELETE FROM `learning_mistakes` WHERE `item_ref` LIKE 'QUIZZES#%';
DELETE FROM `served_hints` WHERE `item_ref` LIKE 'PRACTICE#QUIZZES#%';
DROP TABLE `practice_quiz_questions`;
DROP TABLE `practice_quizzes`;
ALTER TABLE `practice_completions` MODIFY `practice_type`
  enum('DRAG_DROP','FLASHCARDS','LISTENING','MATCHING','TRACING','TYPING') NOT NULL;
ALTER TABLE `practice_answers` MODIFY `practice_type`
  enum('DRAG_DROP','FLASHCARDS','LISTENING','MATCHING','TRACING','TYPING') NOT NULL;

-- 3. A flashcard marked "Still learning" is a mistake of its own kind (reviewed as what the word means).
ALTER TABLE `learning_mistakes` ADD COLUMN `still_learning` bit(1) NOT NULL DEFAULT b'0';

-- 4. A tracing set may name the strokes of each character ("Character 1 of 2 · 0 of 4 strokes"); the platform's own
--    word sets get theirs.
ALTER TABLE `tracing_exercises` ADD COLUMN `character_strokes` varchar(100) DEFAULT NULL;
UPDATE `tracing_exercises` SET `character_strokes` = '4,4'
WHERE `lesson_id` IS NULL AND `created_by_user_id` IS NULL AND `target_character` IN ('中文', '日月') AND `stroke_count` = 8;
UPDATE `tracing_exercises` SET `character_strokes` = '7,6'
WHERE `lesson_id` IS NULL AND `created_by_user_id` IS NULL AND `target_character` = '你好' AND `stroke_count` = 13;
UPDATE `tracing_exercises` SET `character_strokes` = '12,12'
WHERE `lesson_id` IS NULL AND `created_by_user_id` IS NULL AND `target_character` = '谢谢' AND `stroke_count` = 24;

-- 5. A Practice set's XP follows the UI rules: Flashcards and Tracing +10 at HSK 1 and +15 from HSK 2, Matching +15;
--    Listening 3, Drag & Drop 4 and Typing 2 XP an answer. The activities of class lessons pay no XP and are left alone.
UPDATE `flashcards` SET `xp_reward` = IF(`hsk_level` >= 2, 15, 10) WHERE `lesson_id` IS NULL;
UPDATE `tracing_exercises` SET `xp_reward` = IF(`hsk_level` >= 2, 15, 10) WHERE `lesson_id` IS NULL;
UPDATE `matching_games` SET `xp_reward` = 15 WHERE `lesson_id` IS NULL;
UPDATE `listening_exercises`
SET `xp_reward` = 3 * COALESCE(JSON_LENGTH(`question_payload`, '$.questions'), 0)
WHERE JSON_VALID(`question_payload`);
UPDATE `drag_drop_activities` d
SET d.`xp_reward` = 4 * (SELECT COUNT(*) FROM `drag_drop_items` i WHERE i.`drag_drop_activity_id` = d.`id`);
UPDATE `typing_exercises`
SET `xp_reward` = 2 * COALESCE(JSON_LENGTH(`word_payload`, '$.words'), 0)
WHERE JSON_VALID(`word_payload`);

-- 6. Practice set names (Figma, 17 Nov): the platform's Typing sets lose their "Typing" suffix.
UPDATE `typing_exercises` SET `title` = TRIM(TRAILING ' Typing' FROM `title`)
WHERE `created_by_user_id` IS NULL AND `title` LIKE '% Typing';
UPDATE `typing_exercises` SET `title` = 'Mixed Words' WHERE `created_by_user_id` IS NULL AND `title` = 'Mixed Review';

-- 7. AI reports: a learner may report a SparkBot hint (feature HINT; hints are built by rules, so no request is logged
--    under it) and a plan text that does not match what they wrote (reason DOES_NOT_MATCH_INPUT).
ALTER TABLE `ai_usage_logs` MODIFY `feature_key`
  enum('CHAT_TUTOR','HINT','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_DRAFT','QUIZ_GENERATOR','STORY_GENERATOR','XIAOMEI_CONVERSATION') NOT NULL;
ALTER TABLE `ai_response_reports` MODIFY `feature_key`
  enum('CHAT_TUTOR','HINT','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_DRAFT','QUIZ_GENERATOR','STORY_GENERATOR','XIAOMEI_CONVERSATION') NOT NULL;
ALTER TABLE `ai_chat_sessions` MODIFY `feature_key`
  enum('CHAT_TUTOR','HINT','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_DRAFT','QUIZ_GENERATOR','STORY_GENERATOR','XIAOMEI_CONVERSATION') NOT NULL;
ALTER TABLE `ai_response_reports` DROP CHECK `chk_ai_response_reports_reason_category`;
ALTER TABLE `ai_response_reports` ADD CONSTRAINT `chk_ai_response_reports_reason_category` CHECK ((`reason_category` in ('INCORRECT_CONTENT','UNCLEAR_EXPLANATION','INAPPROPRIATE_CONTENT','SCORE_SEEMS_WRONG','DOES_NOT_MATCH_INPUT','OTHER')));

-- 8. The platform has no teachers: the default AI notice no longer names one. A notice an admin wrote is kept.
UPDATE `system_settings` SET `setting_value` = 'AI can make mistakes.'
WHERE `setting_key` = 'ai_disclaimer_text' AND `setting_value` = 'AI responses may contain errors and do not replace your teacher.';

-- 9. Learners' notifications: a rank reached, a new Practice set, an updated class lesson and the daily-goal and
--    mistakes reminders (the reminders' last runs are kept in system_settings, created on their first run).
ALTER TABLE `notifications` MODIFY `notification_type`
  enum('ADMIN_TASK','AI_REPORT_REVIEW','DAILY_GOAL_REMINDER','LESSON_UPDATED','MISTAKES_TO_REVIEW','NEW_PRACTICE_SET','RANK_REACHED','SUPPORT_REQUEST','SYSTEM') NOT NULL;

-- 10. A quiz answer may carry feedback for the learner who picks it (A3.19: Noodles → "That's 面").
ALTER TABLE `quiz_options` ADD COLUMN `feedback` varchar(300) DEFAULT NULL;