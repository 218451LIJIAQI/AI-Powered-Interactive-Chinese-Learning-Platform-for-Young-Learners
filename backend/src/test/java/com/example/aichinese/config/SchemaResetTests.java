package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.aichinese.support.MySqlSchema;

/**
 * The reset that empties the demo (and test) database before Hibernate rebuilds it: every table goes, foreign keys and
 * tables no entity knows included, but only in the database it is meant for. Runs against the MySQL server of the tests,
 * in a database of its own (created and dropped here, named after TEST_DB_NAME so that concurrent runs do not meet).
 */
class SchemaResetTests {

    private MySqlSchema database;

    @BeforeEach
    void createADatabaseLeftByAnOlderVersion() {
        database = MySqlSchema.recreate("schema", "_reset_check");
        JdbcTemplate jdbc = database.jdbc();
        // A table no entity maps any more, referencing one that still exists: Hibernate's create cannot drop either.
        jdbc.execute("CREATE TABLE users (id bigint PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE practice_quizzes (id bigint PRIMARY KEY, created_by_user_id bigint,
                  CONSTRAINT fk_old FOREIGN KEY (created_by_user_id) REFERENCES users (id))
                """);
    }

    @AfterEach
    void dropDatabase() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void emptiesTheDatabaseItIsFor() throws Exception {
        new SchemaResetConfig.SchemaReset(database.dataSource(), database.name()).afterPropertiesSet();

        assertThat(tables()).isEmpty();
        // Foreign key checks are back on for the connection that did the reset.
        assertThat(database.jdbc().queryForObject("select @@foreign_key_checks", Integer.class)).isEqualTo(1);
    }

    @Test
    void refusesAnyOtherDatabase() {
        assertThatThrownBy(() -> new SchemaResetConfig.SchemaReset(database.dataSource(), "ai_chinese_learning").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Refusing to empty database " + database.name() + ": the schema reset is for ai_chinese_learning only.");

        assertThat(tables()).containsExactlyInAnyOrder("practice_quizzes", "users");
    }

    @Test
    void refusesToRunWhenProductionProfileIsActive() {
        assertThatThrownBy(() -> new SchemaResetConfig.SchemaReset(database.dataSource(), database.name(), true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod profile");

        assertThat(tables()).containsExactlyInAnyOrder("practice_quizzes", "users");
    }

    private List<String> tables() {
        return database.jdbc().queryForList(
                "select table_name from information_schema.tables where table_schema = database()", String.class);
    }
}
