package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record ListeningOptionRequest(
        @NotBlank(message = "Listening option text is required.")
        String optionText,
        boolean correctOption,
        @PositiveOrZero(message = "Display order must be greater than or equal to 0.")
        Integer displayOrder) {
}
