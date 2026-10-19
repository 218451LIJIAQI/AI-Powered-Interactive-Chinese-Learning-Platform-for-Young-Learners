package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.enums.LearningStage;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "attemptId",
        "quizId",
        "quizTitle",
        "score",
        "maxScore",
        "totalQuestions",
        "correctCount",
        "wrongCount",
        "earnedPoints",
        "totalPoints",
        "passed",
        "startedAt",
        "submittedAt",
        "answers",
        "percentage",
        "learningStage"
})
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
        List<QuizAttemptResultQuestionResponse> answers,
        int percentage,
        LearningStage learningStage) {

    public QuizAttemptResultResponse {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        Objects.requireNonNull(quizId, "quizId must not be null");
        Objects.requireNonNull(quizTitle, "quizTitle must not be null");
        Objects.requireNonNull(score, "score must not be null");
        Objects.requireNonNull(maxScore, "maxScore must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        requireNonNegative(score, "score");
        requireNonNegative(maxScore, "maxScore");
        requireNonNegative(totalQuestions, "totalQuestions");
        requireNonNegative(correctCount, "correctCount");
        requireNonNegative(wrongCount, "wrongCount");
        requireNonNegative(earnedPoints, "earnedPoints");
        requireNonNegative(totalPoints, "totalPoints");
        answers = answers == null ? List.of() : List.copyOf(answers);
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative, was " + value);
        }
    }
}
