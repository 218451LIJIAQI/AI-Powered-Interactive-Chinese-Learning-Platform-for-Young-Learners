package com.example.aichinese.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reads the versioned Flyway migrations ({@code V{n}__*.sql}) in the order Flyway applies them. */
public final class FlywayMigrationFiles {

    public static final Path MIGRATION_DIRECTORY = Path.of("src", "main", "resources", "db", "migration");
    private static final Pattern MIGRATION_VERSION = Pattern.compile("^V(\\d+)__.+\\.sql$");

    private FlywayMigrationFiles() {
    }

    public static List<Path> inVersionOrder() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIRECTORY)) {
            return files
                    .filter(path -> MIGRATION_VERSION.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(FlywayMigrationFiles::version))
                    .toList();
        }
    }

    /** All versioned migrations concatenated in version order, i.e. the schema Flyway would build. */
    public static String combined() throws IOException {
        StringBuilder combined = new StringBuilder();
        for (Path migration : inVersionOrder()) {
            combined.append(Files.readString(migration)).append('\n');
        }
        return combined.toString();
    }

    public static int version(Path migration) {
        Matcher matcher = MIGRATION_VERSION.matcher(migration.getFileName().toString());
        if (!matcher.matches()) {
            throw new AssertionError("Not a versioned Flyway migration: " + migration);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
