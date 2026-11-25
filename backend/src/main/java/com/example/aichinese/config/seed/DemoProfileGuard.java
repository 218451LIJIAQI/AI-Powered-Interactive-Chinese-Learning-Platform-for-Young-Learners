package com.example.aichinese.config.seed;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.example.aichinese.config.ProductionProfiles;

/**
 * Refuses to run demo seeders against a production profile, a Flyway-managed database, the default persistent
 * database ({@code ai_chinese_learning}), or any database other than {@code app.schema-reset.database}.
 */
@Component
@Profile("demo")
public class DemoProfileGuard implements InitializingBean {

    private static final String DEFAULT_PERSISTENT_DB = "ai_chinese_learning";

    private final DataSource dataSource;
    private final Environment environment;

    public DemoProfileGuard(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() throws SQLException {
        if (ProductionProfiles.isActive(environment)) {
            throw new IllegalStateException("The demo profile must not be active when the prod profile is active.");
        }
        Binder binder = Binder.get(environment);
        if (binder.bind("spring.flyway.enabled", Boolean.class).orElse(true)) {
            throw new IllegalStateException(
                    "Refusing to run demo seeders with spring.flyway.enabled=true on a managed database.");
        }
        String resetDatabase = binder.bind("app.schema-reset.database", String.class).orElse("").strip();
        if (resetDatabase.isEmpty() || DEFAULT_PERSISTENT_DB.equalsIgnoreCase(resetDatabase)) {
            throw new IllegalStateException("Refusing to run demo seeders against '" + resetDatabase
                    + "': app.schema-reset.database must name an isolated demo/test database.");
        }
        try (Connection connection = dataSource.getConnection()) {
            String connected = connection.getCatalog();
            if (!resetDatabase.equals(connected) || DEFAULT_PERSISTENT_DB.equalsIgnoreCase(connected)) {
                throw new IllegalStateException("Refusing to seed demo data into database '" + connected
                        + "': expected isolated database '" + resetDatabase + "'.");
            }
        }
    }
}
