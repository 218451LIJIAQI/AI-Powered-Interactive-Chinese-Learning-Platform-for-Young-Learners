package com.example.aichinese;

import static com.example.aichinese.support.FlywayMigrationFiles.EXPECTED_VERSIONS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs against a real MySQL server: Flyway builds the schema from the migrations and Hibernate validates the
 * entities against it (the context only starts when both succeed).
 */
// MOCK (not NONE): the security configuration needs the servlet web-security auto-configuration,
// which is skipped in a non-web context, so a NONE context fails before Flyway/Hibernate are checked.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.security.jwt.secret=mysql-migration-smoke-test-secret-0123456789")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_MIGRATION_TEST", matches = "true")
class MySqlMigrationSmokeTests {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void buildsTheBaselineWithItsReferenceData() {
        assertThat(Arrays.stream(flyway.info().applied()).map(migration -> migration.getVersion().getVersion()))
                .containsExactlyElementsOf(EXPECTED_VERSIONS);
        assertThat(count("select next_number from support_request_numbers where id = 1"))
                .isGreaterThanOrEqualTo(1000L);
        // Learners and admins: there are no teachers any more.
        assertThat(jdbcTemplate.queryForList("select name from roles order by name", String.class))
                .containsExactly("ADMIN", "STUDENT");
        assertThat(jdbcTemplate.queryForList("select name from learning_categories order by sort_order", String.class))
                .containsExactly("Travel", "Work and career", "Study or exchange", "Daily life", "Personal interest");
        assertThat(jdbcTemplate.queryForList("select name from badges where active = 1 order by name", String.class))
                .containsExactlyInAnyOrder(
                        "10-day Streak", "300 XP", "First Steps", "Lesson Finisher", "Quiz Star", "Streak Explorer",
                        "Unit Champion", "XP Starter");
    }

    @Test
    void storesChineseAndPinyinInUtf8mb4() {
        assertThat(count("""
                select count(*) from information_schema.tables
                where table_schema = database() and table_type = 'BASE TABLE'
                  and table_name <> 'flyway_schema_history'
                  and (table_collation is null or table_collation <> 'utf8mb4_0900_ai_ci')
                """)).isZero();
        // The JDBC connection transfers Chinese characters and tone-marked Pinyin without loss.
        assertThat(jdbcTemplate.queryForObject("select convert('你好 nǐ hǎo' using utf8mb4)", String.class))
                .isEqualTo("你好 nǐ hǎo");
    }

