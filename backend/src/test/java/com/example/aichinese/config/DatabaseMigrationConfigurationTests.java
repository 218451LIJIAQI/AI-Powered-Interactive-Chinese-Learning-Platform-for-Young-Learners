package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import com.example.aichinese.support.FlywayMigrationFiles;

/**
 * Pins the MySQL migration contract and explicitly documents the separate H2
 * schema strategies used by the demo and desktop profiles.
 */
class DatabaseMigrationConfigurationTests {

    private static final Path APPLICATION_YML = Path.of("src/main/resources/application.yml");
    private static final Path APPLICATION_LOCAL_YML = Path.of("src/main/resources/application-local.yml");
    private static final Path APPLICATION_DEMO_YML = Path.of("src/main/resources/application-demo.yml");
    private static final Path APPLICATION_DESKTOP_YML = Path.of("src/main/resources/application-desktop.yml");
    private static final Path MIGRATION_DIRECTORY = FlywayMigrationFiles.MIGRATION_DIRECTORY;
    private static final Path BASELINE_MIGRATION = MIGRATION_DIRECTORY.resolve("V1__consolidated_schema.sql");
    private static final Path REMOVE_COMMERCE_MIGRATION = MIGRATION_DIRECTORY.resolve("V2__remove_commerce.sql");
    private static final Path YOUNG_ADULT_ALIGNMENT_MIGRATION =
            MIGRATION_DIRECTORY.resolve("V3__young_adult_alignment.sql");
    private static final Path CONTENT_REVIEW_MIGRATION =
            MIGRATION_DIRECTORY.resolve("V4__content_review_workflow.sql");
    private static final Path LEARNER_EXPERIENCE_MIGRATION =
            MIGRATION_DIRECTORY.resolve("V5__learner_experience_and_integrity.sql");
    private static final Path BADGE_ALIGNMENT_MIGRATION = MIGRATION_DIRECTORY.resolve("V6__badge_alignment.sql");
    private static final Path SCHEMA_CLEANUP_MIGRATION = MIGRATION_DIRECTORY.resolve("V9__schema_cleanup.sql");
    private static final Path AI_HELPER_MIGRATION = MIGRATION_DIRECTORY.resolve("V13__ai_helper_alignment.sql");
    private static final List<String> REVIEWABLE_ACTIVITY_TABLES = List.of(
            "quizzes", "flashcards", "matching_games", "listening_exercises",
            "tracing_exercises", "drag_drop_activities", "story_activities");
    private static final List<String> COMMERCE_TABLES = List.of(
            "teacher_earnings",
            "refund_requests",
            "payment_entitlements",
            "payment_records",
            "order_items",
            "orders",
            "cart_items",
            "carts",
            "payout_requests");
    private static final Path ENV_EXAMPLE = Path.of(".env.example");
    private static final Path DOCKER_COMPOSE = Path.of("docker-compose.yml");

    @Test
    void runtimeProfilesUseFlywayWithHibernateValidationOnly() throws IOException {
        String application = Files.readString(APPLICATION_YML);
        String local = Files.readString(APPLICATION_LOCAL_YML);
        String demo = Files.readString(APPLICATION_DEMO_YML);

        assertSchemaStrategy(APPLICATION_YML, "validate", true);
        assertThat(application)
                .contains(
                        "username: ${DB_USERNAME:}",
                        "password: ${DB_PASSWORD:}",
                        "connectionTimeZone=Asia/Singapore",
                        "useUnicode=true",
                        "characterEncoding=utf8")
                .doesNotContain(
                        "username: ${DB_USERNAME:root}",
                        "password: ${DB_PASSWORD:root}",
                        "ddl-auto: update",
                        "payment:",
                        "PAYMENT_",
                        "STRIPE_");

        assertThat(local)
                .contains(
                        "on-profile: local",
                        "username: ${DB_USERNAME:aichinese_app}",
                        "password: ${DB_PASSWORD:replace-with-a-strong-application-db-password}",
                        "address: 127.0.0.1",
                        "secret: ${JWT_SECRET:LocalComposeOnlyJwtSecret1234567890AB}")
                .doesNotContain("payment:", "PAYMENT_", "STRIPE_", "sk_test_", "sk_live_");

        assertThat(demo)
                .contains(
                        "on-profile: demo",
                        "jdbc:h2:mem:aichinese-demo")
                .doesNotContain("payment:", "mock-success-enabled");
        assertSchemaStrategy(APPLICATION_DEMO_YML, "create-drop", false);
    }

