package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({
        "title",
        "description",
        "packageId",
        "sourceType",
        "externalVideoUrl",
        "durationSeconds",
        "sortOrder"
})
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

        @Pattern(regexp = "^(https?://\\S+)?$", message = "External video URL must start with http:// or https:// and contain no whitespace.")
        @Size(max = 500, message = "External video URL must be 500 characters or fewer.")
        String externalVideoUrl,

        @PositiveOrZero(message = "Duration must not be negative.")
        Integer durationSeconds,

        @NotNull(message = "Display order is required.")
        @PositiveOrZero(message = "Display order must not be negative.")
        @Max(value = 10_000, message = "Display order must not exceed 10000.")
        Integer sortOrder) {

    public RecordedLessonVideoRequest {
        title = strip(title);
        description = stripToNull(description);
        sourceType = strip(sourceType);
        externalVideoUrl = stripToNull(externalVideoUrl);
    }
}
