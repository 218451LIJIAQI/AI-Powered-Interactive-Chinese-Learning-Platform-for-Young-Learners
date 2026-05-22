package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DatabaseMigrationConfigurationTests {

    @Test
    void runtimeProfilesUseFlywayWithHibernateValidationOnly() throws IOException {
        String application = readResource("src/main/resources/application.yml");
        String example = readResource("src/main/resources/application-example.yml");
        String local = readResource("src/main/resources/application-local.yml");
        String prod = readResource("src/main/resources/application-prod.yml");

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

        assertThat(example).contains("ddl-auto: validate");
        assertThat(example).contains("flyway:");
        assertThat(example).contains("enabled: true");
        assertThat(example).contains("username: ${DB_USERNAME}");
        assertThat(example).contains("password: ${DB_PASSWORD}");
        assertThat(example).contains("connectionTimeZone=Asia/Singapore");
        assertThat(example).contains("useUnicode=true");
        assertThat(example).contains("characterEncoding=utf8");
        assertThat(example).doesNotContain("username: ${DB_USERNAME:root}");
        assertThat(example).doesNotContain("password: ${DB_PASSWORD:root}");
        assertThat(example).doesNotContain("ddl-auto: update");

        assertThat(application).contains("mock-success-enabled: ${PAYMENT_MOCK_SUCCESS_ENABLED:false}");
        assertThat(application).contains("provider: ${PAYMENT_PROVIDER:no-op}");
        assertThat(example).contains("mock-success-enabled: ${PAYMENT_MOCK_SUCCESS_ENABLED:false}");
        assertThat(example).contains("provider: ${PAYMENT_PROVIDER:no-op}");
        assertThat(local).contains("on-profile: local");
        assertThat(local).contains("username: ${DB_USERNAME:aichinese_app}");
        assertThat(local).contains("password: ${DB_PASSWORD:replace-with-a-strong-application-db-password}");
        assertThat(local).contains("address: 127.0.0.1");
        assertThat(local).contains("secret: ${JWT_SECRET:LocalComposeOnlyJwtSecret1234567890AB}");
        assertThat(local).doesNotContain("provider: ${PAYMENT_PROVIDER:stripe}");
        assertThat(prod).contains("mock-success-enabled: ${PAYMENT_MOCK_SUCCESS_ENABLED:false}");
        assertThat(prod).contains("provider: ${PAYMENT_PROVIDER:stripe}");
        assertThat(prod).contains("Startup now fails fast");
    }

    @Test
    void localDatabaseConfigurationUsesApplicationUserAndUtf8mb4() throws IOException {
        String env = readResource(".env.example");
        String compose = readResource("docker-compose.yml");
        String repairScript = readResource("scripts/repair-flyway-checksums.ps1");
        String validationScript = readResource("scripts/validate-flyway-mysql.ps1");

        assertThat(env).contains("DB_USERNAME=aichinese_app");
        assertThat(env).contains("DB_ROOT_PASSWORD=");
        assertThat(env).doesNotContain("DB_USERNAME=root");
        assertThat(env).doesNotContain("DB_PASSWORD=root");

        assertThat(compose).contains("MYSQL_USER: ${DB_USERNAME:-aichinese_app}");
        assertThat(compose).contains("MYSQL_PASSWORD: ${DB_PASSWORD:-replace-with-a-strong-application-db-password}");
        assertThat(compose).contains("MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD:-replace-with-a-strong-root-db-password}");
        assertThat(compose).contains("--character-set-server=utf8mb4");
        assertThat(compose).doesNotContain("MYSQL_ROOT_HOST");
        assertThat(compose).doesNotContain("MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-root}");

        assertThat(repairScript).contains("connectionTimeZone=Asia/Singapore");
        assertThat(repairScript).contains("useUnicode=true");
        assertThat(repairScript).contains("characterEncoding=utf8");

        assertThat(validationScript).contains("$AppUsername = \"aichinese_app\"");
        assertThat(validationScript).contains("DB_USERNAME = $AppUsername");
        assertThat(validationScript).contains("DB_PASSWORD = $AppPassword");
        assertThat(validationScript).contains("\"MYSQL_USER=$AppUsername\"");
        assertThat(validationScript).contains("\"MYSQL_PASSWORD=$AppPassword\"");
        assertThat(validationScript).contains("-DbUsername $AppUsername");
        assertThat(validationScript).contains("fresh V12 migration history");
        assertThat(validationScript).contains("fresh point-award source uniqueness");
        assertThat(validationScript).contains("fresh active reset token uniqueness");
    }

    @Test
    void v8FinancialIntegrityMigrationFailsFastBeforeAddingConstraints() throws IOException {
        String migration = readResource("src/main/resources/db/migration/V8__strengthen_financial_data_integrity.sql");

        assertThat(migration).contains("V8 blocked: orders contain negative totals or invalid currency codes");
        assertThat(migration).contains("V8 blocked: duplicate payment provider references exist");
        assertThat(migration).contains("V8 blocked: duplicate payment entitlement tuples exist");
        assertThat(migration).contains("V8 blocked: multiple active refund requests exist for an order");
        assertThat(migration).contains("V8 blocked: duplicate teacher earning rows exist for an order item");
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
    void v12EnforcesOperationalUniquenessPrivacyAndHotPathIndexes() throws IOException {
        String migration = readResource("src/main/resources/db/migration/V12__enforce_database_contracts_and_privacy_bounds.sql");

        assertThat(migration).contains("V12 blocked: duplicate cart item tuples exist");
        assertThat(migration).contains("V12 blocked: duplicate lesson progress tuples exist");
        assertThat(migration).contains("V12 blocked: duplicate point-award source tuples exist");
        assertThat(migration).contains("V12 blocked: multiple active password reset tokens exist for a user");
        assertThat(migration).contains("legacy_content_redacted_length=");
        assertThat(migration).contains("add constraint uk_cart_items_cart_item");
        assertThat(migration).contains("add constraint uk_lesson_progress_student_lesson");
        assertThat(migration).contains("add constraint uk_student_progress_student");
        assertThat(migration).contains("add constraint uk_daily_streaks_student");
        assertThat(migration).contains("add constraint uk_points_transactions_student_source");
        assertThat(migration).contains("add constraint uk_password_reset_tokens_active_user");
        assertThat(migration).contains("add constraint chk_video_progress_watched_within_total");
        assertThat(migration).contains("create index idx_audit_logs_target_created");
    }

    @Test
    void flywayRepairScriptsDoNotExposeDatabasePasswordsInCommandLine() throws IOException {
        String repairScript = readResource("scripts/repair-flyway-checksums.ps1");
        String validationScript = readResource("scripts/validate-flyway-mysql.ps1");

        assertThat(repairScript).doesNotContain("-Dflyway.password=");
        assertThat(repairScript).contains("FLYWAY_PASSWORD");
        assertThat(validationScript).doesNotContain("$EscapedPassword");
        assertThat(validationScript).doesNotContain("ConvertTo-SecureString '$");
        assertThat(validationScript).contains("AICHINESE_FLYWAY_REPAIR_PASSWORD");
    }

    private String readResource(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
