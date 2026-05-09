package com.example.aichinese.dto.learning;

public record QuizAttemptResultQuestionResponse(
        Long questionId,
        String questionType,
        String questionText,
        String studentAnswer,
        String correctAnswer,
        boolean correct,
        Integer points,
        String explanation) {
}
