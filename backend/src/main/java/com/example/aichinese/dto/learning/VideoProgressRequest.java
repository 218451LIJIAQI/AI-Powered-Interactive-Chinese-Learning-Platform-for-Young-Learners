package com.example.aichinese.dto.learning;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

@JsonPropertyOrder({ "watchedSeconds", "totalSeconds", "progressPercentage", "completed" })
public record VideoProgressRequest(
        @PositiveOrZero(message = "Watched seconds must not be negative.")
        Integer watchedSeconds,

        @PositiveOrZero(message = "Total seconds must not be negative.")
        Integer totalSeconds,

        @PositiveOrZero(message = "Progress percentage must not be negative.")
        @Max(value = 100, message = "Progress percentage must not exceed 100.")
        Integer progressPercentage,

        Boolean completed) {
}
