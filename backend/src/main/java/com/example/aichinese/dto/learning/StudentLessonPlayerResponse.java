package com.example.aichinese.dto.learning;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "lessonId",
        "title",
        "description",
        "basicContent",
        "estimatedDurationMinutes",
        "completionPercent",
        "completed",
        "locked",
        "learningType",
        "liveClasses",
        "recordedVideos"
})
public record StudentLessonPlayerResponse(
        Long lessonId,
        String title,
        String description,
        String basicContent,
        Integer estimatedDurationMinutes,
        int completionPercent,
        boolean completed,
        boolean locked,
        String learningType,
        List<LiveClassResponse> liveClasses,
        List<RecordedLessonVideoResponse> recordedVideos) {

    public StudentLessonPlayerResponse {
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        completionPercent = Math.max(0, Math.min(100, completionPercent));
        liveClasses = liveClasses == null ? List.of() : List.copyOf(liveClasses);
        recordedVideos = recordedVideos == null ? List.of() : List.copyOf(recordedVideos);
    }
}
