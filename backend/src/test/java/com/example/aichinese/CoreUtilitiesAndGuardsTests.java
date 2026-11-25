package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.aichinese.config.DatasourceProductionGuard;
import com.example.aichinese.config.OpenApiConfig;
import com.example.aichinese.config.RefreshCookieOriginFilter;
import com.example.aichinese.config.seed.DemoProfileGuard;
import com.example.aichinese.dto.auth.AcceptAdminInviteRequest;
import com.example.aichinese.dto.auth.RegisterStudentRequest;
import com.example.aichinese.dto.auth.UpdateProfileRequest;
import com.example.aichinese.entity.value.PronunciationFeedback;
import com.example.aichinese.util.CsvWriter;
import com.example.aichinese.util.FileNames;
import com.example.aichinese.util.NetworkAddresses;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Focused unit tests for core utilities and production guards strengthened during codebase hardening. */
class CoreUtilitiesAndGuardsTests {

    private static final String ENCRYPTED_PROD_URL = "jdbc:mysql://db.example:3306/app?sslMode=REQUIRED";

    @Test
    void csvWriterQuotesAndNeutralizesLeadingSpacesAndNewlinesBeforeFormulas() {
        String csv = new CsvWriter()
                .row(Arrays.asList("plain", null, OffsetDateTime.of(2026, 5, 1, 9, 5, 3, 0, ZoneOffset.ofHours(8))))
                .row(List.of("=1+1", " =HYPERLINK(\"http://x\")", "\u00A0+SUM(A1)", "\n=CMD", "a, \"b\""))
                .toString();

        assertThat(csv).isEqualTo(
                "plain,,2026-05-01 09:05:03+08:00\r\n"
                        + "'=1+1,\"' =HYPERLINK(\"\"http://x\"\")\",'\u00A0+SUM(A1),\"'\n=CMD\",\"a, \"\"b\"\"\"\r\n");
    }

    @Test
    void fileNamesStripsUnicodeSpacesAndHandlesNull() {
        assertThat(FileNames.sanitize(null)).isEmpty();
        assertThat(FileNames.sanitize("\u00A0\u202F")).isEmpty();
        assertThat(FileNames.sanitize("\u00A0..photo.png\u00A0")).isEqualTo("photo.png");
        assertThat(FileNames.sanitize("lesson\u00A0\u00A0notes.mp4")).isEqualTo("lesson notes.mp4");
    }

    @Test
    void networkAddressesRejectsNonAsciiDigitsAndOversizedIpv4Octets() {
        assertThat(NetworkAddresses.parseLiteral("\uFF11\uFF12\uFF17.\uFF10.\uFF10.\uFF11")).isNull();
        assertThat(NetworkAddresses.parseLiteral("0001.2.3.4")).isNull();
        assertThat(NetworkAddresses.parseLiteral("012.0.0.1")).isNull();
        assertThat(NetworkAddresses.parseLiteral("010.0.0.1")).isNull();
        assertThat(NetworkAddresses.parseLiteral("[::ffff:012.0.0.1]")).isNull();
        assertThat(NetworkAddresses.parseLiteral("::ffff:010.0.0.1")).isNull();
        assertThat(NetworkAddresses.parseLiteral("[127.0.0.1]")).isNull();
        assertThat(NetworkAddresses.parseLiteral("127.0.0.1")).isNotNull();
        assertThat(NetworkAddresses.parseLiteral("0.0.0.0")).isNotNull();
        assertThat(NetworkAddresses.parseLiteral("[2001:db8::1]")).isNotNull();
        assertThat(NetworkAddresses.parseLiteral("[::ffff:127.0.0.1]")).isNotNull();
    }

