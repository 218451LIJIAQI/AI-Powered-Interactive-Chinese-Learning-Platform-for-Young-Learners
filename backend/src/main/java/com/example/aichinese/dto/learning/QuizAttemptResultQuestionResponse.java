package com.example.aichinese.dto.learning;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Per-question result shown after a quiz submission (FR-QUIZ-04): the student's answer, whether it
 * was correct, the correct answer (text and, for option-based questions, the option id), the
 * explanation, whether the hint was used and the listed options for review.
 */
@JsonPropertyOrder({
        "questionId",
        "questionType",
        "questionText",
        "studentAnswer",
        "correctAnswer",
        "correct",
        "points",
        "explanation",
        "selectedOptionId",
        "correctOptionId",
        "hintUsed",
        "options"
})
public record QuizAttemptResultQuestionResponse(
        Long questionId,
        String questionType,
        String questionText,
        String studentAnswer,
        String correctAnswer,
        boolean correct,
        Integer points,
        String explanation,
        Long selectedOptionId,
        Long correctOptionId,
        boolean hintUsed,
        List<QuizOptionResponse> options) {

    public QuizAttemptResultQuestionResponse {
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(questionType, "questionType must not be null");
        Objects.requireNonNull(questionText, "questionText must not be null");
        Objects.requireNonNull(studentAnswer, "studentAnswer must not be null");
        Objects.requireNonNull(points, "points must not be null");
        options = options == null ? List.of() : List.copyOf(options);
    }
}
