package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "lessonId",
        "packageId",
        "title",
        "description",
        "sourceType",
        "externalVideoUrl",
        "streamUrl",
        "durationSeconds",
        "sortOrder",
        "active",
        "createdAt",
        "updatedAt"
})
public record RecordedLessonVideoResponse(
        Long id,
        Long lessonId,
        Long packageId,
        String title,
        String description,
        String sourceType,
        String externalVideoUrl,
        String streamUrl,
        Integer durationSeconds,
        Integer sortOrder,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public RecordedLessonVideoResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(sortOrder, "sortOrder must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
