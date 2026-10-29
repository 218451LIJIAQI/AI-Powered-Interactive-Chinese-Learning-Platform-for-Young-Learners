package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.AiResponseReportReason;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.ClassContentEventType;
import com.example.aichinese.entity.enums.ClassRevisionStatus;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.LearnQuestionType;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.LevelCheckLevel;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.MeetingPlatform;
import com.example.aichinese.entity.enums.MistakeSource;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;
import com.example.aichinese.entity.enums.PlanSetupState;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.entity.enums.PracticeCheckType;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.SupportClosedBy;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.entity.enums.SupportRequesterRole;
import com.example.aichinese.entity.enums.SupportTopic;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.entity.enums.TeacherVerificationDocumentType;
import com.example.aichinese.support.FlywayMigrationFiles;

/**
 * Pins Java enum values against the Flyway migrations (V1 baseline plus every later
 * V{n} migration, read together in version order). When a later migration redefines a column,
 * only its latest definition in each table is the schema Flyway builds, so that one is checked.
 */
class SchemaEnumContractTests {

    private static final String ADD_CONSTRAINT = "add constraint";
    private static final Pattern ENUM_VALUE = Pattern.compile("'([^']+)'");
    private static final Pattern TABLE_STATEMENT = Pattern.compile(
            "\\b(?:create|alter)\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?(\\w+)`?",
            Pattern.CASE_INSENSITIVE);

    @Test
    void databaseEnumColumnsContainEveryApplicationEnumValue() throws IOException {
        String migration = FlywayMigrationFiles.combined();

        assertLatestColumnEnumDefinitions(migration, "content_status", ContentStatus.values());
        assertLatestColumnEnumDefinitions(migration, "status_before_archive", ContentStatus.values());
        assertLatestColumnEnumDefinitions(migration, "learning_type", LearningType.values());
        assertLatestColumnEnumDefinitions(migration, "difficulty", ActivityDifficulty.values());
        assertLatestColumnEnumDefinitions(migration, "enrolment_status", LiveClassEnrolmentStatus.values());
        assertLatestColumnEnumDefinitions(migration, "live_class_status", LiveClassStatus.values());
        assertLatestColumnEnumDefinitions(migration, "meeting_platform", MeetingPlatform.values());
        assertLatestColumnEnumDefinitions(migration, "notification_status", NotificationStatus.values());
        assertLatestColumnEnumDefinitions(migration, "notification_type", NotificationType.values());
        assertLatestColumnEnumDefinitions(migration, "item_type", PackageItemType.values());
        assertLatestColumnEnumDefinitions(migration, "source_type", RecordedVideoSourceType.values());
        assertLatestColumnEnumDefinitions(migration, "name", RoleName.values());
        assertLatestColumnEnumDefinitions(migration, "teacher_status", TeacherStatus.values());
        assertLatestColumnEnumDefinitions(migration, "learning_stage", LearningStage.values());
        assertLatestColumnEnumDefinitions(migration, "check_type", PracticeCheckType.values());
        assertLatestColumnEnumDefinitions(migration, "feature_key", AiFeatureKey.values());
        assertLatestColumnEnumDefinitions(migration, "request_status", SupportRequestStatus.values());
        assertLatestColumnEnumDefinitions(migration, "request_topic", SupportTopic.values());
        assertLatestColumnEnumDefinitions(migration, "requester_role", SupportRequesterRole.values());
        assertLatestColumnEnumDefinitions(migration, "closed_by", SupportClosedBy.values());
        assertLatestColumnEnumDefinitions(migration, "lesson_kind", LearnLessonKind.values());
        assertLatestColumnEnumDefinitions(migration, "question_kind", LearnQuestionType.values());
        assertLatestColumnEnumDefinitions(migration, "publish_status", PublishStatus.values());
        assertLatestColumnEnumDefinitions(migration, "mistake_source", MistakeSource.values());
        assertLatestColumnEnumDefinitions(migration, "answer_kind", AnswerKind.values());
        assertLatestColumnEnumDefinitions(migration, "skill", LevelCheckSkill.values());
        assertLatestColumnEnumDefinitions(migration, "mandarin_level", LevelCheckLevel.values());
        assertLatestColumnEnumDefinitions(migration, "practice_type", PracticeType.values());
        assertLatestColumnEnumDefinitions(migration, "revision_status", ClassRevisionStatus.values());
        assertLatestColumnEnumDefinitions(migration, "activity_type", ClassActivityType.values());
        assertLatestColumnEnumDefinitions(migration, "event_type", ClassContentEventType.values());
        assertLatestColumnEnumDefinitions(migration, "plan_setup_state", PlanSetupState.values());
        assertLatestColumnEnumDefinitions(migration, "plan_reason", PlanReason.values());
        assertLatestColumnEnumDefinitions(migration, "plan_pace", PlanPace.values());
        assertLatestColumnEnumDefinitions(migration, "self_level", PlanSelfLevel.values());
        assertLatestColumnEnumDefinitions(migration, "placed_level", MandarinLevel.values());
        assertLatestColumnEnumDefinitions(migration, "target_level", MandarinLevel.values());
        assertLatestColumnEnumDefinitions(migration, "step_kind", PlanStepKind.values());
    }

    @Test
    void finalDomainCheckConstraintsContainEveryApplicationEnumValue() throws IOException {
        String migration = FlywayMigrationFiles.combined();

        assertCheckValues(migration, "chk_ai_chat_messages_sender_role", AiChatSenderRole.values());
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

    private static Set<String> extractEnumValues(String rawEnumBody) {
        String normalizedEnumBody = rawEnumBody.replace("''", "'");
        Set<String> values = new LinkedHashSet<>();
        Matcher valueMatcher = ENUM_VALUE.matcher(normalizedEnumBody);
        while (valueMatcher.find()) {
            values.add(valueMatcher.group(1).trim().toUpperCase(Locale.ROOT));
        }
        return values;
    }

    /** The latest enum definition of the column in every table that has it must match the application enum. */
    private static void assertLatestColumnEnumDefinitions(
            String migration,
            String columnName,
            Enum<?>[] applicationEnumValues) {
        Map<String, Set<String>> latestByTable = new LinkedHashMap<>();
        Matcher enumMatcher = columnEnumPattern(columnName).matcher(migration);
        while (enumMatcher.find()) {
            latestByTable.put(tableBefore(migration, enumMatcher.start()), extractEnumValues(enumMatcher.group(1)));
        }

        assertThat(latestByTable)
                .as("The consolidated Flyway baseline must define at least one enum column named " + columnName)
                .isNotEmpty();
        latestByTable.forEach((table, databaseValues) -> assertThat(databaseValues)
                .as("Latest database enum values for " + table + "." + columnName)
                .containsExactlyInAnyOrderElementsOf(enumNames(applicationEnumValues)));
    }

    /** The table of the nearest "create table" or "alter table" statement before the position. */
    private static String tableBefore(String migration, int position) {
        Matcher tableMatcher = TABLE_STATEMENT.matcher(migration);
        String table = null;
        while (tableMatcher.find() && tableMatcher.start() < position) {
            table = tableMatcher.group(1).toLowerCase(Locale.ROOT);
        }
        if (table == null) {
            throw new AssertionError("No table statement precedes the enum definition at " + position);
        }
        return table;
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
