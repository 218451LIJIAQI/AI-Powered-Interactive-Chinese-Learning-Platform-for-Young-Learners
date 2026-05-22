package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.enums.NotificationType;

class SchemaEnumContractTests {

    private static final Pattern MIGRATION_VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");
    private static final Pattern NOTIFICATION_TYPE_ENUM = Pattern.compile(
            "notification_type\\s+enum\\s*\\((.*?)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ENUM_VALUE = Pattern.compile("'([^']+)'");

    @Test
    void latestNotificationTypeDatabaseEnumContainsEveryApplicationValue() throws IOException {
        Set<String> databaseValues = latestNotificationTypeEnumValues();
        Set<String> applicationValues = Arrays.stream(NotificationType.values())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(databaseValues)
                .containsExactlyInAnyOrderElementsOf(applicationValues);
    }

    private Set<String> latestNotificationTypeEnumValues() throws IOException {
        Path migrationDirectory = Path.of("src", "main", "resources", "db", "migration");
        List<Path> migrations = Files.list(migrationDirectory)
                .filter(Files::isRegularFile)
                .filter(path -> MIGRATION_VERSION.matcher(path.getFileName().toString()).matches())
                .sorted(Comparator.comparingInt(SchemaEnumContractTests::migrationVersion))
                .toList();

        Set<String> latestValues = null;
        for (Path migration : migrations) {
            Matcher enumMatcher = NOTIFICATION_TYPE_ENUM.matcher(Files.readString(migration));
            while (enumMatcher.find()) {
                latestValues = extractEnumValues(enumMatcher.group(1));
            }
        }

        assertThat(latestValues)
                .as("At least one Flyway migration must define notifications.notification_type")
                .isNotNull();
        return latestValues;
    }

    private static int migrationVersion(Path path) {
        Matcher matcher = MIGRATION_VERSION.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unexpected migration filename: " + path.getFileName());
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static Set<String> extractEnumValues(String rawEnumBody) {
        Set<String> values = new LinkedHashSet<>();
        Matcher valueMatcher = ENUM_VALUE.matcher(rawEnumBody);
        while (valueMatcher.find()) {
            values.add(valueMatcher.group(1).trim().toUpperCase(Locale.ROOT));
        }
        return values;
    }
}