    private static MockEnvironment prodEnv(String datasourceUrl) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.url", datasourceUrl);
        env.setProperty("spring.datasource.password", "StrongProdDbPass123!");
        env.setProperty("spring.flyway.enabled", "true");
        env.setProperty("spring.flyway.baseline-on-migrate", "false");
        env.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        return env;
    }

    private static void assertGuardRejected(MockEnvironment env, String expectedFragment) {
        assertThatThrownBy(() -> new DatasourceProductionGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedFragment);
    }

    @Test
    void datasourceProductionGuardRequiresEncryptedSslModeInProd() {
        MockEnvironment local = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://localhost:3307/db?sslMode=DISABLED");
        new DatasourceProductionGuard(local);

        new DatasourceProductionGuard(prodEnv(ENCRYPTED_PROD_URL));

        MockEnvironment prodVerifyIdentity = prodEnv("jdbc:mysql://db.example:3306/app?sslMode=VERIFY_IDENTITY");
        prodVerifyIdentity.setActiveProfiles("production");
        new DatasourceProductionGuard(prodVerifyIdentity);

        for (String badUrl : List.of(
                "jdbc:mysql://db.example:3306/app?sslMode=DISABLED",
                "jdbc:mysql://db.example:3306/app?sslMode=PREFERRED",
                "jdbc:mysql://db.example:3306/app?sslMode=REQUIRED&sslMode=",
                "jdbc:mysql://db.example:3306/app?sslmode=REQUIRED",
                "jdbc:mysql://db.example:3306/app?sslMode=DISABLED&sslmode=REQUIRED",
                "jdbc:mysql://db.example:3306/app?sslMode=DISABLED#&sslMode=REQUIRED",
                "jdbc:mysql://db.example:3306/app")) {
            assertGuardRejected(prodEnv(badUrl), "DB_SSL_MODE");
        }
    }

    /** HikariCP's driver properties override the URL's parameters, so a weaker sslMode there is refused as well. */
    @Test
    void datasourceProductionGuardChecksDriverPropertiesThatOverrideTheUrl() {
        for (String weakMode : List.of("DISABLED", "PREFERRED", " ")) {
            assertGuardRejected(
                    prodEnv(ENCRYPTED_PROD_URL).withProperty("spring.datasource.hikari.data-source-properties.sslMode", weakMode),
                    "data-source-properties.sslMode");
        }

        MockEnvironment tightened = prodEnv(ENCRYPTED_PROD_URL);
        tightened.setProperty("spring.datasource.hikari.data-source-properties.sslMode", "VERIFY_IDENTITY");
        tightened.setProperty("spring.datasource.hikari.data-source-properties.cachePrepStmts", "true");
        new DatasourceProductionGuard(tightened);

        // Outside production the driver properties are not checked.
        MockEnvironment local = new MockEnvironment();
        local.setProperty("spring.datasource.url", "jdbc:mysql://localhost:3307/db?sslMode=DISABLED");
        local.setProperty("spring.datasource.hikari.data-source-properties.sslMode", "DISABLED");
        new DatasourceProductionGuard(local);
    }

    @Test
    void datasourceProductionGuardChecksHikariJdbcUrlAndDataSourceClassNameOverrides() {
        assertGuardRejected(
                prodEnv(ENCRYPTED_PROD_URL)
                        .withProperty("spring.datasource.hikari.jdbc-url", "jdbc:mysql://db.example:3306/app?sslMode=DISABLED"),
                "spring.datasource.hikari.jdbc-url");

        new DatasourceProductionGuard(prodEnv(ENCRYPTED_PROD_URL)
                .withProperty("spring.datasource.hikari.jdbc-url", "jdbc:mysql://db.example:3306/app?sslMode=VERIFY_CA"));

        assertGuardRejected(
                prodEnv(ENCRYPTED_PROD_URL)
                        .withProperty("spring.datasource.hikari.data-source-class-name", "com.mysql.cj.jdbc.MysqlDataSource"),
                "data-source-class-name");

        MockEnvironment dsClassWithSsl = prodEnv(ENCRYPTED_PROD_URL);
        dsClassWithSsl.setProperty("spring.datasource.hikari.data-source-class-name", "com.mysql.cj.jdbc.MysqlDataSource");
        dsClassWithSsl.setProperty("spring.datasource.hikari.data-source-properties.sslMode", "VERIFY_IDENTITY");
        new DatasourceProductionGuard(dsClassWithSsl);

        assertGuardRejected(prodEnv(ENCRYPTED_PROD_URL + "&allowPublicKeyRetrieval=true"), "allowPublicKeyRetrieval");
        assertGuardRejected(
                prodEnv(ENCRYPTED_PROD_URL)
                        .withProperty("spring.datasource.hikari.jdbc-url", ENCRYPTED_PROD_URL + "&allowPublicKeyRetrieval=true"),
                "allowPublicKeyRetrieval");
        assertGuardRejected(
                prodEnv(ENCRYPTED_PROD_URL)
                        .withProperty("spring.datasource.hikari.data-source-properties.allowPublicKeyRetrieval", "1"),
                "allowPublicKeyRetrieval");

        MockEnvironment pkrExplicitFalse = prodEnv(ENCRYPTED_PROD_URL + "&allowPublicKeyRetrieval=false");
        pkrExplicitFalse.setProperty("spring.datasource.hikari.data-source-properties.allowPublicKeyRetrieval", "false");
        new DatasourceProductionGuard(pkrExplicitFalse);
    }

    @ParameterizedTest
    @CsvSource({
            "spring.datasource.password, replace-with-db-pass, DB_PASSWORD",
            "spring.flyway.enabled, false, spring.flyway.enabled",
            "spring.flyway.baseline-on-migrate, true, spring.flyway.baseline-on-migrate",
            "spring.jpa.hibernate.ddl-auto, update, spring.jpa.hibernate.ddl-auto",
            "spring.jpa.properties.hibernate.hbm2ddl.auto, create, hibernate.hbm2ddl.auto",
            "spring.jpa.properties.jakarta.persistence.schema-generation.database.action, drop-and-create, schema-generation.database.action",
            "spring.flyway.url, jdbc:mysql://db.example:3306/app?sslMode=DISABLED, spring.flyway.url",
            "spring.datasource.url, jdbc:mysql://db.example:3306/app?ssl%4dode=DISABLED, sslMode",
            "spring.datasource.hikari.data-source-properties.url, jdbc:mysql://db.example:3306/app?sslMode=DISABLED, data-source-properties.url"
    })
    void datasourceProductionGuardRejectsUnsafeProdRuntimeFlagsAndOverrides(
            String property, String value, String expectedFragment) {
        assertGuardRejected(prodEnv(ENCRYPTED_PROD_URL).withProperty(property, value), expectedFragment);
    }

    @Test
    void datasourceProductionGuardRejectsLocalOrDemoCombinedWithProd() {
        MockEnvironment localProd = prodEnv(ENCRYPTED_PROD_URL);
        localProd.setActiveProfiles("local", "prod");
        assertGuardRejected(localProd, "local");

        MockEnvironment demoProd = prodEnv(ENCRYPTED_PROD_URL);
        demoProd.setActiveProfiles("demo", "production");
        assertGuardRejected(demoProd, "demo");
    }

    @Test
    void openApiConfigRejectsEnabledApiDocsOrSwaggerUiInProd() {
        MockEnvironment local = new MockEnvironment()
                .withProperty("springdoc.api-docs.enabled", "true")
                .withProperty("springdoc.swagger-ui.enabled", "true");
        new OpenApiConfig(local);

        MockEnvironment prodSafe = new MockEnvironment();
        prodSafe.setActiveProfiles("prod");
        prodSafe.setProperty("springdoc.api-docs.enabled", "false");
        prodSafe.setProperty("springdoc.swagger-ui.enabled", "false");
        new OpenApiConfig(prodSafe);

        assertThatThrownBy(() -> new OpenApiConfig(prodSafe.withProperty("springdoc.api-docs.enabled", "true")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("springdoc.api-docs.enabled");
        MockEnvironment prodSwagger = new MockEnvironment();
        prodSwagger.setActiveProfiles("prod");
        prodSwagger.setProperty("springdoc.api-docs.enabled", "false");
        prodSwagger.setProperty("springdoc.swagger-ui.enabled", "true");
        assertThatThrownBy(() -> new OpenApiConfig(prodSwagger))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("springdoc.swagger-ui.enabled");
    }

    @Test
    void authNameValidationSeparatesBlankFromControlCharacters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertThat(validator.validateProperty(
                    new RegisterStudentRequest("   ", "a@b.com", "Strong1!", "Strong1!", "en"), "fullName"))
                    .extracting(ConstraintViolation::getMessage)
                    .containsExactly("Full name is required");
            assertThat(validator.validateProperty(
                    new AcceptAdminInviteRequest("tok", "", "Strong1!", "Strong1!", "en"), "fullName"))
                    .extracting(ConstraintViolation::getMessage)
                    .containsExactly("Full name is required");
            assertThat(validator.validateProperty(
                    new UpdateProfileRequest("Alice\u0007", null), "displayName"))
                    .extracting(ConstraintViolation::getMessage)
                    .containsExactly("Display name contains unsupported control characters.");
            assertThat(validator.validateProperty(
                    new UpdateProfileRequest("   ", null), "displayName"))
                    .extracting(ConstraintViolation::getMessage)
                    .containsExactly("Display name is required.");
        }
    }

    @Test
    void demoProfileGuardRejectsProdFlywayAndMainDatabase() {
        MockEnvironment prodEnv = new MockEnvironment().withProperty("spring.flyway.enabled", "false")
                .withProperty("app.schema-reset.database", "ai_chinese_learning_demo");
        prodEnv.setActiveProfiles("demo", "prod");
        assertThatThrownBy(() -> new DemoProfileGuard(null, prodEnv).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");

        MockEnvironment flywayEnv = new MockEnvironment().withProperty("spring.flyway.enabled", "true")
                .withProperty("app.schema-reset.database", "ai_chinese_learning_demo");
        flywayEnv.setActiveProfiles("demo");
        assertThatThrownBy(() -> new DemoProfileGuard(null, flywayEnv).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.flyway.enabled");

        MockEnvironment mainDbEnv = new MockEnvironment().withProperty("spring.flyway.enabled", "false")
                .withProperty("app.schema-reset.database", "ai_chinese_learning");
        mainDbEnv.setActiveProfiles("demo");
        assertThatThrownBy(() -> new DemoProfileGuard(null, mainDbEnv).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ai_chinese_learning");
    }

    @Test
    void pronunciationFeedbackRejectsBlankTextIdenticalMismatchTonesAndOutOfRangeScores() {
        assertThatThrownBy(() -> new PronunciationFeedback.Issue(" ", PronunciationFeedback.IssueKind.OMITTED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PronunciationFeedback.Issue("好", PronunciationFeedback.IssueKind.TONE_MISMATCH, 3, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PronunciationFeedback.ToneComparison(null, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PronunciationFeedback(-1, 80, 90, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PronunciationFeedback(80, 101, 90, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PronunciationFeedback(80, 80, 150, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshCookieOriginFilterRejectsPlainHttpRequestOriginInProduction() throws Exception {
        RefreshCookieOriginFilter prodFilter = new RefreshCookieOriginFilter(
                new ObjectMapper(), Set.of("https://app.example.com"), true);

        MockHttpServletRequest httpSameOrigin = new MockHttpServletRequest("POST", RefreshCookieOriginFilter.REFRESH_PATH);
        httpSameOrigin.setScheme("http");
        httpSameOrigin.setServerName("api.example.com");
        httpSameOrigin.setServerPort(80);
        httpSameOrigin.addHeader(HttpHeaders.ORIGIN, "http://api.example.com");
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        MockFilterChain rejectedChain = new MockFilterChain();
        prodFilter.doFilter(httpSameOrigin, rejected, rejectedChain);
        assertThat(rejected.getStatus()).isEqualTo(403);
        assertThat(rejectedChain.getRequest()).isNull();

        MockHttpServletRequest httpsSameOrigin = new MockHttpServletRequest("POST", RefreshCookieOriginFilter.REFRESH_PATH);
        httpsSameOrigin.setScheme("https");
        httpsSameOrigin.setServerName("api.example.com");
        httpsSameOrigin.setServerPort(443);
        httpsSameOrigin.addHeader(HttpHeaders.ORIGIN, "https://api.example.com");
        MockHttpServletResponse allowed = new MockHttpServletResponse();
        MockFilterChain allowedChain = new MockFilterChain();
        prodFilter.doFilter(httpsSameOrigin, allowed, allowedChain);
        assertThat(allowed.getStatus()).isEqualTo(200);
        assertThat(allowedChain.getRequest()).isNotNull();
    }
}
