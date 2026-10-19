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
                .contains("1", "2", "3", "4", "5", "6");
        assertThat(jdbcTemplate.queryForObject("select count(*) from roles", Long.class)).isGreaterThanOrEqualTo(3);
    }

    @Test
    void badgeAlignmentMigrationLeavesOnlyAwardedBadgesActive() {
        assertThat(jdbcTemplate.queryForList(
                "select name from badges where active = 1 order by name", String.class))
                .containsExactlyInAnyOrder(
                        "First Steps", "Lesson Finisher", "Point Starter", "Quiz Star", "Streak Explorer");
    }

    @Test
    void learnerExperienceMigrationAddsPracticeSubmissionsAndIntegrityConstraints() {
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.tables
                where table_schema = database() and table_name = 'practice_submissions'
                """,
                Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.tables
                where table_schema = database() and table_name = 'student_progress'
                """,
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.referential_constraints
                where constraint_schema = database()
                  and constraint_name in ('fk_student_quiz_progress_quiz', 'fk_ai_chat_sessions_related_lesson',
                                          'fk_teacher_feedback_practice_submission', 'fk_practice_submissions_student')
                """,
                Long.class)).isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.table_constraints
                where table_schema = database() and table_name = 'quiz_attempt_answers'
                  and constraint_name = 'uk_quiz_attempt_answers_attempt_question'
                """,
                Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.statistics
                where table_schema = database() and table_name = 'live_class_enrolments'
                  and index_name = 'uk_live_class_enrolments_live_class_student'
                """,
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.tables
                where table_schema = database() and table_type = 'BASE TABLE'
                  and table_name <> 'flyway_schema_history'
                  and (table_collation is null or table_collation <> 'utf8mb4_0900_ai_ci')
                """,
                Long.class)).isZero();
        // Chinese characters and tone-marked Pinyin survive a round trip (utf8mb4).
        assertThat(jdbcTemplate.queryForObject("select convert('你好 nǐ hǎo' using utf8mb4)", String.class))
                .isEqualTo("你好 nǐ hǎo");
    }

    @Test
    void removeCommerceMigrationLeavesNoCommerceTablesOrColumns() {
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.tables
                where table_schema = database()
                  and table_name in ('carts', 'cart_items', 'orders', 'order_items', 'payment_records',
                                     'payment_entitlements', 'refund_requests', 'payout_requests',
                                     'teacher_earnings')
                """,
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.columns
                where table_schema = database()
                  and table_name in ('learning_modules', 'lessons', 'course_packages')
                  and column_name in ('price', 'access_type')
                """,
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from system_settings where setting_key in ('default_currency', 'platform_commission_rate')",
                Long.class)).isZero();
    }

    @Test
    void contentReviewMigrationAddsLearningStageAndRetiresApprovedStatus() {
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.columns
                where table_schema = database() and column_name = 'learning_stage'
                  and table_name in ('quizzes', 'flashcards', 'matching_games', 'listening_exercises',
                                     'tracing_exercises', 'drag_drop_activities', 'story_activities')
                """,
                Long.class)).isEqualTo(7L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from lessons where content_status = 'APPROVED'",
                Long.class)).isZero();
    }

    @Test
    void youngAdultAlignmentMigrationRemovesAgeBandsAndUnusedSettings() {
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.columns
                where table_schema = database() and column_name = 'age_band'
                """,
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from ai_response_reports where reason_category = 'AGE_INAPPROPRIATE'",
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*) from system_settings
                where setting_key in ('admin_email_notifications_enabled', 'admin_in_app_notifications_enabled',
                                      'admin_daily_summary_enabled', 'strict_content_filtering_enabled')
                   or setting_value like '%Young Learners%'
                """,
                Long.class)).isZero();
    }
}
