package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "attemptId",
        "questionId",
        "quizTitle",
        "questionText",
        "questionType",
        "studentAnswer",
        "correctAnswer",
        "explanation",
        "submittedAt",
        "quizId",
        "lessonId",
        "lessonTitle"
})
public record WrongAnswerResponse(
        Long attemptId,
        Long questionId,
        String quizTitle,
        String questionText,
        String questionType,
        String studentAnswer,
        String correctAnswer,
        String explanation,
        LocalDateTime submittedAt,
        Long quizId,
        Long lessonId,
        String lessonTitle) {

    public WrongAnswerResponse {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(quizTitle, "quizTitle must not be null");
        Objects.requireNonNull(questionText, "questionText must not be null");
        Objects.requireNonNull(questionType, "questionType must not be null");
        Objects.requireNonNull(studentAnswer, "studentAnswer must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
    }
}
