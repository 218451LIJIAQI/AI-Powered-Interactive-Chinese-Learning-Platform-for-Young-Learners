package com.example.aichinese;

import static com.example.aichinese.support.FlywayMigrationFiles.EXPECTED_VERSIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.aichinese.service.account.RetainedSchoolNames;
import com.example.aichinese.support.MySqlSchema;

/**
 * Upgrades a database that already holds data, as a deployed installation would be upgraded: V2 must keep the data of a
 * V1 database, fill in its new columns from it, convert rows to what the code now writes, and drop an unused column only
 * where it is empty; V3 must remove the teachers from a V2 database and keep everything else; V4 must map the plans'
 * paces, remove the Practice quizzes and recompute the Practice XP of a V3 database; each later migration (V7 to V14)
 * must likewise keep and convert the rows its test puts in the version before it. Runs against the MySQL
 * server of the tests, in a database of its own (created and dropped here, named after TEST_DB_NAME so that concurrent
 * runs do not drop each other's).
 */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_MIGRATION_TEST", matches = "true")
class MySqlMigrationUpgradeTests {

    private static final LocalDateTime LAST_CHANGE = LocalDateTime.of(2026, 10, 3, 9, 30);

    private MySqlSchema database;

    @AfterEach
    void dropDatabase() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void v2KeepsTheDataOfAV1DatabaseAndFillsItsNewColumnsFromIt() {
        JdbcTemplate jdbc = upgrade(MySqlMigrationUpgradeTests::insertLegacyRows);

        // The class archived twice is archived by whoever archived it last; a class archived and restored is not.
        assertThat(jdbc.queryForObject("select archived_by_user_id from course_packages where id = 1", Long.class))
                .isEqualTo(7L);
        assertThat(jdbc.queryForObject("select archived_by_user_id from course_packages where id = 2", Long.class))
                .isNull();
        // A completed lesson counts from its last change; an open one has no completion time.
        assertThat(jdbc.queryForObject("select completed_at from lesson_progress where id = 1", LocalDateTime.class))
                .isEqualTo(LAST_CHANGE);
        assertThat(jdbc.queryForObject("select completed_at from lesson_progress where id = 2", LocalDateTime.class))
                .isNull();
        // Unused columns: one holding a value stays with it, an empty one goes.
        assertThat(columns(jdbc, "ai_practice_checks")).contains("shape_score", "tone_detail");
        assertThat(jdbc.queryForObject("select shape_score from ai_practice_checks where id = 1", Integer.class))
                .isEqualTo(64);
        assertThat(columns(jdbc, "student_profiles")).doesNotContain("school_name");
        // Account deletion has no school name to clear here, and does not fail on the missing column.
        new RetainedSchoolNames(jdbc).clear(1L);
        assertThat(columns(jdbc, "live_classes")).contains("reminder_sent_at");
        assertThat(jdbc.queryForObject("""
                select character_maximum_length from information_schema.columns
                where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'meaning'
                """, Integer.class)).isEqualTo(700);
    }

    /**
     * A database whose earlier version stored school names keeps the column (no entity maps it any more); deleting a
     * student's account clears that student's school name, and only theirs.
     */
    @Test
    void accountDeletionClearsTheSchoolNameV2Kept() {
        LocalDateTime at = LAST_CHANGE;
        JdbcTemplate jdbc = upgrade(legacy -> legacy.update("""
                insert into student_profiles (id, total_points, created_at, updated_at, user_id, school_name)
                values (1, 0, ?, ?, 11, 'SMK Taman Desa'), (2, 0, ?, ?, 12, 'SMK Seri Bintang')
                """, at, at, at, at));
        assertThat(columns(jdbc, "student_profiles")).contains("school_name");

        new RetainedSchoolNames(jdbc).clear(11L);

        assertThat(jdbc.queryForList("select school_name from student_profiles order by id", String.class))
                .containsExactly(null, "SMK Seri Bintang");
    }

    /**
     * "New student" notices written before they pointed at the enrolment (target CLASS + the class id) now point at
     * the enrolment of the student they name, so deleting that student's account removes them. The name must open the
     * message exactly; the class may have been renamed since; other notices about the class keep their target.
     */
    @Test
    void v2PointsTheOldNewStudentNoticesAtTheEnrolmentOfTheStudentTheyName() {
        LocalDateTime at = LAST_CHANGE;
        JdbcTemplate jdbc = upgrade(legacy -> {
            legacy.update("""
                    insert into users (id, active, created_at, updated_at, display_name, email, password_hash)
                    values (20, b'1', ?, ?, 'Li Wei', 'li.wei@example.com', 'x'),
                           (21, b'1', ?, ?, 'Aisha', 'aisha@example.com', 'x'),
                           (22, b'1', ?, ?, 'Aisha Tan', 'aisha.tan@example.com', 'x'),
                           (23, b'1', ?, ?, 'Ben', 'ben@example.com', 'x')
                    """, at, at, at, at, at, at, at, at);
            legacy.update("""
                    insert into teacher_profiles (id, created_at, updated_at, user_id, teacher_status)
                    values (1, ?, ?, 20, 'APPROVED')
                    """, at, at);
            legacy.update("""
                    insert into course_packages (id, created_at, updated_at, teacher_id, title, content_status)
                    values (1, ?, ?, 1, 'Campus Mandarin', 'PUBLISHED'), (2, ?, ?, 1, 'Travel Chinese', 'PUBLISHED')
                    """, at, at, at, at);
            legacy.update("""
                    insert into student_profiles (id, total_points, created_at, updated_at, user_id)
                    values (1, 0, ?, ?, 21), (2, 0, ?, ?, 22), (3, 0, ?, ?, 23)
                    """, at, at, at, at, at, at);
            legacy.update("""
                    insert into class_enrolments (id, created_at, updated_at, student_id, course_package_id, joined_at)
                    values (31, ?, ?, 1, 1, ?), (32, ?, ?, 2, 1, ?), (33, ?, ?, 3, 2, ?)
                    """, at, at, at, at, at, at, at, at, at);
            legacy.update("""
                    insert into notifications (id, created_at, updated_at, user_id, notification_type, title, message,
                                               target_type, target_id, target_path, notification_status)
                    values (1, ?, ?, 20, 'CLASS_ENROLMENT', 'New student', 'Aisha joined Campus Mandarin.',
                            'CLASS', 1, '/teacher/students', 'UNREAD'),
                           (2, ?, ?, 20, 'CLASS_ENROLMENT', 'New student', 'Aisha Tan joined Campus Mandarin.',
                            'CLASS', 1, '/teacher/students', 'UNREAD'),
                           (3, ?, ?, 20, 'CLASS_ENROLMENT', 'New student', 'Ben joined Chinese for Travellers.',
                            'CLASS', 2, '/teacher/students', 'UNREAD'),
                           (4, ?, ?, 20, 'CLASS_ENROLMENT', 'New student', 'aisha joined Campus Mandarin.',
                            'CLASS', 1, '/teacher/students', 'UNREAD'),
                           (5, ?, ?, 20, 'CONTENT_REVIEW', 'Class approved', 'Aisha joined Campus Mandarin.',
                            'CLASS', 1, '/teacher/classes/1', 'UNREAD')
                    """, at, at, at, at, at, at, at, at, at, at);
        });

        assertThat(jdbc.queryForList("select concat(target_type, ' ', target_id) from notifications order by id",
                String.class)).containsExactly(
                        "CLASS_ENROLMENT 31",
                        "CLASS_ENROLMENT 32",
                        // Renamed class: the student's name and the class id decide.
                        "CLASS_ENROLMENT 33",
                        // No student of the class has this name, letter for letter.
                        "CLASS 1",
                        // Not a "New student" notice.
                        "CLASS 1");
    }

