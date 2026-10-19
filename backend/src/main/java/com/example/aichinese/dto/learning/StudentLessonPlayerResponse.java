package com.example.aichinese.dto.learning;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Lesson page for a signed-in student: the lesson body text ({@code basicContent}), recorded
 * videos, related live classes, the lesson's published quizzes (stage order) and the progress /
 * mastery indicator (D25). {@code moduleTitle}/{@code courseTitle} tell the learner where the lesson
 * sits (FR-LRN-02; the course is the first published course that lists the lesson or its module),
 * and {@code coverImageUrl} is the lesson's optional cover image (FR-CONT-05).
 */
@JsonPropertyOrder({
        "lessonId",
        "title",
        "description",
        "basicContent",
        "estimatedDurationMinutes",
        "completionPercent",
        "completed",
        "learningType",
        "liveClasses",
        "recordedVideos",
        "quizzes",
        "contentViewed",
        "mastery",
        "bestQuizPercentage",
        "coverImageUrl",
        "moduleId",
        "moduleTitle",
        "courseId",
        "courseTitle"
})
public record StudentLessonPlayerResponse(
        Long lessonId,
        String title,
        String description,
        String basicContent,
        Integer estimatedDurationMinutes,
        int completionPercent,
        boolean completed,
        String learningType,
        List<LiveClassResponse> liveClasses,
        List<RecordedLessonVideoResponse> recordedVideos,
        List<StudentQuizSummaryResponse> quizzes,
        boolean contentViewed,
        String mastery,
        Integer bestQuizPercentage,
        String coverImageUrl,
        Long moduleId,
        String moduleTitle,
        Long courseId,
        String courseTitle) {

    public StudentLessonPlayerResponse {
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        completionPercent = Math.max(0, Math.min(100, completionPercent));
        liveClasses = liveClasses == null ? List.of() : List.copyOf(liveClasses);
        recordedVideos = recordedVideos == null ? List.of() : List.copyOf(recordedVideos);
        quizzes = quizzes == null ? List.of() : List.copyOf(quizzes);
        mastery = mastery == null ? "NOT_STARTED" : mastery;
    }
}
