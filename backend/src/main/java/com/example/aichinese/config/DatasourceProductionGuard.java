package com.example.aichinese.config;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Ensures production deployments keep MySQL transport encryption, non-placeholder DB credentials, Flyway migration
 * discipline, and Hibernate schema validation enabled before any {@link DataSource} connects.
 *
 * <p>{@code application.yml}'s {@code prod | production} document defaults {@code DB_SSL_MODE} to {@code REQUIRED} so
 * operators can tighten to {@code VERIFY_CA} or {@code VERIFY_IDENTITY}; this guard refuses startup if a weaker or
 * blank mode (such as {@code DISABLED}, {@code PREFERRED}, a miscased key, or a trailing empty {@code sslMode=}) is
 * supplied in production, or if local/demo profiles are combined with production.</p>
 */
@Configuration(proxyBeanMethods = false)
public class DatasourceProductionGuard {

    private static final Profiles NON_PRODUCTION_PROFILES = Profiles.of("local", "demo");
    private static final Set<String> ENCRYPTED_SSL_MODES = Set.of("REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY");
    /** Connection properties HikariCP hands to the driver; Connector/J applies them over the URL's parameters. */
    private static final String DRIVER_PROPERTIES = "spring.datasource.hikari.data-source-properties";
    private static final String HIKARI_JDBC_URL = "spring.datasource.hikari.jdbc-url";
    private static final String HIKARI_DATA_SOURCE_CLASS = "spring.datasource.hikari.data-source-class-name";
    private static final String FLYWAY_URL = "spring.flyway.url";

