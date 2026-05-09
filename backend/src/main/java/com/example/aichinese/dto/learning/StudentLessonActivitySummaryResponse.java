package com.example.aichinese.dto.learning;

public record StudentLessonActivitySummaryResponse(
        String activityType,
        Long sourceId,
        Long placeholderId,
        String title,
        String description,
        boolean available,
        boolean completed,
        boolean locked) {
}
