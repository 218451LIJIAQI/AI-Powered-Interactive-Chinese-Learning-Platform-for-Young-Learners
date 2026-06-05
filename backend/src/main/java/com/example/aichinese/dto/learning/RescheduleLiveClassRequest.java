package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "startTime", "endTime", "timeZone" })
public record RescheduleLiveClassRequest(
        @NotNull(message = "Start time is required.")
        LocalDateTime startTime,

        @NotNull(message = "End time is required.")
        LocalDateTime endTime,

        @Size(max = 64, message = "Time zone must be 64 characters or fewer.")
        String timeZone) {

    public RescheduleLiveClassRequest {
        timeZone = stripToNull(timeZone);
    }
}