    public DatasourceProductionGuard(Environment environment) {
        if (!ProductionProfiles.isActive(environment)) {
            return;
        }
        if (environment.acceptsProfiles(NON_PRODUCTION_PROFILES)) {
            throw new IllegalStateException(
                    "The local and demo profiles must not be active when the prod profile is active.");
        }

        Binder binder = Binder.get(environment);
        String datasourceUrl = binder.bind("spring.datasource.url", String.class).orElse("");
        validateEncryptedJdbcUrl(datasourceUrl, "DB_SSL_MODE", "allowPublicKeyRetrieval");

        String dbPassword = binder.bind("spring.datasource.password", String.class).orElse(null);
        if (dbPassword == null || dbPassword.isBlank() || ProductionProfiles.isPlaceholder(dbPassword)) {
            throw new IllegalStateException(
                    "DB_PASSWORD must be configured with a non-placeholder password when the prod profile is active.");
        }

        if (!binder.bind("spring.flyway.enabled", Boolean.class).orElse(true)) {
            throw new IllegalStateException(
                    "spring.flyway.enabled must be true when the prod profile is active.");
        }
        if (binder.bind("spring.flyway.baseline-on-migrate", Boolean.class).orElse(false)) {
            throw new IllegalStateException(
                    "spring.flyway.baseline-on-migrate must be false when the prod profile is active.");
        }
        String ddlAuto = binder.bind("spring.jpa.hibernate.ddl-auto", String.class).orElse("validate").strip();
        if (!"validate".equalsIgnoreCase(ddlAuto)) {
            throw new IllegalStateException(
                    "spring.jpa.hibernate.ddl-auto must be validate when the prod profile is active.");
        }

        Map<String, String> jpaProperties = binder
                .bind("spring.jpa.properties", Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
        for (Map.Entry<String, String> entry : jpaProperties.entrySet()) {
            String normalizedKey = normalizePropertyKey(entry.getKey());
            String value = entry.getValue() == null ? "" : entry.getValue().strip();
            if ("hibernate.hbm2ddl.auto".equals(normalizedKey) && !"validate".equalsIgnoreCase(value)) {
                throw new IllegalStateException(
                        "spring.jpa.properties.hibernate.hbm2ddl.auto must be validate when the prod profile is active.");
            }
            if (("jakarta.persistence.schema-generation.database.action".equals(normalizedKey)
                    || "javax.persistence.schema-generation.database.action".equals(normalizedKey))
                    && !value.isEmpty() && !"none".equalsIgnoreCase(value)) {
                throw new IllegalStateException(
                        "spring.jpa.properties." + entry.getKey() + " must be none when the prod profile is active.");
            }
        }

        validateOptionalEncryptedJdbcUrl(binder.bind(FLYWAY_URL, String.class).orElse(""), FLYWAY_URL);
        validateOptionalEncryptedJdbcUrl(binder.bind(HIKARI_JDBC_URL, String.class).orElse(""), HIKARI_JDBC_URL);
        Map<String, String> driverProperties = binder
                .bind(DRIVER_PROPERTIES, Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
        boolean hasEncryptedDriverSslMode = false;
        for (Map.Entry<String, String> entry : driverProperties.entrySet()) {
            if ("sslMode".equalsIgnoreCase(entry.getKey())) {
                String value = entry.getValue() == null ? "" : entry.getValue().strip();
                if (!isEncryptedSslMode(value)) {
                    throw new IllegalStateException(DRIVER_PROPERTIES
                            + ".sslMode must be REQUIRED, VERIFY_CA, or VERIFY_IDENTITY when the prod profile is active.");
                }
                hasEncryptedDriverSslMode = true;
            } else if ("allowPublicKeyRetrieval".equalsIgnoreCase(entry.getKey())) {
                if (enablesPublicKeyRetrieval(entry.getValue() == null ? "" : entry.getValue())) {
                    throw new IllegalStateException(DRIVER_PROPERTIES
                            + ".allowPublicKeyRetrieval must be false when the prod profile is active.");
                }
            } else if ("url".equalsIgnoreCase(entry.getKey())) {
                if (validateOptionalEncryptedJdbcUrl(entry.getValue(), DRIVER_PROPERTIES + ".url")) {
                    hasEncryptedDriverSslMode = true;
                }
            }
        }
        String dataSourceClassName = binder.bind(HIKARI_DATA_SOURCE_CLASS, String.class).orElse("");
        if (!dataSourceClassName.isBlank() && !hasEncryptedDriverSslMode) {
            throw new IllegalStateException(DRIVER_PROPERTIES
                    + ".sslMode must be REQUIRED, VERIFY_CA, or VERIFY_IDENTITY when "
                    + HIKARI_DATA_SOURCE_CLASS + " is configured in prod.");
        }
    }

    private static String normalizePropertyKey(String key) {
        return key == null ? "" : key.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean validateOptionalEncryptedJdbcUrl(String rawUrl, String propertyLabel) {
        String value = rawUrl == null ? "" : rawUrl.strip();
        if (value.isBlank()) {
            return false;
        }
        validateEncryptedJdbcUrl(value, propertyLabel, propertyLabel + " allowPublicKeyRetrieval");
        return true;
    }

    private static boolean isEncryptedSslMode(String mode) {
        return mode != null && ENCRYPTED_SSL_MODES.contains(mode.toUpperCase(Locale.ROOT));
    }

    private static void validateEncryptedJdbcUrl(String jdbcUrl, String sslLabel, String pkrLabel) {
        if (!isEncryptedSslMode(extractQueryParam(jdbcUrl, "sslMode"))) {
            throw new IllegalStateException(
                    sslLabel + " must set sslMode to REQUIRED, VERIFY_CA, or VERIFY_IDENTITY when the prod profile is active.");
        }
        if (enablesPublicKeyRetrieval(extractQueryParam(jdbcUrl, "allowPublicKeyRetrieval"))) {
            throw new IllegalStateException(
                    pkrLabel + " must be false when the prod profile is active.");
        }
    }

    /** Connector/J treats {@code true}, {@code yes} and {@code 1} as true; require explicit {@code false} when set. */
    private static boolean enablesPublicKeyRetrieval(String value) {
        return value != null && !"false".equalsIgnoreCase(value.strip());
    }

    /**
     * Parses only the JDBC query segment ({@code ?} to {@code #}), percent-decoding both keys and values as MySQL
     * Connector/J does, and matching the exact case-sensitive key so encoded or miscased keys cannot bypass the check.
     */
    private static String extractQueryParam(String url, String key) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return null;
        }
        int fragmentStart = url.indexOf('#', queryStart + 1);
        String query = fragmentStart < 0 ? url.substring(queryStart + 1) : url.substring(queryStart + 1, fragmentStart);
        String value = null;
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0) {
                String decodedKey = decodePercent(param.substring(0, eq));
                if (key.equals(decodedKey)) {
                    value = decodePercent(param.substring(eq + 1)).strip();
                }
            }
        }
        return value;
    }

    private static String decodePercent(String raw) {
        if (raw.indexOf('%') < 0) {
            return raw;
        }
        try {
            return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidEscape) {
            return raw;
        }
    }

    /** Forces every {@link DataSource} bean to initialize after this guard so unencrypted connections never open. */
    @Bean
    static BeanFactoryPostProcessor datasourceDependsOnProductionGuard() {
        return new AbstractDependsOnBeanFactoryPostProcessor(DataSource.class, DatasourceProductionGuard.class) {
        };
    }
}
