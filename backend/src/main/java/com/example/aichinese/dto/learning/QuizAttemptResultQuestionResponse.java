package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "questionId",
        "questionType",
        "questionText",
        "studentAnswer",
        "correctAnswer",
        "correct",
        "points",
        "explanation"
})
public record QuizAttemptResultQuestionResponse(
        Long questionId,
        String questionType,
        String questionText,
        String studentAnswer,
        String correctAnswer,
        boolean correct,
        Integer points,
        String explanation) {

    public QuizAttemptResultQuestionResponse {
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(questionType, "questionType must not be null");
        Objects.requireNonNull(questionText, "questionText must not be null");
        Objects.requireNonNull(studentAnswer, "studentAnswer must not be null");
        Objects.requireNonNull(points, "points must not be null");
    }
}
