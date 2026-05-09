package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.Size;

public record CompleteTracingExerciseRequest(
        @Size(max = 255, message = "Canvas reference must not exceed 255 characters.")
        String canvasImageUrl) {
}
