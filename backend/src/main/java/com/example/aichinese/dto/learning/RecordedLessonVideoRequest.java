package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RecordedLessonVideoRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be 200 characters or fewer.")
        String title,

        @Size(max = 1000, message = "Description must be 1000 characters or fewer.")
        String description,

        Long packageId,

        @NotBlank(message = "Source type is required.")
        String sourceType,

        String externalVideoUrl,

        @PositiveOrZero(message = "Duration must not be negative.")
        Integer durationSeconds,

        @NotNull(message = "Display order is required.")
        @PositiveOrZero(message = "Display order must not be negative.")
        Integer sortOrder) {
}
