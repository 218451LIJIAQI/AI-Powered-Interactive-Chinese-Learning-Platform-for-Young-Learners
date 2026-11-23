package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.AiResponseReportReason;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.AnswerConfidence;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.HintMode;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.LearnQuestionType;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.LevelCheckLevel;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.MistakeSource;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;
import com.example.aichinese.entity.enums.PlanSetupState;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.entity.enums.PracticeCheckType;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.SupportClosedBy;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.entity.enums.SupportRequesterRole;
import com.example.aichinese.entity.enums.SupportTopic;
import com.example.aichinese.support.FlywayMigrationFiles;

/**
 * Pins Java enum values against the enum columns and CHECK constraints of the MySQL schema the migrations build: the
 * baseline's CREATE TABLE statements, followed by the later migrations' ALTER TABLE (MODIFY, ADD COLUMN, DROP COLUMN)
 * and DROP TABLE statements, in version order.
 */
class SchemaEnumContractTests {

    private static final Pattern ENUM_VALUE = Pattern.compile("'([^']+)'");
    private static final Pattern STATEMENT_TABLE = Pattern.compile(
            "^(CREATE|ALTER|DROP)\\s+TABLE\\s+(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?`?(\\w+)`?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENUM_COLUMN = Pattern.compile(
            "`?(\\w+)`?\\s+enum\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROPPED_COLUMN = Pattern.compile(
            "DROP\\s+(?:COLUMN\\s+|(?!(?:INDEX|KEY|PRIMARY|FOREIGN|CONSTRAINT|CHECK)\\b))`?(\\w+)`?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MODIFIED_COLUMN = Pattern.compile(
            "MODIFY\\s+(?:COLUMN\\s+)?`?(\\w+)`?\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANGED_COLUMN = Pattern.compile(
            "CHANGE\\s+(?:COLUMN\\s+)?`?(\\w+)`?\\s+`?(\\w+)`?\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHECK_CONSTRAINT = Pattern.compile(
            "CONSTRAINT\\s+`?(\\w+)`?\\s+CHECK\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROPPED_CHECK = Pattern.compile(
            "DROP\\s+(?:CHECK|CONSTRAINT)\\s+`?(\\w+)`?",
            Pattern.CASE_INSENSITIVE);

    /** Table -> enum column -> its values, as the migrations leave them. */
    private static final Map<String, Map<String, Set<String>>> ENUM_COLUMNS = new LinkedHashMap<>();
    /** Table -> check constraint name -> its balanced CHECK(...) body, as the migrations leave them. */
    private static final Map<String, Map<String, String>> CHECK_CONSTRAINTS = new LinkedHashMap<>();

