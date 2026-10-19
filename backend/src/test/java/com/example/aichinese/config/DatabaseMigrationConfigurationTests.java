package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Pins the MySQL migration contract and explicitly documents the separate H2
 * schema strategies used by the demo, desktop, and test profiles.
 */
class DatabaseMigrationConfigurationTests {

    private static final Path APPLICATION_YML = Path.of("src/main/resources/application.yml");
    private static final Path APPLICATION_LOCAL_YML = Path.of("src/main/resources/application-local.yml");
    private static final Path APPLICATION_DEMO_YML = Path.of("src/main/resources/application-demo.yml");
    private static final Path APPLICATION_DESKTOP_YML = Path.of("src/main/resources/application-desktop.yml");
    private static final Path APPLICATION_TEST_YML = Path.of("src/test/resources/application-test.yml");
    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration");
    private static final Path BASELINE_MIGRATION = MIGRATION_DIRECTORY.resolve("V1__consolidated_schema.sql");
    private static final Path REMOVE_COMMERCE_MIGRATION = MIGRATION_DIRECTORY.resolve("V2__remove_commerce.sql");
    private static final Path YOUNG_ADULT_ALIGNMENT_MIGRATION =
            MIGRATION_DIRECTORY.resolve("V3__young_adult_alignment.sql");
    private static final Path CONTENT_REVIEW_MIGRATION =
            MIGRATION_DIRECTORY.resolve("V4__content_review_workflow.sql");
    private static final Path LEARNER_EXPERIENCE_MIGRATION =
            MIGRATION_DIRECTORY.resolve("V5__learner_experience_and_integrity.sql");
    private static final Path BADGE_ALIGNMENT_MIGRATION = MIGRATION_DIRECTORY.resolve("V6__badge_alignment.sql");
    private static final List<String> REVIEWABLE_ACTIVITY_TABLES = List.of(
            "quizzes", "flashcards", "matching_games", "listening_exercises",
            "tracing_exercises", "drag_drop_activities", "story_activities");
    private static final Pattern MIGRATION_VERSION = Pattern.compile("^V(\\d+)__.+\\.sql$");
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
        String application = readResource(APPLICATION_YML);
        String local = readResource(APPLICATION_LOCAL_YML);
        String demo = readResource(APPLICATION_DEMO_YML);

