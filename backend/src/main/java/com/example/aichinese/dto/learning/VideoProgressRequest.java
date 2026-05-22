package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.PositiveOrZero;

public record VideoProgressRequest(
        @PositiveOrZero(message = "Watched seconds must not be negative.")
        Integer watchedSeconds,

        @PositiveOrZero(message = "Total seconds must not be negative.")
        Integer totalSeconds,

        @PositiveOrZero(message = "Progress percentage must not be negative.")
        Integer progressPercentage,

        Boolean completed) {
}
