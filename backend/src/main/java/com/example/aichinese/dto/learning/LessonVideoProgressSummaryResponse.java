package com.example.aichinese.dto.learning;

public record LessonVideoProgressSummaryResponse(
        Long studentId,
        String studentName,
        String studentEmail,
        Long recordedVideoId,
        String recordedVideoTitle,
        Integer progressPercentage,
        boolean completed,
        Integer watchedSeconds,
        Integer totalSeconds,
        String lastWatchedAt) {
}
