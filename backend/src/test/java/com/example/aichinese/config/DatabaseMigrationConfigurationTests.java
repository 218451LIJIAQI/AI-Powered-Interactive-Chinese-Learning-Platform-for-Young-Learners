package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Pins the migration contract: runtime profiles use Flyway-managed
 * schema with Hibernate set to {@code validate} (never
 * {@code update} / {@code create-drop}), preventing accidental schema
 * drift between FYP demo, local development, and tests.
 */
class DatabaseMigrationConfigurationTests {

    @Test
    void runtimeProfilesUseFlywayWithHibernateValidationOnly() throws IOException {
        String application = readResource("src/main/resources/application.yml");
        String local = readResource("src/main/resources/application-local.yml");
        String demo = readResource("src/main/resources/application-demo.yml");

        assertThat(application).contains("ddl-auto: validate");
        assertThat(application).contains("flyway:");
        assertThat(application).contains("enabled: true");
        assertThat(application).contains("username: ${DB_USERNAME:}");
        assertThat(application).contains("password: ${DB_PASSWORD:}");
        assertThat(application).contains("connectionTimeZone=Asia/Singapore");
        assertThat(application).contains("useUnicode=true");
        assertThat(application).contains("characterEncoding=utf8");
        assertThat(application).doesNotContain("username: ${DB_USERNAME:root}");
        assertThat(application).doesNotContain("password: ${DB_PASSWORD:root}");
        assertThat(application).doesNotContain("ddl-auto: update");

        assertThat(application).contains("mock-success-enabled: ${PAYMENT_MOCK_SUCCESS_ENABLED:false}");
        assertThat(application).contains("provider: ${PAYMENT_PROVIDER:no-op}");
        assertThat(local).contains("on-profile: local");
        assertThat(local).contains("username: ${DB_USERNAME:aichinese_app}");
        assertThat(local).contains("password: ${DB_PASSWORD:replace-with-a-strong-application-db-password}");
        assertThat(local).contains("address: 127.0.0.1");
        assertThat(local).contains("secret: ${JWT_SECRET:LocalComposeOnlyJwtSecret1234567890AB}");
        assertThat(local).contains("provider: ${PAYMENT_PROVIDER:no-op}");
        assertThat(local).contains("stripe-api-key: ${STRIPE_API_KEY:}");
        assertThat(local).doesNotContain("sk_test_");
        assertThat(local).doesNotContain("sk_live_");
        assertThat(local).doesNotContain("provider: ${PAYMENT_PROVIDER:stripe}");
        assertThat(demo).contains("on-profile: demo");
        assertThat(demo).contains("jdbc:h2:mem:aichinese-demo");
        assertThat(demo).contains("ddl-auto: create-drop");
        assertThat(demo).contains("flyway:");
        assertThat(demo).contains("enabled: false");
        assertThat(demo).contains("provider: mock");
        assertThat(demo).contains("mock-success-enabled: true");
    }

    @Test
    void localDatabaseConfigurationUsesApplicationUserAndUtf8mb4() throws IOException {
        String env = readResource(".env.example");
        String compose = readResource("docker-compose.yml");

        assertThat(env).contains("DB_USERNAME=aichinese_app");
        assertThat(env).contains("DB_ROOT_PASSWORD=");
        assertThat(env).doesNotContain("DB_USERNAME=root");
        assertThat(env).doesNotContain("DB_PASSWORD=root");

        assertThat(compose).contains("MYSQL_USER: ${DB_USERNAME:-aichinese_app}");
        assertThat(compose).contains("MYSQL_PASSWORD: ${DB_PASSWORD:-replace-with-a-strong-application-db-password}");
        assertThat(compose).contains("MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD:-replace-with-a-strong-root-db-password}");
        assertThat(compose).contains("--character-set-server=utf8mb4");
        assertThat(compose).contains("MYSQL_PWD=\"$${MYSQL_PASSWORD}\" mysqladmin ping");
        assertThat(compose).doesNotContain("-p${DB_PASSWORD");
        assertThat(compose).doesNotContain("-p$${MYSQL_PASSWORD}");
        assertThat(compose).doesNotContain("MYSQL_ROOT_HOST");
        assertThat(compose).doesNotContain("MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-root}");
    }

