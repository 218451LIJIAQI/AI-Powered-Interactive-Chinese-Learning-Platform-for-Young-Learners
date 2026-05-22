package com.example.aichinese.dto.learning;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateQuizQuestionRequest(
        @NotBlank(message = "Question type is required.")
        String questionType,
        @NotBlank(message = "Question text is required.")
        String questionText,
        String correctAnswerText,
        @NotNull(message = "Question points are required.")
        @Positive(message = "Question points must be greater than 0.")
        Integer points,
        @PositiveOrZero(message = "Question display order must be greater than or equal to 0.")
        Integer displayOrder,
        @Valid
        List<QuizOptionRequest> options,
        String explanation) {
}
