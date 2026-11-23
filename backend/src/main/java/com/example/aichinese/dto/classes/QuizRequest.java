package com.example.aichinese.dto.classes;

import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The lesson's quiz when it is created; "Allow SparkBot hints" is off unless {@code hintsAllowed} is true. */
public record QuizRequest(
        @NotBlank(message = "Quiz title is required.")
        @Size(max = 200, message = "Quiz title must not exceed 200 characters.")
        String title,
        Boolean hintsAllowed) {

    public QuizRequest {
        title = strip(title);
    }
}