    @Test
    void localDatabaseConfigurationUsesApplicationUserAndUtf8mb4() throws IOException {
        String env = Files.readString(ENV_EXAMPLE);
        String compose = Files.readString(DOCKER_COMPOSE);

        assertThat(env)
                .contains("DB_USERNAME=aichinese_app", "DB_ROOT_PASSWORD=")
                .doesNotContain("DB_USERNAME=root", "DB_PASSWORD=root", "PAYMENT_", "STRIPE_");

        assertThat(compose)
                .contains(
                        "MYSQL_USER: ${DB_USERNAME:-aichinese_app}",
                        "MYSQL_PASSWORD: ${DB_PASSWORD:-replace-with-a-strong-application-db-password}",
                        "MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD:-replace-with-a-strong-root-db-password}",
                        "--character-set-server=utf8mb4",
                        "MYSQL_PWD=\"$${MYSQL_PASSWORD}\" mysqladmin ping")
                .doesNotContain(
                        "-p${DB_PASSWORD",
                        "-p$${MYSQL_PASSWORD}",
                        "MYSQL_ROOT_HOST",
                        "MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-root}");
    }

    @Test
    void migrationsAreVersionedFromTheUntouchedBaseline() throws IOException {
        List<Path> migrations = FlywayMigrationFiles.inVersionOrder();

        assertThat(migrations.get(0)).isEqualTo(BASELINE_MIGRATION);
        assertThat(migrations.stream().map(FlywayMigrationFiles::version).toList())
                .as("Flyway versions run 1..N without gaps or duplicates")
                .isEqualTo(IntStream.rangeClosed(1, migrations.size()).boxed().toList());
    }

    @Test
    void youngAdultAlignmentMigrationDropsAgeBandsAndRenamesReportReason() throws IOException {
        String migration = Files.readString(YOUNG_ADULT_ALIGNMENT_MIGRATION).toLowerCase(Locale.ROOT);

        for (String table : List.of("flashcards", "matching_games", "listening_exercises",
                "tracing_exercises", "drag_drop_activities", "story_activities")) {
            int dropIndex = migration.indexOf("alter table " + table + " drop index idx_" + table + "_age_band");
            int dropColumn = migration.indexOf("alter table " + table + " drop column age_band");
            assertThat(dropIndex).as("V3 must drop the age_band index of " + table).isPositive();
            assertThat(dropColumn).as("V3 must drop the age_band column of " + table).isGreaterThan(dropIndex);
        }
        int dropCheck = migration.indexOf("drop check chk_ai_response_reports_reason_category");
        int remap = migration.indexOf("set reason_category = 'inappropriate_content'");
        int addCheck = migration.indexOf("add constraint chk_ai_response_reports_reason_category");
        assertThat(dropCheck).isPositive();
        assertThat(remap).isGreaterThan(dropCheck);
        assertThat(addCheck).isGreaterThan(remap);
        assertThat(migration)
                .contains("for young adults in malaysia")
                .contains("set preferred_language = 'zh-cn'");
        // The retired settings are deleted, not inserted.
        int deleteSettings = migration.indexOf("delete from system_settings");
        assertThat(deleteSettings).isPositive();
        assertThat(migration.indexOf("'admin_email_notifications_enabled'", deleteSettings)).isPositive();
        assertThat(migration.indexOf("'strict_content_filtering_enabled'", deleteSettings)).isPositive();
    }

