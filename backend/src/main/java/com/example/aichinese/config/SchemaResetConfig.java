package com.example.aichinese.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Drops the demo or test tables before Hibernate creates the schema. This includes tables and foreign keys from
 * earlier versions that the current entities no longer map. The connection must select the database named by
 * {@code app.schema-reset.database} (DEMO_DB_NAME or TEST_DB_NAME).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${app.schema-reset.database:}' != '' and !${spring.flyway.enabled:true}")
public class SchemaResetConfig {

    static final String BEAN_NAME = "schemaReset";

    /** Hibernate's schema is built after the reset. */
    @Bean
    static EntityManagerFactoryDependsOnPostProcessor schemaResetBeforeHibernate() {
        return new EntityManagerFactoryDependsOnPostProcessor(BEAN_NAME);
    }

    @Bean(BEAN_NAME)
    SchemaReset schemaReset(
            DataSource dataSource,
            Environment environment,
            @Value("${app.schema-reset.database}") String database) {
        return new SchemaReset(dataSource, database, ProductionProfiles.isActive(environment));
    }

    /** Drops every table of the named database, foreign keys and all. */
    public static final class SchemaReset implements InitializingBean {

        private static final Logger log = LoggerFactory.getLogger(SchemaReset.class);

        private final DataSource dataSource;
        private final String database;
        private final boolean productionProfileActive;

        SchemaReset(DataSource dataSource, String database) {
            this(dataSource, database, false);
        }

        SchemaReset(DataSource dataSource, String database, boolean productionProfileActive) {
            this.dataSource = dataSource;
            this.database = database == null ? "" : database.strip();
            this.productionProfileActive = productionProfileActive;
        }

        @Override
        public void afterPropertiesSet() throws SQLException {
            if (productionProfileActive) {
                throw new IllegalStateException("Refusing to reset schema while the prod profile is active.");
            }
            if (database.isEmpty()) {
                throw new IllegalStateException("app.schema-reset.database must not be blank.");
            }
            // One connection: FOREIGN_KEY_CHECKS is a session setting.
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                String connected = connection.getCatalog();
                if (!database.equals(connected)) {
                    throw new IllegalStateException("Refusing to empty database " + connected
                            + ": the schema reset is for " + database + " only.");
                }
                List<String> tables = new ArrayList<>();
                try (ResultSet rows = connection.getMetaData().getTables(connected, null, "%", new String[] { "TABLE" })) {
                    while (rows.next()) {
                        tables.add(rows.getString("TABLE_NAME"));
                    }
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                Throwable primaryFailure = null;
                try {
                    for (String table : tables) {
                        statement.execute("DROP TABLE `" + table.replace("`", "``") + "`");
                    }
                } catch (Throwable failure) {
                    primaryFailure = failure;
                    throw failure;
                } finally {
                    try {
                        statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                    } catch (SQLException restoreFailure) {
                        if (primaryFailure != null) {
                            primaryFailure.addSuppressed(restoreFailure);
                        } else {
                            throw restoreFailure;
                        }
                    }
                }
                log.info("Emptied database {} ({} tables) before Hibernate rebuilds it.", connected, tables.size());
            }
        }
    }
}
