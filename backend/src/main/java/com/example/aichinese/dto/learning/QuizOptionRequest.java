package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "optionText", "correctOption", "displayOrder" })
public record QuizOptionRequest(
        @NotBlank(message = "Option text is required.")
        @Size(max = 1000, message = "Option text must be 1000 characters or fewer.")
        String optionText,
        boolean correctOption,
        @PositiveOrZero(message = "Option display order must be greater than or equal to 0.")
        Integer displayOrder) {

    public QuizOptionRequest {
        optionText = strip(optionText);
    }
}