    @Test
    void contentReviewMigrationAddsLearningStagesReasonsAndRetiresApproved() throws IOException {
        String migration = Files.readString(CONTENT_REVIEW_MIGRATION).toLowerCase(Locale.ROOT);

        for (String table : REVIEWABLE_ACTIVITY_TABLES) {
            assertThat(migration)
                    .as("V4 must add learning_stage to " + table)
                    .contains("alter table " + table
                            + " add column learning_stage enum (''guided'',''independent'',''scaffolded'') null");
            assertThat(migration)
                    .as("V4 must add rejection_reason to " + table)
                    .contains("alter table " + table + " add column rejection_reason varchar(1000) null");
        }
        for (String table : List.of("course_packages", "learning_modules", "lessons", "quizzes", "flashcards",
                "matching_games", "listening_exercises", "tracing_exercises", "drag_drop_activities", "story_activities")) {
            assertThat(migration)
                    .as("V4 must retire APPROVED rows of " + table)
                    .contains("update " + table + " set content_status = 'archived' where content_status = 'approved';");
            assertThat(migration)
                    .as("V4 must add the review-queue index of " + table)
                    .contains("create index idx_" + table + "_status_updated on " + table + " (content_status, updated_at)");
        }
        assertThat(migration)
                .contains("not exists (select 1 from quiz_questions qq where qq.quiz_id = quizzes.id)")
                .doesNotContain("drop column", "drop table");
    }

    @Test
    void learnerExperienceMigrationAddsPracticeSubmissionsHintsAndIntegrityConstraints() throws IOException {
        String migration = Files.readString(LEARNER_EXPERIENCE_MIGRATION).toLowerCase(Locale.ROOT);

        assertThat(migration)
                .contains(
                        "create table if not exists practice_submissions (",
                        "constraint fk_practice_submissions_student foreign key (student_id) references student_profiles (id)",
                        "constraint fk_practice_submissions_lesson foreign key (lesson_id) references lessons (id)",
                        "constraint fk_practice_submissions_uploaded_file foreign key (uploaded_file_id) references uploaded_files (id)",
                        "alter table teacher_feedback add column practice_submission_id bigint null",
                        "add constraint fk_teacher_feedback_practice_submission foreign key (practice_submission_id) references practice_submissions (id)",
                        "alter table quiz_questions add column hint varchar(1000) null",
                        "alter table quiz_questions modify column explanation varchar(2000) null",
                        "alter table quiz_attempt_answers add column hint_used bit not null default 0",
                        "alter table quiz_attempt_answers add column selected_option_id bigint null",
                        "add constraint uk_quiz_attempt_answers_attempt_question unique (quiz_attempt_id, question_id)",
                        "add constraint fk_student_quiz_progress_quiz foreign key (quiz_id) references quizzes (id)",
                        "add constraint fk_ai_chat_sessions_related_lesson foreign key (related_lesson_id) references lessons (id)",
                        "drop table if exists student_progress;",
                        "alter table live_class_enrolments drop index uk_live_class_enrolments_live_class_student",
                        "alter table story_questions add column correct_option_index integer null",
                        "alter table lesson_progress add column content_viewed_at datetime(6) null",
                        "alter table lessons modify column teacher_id bigint not null",
                        "alter table quizzes modify column lesson_id bigint not null",
                        "alter database character set utf8mb4 collate utf8mb4_0900_ai_ci;",
                        "alter table users convert to character set utf8mb4 collate utf8mb4_0900_ai_ci")
                .doesNotContain("drop table if exists quiz_attempt_answers", "drop index uk_live_class_enrolments_active_student");
        // Orphaned chat lesson ids are cleared before the foreign key is added.
        assertThat(migration.indexOf("set s.related_lesson_id = null"))
                .isBetween(0, migration.indexOf("add constraint fk_ai_chat_sessions_related_lesson"));
    }