    @Test
    void baselineFinancialIntegrityFailsFastBeforeAddingConstraints() throws IOException {
        String migration = readResource("src/main/resources/db/migration/V1__consolidated_schema.sql");

        assertThat(migration).contains("Baseline blocked: orders contain negative totals or invalid currency codes");
        assertThat(migration).contains("Baseline blocked: duplicate payment provider references exist");
        assertThat(migration).contains("Baseline blocked: duplicate payment entitlement tuples exist");
        assertThat(migration).contains("Baseline blocked: multiple active refund requests exist for an order");
        assertThat(migration).contains("Baseline blocked: duplicate teacher earning rows exist for an order item");
        assertThat(migration).contains("add constraint idx_orders_order_number unique (order_number)");
        assertThat(migration).doesNotContain("UKnthkiu7pgmnqnu86i2jyoe2v7");
        assertThat(migration).contains("add constraint chk_orders_total_amount_non_negative");
        assertThat(migration).contains("add constraint uk_payment_entitlements_order_item_feature");
        assertThat(migration).contains("add constraint uk_refund_requests_active_order");
        assertThat(migration).contains("add constraint uk_teacher_earnings_order_item");
    }

    @Test
    void testProfileDocumentsCreateDropException() throws IOException {
        String testTest = readResource("src/test/resources/application-test.yml");

        assertThat(testTest).contains("ddl-auto: create-drop");
        assertThat(testTest).contains("enabled: false");
        assertThat(testTest).contains("Test profile intentionally uses Hibernate create-drop");
    }

    @Test
    void baselineEnforcesOperationalUniquenessPrivacyAndHotPathIndexes() throws IOException {
        String migration = readResource("src/main/resources/db/migration/V1__consolidated_schema.sql");

        assertThat(migration).contains("Baseline blocked: duplicate cart item tuples exist");
        assertThat(migration).contains("Baseline blocked: duplicate lesson progress tuples exist");
        assertThat(migration).contains("Baseline blocked: duplicate point-award source tuples exist");
        assertThat(migration).contains("Baseline blocked: multiple active password reset tokens exist for a user");
        assertThat(migration).contains("legacy_content_redacted_length=");
        assertThat(migration).contains("add constraint uk_cart_items_cart_item");
        assertThat(migration).contains("add constraint uk_lesson_progress_student_lesson");
        assertThat(migration).contains("add constraint uk_student_progress_student");
        assertThat(migration).contains("add constraint uk_daily_streaks_student");
        assertThat(migration).contains("add constraint uk_points_transactions_student_source");
        assertThat(migration).doesNotContain("chk_points_transactions_delta_non_negative");
        assertThat(migration).contains("add constraint uk_password_reset_tokens_active_user");
        assertThat(migration).contains("add constraint chk_video_progress_watched_within_total");
        assertThat(migration).contains("create index idx_audit_logs_target_created");
    }

    @Test
    void consolidatedBaselineEnforcesFinalDomainChecksSessionsQuotaSearchAndHotPathIndexes() throws IOException {
        String migration = readResource("src/main/resources/db/migration/V1__consolidated_schema.sql");

        assertThat(migration).contains("Baseline blocked: rows violate tightened status/domain checks");
        assertThat(migration).contains("Baseline blocked: rows violate review/read state consistency checks");
        assertThat(migration).contains("add constraint chk_ai_chat_messages_sender_role");
        assertThat(migration).contains("add constraint chk_teacher_earnings_status");
        assertThat(migration).contains("add constraint chk_ai_usage_logs_status");
        assertThat(migration).contains("add constraint chk_ai_response_reports_review_consistency");
        assertThat(migration).contains("add constraint chk_learning_modules_access_price");
        assertThat(migration).contains("add constraint chk_lessons_access_price");
        assertThat(migration).contains("add constraint chk_course_packages_access_price");
        assertThat(migration).contains("current_document_key varchar(160) generated always as");
        assertThat(migration).contains("add constraint uk_teacher_verification_documents_current_type");
        assertThat(migration).contains("create index idx_ai_chat_messages_session_role_id");
        assertThat(migration).contains("create index idx_ai_response_reports_student_created");
        assertThat(migration).contains("create index idx_teacher_verification_reviews_latest");
        assertThat(migration).contains("create index idx_notifications_user_created_id");
        assertThat(migration).contains("create index idx_notifications_user_status_created_id");
        assertThat(migration).doesNotContain("idx_notifications_user_status_created_at");
        assertThat(migration).contains("create table refresh_tokens");
        assertThat(migration).contains("add constraint uk_refresh_tokens_token_hash");
        assertThat(migration).contains("create table ai_usage_quota_counters");
        assertThat(migration).contains("add constraint uk_ai_usage_quota_counters_user");
        assertThat(migration).contains("add fulltext index ft_lessons_title_summary_content");
        assertThat(migration).contains("add fulltext index ft_course_packages_title_description");
        assertThat(migration).contains("add fulltext index ft_flashcards_learning_text");
        assertThat(migration).doesNotContainPattern(
                "create\\s+index\\s+idx_payment_records_provider_reference\\s+on\\s+payment_records");
        assertThat(migration).doesNotContainPattern(
                "create\\s+index\\s+idx_refund_requests_student_created\\s+on\\s+refund_requests");
    }

    private String readResource(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
