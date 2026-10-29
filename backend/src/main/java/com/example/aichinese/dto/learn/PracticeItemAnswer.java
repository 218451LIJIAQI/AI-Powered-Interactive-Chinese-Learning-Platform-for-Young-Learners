package com.example.aichinese.dto.learn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One answer to a Practice item (chosen option text, matched meaning, or tiles separated by spaces). */
public record PracticeItemAnswer(
        @NotBlank(message = "Item is required.")
        @Size(max = 40, message = "Item is required.")
        String itemKey,

        @Size(max = 300, message = "Answer must not exceed 300 characters.")
        String answer) {
}