    @Test
    void badgeAlignmentMigrationSeedsExactlyTheAwardedBadges() throws IOException {
        String migration = Files.readString(BADGE_ALIGNMENT_MIGRATION);

        // V6 aligned the five badges awarded at the time; V11 renames Point Starter and DataSeeder adds the rest.
        for (String badge : List.of("First Steps", "Quiz Star", "Lesson Finisher", "Streak Explorer", "Point Starter")) {
            assertThat(migration)
                    .as("V6 must insert and align " + badge)
                    .contains("where not exists (select 1 from badges b where b.name = '" + badge + "');");
        }
        assertThat(migration.toLowerCase(Locale.ROOT))
                .contains("not exists (select 1 from student_badges sb where sb.badge_id = badges.id)")
                .doesNotContain("drop table", "drop column", "alter table");
    }

    @Test
    void removeCommerceMigrationDropsCommerceSchemaInDependencyOrder() throws IOException {
        String migration = Files.readString(REMOVE_COMMERCE_MIGRATION).toLowerCase(Locale.ROOT);

        int previousDrop = -1;
        for (String table : COMMERCE_TABLES) {
            int dropIndex = migration.indexOf("drop table if exists " + table + ";");
            assertThat(dropIndex).as("V2 must drop commerce table " + table).isPositive();
            assertThat(dropIndex).as("commerce tables must be dropped dependants first: " + table).isGreaterThan(previousDrop);
            previousDrop = dropIndex;
        }
        assertThat(migration.indexOf("drop foreign key fkh0ecr76qq1quwldf7lapak879"))
                .as("foreign keys between commerce tables are dropped before the tables")
                .isBetween(0, migration.indexOf("drop table if exists teacher_earnings;"));

        for (String table : List.of("learning_modules", "lessons", "course_packages")) {
            int dropCheck = migration.indexOf("alter table " + table + " drop check chk_" + table + "_access_price");
            int dropPrice = migration.indexOf("alter table " + table + " drop column price");
            int dropAccessType = migration.indexOf("alter table " + table + " drop column access_type");
            assertThat(dropCheck).as("V2 must drop the access/price check on " + table).isPositive();
            assertThat(dropPrice).as("price check is dropped before the price column on " + table).isGreaterThan(dropCheck);
            assertThat(dropAccessType).as("check is dropped before access_type on " + table).isGreaterThan(dropCheck);
        }

        assertThat(migration)
                .contains(
                        "delete from system_settings",
                        "'default_currency', 'platform_commission_rate'",
                        "set notification_type = 'system'",
                        "modify column notification_type enum (")
                .doesNotContain("drop column learning_type", "drop table if exists course_packages");
        assertThat(migration.indexOf("set notification_type = 'system'"))
                .as("removed notification types are remapped before the enum is narrowed")
                .isLessThan(migration.indexOf("modify column notification_type enum ("));
    }

    @Test
    void combinedMigrationsLeaveNoCommerceTablesBehind() throws IOException {
        String migrations = FlywayMigrationFiles.combined().toLowerCase(Locale.ROOT);

        for (String table : COMMERCE_TABLES) {
            assertThat(migrations.lastIndexOf("drop table if exists " + table + ";"))
                    .as("the last statement touching " + table + " must be its drop")
                    .isGreaterThan(migrations.lastIndexOf("create table " + table + " ("));
        }
    }

    @Test
    void desktopProfileKeepsDemoDataInAFile() throws IOException {
        String desktop = Files.readString(APPLICATION_DESKTOP_YML);

        assertThat(desktop).contains(
                "on-profile: desktop",
                "jdbc:h2:file:${FYP_DATA_DIR:./.local/data}/aichinese");
        assertSchemaStrategy(APPLICATION_DESKTOP_YML, "update", false);
    }

