package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "studentId",
        "studentName",
        "recordedVideoId",
        "recordedVideoTitle",
        "progressPercentage",
        "completed",
        "watchedSeconds",
        "totalSeconds",
        "lastWatchedAt"
})
public record LessonVideoProgressSummaryResponse(
        Long studentId,
        String studentName,
        Long recordedVideoId,
        String recordedVideoTitle,
        Integer progressPercentage,
        boolean completed,
        Integer watchedSeconds,
        Integer totalSeconds,
        String lastWatchedAt) {

    public LessonVideoProgressSummaryResponse {
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(studentName, "studentName must not be null");
    }
}
