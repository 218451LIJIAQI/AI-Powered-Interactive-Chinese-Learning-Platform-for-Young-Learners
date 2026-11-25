package com.example.aichinese;

import static com.example.aichinese.support.FlywayMigrationFiles.EXPECTED_VERSIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.aichinese.service.account.RetainedSchoolNames;
import com.example.aichinese.support.MySqlSchema;

/**
 * Runs against a real MySQL server: Flyway builds the consolidated baseline schema and Hibernate validates the
 * entities against it (the context only starts when both succeed).
 */
// MOCK (not NONE): the security configuration needs the servlet web-security auto-configuration,
// which is skipped in a non-web context, so a NONE context fails before Flyway/Hibernate are checked.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.security.jwt.secret=mysql-migration-smoke-test-secret-0123456789")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_MIGRATION_TEST", matches = "true")
class MySqlMigrationSmokeTests {

    private static final LocalDateTime SAMPLE_TIME = LocalDateTime.of(2026, 10, 3, 9, 30);

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
                        "chk_quiz_attempts_wide_score_bounds",
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
                        "idx_typing_exercises_status_updated",
                        // Hot-path lookup, queue, and analytics indexes.
                        "idx_notifications_target",
                        "idx_practice_answers_student_item_created",
                        "idx_practice_completions_student_type_activity",
                        "idx_practice_completions_created_at",
                        "idx_lesson_progress_completed_at",
                        "idx_lesson_progress_last_accessed",
                        "idx_points_transactions_student_source_pair",
                        "idx_package_items_item_package",
                        "idx_learning_mistakes_student_review_order",
                        "idx_learning_mistakes_cleared_student",
                        "idx_learn_lesson_completions_first_completed",
                        "idx_learn_lesson_completions_last_completed",
                        "idx_learn_lesson_completions_student_first",
                        "idx_quiz_attempts_student_quiz_submitted",
                        "idx_ai_practice_checks_student_deleted_created",
                        "idx_admin_invites_pending",
                        "idx_support_requests_status_updated",
                        "idx_support_requests_status_solved",
                        "idx_support_requests_requester_email",
                        "idx_ai_usage_logs_feature_status",
                        "idx_refresh_tokens_family_active",
                        "idx_refresh_tokens_user_revoked",
                        "idx_served_hints_student_run",
                        "idx_ai_chat_sessions_user_id",
                        // Composite covering FK / ordering indexes.
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
                        // Retired / superseded secondary indexes.
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
        // Retired unmapped columns stay absent on a fresh database.
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
        // No teacher tables or columns; admin-authored classes keep archived_at, quizzes hints_allowed, questions ai_draft.
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
        // Practice has no quizzes; plans can be drafts with class picks, flashcards "Still learning" mistakes, tracing
        // sets per-character strokes, and quiz options per-answer feedback.
        assertThat(count("""
                select count(*) from information_schema.tables
                where table_schema = database() and table_name like 'practice_quiz%'
                """)).isZero();
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database()
                  and ((table_name = 'learning_plans' and column_name in ('confirmed', 'picked_class_id'))
                    or (table_name = 'learning_mistakes' and column_name = 'still_learning')
                    or (table_name = 'tracing_exercises' and column_name = 'character_strokes')
                    or (table_name = 'quiz_options' and column_name = 'feedback'))
                """)).isEqualTo(5L);
        // Xiaomei memory notes, soft-deleted practice checks, pronunciation JSON, and run-scoped hints.
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database()
                  and ((table_name = 'xiaomei_memory_notes' and column_name in ('note_text', 'plan_reason', 'forgotten_at'))
                    or (table_name = 'ai_practice_checks' and column_name in ('deleted_at', 'pronunciation'))
                    or (table_name = 'served_hints' and column_name = 'run_id')
                    or (table_name = 'admin_invites' and column_name in ('cancelled_at', 'unaccepted_email')))
                """)).isEqualTo(8L);
        // Retired write-only and denormalized columns stay absent.
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema = database()
                  and ((table_name = 'learning_modules' and column_name = 'content_status')
                    or (table_name = 'ai_chat_sessions' and column_name = 'feature_key')
                    or (table_name = 'uploaded_files' and column_name = 'active')
                    or (table_name = 'video_progress' and column_name in ('last_watched_at', 'lesson_id'))
                    or (table_name = 'refresh_tokens' and column_name in ('created_by_ip', 'user_agent')))
                """)).isZero();

        // Composite covering index column orders.
        assertThat(indexColumns(jdbcTemplate, "admin_invites", "idx_admin_invites_pending"))
                .containsExactly("accepted_at", "cancelled_at", "expires_at");
        assertThat(indexColumns(jdbcTemplate, "served_hints", "idx_served_hints_student_run"))
                .containsExactly("student_id", "run_id", "item_ref");
        assertThat(indexColumns(jdbcTemplate, "drag_drop_items", "idx_drag_drop_items_activity_order"))
                .containsExactly("drag_drop_activity_id", "sort_order", "created_at");
        assertThat(indexColumns(jdbcTemplate, "matching_game_items", "idx_matching_game_items_game_order"))
                .containsExactly("matching_game_id", "sort_order", "created_at");
        assertThat(indexColumns(jdbcTemplate, "quiz_questions", "idx_quiz_questions_quiz_order"))
                .containsExactly("quiz_id", "sort_order", "created_at");
        assertThat(indexColumns(jdbcTemplate, "quiz_options", "idx_quiz_options_question_order"))
                .containsExactly("question_id", "sort_order", "created_at");
        assertThat(indexColumns(jdbcTemplate, "email_change_tokens", "idx_email_change_tokens_user_open"))
                .containsExactly("user_id", "used_at", "expires_at");
    }

    /**
     * Exercises live MySQL enforcement of the consolidated schema's CHECK constraints, generated unique keys, foreign
     * keys, widened numeric/string types, and JSON columns against an isolated fresh Flyway database.
     */
    @Test
    void enforcesDatabaseConstraintsAndColumnContracts() {
        LocalDateTime at = SAMPLE_TIME;
        LocalDateTime expires = at.plusDays(7);
        try (MySqlSchema schema = MySqlSchema.recreate("migration", "_upgrade_check")) {
            Flyway freshFlyway = Flyway.configure().dataSource(schema.dataSource()).load();
            freshFlyway.migrate();
            JdbcTemplate jdbc = schema.jdbc();

            // Column sizes and safe handling when student_profiles.school_name is absent.
            assertThat(jdbc.queryForObject("""
                    select character_maximum_length from information_schema.columns
                    where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'pinyin'
                    """, Integer.class)).isEqualTo(400);
            assertThat(jdbc.queryForObject("""
                    select character_maximum_length from information_schema.columns
                    where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'meaning'
                    """, Integer.class)).isEqualTo(700);
            new RetainedSchoolNames(jdbc).clear(1L);

            jdbc.update("""
                    insert into users (id, active, created_at, updated_at, display_name, email, password_hash)
                    values (1, b'1', ?, ?, 'Learner', 'learner@example.com', 'x')
                    """, at, at);
            jdbc.update("""
                    insert into student_profiles (id, total_points, created_at, updated_at, user_id)
                    values (1, 0, ?, ?, 1)
                    """, at, at);

            // admin_invites: generated unaccepted_email unique key and accepted/cancelled mutual exclusion.
            jdbc.update("""
                    insert into admin_invites (id, email, token_hash, expires_at, accepted_at, created_at, updated_at)
                    values (1, 'dup@example.com', 'hash-accepted-1', ?, ?, ?, ?),
                           (2, 'dup@example.com', 'hash-accepted-2', ?, ?, ?, ?),
                           (3, 'dup@example.com', 'hash-open-1', ?, null, ?, ?)
                    """, expires, at, at, at, expires, at, at, at, expires, at, at);
            assertThat(jdbc.queryForList("select unaccepted_email from admin_invites order by id", String.class))
                    .containsExactly(null, null, "dup@example.com");
            assertThatThrownBy(() -> jdbc.update("""
                    insert into admin_invites (id, email, token_hash, expires_at, accepted_at, created_at, updated_at)
                    values (4, 'dup@example.com', 'hash-open-2', ?, null, ?, ?)
                    """, expires, at, at))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("uk_admin_invites_unaccepted_email");
            assertThatThrownBy(() -> jdbc.update("""
                    insert into admin_invites (id, email, token_hash, expires_at, accepted_at, cancelled_at, created_at, updated_at)
                    values (5, 'both@example.com', 'hash-both', ?, ?, ?, ?, ?)
                    """, expires, at, at, at, at))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("chk_admin_invites_not_accepted_and_cancelled");

            // package_items: FK to learning_modules and unique (course_package_id, item_id).
            jdbc.update("""
                    insert into course_packages (id, created_at, updated_at, title, content_status)
                    values (1, ?, ?, 'Travel Class', 'PUBLISHED')
                    """, at, at);
            jdbc.update("""
                    insert into learning_modules (id, created_at, updated_at, title)
                    values (10, ?, ?, 'Unit 1')
                    """, at, at);
            jdbc.update("""
                    insert into package_items (id, created_at, updated_at, course_package_id, item_id, sort_order)
                    values (1, ?, ?, 1, 10, 1)
                    """, at, at);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into package_items (id, created_at, updated_at, course_package_id, item_id, sort_order)
                    values (2, ?, ?, 1, 10, 2)
                    """, at, at))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("uk_package_items_package_item");
            assertThatThrownBy(() -> jdbc.update("""
                    insert into package_items (id, created_at, updated_at, course_package_id, item_id, sort_order)
                    values (3, ?, ?, 1, 998, 3)
                    """, at, at))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("fk_package_items_module");

            // lessons: unique working-copy revision per published lesson while allowing multiple NULLs.
            jdbc.update("""
                    insert into lessons (id, created_at, updated_at, title, content_status, unit_position, revision_of_lesson_id)
                    values (10, ?, ?, 'Published Lesson', 'PUBLISHED', 1, null),
                           (11, ?, ?, 'Second Published Lesson', 'PUBLISHED', 2, null),
                           (12, ?, ?, 'Working Copy', 'DRAFT', 1, 10)
                    """, at, at, at, at, at, at);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into lessons (id, created_at, updated_at, title, content_status, unit_position, revision_of_lesson_id)
                    values (13, ?, ?, 'Duplicate Revision', 'DRAFT', 1, 10)
                    """, at, at))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("uk_lessons_revision_of");

            // quizzes, case-sensitive question_type check, and 64-bit quiz_attempts score bounds.
            jdbc.update("""
                    insert into quizzes (id, created_at, updated_at, lesson_id, title, content_status)
                    values (1, ?, ?, 10, 'Lesson Quiz', 'PUBLISHED')
                    """, at, at);
            jdbc.update("""
                    insert into quiz_questions (id, quiz_id, points, sort_order, question_type, question_text, created_at, updated_at)
                    values (1, 1, 5, 1, 'MULTIPLE_CHOICE', 'A', ?, ?)
                    """, at, at);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into quiz_questions (id, quiz_id, points, sort_order, question_type, question_text, created_at, updated_at)
                    values (2, 1, 5, 2, 'true_false', 'B', ?, ?)
                    """, at, at))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("chk_quiz_questions_question_type");

            jdbc.update("""
                    insert into quiz_attempts (id, quiz_id, student_id, submitted, score, max_score, completed_at, created_at, updated_at)
                    values (1, 1, 1, b'1', 5200000000, 5200000000, ?, ?, ?)
                    """, at, at, at);
            assertThat(jdbc.queryForObject("select score from quiz_attempts where id = 1", Long.class))
                    .isEqualTo(5_200_000_000L);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into quiz_attempts (id, quiz_id, student_id, submitted, score, max_score, completed_at, created_at, updated_at)
                    values (2, 1, 1, b'1', 10, 5, ?, ?, ?)
                    """, at, at, at))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("chk_quiz_attempts_wide_score_bounds");

            // served_hints: accepts both NULL and ASCII run IDs.
            jdbc.update("""
                    insert into served_hints (step, created_at, updated_at, student_id, item_ref, run_id)
                    values (1, ?, ?, 1, 'LESSON#1#2', null),
                           (2, ?, ?, 1, 'LESSON#1#2', '01234567-89ab-cdef-0123-456789abcdef')
                    """, at, at, at, at);

            // Case-sensitive AI enum CHECK constraints and active-only check_type + pronunciation JSON.
            jdbc.update("""
                    insert into ai_chat_sessions (id, user_id, created_at, updated_at)
                    values (1, 1, ?, ?)
                    """, at, at);
            jdbc.update("""
                    insert into ai_chat_messages (id, session_id, sender_role, message_text, created_at, updated_at)
                    values (1, 1, 'USER', 'hello', ?, ?)
                    """, at, at);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into ai_chat_messages (id, session_id, sender_role, message_text, created_at, updated_at)
                    values (2, 1, 'user', 'bad', ?, ?)
                    """, at, at))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("chk_ai_chat_messages_sender_role");

            jdbc.update("""
                    insert into ai_usage_logs (id, user_id, feature_key, status, created_at, updated_at)
                    values (1, 1, 'CHAT_TUTOR', 'SUCCESS', ?, ?)
                    """, at, at);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into ai_usage_logs (id, user_id, feature_key, status, created_at, updated_at)
                    values (2, 1, 'CHAT_TUTOR', 'success', ?, ?)
                    """, at, at))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("chk_ai_usage_logs_status");

            jdbc.update("""
                    insert into ai_response_reports
                        (id, student_user_id, feature_key, status, reason_category, ai_response_snapshot, reviewed_at, created_at, updated_at)
                    values (1, 1, 'LEARNING_PLAN', 'PENDING_REVIEW', 'DOES_NOT_MATCH_INPUT', 'resp-1', null, ?, ?)
                    """, at, at);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into ai_response_reports
                        (id, student_user_id, feature_key, status, reason_category, ai_response_snapshot, reviewed_at, created_at, updated_at)
                    values (2, 1, 'CHAT_TUTOR', 'pending_review', 'OTHER', 'resp', null, ?, ?)
                    """, at, at))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageMatching("(?s).*chk_ai_response_reports_(status|review_consistency).*");
            assertThatThrownBy(() -> jdbc.update("""
                    insert into ai_response_reports
                        (id, student_user_id, feature_key, status, reason_category, ai_response_snapshot, reviewed_at, created_at, updated_at)
                    values (3, 1, 'CHAT_TUTOR', 'PENDING_REVIEW', 'other', 'resp', null, ?, ?)
                    """, at, at))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("chk_ai_response_reports_reason_category");

            jdbc.update("""
                    insert into ai_practice_checks
                        (id, created_at, updated_at, student_user_id, check_type, target_text, score, headline, pronunciation)
                    values (1, ?, ?, 1, 'RECORDING', '你好', 88, 'Clear pronunciation', '{"overall":88}')
                    """, at, at);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into ai_practice_checks
                        (id, created_at, updated_at, student_user_id, check_type, target_text, score, headline)
                    values (2, ?, ?, 1, 'TONES', '再见', 80, 'Rejected')
                    """, at, at))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("check_type");
        }
    }

    private Long count(String countQuery) {
        return jdbcTemplate.queryForObject(countQuery, Long.class);
    }

    private static List<String> indexColumns(JdbcTemplate jdbc, String table, String index) {
        return jdbc.queryForList("""
                select column_name from information_schema.statistics
                where table_schema = database() and table_name = ? and index_name = ?
                order by seq_in_index asc
                """, String.class, table, index);
    }
}