    /**
     * Constraints the data depends on and indexes of the hot paths: Hibernate validation does not read them, so the
     * built schema is checked directly.
     */
    @Test
    void keepsIntegrityConstraintsAndHotPathIndexes() {
        assertThat(jdbcTemplate.queryForList(
                "select constraint_name from information_schema.table_constraints where table_schema = database()",
                String.class))
                .contains(
                        "uk_lesson_progress_student_lesson",
                        "uk_daily_streaks_student",
                        "uk_points_transactions_student_source",
                        "uk_password_reset_tokens_active_user",
                        "uk_refresh_tokens_token_hash",
                        "uk_ai_usage_quota_counters_user",
                        "uk_quiz_attempt_answers_attempt_question",
                        "uk_admin_invites_unaccepted_email",
                        "uk_lessons_revision_of",
                        "fk_package_items_module",
                        "uk_package_items_package_item",
                        "chk_admin_invites_not_accepted_and_cancelled",
                        "chk_quiz_questions_question_type",
                        "chk_ai_chat_messages_sender_role",
                        "chk_ai_usage_logs_status",
                        "chk_ai_response_reports_status",
                        "chk_ai_response_reports_reason_category",
                        "chk_ai_response_reports_review_consistency",
                        "chk_ai_response_reports_snapshot_source");
        assertThat(jdbcTemplate.queryForList(
                "select index_name from information_schema.statistics where table_schema = database()",
                String.class))
                .contains(
                        "idx_audit_logs_target_created",
                        "idx_ai_chat_messages_session_role_id",
                        "idx_ai_response_reports_student_created",
                        "idx_notifications_user_created_id",
                        "idx_notifications_user_status_created_id",
                        // The status lists of each content table.
                        "idx_course_packages_status_updated",
                        "idx_lessons_status_updated",
                        "idx_flashcards_status_updated",
                        "idx_matching_games_status_updated",
                        "idx_listening_exercises_status_updated",
                        "idx_tracing_exercises_status_updated",
                        "idx_drag_drop_activities_status_updated",
                        // V10 hot-path indexes.
                        "idx_notifications_target",
                        "idx_practice_answers_student_item_created",
                        "idx_practice_completions_student_type_activity",
                        "idx_practice_completions_created_at",
                        "idx_lesson_progress_completed_at",
                        "idx_lesson_progress_last_accessed",
                        "idx_points_transactions_student_source_pair",
                        "idx_package_items_item_package",
                        "idx_typing_exercises_status_updated",
                        "idx_learning_mistakes_student_review_order",
                        "idx_learning_mistakes_cleared_student",
                        // V11 hot-path indexes.
                        "idx_learn_lesson_completions_first_completed",
                        "idx_learn_lesson_completions_last_completed",
                        "idx_learn_lesson_completions_student_first",
                        "idx_quiz_attempts_student_quiz_submitted",
                        "idx_ai_practice_checks_student_deleted_created",
                        "idx_admin_invites_pending",
                        // V12 hot-path indexes.
                        "idx_support_requests_status_updated",
                        "idx_support_requests_status_solved",
                        "idx_support_requests_requester_email",
                        "idx_ai_usage_logs_feature_status",
                        "idx_refresh_tokens_family_active",
                        "idx_refresh_tokens_user_revoked",
                        // V13 FK-backing replacement index.
                        "idx_ai_chat_sessions_user_id",
                        // V14 composite covering indexes.
                        "idx_drag_drop_items_activity_order",
                        "idx_matching_game_items_game_order",
                        "idx_quiz_questions_quiz_order",
                        "idx_quiz_options_question_order",
                        "idx_email_change_tokens_user_open")
                .doesNotContain(
                        "idx_ai_usage_quota_counters_user_id",
                        "idx_daily_streaks_student_id",
                        "idx_package_items_package_id",
                        "idx_quiz_attempts_student_id",
                        "idx_ai_practice_checks_student_deleted",
                        "idx_ai_practice_checks_student_created",
                        "idx_ai_practice_checks_deleted",
                        "idx_learning_mistakes_student_waiting",
                        "idx_xiaomei_memory_notes_forgotten",
                        "idx_course_packages_status",
                        "idx_typing_exercises_status",
                        "idx_support_requests_status_created",
                        "idx_ai_usage_logs_feature_key",
                        "idx_refresh_tokens_user_id",
                        // V13 dropped unused/superseded secondary indexes.
                        "idx_refresh_tokens_family_id",
                        "idx_refresh_tokens_expires_at",
                        "idx_ai_chat_sessions_feature_key",
                        "idx_ai_chat_sessions_user_feature_created",
                        "idx_ai_response_reports_feature_key",
                        "idx_audit_logs_request_id",
                        "idx_audit_logs_actor_email_created",
                        "idx_uploaded_files_created_at",
                        "idx_student_badges_student_awarded",
                        "idx_practice_completions_student_created",
                        "idx_learning_modules_status_created_at",
                        "idx_learning_modules_status_updated",
                        "idx_lessons_status_created_at",
                        "idx_quizzes_status_updated",
                        "idx_video_progress_lesson_last_watched",
                        // V14 dropped unqueried hsk_level and superseded single-column FK indexes.
                        "idx_lessons_revision_of",
                        "idx_drag_drop_items_activity_id",
                        "idx_matching_game_items_game_id",
                        "idx_quiz_questions_quiz_id",
                        "idx_quiz_options_question_id",
                        "idx_email_change_tokens_user_id",
                        "idx_flashcards_hsk_level",
                        "idx_matching_games_hsk_level",
                        "idx_listening_exercises_hsk_level",
                        "idx_tracing_exercises_hsk_level",
                        "idx_drag_drop_activities_hsk_level",
                        "idx_typing_exercises_hsk_level");
        // V2 drops the columns no code uses where they are empty, as on a new database.
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database()
                  and ((table_name = 'ai_practice_checks' and column_name in ('shape_score', 'tone_detail'))
                    or (table_name = 'student_profiles' and column_name = 'school_name'))
                """)).isZero();
        // A quiz always has its lesson.
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database() and is_nullable = 'YES'
                  and table_name = 'quizzes' and column_name = 'lesson_id'
                """)).isZero();
        // V3: no teacher anywhere; the admins' classes keep when they were archived, quizzes their hint switch and
        // questions their AI draft mark.
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database() and column_name = 'teacher_id'
                """)).isZero();
        assertThat(count("""
                select count(*) from information_schema.tables
                where table_schema = database() and table_name like 'teacher%'
                """)).isZero();
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database()
                  and ((table_name = 'course_packages' and column_name = 'archived_at')
                    or (table_name = 'quizzes' and column_name = 'hints_allowed')
                    or (table_name = 'quiz_questions' and column_name = 'ai_draft'))
                """)).isEqualTo(3L);
        // V4: Practice has no quizzes; plans can be drafts, flashcards "Still learning" mistakes, tracing sets the
        // strokes of each character and quiz answers their feedback.
        assertThat(count("""
                select count(*) from information_schema.tables
                where table_schema = database() and table_name like 'practice_quiz%'
                """)).isZero();
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database()
                  and ((table_name = 'learning_plans' and column_name = 'confirmed')
                    or (table_name = 'learning_mistakes' and column_name = 'still_learning')
                    or (table_name = 'tracing_exercises' and column_name = 'character_strokes')
                    or (table_name = 'quiz_options' and column_name = 'feedback'))
                """)).isEqualTo(4L);
        // V5: what Xiaomei remembers, and the Undo of a deleted practice check.
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database()
                  and ((table_name = 'xiaomei_memory_notes' and column_name in ('note_text', 'plan_reason', 'forgotten_at'))
                    or (table_name = 'ai_practice_checks' and column_name = 'deleted_at'))
                """)).isEqualTo(4L);
        // V13: write-only and denormalized columns removed.
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database()
                  and ((table_name = 'learning_modules' and column_name = 'content_status')
                    or (table_name = 'ai_chat_sessions' and column_name = 'feature_key')
                    or (table_name = 'uploaded_files' and column_name = 'active')
                    or (table_name = 'video_progress' and column_name in ('last_watched_at', 'lesson_id'))
                    or (table_name = 'refresh_tokens' and column_name in ('created_by_ip', 'user_agent')))
                """)).isZero();
    }

    private Long count(String countQuery) {
        return jdbcTemplate.queryForObject(countQuery, Long.class);
    }
}
