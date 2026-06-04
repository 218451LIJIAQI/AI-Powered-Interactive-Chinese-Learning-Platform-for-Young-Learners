package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({
        "title",
        "description",
        "lessonId",
        "packageId",
        "startTime",
        "endTime",
        "timeZone",
        "meetingLink",
        "meetingPlatform",
        "status",
        "maxParticipants"
})
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

        @Size(max = 64, message = "Time zone must be 64 characters or fewer.")
        String timeZone,

        @NotBlank(message = "Meeting link is required.")
        @Pattern(regexp = "^https?://\\S+$", message = "Meeting link must start with http:// or https:// and contain no whitespace.")
        @Size(max = 500, message = "Meeting link must be 500 characters or fewer.")
        String meetingLink,

        @NotBlank(message = "Meeting platform is required.")
        @Size(max = 80, message = "Meeting platform must be 80 characters or fewer.")
        String meetingPlatform,

        @Size(max = 30, message = "Status must be 30 characters or fewer.")
        String status,

        @Min(value = 1, message = "Maximum participants must be positive.")
        Integer maxParticipants) {

    public UpdateLiveClassRequest {
        title = strip(title);
        description = stripToNull(description);
        timeZone = stripToNull(timeZone);
        meetingLink = strip(meetingLink);
        meetingPlatform = strip(meetingPlatform);
        status = stripToNull(status);
    }
}
