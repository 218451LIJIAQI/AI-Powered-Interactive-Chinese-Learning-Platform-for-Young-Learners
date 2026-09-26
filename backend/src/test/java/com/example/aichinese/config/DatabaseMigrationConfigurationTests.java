package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    private static final Path BASELINE_MIGRATION = Path.of("src/main/resources/db/migration/V1__consolidated_schema.sql");
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
                        "characterEncoding=utf8",
                        "mock-success-enabled: ${PAYMENT_MOCK_SUCCESS_ENABLED:false}",
                        "provider: ${PAYMENT_PROVIDER:no-op}")
                .doesNotContain(
                        "username: ${DB_USERNAME:root}",
                        "password: ${DB_PASSWORD:root}",
                        "ddl-auto: update");

        assertThat(local)
                .contains(
                        "on-profile: local",
                        "username: ${DB_USERNAME:aichinese_app}",
                        "password: ${DB_PASSWORD:replace-with-a-strong-application-db-password}",
                        "address: 127.0.0.1",
                        "secret: ${JWT_SECRET:LocalComposeOnlyJwtSecret1234567890AB}",
                        "provider: ${PAYMENT_PROVIDER:no-op}",
                        "stripe-api-key: ${STRIPE_API_KEY:}")
                .doesNotContain("sk_test_", "sk_live_", "provider: ${PAYMENT_PROVIDER:stripe}");

        assertThat(demo)
                .contains(
                        "on-profile: demo",
                        "jdbc:h2:mem:aichinese-demo",
                        "ddl-auto: create-drop",
                        "flyway:",
                        "enabled: false",
                        "provider: mock",
                        "mock-success-enabled: true");
    }

    @Test
    void localDatabaseConfigurationUsesApplicationUserAndUtf8mb4() throws IOException {
        String env = readResource(ENV_EXAMPLE);
        String compose = readResource(DOCKER_COMPOSE);

        assertThat(env)
                .contains("DB_USERNAME=aichinese_app", "DB_ROOT_PASSWORD=")
                .doesNotContain("DB_USERNAME=root", "DB_PASSWORD=root");

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
    void baselineFinancialIntegrityFailsFastBeforeAddingConstraints() throws IOException {
        String migration = readResource(BASELINE_MIGRATION);

        assertThat(migration)
                .contains(
                        "Baseline blocked: orders contain negative totals or invalid currency codes",
                        "Baseline blocked: duplicate payment provider references exist",
                        "Baseline blocked: duplicate payment entitlement tuples exist",
                        "Baseline blocked: multiple active refund requests exist for an order",
                        "Baseline blocked: duplicate teacher earning rows exist for an order item",
                        "add constraint idx_orders_order_number unique (order_number)",
                        "add constraint chk_orders_total_amount_non_negative",
                        "add constraint uk_payment_entitlements_order_item_feature",
                        "add constraint uk_refund_requests_active_order",
                        "add constraint uk_teacher_earnings_order_item")
                .doesNotContain("UKnthkiu7pgmnqnu86i2jyoe2v7");
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
                        "Baseline blocked: duplicate cart item tuples exist",
                        "Baseline blocked: duplicate lesson progress tuples exist",
                        "Baseline blocked: duplicate point-award source tuples exist",
                        "Baseline blocked: multiple active password reset tokens exist for a user",
                        "legacy_content_redacted_length=",
                        "add constraint uk_cart_items_cart_item",
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
                        "add constraint chk_teacher_earnings_status",
                        "add constraint chk_ai_usage_logs_status",
                        "add constraint chk_ai_response_reports_review_consistency",
                        "add constraint chk_learning_modules_access_price",
                        "add constraint chk_lessons_access_price",
                        "add constraint chk_course_packages_access_price",
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
                .doesNotContain("idx_notifications_user_status_created_at")
                .doesNotContainPattern(
                        "create\\s+index\\s+idx_payment_records_provider_reference\\s+on\\s+payment_records")
                .doesNotContainPattern(
                        "create\\s+index\\s+idx_refund_requests_student_created\\s+on\\s+refund_requests");
    }

    private static String readResource(Path path) throws IOException {
        return Files.readString(path);
    }
}
