package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

public record RescheduleLiveClassRequest(
        @NotNull(message = "Start time is required.")
        LocalDateTime startTime,

        @NotNull(message = "End time is required.")
        LocalDateTime endTime) {
}
