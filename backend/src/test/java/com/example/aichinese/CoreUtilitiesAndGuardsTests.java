package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.example.aichinese.config.DatasourceProductionGuard;
import com.example.aichinese.entity.value.PronunciationFeedback;
import com.example.aichinese.util.CsvWriter;
import com.example.aichinese.util.FileNames;
import com.example.aichinese.util.NetworkAddresses;

/** Focused unit tests for core utilities and production guards strengthened during codebase hardening. */
class CoreUtilitiesAndGuardsTests {

    @Test
    void csvWriterQuotesAndNeutralizesLeadingSpacesAndNewlinesBeforeFormulas() {
        String csv = new CsvWriter()
                .row(Arrays.asList("plain", null, LocalDateTime.of(2026, 5, 1, 9, 5, 3)))
                .row(List.of("=1+1", " =HYPERLINK(\"http://x\")", "\u00A0+SUM(A1)", "\n=CMD", "a, \"b\""))
                .toString();

        assertThat(csv).isEqualTo(
                "plain,,2026-05-01 09:05:03\r\n"
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

    @Test
    void datasourceProductionGuardRequiresEncryptedSslModeInProd() {
        MockEnvironment local = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://localhost:3307/db?sslMode=DISABLED");
        new DatasourceProductionGuard(local);

        MockEnvironment prodRequired = new MockEnvironment();
        prodRequired.setActiveProfiles("prod");
        prodRequired.setProperty("spring.datasource.url", "jdbc:mysql://db.example:3306/app?sslMode=REQUIRED");
        new DatasourceProductionGuard(prodRequired);

        MockEnvironment prodVerifyIdentity = new MockEnvironment();
        prodVerifyIdentity.setActiveProfiles("production");
        prodVerifyIdentity.setProperty("spring.datasource.url", "jdbc:mysql://db.example:3306/app?sslMode=VERIFY_IDENTITY");
        new DatasourceProductionGuard(prodVerifyIdentity);

        for (String badUrl : List.of(
                "jdbc:mysql://db.example:3306/app?sslMode=DISABLED",
                "jdbc:mysql://db.example:3306/app?sslMode=PREFERRED",
                "jdbc:mysql://db.example:3306/app?sslMode=REQUIRED&sslMode=",
                "jdbc:mysql://db.example:3306/app?sslmode=REQUIRED",
                "jdbc:mysql://db.example:3306/app?sslMode=DISABLED&sslmode=REQUIRED",
                "jdbc:mysql://db.example:3306/app?sslMode=DISABLED#&sslMode=REQUIRED",
                "jdbc:mysql://db.example:3306/app")) {
            MockEnvironment badProd = new MockEnvironment();
            badProd.setActiveProfiles("prod");
            badProd.setProperty("spring.datasource.url", badUrl);
            assertThatThrownBy(() -> new DatasourceProductionGuard(badProd))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_SSL_MODE");
        }
    }

    /** HikariCP's driver properties override the URL's parameters, so a weaker sslMode there is refused as well. */
    @Test
    void datasourceProductionGuardChecksDriverPropertiesThatOverrideTheUrl() {
        String encryptedUrl = "jdbc:mysql://db.example:3306/app?sslMode=REQUIRED";
        for (String weakMode : List.of("DISABLED", "PREFERRED", " ")) {
            MockEnvironment overridden = new MockEnvironment();
            overridden.setActiveProfiles("prod");
            overridden.setProperty("spring.datasource.url", encryptedUrl);
            overridden.setProperty("spring.datasource.hikari.data-source-properties.sslMode", weakMode);
            assertThatThrownBy(() -> new DatasourceProductionGuard(overridden))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("data-source-properties.sslMode");
        }

        MockEnvironment tightened = new MockEnvironment();
        tightened.setActiveProfiles("prod");
        tightened.setProperty("spring.datasource.url", encryptedUrl);
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
        String encryptedUrl = "jdbc:mysql://db.example:3306/app?sslMode=REQUIRED";

        MockEnvironment weakHikariUrl = new MockEnvironment();
        weakHikariUrl.setActiveProfiles("prod");
        weakHikariUrl.setProperty("spring.datasource.url", encryptedUrl);
        weakHikariUrl.setProperty("spring.datasource.hikari.jdbc-url", "jdbc:mysql://db.example:3306/app?sslMode=DISABLED");
        assertThatThrownBy(() -> new DatasourceProductionGuard(weakHikariUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.hikari.jdbc-url");

        MockEnvironment strongHikariUrl = new MockEnvironment();
        strongHikariUrl.setActiveProfiles("prod");
        strongHikariUrl.setProperty("spring.datasource.url", encryptedUrl);
        strongHikariUrl.setProperty("spring.datasource.hikari.jdbc-url", "jdbc:mysql://db.example:3306/app?sslMode=VERIFY_CA");
        new DatasourceProductionGuard(strongHikariUrl);

        MockEnvironment dsClassWithoutSsl = new MockEnvironment();
        dsClassWithoutSsl.setActiveProfiles("prod");
        dsClassWithoutSsl.setProperty("spring.datasource.url", encryptedUrl);
        dsClassWithoutSsl.setProperty("spring.datasource.hikari.data-source-class-name", "com.mysql.cj.jdbc.MysqlDataSource");
        assertThatThrownBy(() -> new DatasourceProductionGuard(dsClassWithoutSsl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("data-source-class-name");

        MockEnvironment dsClassWithSsl = new MockEnvironment();
        dsClassWithSsl.setActiveProfiles("prod");
        dsClassWithSsl.setProperty("spring.datasource.url", encryptedUrl);
        dsClassWithSsl.setProperty("spring.datasource.hikari.data-source-class-name", "com.mysql.cj.jdbc.MysqlDataSource");
        dsClassWithSsl.setProperty("spring.datasource.hikari.data-source-properties.sslMode", "VERIFY_IDENTITY");
        new DatasourceProductionGuard(dsClassWithSsl);

        MockEnvironment pkrInUrl = new MockEnvironment();
        pkrInUrl.setActiveProfiles("prod");
        pkrInUrl.setProperty("spring.datasource.url", encryptedUrl + "&allowPublicKeyRetrieval=true");
        assertThatThrownBy(() -> new DatasourceProductionGuard(pkrInUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowPublicKeyRetrieval");

        MockEnvironment pkrInHikariUrl = new MockEnvironment();
        pkrInHikariUrl.setActiveProfiles("prod");
        pkrInHikariUrl.setProperty("spring.datasource.url", encryptedUrl);
        pkrInHikariUrl.setProperty("spring.datasource.hikari.jdbc-url", encryptedUrl + "&allowPublicKeyRetrieval=true");
        assertThatThrownBy(() -> new DatasourceProductionGuard(pkrInHikariUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowPublicKeyRetrieval");

        MockEnvironment pkrInDriverProps = new MockEnvironment();
        pkrInDriverProps.setActiveProfiles("prod");
        pkrInDriverProps.setProperty("spring.datasource.url", encryptedUrl);
        pkrInDriverProps.setProperty("spring.datasource.hikari.data-source-properties.allowPublicKeyRetrieval", "1");
        assertThatThrownBy(() -> new DatasourceProductionGuard(pkrInDriverProps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowPublicKeyRetrieval");

        MockEnvironment pkrExplicitFalse = new MockEnvironment();
        pkrExplicitFalse.setActiveProfiles("prod");
        pkrExplicitFalse.setProperty("spring.datasource.url", encryptedUrl + "&allowPublicKeyRetrieval=false");
        pkrExplicitFalse.setProperty("spring.datasource.hikari.data-source-properties.allowPublicKeyRetrieval", "false");
        new DatasourceProductionGuard(pkrExplicitFalse);
    }

    @Test
    void pronunciationFeedbackRejectsBlankTextAndIdenticalMismatchTones() {
        assertThatThrownBy(() -> new PronunciationFeedback.Issue(" ", PronunciationFeedback.IssueKind.OMITTED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PronunciationFeedback.Issue("好", PronunciationFeedback.IssueKind.TONE_MISMATCH, 3, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PronunciationFeedback.ToneComparison(null, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
