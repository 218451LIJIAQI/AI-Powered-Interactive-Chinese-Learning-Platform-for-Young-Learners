package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

public record WrongAnswerResponse(
        Long attemptId,
        Long questionId,
        String quizTitle,
        String questionText,
        String questionType,
        String studentAnswer,
        String correctAnswer,
        String explanation,
        LocalDateTime submittedAt) {
}
