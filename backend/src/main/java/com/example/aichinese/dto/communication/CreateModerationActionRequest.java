package com.example.aichinese.dto.communication;

import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateModerationActionRequest(
        @NotBlank(message = "Target type is required.")
        @Size(max = MAX_TARGET_TYPE_LENGTH, message = "Target type must be 40 characters or fewer.")
        String targetType,

        @NotNull(message = "Target id is required.")
        @Positive(message = "Target id must be positive.")
        Long targetId,

        @NotBlank(message = "Reason is required.")
        @Size(max = MAX_REASON_LENGTH, message = "Reason must be 1000 characters or fewer.")
        String reason) {

    public static final int MAX_TARGET_TYPE_LENGTH = 40;

    public static final int MAX_REASON_LENGTH = 1000;

    public CreateModerationActionRequest {
        targetType = strip(targetType);
        reason = strip(reason);
    }
}
