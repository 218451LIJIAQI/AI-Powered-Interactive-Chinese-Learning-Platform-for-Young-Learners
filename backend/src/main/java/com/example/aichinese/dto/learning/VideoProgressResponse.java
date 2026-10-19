package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "lessonId",
        "recordedVideoId",
        "watchedSeconds",
        "totalSeconds",
        "progressPercentage",
        "completed",
        "lastWatchedAt",
        "updatedAt"
})
public record VideoProgressResponse(
        Long id,
        Long lessonId,
        Long recordedVideoId,
        Integer watchedSeconds,
        Integer totalSeconds,
        Integer progressPercentage,
        boolean completed,
        LocalDateTime lastWatchedAt,
        LocalDateTime updatedAt) {

    public VideoProgressResponse {
        Objects.requireNonNull(lessonId, "lessonId must not be null");
    }
}
