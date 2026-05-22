package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record QuizSubmissionAnswerRequest(
        @NotNull(message = "Question id is required.")
        @Positive(message = "Question id must be positive.")
        Long questionId,
        @NotBlank(message = "Answer is required.")
        @Size(max = 2000, message = "Answer must be 2000 characters or fewer.")
        String answerText) {
}
