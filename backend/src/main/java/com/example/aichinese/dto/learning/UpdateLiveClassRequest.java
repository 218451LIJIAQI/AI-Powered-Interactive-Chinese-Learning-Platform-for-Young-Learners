package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateLiveClassRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be 200 characters or fewer.")
        String title,

        @Size(max = 1000, message = "Description must be 1000 characters or fewer.")
        String description,

        @Positive(message = "Lesson id must be positive.")
        Long lessonId,
        @Positive(message = "Package id must be positive.")
        Long packageId,

        @NotNull(message = "Start time is required.")
        LocalDateTime startTime,

        @NotNull(message = "End time is required.")
        LocalDateTime endTime,

        @NotBlank(message = "Meeting link is required.")
        @Pattern(regexp = "^https?://.+$", message = "Meeting link must start with http:// or https://.")
        @Size(max = 500, message = "Meeting link must be 500 characters or fewer.")
        String meetingLink,

        @NotBlank(message = "Meeting platform is required.")
        @Size(max = 80, message = "Meeting platform must be 80 characters or fewer.")
        String meetingPlatform,

        @Size(max = 30, message = "Status must be 30 characters or fewer.")
        String status,

        @Min(value = 1, message = "Maximum participants must be positive.")
        Integer maxParticipants) {
}
