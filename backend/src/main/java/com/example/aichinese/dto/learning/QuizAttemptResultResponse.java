package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.List;

public record QuizAttemptResultResponse(
        Long attemptId,
        Long quizId,
        String quizTitle,
        Integer score,
        Integer maxScore,
        int totalQuestions,
        int correctCount,
        int wrongCount,
        int earnedPoints,
        int totalPoints,
        boolean passed,
        LocalDateTime startedAt,
        LocalDateTime submittedAt,
        List<QuizAttemptResultQuestionResponse> answers) {
}
