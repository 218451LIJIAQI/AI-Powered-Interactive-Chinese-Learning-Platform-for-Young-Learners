package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One student on an approved teacher's roster. No e-mail or other account detail is exposed.
 * {@code atRisk} / {@code needsSupport} are true when the student's quiz average on this teacher's
 * quizzes is low or the student has been inactive ({@code supportReasons} LOW_QUIZ_SCORE,
 * INACTIVE); receiving feedback never flags a student. {@code weakAreas} are lesson titles of
 * wrong answers on this teacher's quizzes; {@code lessonMastery} covers this teacher's lessons (D25).
 */
@JsonPropertyOrder({
        "studentId",
        "userId",
        "fullName",
        "displayName",
        "currentLevel",
        "progressPercent",
        "quizAveragePercent",
        "atRisk",
        "needsSupport",
        "supportReasons",
        "weakAreas",
        "latestFeedbackType",
        "latestFeedbackAt",
        "latestActivityAt",
        "joinedAt",
        "lessonMastery"
})
public record TeacherStudentSummaryResponse(
        Long studentId,
        Long userId,
        String fullName,
        String displayName,
        String currentLevel,
        Integer progressPercent,
        Double quizAveragePercent,
        boolean atRisk,
        boolean needsSupport,
        List<String> supportReasons,
        List<String> weakAreas,
        String latestFeedbackType,
        LocalDateTime latestFeedbackAt,
        LocalDateTime latestActivityAt,
        LocalDateTime joinedAt,
        List<LessonMasteryItem> lessonMastery) {

    public TeacherStudentSummaryResponse {
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(progressPercent, "progressPercent must not be null");
        Objects.requireNonNull(joinedAt, "joinedAt must not be null");
        supportReasons = supportReasons == null ? List.of() : List.copyOf(supportReasons);
        weakAreas = weakAreas == null ? List.of() : List.copyOf(weakAreas);
        lessonMastery = lessonMastery == null ? List.of() : List.copyOf(lessonMastery);
    }

    /** Mastery indicator of one of the teacher's lessons for this student. */
    @JsonPropertyOrder({ "lessonId", "lessonTitle", "completionPercent", "mastery", "bestQuizPercentage" })
    public record LessonMasteryItem(
            Long lessonId,
            String lessonTitle,
            int completionPercent,
            String mastery,
            Integer bestQuizPercentage) {

        public LessonMasteryItem {
            Objects.requireNonNull(lessonId, "lessonId must not be null");
            Objects.requireNonNull(mastery, "mastery must not be null");
        }
    }
}
