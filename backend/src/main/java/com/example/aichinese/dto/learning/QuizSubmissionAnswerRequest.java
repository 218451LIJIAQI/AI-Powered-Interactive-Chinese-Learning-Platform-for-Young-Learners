package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * One answer of a quiz attempt. Option-based questions are answered (and scored) by
 * {@code optionId}; short-answer types (FILL_BLANK, REARRANGE_SENTENCE) by {@code answerText}.
 */
public record QuizSubmissionAnswerRequest(
        @NotNull(message = "Question id is required.")
        @Positive(message = "Question id must be positive.")
        Long questionId,

        @Positive(message = "Option id must be positive.")
        Long optionId,

        @Size(max = 2000, message = "Answer must be 2000 characters or fewer.")
        String answerText) {

    public QuizSubmissionAnswerRequest {
        answerText = strip(answerText);
    }
}
