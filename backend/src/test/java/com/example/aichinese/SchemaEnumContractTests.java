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

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.AiResponseReportReason;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.MeetingPlatform;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.entity.enums.PayoutStatus;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;
import com.example.aichinese.entity.enums.RefundStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherEarningStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
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
    void databaseEnumColumnsContainEveryApplicationEnumValue() throws IOException {
        String migration = Files.readString(CONSOLIDATED_MIGRATION);

        assertEveryColumnEnumDefinition(migration, "access_type", AccessType.values());
        assertEveryColumnEnumDefinition(migration, "content_status", ContentStatus.values());
        assertEveryColumnEnumDefinition(migration, "learning_type", LearningType.values());
        assertEveryColumnEnumDefinition(migration, "age_band", AgeBand.values());
        assertEveryColumnEnumDefinition(migration, "difficulty", ActivityDifficulty.values());
        assertEveryColumnEnumDefinition(migration, "enrolment_status", LiveClassEnrolmentStatus.values());
        assertEveryColumnEnumDefinition(migration, "live_class_status", LiveClassStatus.values());
        assertEveryColumnEnumDefinition(migration, "meeting_platform", MeetingPlatform.values());
        assertEveryColumnEnumDefinition(migration, "notification_status", NotificationStatus.values());
        assertEveryColumnEnumDefinition(migration, "payment_status", PaymentStatus.values());
        assertEveryColumnEnumDefinition(migration, "item_type", PackageItemType.values());
        assertEveryColumnEnumDefinition(migration, "source_type", RecordedVideoSourceType.values());
        assertEveryColumnEnumDefinition(migration, "refund_status", RefundStatus.values());
        assertEveryColumnEnumDefinition(migration, "payout_status", PayoutStatus.values());
        assertEveryColumnEnumDefinition(migration, "name", RoleName.values());
        assertEveryColumnEnumDefinition(migration, "teacher_status", TeacherStatus.values());
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
        assertCheckValues(
                migration,
                "chk_teacher_verification_reviews_final_decision",
                TeacherStatus.APPROVED,
                TeacherStatus.REJECTED);
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
        String normalizedEnumBody = rawEnumBody.replace("''", "'");
        Set<String> values = new LinkedHashSet<>();
        Matcher valueMatcher = ENUM_VALUE.matcher(normalizedEnumBody);
        while (valueMatcher.find()) {
            values.add(valueMatcher.group(1).trim().toUpperCase(Locale.ROOT));
        }
        return values;
    }

    private static void assertEveryColumnEnumDefinition(String migration, String columnName, Enum<?>[] applicationEnumValues) {
        Pattern columnEnum = Pattern.compile(
                "\\b" + Pattern.quote(columnName) + "\\s+enum\\s*\\((.*?)\\)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher enumMatcher = columnEnum.matcher(migration);
        int definitionCount = 0;
        while (enumMatcher.find()) {
            definitionCount++;
            assertThat(extractEnumValues(enumMatcher.group(1)))
                    .as("Database enum values for column " + columnName + " definition #" + definitionCount)
                    .containsExactlyInAnyOrderElementsOf(enumNames(applicationEnumValues));
        }

        assertThat(definitionCount)
                .as("The consolidated Flyway baseline must define at least one enum column named " + columnName)
                .isPositive();
    }

    private static void assertCheckValues(String migration, String constraintName, Enum<?>... expectedValues) {
        assertCheckValues(migration, constraintName, enumNames(expectedValues));
    }

    private static void assertCheckValues(String migration, String constraintName, Set<String> expectedValues) {
        Set<String> databaseValues = checkValues(migration, constraintName);

        assertThat(databaseValues)
                .as("Database check values for " + constraintName)
                .containsExactlyInAnyOrderElementsOf(expectedValues);
    }

    private static Set<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
