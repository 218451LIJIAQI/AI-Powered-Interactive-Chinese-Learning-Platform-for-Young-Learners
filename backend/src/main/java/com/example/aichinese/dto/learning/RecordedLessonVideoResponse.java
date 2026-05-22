package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

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
}
