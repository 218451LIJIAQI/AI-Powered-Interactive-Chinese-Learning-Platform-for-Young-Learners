package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuizSubmissionAnswerRequest(
        @NotNull(message = "Question id is required.")
        Long questionId,
        @NotBlank(message = "Answer is required.")
        String answerText) {
}
