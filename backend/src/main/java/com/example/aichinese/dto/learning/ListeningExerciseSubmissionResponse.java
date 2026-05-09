package com.example.aichinese.dto.learning;

public record ListeningExerciseSubmissionResponse(
        Long exerciseId,
        boolean correct,
        boolean firstCompletion,
        String correctAnswer,
        String explanation,
        int earnedPoints,
        int totalPoints,
        int lessonCompletionPercent) {
}
