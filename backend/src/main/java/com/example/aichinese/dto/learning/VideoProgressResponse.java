package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

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
}
