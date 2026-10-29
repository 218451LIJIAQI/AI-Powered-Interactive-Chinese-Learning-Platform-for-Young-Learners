package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs against an actual MySQL service in CI with Flyway and Hibernate validation enabled. */
// MOCK (not NONE): the security configuration needs the servlet web-security auto-configuration,
// which is skipped in a non-web context, so a NONE context fails before Flyway/Hibernate are checked.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_MIGRATION_TEST", matches = "true")
class MySqlMigrationSmokeTests {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesAllMigrationsAndStartsWithValidatedSchema() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(Arrays.stream(flyway.info().applied())
                .map(migration -> migration.getVersion().getVersion()))
                .contains("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16");
        assertThat(count("select count(*) from roles")).isGreaterThanOrEqualTo(3);
        // V15: the learning plan and the sign-up e-mail confirmation.
        assertThat(count("""
                select count(*) from information_schema.tables
                where table_schema = database()
                  and table_name in ('learning_plans', 'learning_plan_steps', 'email_confirmation_tokens')
                """)).isEqualTo(3L);
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = 'student_profiles' and column_name = 'plan_setup_state'
                """)).isEqualTo(1L);
    }

    @Test
    void badgeAlignmentMigrationLeavesOnlyAwardedBadgesActive() {
        assertThat(jdbcTemplate.queryForList(
                "select name from badges where active = 1 order by name", String.class))
                .containsExactlyInAnyOrder(
                        "10-day Streak", "300 XP", "First Steps", "Lesson Finisher", "Quiz Star", "Streak Explorer",
                        "Unit Champion", "XP Starter");
    }

    @Test
    void learnerExperienceAndAiHelperMigrationsLeaveTheExpectedSchema() {
        // V5 added practice_submissions for teacher review; V13 replaced it with student-only practice checks.
        assertThat(count("""
                select count(*) from information_schema.tables
                where table_schema = database() and table_name in ('practice_submissions', 'ai_feedback_records')
                """)).isZero();
        assertThat(count("""
                select count(*) from information_schema.tables
                where table_schema = database() and table_name = 'ai_practice_checks'
                """)).isEqualTo(1L);
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = 'teacher_feedback'
                  and column_name = 'practice_submission_id'
                """)).isZero();
        assertThat(count("""
                select count(*) from information_schema.tables
                where table_schema = database() and table_name = 'student_progress'
                """)).isZero();
        assertThat(count("""
                select count(*) from information_schema.referential_constraints
                where constraint_schema = database()
                  and constraint_name = 'fk_ai_practice_checks_student_user'
                """)).isEqualTo(1L);
        assertThat(count("""
                select count(*) from information_schema.tables
                where table_schema = database()
                  and table_name in ('content_moderation_records', 'student_quiz_progress')
                """)).isZero();
        assertThat(count("""
                select count(*) from information_schema.table_constraints
                where table_schema = database() and table_name = 'quiz_attempt_answers'
                  and constraint_name = 'uk_quiz_attempt_answers_attempt_question'
                """)).isEqualTo(1L);
        assertThat(count("""
                select count(*) from information_schema.statistics
                where table_schema = database() and table_name = 'live_class_enrolments'
                  and index_name = 'uk_live_class_enrolments_live_class_student'
                """)).isZero();
        assertThat(count("""
                select count(*) from information_schema.tables
                where table_schema = database() and table_type = 'BASE TABLE'
                  and table_name <> 'flyway_schema_history'
                  and (table_collation is null or table_collation <> 'utf8mb4_0900_ai_ci')
                """)).isZero();
        // The JDBC connection transfers Chinese characters and tone-marked Pinyin without loss (utf8mb4).
        assertThat(jdbcTemplate.queryForObject("select convert('你好 nǐ hǎo' using utf8mb4)", String.class))
                .isEqualTo("你好 nǐ hǎo");
    }

    @Test
    void removeCommerceMigrationLeavesNoCommerceTablesOrColumns() {
        assertThat(count("""
                select count(*) from information_schema.tables
                where table_schema = database()
                  and table_name in ('carts', 'cart_items', 'orders', 'order_items', 'payment_records',
                                     'payment_entitlements', 'refund_requests', 'payout_requests',
                                     'teacher_earnings')
                """)).isZero();
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database()
                  and table_name in ('learning_modules', 'lessons', 'course_packages')
                  and column_name in ('price', 'access_type')
                """)).isZero();
        assertThat(count(
                "select count(*) from system_settings where setting_key in ('default_currency', 'platform_commission_rate')"))
                .isZero();
    }

    @Test
    void contentReviewMigrationAddsLearningStageAndRetiresApprovedStatus() {
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database() and column_name = 'learning_stage'
                  and table_name in ('quizzes', 'flashcards', 'matching_games', 'listening_exercises',
                                     'tracing_exercises', 'drag_drop_activities', 'story_activities')
                """)).isEqualTo(7L);
        assertThat(count("select count(*) from lessons where content_status = 'APPROVED'")).isZero();
    }

    @Test
    void youngAdultAlignmentMigrationRemovesAgeBandsAndUnusedSettings() {
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database() and column_name = 'age_band'
                """)).isZero();
        assertThat(count("select count(*) from ai_response_reports where reason_category = 'AGE_INAPPROPRIATE'"))
                .isZero();
        assertThat(count("""
                select count(*) from system_settings
                where setting_key in ('admin_email_notifications_enabled', 'admin_in_app_notifications_enabled',
                                      'admin_daily_summary_enabled', 'strict_content_filtering_enabled')
                   or setting_value like '%Young Learners%'
                """)).isZero();
    }

    @Test
    void schemaCleanupMigrationDropsUnusedColumnsIndexesAndEnumValues() {
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database()
                  and ((table_name = 'student_profiles' and column_name in ('display_name', 'country', 'avatar_url'))
                    or (table_name = 'ai_feedback_records' and column_name = 'input_file_url')
                    or (table_name = 'ai_chat_sessions' and column_name = 'related_lesson_id'))
                """)).isZero();
        // SDD Table 4.2 lists student_profiles.school_name, so V9 keeps it.
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = 'student_profiles' and column_name = 'school_name'
                """)).isEqualTo(1L);
        assertThat(count("""
                select count(*) from information_schema.table_constraints
                where table_schema = database() and table_name = 'ai_chat_sessions'
                  and constraint_name = 'fk_ai_chat_sessions_related_lesson'
                """)).isZero();
        assertThat(count("""
                select count(*) from information_schema.statistics
                where table_schema = database()
                  and (index_name in ('idx_ai_chat_sessions_related_lesson_id', 'ft_lessons_title_summary_content',
                                      'ft_course_packages_title_description', 'ft_flashcards_learning_text')
                       or index_type = 'FULLTEXT')
                """)).isZero();

        assertThat(columnType("notifications", "notification_type"))
                .contains("'SYSTEM'")
                .doesNotContain("'QUIZ_RESULT'", "'REWARD_EARNED'");
        assertThat(columnType("live_class_enrolments", "enrolment_status"))
                .contains("'CANCELLED'")
                .doesNotContain("'MISSED'");
    }

    private Long count(String countQuery) {
        return jdbcTemplate.queryForObject(countQuery, Long.class);
    }

    private String columnType(String table, String column) {
        return jdbcTemplate.queryForObject(
                """
                select column_type from information_schema.columns
                where table_schema = database() and table_name = ? and column_name = ?
                """,
                String.class,
                table,
                column);
    }
}