        assertThat(application)
                .contains(
                        "ddl-auto: validate",
                        "flyway:",
                        "enabled: true",
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
                        "jdbc:h2:mem:aichinese-demo",
                        "ddl-auto: create-drop",
                        "flyway:",
                        "enabled: false")
                .doesNotContain("payment:", "mock-success-enabled");
    }

    @Test
    void localDatabaseConfigurationUsesApplicationUserAndUtf8mb4() throws IOException {
        String env = readResource(ENV_EXAMPLE);
        String compose = readResource(DOCKER_COMPOSE);

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
        List<Path> migrations = migrationFilesInVersionOrder();

        assertThat(migrations).hasSizeGreaterThanOrEqualTo(6);
        assertThat(migrations.get(0)).isEqualTo(BASELINE_MIGRATION);
        assertThat(migrations.get(1)).isEqualTo(REMOVE_COMMERCE_MIGRATION);
        assertThat(migrations.get(2)).isEqualTo(YOUNG_ADULT_ALIGNMENT_MIGRATION);
        assertThat(migrations.get(3)).isEqualTo(CONTENT_REVIEW_MIGRATION);
        assertThat(migrations.get(4)).isEqualTo(LEARNER_EXPERIENCE_MIGRATION);
        assertThat(migrations.get(5)).isEqualTo(BADGE_ALIGNMENT_MIGRATION);
        assertThat(migrations.stream().map(DatabaseMigrationConfigurationTests::migrationVersion).toList())
                .as("Flyway versions must be unique")
                .doesNotHaveDuplicates();
    }

    @Test
    void youngAdultAlignmentMigrationDropsAgeBandsAndRenamesReportReason() throws IOException {
        String migration = readResource(YOUNG_ADULT_ALIGNMENT_MIGRATION).toLowerCase(java.util.Locale.ROOT);

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
        String migration = readResource(CONTENT_REVIEW_MIGRATION).toLowerCase(java.util.Locale.ROOT);

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
        String migration = readResource(LEARNER_EXPERIENCE_MIGRATION).toLowerCase(java.util.Locale.ROOT);

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
        String migration = readResource(BADGE_ALIGNMENT_MIGRATION);

        for (com.example.aichinese.service.RewardBadge badge : com.example.aichinese.service.RewardBadge.values()) {
            assertThat(migration)
                    .as("V6 must insert and align " + badge.badgeName())
                    .contains("where not exists (select 1 from badges b where b.name = '" + badge.badgeName() + "');")
                    .contains("'" + badge.description() + "'")
                    .contains("icon_key = '" + badge.iconKey() + "'");
        }
        assertThat(migration.toLowerCase(java.util.Locale.ROOT))
                .contains("not exists (select 1 from student_badges sb where sb.badge_id = badges.id)")
                .doesNotContain("drop table", "drop column", "alter table");
    }

    @Test
    void removeCommerceMigrationDropsCommerceSchemaInDependencyOrder() throws IOException {
        String migration = readResource(REMOVE_COMMERCE_MIGRATION).toLowerCase(java.util.Locale.ROOT);

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
        String migrations = readCombinedMigrations().toLowerCase(java.util.Locale.ROOT);

        for (String table : COMMERCE_TABLES) {
            assertThat(migrations.lastIndexOf("drop table if exists " + table + ";"))
                    .as("the last statement touching " + table + " must be its drop")
                    .isGreaterThan(migrations.lastIndexOf("create table " + table + " ("));
        }
    }

    @Test
    void testProfileDocumentsCreateDropException() throws IOException {
        String testProfile = readResource(APPLICATION_TEST_YML);

        assertThat(testProfile)
                .contains(
                        "ddl-auto: create-drop",
                        "enabled: false",
                        "Test profile intentionally uses Hibernate create-drop");
    }

    @Test
    void desktopProfileKeepsDemoDataInAFile() throws IOException {
        String desktop = readResource(APPLICATION_DESKTOP_YML);

        assertThat(desktop).contains(
                "on-profile: desktop",
                "jdbc:h2:file:${FYP_DATA_DIR:./.local/data}/aichinese",
                "ddl-auto: update",
                "enabled: false");
    }

    @Test
    void baselineEnforcesOperationalUniquenessPrivacyAndHotPathIndexes() throws IOException {
        String migration = readResource(BASELINE_MIGRATION);

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
    void consolidatedBaselineEnforcesFinalDomainChecksSessionsQuotaSearchAndHotPathIndexes() throws IOException {
        String migration = readResource(BASELINE_MIGRATION);

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
                        "add constraint uk_ai_usage_quota_counters_user",
                        "add fulltext index ft_lessons_title_summary_content",
                        "add fulltext index ft_course_packages_title_description",
                        "add fulltext index ft_flashcards_learning_text")
                .doesNotContain("idx_notifications_user_status_created_at");
    }

    private static String readResource(Path path) throws IOException {
        return Files.readString(path);
    }

    /** All Flyway migrations concatenated in version order, i.e. the schema Flyway would build. */
    private static String readCombinedMigrations() throws IOException {
        StringBuilder combined = new StringBuilder();
        for (Path migration : migrationFilesInVersionOrder()) {
            combined.append(readResource(migration)).append('\n');
        }
        return combined.toString();
    }

    private static List<Path> migrationFilesInVersionOrder() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIRECTORY)) {
            return files
                    .filter(path -> MIGRATION_VERSION.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(DatabaseMigrationConfigurationTests::migrationVersion))
                    .toList();
        }
    }

    private static int migrationVersion(Path migration) {
        Matcher matcher = MIGRATION_VERSION.matcher(migration.getFileName().toString());
        if (!matcher.matches()) {
            throw new AssertionError("Not a versioned Flyway migration: " + migration);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
