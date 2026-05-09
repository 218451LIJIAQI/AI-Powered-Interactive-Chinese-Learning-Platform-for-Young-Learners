package com.example.aichinese.dto.learning;

public record FlashcardPracticeResponse(
        Long flashcardId,
        boolean completed,
        boolean firstCompletion,
        int totalPoints,
        int earnedPoints,
        int lessonCompletionPercent) {
}