    @Test
    void baselineEnforcesOperationalUniquenessPrivacyAndHotPathIndexes() throws IOException {
        String migration = Files.readString(BASELINE_MIGRATION);

        assertThat(migration)
                .contains(
                        "Baseline blocked: duplicate lesson progress tuples exist",
                        "Baseline blocked: duplicate point-award source tuples exist",
                        "Baseline blocked: multiple active password reset tokens exist for a user",
                        "legacy_content_redacted_length=",
                        "add constraint uk_lesson_progress_student_lesson",
                        "add constraint uk_student_progress_student",
                        "add constraint uk_daily_streaks_student",
                        "add constraint uk_points_transactions_student_source",
                        "add constraint uk_password_reset_tokens_active_user",
                        "add constraint chk_video_progress_watched_within_total",
                        "create index idx_audit_logs_target_created")
                .doesNotContain("chk_points_transactions_delta_non_negative");
    }

    @Test
    void consolidatedBaselineEnforcesFinalDomainChecksSessionsQuotaAndHotPathIndexes() throws IOException {
        String migration = Files.readString(BASELINE_MIGRATION);

        assertThat(migration)
                .contains(
                        "Baseline blocked: rows violate tightened status/domain checks",
                        "Baseline blocked: rows violate review/read state consistency checks",
                        "add constraint chk_ai_chat_messages_sender_role",
                        "add constraint chk_ai_usage_logs_status",
                        "add constraint chk_ai_response_reports_review_consistency",
                        "current_document_key varchar(160) generated always as",
                        "add constraint uk_teacher_verification_documents_current_type",
                        "create index idx_ai_chat_messages_session_role_id",
                        "create index idx_ai_response_reports_student_created",
                        "create index idx_teacher_verification_reviews_latest",
                        "create index idx_notifications_user_created_id",
                        "create index idx_notifications_user_status_created_id",
                        "create table refresh_tokens",
                        "add constraint uk_refresh_tokens_token_hash",
                        "create table ai_usage_quota_counters",
                        "add constraint uk_ai_usage_quota_counters_user")
                .doesNotContain("idx_notifications_user_status_created_at");
    }

    @Test
    void schemaCleanupMigrationRemapsAndDropsUnusedSchemaBehindGuards() throws IOException {
        String migration = Files.readString(SCHEMA_CLEANUP_MIGRATION);

        assertThat(migration)
                .contains(
                        "set notification_type = 'SYSTEM'",
                        "where notification_type in ('QUIZ_RESULT', 'REWARD_EARNED')",
                        "set enrolment_status = 'CANCELLED'",
                        "cancelled_at = coalesce(cancelled_at, updated_at)",
                        "where enrolment_status = 'MISSED'",
                        "alter table student_profiles drop column display_name",
                        "alter table student_profiles drop column country",
                        "alter table student_profiles drop column avatar_url",
                        "alter table ai_feedback_records drop column input_file_url",
                        "alter table ai_chat_sessions drop foreign key fk_ai_chat_sessions_related_lesson",
                        "alter table ai_chat_sessions drop index idx_ai_chat_sessions_related_lesson_id",
                        "alter table ai_chat_sessions drop column related_lesson_id",
                        "alter table lessons drop index ft_lessons_title_summary_content",
                        "alter table course_packages drop index ft_course_packages_title_description",
                        "alter table flashcards drop index ft_flashcards_learning_text")
                // SDD Table 4.2 lists student_profiles.school_name.
                .doesNotContain("drop column school_name");
        // The foreign key goes before the index it uses, and both before the column.
        assertThat(migration.indexOf("drop foreign key fk_ai_chat_sessions_related_lesson"))
                .isLessThan(migration.indexOf("drop index idx_ai_chat_sessions_related_lesson_id"));
        assertThat(migration.indexOf("drop index idx_ai_chat_sessions_related_lesson_id"))
                .isLessThan(migration.indexOf("drop column related_lesson_id"));
        // Rows holding a removed enum value are remapped before the column type drops that value.
        assertThat(migration.indexOf("where notification_type in ('QUIZ_RESULT', 'REWARD_EARNED')"))
                .isNotNegative()
                .isLessThan(migration.indexOf("alter table notifications modify column notification_type"));
        assertThat(migration.indexOf("where enrolment_status = 'MISSED'"))
                .isNotNegative()
                .isLessThan(migration.indexOf("alter table live_class_enrolments modify column enrolment_status"));
        // Every DDL statement is a prepared statement chosen through information_schema: no line runs a bare
        // "alter table", and each quoted one has its own prepare.
        assertThat(migration.lines().map(line -> line.strip().toLowerCase(Locale.ROOT)))
                .noneMatch(line -> line.startsWith("alter table"));
        long preparedStatements = migration.lines().filter(line -> line.strip().startsWith("prepare ")).count();
        long ddlStatements = migration.lines().filter(line -> line.contains("'alter table ")).count();
        assertThat(preparedStatements).isEqualTo(ddlStatements).isPositive();
    }

