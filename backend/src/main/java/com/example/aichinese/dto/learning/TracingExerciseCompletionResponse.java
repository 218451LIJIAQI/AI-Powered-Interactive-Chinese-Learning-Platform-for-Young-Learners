package com.example.aichinese.dto.learning;

public record TracingExerciseCompletionResponse(
        Long exerciseId,
        boolean completed,
        boolean firstCompletion,
        int earnedPoints,
        int totalPoints,
        int lessonCompletionPercent) {
}