    @BeforeAll
    static void readSchema() throws IOException {
        ENUM_COLUMNS.clear();
        CHECK_CONSTRAINTS.clear();
        for (Path migration : FlywayMigrationFiles.inVersionOrder()) {
            String text = Files.readString(migration);
            // Strip whole-line SQL "--" comments before splitting semicolon-delimited statements.
            String code = text.lines()
                    .filter(line -> !line.strip().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            for (String statement : code.split(";")) {
                apply(statement.strip());
            }
        }
    }

    private static void apply(String statement) {
        Matcher table = STATEMENT_TABLE.matcher(statement);
        if (!table.find()) {
            return;
        }
        String action = table.group(1).toUpperCase(Locale.ROOT);
        String name = table.group(2).toLowerCase(Locale.ROOT);
        switch (action) {
            case "DROP" -> {
                ENUM_COLUMNS.remove(name);
                CHECK_CONSTRAINTS.remove(name);
            }
            case "CREATE" -> {
                ENUM_COLUMNS.put(name, enumColumns(statement));
                CHECK_CONSTRAINTS.put(name, checkConstraints(statement));
            }
            default -> {
                Map<String, Set<String>> columns = ENUM_COLUMNS.computeIfAbsent(name, ignored -> new LinkedHashMap<>());
                Map<String, String> checks = CHECK_CONSTRAINTS.computeIfAbsent(name, ignored -> new LinkedHashMap<>());
                Matcher dropped = DROPPED_COLUMN.matcher(statement);
                while (dropped.find()) {
                    columns.remove(dropped.group(1).toLowerCase(Locale.ROOT));
                }
                Matcher droppedCheck = DROPPED_CHECK.matcher(statement);
                while (droppedCheck.find()) {
                    checks.remove(droppedCheck.group(1).toLowerCase(Locale.ROOT));
                }
                // Drop columns converted away from ENUM, and remove renamed source columns on CHANGE.
                Matcher modified = MODIFIED_COLUMN.matcher(statement);
                while (modified.find()) {
                    if (!"enum".equalsIgnoreCase(modified.group(2))) {
                        columns.remove(modified.group(1).toLowerCase(Locale.ROOT));
                    }
                }
                Matcher changed = CHANGED_COLUMN.matcher(statement);
                while (changed.find()) {
                    String oldCol = changed.group(1).toLowerCase(Locale.ROOT);
                    String newCol = changed.group(2).toLowerCase(Locale.ROOT);
                    String newType = changed.group(3);
                    if (!oldCol.equals(newCol) || !"enum".equalsIgnoreCase(newType)) {
                        columns.remove(oldCol);
                    }
                    if (!"enum".equalsIgnoreCase(newType)) {
                        columns.remove(newCol);
                    }
                }
                columns.putAll(enumColumns(statement));
                checks.putAll(checkConstraints(statement));
            }
        }
    }

    private static Map<String, Set<String>> enumColumns(String statement) {
        Map<String, Set<String>> columns = new LinkedHashMap<>();
        Matcher column = ENUM_COLUMN.matcher(statement);
        while (column.find()) {
            columns.put(column.group(1).toLowerCase(Locale.ROOT), extractEnumValues(column.group(2)));
        }
        return columns;
    }

    private static Map<String, String> checkConstraints(String statement) {
        Map<String, String> checks = new LinkedHashMap<>();
        Matcher matcher = CHECK_CONSTRAINT.matcher(statement);
        while (matcher.find()) {
            String constraintName = matcher.group(1).toLowerCase(Locale.ROOT);
            int openParen = matcher.end() - 1;
            int depth = 0;
            int end = openParen;
            boolean inSingleQuote = false;
            for (int i = openParen; i < statement.length(); i++) {
                char c = statement.charAt(i);
                if (inSingleQuote) {
                    if (c == '\'') {
                        if (i + 1 < statement.length() && statement.charAt(i + 1) == '\'') {
                            i++;
                        } else {
                            inSingleQuote = false;
                        }
                    }
                } else if (c == '\'') {
                    inSingleQuote = true;
                } else if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        end = i + 1;
                        break;
                    }
                }
            }
            assertThat(depth == 0 && end > openParen)
                    .as("CHECK constraint %s must have balanced parentheses", constraintName)
                    .isTrue();
            checks.put(constraintName, statement.substring(openParen, end));
        }
        return checks;
    }

    @Test
    void databaseEnumColumnsContainEveryApplicationEnumValue() {
        Set<String> verifiedColumns = new LinkedHashSet<>();
        assertColumnEnumDefinitions("content_status", ContentStatus.values(), verifiedColumns);
        assertColumnEnumDefinitions("status_before_archive", ContentStatus.values(), verifiedColumns);
        assertColumnEnumDefinitions("difficulty", ActivityDifficulty.values(), verifiedColumns);
        assertColumnEnumDefinitions("notification_status", NotificationStatus.values(), verifiedColumns);
        assertColumnEnumDefinitions("notification_type", NotificationType.values(), verifiedColumns);
        assertColumnEnumDefinitions("source_type", RecordedVideoSourceType.values(), verifiedColumns);
        assertColumnEnumDefinitions("name", RoleName.values(), verifiedColumns);
        assertColumnEnumDefinitions("learning_stage", LearningStage.values(), verifiedColumns);
        assertColumnEnumDefinitions("check_type", PracticeCheckType.values(), verifiedColumns);
        assertColumnEnumDefinitions("feature_key", AiFeatureKey.values(), verifiedColumns);
        assertColumnEnumDefinitions("request_status", SupportRequestStatus.values(), verifiedColumns);
        assertColumnEnumDefinitions("request_topic", SupportTopic.values(), verifiedColumns);
        assertColumnEnumDefinitions("requester_role", SupportRequesterRole.values(), verifiedColumns);
        assertColumnEnumDefinitions("closed_by", SupportClosedBy.values(), verifiedColumns);
        assertColumnEnumDefinitions("lesson_kind", LearnLessonKind.values(), verifiedColumns);
        assertColumnEnumDefinitions("question_kind", LearnQuestionType.values(), verifiedColumns);
        assertColumnEnumDefinitions("publish_status", PublishStatus.values(), verifiedColumns);
        assertColumnEnumDefinitions("mistake_source", MistakeSource.values(), verifiedColumns);
        assertColumnEnumDefinitions("answer_kind", AnswerKind.values(), verifiedColumns);
        assertColumnEnumDefinitions("skill", LevelCheckSkill.values(), verifiedColumns);
        assertColumnEnumDefinitions("mandarin_level", LevelCheckLevel.values(), verifiedColumns);
        assertColumnEnumDefinitions("practice_type", PracticeType.values(), verifiedColumns);
        assertColumnEnumDefinitions("activity_type", ClassActivityType.values(), verifiedColumns);
        assertColumnEnumDefinitions("plan_setup_state", PlanSetupState.values(), verifiedColumns);
        assertColumnEnumDefinitions("plan_reason", PlanReason.values(), verifiedColumns);
        assertColumnEnumDefinitions("plan_pace", PlanPace.values(), verifiedColumns);
        assertColumnEnumDefinitions("self_level", PlanSelfLevel.values(), verifiedColumns);
        assertColumnEnumDefinitions("placed_level", MandarinLevel.values(), verifiedColumns);
        assertColumnEnumDefinitions("target_level", MandarinLevel.values(), verifiedColumns);
        assertColumnEnumDefinitions("step_kind", PlanStepKind.values(), verifiedColumns);
        assertColumnEnumDefinitions("confidence", AnswerConfidence.values(), verifiedColumns);
        assertColumnEnumDefinitions("hint_mode", HintMode.values(), verifiedColumns);

        Set<String> allSchemaEnumColumns = ENUM_COLUMNS.values().stream()
                .flatMap(columns -> columns.keySet().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(verifiedColumns)
                .as("Every remaining database ENUM column must be covered by this contract test")
                .containsExactlyInAnyOrderElementsOf(allSchemaEnumColumns);
    }

    /** The columns of the removed teacher features went with their tables (V3). */
    @Test
    void theTeacherEraColumnsAreGone() {
        assertThat(ENUM_COLUMNS).doesNotContainKeys(
                "teacher_profiles", "teacher_verification_documents", "teacher_verification_reviews", "live_classes",
                "class_questions", "teacher_feedback", "class_content_events");
        assertThat(ENUM_COLUMNS.get("course_packages")).doesNotContainKey("revision_status");
        assertThat(ENUM_COLUMNS.get("quizzes")).doesNotContainKey("learning_stage");
    }

    @Test
    void domainCheckConstraintsContainEveryApplicationEnumValue() {
        assertCheckValues("chk_ai_chat_messages_sender_role", AiChatSenderRole.values());
        assertCheckValues("chk_ai_usage_logs_status", AiRequestStatus.values());
        assertCheckValues("chk_ai_response_reports_status", AiResponseReportStatus.values());
        assertCheckValues("chk_ai_response_reports_reason_category", AiResponseReportReason.values());
        assertCheckValues("chk_quiz_questions_question_type", QuizQuestionType.values());
    }

    private static Set<String> extractEnumValues(String rawEnumBody) {
        return ENUM_VALUE.matcher(rawEnumBody).results()
                .map(match -> match.group(1))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Every enum column with this name, in whichever tables have one, must list exactly the application values. */
    private static void assertColumnEnumDefinitions(
            String columnName, Enum<?>[] applicationEnumValues, Set<String> verifiedColumns) {
        verifiedColumns.add(columnName);
        List<String> expected = enumNames(applicationEnumValues);
        assertThat(ENUM_COLUMNS.entrySet())
                .filteredOn(table -> table.getValue().containsKey(columnName))
                .as("Tables with an enum column named %s", columnName)
                .isNotEmpty()
                .allSatisfy(table -> assertThat(table.getValue().get(columnName))
                        .as("Database enum values for %s.%s", table.getKey(), columnName)
                        .containsExactlyInAnyOrderElementsOf(expected));
    }

    /** The constraint as the last migration that (re)defines it leaves it. */
    private static void assertCheckValues(String constraintName, Enum<?>... expectedValues) {
        String key = constraintName.toLowerCase(Locale.ROOT);
        List<String> definitions = CHECK_CONSTRAINTS.values().stream()
                .map(tableChecks -> tableChecks.get(key))
                .filter(Objects::nonNull)
                .toList();
        assertThat(definitions).as("The schema must have the check constraint " + constraintName).hasSize(1);
        assertThat(extractEnumValues(definitions.getFirst()))
                .as("Database check values for " + constraintName)
                .containsExactlyInAnyOrderElementsOf(enumNames(expectedValues));
    }

    private static List<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