    @Test
    void aiHelperMigrationReplacesPracticeSubmissionsAndRemapsBeforeNarrowingEnums() throws IOException {
        String migration = Files.readString(AI_HELPER_MIGRATION);

        assertThat(migration)
                .contains(
                        "create table ai_practice_checks (",
                        "check_type enum ('HANDWRITING','RECORDING') not null",
                        "constraint fk_ai_practice_checks_student_user foreign key (student_user_id) references users (id)",
                        "alter table teacher_feedback drop foreign key fk_teacher_feedback_practice_submission",
                        "drop table practice_submissions",
                        "drop table ai_feedback_records",
                        "delete from uploaded_files where id in (select id from v13_practice_upload_ids)",
                        "delete from system_settings where setting_key = 'platform_name'");
        // The FK goes before its index and the column; uploads are collected before their tables are dropped.
        assertThat(migration.indexOf("drop foreign key fk_teacher_feedback_practice_submission"))
                .isLessThan(migration.indexOf("drop index idx_teacher_feedback_practice_submission_id"));
        assertThat(migration.indexOf("select uploaded_file_id from practice_submissions"))
                .isLessThan(migration.indexOf("drop table practice_submissions"));
        // Rows are remapped before a column or check narrows its values.
        assertThat(migration.indexOf("where feature_key in ('VOCABULARY_HELPER', 'SENTENCE_PRACTICE')"))
                .isNotNegative()
                .isLessThan(migration.indexOf("modify column feature_key enum"));
        assertThat(migration.indexOf("where status = 'IN_REVIEW'"))
                .isNotNegative()
                .isLessThan(migration.indexOf("add constraint chk_ai_response_reports_status"));
        assertThat(migration.indexOf("where reason_category = 'HALLUCINATION'"))
                .isNotNegative()
                .isLessThan(migration.indexOf("add constraint chk_ai_response_reports_reason_category"));
    }

    /**
     * The Hibernate {@code ddl-auto} mode and whether Flyway runs. Read as YAML: several unrelated keys are also
     * named "enabled", so a text search cannot pin these.
     */
    private static void assertSchemaStrategy(Path profile, String ddlAuto, boolean flywayEnabled) throws IOException {
        assertThat(yamlValue(profile, "spring.jpa.hibernate.ddl-auto")).as(profile + " ddl-auto").isEqualTo(ddlAuto);
        assertThat(yamlValue(profile, "spring.flyway.enabled")).as(profile + " flyway").isEqualTo(flywayEnabled);
    }

    /** The value at a dotted key, taken from the first YAML document of the file that defines it. */
    private static Object yamlValue(Path path, String dottedKey) throws IOException {
        for (Object document : new Yaml().loadAll(Files.readString(path))) {
            Object node = document;
            for (String key : dottedKey.split("\\.")) {
                node = node instanceof Map<?, ?> map ? map.get(key) : null;
            }
            if (node != null) {
                return node;
            }
        }
        throw new AssertionError(path + " does not define " + dottedKey);
    }
}
