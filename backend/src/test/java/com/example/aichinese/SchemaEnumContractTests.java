package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.AiResponseReportReason;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.TeacherEarningStatus;
import com.example.aichinese.entity.enums.TeacherVerificationDocumentType;

/**
 * Pins Java enum values against the single consolidated Flyway baseline.
 */
class SchemaEnumContractTests {

    private static final Path CONSOLIDATED_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V1__consolidated_schema.sql");
    private static final Pattern NOTIFICATION_TYPE_ENUM = Pattern.compile(
            "notification_type\\s+enum\\s*\\((.*?)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ENUM_VALUE = Pattern.compile("'([^']+)'");

    @Test
    void latestNotificationTypeDatabaseEnumContainsEveryApplicationValue() throws IOException {
        Set<String> databaseValues = latestNotificationTypeEnumValues();
        Set<String> applicationValues = Arrays.stream(NotificationType.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(databaseValues)
                .containsExactlyInAnyOrderElementsOf(applicationValues);
    }

    @Test
    void finalDomainCheckConstraintsContainEveryApplicationEnumValue() throws IOException {
        String migration = Files.readString(CONSOLIDATED_MIGRATION);

        assertCheckValues(migration, "chk_ai_chat_messages_sender_role", AiChatSenderRole.values());
        assertCheckValues(migration, "chk_teacher_earnings_status", TeacherEarningStatus.values());
        assertCheckValues(migration, "chk_ai_usage_logs_status", AiRequestStatus.values());
        assertCheckValues(migration, "chk_ai_response_reports_status", AiResponseReportStatus.values());
        assertCheckValues(migration, "chk_ai_response_reports_reason_category", AiResponseReportReason.values());
        assertCheckValues(migration, "chk_teacher_verification_documents_type", TeacherVerificationDocumentType.values());
    }

    private Set<String> latestNotificationTypeEnumValues() throws IOException {
        Set<String> latestValues = null;
        Matcher enumMatcher = NOTIFICATION_TYPE_ENUM.matcher(Files.readString(CONSOLIDATED_MIGRATION));
        while (enumMatcher.find()) {
            latestValues = extractEnumValues(enumMatcher.group(1));
        }

        assertThat(latestValues)
                .as("The consolidated Flyway baseline must define notifications.notification_type")
                .isNotNull();
        return latestValues;
    }

    private static Set<String> extractEnumValues(String rawEnumBody) {
        Set<String> values = new LinkedHashSet<>();
        Matcher valueMatcher = ENUM_VALUE.matcher(rawEnumBody);
        while (valueMatcher.find()) {
            values.add(valueMatcher.group(1).trim().toUpperCase(Locale.ROOT));
        }
        return values;
    }

    private static void assertCheckValues(String migration, String constraintName, Enum<?>[] applicationEnumValues) {
        Set<String> databaseValues = checkValues(migration, constraintName);
        Set<String> applicationValues = Arrays.stream(applicationEnumValues)
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(databaseValues)
                .as("Database check values for " + constraintName)
                .containsExactlyInAnyOrderElementsOf(applicationValues);
    }

    private static Set<String> checkValues(String migration, String constraintName) {
        int constraintStart = migration.toLowerCase(Locale.ROOT)
                .indexOf(constraintName.toLowerCase(Locale.ROOT));
        if (constraintStart < 0) {
            throw new AssertionError("Could not find check constraint " + constraintName);
        }
        int nextConstraintStart = migration.toLowerCase(Locale.ROOT).indexOf("add constraint", constraintStart + 1);
        int statementEnd = migration.indexOf(';', constraintStart);
        int constraintEnd = nextConstraintStart >= 0 && nextConstraintStart < statementEnd
                ? nextConstraintStart
                : statementEnd;
        if (constraintEnd < 0) {
            throw new AssertionError("Could not find statement terminator for check constraint " + constraintName);
        }
        return extractEnumValues(migration.substring(constraintStart, constraintEnd));
    }
}
