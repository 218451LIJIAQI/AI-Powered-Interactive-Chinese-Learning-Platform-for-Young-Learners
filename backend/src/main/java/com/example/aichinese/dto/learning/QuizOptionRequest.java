package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuizOptionRequest(
        @NotBlank(message = "Option text is required.")
        @Size(max = 1000, message = "Option text must be 1000 characters or fewer.")
        String optionText,
        boolean correctOption) {

    public QuizOptionRequest {
        optionText = strip(optionText);
    }
}
