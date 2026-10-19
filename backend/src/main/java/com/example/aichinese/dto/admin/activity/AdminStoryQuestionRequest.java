package com.example.aichinese.dto.admin.activity;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminStoryQuestionRequest(
        @NotBlank(message = "Question text is required.")
        @Size(max = 500, message = "Question text must not exceed 500 characters.")
        String questionText,

        @NotBlank(message = "Options payload (JSON) is required.")
        @Size(max = 16_000, message = "Options payload is too large.")
        String optionsPayload,

        @Size(max = 1000, message = "Explanation must not exceed 1000 characters.")
        String explanation,

        @PositiveOrZero(message = "Sort order must be non-negative.")
        @Max(value = 10_000, message = "Sort order must not exceed 10000.")
        Integer sortOrder,

        /** 0-based index of the correct option (kept on the server; required before review). */
        @PositiveOrZero(message = "Correct option index must be non-negative.")
        @Max(value = 50, message = "Correct option index must not exceed 50.")
        Integer correctOptionIndex) {

    public AdminStoryQuestionRequest {
        questionText = strip(questionText);
        optionsPayload = strip(optionsPayload);
        explanation = stripToNull(explanation);
    }
}
