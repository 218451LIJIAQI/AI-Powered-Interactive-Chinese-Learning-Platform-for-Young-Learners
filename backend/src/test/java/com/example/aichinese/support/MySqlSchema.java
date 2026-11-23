package com.example.aichinese.support;

import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** A dedicated MySQL schema for a test that needs to prepare its own migration or reset state. */
public final class MySqlSchema implements AutoCloseable {

    private static final Pattern SAFE_SCHEMA_NAME = Pattern.compile("[A-Za-z0-9_]{1,64}");

    private final String name;
    private final SingleConnectionDataSource dataSource;
    private final JdbcTemplate jdbc;

    private MySqlSchema(String name) {
        this.name = name;
        dataSource = connect(name);
        jdbc = new JdbcTemplate(dataSource);
    }

    public static MySqlSchema recreate(String fallback, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            throw new IllegalArgumentException("Schema suffix must not be blank.");
        }
        String name = config("TEST_DB_NAME", fallback) + suffix;
        if (!SAFE_SCHEMA_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Unsafe MySQL schema name: " + name);
        }
        String identifier = quotedName(name);
        onServer("DROP DATABASE IF EXISTS " + identifier,
                "CREATE DATABASE " + identifier + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        return new MySqlSchema(name);
    }

    public String name() {
        return name;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    public void close() {
        try {
            dataSource.destroy();
        } finally {
            onServer("DROP DATABASE IF EXISTS " + quotedName(name));
        }
    }

    private static String quotedName(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    private static void onServer(String... statements) {
        SingleConnectionDataSource root = connect("");
        try {
            JdbcTemplate jdbc = new JdbcTemplate(root);
            for (String statement : statements) {
                jdbc.execute(statement);
            }
        } finally {
            root.destroy();
        }
    }

    private static SingleConnectionDataSource connect(String schema) {
        String url = "jdbc:mysql://" + config("DB_HOST", "localhost") + ":" + config("DB_PORT", "3307") + "/" + schema
                + "?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Singapore&useUnicode=true&characterEncoding=utf8";
        var source = new SingleConnectionDataSource(url, config("DB_USERNAME", "root"), config("DB_PASSWORD", ""), true);
        source.setAutoCommit(true);
        return source;
    }

    private static String config(String name, String fallback) {
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
