package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTracingExerciseRequest(
        @NotBlank(message = "Tracing exercise title is required.")
        @Size(max = 200, message = "Tracing exercise title must not exceed 200 characters.")
        String title,
        @NotBlank(message = "Target character is required.")
        @Size(max = 100, message = "Target character must not exceed 100 characters.")
        String targetCharacter,
        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,
        @Size(max = 500, message = "Meaning must not exceed 500 characters.")
        String meaning,
        @Size(max = 255, message = "Worksheet reference must not exceed 255 characters.")
        String worksheetUrl,
        @Size(max = 1000, message = "Instructions must not exceed 1000 characters.")
        String instructions) {
}
