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
 * drift between dev / test / prod.
 */
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
        assertThat(local).contains("provider: ${PAYMENT_PROVIDER:no-op}");
        assertThat(local).contains("stripe-api-key: ${STRIPE_API_KEY:}");
        assertThat(local).doesNotContain("sk_test_");
        assertThat(local).doesNotContain("sk_live_");
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
        assertThat(compose).contains("MYSQL_PWD=\\\"$${MYSQL_PASSWORD}\\\" mysqladmin ping");
        assertThat(compose).doesNotContain("-p${DB_PASSWORD");
        assertThat(compose).doesNotContain("-p$${MYSQL_PASSWORD}");
        assertThat(compose).doesNotContain("MYSQL_ROOT_HOST");
        assertThat(compose).doesNotContain("MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-root}");

        assertThat(repairScript).contains("connectionTimeZone=Asia/Singapore");
        assertThat(repairScript).contains("useUnicode=true");
        assertThat(repairScript).contains("characterEncoding=utf8");

        assertThat(validationScript).contains("$AppUsername = \"aichinese_app\"");
        assertThat(validationScript).contains("DB_USERNAME = $AppUsername");
        assertThat(validationScript).contains("DB_PASSWORD = $AppPassword");
        assertThat(validationScript).contains("MYSQL_USER = $AppUsername");
        assertThat(validationScript).contains("MYSQL_PASSWORD = $AppPassword");
        assertThat(validationScript).doesNotContain("\"MYSQL_PASSWORD=$AppPassword\"");
        assertThat(validationScript).doesNotContain("\"MYSQL_PWD=$RootPassword\"");
        assertThat(validationScript).contains("-DbUsername $AppUsername");
        assertThat(validationScript).contains("fresh consolidated V1 migration history");
        assertThat(validationScript).contains("fresh point-award source uniqueness");
        assertThat(validationScript).contains("fresh active reset token uniqueness");
    }

    @Test
    void productionOperationalConfigPinsHealthChecksCachingAndCiQualityGates() throws IOException {
        String productionCompose = readResource("../docker-compose.prod.yml");
        String backendDockerfile = readResource("Dockerfile");
        String frontendDockerfile = readResource("../frontend/Dockerfile");
        String productionEnv = readResource("../.env.prod.example");
        String nginx = readResource("../frontend/nginx.conf");
        String qualityGate = readResource("../.github/workflows/repository-quality-gate.yml");

        assertThat(productionCompose).contains("MYSQL_PWD=\\\"$${MYSQL_PASSWORD}\\\" mysqladmin ping");
        assertThat(productionCompose).doesNotContain("-p${DB_PASSWORD");
        assertThat(productionCompose).doesNotContain("-p$${MYSQL_PASSWORD}");
        assertThat(productionCompose).contains("curl -fsS http://127.0.0.1:8080/actuator/health/readiness >/dev/null");
        assertThat(productionCompose).contains("condition: service_healthy");
        assertThat(productionCompose).contains("curl -fsS http://127.0.0.1:8080/ >/dev/null");
        assertThat(productionCompose).contains("VITE_API_BASE_URL: ${VITE_API_BASE_URL:-/api/v1}");

        assertThat(backendDockerfile).contains("apk add --no-cache curl");
        assertThat(backendDockerfile).contains("HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3");
        assertThat(backendDockerfile).contains("curl -fsS http://127.0.0.1:8080/actuator/health/readiness");
        assertThat(frontendDockerfile).contains("ARG VITE_API_BASE_URL=/api/v1");
        assertThat(frontendDockerfile).contains("ENV VITE_API_BASE_URL=$VITE_API_BASE_URL");
        assertThat(frontendDockerfile).contains("apk add --no-cache curl");
        assertThat(frontendDockerfile).contains("COPY --from=build --chown=nginx:nginx /workspace/dist /usr/share/nginx/html");
        assertThat(frontendDockerfile).contains("HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3");
        assertThat(frontendDockerfile).contains("curl -fsS http://127.0.0.1:8080/");
        assertThat(productionEnv).contains("VITE_API_BASE_URL=/api/v1");

        assertThat(nginx).contains("location = /index.html");
        assertThat(nginx).contains("location ~ ^/(sw\\.js|registerSW\\.js|manifest\\.webmanifest)$");
        assertThat(nginx).contains("Cache-Control \"no-cache, no-store, must-revalidate\"");
        assertThat(nginx).contains("Cache-Control \"public, max-age=604800, immutable\"");

        assertThat(qualityGate).contains("Frontend format check");
        assertThat(qualityGate).contains("npm run format:check");
        assertThat(qualityGate).contains("Validate Docker Compose configuration");
        assertThat(qualityGate).contains("env_file=\"${RUNNER_TEMP}/quality-gate.prod.env\"");
        assertThat(qualityGate).contains("trap 'rm -f \"${env_file}\"' EXIT");
        assertThat(qualityGate).contains("docker compose --env-file \"${env_file}\" -f docker-compose.prod.yml config >/dev/null");
        assertThat(qualityGate).contains("docker compose -f backend/docker-compose.yml config >/dev/null");
        assertThat(qualityGate).contains("STRIPE_API_KEY=quality-gate-stripe-key-placeholder");
        assertThat(qualityGate).contains("STRIPE_WEBHOOK_SECRET=quality-gate-stripe-webhook-placeholder");
        assertThat(qualityGate).doesNotContain("sk_live_");
        assertThat(qualityGate).doesNotContain("sk_test_");
        assertThat(qualityGate).doesNotContain("whsec_");
        assertThat(qualityGate).contains("Backend static analysis");
        assertThat(qualityGate).contains("spotbugs:check");
        assertThat(qualityGate).contains("-DskipOpenApiContract=false");
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
        String validationScript = readResource("scripts/validate-flyway-mysql.ps1");

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
        assertThat(migration).contains("create index idx_notifications_user_status_created_id");
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

        assertThat(validationScript).contains("fresh consolidated V1 migration history");
        assertThat(validationScript).contains("fresh AI chat sender role check");
        assertThat(validationScript).contains("fresh lesson access price check");
        assertThat(validationScript).contains("fresh current teacher verification document uniqueness");
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

    @Test
    void databaseBackupAndRestoreScriptsVerifyArtifactsSafely() throws IOException {
        String backupScript = readResource("../scripts/backup-db.sh");
        String restoreScript = readResource("../scripts/restore-db.sh");

        assertThat(backupScript).contains("umask 077");
        assertThat(backupScript).contains("require_command mysqldump");
        assertThat(backupScript).contains("require_command gzip");
        assertThat(backupScript).contains("require_command sha256sum");
        assertThat(backupScript).contains("require_command awk");
        assertThat(backupScript).contains("--hex-blob");
        assertThat(backupScript).contains("--default-character-set=utf8mb4");
        assertThat(backupScript).contains("sha256sum \"$tmp_target\"");
        assertThat(backupScript).contains("printf '%s  %s\\n' \"$checksum\"");
        assertThat(backupScript).contains("must not start with a hyphen");
        assertThat(backupScript).doesNotContain("sha256sum \"$target\" > \"$target.sha256\"");

        assertThat(restoreScript).contains("read_checksum_digest()");
        assertThat(restoreScript).contains("mapfile -t checksum_lines");
        assertThat(restoreScript).contains("Checksum file must contain exactly one entry");
        assertThat(restoreScript).contains("actual_checksum=\"$(sha256sum \"$backup_path\")\"");
        assertThat(restoreScript).contains("Checksum verification failed");
        assertThat(restoreScript).contains("gzip -t -- \"$backup_path\"");
        assertThat(restoreScript).contains("RESTORE_ALLOW_UNVERIFIED");
        assertThat(restoreScript).contains("--binary-mode=1");
        assertThat(restoreScript).contains("--default-character-set=utf8mb4");
        assertThat(restoreScript).contains("must not start with a hyphen");
        assertThat(restoreScript).doesNotContain("sha256sum --check");
        assertThat(restoreScript).doesNotContain("require_command wc");
        assertThat(restoreScript).doesNotContain("require_command tr");
    }

    private String readResource(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
