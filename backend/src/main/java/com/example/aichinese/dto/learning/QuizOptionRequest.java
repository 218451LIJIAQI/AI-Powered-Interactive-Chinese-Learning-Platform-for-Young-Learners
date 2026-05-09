package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record QuizOptionRequest(
        @NotBlank(message = "Option text is required.")
        String optionText,
        boolean correctOption,
        @PositiveOrZero(message = "Option display order must be greater than or equal to 0.")
        Integer displayOrder) {
}
