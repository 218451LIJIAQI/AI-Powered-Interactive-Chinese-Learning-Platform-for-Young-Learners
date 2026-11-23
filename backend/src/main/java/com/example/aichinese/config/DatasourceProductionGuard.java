package com.example.aichinese.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Ensures production deployments keep MySQL transport encryption enabled before any {@link DataSource} connects.
 *
 * <p>{@code application.yml}'s {@code prod | production} document defaults {@code DB_SSL_MODE} to {@code REQUIRED} so
 * operators can tighten to {@code VERIFY_CA} or {@code VERIFY_IDENTITY}; this guard refuses startup if a weaker,
 * miscased, or blank mode (such as {@code DISABLED}, {@code PREFERRED}, or a trailing empty {@code sslMode=}) is
 * supplied in production.</p>
 */
@Configuration(proxyBeanMethods = false)
public class DatasourceProductionGuard {

    private static final String GUARD_BEAN_NAME = "datasourceProductionGuard";
    private static final Set<String> ENCRYPTED_SSL_MODES = Set.of("REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY");
    /** Connection properties HikariCP hands to the driver; Connector/J applies them over the URL's parameters. */
    private static final String DRIVER_PROPERTIES = "spring.datasource.hikari.data-source-properties";
    private static final String HIKARI_JDBC_URL = "spring.datasource.hikari.jdbc-url";
    private static final String HIKARI_DATA_SOURCE_CLASS = "spring.datasource.hikari.data-source-class-name";

    public DatasourceProductionGuard(Environment environment) {
        if (!ProductionProfiles.isActive(environment)) {
            return;
        }
        String datasourceUrl = environment.getProperty("spring.datasource.url", "");
        String mode = extractQueryParam(datasourceUrl, "sslMode");
        if (mode == null || !ENCRYPTED_SSL_MODES.contains(mode.toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "DB_SSL_MODE must be REQUIRED, VERIFY_CA, or VERIFY_IDENTITY when the prod profile is active.");
        }
        if (enablesPublicKeyRetrieval(extractQueryParam(datasourceUrl, "allowPublicKeyRetrieval"))) {
            throw new IllegalStateException(
                    "allowPublicKeyRetrieval must be false when the prod profile is active.");
        }
        Binder binder = Binder.get(environment);
        String hikariJdbcUrl = binder.bind(HIKARI_JDBC_URL, String.class).orElse("");
        if (!hikariJdbcUrl.isBlank()) {
            String hikariMode = extractQueryParam(hikariJdbcUrl, "sslMode");
            if (hikariMode == null || !ENCRYPTED_SSL_MODES.contains(hikariMode.toUpperCase(Locale.ROOT))) {
                throw new IllegalStateException(HIKARI_JDBC_URL
                        + " must set sslMode to REQUIRED, VERIFY_CA, or VERIFY_IDENTITY when the prod profile is active.");
            }
            if (enablesPublicKeyRetrieval(extractQueryParam(hikariJdbcUrl, "allowPublicKeyRetrieval"))) {
                throw new IllegalStateException(HIKARI_JDBC_URL
                        + " must keep allowPublicKeyRetrieval=false when the prod profile is active.");
            }
        }
        Map<String, String> driverProperties = binder
                .bind(DRIVER_PROPERTIES, Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
        boolean hasEncryptedDriverSslMode = false;
        for (Map.Entry<String, String> entry : driverProperties.entrySet()) {
            if ("sslMode".equalsIgnoreCase(entry.getKey())) {
                String value = entry.getValue() == null ? "" : entry.getValue().strip().toUpperCase(Locale.ROOT);
                if (!ENCRYPTED_SSL_MODES.contains(value)) {
                    throw new IllegalStateException(DRIVER_PROPERTIES
                            + ".sslMode must be REQUIRED, VERIFY_CA, or VERIFY_IDENTITY when the prod profile is active.");
                }
                hasEncryptedDriverSslMode = true;
            } else if ("allowPublicKeyRetrieval".equalsIgnoreCase(entry.getKey())) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                if (enablesPublicKeyRetrieval(value)) {
                    throw new IllegalStateException(DRIVER_PROPERTIES
                            + ".allowPublicKeyRetrieval must be false when the prod profile is active.");
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

    /** Connector/J treats {@code true}, {@code yes} and {@code 1} as true; require explicit {@code false} when set. */
    private static boolean enablesPublicKeyRetrieval(String value) {
        return value != null && !"false".equalsIgnoreCase(value.strip());
    }

    /**
     * Parses only the JDBC query segment ({@code ?} to {@code #}) using MySQL Connector/J's exact case-sensitive
     * property key so miscased keys or fragments cannot bypass the check.
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
            if (eq > 0 && key.equals(param.substring(0, eq))) {
                value = param.substring(eq + 1).strip();
            }
        }
        return value;
    }

    /** Forces every {@link DataSource} bean to initialize after this guard so unencrypted connections never open. */
    @Bean
    static BeanFactoryPostProcessor datasourceDependsOnProductionGuard() {
        return beanFactory -> {
            for (String name : beanFactory.getBeanNamesForType(DataSource.class, true, false)) {
                BeanDefinition definition = beanFactory.getBeanDefinition(name);
                String[] existing = definition.getDependsOn();
                if (existing == null || existing.length == 0) {
                    definition.setDependsOn(GUARD_BEAN_NAME);
                } else if (!Arrays.asList(existing).contains(GUARD_BEAN_NAME)) {
                    String[] updated = Arrays.copyOf(existing, existing.length + 1);
                    updated[existing.length] = GUARD_BEAN_NAME;
                    definition.setDependsOn(updated);
                }
            }
        };
    }
}
