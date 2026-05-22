package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RecordedLessonVideoRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be 200 characters or fewer.")
        String title,

        @Size(max = 1000, message = "Description must be 1000 characters or fewer.")
        String description,

        @Positive(message = "Package id must be positive.")
        Long packageId,

        @NotBlank(message = "Source type is required.")
        @Size(max = 30, message = "Source type must be 30 characters or fewer.")
        String sourceType,

        @Pattern(regexp = "^(https?://.+)?$", message = "External video URL must start with http:// or https://.")
        @Size(max = 500, message = "External video URL must be 500 characters or fewer.")
        String externalVideoUrl,

        @PositiveOrZero(message = "Duration must not be negative.")
        Integer durationSeconds,

        @NotNull(message = "Display order is required.")
        @PositiveOrZero(message = "Display order must not be negative.")
        Integer sortOrder) {
}
