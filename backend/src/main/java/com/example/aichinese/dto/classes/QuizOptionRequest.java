package com.example.aichinese.dto.classes;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An answer of a quiz question; {@code feedback} is what a learner who picks it reads in the quiz review (A3.19:
 * Noodles -> "That's 面"), optional.
 */
public record QuizOptionRequest(
        @NotBlank(message = "Option text is required.")
        @Size(max = 1000, message = "Option text must be 1000 characters or fewer.")
        String optionText,
        boolean correctOption,
        @Size(max = 300, message = "Feedback must be 300 characters or fewer.")
        String feedback) {

    public QuizOptionRequest {
        optionText = strip(optionText);
        feedback = stripToNull(feedback);
    }
}
