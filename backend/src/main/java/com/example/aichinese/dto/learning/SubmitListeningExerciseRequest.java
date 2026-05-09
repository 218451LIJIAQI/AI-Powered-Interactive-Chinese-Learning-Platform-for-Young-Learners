package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;

public record SubmitListeningExerciseRequest(
        @NotBlank(message = "Selected answer is required.")
        String answerText) {
}