    /**
     * V3 removes the teachers: a teacher account loses its role and can no longer sign in (an account that is also a
     * learner stays one), content in review becomes a draft, an archived class keeps when it was archived, a quiz's
     * stage becomes its hint switch, the notifications of the removed features go and the AI quiz draft's feature key
     * is renamed. Learners, admins and everything else stay as they were.
     */
    @Test
    void v3RemovesTheTeachersAndKeepsEverythingElse() {
        JdbcTemplate jdbc = upgrade("2", MySqlMigrationUpgradeTests::insertTeacherEraRows, "3");

        // Accounts: the teacher (3) can no longer sign in; the teacher who also learns (4) keeps learning.
        assertThat(jdbc.queryForList("select name from roles order by name", String.class)).containsExactly("ADMIN", "STUDENT");
        assertThat(jdbc.queryForList("select concat(user_id, ' ', role_id) from user_roles order by user_id, role_id",
                String.class)).containsExactly("1 1", "2 2", "4 2");
        assertThat(jdbc.queryForList("select concat(id, ' ', active + 0, ' ', session_version) from users order by id",
                String.class)).containsExactly("1 1 0", "2 1 0", "3 0 3", "4 1 0");
        assertThat(jdbc.queryForList("select user_id from refresh_tokens order by user_id", Long.class))
                .containsExactly(2L, 4L);
        assertThat(columns(jdbc, "users")).contains("notify_review_queues")
                .doesNotContain("notify_live_session_reminders", "notify_feedback", "notify_content_reviews");

        // Content in review is a draft; an archived class knows when it was archived.
        assertThat(jdbc.queryForList("""
                select concat(id, ' ', content_status, ' ', coalesce(status_before_archive, '-'), ' ',
                              coalesce(date_format(archived_at, '%Y-%m-%d'), '-'))
                from course_packages order by id
                """, String.class)).containsExactly(
                        "1 DRAFT - -",
                        "2 DRAFT - -",
                        "3 ARCHIVED DRAFT 2026-10-02",
                        "4 ARCHIVED PUBLISHED 2026-10-03",
                        "5 PUBLISHED - -");
        assertThat(columns(jdbc, "course_packages")).doesNotContain("teacher_id", "archived_by_user_id",
                "rejection_reason", "submitted_at", "pending_title", "revision_status");
        assertThat(jdbc.queryForList("select content_status from lessons order by id", String.class))
                .containsExactly("DRAFT", "PUBLISHED");
        assertThat(columns(jdbc, "lessons")).doesNotContain("teacher_id", "rejection_reason", "submitted_at");
        // A quiz allows hints unless its stage was Independent.
        assertThat(jdbc.queryForList("select concat(id, ' ', content_status, ' ', hints_allowed + 0) from quizzes order by id",
                String.class)).containsExactly("1 PUBLISHED 1", "2 PUBLISHED 0", "3 DRAFT 1");
        assertThat(columns(jdbc, "quizzes")).doesNotContain("teacher_id", "learning_stage");
        assertThat(jdbc.queryForObject("select ai_draft + 0 from quiz_questions where id = 1", Integer.class)).isZero();

        // The removed features' tables and the verification documents' upload records go; other uploads stay.
        assertThat(jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = database() and table_name in ('teacher_profiles', 'teacher_verification_documents',
                    'teacher_verification_reviews', 'live_classes', 'class_questions', 'teacher_feedback',
                    'class_content_events')
                """, String.class)).isEmpty();
        assertThat(jdbc.queryForList("select id from uploaded_files order by id", Long.class)).containsExactly(11L);

        // Notifications of the removed features and the learners' support notices go; the admins' stay.
        assertThat(jdbc.queryForList("select id from notifications order by id", Long.class))
                .containsExactly(5L, 6L, 11L, 12L);
        assertThat(jdbc.queryForList("select concat(id, ' ', feature_key) from ai_usage_logs order by id", String.class))
                .containsExactly("1 QUIZ_DRAFT", "2 CHAT_TUTOR");
        // A teacher's support request stays in the queue as it was.
        assertThat(jdbc.queryForObject("select requester_role from support_requests where id = 1", String.class))
                .isEqualTo("TEACHER");
    }

    /**
     * V4: the old paces become bands of daily study time and every plan saved before was started; the Practice quizzes
     * go with their runs, answers, mistakes and served hints while the other types' stay; the platform's sets earn the
     * XP of the UI rules and take their new names (an admin's set keeps its name, a class lesson's activity its XP); the
     * default AI notice no longer names a teacher.
     */
    @Test
    void v4MapsThePacesRemovesThePracticeQuizzesAndRecomputesTheXp() {
        JdbcTemplate jdbc = upgrade("3", MySqlMigrationUpgradeTests::insertV3PracticeRows, "4");

        assertThat(jdbc.queryForList("select concat(version, ' ', plan_pace, ' ', confirmed + 0) from learning_plans order by version",
                String.class)).containsExactly(
                        "1 UNDER_30_MIN 1", "2 UNDER_30_MIN 1", "3 UNDER_30_MIN 1", "4 MINUTES_30_TO_60 1");

        assertThat(jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = database() and table_name like 'practice_quiz%'
                """, String.class)).isEmpty();
        assertThat(jdbc.queryForList("select practice_type from practice_completions order by id", String.class))
                .containsExactly("LISTENING");
        assertThat(jdbc.queryForList("select item_ref from practice_answers order by id", String.class))
                .containsExactly("LISTENING#1#0");
        assertThat(jdbc.queryForList("select concat(item_ref, ' ', still_learning + 0) from learning_mistakes order by id",
                String.class)).containsExactly("LISTENING#1#0 0");
        assertThat(jdbc.queryForList("select item_ref from served_hints order by id", String.class))
                .containsExactly("PRACTICE#LISTENING#1#0");

