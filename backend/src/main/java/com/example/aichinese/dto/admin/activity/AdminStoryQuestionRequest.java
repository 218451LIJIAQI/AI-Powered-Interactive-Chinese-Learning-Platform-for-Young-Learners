package com.example.aichinese.dto.admin.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminStoryQuestionRequest(
        @NotBlank(message = "Question text is required.")
        @Size(max = 500, message = "Question text must not exceed 500 characters.")
        String questionText,
        @NotBlank(message = "Options payload (JSON) is required.")
        @Size(max = 65535, message = "Options payload is too large.")
        String optionsPayload,
        @Size(max = 1000, message = "Explanation must not exceed 1000 characters.")
        String explanation,
        @PositiveOrZero(message = "Sort order must be greater than or equal to 0.")
        Integer sortOrder) {
}
