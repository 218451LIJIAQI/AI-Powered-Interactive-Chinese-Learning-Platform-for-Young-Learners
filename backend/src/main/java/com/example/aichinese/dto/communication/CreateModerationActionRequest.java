package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateModerationActionRequest(
        @NotBlank(message = "Target type is required.")
        @Size(max = 40, message = "Target type must be 40 characters or fewer.")
        String targetType,

        @NotNull(message = "Target id is required.")
        @Positive(message = "Target id must be positive.")
        Long targetId,

        @NotBlank(message = "Reason is required.")
        @Size(max = 1000, message = "Reason must be 1000 characters or fewer.")
        String reason) {
}
