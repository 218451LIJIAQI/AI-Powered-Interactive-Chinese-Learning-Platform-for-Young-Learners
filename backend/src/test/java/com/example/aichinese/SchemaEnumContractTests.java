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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.AiResponseReportReason;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.MeetingPlatform;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.entity.enums.PracticeAiFeedbackStatus;
import com.example.aichinese.entity.enums.PracticeReviewStatus;
import com.example.aichinese.entity.enums.PracticeSubmissionType;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.entity.enums.TeacherVerificationDocumentType;

/**
 * Pins Java enum values against the Flyway migrations (V1 baseline plus every later
 * V{n} migration, read together in version order).
 */
class SchemaEnumContractTests {

    private static final String ADD_CONSTRAINT = "add constraint";
    private static final Path MIGRATION_DIRECTORY = Path.of("src", "main", "resources", "db", "migration");
    private static final Pattern MIGRATION_VERSION = Pattern.compile("^V(\\d+)__.+\\.sql$");
    private static final Pattern NOTIFICATION_TYPE_ENUM = Pattern.compile(
            "notification_type\\s+enum\\s*\\((.*?)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ENUM_VALUE = Pattern.compile("'([^']+)'");

    @Test
    void latestNotificationTypeDatabaseEnumContainsEveryApplicationValue() throws IOException {
        Set<String> databaseValues = latestNotificationTypeEnumValues(readConsolidatedMigration());

        assertThat(databaseValues)
                .containsExactlyInAnyOrderElementsOf(enumNames(NotificationType.values()));
    }

    @Test
    void databaseEnumColumnsContainEveryApplicationEnumValue() throws IOException {
        String migration = readConsolidatedMigration();

        assertEveryColumnEnumDefinition(migration, "content_status", ContentStatus.values());
        assertEveryColumnEnumDefinition(migration, "learning_type", LearningType.values());
        assertEveryColumnEnumDefinition(migration, "difficulty", ActivityDifficulty.values());
        assertEveryColumnEnumDefinition(migration, "enrolment_status", LiveClassEnrolmentStatus.values());
        assertEveryColumnEnumDefinition(migration, "live_class_status", LiveClassStatus.values());
        assertEveryColumnEnumDefinition(migration, "meeting_platform", MeetingPlatform.values());
        assertEveryColumnEnumDefinition(migration, "notification_status", NotificationStatus.values());
        assertEveryColumnEnumDefinition(migration, "item_type", PackageItemType.values());
        assertEveryColumnEnumDefinition(migration, "source_type", RecordedVideoSourceType.values());
        assertEveryColumnEnumDefinition(migration, "name", RoleName.values());
        assertEveryColumnEnumDefinition(migration, "teacher_status", TeacherStatus.values());
        assertEveryColumnEnumDefinition(migration, "learning_stage", LearningStage.values());
        assertEveryColumnEnumDefinition(migration, "submission_type", PracticeSubmissionType.values());
        assertEveryColumnEnumDefinition(migration, "review_status", PracticeReviewStatus.values());
    }

    @Test
    void finalDomainCheckConstraintsContainEveryApplicationEnumValue() throws IOException {
        String migration = readConsolidatedMigration();

        assertCheckValues(migration, "chk_ai_chat_messages_sender_role", AiChatSenderRole.values());
        assertCheckValues(migration, "chk_ai_usage_logs_status", AiRequestStatus.values());
        assertCheckValues(migration, "chk_ai_response_reports_status", AiResponseReportStatus.values());
        assertCheckValues(migration, "chk_ai_response_reports_reason_category", AiResponseReportReason.values());
        assertCheckValues(migration, "chk_teacher_verification_documents_type", TeacherVerificationDocumentType.values());
        assertCheckValues(migration, "chk_practice_submissions_ai_feedback_status", PracticeAiFeedbackStatus.values());
        assertCheckValues(
                migration,
                "chk_teacher_verification_reviews_final_decision",
                TeacherStatus.APPROVED,
                TeacherStatus.REJECTED);
    }

    /** Every versioned migration concatenated in Flyway version order. */
    private static String readConsolidatedMigration() throws IOException {
        List<Path> migrations;
        try (Stream<Path> files = Files.list(MIGRATION_DIRECTORY)) {
            migrations = files
                    .filter(path -> MIGRATION_VERSION.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(SchemaEnumContractTests::migrationVersion))
                    .toList();
        }
        StringBuilder combined = new StringBuilder();
        for (Path migration : migrations) {
            combined.append(Files.readString(migration)).append('\n');
        }
        return combined.toString();
    }

    private static int migrationVersion(Path migration) {
        Matcher matcher = MIGRATION_VERSION.matcher(migration.getFileName().toString());
        if (!matcher.matches()) {
            throw new AssertionError("Not a versioned Flyway migration: " + migration);
        }
        return Integer.parseInt(matcher.group(1));
    }

    @Test
    void ageBandColumnsAreDroppedAndReportReasonIsRenamedInTheLatestSchema() throws IOException {
        String migration = readConsolidatedMigration().toLowerCase(Locale.ROOT);

        for (String table : List.of("flashcards", "matching_games", "listening_exercises",
                "tracing_exercises", "drag_drop_activities", "story_activities")) {
            assertThat(migration).contains("alter table " + table + " drop column age_band");
            assertThat(migration).contains("alter table " + table + " drop index idx_" + table + "_age_band");
        }
        assertThat(checkValues(readConsolidatedMigration(), "chk_ai_response_reports_reason_category"))
                .contains("INAPPROPRIATE_CONTENT")
                .doesNotContain("AGE_INAPPROPRIATE");
    }

    @Test
    void removedCommerceNotificationTypesAreGoneFromTheLatestSchema() throws IOException {
        assertThat(latestNotificationTypeEnumValues(readConsolidatedMigration()))
                .doesNotContain("PAYMENT_STATUS", "REFUND_STATUS", "PAYOUT_STATUS");
    }

    private static Set<String> latestNotificationTypeEnumValues(String migration) {
        Set<String> latestValues = null;
        Matcher enumMatcher = NOTIFICATION_TYPE_ENUM.matcher(migration);
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

    private static void assertEveryColumnEnumDefinition(
            String migration,
            String columnName,
            Enum<?>[] applicationEnumValues) {
        Matcher enumMatcher = columnEnumPattern(columnName).matcher(migration);
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

    private static Pattern columnEnumPattern(String columnName) {
        return Pattern.compile(
                "\\b" + Pattern.quote(columnName) + "\\s+enum\\s*\\((.*?)\\)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
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
        String lowerMigration = migration.toLowerCase(Locale.ROOT);
        // A later migration may drop and re-add a check constraint, so the latest definition wins.
        int constraintStart = lowerMigration.lastIndexOf(ADD_CONSTRAINT + " " + constraintName.toLowerCase(Locale.ROOT));
        if (constraintStart < 0) {
            throw new AssertionError("Could not find check constraint " + constraintName);
        }
        int nextConstraintStart = lowerMigration.indexOf(ADD_CONSTRAINT, constraintStart + 1);
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