        assertThat(jdbc.queryForObject("select xp_reward from listening_exercises where id = 1", Integer.class)).isEqualTo(9);
        assertThat(jdbc.queryForList("select concat(id, ' ', title, ' ', xp_reward) from typing_exercises order by id",
                String.class)).containsExactly("1 Numbers 6", "2 Mixed Words 16", "3 Admin Typing 4");
        assertThat(jdbc.queryForList("""
                select concat(id, ' ', xp_reward, ' ', coalesce(character_strokes, '-')) from tracing_exercises order by id
                """, String.class)).containsExactly("1 10 7,6", "2 15 -", "3 0 -");

        assertThat(jdbc.queryForObject("select setting_value from system_settings where setting_key = 'ai_disclaimer_text'",
                String.class)).isEqualTo("AI can make mistakes.");
    }

    @Test
    void v7RestoresOverflowedScoresOnlyWhenTheStoredAnswersExplainBothTotals() {
        JdbcTemplate jdbc = upgrade("6", legacy -> {
            legacy.update("""
                    insert into quiz_questions (id, quiz_id, points, sort_order, question_type, question_text, created_at, updated_at)
                    values (1, 1, 2000000000, 1, 'FILL_BLANK', 'A', now(), now()),
                           (2, 1, 2000000000, 2, 'FILL_BLANK', 'B', now(), now()),
                           (3, 1, 1200000000, 3, 'FILL_BLANK', 'C', now(), now())
                    """);
            legacy.update("""
                    insert into quiz_attempts (id, quiz_id, student_id, submitted, score, max_score, completed_at, created_at, updated_at)
                    values (1, 1, 1, 1, 905032704, 905032704, now(), now(), now()),
                           (2, 1, 2, 1, 1, 2, now(), now(), now())
                    """);
            legacy.update("""
                    insert into quiz_attempt_answers (quiz_attempt_id, question_id, correct, created_at, updated_at)
                    select a.id, q.id, 1, now(), now() from quiz_attempts a join quiz_questions q on q.quiz_id = a.quiz_id
                    """);
        }, "7");

        assertThat(jdbc.queryForList("select score from quiz_attempts order by id", Long.class))
                .containsExactly(5_200_000_000L, 1L);
        assertThat(jdbc.queryForList("select max_score from quiz_attempts order by id", Long.class))
                .containsExactly(5_200_000_000L, 2L);
    }

    @Test
    void v8KeepsCounterValuesAndAcceptsAnAlreadyAbsentIndex() {
        JdbcTemplate jdbc = upgrade("7", legacy -> {
            legacy.execute("alter table daily_streaks drop index idx_daily_streaks_student_id");
            legacy.update("""
                    insert into daily_streaks (student_id, current_streak, best_streak, created_at, updated_at)
                    values (11, 2, 3, now(), now())
                    """);
            legacy.update("""
                    insert into ai_usage_quota_counters
                        (user_id, day_count, minute_count, minute_started_at, usage_day, created_at, updated_at)
                    values (11, 4, 1, now(), current_date, now(), now())
                    """);
        }, "8");

        assertThat(jdbc.queryForObject("select best_streak from daily_streaks where student_id = 11", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("select day_count from ai_usage_quota_counters where user_id = 11", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void v9KeepsTheDeliveryTimeOfHintsWhoseRunWasNeverRecorded() {
        JdbcTemplate jdbc = upgrade("8", legacy -> legacy.update("""
                insert into served_hints (step, created_at, updated_at, student_id, item_ref)
                values (1, ?, ?, 1, 'LESSON#1#2')
                """, LAST_CHANGE, LAST_CHANGE), "9");

        assertThat(jdbc.queryForObject("""
                select created_at from served_hints where item_ref = 'LESSON#1#2' and run_id is null
                """, LocalDateTime.class)).isEqualTo(LAST_CHANGE);
    }

    @Test
    void v10DeduplicatesUnacceptedAdminInvitesAndKeepsAcceptedHistory() {
        LocalDateTime at = LAST_CHANGE;
        LocalDateTime expires = LAST_CHANGE.plusDays(7);
        JdbcTemplate jdbc = upgrade("9", legacy -> legacy.update("""
                insert into admin_invites (id, email, token_hash, expires_at, accepted_at, created_at, updated_at)
                values (1, 'dup@example.com', 'hash-accepted', ?, ?, ?, ?),
                       (2, 'dup@example.com', 'hash-older-open', ?, null, ?, ?),
                       (3, 'dup@example.com', 'hash-newer-open', ?, null, ?, ?)
                """, expires, at, at, at, expires, at, at, expires, at, at), "10");

        assertThat(jdbc.queryForList("select id from admin_invites order by id", Long.class))
                .containsExactly(1L, 3L);
        assertThat(jdbc.queryForList("select unaccepted_email from admin_invites order by id", String.class))
                .containsExactly(null, "dup@example.com");
    }

    @Test
    void v11CleansOrphanAndDuplicatePackageItemsAndNormalizesAcceptedInvites() {
        LocalDateTime at = LAST_CHANGE;
        LocalDateTime expires = LAST_CHANGE.plusDays(7);
        JdbcTemplate jdbc = upgrade("10", legacy -> {
            legacy.update("""
                    insert into course_packages (id, created_at, updated_at, title, content_status)
                    values (1, ?, ?, 'Travel Class', 'PUBLISHED')
                    """, at, at);
            legacy.update("""
                    insert into learning_modules (id, created_at, updated_at, title, content_status)
                    values (10, ?, ?, 'Unit 1', 'PUBLISHED'),
                           (11, ?, ?, 'Unit 2', 'PUBLISHED')
                    """, at, at, at, at);
            // Unit 10 twice (the lower sort order stays), a missing unit, and unit 11 twice at the same sort order
            // (the lower id stays).
            legacy.update("""
                    insert into package_items (id, created_at, updated_at, course_package_id, item_id, sort_order)
                    values (1, ?, ?, 1, 10, 2),
                           (2, ?, ?, 1, 10, 1),
                           (3, ?, ?, 1, 999, 3),
                           (4, ?, ?, 1, 11, 4),
                           (5, ?, ?, 1, 11, 4)
                    """, at, at, at, at, at, at, at, at, at, at);
            legacy.update("""
                    insert into admin_invites (id, email, token_hash, expires_at, accepted_at, cancelled_at, created_at, updated_at)
                    values (1, 'both@example.com', 'hash-both', ?, ?, ?, ?, ?)
                    """, expires, at, at, at, at);
            // Question types the entity can only map by their exact constant name.
            legacy.update("""
                    insert into quiz_questions (id, quiz_id, points, sort_order, question_type, question_text, created_at, updated_at)
                    values (1, 1, 1, 1, 'MULTIPLE_CHOICE', 'A', ?, ?),
                           (2, 1, 1, 2, 'multiple_choice', 'B', ?, ?),
                           (3, 1, 1, 3, ' TRUE_FALSE ', 'C', ?, ?),
                           (4, 1, 1, 4, 'Fill_Blank', 'D', ?, ?)
                    """, at, at, at, at, at, at, at, at);
        }, "11");

        assertThat(jdbc.queryForList("select id from package_items order by id", Long.class))
                .containsExactly(2L, 4L);
        assertThat(jdbc.queryForObject("select cancelled_at from admin_invites where id = 1", LocalDateTime.class))
                .isNull();
        assertThat(jdbc.queryForObject("select accepted_at from admin_invites where id = 1", LocalDateTime.class))
                .isEqualTo(at);
        assertThat(jdbc.queryForList("select question_type from quiz_questions order by id", String.class))
                .containsExactly("MULTIPLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK");
        // The check is case-sensitive although the column's collation is not: it admits only names the entity maps.
        assertThatThrownBy(() -> jdbc.update("""
                insert into quiz_questions (id, quiz_id, points, sort_order, question_type, question_text, created_at, updated_at)
                values (5, 1, 1, 5, 'true_false', 'E', now(), now())
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_quiz_questions_question_type");
        // The links now need an existing unit.
        assertThatThrownBy(() -> jdbc.update("""
                insert into package_items (id, created_at, updated_at, course_package_id, item_id, sort_order)
                values (6, now(), now(), 1, 998, 5)
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_package_items_module");
    }

    /** A left-prefix index is dropped only while the index that covers it is there (and can serve its foreign key). */
    @Test
    void v11DropsRedundantIndexesOnlyWhenTheirCoveringIndexExists() {
        JdbcTemplate jdbc = upgrade("10", legacy ->
                legacy.execute("alter table lessons drop index idx_lessons_module_position"), "11");

        assertThat(indexExists(jdbc, "lessons", "idx_lessons_module_id")).isTrue();
        assertThat(indexExists(jdbc, "quiz_attempts", "idx_quiz_attempts_student_id")).isFalse();
        assertThat(indexExists(jdbc, "quiz_attempts", "idx_quiz_attempts_student_quiz_submitted")).isTrue();
        assertThat(indexExists(jdbc, "package_items", "idx_package_items_package_id")).isFalse();
        assertThat(indexExists(jdbc, "package_items", "uk_package_items_package_item")).isTrue();
    }

    @Test
    void v12NormalizesAiEnumValuesEnforcesCaseSensitiveChecksAndTunesIndexes() {
        LocalDateTime at = LAST_CHANGE;
        JdbcTemplate jdbc = upgrade("11", legacy -> {
            legacy.update("""
                    insert into users (id, active, created_at, updated_at, display_name, email, password_hash)
                    values (1, b'1', ?, ?, 'Learner', 'v12-learner@example.com', 'x')
                    """, at, at);
            legacy.update("""
                    insert into ai_chat_sessions (id, user_id, feature_key, created_at, updated_at)
                    values (1, 1, 'CHAT_TUTOR', ?, ?)
                    """, at, at);
            // V1/V4 checks used utf8mb4_0900_ai_ci, so mixed-case enum values were accepted in V11.
            legacy.update("""
                    insert into ai_chat_messages (id, session_id, sender_role, message_text, created_at, updated_at)
                    values (1, 1, 'user', 'hello', ?, ?),
                           (2, 1, 'Assistant', 'hi', ?, ?)
                    """, at, at, at, at);
            legacy.update("""
                    insert into ai_usage_logs (id, user_id, feature_key, status, created_at, updated_at)
                    values (1, 1, 'CHAT_TUTOR', 'success', ?, ?),
                           (2, 1, 'CHAT_TUTOR', 'Failed', ?, ?)
                    """, at, at, at, at);
            legacy.update("""
                    insert into ai_response_reports
                        (id, student_user_id, feature_key, status, reason_category, ai_response_snapshot, reviewed_at, created_at, updated_at)
                    values (1, 1, 'CHAT_TUTOR', 'pending_review', 'incorrect_content', 'resp-1', null, ?, ?),
                           (2, 1, 'LEARNING_PLAN', 'Resolved', 'does_not_match_input', 'resp-2', ?, ?, ?)
                    """, at, at, at, at, at);
        }, "12");

        assertThat(jdbc.queryForList("select sender_role from ai_chat_messages order by id", String.class))
                .containsExactly("USER", "ASSISTANT");
        assertThat(jdbc.queryForList("select status from ai_usage_logs order by id", String.class))
                .containsExactly("SUCCESS", "FAILED");
        assertThat(jdbc.queryForList("select concat(status, ':', reason_category) from ai_response_reports order by id", String.class))
                .containsExactly("PENDING_REVIEW:INCORRECT_CONTENT", "RESOLVED:DOES_NOT_MATCH_INPUT");

        assertThatThrownBy(() -> jdbc.update("""
                insert into ai_chat_messages (id, session_id, sender_role, message_text, created_at, updated_at)
                values (3, 1, 'user', 'bad', now(), now())
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_ai_chat_messages_sender_role");
        assertThatThrownBy(() -> jdbc.update("""
                insert into ai_usage_logs (id, user_id, feature_key, status, created_at, updated_at)
                values (3, 1, 'CHAT_TUTOR', 'success', now(), now())
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_ai_usage_logs_status");
        assertThatThrownBy(() -> jdbc.update("""
                insert into ai_response_reports
                    (id, student_user_id, feature_key, status, reason_category, ai_response_snapshot, reviewed_at, created_at, updated_at)
                values (3, 1, 'CHAT_TUTOR', 'pending_review', 'OTHER', 'resp', null, now(), now())
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*chk_ai_response_reports_(status|review_consistency).*");
        assertThatThrownBy(() -> jdbc.update("""
                insert into ai_response_reports
                    (id, student_user_id, feature_key, status, reason_category, ai_response_snapshot, reviewed_at, created_at, updated_at)
                values (4, 1, 'CHAT_TUTOR', 'PENDING_REVIEW', 'other', 'resp', null, now(), now())
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_ai_response_reports_reason_category");

        assertThat(indexExists(jdbc, "support_requests", "idx_support_requests_status_updated")).isTrue();
        assertThat(indexExists(jdbc, "support_requests", "idx_support_requests_status_solved")).isTrue();
        assertThat(indexExists(jdbc, "support_requests", "idx_support_requests_requester_email")).isTrue();
        assertThat(indexExists(jdbc, "support_requests", "idx_support_requests_status_created")).isFalse();
        assertThat(indexExists(jdbc, "ai_usage_logs", "idx_ai_usage_logs_feature_status")).isTrue();
        assertThat(indexExists(jdbc, "ai_usage_logs", "idx_ai_usage_logs_feature_key")).isFalse();
        assertThat(indexExists(jdbc, "refresh_tokens", "idx_refresh_tokens_family_active")).isTrue();
        assertThat(indexExists(jdbc, "refresh_tokens", "idx_refresh_tokens_user_revoked")).isTrue();
        assertThat(indexExists(jdbc, "refresh_tokens", "idx_refresh_tokens_user_id")).isFalse();
    }

    @Test
    void v13DropsUnusedColumnsIndexesAndLegacyToneChecks() {
        LocalDateTime at = LAST_CHANGE;
        JdbcTemplate jdbc = upgrade("12", legacy -> {
            legacy.update("""
                    insert into users (id, active, created_at, updated_at, display_name, email, password_hash)
                    values (1, b'1', ?, ?, 'Mei', 'mei.v13@example.com', 'x')
                    """, at, at);
            legacy.update("""
                    insert into ai_practice_checks
                        (id, created_at, updated_at, student_user_id, check_type, target_text, score, headline)
                    values (1, ?, ?, 1, 'TONES', '你好', 75, 'Legacy tone score'),
                           (2, ?, ?, 1, 'RECORDING', '谢谢', 88, 'Clear pronunciation')
                    """, at, at, at, at);
            legacy.update("""
                    insert into ai_chat_sessions (id, created_at, updated_at, user_id, feature_key)
                    values (1, ?, ?, 1, 'CHAT_TUTOR')
                    """, at, at);
            legacy.update("""
                    insert into video_progress
                        (id, created_at, updated_at, student_id, lesson_id, recorded_video_id, version, completed, last_watched_at)
                    values (1, ?, ?, 1, 2, 3, 0, b'1', ?)
                    """, at, at, at);
        }, "13");

        assertThat(jdbc.queryForList("select check_type from ai_practice_checks order by id", String.class))
                .containsExactly("RECORDING");
        assertThatThrownBy(() -> jdbc.update("""
                insert into ai_practice_checks
                    (id, created_at, updated_at, student_user_id, check_type, target_text, score, headline)
                values (3, ?, ?, 1, 'TONES', '再见', 80, 'Rejected')
                """, at, at))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("check_type");
        assertThat(jdbc.update("""
                insert into ai_practice_checks
                    (id, created_at, updated_at, student_user_id, check_type, target_text, score, headline)
                values (4, ?, ?, 1, 'RECORDING', '再见', 84, 'Accepted')
                """, at, at)).isOne();

        assertThat(columns(jdbc, "learning_modules")).doesNotContain("content_status");
        assertThat(columns(jdbc, "ai_chat_sessions")).doesNotContain("feature_key");
        assertThat(columns(jdbc, "uploaded_files")).doesNotContain("active");
        assertThat(columns(jdbc, "video_progress")).doesNotContain("last_watched_at", "lesson_id");
        assertThat(columns(jdbc, "refresh_tokens")).doesNotContain("created_by_ip", "user_agent");

        assertThat(jdbc.queryForObject("select count(*) from ai_chat_sessions where id = 1", Long.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from video_progress where id = 1", Long.class)).isOne();
        assertThat(indexExists(jdbc, "ai_chat_sessions", "idx_ai_chat_sessions_user_id")).isTrue();
        assertThat(indexExists(jdbc, "ai_chat_sessions", "idx_ai_chat_sessions_feature_key")).isFalse();
        assertThat(indexExists(jdbc, "ai_chat_sessions", "idx_ai_chat_sessions_user_feature_created")).isFalse();
        assertThat(indexExists(jdbc, "refresh_tokens", "idx_refresh_tokens_family_id")).isFalse();
        assertThat(indexExists(jdbc, "refresh_tokens", "idx_refresh_tokens_expires_at")).isFalse();
        assertThat(indexExists(jdbc, "video_progress", "idx_video_progress_lesson_last_watched")).isFalse();
        assertThat(indexExists(jdbc, "learning_modules", "idx_learning_modules_status_updated")).isFalse();
        assertThat(indexExists(jdbc, "quizzes", "idx_quizzes_status_updated")).isFalse();
        assertThat(indexExists(jdbc, "lessons", "idx_lessons_status_created_at")).isFalse();
        assertThat(indexExists(jdbc, "audit_logs", "idx_audit_logs_request_id")).isFalse();
        assertThat(indexExists(jdbc, "audit_logs", "idx_audit_logs_actor_email_created")).isFalse();
    }

    @Test
    void v14EnforcesUniqueLessonRevisionAndCleansIndexes() {
        LocalDateTime at = LAST_CHANGE;
        JdbcTemplate jdbc = upgrade("13", legacy -> {
            legacy.update("""
                    insert into lessons (id, created_at, updated_at, title, content_status, unit_position, revision_of_lesson_id)
                    values (10, ?, ?, 'Published Lesson', 'PUBLISHED', 1, null),
                           (11, ?, ?, 'Second Published Lesson', 'PUBLISHED', 2, null),
                           (12, ?, ?, 'Working Copy', 'DRAFT', 1, 10)
                    """, at, at, at, at, at, at);
        }, "14");

        // Multiple NULL revision_of_lesson_id rows stay allowed, while a second revision for lesson 10 is rejected.
        assertThat(jdbc.update("""
                insert into lessons (id, created_at, updated_at, title, content_status, unit_position, revision_of_lesson_id)
                values (13, ?, ?, 'Third Published Lesson', 'PUBLISHED', 3, null)
                """, at, at)).isOne();
        assertThatThrownBy(() -> jdbc.update("""
                insert into lessons (id, created_at, updated_at, title, content_status, unit_position, revision_of_lesson_id)
                values (14, ?, ?, 'Duplicate Revision', 'DRAFT', 1, 10)
                """, at, at))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_lessons_revision_of");

        assertThat(indexExists(jdbc, "lessons", "uk_lessons_revision_of")).isTrue();
        assertThat(indexExists(jdbc, "lessons", "idx_lessons_revision_of")).isFalse();
        assertThat(indexColumns(jdbc, "drag_drop_items", "idx_drag_drop_items_activity_order"))
                .containsExactly("drag_drop_activity_id", "sort_order", "created_at");
        assertThat(indexExists(jdbc, "drag_drop_items", "idx_drag_drop_items_activity_id")).isFalse();
        assertThat(indexColumns(jdbc, "matching_game_items", "idx_matching_game_items_game_order"))
                .containsExactly("matching_game_id", "sort_order", "created_at");
        assertThat(indexExists(jdbc, "matching_game_items", "idx_matching_game_items_game_id")).isFalse();
        assertThat(indexColumns(jdbc, "quiz_questions", "idx_quiz_questions_quiz_order"))
                .containsExactly("quiz_id", "sort_order", "created_at");
        assertThat(indexExists(jdbc, "quiz_questions", "idx_quiz_questions_quiz_id")).isFalse();
        assertThat(indexColumns(jdbc, "quiz_options", "idx_quiz_options_question_order"))
                .containsExactly("question_id", "sort_order", "created_at");
        assertThat(indexExists(jdbc, "quiz_options", "idx_quiz_options_question_id")).isFalse();
        assertThat(indexColumns(jdbc, "email_change_tokens", "idx_email_change_tokens_user_open"))
                .containsExactly("user_id", "used_at", "expires_at");
        assertThat(indexExists(jdbc, "email_change_tokens", "idx_email_change_tokens_user_id")).isFalse();
        assertThat(indexExists(jdbc, "flashcards", "idx_flashcards_hsk_level")).isFalse();
        assertThat(indexExists(jdbc, "matching_games", "idx_matching_games_hsk_level")).isFalse();
        assertThat(indexExists(jdbc, "listening_exercises", "idx_listening_exercises_hsk_level")).isFalse();
        assertThat(indexExists(jdbc, "tracing_exercises", "idx_tracing_exercises_hsk_level")).isFalse();
        assertThat(indexExists(jdbc, "drag_drop_activities", "idx_drag_drop_activities_hsk_level")).isFalse();
        assertThat(indexExists(jdbc, "typing_exercises", "idx_typing_exercises_hsk_level")).isFalse();
    }

    private static boolean indexExists(JdbcTemplate jdbc, String table, String index) {
        return !indexColumns(jdbc, table, index).isEmpty();
    }

    private static List<String> indexColumns(JdbcTemplate jdbc, String table, String index) {
        return jdbc.queryForList("""
                select column_name from information_schema.statistics
                where table_schema = database() and table_name = ? and index_name = ?
                order by seq_in_index asc
                """, String.class, table, index);
    }

    /** A database V1 built, holding the given rows, then migrated to V2. */
    private JdbcTemplate upgrade(Consumer<JdbcTemplate> legacyRows) {
        return upgrade("1", legacyRows, "2");
    }

    /** A database migrated to {@code from}, holding the given rows, then migrated to {@code to}. */
    private JdbcTemplate upgrade(String from, Consumer<JdbcTemplate> legacyRows, String to) {
        database = MySqlSchema.recreate("migration", "_upgrade_check");
        Flyway.configure().dataSource(database.dataSource()).target(from).load().migrate();
        JdbcTemplate jdbc = database.jdbc();
        // Foreign keys are off because only the columns the next migration reads matter here.
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        legacyRows.accept(jdbc);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");

        Flyway flyway = Flyway.configure().dataSource(database.dataSource()).target(to).load();
        flyway.migrate();
        assertThat(Arrays.stream(flyway.info().applied()).map(migration -> migration.getVersion().getVersion()))
                .containsExactlyElementsOf(EXPECTED_VERSIONS.subList(0, EXPECTED_VERSIONS.indexOf(to) + 1));
        return jdbc;
    }

    /** Rows of the tables V2 changes. */
    private static void insertLegacyRows(JdbcTemplate jdbc) {
        LocalDateTime at = LAST_CHANGE;
        // The admins who archived (the events' actors exist, as their foreign key requires in a real database).
        jdbc.update("""
                insert into users (id, active, created_at, updated_at, display_name, email, password_hash)
                values (5, b'1', ?, ?, 'Admin A', 'admin.a@example.com', 'x'),
                       (7, b'1', ?, ?, 'Admin B', 'admin.b@example.com', 'x')
                """, at, at, at, at);
        jdbc.update("""
                insert into course_packages (id, created_at, updated_at, teacher_id, title, content_status)
                values (1, ?, ?, 1, 'Archived class', 'ARCHIVED'), (2, ?, ?, 1, 'Restored class', 'PUBLISHED')
                """, at, at, at, at);
        LocalDateTime nineDaysBefore = LAST_CHANGE.minusDays(9);
        jdbc.update("""
                insert into class_content_events (created_at, updated_at, course_package_id, actor_user_id, event_type, subject_title)
                values (?, ?, 1, 5, 'CLASS_ARCHIVED', 'Archived class'),
                       (?, ?, 1, 7, 'CLASS_ARCHIVED', 'Archived class'),
                       (?, ?, 2, 5, 'CLASS_ARCHIVED', 'Restored class')
                """, nineDaysBefore, nineDaysBefore, at, at, at, at);
        jdbc.update("""
                insert into lesson_progress (id, completed, created_at, updated_at, lesson_id, student_id)
                values (1, b'1', ?, ?, 1, 1), (2, b'0', ?, ?, 2, 1)
                """, at, at, at, at);
        jdbc.update("""
                insert into ai_practice_checks
                    (id, created_at, updated_at, student_user_id, check_type, target_text, score, headline, shape_score)
                values (1, ?, ?, 1, 'HANDWRITING', '你好', 80, 'Neat strokes', 64)
                """, at, at);
        jdbc.update("""
                insert into student_profiles (id, total_points, created_at, updated_at, user_id)
                values (1, 0, ?, ?, 1)
                """, at, at);
    }

    /**
     * A V2 database of the teacher era: an admin (1), a learner (2), a teacher (3) and a teacher who also learns (4),
     * classes and lessons in every review state, quizzes of every stage, the removed features' notifications and rows.
     */
    private static void insertTeacherEraRows(JdbcTemplate jdbc) {
        LocalDateTime at = LAST_CHANGE;
        LocalDateTime expiresAt = LAST_CHANGE.plusDays(30);
        LocalDateTime twoDaysBefore = LAST_CHANGE.minusDays(2);
        LocalDateTime oneDayBefore = LAST_CHANGE.minusDays(1);
        jdbc.update("""
                insert into roles (id, created_at, updated_at, name)
                values (1, ?, ?, 'ADMIN'), (2, ?, ?, 'STUDENT'), (3, ?, ?, 'TEACHER')
                """, at, at, at, at, at, at);
        jdbc.update("""
                insert into users (id, active, created_at, updated_at, display_name, email, password_hash, session_version)
                values (1, b'1', ?, ?, 'Admin', 'admin@example.com', 'x', 0),
                       (2, b'1', ?, ?, 'Learner', 'learner@example.com', 'x', 0),
                       (3, b'1', ?, ?, 'Teacher', 'teacher@example.com', 'x', 2),
                       (4, b'1', ?, ?, 'Teacher and learner', 'both@example.com', 'x', 0)
                """, at, at, at, at, at, at, at, at);
        jdbc.update("insert into user_roles (user_id, role_id) values (1, 1), (2, 2), (3, 3), (4, 3), (4, 2)");
        jdbc.update("""
                insert into refresh_tokens (id, created_at, updated_at, expires_at, user_id, family_id, token_hash)
                values (1, ?, ?, ?, 2, 'family-2', 'hash-2'), (2, ?, ?, ?, 3, 'family-3', 'hash-3'),
                       (3, ?, ?, ?, 4, 'family-4', 'hash-4')
                """, at, at, expiresAt, at, at, expiresAt, at, at, expiresAt);
        jdbc.update("""
                insert into teacher_profiles (id, created_at, updated_at, user_id, teacher_status)
                values (1, ?, ?, 3, 'APPROVED'), (2, ?, ?, 4, 'APPROVED')
                """, at, at, at, at);
        jdbc.update("""
                insert into uploaded_files (id, active, created_at, updated_at, file_size, content_type, file_name, stored_file_name)
                values (10, b'1', ?, ?, 100, 'application/pdf', 'certificate.pdf', 'stored-certificate.pdf'),
                       (11, b'1', ?, ?, 100, 'image/png', 'screen.png', 'stored-screen.png')
                """, at, at, at, at);
        jdbc.update("""
                insert into teacher_verification_documents
                    (id, current_version, created_at, updated_at, submitted_at, teacher_id, uploaded_file_id, document_type)
                values (1, b'1', ?, ?, ?, 1, 10, 'CERTIFICATE')
                """, at, at, at);
        jdbc.update("""
                insert into course_packages (id, created_at, updated_at, teacher_id, title, content_status, status_before_archive)
                values (1, ?, ?, 1, 'In review', 'SUBMITTED', null),
                       (2, ?, ?, 1, 'Sent back', 'REJECTED', null),
                       (3, ?, ?, 1, 'Archived while sent back', 'ARCHIVED', 'REJECTED'),
                       (4, ?, ?, 1, 'Archived without history', 'ARCHIVED', 'PUBLISHED'),
                       (5, ?, ?, 1, 'Live', 'PUBLISHED', null)
                """, at, at, at, at, at, at, at, at, at, at);
        jdbc.update("""
                insert into class_content_events (created_at, updated_at, course_package_id, actor_user_id, event_type, subject_title)
                values (?, ?, 3, 3, 'CLASS_ARCHIVED', 'Archived while sent back'),
                       (?, ?, 3, 3, 'CLASS_ARCHIVED', 'Archived while sent back')
                """, twoDaysBefore, twoDaysBefore, oneDayBefore, oneDayBefore);
        jdbc.update("""
                insert into lessons (id, created_at, updated_at, teacher_id, title, content_status)
                values (1, ?, ?, 1, 'Lesson in review', 'SUBMITTED'), (2, ?, ?, 1, 'Live lesson', 'PUBLISHED')
                """, at, at, at, at);
        jdbc.update("""
                insert into quizzes (id, created_at, updated_at, lesson_id, teacher_id, title, content_status, learning_stage)
                values (1, ?, ?, 2, 1, 'Guided quiz', 'PUBLISHED', 'GUIDED'),
                       (2, ?, ?, 2, 1, 'Independent quiz', 'PUBLISHED', 'INDEPENDENT'),
                       (3, ?, ?, 1, 1, 'Quiz in review', 'SUBMITTED', null)
                """, at, at, at, at, at, at);
        jdbc.update("""
                insert into quiz_questions (id, created_at, updated_at, quiz_id, points, sort_order, question_type, question_text)
                values (1, ?, ?, 1, 5, 1, 'MULTIPLE_CHOICE', 'Which word means hello?')
                """, at, at);
        jdbc.update("""
                insert into notifications (id, created_at, updated_at, user_id, notification_type, title, message,
                                           target_path, notification_status)
                values (1, ?, ?, 2, 'TEACHER_FEEDBACK', 'New feedback', 'Feedback', '/student/classes/5/feedback', 'UNREAD'),
                       (2, ?, ?, 2, 'LIVE_CLASS_REMINDER', 'Live session starts soon', 'Soon', '/student/classes', 'UNREAD'),
                       (3, ?, ?, 3, 'TEACHER_APPROVAL', 'Approved', 'Approved', '/teacher', 'UNREAD'),
                       (4, ?, ?, 2, 'SUPPORT_REQUEST', 'Support replied', 'Reply', '/student/support?request=1', 'UNREAD'),
                       (5, ?, ?, 1, 'SUPPORT_REQUEST', 'New support request', 'Sent', '/admin/support?request=1', 'UNREAD'),
                       (6, ?, ?, 2, 'SYSTEM', 'Your account is active again', 'Welcome back', '/student', 'UNREAD'),
                       (7, ?, ?, 1, 'CONTENT_REVIEW', 'Class submitted', 'Review', '/admin/content', 'UNREAD'),
                       (8, ?, ?, 2, 'MODERATION_ACTION', 'Moderated', 'Moderated', '/student', 'UNREAD'),
                       (9, ?, ?, 3, 'CLASS_ENROLMENT', 'New student', 'Learner joined Live.', '/teacher/students', 'UNREAD'),
                       (10, ?, ?, 2, 'CLASS_QUESTION', 'Answered', 'Answered', '/student/classes/5/feedback', 'UNREAD'),
                       (11, ?, ?, 2, 'AI_REPORT_REVIEW', 'An admin replied', 'Resolved', '/student/ai', 'UNREAD'),
                       (12, ?, ?, 1, 'ADMIN_TASK', 'AI report needs review', 'Report', '/admin/ai', 'UNREAD'),
                       (13, ?, ?, 3, 'SUPPORT_REQUEST', 'Support replied', 'Reply', '/teacher/support?request=1', 'UNREAD')
                """, at, at, at, at, at, at, at, at, at, at, at, at, at, at, at, at, at, at, at, at, at, at, at, at, at, at);
        jdbc.update("""
                insert into ai_usage_logs (id, created_at, updated_at, user_id, status, feature_key)
                values (1, ?, ?, 3, 'SUCCESS', 'TEACHER_DRAFT'), (2, ?, ?, 2, 'SUCCESS', 'CHAT_TUTOR')
                """, at, at, at, at);
        jdbc.update("""
                insert into support_requests (id, created_at, updated_at, request_number, requester_user_id, requester_email,
                                              subject, requester_role, request_topic, request_status)
                values (1, ?, ?, 1000, 3, 'teacher@example.com', 'My certificate', 'TEACHER', 'VERIFICATION', 'OPEN')
                """, at, at);
    }

    /**
     * A V3 database: one learner's plans at each old pace, a Practice quiz and a listening set each with a finished run,
     * an answer, a mistake and a served hint, the platform's typing and tracing sets beside an admin's and a class
     * lesson's, and the old default AI notice.
     */
    private static void insertV3PracticeRows(JdbcTemplate jdbc) {
        LocalDateTime at = LAST_CHANGE;
        List<String> oldPaces = List.of("LIGHT", "REGULAR", "SERIOUS", "INTENSE");
        for (int i = 0; i < oldPaces.size(); i++) {
            jdbc.update("""
                    insert into learning_plans (created_at, updated_at, student_id, version, active, plan_reason, goal_picks,
                        plan_pace, focus_skills, self_level, placed_level, head_start, known_skills, new_skills,
                        level_check_answered, target_level, ai_assisted, starts_on, total_weeks, content_weeks)
                    values (?, ?, 1, ?, ?, 'TRAVEL', 'ORDER_FOOD', ?, '', 'NEW_TO_MANDARIN', 'NEW_TO_MANDARIN', b'0', '', '',
                        0, 'BEGINNER_1', b'0', '2026-10-01', 12, 10)
                    """, at, at, i + 1, i == oldPaces.size() - 1, oldPaces.get(i));
        }
        jdbc.update("""
                insert into practice_quizzes (id, created_at, updated_at, title, content_status, xp_reward)
                values (1, ?, ?, 'Greetings Quiz', 'PUBLISHED', 20)
                """, at, at);
        jdbc.update("""
                insert into practice_completions (id, created_at, updated_at, student_id, practice_type, activity_id,
                    correct_count, item_count)
                values (1, ?, ?, 1, 'QUIZZES', 1, 4, 5), (2, ?, ?, 1, 'LISTENING', 1, 2, 3)
                """, at, at, at, at);
        jdbc.update("""
                insert into practice_answers (id, correct, hint_steps, created_at, updated_at, student_id, item_ref, practice_type)
                values (1, b'0', 0, ?, ?, 1, 'QUIZZES#1#0', 'QUIZZES'), (2, b'0', 1, ?, ?, 1, 'LISTENING#1#0', 'LISTENING')
                """, at, at, at, at);
        jdbc.update("""
                insert into learning_mistakes (id, created_at, updated_at, student_id, mistake_source, item_ref, source_title,
                    answer_kind, choices, answer, wrong_count, last_wrong_at)
                values (1, ?, ?, 1, 'PRACTICE', 'QUIZZES#1#0', 'Greetings Quiz', 'CHOICE', '[]', '你好', 1, ?),
                       (2, ?, ?, 1, 'PRACTICE', 'LISTENING#1#0', 'At the Café', 'CHOICE', '[]', '咖啡', 1, ?)
                """, at, at, at, at, at, at);
        jdbc.update("""
                insert into served_hints (id, step, created_at, updated_at, student_id, item_ref)
                values (1, 1, ?, ?, 1, 'PRACTICE#QUIZZES#1#0'), (2, 1, ?, ?, 1, 'PRACTICE#LISTENING#1#0')
                """, at, at, at, at);
        jdbc.update("""
                insert into listening_exercises (id, created_at, updated_at, title, content_status, xp_reward, question_payload)
                values (1, ?, ?, 'At the Café', 'PUBLISHED', 10, '{"questions": [{}, {}, {}]}')
                """, at, at);
        jdbc.update("""
                insert into typing_exercises (id, created_at, updated_at, created_by_user_id, title, content_status, xp_reward,
                    word_payload)
                values (1, ?, ?, null, 'Numbers Typing', 'PUBLISHED', 15, '{"words": [{}, {}, {}]}'),
                       (2, ?, ?, null, 'Mixed Review', 'PUBLISHED', 15, '{"words": [{}, {}, {}, {}, {}, {}, {}, {}]}'),
                       (3, ?, ?, 1, 'Admin Typing', 'DRAFT', 15, '{"words": [{}, {}]}')
                """, at, at, at, at, at, at);
        jdbc.update("""
                insert into tracing_exercises (id, created_at, updated_at, created_by_user_id, lesson_id, title, content_status,
                    target_character, stroke_count, hsk_level, xp_reward)
                values (1, ?, ?, null, null, 'Write 你好', 'PUBLISHED', '你好', 13, 1, 20),
                       (2, ?, ?, 1, null, 'My 你好', 'DRAFT', '你好', 13, 2, 20),
                       (3, ?, ?, null, 5, 'Lesson 你好', 'PUBLISHED', '你好', 13, 1, 0)
                """, at, at, at, at, at, at);
        jdbc.update("""
                insert into system_settings (created_at, updated_at, setting_key, setting_value)
                values (?, ?, 'ai_disclaimer_text', 'AI responses may contain errors and do not replace your teacher.')
                """, at, at);
    }

    private static List<String> columns(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("""
                select column_name from information_schema.columns
                where table_schema = database() and table_name = ?
                """, String.class, table);
    }
}
