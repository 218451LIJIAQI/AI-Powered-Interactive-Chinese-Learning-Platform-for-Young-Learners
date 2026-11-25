package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import com.example.aichinese.support.DemoProfileTest;
import com.example.aichinese.support.FlywayMigrationFiles;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pins the MySQL migration contract and explicitly documents the separate schema strategy of the demo and test
 * profiles, whose databases Hibernate rebuilds. What the migrations build is checked on a real database by
 * {@link com.example.aichinese.MySqlMigrationSmokeTests}.
 */
class DatabaseMigrationConfigurationTests {

    private static final Path APPLICATION_YML = Path.of("src/main/resources/application.yml");
    private static final Path APPLICATION_LOCAL_YML = Path.of("src/main/resources/application-local.yml");
    private static final Path APPLICATION_DEMO_YML = Path.of("src/main/resources/application-demo.yml");
    private static final Path APPLICATION_TEST_YML = Path.of("src/test/resources/application-test.yml");
    private static final Path APPLICATION_DEMO_TEST_YML = Path.of("src/test/resources/application-demo-test.yml");
    private static final Path BASELINE_MIGRATION =
            FlywayMigrationFiles.MIGRATION_DIRECTORY.resolve("V1__consolidated_schema.sql");

    private final YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();

    @Test
    void runtimeProfilesUseFlywayWithHibernateValidationOnly() throws IOException {
        List<PropertySource<?>> appDocs = loadYamlDocuments(APPLICATION_YML);
        List<PropertySource<?>> localDocs = loadYamlDocuments(APPLICATION_LOCAL_YML);

        assertSchemaStrategy(APPLICATION_YML, appDocs, "validate", true, true);
        assertNoSchemaResetDatabase(APPLICATION_YML, appDocs);
        PropertySource<?> baseApp = appDocs.getFirst();
        assertThat(baseApp.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME:}");
        assertThat(baseApp.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD:}");
        assertThat(String.valueOf(baseApp.getProperty("spring.datasource.url")))
                .contains("connectionTimeZone=${APP_DEFAULT_TIME_ZONE:Asia/Singapore}", "useUnicode=true", "characterEncoding=utf8");

        PropertySource<?> prodDoc = appDocs.stream()
                .filter(doc -> "prod | production".equals(doc.getProperty("spring.config.activate.on-profile")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing prod | production document in application.yml"));
        assertThat(String.valueOf(prodDoc.getProperty("spring.datasource.url")))
                .contains("sslMode=${DB_SSL_MODE:REQUIRED}&allowPublicKeyRetrieval=false");
        assertThat(prodDoc.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo(false);

        PropertySource<?> localDoc = localDocs.getFirst();
        assertThat(localDoc.getProperty("spring.config.activate.on-profile")).isEqualTo("local");
        assertThat(localDoc.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME:aichinese_app}");
        assertThat(localDoc.getProperty("spring.datasource.password"))
                .isEqualTo("${DB_PASSWORD:replace-with-a-strong-application-db-password}");
        assertThat(localDoc.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(localDoc.getProperty("app.security.jwt.secret"))
                .isEqualTo("${JWT_SECRET:LocalComposeOnlyJwtSecret1234567890AB}");
        assertSchemaStrategy(APPLICATION_LOCAL_YML, localDocs, "validate", true, false);
        assertNoSchemaResetDatabase(APPLICATION_LOCAL_YML, localDocs);
    }

    /** The reset and Hibernate rebuild run only against the databases of the demo and test profiles. */
    @Test
    void rebuiltProfilesUseTheirOwnDatabases() throws IOException {
        List<PropertySource<?>> demoDocs = loadYamlDocuments(APPLICATION_DEMO_YML);
        PropertySource<?> demoDoc = demoDocs.getFirst();
        assertThat(demoDoc.getProperty("spring.config.activate.on-profile")).isEqualTo("demo");
        assertThat(String.valueOf(demoDoc.getProperty("spring.datasource.url")))
                .contains("/${DEMO_DB_NAME:ai_chinese_learning_demo}?createDatabaseIfNotExist=true")
                .doesNotContain("DB_NAME:ai_chinese_learning}");
        assertSchemaStrategy(APPLICATION_DEMO_YML, demoDocs, "create-only", false, true);
        assertThat(demoDoc.getProperty("app.schema-reset.database"))
                .isEqualTo("${DEMO_DB_NAME:ai_chinese_learning_demo}");

        List<PropertySource<?>> testDocs = loadYamlDocuments(APPLICATION_TEST_YML);
        PropertySource<?> testDoc = testDocs.getFirst();
        assertThat(String.valueOf(testDoc.getProperty("spring.datasource.url")))
                .contains("/${TEST_DB_NAME:ai_chinese_learning_test}?createDatabaseIfNotExist=true");
        assertSchemaStrategy(APPLICATION_TEST_YML, testDocs, "create-only", false, true);
        assertThat(testDoc.getProperty("app.schema-reset.database"))
                .isEqualTo("${TEST_DB_NAME:ai_chinese_learning_test}");

        // Demo seeder tests layer demo -> test -> demo-test, so demo-test must not override the test DB or schema rules.
        ActiveProfiles demoProfiles = DemoProfileTest.class.getAnnotation(ActiveProfiles.class);
        assertThat(demoProfiles).isNotNull();
        assertThat(demoProfiles.value()).containsExactly("demo", "test", "demo-test");
        List<PropertySource<?>> demoTestDocs = loadYamlDocuments(APPLICATION_DEMO_TEST_YML);
        assertSchemaStrategy(APPLICATION_DEMO_TEST_YML, demoTestDocs, "create-only", false, false);
        assertNoSchemaResetDatabase(APPLICATION_DEMO_TEST_YML, demoTestDocs);
        assertThat(demoTestDocs.getFirst().getProperty("spring.datasource.url")).isNull();
    }

    /**
     * application.yml leaves these safety settings to the defaults. Boot passes its own to Flyway, and flyway-core is
     * pinned to a version Boot does not manage, so a version bump in either must not change them unnoticed.
     */
    @Test
    void flywayDefaultsKeepCleanDisabledAndValidationOn() {
        FlywayProperties boot = new FlywayProperties();
        assertThat(boot.isCleanDisabled()).isTrue();
        assertThat(boot.isValidateOnMigrate()).isTrue();
        assertThat(boot.getBaselineVersion()).isEqualTo("1");

        FluentConfiguration flyway = Flyway.configure();
        assertThat(flyway.isCleanDisabled()).isTrue();
        assertThat(flyway.isValidateOnMigrate()).isTrue();
    }

    /** Versions run without gaps or duplicates: each is the next whole version or the next step (5, 5.1, 5.2, 6). */
    @Test
    void migrationsAreVersionedFromTheUntouchedBaseline() throws IOException {
        List<Path> migrations = FlywayMigrationFiles.inVersionOrder();
        List<String> versions = migrations.stream().map(FlywayMigrationFiles::version).toList();

        try (Stream<Path> files = Files.list(FlywayMigrationFiles.MIGRATION_DIRECTORY)) {
            List<Path> sqlFiles = files.filter(file -> file.getFileName().toString().endsWith(".sql")).toList();
            assertThat(migrations).as("every .sql file in db/migration is a valid versioned migration")
                    .hasSize(sqlFiles.size());
        }
        assertThat(versions).containsExactlyElementsOf(FlywayMigrationFiles.EXPECTED_VERSIONS);
        assertThat(migrations.get(0)).isEqualTo(BASELINE_MIGRATION);
        assertThat(versions.get(0)).isEqualTo("1");
        for (int index = 1; index < versions.size(); index++) {
            assertThat(versions.get(index)).as("the version after " + versions.get(index - 1))
                    .isIn(versionsAfter(versions.get(index - 1)));
        }
    }

    /** The versions that may follow {@code version}: the next whole version, or the next step of this one. */
    private static List<String> versionsAfter(String version) {
        String[] parts = version.split("\\.");
        int whole = Integer.parseInt(parts[0]);
        int step = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return List.of(String.valueOf(whole + 1), whole + "." + (step + 1));
    }

    /**
     * Loads YAML through Spring's {@link YamlPropertySourceLoader} so both nested and flat dotted keys resolve
     * identically to runtime Boot binding.
     */
    private List<PropertySource<?>> loadYamlDocuments(Path profile) throws IOException {
        return yamlLoader.load(profile.getFileName().toString(), new FileSystemResource(profile));
    }

    private static void assertSchemaStrategy(
            Path profile, List<PropertySource<?>> documents, String ddlAuto, boolean flywayEnabled, boolean requireOnFirstDoc) {
        assertThat(documents).as(profile + " YAML documents").isNotEmpty();
        for (int index = 0; index < documents.size(); index++) {
            PropertySource<?> doc = documents.get(index);
            Object actualDdl = doc.getProperty("spring.jpa.hibernate.ddl-auto");
            Object actualFlyway = doc.getProperty("spring.flyway.enabled");
            if (index == 0 && requireOnFirstDoc) {
                assertThat(actualDdl).as(profile + " doc#0 ddl-auto").isEqualTo(ddlAuto);
                assertThat(actualFlyway).as(profile + " doc#0 flyway").isEqualTo(flywayEnabled);
            } else {
                assertThat(actualDdl).as(profile + " doc#" + index + " ddl-auto").isIn(null, ddlAuto);
                assertThat(actualFlyway).as(profile + " doc#" + index + " flyway").isIn(null, flywayEnabled);
            }
        }
    }

    private static void assertNoSchemaResetDatabase(Path profile, List<PropertySource<?>> documents) {
        for (int index = 0; index < documents.size(); index++) {
            assertThat(documents.get(index).getProperty("app.schema-reset.database"))
                    .as(profile + " doc#" + index + " app.schema-reset.database")
                    .isNull();
        }
    }
}
