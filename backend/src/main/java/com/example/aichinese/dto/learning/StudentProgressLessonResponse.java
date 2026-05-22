package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

public record StudentProgressLessonResponse(
        Long lessonId,
        String lessonTitle,
        int completionPercent,
        boolean completed,
        LocalDateTime lastAccessedAt) {
}
