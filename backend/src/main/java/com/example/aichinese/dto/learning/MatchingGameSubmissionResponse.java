package com.example.aichinese.dto.learning;

public record MatchingGameSubmissionResponse(
        Long gameId,
        boolean completed,
        boolean firstCompletion,
        int correctPairs,
        int totalPairs,
        int earnedPoints,
        int totalPoints,
        int lessonCompletionPercent) {
}
