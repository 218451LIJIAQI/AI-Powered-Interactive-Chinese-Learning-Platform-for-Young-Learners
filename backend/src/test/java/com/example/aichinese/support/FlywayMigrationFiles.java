package com.example.aichinese.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.flywaydb.core.api.MigrationVersion;

/**
 * Reads the versioned Flyway migrations ({@code V5__*.sql}, and steps such as {@code V5_1__*.sql}) in the order Flyway
 * applies them.
 */
public final class FlywayMigrationFiles {

    public static final Path MIGRATION_DIRECTORY = Path.of("src", "main", "resources", "db", "migration");
    public static final List<String> EXPECTED_VERSIONS =
            List.of("1", "2", "3", "4", "5", "5.1", "5.2", "6", "7", "8", "9", "10", "11", "12", "13", "14");
    private static final Pattern MIGRATION_VERSION = Pattern.compile("^V(\\d+(?:_\\d+)*)__.+\\.sql$");

    private FlywayMigrationFiles() {
    }

    public static List<Path> inVersionOrder() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIRECTORY)) {
            return files
                    .filter(path -> MIGRATION_VERSION.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(path -> MigrationVersion.fromVersion(version(path))))
                    .toList();
        }
    }

    /** The version as Flyway writes it: {@code 5} for {@code V5__...sql}, {@code 5.1} for {@code V5_1__...sql}. */
    public static String version(Path migration) {
        Matcher matcher = MIGRATION_VERSION.matcher(migration.getFileName().toString());
        if (!matcher.matches()) {
            throw new AssertionError("Not a versioned Flyway migration: " + migration);
        }
        return matcher.group(1).replace('_', '.');
    }
}
